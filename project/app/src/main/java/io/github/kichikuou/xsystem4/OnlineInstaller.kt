package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

data class ModDownloadRequest(
    val url: String,
    val fileName: String,
    val contentLength: Long,
    val expectedSha256: String? = null
)

data class ModReleaseEntry(
    val fileId: Int,
    val version: String,
    val fileName: String,
    val summary: String,
    val changelog: String,
    val uploadedAt: String,
    val downloadUrl: String,
    val isPatch: Boolean,
    val baseFileId: Int?,
    val baseVersion: String?,
    val expectedSize: Long = -1L,
    val expectedSha256: String? = null,
    val group: String = ""
) {
    val formattedDate: String
        get() = uploadedAt.replace('T', ' ').take(16)

    val isFullMarked: Boolean
        get() = version.contains("完整") || fileName.contains("完整") || version.contains("正式版")
}

data class InstalledModInfo @JvmOverloads constructor(
    val version: String,
    val baseVersion: String?,
    val fileId: Int,
    val baseFileId: Int,
    val fileName: String,
    val uploadedAt: String,
    val source: String,
    val installedAtMs: Long,
    val kind: String = KIND_MOD,
    val group: String = ""
) {
    val formattedDate: String
        get() = uploadedAt.replace('T', ' ').take(16)

    companion object {
        const val META_FILE_NAME = ".mod_version.json"
        const val KIND_MOD = "mod"
        const val KIND_CN_PATCH = "cn_patch"

        fun read(gameDir: File): InstalledModInfo? {
            val f = File(gameDir, META_FILE_NAME)
            if (!f.isFile) return null
            return try {
                val j = JSONObject(f.readText(Charsets.UTF_8))
                val rawVer = j.optString("version", "").trim()
                if (rawVer.isEmpty()) return null
                val rawBaseVer = j.optString("baseVersion", "").trim().takeIf { it.isNotEmpty() }
                val source = j.optString("source", "online").trim().ifEmpty { "online" }
                val fileName = j.optString("fileName", "").trim()
                val kind = j.optString("kind", KIND_MOD).trim().ifEmpty { KIND_MOD }
                val isPatchStored = rawBaseVer != null && rawBaseVer != rawVer
                val isPatchHint = when {
                    rawBaseVer == null -> null
                    isPatchStored -> true
                    else -> false
                }
                val ver = if (source == "online" || kind == KIND_CN_PATCH) {
                    rawVer
                } else {
                    OnlineInstaller.inferVersionFromFileName(
                        fileName = rawVer.ifEmpty { fileName },
                        isPatchHint = isPatchHint,
                        fallbackIniDir = if (!isPatchStored) gameDir else null
                    )
                }
                val baseVer = when {
                    rawBaseVer == null -> null
                    source == "online" || kind == KIND_CN_PATCH -> rawBaseVer
                    else -> OnlineInstaller.inferVersionFromFileName(
                        fileName = rawBaseVer,
                        isPatchHint = false,
                        fallbackIniDir = gameDir
                    )
                }
                val group = if (j.has("group")) {
                    j.optString("group", "").trim()
                } else if (kind == KIND_MOD) {
                    OnlineInstaller.inferModGroup(fileName, gameDir)
                } else {
                    ""
                }
                InstalledModInfo(
                    version = ver,
                    baseVersion = baseVer,
                    fileId = j.optInt("fileId", -1),
                    baseFileId = j.optInt("baseFileId", -1),
                    fileName = fileName,
                    uploadedAt = j.optString("uploadedAt", "").trim(),
                    source = source,
                    installedAtMs = j.optLong("installedAtMs", 0L),
                    kind = kind,
                    group = group
                )
            } catch (_: Throwable) {
                null
            }
        }

        fun readOrInfer(gameDir: File): InstalledModInfo? {
            read(gameDir)?.let { return it }
            if (!Item.detectInstalledMod(gameDir)) return null
            val inferred = OnlineInstaller.extractCanonicalModVersionFromDir(gameDir)
                ?: OnlineInstaller.inferModGroup("", gameDir).takeIf { it.isNotEmpty() }
                ?: return null
            val inferredGroup = OnlineInstaller.inferModGroup("", gameDir)
            return InstalledModInfo(
                version = inferred,
                baseVersion = inferred,
                fileId = -1,
                baseFileId = -1,
                fileName = "",
                uploadedAt = "",
                source = "preinstalled",
                installedAtMs = 0L,
                group = inferredGroup
            )
        }

        fun write(gameDir: File, info: InstalledModInfo) {
            try {
                val j = JSONObject()
                    .put("version", info.version)
                    .put("baseVersion", info.baseVersion ?: "")
                    .put("fileId", info.fileId)
                    .put("baseFileId", info.baseFileId)
                    .put("fileName", info.fileName)
                    .put("uploadedAt", info.uploadedAt)
                    .put("source", info.source)
                    .put("installedAtMs", info.installedAtMs)
                    .put("kind", info.kind)
                    .put("group", info.group)
                File(gameDir, META_FILE_NAME).writeText(j.toString(), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }

        fun delete(gameDir: File) {
            try {
                File(gameDir, META_FILE_NAME).delete()
            } catch (_: Throwable) {
            }
        }
    }
}

data class ModUpdatePlan(
    val targetRelease: ModReleaseEntry,
    val steps: List<ModReleaseEntry>,
    val isIncrementalOnly: Boolean
)

data class OnlineProgressUpdate(
    val text: String,
    val permille: Int? = null
)

class InstallAbortHandle {
    private val lock = Any()
    private var aborted = false
    private val connections = HashSet<HttpURLConnection>()
    private val workerThreads = HashSet<Thread>()
    private val children = HashSet<InstallAbortHandle>()

    val isAborted: Boolean
        get() = synchronized(lock) { aborted }

    fun addChild(child: InstallAbortHandle) {
        val abortNow = synchronized(lock) {
            if (aborted) {
                true
            } else {
                children.add(child)
                false
            }
        }
        if (abortNow) {
            child.abort()
        }
    }

    fun removeChild(child: InstallAbortHandle) {
        synchronized(lock) {
            children.remove(child)
        }
    }

    fun register(conn: HttpURLConnection) {
        val curThread = Thread.currentThread()
        val disconnectImmediately = synchronized(lock) {
            if (aborted) {
                true
            } else {
                connections.add(conn)
                workerThreads.add(curThread)
                false
            }
        }
        if (disconnectImmediately) {
            disconnectAsync(listOf(conn))
            throw CancellationException()
        }
    }

    fun unregister(conn: HttpURLConnection?) {
        val curThread = Thread.currentThread()
        val wasAborted = synchronized(lock) {
            if (conn != null) {
                connections.remove(conn)
            }
            workerThreads.remove(curThread)
            aborted
        }
        if (wasAborted) {
            // Clear interrupt flag before returning worker thread to Dispatchers.IO pool
            Thread.interrupted()
        }
    }

    fun abort() {
        val (threads, toClose, childCopy) = synchronized(lock) {
            aborted = true
            val tCopy = workerThreads.toList()
            val cCopy = connections.toList()
            val chCopy = children.toList()
            workerThreads.clear()
            connections.clear()
            children.clear()
            Triple(tCopy, cCopy, chCopy)
        }
        for (ch in childCopy) {
            try {
                ch.abort()
            } catch (_: Throwable) {
            }
        }
        for (t in threads) {
            try {
                t.interrupt()
            } catch (_: Throwable) {
            }
        }
        if (toClose.isNotEmpty()) {
            disconnectAsync(toClose)
        }
    }

    companion object {
        fun disconnectAsync(conns: List<HttpURLConnection>) {
            Thread({
                for (c in conns) {
                    try {
                        c.disconnect()
                    } catch (_: Throwable) {
                    }
                }
            }, "InstallHttpAbort").apply {
                isDaemon = true
                start()
            }
        }
    }
}

object OnlineInstaller {
    const val RANCE10_CDN_URL = "https://alicehub.000.moe/rance10"
    const val DOHNA_DOHNA_CDN_URL = "https://alicehub.000.moe/dohnadohna"
    const val EVENICLE_CDN_URL = "https://alicehub.000.moe/evenicle"
    const val EVENICLE2_CDN_URL = "https://alicehub.000.moe/evenicle2"
    const val SENGOKU_RANCE_CDN_URL = "https://alicehub.000.moe/sengokurance"
    const val RANCE01_CDN_URL = "https://alicehub.000.moe/rance01"
    const val RANCE02_CDN_URL = "https://alicehub.000.moe/rance02"
    const val RANCE03_CDN_URL = "https://alicehub.000.moe/rance03"
    const val RANCE04_CDN_URL = "https://alicehub.000.moe/rance04"
    const val RANCE41_CDN_URL = "https://alicehub.000.moe/rance41"
    const val RANCE42_CDN_URL = "https://alicehub.000.moe/rance42"
    const val RANCE5D_CDN_URL = "https://alicehub.000.moe/rance5d"
    const val RANCE06_CDN_URL = "https://alicehub.000.moe/rance06"
    const val RANCE08_CDN_URL = "https://alicehub.000.moe/rance08"
    const val RANCE09_CDN_URL = "https://alicehub.000.moe/rance09"
    const val KICHIKUOU_CDN_URL = "https://alicehub.000.moe/kichikuou"
    const val DAIAKUJI_CDN_URL = "https://alicehub.000.moe/daiakuji"
    const val DAIBANCHOU_CDN_URL = "https://alicehub.000.moe/daibanchou"
    const val DAITEIKOKU_CDN_URL = "https://alicehub.000.moe/daiteikoku"
    const val ESCALAYER_CDN_URL = "https://alicehub.000.moe/escalayer"
    const val ESCALAYER_REBOOT_CDN_URL = "https://alicehub.000.moe/escalayerreboot"
    const val GALZOO_CDN_URL = "https://alicehub.000.moe/galzoo"
    const val IXSEAL_CDN_URL = "https://alicehub.000.moe/ixseal"
    const val HARUKA_CDN_URL = "https://alicehub.000.moe/haruka"
    const val TSUMASHIBORI_CDN_URL = "https://alicehub.000.moe/tsumashibori"
    const val TSUMAMIGUI_CDN_URL = "https://alicehub.000.moe/tsumamigui"
    const val TSUMAMIGUI3_CDN_URL = "https://alicehub.000.moe/tsumamigui3"
    const val KAERUNYO_CDN_URL = "https://alicehub.000.moe/kaerunyo"
    const val ATLACHNACHA_CDN_URL = "https://alicehub.000.moe/atlachnacha"
    const val REISHIKI_CDN_URL = "https://alicehub.000.moe/reishiki"
    const val HARUURARE_CDN_URL = "https://alicehub.000.moe/haruurare"
    const val OKUSAMAJUN_CDN_URL = "https://alicehub.000.moe/okusamajun"
    const val OKUSAMANTR_CDN_URL = "https://alicehub.000.moe/okusamantr"
    const val EVENICLE2RANCE_CDN_URL = "https://alicehub.000.moe/evenicle2rance"
    const val EVENICLEGAIDEN_CDN_URL = "https://alicehub.000.moe/eveniclegaiden"
    const val WAIDONYO_CDN_URL = "https://alicehub.000.moe/waidonyo"
    const val RANCE6AFTER_CDN_URL = "https://alicehub.000.moe/rance6after"
    const val YAKATA2_CDN_URL = "https://alicehub.000.moe/yakata2"
    const val SDPSMARIA_CDN_URL = "https://alicehub.000.moe/sdpsmaria"
    const val TOUSHIN3_CDN_URL = "https://alicehub.000.moe/toushin3"
    const val OYAKORANKAN_CDN_URL = "https://alicehub.000.moe/oyakorankan"
    const val YORUGAKURU_CDN_URL = "https://alicehub.000.moe/yorugakuru"
    const val RANCE1DIGEST_CDN_URL = "https://alicehub.000.moe/rance1digest"
    const val MAMAN_CDN_URL = "https://alicehub.000.moe/maman"
    const val EVENICLERANCE_CDN_URL = "https://alicehub.000.moe/eveniclerance"
    const val HAHARANMAN_CDN_URL = "https://alicehub.000.moe/haharanman"
    const val TSUMAMIGUI2_CDN_URL = "https://alicehub.000.moe/tsumamigui2"
    const val TSUMAMIGUI3JUN_CDN_URL = "https://alicehub.000.moe/tsumamigui3jun"
    const val TSUMAMIGUI3NTR_CDN_URL = "https://alicehub.000.moe/tsumamigui3ntr"
    const val DIABOLIQUE_CDN_URL = "https://alicehub.000.moe/diabolique"
    const val GAKUENKING_CDN_URL = "https://alicehub.000.moe/gakuenking"
    const val GAKUENKINGEX_CDN_URL = "https://alicehub.000.moe/gakuenkingex"
    const val OUDOUYUUSHA_CDN_URL = "https://alicehub.000.moe/oudouyuusha"
    const val TOUSHIN1_CDN_URL = "https://alicehub.000.moe/toushin1"
    const val TOUSHIN2_CDN_URL = "https://alicehub.000.moe/toushin2"
    const val DARCROWS_CDN_URL = "https://alicehub.000.moe/darcrows"
    const val IKUSAMIKO_CDN_URL = "https://alicehub.000.moe/ikusamiko"
    const val ONLYYOU_CDN_URL = "https://alicehub.000.moe/onlyyou"
    const val SANBIKI_CDN_URL = "https://alicehub.000.moe/sanbiki"
    const val PERSIOM_CDN_URL = "https://alicehub.000.moe/persiom"
    const val SEEINAO_CDN_URL = "https://alicehub.000.moe/seeinao"
    const val HUSHABY_CDN_URL = "https://alicehub.000.moe/hushaby"
    const val AYUMI_CDN_URL = "https://alicehub.000.moe/ayumi"
    const val CIELCLAIRE_CDN_URL = "https://alicehub.000.moe/cielclaire"
    const val NIGHTDEMON_CDN_URL = "https://alicehub.000.moe/nightdemon"
    const val KATSUMI_CDN_URL = "https://alicehub.000.moe/katsumi"
    const val DRSTOP_CDN_URL = "https://alicehub.000.moe/drstop"
    const val DPSALL_CDN_URL = "https://alicehub.000.moe/dpsall"
    const val KOREDPS_CDN_URL = "https://alicehub.000.moe/koredps"
    const val KAERUALICE_CDN_URL = "https://alicehub.000.moe/kaerualice"
    const val PROSTUDENTG_CDN_URL = "https://alicehub.000.moe/prostudentg"
    const val PROSTUDENTGOOD_CDN_URL = "https://alicehub.000.moe/prostudentgood"
    const val PASTELCHIME_CDN_URL = "https://alicehub.000.moe/pastelchime"
    const val PASTELCHIME2_CDN_URL = "https://alicehub.000.moe/pastelchime2"
    const val YAKATA3_CDN_URL = "https://alicehub.000.moe/yakata3"
    const val DALK_CDN_URL = "https://alicehub.000.moe/dalk"
    const val NINGENGARI_CDN_URL = "https://alicehub.000.moe/ningengari"
    const val PASCHACPP_CDN_URL = "https://alicehub.000.moe/paschacpp"
    const val PASCHA3_CDN_URL = "https://alicehub.000.moe/pascha3"
    const val MAMATOTO_CDN_URL = "https://alicehub.000.moe/mamatoto"
    const val ONLYYOURECROSS_CDN_URL = "https://alicehub.000.moe/onlyyourecross"
    const val MAMORIGAMI_CDN_URL = "https://alicehub.000.moe/mamorigami"
    const val AMBIVALENZ_CDN_URL = "https://alicehub.000.moe/ambivalenz"
    const val FUNNYBEE_CDN_URL = "https://alicehub.000.moe/funnybee"
    const val TOUSHIN2SP_CDN_URL = "https://alicehub.000.moe/toushin2sp"
    const val MUGENHOUYOU_CDN_URL = "https://alicehub.000.moe/mugenhouyou"
    const val OTOMESENKI_CDN_URL = "https://alicehub.000.moe/otomesenki"
    const val GAKUENHYOURYUU_CDN_URL = "https://alicehub.000.moe/gakuenhyouryuu"
    const val MAIDNOSUSUME_CDN_URL = "https://alicehub.000.moe/maidnosusume"
    const val DALKGAIDEN_CDN_URL = "https://alicehub.000.moe/dalkgaiden"
    const val MAMANYONYO_CDN_URL = "https://alicehub.000.moe/mamanyonyo"
    const val MAJONOSHOKUZAI_CDN_URL = "https://alicehub.000.moe/majonoshokuzai"
    const val SABOTEN_CDN_URL = "https://alicehub.000.moe/saboten"
    const val DOUBLESENSEI_CDN_URL = "https://alicehub.000.moe/doublesensei"
    const val OJOUSAMA_CDN_URL = "https://alicehub.000.moe/ojousama"
    const val EDORANZE_CDN_URL = "https://alicehub.000.moe/edoranze"
    const val VANISH_CDN_URL = "https://alicehub.000.moe/vanish"
    const val HOKENSHITSU_CDN_URL = "https://alicehub.000.moe/hokenshitsu"
    const val KAMUNAGARA_CDN_URL = "https://alicehub.000.moe/kamunagara"
    const val ERUMEIFLOWER_CDN_URL = "https://alicehub.000.moe/erumeiflower"
    const val ERUMEIWAKARE_CDN_URL = "https://alicehub.000.moe/erumeiwakare"
    const val MANANA1_CDN_URL = "https://alicehub.000.moe/manana1"
    const val MANANA2_CDN_URL = "https://alicehub.000.moe/manana2"
    const val MANANA3_CDN_URL = "https://alicehub.000.moe/manana3"
    const val MANANA4_CDN_URL = "https://alicehub.000.moe/manana4"
    const val MANANA5_CDN_URL = "https://alicehub.000.moe/manana5"
    const val TSUMASAKURA_CDN_URL = "https://alicehub.000.moe/tsumasakura"
    const val SHAMANSSANCTUARY_CDN_URL = "https://alicehub.000.moe/shamanssanctuary"
    const val ALIVEZ_CDN_URL = "https://alicehub.000.moe/alivez"
    const val DUNGEONSDOLLS_CDN_URL = "https://alicehub.000.moe/dungeonsdolls"
    const val SUZUMIYA_CDN_URL = "https://alicehub.000.moe/suzumiya"
    const val SHIMAIMA_CDN_URL = "https://alicehub.000.moe/shimaima"
    const val DAIBANCHOUGAIDEN_CDN_URL = "https://alicehub.000.moe/daibanchougaiden"
    const val COSPLAYLEMON_CDN_URL = "https://alicehub.000.moe/cosplaylemon"
    const val WHIPHONEY_CDN_URL = "https://alicehub.000.moe/whiphoney"
    const val ONIMUSU_CDN_URL = "https://alicehub.000.moe/onimusu"
    const val PROGRX_CDN_URL = "https://alicehub.000.moe/progrx"
    const val DPSVERN_CDN_URL = "https://alicehub.000.moe/dpsvern"
    const val SUKIIRONO_CDN_URL = "https://alicehub.000.moe/sukiirono"
    const val GIRLS_CDN_URL = "https://alicehub.000.moe/girls"
    const val DEBUT_CDN_URL = "https://alicehub.000.moe/debut"
    const val HARUKAVSESCA_CDN_URL = "https://alicehub.000.moe/harukavsesca"
    const val HARUKANINJA_CDN_URL = "https://alicehub.000.moe/harukaninja"
    const val MEGANE_CDN_URL = "https://alicehub.000.moe/megane"
    const val RACHELSAKURA_CDN_URL = "https://alicehub.000.moe/rachelsakura"
    const val SABAKUNIKA_CDN_URL = "https://alicehub.000.moe/sabakunika"
    const val VANISHPALM_CDN_URL = "https://alicehub.000.moe/vanishpalm"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val HEADROOM_BYTES = 64L * 1024L * 1024L
    private const val MAX_HTTP_RETRIES = 8
    private const val MAX_REDIRECTS = 6
    private const val READ_SLICE_TIMEOUT_MS = 3_000
    private const val TOTAL_READ_TIMEOUT_MS = 45_000L
    const val DEFAULT_MIN_SEGMENTED_BYTES = 8L * 1024L * 1024L
    private const val MIN_SEGMENT_SIZE_BYTES = 4L * 1024L * 1024L
    private const val SEGMENT_META_PERSIST_BYTES = 2L * 1024L * 1024L

    data class ManifestEntry(
        val path: String,
        val key: String,
        val size: Long,
        val sha256: String,
        val extract: Boolean
    )

    data class OnlineManifest(
        val gameName: String,
        val baseName: String,
        val totalBytes: Long,
        val entries: List<ManifestEntry>
    )

    internal data class SegmentProgress(
        val start: Long,
        val end: Long,
        @Volatile var done: Long
    ) {
        val length: Long get() = end - start + 1L
        val isComplete: Boolean get() = done >= length
    }

    private data class SavedMeta(
        val url: String?,
        val etag: String?,
        val totalBytes: Long,
        val segmented: Boolean = false,
        val segments: List<SegmentProgress> = emptyList()
    )

    internal fun computeSegmentCount(
        remainingBytes: Long,
        maxConnections: Int,
        minSegmentedBytes: Long = DEFAULT_MIN_SEGMENTED_BYTES
    ): Int {
        val capped = AppSettings.normalizeDownloadConcurrency(maxConnections)
        if (capped <= 1 || remainingBytes < minSegmentedBytes || minSegmentedBytes <= 0L) return 1
        val minChunk = (minSegmentedBytes / 2L).coerceAtLeast(1L).coerceAtMost(MIN_SEGMENT_SIZE_BYTES)
        val bySize = (remainingBytes / minChunk).coerceAtMost(capped.toLong()).toInt().coerceAtLeast(1)
        return bySize.coerceIn(1, capped)
    }

    internal fun buildInitialSegments(
        alreadyDonePrefix: Long,
        totalBytes: Long,
        segmentCount: Int
    ): List<SegmentProgress> {
        val out = ArrayList<SegmentProgress>(segmentCount + 1)
        if (alreadyDonePrefix > 0L) {
            out.add(SegmentProgress(0L, alreadyDonePrefix - 1L, alreadyDonePrefix))
        }
        val remaining = totalBytes - alreadyDonePrefix
        val count = segmentCount.coerceAtLeast(1)
        val base = remaining / count
        val rem = remaining % count
        var cursor = alreadyDonePrefix
        for (i in 0 until count) {
            val len = base + if (i < rem) 1L else 0L
            if (len <= 0L) continue
            out.add(SegmentProgress(cursor, cursor + len - 1L, 0L))
            cursor += len
        }
        return out
    }

    class InvalidManifestException(message: String) : IOException(message)

    const val BASE_STAGING_IDENTITY_FILE = ".online_base_staging_identity"
    /** Written into a finished online install; holds the CDN base URL it was installed from. */
    const val ONLINE_INSTALL_SOURCE_FILE = ".online_install_source"
    private const val REINSTALL_BACKUP_PREFIX = ".online_base_backup_"
    private val sha256HexRegex = Regex("^[0-9a-fA-F]{64}$")

    private val copySuffixRegex = Regex("""(?:\s*[\(（]\d+[\)）]|\s*[-_]?\s*副本)+$""")

    private fun stripArchiveExtAndCopySuffix(raw: String): String {
        var s = raw.trim()
        for (ext in arrayOf(".tar.gz", ".tar.xz", ".rar", ".7z", ".zip", ".tgz", ".txz")) {
            if (s.lowercase(Locale.ROOT).endsWith(ext)) {
                s = s.substring(0, s.length - ext.length).trim()
                break
            }
        }
        s = copySuffixRegex.replace(s, "").trim()
        return s
    }

    fun normalizeModVersionKey(raw: String): String {
        var s = stripArchiveExtAndCopySuffix(raw.lowercase(Locale.ROOT))
        s = s.replace("(", "")
            .replace(")", "")
            .replace("（", "")
            .replace("）", "")
            .replace("[", "")
            .replace("]", "")
            .replace(" ", "")
            .replace("_", "")
        var changed = true
        while (changed) {
            changed = false
            for (prefix in arrayOf(
                "兰斯10日不落mod",
                "rance10日不落mod",
                "日不落mod",
                "日不落",
                "兰斯10全系列mod",
                "rance10全系列mod",
                "r10全系列mod",
                "全系列mod",
                "全系列",
                "bfyy",
                "兰斯10mod",
                "rance10mod",
                "r10mod",
                "兰斯10",
                "rance10",
                "r10"
            )) {
                if (s.startsWith(prefix)) {
                    s = s.removePrefix(prefix).trimStart('-', '.')
                    changed = true
                }
            }
        }
        if (s.startsWith("v") && s.length > 1 && s[1].isDigit()) {
            s = s.substring(1)
        }
        s = s.replace("bug修复补丁", "补丁")
            .replace("修复补丁", "补丁")
            .replace("增量补丁", "补丁")
            .replace("patch", "补丁")
        if (!s.contains("补丁")) {
            var suffixChanged = true
            while (suffixChanged) {
                suffixChanged = false
                for (suffix in arrayOf("完整版", "正式版", "完整包", "整合版", "本体", "更新")) {
                    if (s.endsWith(suffix) && s.length > suffix.length) {
                        s = s.removeSuffix(suffix).trimEnd('-', '.')
                        suffixChanged = true
                    }
                }
            }
        }
        return s
    }

    private val modVersionNumberRegex = Regex("""v?(\d+\.\d+(?:\.\d+)?[a-zA-Z]?)""", RegexOption.IGNORE_CASE)
    private val patchSeqRegex = Regex("""(?:补丁|patch|hotfix)\s*(\d+)""", RegexOption.IGNORE_CASE)

    internal fun resolveModVersionIniFile(gameDir: File): File? {
        if (!gameDir.isDirectory) return null
        return File(gameDir, "AliceStart.ini").takeIf { it.isFile }
            ?: File(gameDir, "System40.ini").takeIf { it.isFile }
            ?: gameDir.listFiles()?.firstOrNull {
                it.isFile && (it.name.equals("AliceStart.ini", ignoreCase = true) ||
                    it.name.equals("System40.ini", ignoreCase = true))
            }
    }

    fun extractCanonicalModVersionFromDir(gameDir: File): String? {
        if (!gameDir.isDirectory) return null
        val iniFile = resolveModVersionIniFile(gameDir)
        val gn = iniFile?.let {
            try {
                System40Ini.parse(it).gameName
            } catch (_: Throwable) {
                null
            }
        }
        if (!gn.isNullOrBlank()) {
            extractCanonicalModVersion(gn, isPatchHint = false)?.let { return it }
        }
        return extractCanonicalModVersion(gameDir.name, isPatchHint = false)
    }

    private fun extractCanonicalModVersion(raw: String, isPatchHint: Boolean?): String? {
        val base = stripArchiveExtAndCopySuffix(
            raw.substringAfterLast('/').substringAfterLast('\\').trim()
        )
        if (base.isEmpty()) return null

        var cleaned = base
            .replace('（', ' ')
            .replace('）', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('【', ' ')
            .replace('】', ' ')
            .replace("收费必是倒卖", " ")
            .replace("免费分享", " ")
            .replace("严禁倒卖", " ")
            .trim()

        val trimSep = charArrayOf(' ', '-', '_', '·', ':', '：')
        var prefixStripped = true
        while (prefixStripped) {
            prefixStripped = false
            cleaned = cleaned.trimStart(*trimSep)
            for (prefix in arrayOf(
                "兰斯10",
                "兰斯１０",
                "决战",
                "rance10",
                "rance 10",
                "r10",
                "日不落mod",
                "日不落",
                "全系列mod",
                "全系列",
                "bfyy",
                "mod"
            )) {
                if (cleaned.startsWith(prefix, ignoreCase = true)) {
                    cleaned = cleaned.substring(prefix.length).trimStart(*trimSep)
                    prefixStripped = true
                }
            }
        }

        val match = modVersionNumberRegex.find(cleaned) ?: return null
        val ver = match.groupValues[1]
        val lower = cleaned.lowercase(Locale.ROOT)
        val afterVer = cleaned.substring(match.range.last + 1)

        val hasPatchWord = lower.contains("补丁") || lower.contains("patch") ||
            lower.contains("hotfix") || lower.contains("热更") || lower.contains("修复")
        val hasFullWord = lower.contains("完整") || lower.contains("正式") ||
            lower.contains("整合") || lower.contains("本体")

        val isPatch = when {
            hasPatchWord && !hasFullWord -> true
            hasFullWord && !hasPatchWord -> false
            isPatchHint != null -> isPatchHint
            else -> hasPatchWord
        }

        if (isPatch) {
            val patchNum = patchSeqRegex.find(afterVer)?.groupValues?.getOrNull(1).orEmpty()
            return if (lower.contains("增量")) {
                "v${ver}增量补丁$patchNum"
            } else {
                "v${ver}修复补丁$patchNum"
            }
        }

        return when {
            lower.contains("内测") -> "v${ver}内测版"
            lower.contains("公测") -> "v${ver}公测版"
            lower.contains("测试") || lower.contains("beta") -> "v${ver}测试版"
            lower.contains("先行") -> "v${ver}先行版"
            lower.contains("体验") -> "v${ver}体验版"
            else -> "v${ver}正式版"
        }
    }

    fun inferVersionFromFileName(
        fileName: String,
        isPatchHint: Boolean? = null,
        fallbackIniDir: File? = null
    ): String {
        extractCanonicalModVersion(fileName, isPatchHint)?.let { return it }

        if (fallbackIniDir != null) {
            extractCanonicalModVersionFromDir(fallbackIniDir)?.let { return it }
        }

        val rawBase = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val stripped = stripArchiveExtAndCopySuffix(rawBase)
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('（', ' ')
            .replace('）', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (stripped.isEmpty()) return "MOD"
        val lower = stripped.lowercase(Locale.ROOT)
        if (lower == "mod" || lower == "mods" || lower == "rance10" || lower == "兰斯10") {
            return "MOD"
        }
        return stripped
    }

    /** Infers display version for a local overlay archive/folder. */
    fun inferLocalOverlayVersion(
        fileName: String,
        isPatchHint: Boolean,
        fallbackIniDir: File?,
        isCnPatch: Boolean
    ): String {
        if (!isCnPatch) {
            return inferVersionFromFileName(
                fileName = fileName,
                isPatchHint = isPatchHint,
                fallbackIniDir = if (!isPatchHint) fallbackIniDir else null
            )
        }
        val iniName = fallbackIniDir?.let { dir ->
            val iniFile = File(dir, "AliceStart.ini").takeIf { it.isFile }
                ?: File(dir, "System40.ini").takeIf { it.isFile }
            iniFile?.let {
                try {
                    System40Ini.parse(it).gameName
                } catch (_: Throwable) {
                    null
                }
            }
        }.orEmpty()
        val verMatch = modVersionNumberRegex.find(fileName)
            ?: modVersionNumberRegex.find(iniName)
        return if (verMatch != null) "V${verMatch.groupValues[1]}" else "V1.00"
    }

    const val MOD_GROUP_RIBULUO = "日不落MOD"
    const val MOD_GROUP_BFYY = "全系列MOD"

    fun inferModGroup(fileName: String = "", gameDir: File? = null): String {
        val lowerName = fileName.lowercase(Locale.ROOT)
        if (lowerName.contains("全系列") || lowerName.contains("bfyy") || lowerName.contains("波风幽影")) {
            return MOD_GROUP_BFYY
        }
        if (lowerName.contains("日不落")) {
            return MOD_GROUP_RIBULUO
        }
        if (gameDir != null && gameDir.isDirectory) {
            val children = gameDir.listFiles()
            if (children != null) {
                for (f in children) {
                    if (!f.isFile) continue
                    val n = f.name.lowercase(Locale.ROOT)
                    if ((n.startsWith("rance10cgbfyy") || n.startsWith("rance10flatbfyy")) && n.endsWith(".afa")) {
                        return MOD_GROUP_BFYY
                    }
                    if ((n.startsWith("rance10modcg") || n.startsWith("rance10modflat")) && n.endsWith(".afa")) {
                        return MOD_GROUP_RIBULUO
                    }
                }
            }
            val iniFile = resolveModVersionIniFile(gameDir)
            if (iniFile != null) {
                val ini = runCatching { System40Ini.parse(iniFile) }.getOrNull()
                if (ini?.SaveFolder?.equals("SaveDataBFYY", ignoreCase = true) == true) {
                    return MOD_GROUP_BFYY
                }
                val gn = ini?.gameName.orEmpty()
                if (gn.contains("全系列") || gn.contains("bfyy", ignoreCase = true)) {
                    return MOD_GROUP_BFYY
                }
                if (gn.contains("日不落") || ini?.SaveFolder?.equals("ModSaveData", ignoreCase = true) == true) {
                    return MOD_GROUP_RIBULUO
                }
            }
        }
        return ""
    }

    fun distinctModGroups(releases: List<ModReleaseEntry>): List<String> {
        val seen = LinkedHashSet<String>()
        for (r in releases) {
            val g = r.group.trim()
            if (g.isNotEmpty()) seen.add(g)
        }
        return seen.toList()
    }

    fun releasesForGroup(releases: List<ModReleaseEntry>, group: String?): List<ModReleaseEntry> {
        val groups = distinctModGroups(releases)
        if (groups.size <= 1 || group.isNullOrBlank()) return releases
        val filtered = releases.filter { it.group.equals(group, ignoreCase = true) }
        return if (filtered.isNotEmpty()) filtered else releases
    }

    fun groupsConflict(a: String?, b: String?): Boolean {
        val ga = a?.trim().orEmpty()
        val gb = b?.trim().orEmpty()
        return ga.isNotEmpty() && gb.isNotEmpty() && !ga.equals(gb, ignoreCase = true)
    }

    private fun sameReleaseGroup(a: ModReleaseEntry, b: ModReleaseEntry): Boolean =
        !groupsConflict(a.group, b.group)

    fun matchInstalledIndex(releases: List<ModReleaseEntry>, installed: InstalledModInfo?): Int {
        if (installed == null || releases.isEmpty()) return -1
        if (installed.fileId > 0) {
            val byId = releases.indexOfFirst {
                it.fileId == installed.fileId && !groupsConflict(it.group, installed.group)
            }
            if (byId >= 0) return byId
        }
        if (installed.fileName.isNotBlank()) {
            val byFile = releases.indexOfFirst {
                it.fileName.equals(installed.fileName, ignoreCase = true) && !groupsConflict(it.group, installed.group)
            }
            if (byFile >= 0) return byFile
            val normFile = normalizeModVersionKey(installed.fileName)
            if (normFile.isNotEmpty()) {
                val byNormFile = releases.indexOfFirst {
                    !groupsConflict(it.group, installed.group) && (
                        normalizeModVersionKey(it.fileName) == normFile ||
                            normalizeModVersionKey(it.version) == normFile
                        )
                }
                if (byNormFile >= 0) return byNormFile
            }
        }
        val normInstalled = normalizeModVersionKey(installed.version)
        if (normInstalled.isNotEmpty()) {
            val byVer = releases.indexOfFirst {
                !groupsConflict(it.group, installed.group) && (
                    normalizeModVersionKey(it.version) == normInstalled ||
                        normalizeModVersionKey(it.fileName) == normInstalled
                    )
            }
            if (byVer >= 0) return byVer
        }
        return -1
    }

    private fun isInstalledMatchingBaseRelease(
        baseRelease: ModReleaseEntry,
        installed: InstalledModInfo?
    ): Boolean {
        if (installed == null) return false
        if (groupsConflict(baseRelease.group, installed.group)) return false
        if (installed.baseFileId > 0 && installed.baseFileId == baseRelease.fileId) {
            return true
        }
        val baseNorm = normalizeModVersionKey(baseRelease.version)
        if (baseNorm.isNotEmpty() && !installed.baseVersion.isNullOrBlank()) {
            if (normalizeModVersionKey(installed.baseVersion) == baseNorm) {
                return true
            }
        }
        return false
    }

    fun hasMatchingBaseForPatch(
        releases: List<ModReleaseEntry>,
        patchIdx: Int,
        hasMod: Boolean,
        installed: InstalledModInfo?
    ): Boolean {
        if (!hasMod || patchIdx !in releases.indices) return false
        val patch = releases[patchIdx]
        if (!patch.isPatch) return false
        if (groupsConflict(patch.group, installed?.group)) return false
        val baseIdx = (patchIdx + 1 until releases.size).firstOrNull {
            !releases[it].isPatch && sameReleaseGroup(patch, releases[it])
        } ?: return false
        val baseRelease = releases[baseIdx]
        val installedIdx = matchInstalledIndex(releases, installed)
        if (installedIdx in patchIdx..baseIdx && sameReleaseGroup(patch, releases[installedIdx])) return true
        return isInstalledMatchingBaseRelease(baseRelease, installed)
    }

    @JvmOverloads
    fun computeUpdatePlan(
        releases: List<ModReleaseEntry>,
        hasMod: Boolean,
        installed: InstalledModInfo?,
        canRestore: Boolean = true
    ): ModUpdatePlan? {
        val latest = releases.firstOrNull() ?: return null
        val switchingGroup = hasMod && groupsConflict(latest.group, installed?.group)
        if (switchingGroup && !canRestore) {
            return null
        }
        val installedIdx = if (hasMod && !switchingGroup) matchInstalledIndex(releases, installed) else -1
        if (hasMod && !switchingGroup && installedIdx == 0) {
            return null
        }
        if (hasMod && !switchingGroup && installed != null && installed.fileId > 0 && latest.fileId > 0 &&
            installedIdx < 0 && installed.fileId >= latest.fileId
        ) {
            return null
        }
        if (!latest.isPatch) {
            return ModUpdatePlan(
                targetRelease = latest,
                steps = listOf(latest),
                isIncrementalOnly = false
            )
        }
        val baseIdx = (1 until releases.size).firstOrNull {
            !releases[it].isPatch && sameReleaseGroup(latest, releases[it])
        } ?: -1
        if (baseIdx < 0) {
            return if (hasMod && !switchingGroup) {
                ModUpdatePlan(
                    targetRelease = latest,
                    steps = listOf(latest),
                    isIncrementalOnly = true
                )
            } else {
                null
            }
        }
        val baseRelease = releases[baseIdx]
        val matchingBase = hasMod && !switchingGroup && (
            (installedIdx in 1..baseIdx && sameReleaseGroup(latest, releases[installedIdx])) ||
                isInstalledMatchingBaseRelease(baseRelease, installed)
            )
        if (matchingBase) {
            val patches = if (installedIdx in 1..baseIdx) {
                (installedIdx - 1 downTo 0).map { releases[it] }
                    .filter { it.isPatch && sameReleaseGroup(latest, it) }
            } else {
                val minFid = installed?.fileId ?: -1
                (baseIdx - 1 downTo 0).map { releases[it] }.filter {
                    it.isPatch && sameReleaseGroup(latest, it) &&
                        (minFid <= 0 || it.fileId <= 0 || it.fileId > minFid)
                }
            }
            if (patches.isNotEmpty()) {
                return ModUpdatePlan(
                    targetRelease = latest,
                    steps = patches,
                    isIncrementalOnly = true
                )
            }
        }
        val fullSteps = ArrayList<ModReleaseEntry>()
        fullSteps.add(baseRelease)
        for (i in baseIdx - 1 downTo 0) {
            if (releases[i].isPatch && sameReleaseGroup(latest, releases[i])) {
                fullSteps.add(releases[i])
            }
        }
        return ModUpdatePlan(
            targetRelease = latest,
            steps = fullSteps,
            isIncrementalOnly = false
        )
    }

    @JvmOverloads
    fun computeStepsForSelectedRelease(
        releases: List<ModReleaseEntry>,
        targetIdx: Int,
        hasMod: Boolean,
        installed: InstalledModInfo?,
        includeBaseForPatch: Boolean,
        canRestore: Boolean = true
    ): List<ModReleaseEntry> {
        if (targetIdx !in releases.indices) return emptyList()
        val target = releases[targetIdx]
        val switchingGroup = hasMod && groupsConflict(target.group, installed?.group)
        if (switchingGroup && !canRestore) {
            return emptyList()
        }
        if (!target.isPatch) {
            return listOf(target)
        }
        val baseIdx = (targetIdx + 1 until releases.size).firstOrNull {
            !releases[it].isPatch && sameReleaseGroup(target, releases[it])
        } ?: -1
        if (switchingGroup) {
            if (!includeBaseForPatch || baseIdx < 0) {
                return emptyList()
            }
        }
        if (!includeBaseForPatch || baseIdx < 0) {
            if (hasMod && baseIdx >= 0 && !switchingGroup) {
                val installedIdx = matchInstalledIndex(releases, installed)
                if (installedIdx in (targetIdx + 1)..baseIdx && sameReleaseGroup(target, releases[installedIdx])) {
                    val incremental = (installedIdx - 1 downTo targetIdx)
                        .map { releases[it] }
                        .filter { it.isPatch && sameReleaseGroup(target, it) }
                    if (incremental.isNotEmpty()) return incremental
                }
            }
            return listOf(target)
        }
        val steps = ArrayList<ModReleaseEntry>()
        steps.add(releases[baseIdx])
        for (i in baseIdx - 1 downTo targetIdx) {
            if (releases[i].isPatch && sameReleaseGroup(target, releases[i])) {
                steps.add(releases[i])
            }
        }
        return steps
    }

    private fun sanitizeParenFree(raw: String, multiline: Boolean): String {
        val cleaned = raw
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('（', ' ')
            .replace('）', ' ')
        return if (multiline) {
            cleaned.replace("\r", "")
                .split('\n')
                .map { it.replace(Regex("""\s+"""), " ").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        } else {
            cleaned.replace(Regex("""\s+"""), " ").trim()
        }
    }

    fun fetchModCatalog(
        cdnBaseUrl: String = RANCE10_CDN_URL,
        catalogRelPath: String = "files/mods/index.json"
    ): List<ModReleaseEntry> {
        val base = cdnBaseUrl.trimEnd('/')
        val cleanRel = catalogRelPath.trimStart('/')
        var conn: HttpURLConnection? = null
        try {
            conn = openFollowingRedirects(
                initialUrl = "$base/$cleanRel",
                headers = mapOf(
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Accept" to "application/json",
                    "Accept-Encoding" to "identity"
                )
            )
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("MOD catalog HTTP $code from $base")
            }
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val arr = root.optJSONArray("releases")
                ?: throw IOException("No MOD releases array in catalog")
            val rawRows = ArrayList<ModReleaseEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("key", "").trim()
                val fileName = obj.optString("fileName", "").trim()
                val version = sanitizeParenFree(obj.optString("version", ""), multiline = false)
                if (key.isEmpty() || fileName.isEmpty() || version.isEmpty()) continue
                if (!ArchiveExtractor.isSupportedArchiveName(fileName)) continue
                val fileId = obj.optInt("fileId", 1000 - i)
                val group = sanitizeParenFree(obj.optString("group", ""), multiline = false)
                val summary = sanitizeParenFree(obj.optString("summary", ""), multiline = false)
                val changelog = sanitizeParenFree(obj.optString("changelog", ""), multiline = true)
                    .ifEmpty { summary }
                val uploadedAt = obj.optString("uploadedAt", "").trim()
                val isPatch = obj.optBoolean(
                    "isPatch",
                    version.contains("补丁") || fileName.contains("补丁")
                )
                val encodedKey = key.split('/').joinToString("/") { Uri.encode(it) }
                val fileUrl = "$base/files/$encodedKey"
                rawRows.add(
                    ModReleaseEntry(
                        fileId = fileId,
                        version = version,
                        fileName = fileName,
                        summary = summary,
                        changelog = changelog,
                        uploadedAt = uploadedAt,
                        downloadUrl = fileUrl,
                        isPatch = isPatch,
                        baseFileId = null,
                        baseVersion = null,
                        expectedSize = obj.optLong("size", -1L),
                        expectedSha256 = obj.optString("sha256", "").trim().takeIf { it.isNotEmpty() },
                        group = group
                    )
                )
            }
            if (rawRows.isEmpty()) {
                throw IOException("No MOD releases found")
            }
            val resolved = ArrayList<ModReleaseEntry>(rawRows.size)
            for (i in rawRows.indices) {
                val item = rawRows[i]
                if (!item.isPatch) {
                    resolved.add(item)
                } else {
                    val baseEntry = (i + 1 until rawRows.size).map { rawRows[it] }
                        .firstOrNull { !it.isPatch && sameReleaseGroup(item, it) }
                    resolved.add(
                        item.copy(
                            baseFileId = baseEntry?.fileId,
                            baseVersion = baseEntry?.version
                        )
                    )
                }
            }
            return resolved
        } catch (t: Throwable) {
            throw (t as? IOException) ?: IOException("No MOD releases found", t)
        } finally {
            conn?.disconnect()
        }
    }

    @JvmOverloads
    fun installBaseGameFromCloud(
        context: Context,
        storagePath: File,
        cdnBaseUrl: String = RANCE10_CDN_URL,
        abortHandle: InstallAbortHandle? = null,
        onStagingDirReady: ((File) -> Unit)? = null,
        checkCancelled: () -> Unit,
        onProgress: (OnlineProgressUpdate) -> Unit,
        concurrency: Int = AppSettings.getDownloadConcurrency(context),
        minSegmentedBytes: Long = DEFAULT_MIN_SEGMENTED_BYTES
    ): File {
        onProgress(OnlineProgressUpdate(context.getString(R.string.online_fetching_manifest), null))
        val canonicalBaseUrl = AppSettings.canonicalCdnUrl(cdnBaseUrl)
        val fetchBaseUrl = AppSettings.resolveCdnUrl(context, canonicalBaseUrl).trimEnd('/')
        val manifest = fetchManifest(fetchBaseUrl, abortHandle, checkCancelled)
        if (manifest.entries.isEmpty()) {
            throw GameList.InstallFailureException(R.string.online_download_error)
        }
        if (abortHandle?.isAborted == true) throw CancellationException()
        checkCancelled()
        AppSettings.setCachedBaseGameSize(context, canonicalBaseUrl, manifest.totalBytes)

        val safeBase = sanitizeInstallDirName(manifest.baseName, fallback = "Game")
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .trim('_')
            .ifEmpty { "Game" }
        val storageCanonical = storagePath.canonicalFile
        val stagingDir = File(storageCanonical, ".online_base_staging_$safeBase").canonicalFile
        if (stagingDir.parentFile != storageCanonical) {
            throw IOException("Invalid staging path")
        }
        if (abortHandle?.isAborted == true) throw CancellationException()
        checkCancelled()
        prepareBaseStagingDir(stagingDir, computeBaseStagingIdentity(canonicalBaseUrl, manifest))
        onStagingDirReady?.invoke(stagingDir)
        val stagingPrefix = stagingDir.path + File.separator
        val verifiedDoneEntries = HashSet<String>()

        data class PreparedEntry(
            val index: Int,
            val entry: ManifestEntry,
            val cleanPath: String,
            val finalFile: File,
            val partFile: File,
            val partMetaFile: File,
            val fileUrl: String,
            val alreadyDone: Boolean,
            val initialPartBytes: Long
        )

        val prepared = ArrayList<PreparedEntry>(manifest.entries.size)
        var alreadyCompletedBytes = 0L
        var partialBytesOnDisk = 0L
        var alreadyDoneFiles = 0

        for ((index, entry) in manifest.entries.withIndex()) {
            checkCancelled()
            val cleanPath = ArchiveExtractor.repairSjisAsGbkMojibake(entry.path)
            val relParts = cleanPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (relParts.isEmpty() || relParts.any { it == ".." }) {
                throw IOException("Invalid manifest entry path: ${entry.path}")
            }
            val finalFile = File(stagingDir, relParts.joinToString(File.separator)).canonicalFile
            if (!finalFile.path.startsWith(stagingPrefix)) {
                throw IOException("Manifest path escapes staging directory: ${entry.path}")
            }
            val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
            val partMetaFile = File("${partFile.path}.meta")
            val encodedKey = entry.key.split('/').joinToString("/") { Uri.encode(it) }
            val fileUrl = "$fetchBaseUrl/files/$encodedKey"

            val done = isManifestEntryDone(stagingDir, stagingPrefix, entry, verifiedDoneEntries, checkCancelled)
            val initPart = if (done) {
                alreadyCompletedBytes += entry.size
                alreadyDoneFiles++
                entry.size
            } else {
                val pBytes = estimatePartialBytesOnDisk(partFile, partMetaFile, fileUrl, entry.size, entry.sha256)
                partialBytesOnDisk += pBytes
                pBytes
            }
            prepared.add(
                PreparedEntry(
                    index = index,
                    entry = entry,
                    cleanPath = cleanPath,
                    finalFile = finalFile,
                    partFile = partFile,
                    partMetaFile = partMetaFile,
                    fileUrl = fileUrl,
                    alreadyDone = done,
                    initialPartBytes = initPart
                )
            )
        }

        val remainingTotal = (manifest.totalBytes - alreadyCompletedBytes - partialBytesOnDisk).coerceAtLeast(0L)
        ensureAvailableSpace(storageCanonical, remainingTotal)

        val totalFiles = manifest.entries.size
        val totalGameMb = (manifest.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
        val pending = prepared.filter { !it.alreadyDone }
        val effectiveConcurrency = AppSettings.normalizeDownloadConcurrency(concurrency)

        fun commitEntry(item: PreparedEntry, committedBytesBefore: Long) {
            checkCancelled()
            if (item.entry.extract) {
                onProgress(
                    OnlineProgressUpdate(
                        context.getString(R.string.online_verifying_extracting, item.cleanPath),
                        if (manifest.totalBytes > 0L) {
                            (((committedBytesBefore + item.entry.size) * 1000L) / manifest.totalBytes)
                                .toInt().coerceIn(0, 1000)
                        } else null
                    )
                )
                ArchiveExtractor.extractFileToDirectory(
                    archiveFile = item.partFile,
                    destDir = stagingDir,
                    shouldSkip = { rel -> NativeGameImport.skip(rel) },
                    throwInstallFailureOnNoSpace = true,
                    checkCancelled = checkCancelled
                )
                markerFileForExtractEntry(stagingDir, item.entry).writeText(item.entry.sha256)
                item.partFile.delete()
                item.partMetaFile.delete()
            } else {
                item.finalFile.parentFile?.mkdirs()
                if (item.finalFile.exists() && !item.finalFile.delete()) {
                    throw IOException("Cannot overwrite target file: ${item.finalFile}")
                }
                if (!moveFileOrDir(item.partFile, item.finalFile)) {
                    throw IOException("Failed to finalize downloaded file: ${item.partFile} -> ${item.finalFile}")
                }
                item.partMetaFile.delete()
            }
        }

        if (pending.isNotEmpty()) {
            if (effectiveConcurrency <= 1 || pending.size == 1) {
                var completedBeforeCurrent = alreadyCompletedBytes
                val perFileConns = if (pending.size == 1) effectiveConcurrency else 1
                for (item in pending) {
                    checkCancelled()
                    item.finalFile.parentFile?.mkdirs()
                    downloadFileWithResume(
                        url = item.fileUrl,
                        destPartFile = item.partFile,
                        expectedSize = item.entry.size,
                        expectedSha256 = item.entry.sha256,
                        headers = mapOf("User-Agent" to DEFAULT_USER_AGENT),
                        abortHandle = abortHandle,
                        checkCancelled = checkCancelled,
                        onProgress = { fileDoneBytes, _, speedBps ->
                            val globalDone = (completedBeforeCurrent + fileDoneBytes).coerceAtMost(manifest.totalBytes)
                            val permille = if (manifest.totalBytes > 0L) {
                                ((globalDone * 1000L) / manifest.totalBytes).toInt().coerceIn(0, 1000)
                            } else {
                                0
                            }
                            val pct = permille / 10
                            val doneMb = globalDone / (1024L * 1024L)
                            val speedStr = formatSpeed(speedBps)
                            val msg = context.getString(
                                R.string.online_base_download_progress,
                                item.index + 1,
                                totalFiles,
                                pct,
                                speedStr,
                                doneMb,
                                totalGameMb,
                                item.cleanPath
                            )
                            onProgress(OnlineProgressUpdate(msg, permille))
                        },
                        maxConnections = perFileConns,
                        minSegmentedBytes = minSegmentedBytes
                    )
                    commitEntry(item, completedBeforeCurrent)
                    completedBeforeCurrent += item.entry.size
                }
            } else {
                val batchAbort = InstallAbortHandle()
                abortHandle?.addChild(batchAbort)
                val workerCount = effectiveConcurrency
                val pool = Executors.newFixedThreadPool(workerCount) { r ->
                    Thread(r, "AliceHubDL").apply { isDaemon = true }
                }
                val entryDone = AtomicLongArray(totalFiles)
                for (item in prepared) {
                    entryDone.set(item.index, item.initialPartBytes)
                }
                val completedFilesCount = AtomicInteger(alreadyDoneFiles)
                val maxReportedDone = AtomicLong(alreadyCompletedBytes + partialBytesOnDisk)
                val progressLock = Any()
                val progressEmitLock = Any()
                val activeNames = LinkedHashSet<String>()
                var lastCompletedName = pending.first().cleanPath
                var speedWindowStartMs = SystemClock.elapsedRealtime()
                var speedWindowBytes = 0L
                var currentSpeedBps = 0L
                var lastUiReportMs = 0L

                fun reportAggregateProgress(deltaBytes: Long, force: Boolean = false) {
                    synchronized(progressEmitLock) {
                        var update: OnlineProgressUpdate? = null
                        synchronized(progressLock) {
                            if (deltaBytes > 0L) {
                                speedWindowBytes += deltaBytes
                            }
                            val now = SystemClock.elapsedRealtime()
                            val elapsed = now - speedWindowStartMs
                            if (elapsed >= 600L) {
                                currentSpeedBps = (speedWindowBytes * 1000L) / elapsed
                                speedWindowStartMs = now
                                speedWindowBytes = 0L
                            }
                            if (!force && now - lastUiReportMs < 150L) return
                            lastUiReportMs = now
                            var sum = 0L
                            for (i in 0 until totalFiles) {
                                sum += entryDone.get(i)
                            }
                            val clamped = sum.coerceAtMost(manifest.totalBytes)
                            val globalDone = maxReportedDone.updateAndGet { prev -> maxOf(prev, clamped) }
                            val permille = if (manifest.totalBytes > 0L) {
                                ((globalDone * 1000L) / manifest.totalBytes).toInt().coerceIn(0, 1000)
                            } else {
                                0
                            }
                            val pct = permille / 10
                            val doneMb = globalDone / (1024L * 1024L)
                            val speedStr = formatSpeed(currentSpeedBps)
                            val displayIdx = (completedFilesCount.get() + 1).coerceAtMost(totalFiles)
                            val activeCount = activeNames.size.coerceAtMost(effectiveConcurrency)
                            val primaryName = activeNames.firstOrNull() ?: lastCompletedName
                            val displayLabel = if (activeCount > 1) {
                                "$primaryName (+${activeCount - 1})"
                            } else {
                                primaryName
                            }
                            val msg = context.getString(
                                R.string.online_base_download_progress,
                                displayIdx,
                                totalFiles,
                                pct,
                                speedStr,
                                doneMb,
                                totalGameMb,
                                displayLabel
                            )
                            update = OnlineProgressUpdate(msg, permille)
                        }
                        update?.let(onProgress)
                    }
                }

                // Group pending entries sharing the exact same partFile (defensive deduplication)
                val groupedByPart = LinkedHashMap<String, MutableList<PreparedEntry>>()
                for (item in pending) {
                    groupedByPart.getOrPut(item.partFile.canonicalPath) { ArrayList() }.add(item)
                }
                // Schedule largest files first so huge archives start immediately alongside smaller files
                val orderedGroups = groupedByPart.values.sortedByDescending { grp -> grp.maxOf { it.entry.size } }

                val remainingTasks = AtomicInteger(orderedGroups.size)
                val allDoneLatch = CountDownLatch(1)
                val firstError = AtomicReference<Throwable?>(null)

                fun failBatch(t: Throwable) {
                    if (firstError.compareAndSet(null, t)) {
                        batchAbort.abort()
                    }
                }

                fun submitDynamicTask(task: () -> Unit): Boolean {
                    if (batchAbort.isAborted || firstError.get() != null) return false
                    remainingTasks.incrementAndGet()
                    return try {
                        pool.execute {
                            try {
                                task()
                            } catch (t: Throwable) {
                                failBatch(t)
                            } finally {
                                if (remainingTasks.decrementAndGet() == 0) {
                                    allDoneLatch.countDown()
                                }
                            }
                        }
                        true
                    } catch (_: RejectedExecutionException) {
                        if (remainingTasks.decrementAndGet() == 0) {
                            allDoneLatch.countDown()
                        }
                        false
                    }
                }

                val workerCheckCancelled = {
                    if (batchAbort.isAborted || abortHandle?.isAborted == true) {
                        throw CancellationException()
                    }
                    checkCancelled()
                }

                try {
                    for (group in orderedGroups) {
                        pool.execute {
                            try {
                                for (item in group) {
                                    workerCheckCancelled()
                                    item.finalFile.parentFile?.mkdirs()
                                    synchronized(progressLock) { activeNames.add(item.cleanPath) }
                                    reportAggregateProgress(0L, force = true)
                                    val prevFileDone = AtomicLong(entryDone.get(item.index))
                                    val onItemDone: (Throwable?) -> Unit = { err ->
                                        synchronized(progressLock) {
                                            activeNames.remove(item.cleanPath)
                                            lastCompletedName = item.cleanPath
                                        }
                                        if (err != null) {
                                            failBatch(err)
                                        } else {
                                            val prev = prevFileDone.getAndSet(item.entry.size)
                                            val delta = (item.entry.size - prev).coerceAtLeast(0L)
                                            entryDone.set(item.index, item.entry.size)
                                            completedFilesCount.incrementAndGet()
                                            reportAggregateProgress(delta, force = true)
                                        }
                                    }
                                    if (group.size > 1) {
                                        // Serialized within identical-partFile group
                                        downloadFileWithResume(
                                            url = item.fileUrl,
                                            destPartFile = item.partFile,
                                            expectedSize = item.entry.size,
                                            expectedSha256 = item.entry.sha256,
                                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT),
                                            abortHandle = batchAbort,
                                            checkCancelled = workerCheckCancelled,
                                            onProgress = { fileDoneBytes, _, _ ->
                                                val clampedFile = fileDoneBytes.coerceAtMost(item.entry.size)
                                                val prev = prevFileDone.getAndSet(clampedFile)
                                                entryDone.set(item.index, clampedFile)
                                                reportAggregateProgress((clampedFile - prev).coerceAtLeast(0L))
                                            },
                                            maxConnections = 1,
                                            minSegmentedBytes = minSegmentedBytes
                                        )
                                        onItemDone(null)
                                    } else {
                                        startFileDownloadOnPool(
                                            url = item.fileUrl,
                                            destPartFile = item.partFile,
                                            expectedSize = item.entry.size,
                                            expectedSha256 = item.entry.sha256,
                                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT),
                                            maxConnections = effectiveConcurrency,
                                            minSegmentedBytes = minSegmentedBytes,
                                            batchAbort = batchAbort,
                                            checkCancelled = workerCheckCancelled,
                                            onProgressDelta = { fileDoneBytes, delta, _ ->
                                                val clampedFile = fileDoneBytes.coerceAtMost(item.entry.size)
                                                prevFileDone.set(clampedFile)
                                                entryDone.set(item.index, clampedFile)
                                                reportAggregateProgress(delta)
                                            },
                                            submitSubTask = ::submitDynamicTask,
                                            onFileFinished = onItemDone
                                        )
                                    }
                                }
                            } catch (t: Throwable) {
                                failBatch(t)
                            } finally {
                                if (remainingTasks.decrementAndGet() == 0) {
                                    allDoneLatch.countDown()
                                }
                            }
                        }
                    }

                    while (!allDoneLatch.await(150L, TimeUnit.MILLISECONDS)) {
                        if (abortHandle?.isAborted == true) {
                            batchAbort.abort()
                            throw CancellationException()
                        }
                        try {
                            checkCancelled()
                        } catch (t: Throwable) {
                            batchAbort.abort()
                            throw t
                        }
                    }
                } finally {
                    pool.shutdown()
                    if (!pool.awaitTermination(5L, TimeUnit.SECONDS)) {
                        batchAbort.abort()
                        pool.shutdownNow()
                        pool.awaitTermination(3L, TimeUnit.SECONDS)
                    }
                    abortHandle?.removeChild(batchAbort)
                }

                if (abortHandle?.isAborted == true) throw CancellationException()
                checkCancelled()
                firstError.get()?.let { err ->
                    when (err) {
                        is CancellationException -> throw err
                        is GameList.InstallFailureException -> throw err
                        is IOException -> throw err
                        is RuntimeException -> throw err
                        is Error -> throw err
                        else -> throw IOException("Concurrent download failed", err)
                    }
                }

                // Phase 2: Commit downloaded files & extract archives strictly in manifest order
                var committedBytes = alreadyCompletedBytes
                for (item in prepared) {
                    if (item.alreadyDone) continue
                    commitEntry(item, committedBytes)
                    committedBytes += item.entry.size
                }
            }
        }

        // Clean up internal marker/meta files before finalizing game directory. The staging
        // identity stays until the move succeeds so a failed move keeps verified downloads reusable.
        stagingDir.walkTopDown()
            .filter {
                it.isFile && (it.name.startsWith(".extracted_") ||
                    it.name.endsWith(".part.meta") ||
                    it.name.endsWith(".part.meta.tmp"))
            }
            .toList()
            .forEach { it.delete() }

        val hasSys4Ini = stagingDir.listFiles()?.any {
            it.isFile && GameCatalog.isSystem4LaunchIni(it.name)
        } == true
        val sys4AinFile = findPrimarySys4AinFile(stagingDir)
        val isSys3x = GameCatalog.isSystem3xGameDir(stagingDir)
        if (!(hasSys4Ini && sys4AinFile != null) && !isSys3x) {
            throw GameList.InstallFailureException(R.string.directory_no_game)
        }

        val dirBaseName = resolveInstallDirBaseName(
            stagingDir = stagingDir,
            manifestBaseName = manifest.baseName,
            sys4AinFile = sys4AinFile,
            isSys3x = isSys3x
        )
        recoverInterruptedReinstalls(storageCanonical)
        val dstDir = resolveBaseInstallDestination(storageCanonical, dirBaseName, canonicalBaseUrl)
        if (dstDir.parentFile != storageCanonical) {
            throw IOException("Destination directory escapes storage path: $dstDir")
        }
        if (dstDir.exists()) {
            replaceKeepingUserFiles(stagingDir, dstDir)
        } else if (!moveFileOrDir(stagingDir, dstDir)) {
            throw IOException("Failed to finalize installed game directory: $stagingDir -> $dstDir")
        }
        File(dstDir, BASE_STAGING_IDENTITY_FILE).delete()
        try {
            File(dstDir, ONLINE_INSTALL_SOURCE_FILE).writeText(canonicalBaseUrl)
        } catch (_: Throwable) {
            // Only costs in-place refresh on the next re-trigger; the install itself is complete.
        }
        return dstDir
    }

    /**
     * Picks the directory a finished base-game staging dir becomes. A directory previously
     * online-installed from the same [sourceUrl] (re-triggered install) is reused so it is
     * refreshed in place instead of duplicated as "<Name>-N"; manually imported or other
     * copies are never touched and the first free "<Name>-N" name is used instead.
     */
    internal fun resolveBaseInstallDestination(storageDir: File, dirBaseName: String, sourceUrl: String): File {
        var dstDir = File(storageDir, dirBaseName).canonicalFile
        var suffix = 1
        while (dstDir.exists()) {
            if (dstDir.isDirectory && readOnlineInstallSource(dstDir) == sourceUrl) return dstDir
            dstDir = File(storageDir, "$dirBaseName-${suffix++}").canonicalFile
        }
        return dstDir
    }

    private fun readOnlineInstallSource(dir: File): String? = try {
        File(dir, ONLINE_INSTALL_SOURCE_FILE).takeIf { it.isFile }?.readText()?.trim()?.let {
            AppSettings.canonicalCdnUrl(it)
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Replaces [dst] with [staging], carrying over files only [dst] has (saves, settings).
     * [dst] stays untouched until the complete new tree is ready, then the two are swapped by
     * renames; a failure at any point leaves the previous installation playable.
     */
    internal fun replaceKeepingUserFiles(staging: File, dst: File) {
        val copied = ArrayList<File>()
        val backup = File(dst.parentFile, REINSTALL_BACKUP_PREFIX + dst.name)
        try {
            if (!copyMissingInto(dst, staging, copied)) {
                throw IOException("Failed to carry existing files into $staging")
            }
            if (backup.exists() && !backup.deleteRecursively()) {
                throw IOException("Cannot clear stale reinstall backup: $backup")
            }
            if (!dst.renameTo(backup)) {
                throw IOException("Cannot move existing installation aside: $dst")
            }
        } catch (t: Throwable) {
            for (f in copied.asReversed()) f.deleteRecursively()
            throw t
        }
        if (!staging.renameTo(dst)) {
            // If this restore fails too, recoverInterruptedReinstalls() retries it later.
            backup.renameTo(dst)
            for (f in copied.asReversed()) f.deleteRecursively()
            throw IOException("Failed to finalize installed game directory: $staging -> $dst")
        }
        backup.deleteRecursively()
    }

    /** Copies entries of [src] that [dst] lacks into [dst], recording each created top entry. */
    private fun copyMissingInto(src: File, dst: File, copied: MutableList<File>): Boolean {
        val children = src.listFiles() ?: return false
        for (child in children) {
            if (child.name == ONLINE_INSTALL_SOURCE_FILE) continue
            val target = File(dst, child.name)
            if (target.exists()) {
                if (child.isDirectory && target.isDirectory && !copyMissingInto(child, target, copied)) {
                    return false
                }
                continue
            }
            copied.add(target)
            if (!copyRecursivelyVerified(child, target)) return false
        }
        return true
    }

    /**
     * Undoes a reinstall swap cut short between moving the old installation aside and moving
     * the new one in, and drops backups whose swap completed.
     */
    fun recoverInterruptedReinstalls(storageDir: File) {
        val children = storageDir.listFiles() ?: return
        for (backup in children) {
            if (!backup.isDirectory || !backup.name.startsWith(REINSTALL_BACKUP_PREFIX)) continue
            val original = File(storageDir, backup.name.removePrefix(REINSTALL_BACKUP_PREFIX))
            if (original.exists()) {
                backup.deleteRecursively()
            } else {
                backup.renameTo(original)
            }
        }
    }

    fun computeBaseStagingIdentity(cleanBaseUrl: String, manifest: OnlineManifest): String {
        val sb = StringBuilder("v1\n")
        sb.append(cleanBaseUrl).append('\n')
        sb.append(manifest.baseName).append('\n')
        for (e in manifest.entries) {
            sb.append(e.path).append('\t')
                .append(e.key).append('\t')
                .append(e.size).append('\t')
                .append(e.sha256.lowercase(Locale.ROOT)).append('\t')
                .append(e.extract).append('\n')
        }
        return sha256Hex(sb.toString().toByteArray(Charsets.UTF_8))
    }

    /**
     * Staging contents are only reused when they were produced for the exact same source and
     * manifest; anything else (including legacy staging without an identity) is discarded, since
     * files from an older manifest cannot be told apart from current ones after extraction.
     */
    fun prepareBaseStagingDir(stagingDir: File, identity: String) {
        val identityFile = File(stagingDir, BASE_STAGING_IDENTITY_FILE)
        if (stagingDir.exists()) {
            val saved = try {
                if (identityFile.isFile) identityFile.readText().trim() else null
            } catch (_: Throwable) {
                null
            }
            val hasContent = !stagingDir.isDirectory || stagingDir.listFiles()?.isNotEmpty() == true
            if (saved != identity && hasContent) {
                val trashed = evictFileToTrash(stagingDir)
                trashed?.deleteRecursively()
                if (stagingDir.exists()) {
                    throw IOException("Cannot reset stale staging directory: $stagingDir")
                }
            }
        }
        stagingDir.mkdirs()
        if (!stagingDir.isDirectory) {
            throw IOException("Cannot create staging directory: $stagingDir")
        }
        identityFile.writeText(identity)
    }

    private fun findPrimarySys4AinFile(stagingDir: File): File? {
        val files = stagingDir.listFiles()?.filter { it.isFile }.orEmpty()
        val names = files.map { it.name }
        val readSmall: (String) -> ByteArray? = { GameCatalog.readSmallRootFile(stagingDir, it) }
        return files.firstOrNull {
            !it.name.startsWith('.') && GameCatalog.isSystem4AinFile(it.name)
        } ?: files.firstOrNull {
            GameCatalog.isSystem4AinFile(it.name)
        } ?: files.firstOrNull {
            !it.name.startsWith('.') && GameCatalog.isSystem4CodeFile(it.name, names, readSmall)
        } ?: files.firstOrNull {
            GameCatalog.isSystem4CodeFile(it.name, names, readSmall)
        }
    }

    @JvmOverloads
    fun resolveInstallDirBaseName(
        stagingDir: File,
        manifestBaseName: String = "",
        sys4AinFile: File? = findPrimarySys4AinFile(stagingDir),
        isSys3x: Boolean = GameCatalog.isSystem3xGameDir(stagingDir)
    ): String {
        val rawAinBase = sys4AinFile?.let { ArchiveExtractor.repairSjisAsGbkMojibake(it.nameWithoutExtension) }.orEmpty()
        val sanitizedManifestBase = sanitizeInstallDirName(manifestBaseName, fallback = "")
        val sanitizedAinBase = sanitizeInstallDirName(rawAinBase, fallback = "")
        val classifiedStagedId = GameCatalog.classifyGameRootDir(stagingDir)
        return when {
            classifiedStagedId == GameCatalog.ID_RANCE04 -> sanitizedManifestBase.ifEmpty { "Rance04" }
            classifiedStagedId == GameCatalog.ID_RANCE41 -> sanitizedManifestBase.ifEmpty { "Rance41" }
            classifiedStagedId == GameCatalog.ID_RANCE42 -> sanitizedManifestBase.ifEmpty { "Rance42" }
            classifiedStagedId == GameCatalog.ID_RANCE5D -> sanitizedManifestBase.ifEmpty { "Rance5D" }
            classifiedStagedId == GameCatalog.ID_RANCE06 -> sanitizedManifestBase.ifEmpty { "Rance06" }
            classifiedStagedId == GameCatalog.ID_GALZOO -> sanitizedManifestBase.ifEmpty { "GALZOO" }
            classifiedStagedId == GameCatalog.ID_HARUKA -> sanitizedManifestBase.ifEmpty { "Haruka" }
            classifiedStagedId == GameCatalog.ID_TSUMASHIBORI -> sanitizedManifestBase.ifEmpty { "Tsumashibori" }
            classifiedStagedId == GameCatalog.ID_TSUMAMIGUI -> sanitizedManifestBase.ifEmpty { "Tsumamigui" }
            classifiedStagedId == GameCatalog.ID_TSUMAMIGUI3 -> sanitizedManifestBase.ifEmpty { "Tsumamigui3" }
            classifiedStagedId == GameCatalog.ID_DAIBANCHOU -> sanitizedManifestBase.ifEmpty { "Daibanchou" }
            classifiedStagedId == GameCatalog.ID_DAITEIKOKU -> sanitizedManifestBase.ifEmpty { "Daiteikoku" }
            classifiedStagedId == GameCatalog.ID_KAERUNYO -> sanitizedManifestBase.ifEmpty { "Kaerunyo" }
            classifiedStagedId == GameCatalog.ID_ATLACHNACHA -> sanitizedManifestBase.ifEmpty { "AtlachNacha" }
            classifiedStagedId == GameCatalog.ID_REISHIKI -> sanitizedManifestBase.ifEmpty { "Reishiki" }
            classifiedStagedId == GameCatalog.ID_HARUURARE -> sanitizedManifestBase.ifEmpty { "Haruurare" }
            classifiedStagedId == GameCatalog.ID_OKUSAMAJUN -> sanitizedManifestBase.ifEmpty { "OkusamaJun" }
            classifiedStagedId == GameCatalog.ID_OKUSAMANTR -> sanitizedManifestBase.ifEmpty { "OkusamaNtr" }
            classifiedStagedId == GameCatalog.ID_EVENICLE2RANCE -> sanitizedManifestBase.ifEmpty { "Evenicle2Rance" }
            classifiedStagedId == GameCatalog.ID_EVENICLEGAIDEN -> sanitizedManifestBase.ifEmpty { "EvenicleGaiden" }
            classifiedStagedId == GameCatalog.ID_WAIDONYO -> sanitizedManifestBase.ifEmpty { "Waidonyo" }
            classifiedStagedId == GameCatalog.ID_RANCE6AFTER -> sanitizedManifestBase.ifEmpty { "Rance6After" }
            classifiedStagedId == GameCatalog.ID_YAKATA2 -> sanitizedManifestBase.ifEmpty { "Yakata2" }
            classifiedStagedId == GameCatalog.ID_SDPSMARIA -> sanitizedManifestBase.ifEmpty { "SuperDpsMaria" }
            classifiedStagedId == GameCatalog.ID_TOUSHIN3 -> sanitizedManifestBase.ifEmpty { "Toushin3" }
            classifiedStagedId == GameCatalog.ID_OYAKORANKAN -> sanitizedManifestBase.ifEmpty { "Oyakorankan" }
            classifiedStagedId == GameCatalog.ID_YORUGAKURU -> sanitizedManifestBase.ifEmpty { "Yorugakuru" }
            classifiedStagedId == GameCatalog.ID_RANCE1DIGEST -> sanitizedManifestBase.ifEmpty { "Rance1Digest" }
            classifiedStagedId == GameCatalog.ID_MAMAN -> sanitizedManifestBase.ifEmpty { "Maman" }
            classifiedStagedId == GameCatalog.ID_EVENICLERANCE -> sanitizedManifestBase.ifEmpty { "EvenicleRance" }
            classifiedStagedId == GameCatalog.ID_HAHARANMAN -> sanitizedManifestBase.ifEmpty { "Haharanman" }
            classifiedStagedId == GameCatalog.ID_TSUMAMIGUI2 -> sanitizedManifestBase.ifEmpty { "Tsumamigui2" }
            classifiedStagedId == GameCatalog.ID_TSUMAMIGUI3JUN -> sanitizedManifestBase.ifEmpty { "Tsumamigui3Jun" }
            classifiedStagedId == GameCatalog.ID_TSUMAMIGUI3NTR -> sanitizedManifestBase.ifEmpty { "Tsumamigui3Ntr" }
            classifiedStagedId == GameCatalog.ID_DIABOLIQUE -> sanitizedManifestBase.ifEmpty { "DiaboLiQuE" }
            classifiedStagedId == GameCatalog.ID_GAKUENKING -> sanitizedManifestBase.ifEmpty { "GakuenKING" }
            classifiedStagedId == GameCatalog.ID_GAKUENKINGEX -> sanitizedManifestBase.ifEmpty { "GakuenKINGEx" }
            classifiedStagedId == GameCatalog.ID_OUDOUYUUSHA -> sanitizedManifestBase.ifEmpty { "OudouYuusha" }
            classifiedStagedId == GameCatalog.ID_TOUSHIN1 -> sanitizedManifestBase.ifEmpty { "Toushin1" }
            classifiedStagedId == GameCatalog.ID_TOUSHIN2 -> sanitizedManifestBase.ifEmpty { "Toushin2" }
            classifiedStagedId == GameCatalog.ID_DARCROWS -> sanitizedManifestBase.ifEmpty { "DARCROWS" }
            classifiedStagedId == GameCatalog.ID_IKUSAMIKO -> sanitizedManifestBase.ifEmpty { "IkusaMiko" }
            classifiedStagedId == GameCatalog.ID_ONLYYOU -> sanitizedManifestBase.ifEmpty { "OnlyYou" }
            classifiedStagedId == GameCatalog.ID_SANBIKI -> sanitizedManifestBase.ifEmpty { "Sanbiki" }
            classifiedStagedId == GameCatalog.ID_PERSIOM -> sanitizedManifestBase.ifEmpty { "PERSIOM" }
            classifiedStagedId == GameCatalog.ID_SEEINAO -> sanitizedManifestBase.ifEmpty { "SeeInAO" }
            classifiedStagedId == GameCatalog.ID_HUSHABY -> sanitizedManifestBase.ifEmpty { "HushabyBaby" }
            classifiedStagedId == GameCatalog.ID_AYUMI -> sanitizedManifestBase.ifEmpty { "AyumiChan" }
            classifiedStagedId == GameCatalog.ID_CIELCLAIRE -> sanitizedManifestBase.ifEmpty { "CielClaire" }
            classifiedStagedId == GameCatalog.ID_NIGHTDEMON -> sanitizedManifestBase.ifEmpty { "NightDemon" }
            classifiedStagedId == GameCatalog.ID_KATSUMI -> sanitizedManifestBase.ifEmpty { "Katsumi" }
            classifiedStagedId == GameCatalog.ID_DRSTOP -> sanitizedManifestBase.ifEmpty { "DrStop" }
            classifiedStagedId == GameCatalog.ID_DPSALL -> sanitizedManifestBase.ifEmpty { "DpsAll" }
            classifiedStagedId == GameCatalog.ID_KOREDPS -> sanitizedManifestBase.ifEmpty { "KoreDps" }
            classifiedStagedId == GameCatalog.ID_KAERUALICE -> sanitizedManifestBase.ifEmpty { "KaeruAlice" }
            classifiedStagedId == GameCatalog.ID_PROSTUDENTG -> sanitizedManifestBase.ifEmpty { "ProstudentG" }
            classifiedStagedId == GameCatalog.ID_PROSTUDENTGOOD -> sanitizedManifestBase.ifEmpty { "ProstudentGood" }
            classifiedStagedId == GameCatalog.ID_PASTELCHIME -> sanitizedManifestBase.ifEmpty { "PastelChime" }
            classifiedStagedId == GameCatalog.ID_PASTELCHIME2 -> sanitizedManifestBase.ifEmpty { "PastelChime2" }
            classifiedStagedId == GameCatalog.ID_YAKATA3 -> sanitizedManifestBase.ifEmpty { "Yakata3" }
            classifiedStagedId == GameCatalog.ID_DALK -> sanitizedManifestBase.ifEmpty { "DALK" }
            classifiedStagedId == GameCatalog.ID_NINGENGARI -> sanitizedManifestBase.ifEmpty { "NingenGari" }
            classifiedStagedId == GameCatalog.ID_PASCHACPP -> sanitizedManifestBase.ifEmpty { "PaschaCpp" }
            classifiedStagedId == GameCatalog.ID_PASCHA3 -> sanitizedManifestBase.ifEmpty { "Pascha3" }
            classifiedStagedId == GameCatalog.ID_MAMATOTO -> sanitizedManifestBase.ifEmpty { "Mamatoto" }
            classifiedStagedId == GameCatalog.ID_ONLYYOURECROSS -> sanitizedManifestBase.ifEmpty { "OnlyYouReCross" }
            classifiedStagedId == GameCatalog.ID_MAMORIGAMI -> sanitizedManifestBase.ifEmpty { "Mamorigami" }
            classifiedStagedId == GameCatalog.ID_AMBIVALENZ -> sanitizedManifestBase.ifEmpty { "AmbivalenZ" }
            classifiedStagedId == GameCatalog.ID_FUNNYBEE -> sanitizedManifestBase.ifEmpty { "FunnyBee" }
            classifiedStagedId == GameCatalog.ID_TOUSHIN2SP -> sanitizedManifestBase.ifEmpty { "Toushin2Sp" }
            classifiedStagedId == GameCatalog.ID_MUGENHOUYOU -> sanitizedManifestBase.ifEmpty { "MugenHouyou" }
            classifiedStagedId == GameCatalog.ID_OTOMESENKI -> sanitizedManifestBase.ifEmpty { "OtomeSenki" }
            classifiedStagedId == GameCatalog.ID_GAKUENHYOURYUU -> sanitizedManifestBase.ifEmpty { "GakuenHyouryuu" }
            classifiedStagedId == GameCatalog.ID_MAIDNOSUSUME -> sanitizedManifestBase.ifEmpty { "MaidNoSusume" }
            classifiedStagedId == GameCatalog.ID_DALKGAIDEN -> sanitizedManifestBase.ifEmpty { "DalkGaiden" }
            classifiedStagedId == GameCatalog.ID_MAMANYONYO -> sanitizedManifestBase.ifEmpty { "Mamanyonyo" }
            classifiedStagedId == GameCatalog.ID_MAJONOSHOKUZAI -> sanitizedManifestBase.ifEmpty { "MajoNoShokuzai" }
            classifiedStagedId == GameCatalog.ID_SABOTEN -> sanitizedManifestBase.ifEmpty { "Saboten" }
            classifiedStagedId == GameCatalog.ID_DOUBLESENSEI -> sanitizedManifestBase.ifEmpty { "DoubleSensei" }
            classifiedStagedId == GameCatalog.ID_OJOUSAMA -> sanitizedManifestBase.ifEmpty { "Ojousama" }
            classifiedStagedId == GameCatalog.ID_EDORANZE -> sanitizedManifestBase.ifEmpty { "Edoranze" }
            classifiedStagedId == GameCatalog.ID_VANISH -> sanitizedManifestBase.ifEmpty { "Vanish" }
            classifiedStagedId == GameCatalog.ID_HOKENSHITSU -> sanitizedManifestBase.ifEmpty { "Hokenshitsu" }
            classifiedStagedId == GameCatalog.ID_KAMUNAGARA -> sanitizedManifestBase.ifEmpty { "Kamunagara" }
            classifiedStagedId == GameCatalog.ID_ERUMEIFLOWER -> sanitizedManifestBase.ifEmpty { "ErumeiFlower" }
            classifiedStagedId == GameCatalog.ID_ERUMEIWAKARE -> sanitizedManifestBase.ifEmpty { "ErumeiWakare" }
            classifiedStagedId == GameCatalog.ID_MANANA1 -> sanitizedManifestBase.ifEmpty { "Manana1" }
            classifiedStagedId == GameCatalog.ID_MANANA2 -> sanitizedManifestBase.ifEmpty { "Manana2" }
            classifiedStagedId == GameCatalog.ID_MANANA3 -> sanitizedManifestBase.ifEmpty { "Manana3" }
            classifiedStagedId == GameCatalog.ID_MANANA4 -> sanitizedManifestBase.ifEmpty { "Manana4" }
            classifiedStagedId == GameCatalog.ID_MANANA5 -> sanitizedManifestBase.ifEmpty { "Manana5" }
            classifiedStagedId == GameCatalog.ID_TSUMASAKURA -> sanitizedManifestBase.ifEmpty { "TsumaSakura" }
            classifiedStagedId == GameCatalog.ID_SHAMANSSANCTUARY -> sanitizedManifestBase.ifEmpty { "ShamansSanctuary" }
            classifiedStagedId == GameCatalog.ID_ALIVEZ -> sanitizedManifestBase.ifEmpty { "AliveZ" }
            classifiedStagedId == GameCatalog.ID_DUNGEONSDOLLS -> sanitizedManifestBase.ifEmpty { "DungeonsDolls" }
            classifiedStagedId == GameCatalog.ID_SUZUMIYA -> sanitizedManifestBase.ifEmpty { "Suzumiya" }
            classifiedStagedId == GameCatalog.ID_SHIMAIMA -> sanitizedManifestBase.ifEmpty { "Shimaima" }
            classifiedStagedId == GameCatalog.ID_DAIBANCHOUGAIDEN -> sanitizedManifestBase.ifEmpty { "DaibanchouGaiden" }
            classifiedStagedId == GameCatalog.ID_COSPLAYLEMON -> sanitizedManifestBase.ifEmpty { "CosplayLemon" }
            classifiedStagedId == GameCatalog.ID_WHIPHONEY -> sanitizedManifestBase.ifEmpty { "WhipHoney" }
            classifiedStagedId == GameCatalog.ID_ONIMUSU -> sanitizedManifestBase.ifEmpty { "Onimusu" }
            classifiedStagedId == GameCatalog.ID_PROGRX -> sanitizedManifestBase.ifEmpty { "ProGrx" }
            classifiedStagedId == GameCatalog.ID_DPSVERN -> sanitizedManifestBase.ifEmpty { "DpsVerN" }
            classifiedStagedId == GameCatalog.ID_SUKIIRONO -> sanitizedManifestBase.ifEmpty { "Sukiirono" }
            classifiedStagedId == GameCatalog.ID_GIRLS -> sanitizedManifestBase.ifEmpty { "Girls" }
            classifiedStagedId == GameCatalog.ID_DEBUT -> sanitizedManifestBase.ifEmpty { "Debut" }
            classifiedStagedId == GameCatalog.ID_HARUKAVSESCA -> sanitizedManifestBase.ifEmpty { "HarukaVsEsca" }
            classifiedStagedId == GameCatalog.ID_HARUKANINJA -> sanitizedManifestBase.ifEmpty { "HarukaNinja" }
            classifiedStagedId == GameCatalog.ID_MEGANE -> sanitizedManifestBase.ifEmpty { "Megane" }
            classifiedStagedId == GameCatalog.ID_RACHELSAKURA -> sanitizedManifestBase.ifEmpty { "RachelSakura" }
            classifiedStagedId == GameCatalog.ID_SABAKUNIKA -> sanitizedManifestBase.ifEmpty { "SabakuNika" }
            classifiedStagedId == GameCatalog.ID_VANISHPALM -> sanitizedManifestBase.ifEmpty { "VanishPalm" }
            sanitizedManifestBase.isNotEmpty() &&
                (isSys3x || sanitizedAinBase.isEmpty() || sanitizedAinBase.any { it.code >= 0x80 }) ->
                sanitizedManifestBase
            else -> sanitizedAinBase.ifEmpty { sanitizedManifestBase.ifEmpty { "Game" } }
        }
    }

    fun sanitizeInstallDirName(raw: String, fallback: String = "Game"): String {
        var cleaned = raw.filter { it != '/' && it != '\\' && it.code >= 0x20 }
        cleaned = cleaned.trim { it.isWhitespace() || it == '.' }
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_")
        }
        cleaned = cleaned.trim { it.isWhitespace() || it == '.' }
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback else cleaned
    }

    fun moveFileOrDir(src: File, dst: File): Boolean {
        if (!src.exists()) return false
        return try {
            val srcCanonical = src.canonicalFile
            val dstCanonical = dst.canonicalFile
            if (srcCanonical == dstCanonical) return true
            dstCanonical.parentFile?.mkdirs()
            if (src.renameTo(dst)) {
                return dst.exists()
            }
            if (dst.exists() && !dst.deleteRecursively()) {
                return false
            }
            if (!copyRecursivelyVerified(src, dst)) {
                dst.deleteRecursively()
                return false
            }
            if (!src.deleteRecursively() || src.exists()) {
                return false
            }
            dst.exists()
        } catch (_: Throwable) {
            try {
                if (src.exists() && dst.exists() && src.canonicalFile != dst.canonicalFile) {
                    dst.deleteRecursively()
                }
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun copyRecursivelyVerified(src: File, dst: File): Boolean {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) return false
            val children = src.listFiles() ?: return false
            for (child in children) {
                if (!copyRecursivelyVerified(child, File(dst, child.name))) {
                    return false
                }
            }
            return dst.isDirectory
        } else if (src.isFile) {
            dst.parentFile?.mkdirs()
            val expectedLen = src.length()
            FileInputStream(src).use { input ->
                FileOutputStream(dst, false).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) output.write(buf, 0, n)
                    }
                    output.fd.sync()
                }
            }
            return dst.isFile && dst.length() == expectedLen
        }
        return false
    }

    fun downloadAndExtractMod(
        context: Context,
        req: ModDownloadRequest,
        storagePath: File,
        tempExtractDir: File,
        stepPrefix: String = "",
        releaseLabel: String? = null,
        abortHandle: InstallAbortHandle? = null,
        checkCancelled: () -> Unit,
        onProgress: (OnlineProgressUpdate) -> Unit,
        applyStaging: (File) -> Unit
    ) {
        val cacheDir = File(storagePath, ".online_mod_cache")
        cacheDir.mkdirs()
        val effectiveUrl = AppSettings.resolveCdnUrl(context, req.url)
        val cacheId = req.expectedSha256?.takeIf { it.length >= 16 }?.take(16)
            ?: sha256Hex(AppSettings.canonicalCdnUrl(req.url).toByteArray(Charsets.UTF_8)).take(16)
        val ext = extractArchiveExtension(req.fileName)
        val partFile = File(cacheDir, "mod_${cacheId}${ext}.part")
        val metaFile = File("${partFile.path}.meta")

        val headers = mapOf("User-Agent" to DEFAULT_USER_AGENT)

        val rawName = req.fileName.ifBlank { "MOD$ext" }
        val displayName = if (!releaseLabel.isNullOrBlank() && !rawName.contains(releaseLabel)) {
            "$releaseLabel · $rawName"
        } else {
            rawName
        }

        val reportProgress: (Long, Long, Long) -> Unit = { doneBytes, totalBytes, speedBps ->
            val speedStr = formatSpeed(speedBps)
            val doneMb = doneBytes / (1024L * 1024L)
            if (totalBytes > 0L) {
                val permille = ((doneBytes * 1000L) / totalBytes).toInt().coerceIn(0, 1000)
                val pct = permille / 10
                val totalMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                val msg = stepPrefix + context.getString(
                    R.string.online_mod_download_progress,
                    pct,
                    speedStr,
                    doneMb,
                    totalMb,
                    displayName
                )
                onProgress(OnlineProgressUpdate(msg, permille))
            } else {
                val msg = stepPrefix + context.getString(
                    R.string.online_mod_download_progress_indeterminate,
                    doneMb,
                    speedStr,
                    displayName
                )
                onProgress(OnlineProgressUpdate(msg, null))
            }
        }

        downloadFileWithResume(
            url = effectiveUrl,
            destPartFile = partFile,
            expectedSize = req.contentLength.takeIf { it > 0L } ?: -1L,
            expectedSha256 = req.expectedSha256,
            headers = headers,
            abortHandle = abortHandle,
            checkCancelled = checkCancelled,
            onProgress = reportProgress,
            maxConnections = AppSettings.getDownloadConcurrency(context)
        )

        var extractedOk = false
        try {
            tempExtractDir.deleteRecursively()
            ArchiveExtractor.extractFileToDirectory(
                archiveFile = partFile,
                destDir = tempExtractDir,
                shouldSkip = { rel -> NativeGameImport.skip(rel) },
                throwInstallFailureOnNoSpace = true,
                checkCancelled = checkCancelled,
                onProgress = { extractedBytes, entryPath ->
                    val msg = stepPrefix + context.getString(
                        R.string.import_extract_progress,
                        extractedBytes / (1024L * 1024L),
                        entryPath
                    )
                    onProgress(OnlineProgressUpdate(msg, null))
                }
            )
            extractedOk = true
            applyStaging(tempExtractDir)
            partFile.delete()
            metaFile.delete()
            if (cacheDir.listFiles()?.isEmpty() == true) {
                cacheDir.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GameList.InstallFailureException) {
            if (!extractedOk) {
                partFile.delete()
                metaFile.delete()
            }
            throw e
        } catch (e: Exception) {
            if (!extractedOk) {
                partFile.delete()
                metaFile.delete()
            }
            throw e
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    private fun verifyStagedEntryHash(
        file: File,
        entry: ManifestEntry,
        verifiedCache: MutableSet<String>,
        checkCancelled: () -> Unit
    ): Boolean {
        if (!file.isFile || file.length() != entry.size) {
            return false
        }
        if (entry.sha256.isBlank()) {
            return false
        }
        val cacheKey = "${file.path}:${file.length()}:${file.lastModified()}:${entry.sha256.lowercase(Locale.ROOT)}"
        if (cacheKey in verifiedCache) {
            return true
        }
        val actualSha = computeFileSha256(file, checkCancelled)
        return if (actualSha.equals(entry.sha256, ignoreCase = true)) {
            verifiedCache.add(cacheKey)
            true
        } else {
            file.delete()
            false
        }
    }

    private fun isManifestEntryDone(
        stagingDir: File,
        stagingPrefix: String,
        entry: ManifestEntry,
        verifiedCache: MutableSet<String>,
        checkCancelled: () -> Unit
    ): Boolean {
        if (entry.extract) {
            val marker = markerFileForExtractEntry(stagingDir, entry)
            return marker.isFile && marker.readText().trim().equals(entry.sha256, ignoreCase = true)
        }
        val cleanPath = ArchiveExtractor.repairSjisAsGbkMojibake(entry.path)
        val relParts = cleanPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (relParts.isEmpty() || relParts.any { it == ".." }) return false
        val f = File(stagingDir, relParts.joinToString(File.separator)).canonicalFile
        if (!f.path.startsWith(stagingPrefix)) return false
        if (f.isFile) {
            if (verifyStagedEntryHash(f, entry, verifiedCache, checkCancelled)) {
                return true
            }
            if (f.exists() && f.length() != entry.size) {
                f.delete()
            }
        }
        if (cleanPath != entry.path) {
            val rawParts = entry.path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (rawParts.isNotEmpty() && rawParts.none { it == ".." }) {
                val rawFile = File(stagingDir, rawParts.joinToString(File.separator)).canonicalFile
                if (rawFile.path.startsWith(stagingPrefix) && rawFile.isFile) {
                    if (verifyStagedEntryHash(rawFile, entry, verifiedCache, checkCancelled)) {
                        f.parentFile?.mkdirs()
                        if (!moveFileOrDir(rawFile, f)) {
                            return false
                        }
                        if (entry.sha256.isNotBlank()) {
                            verifiedCache.add(
                                "${f.path}:${f.length()}:${f.lastModified()}:${entry.sha256.lowercase(Locale.ROOT)}"
                            )
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun markerFileForExtractEntry(stagingDir: File, entry: ManifestEntry): File {
        val safe = entry.path.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        return File(stagingDir, ".extracted_${safe}.done")
    }

    fun evictFileToTrash(target: File?): File? {
        if (target == null || !target.exists()) return null
        val parent = target.parentFile ?: return target
        val trash = File(parent, ".trash_${System.nanoTime()}_${(Math.random() * 100000).toInt()}")
        return if (target.renameTo(trash)) trash else target
    }

    fun evictBaseStagingToTrash(
        storagePath: File,
        targetGameId: String?,
        resolvedStagingDir: File?
    ): List<File> {
        val out = ArrayList<File>()
        try {
            val storageCanonical = storagePath.canonicalFile
            if (resolvedStagingDir != null &&
                resolvedStagingDir.canonicalFile.parentFile == storageCanonical &&
                resolvedStagingDir.exists()
            ) {
                evictFileToTrash(resolvedStagingDir)?.let { out.add(it) }
            }
            val children = storageCanonical.listFiles() ?: return out
            for (child in children) {
                if (!child.isDirectory || !child.name.startsWith(".online_base_staging_")) continue
                val lower = child.name.lowercase(Locale.ROOT)
                val matches = when (targetGameId) {
                    GameCatalog.ID_RANCE10 -> lower.contains("rance10") || lower.contains("rance_10")
                    GameCatalog.ID_DOHNA_DOHNA -> lower.contains("dohna")
                    GameCatalog.ID_EVENICLE -> lower.contains("evenicle") &&
                        !lower.contains("evenicle2") &&
                        !lower.contains("evenicle_2") &&
                        !lower.contains("rance") &&
                        !lower.contains("gaiden") &&
                        !lower.contains("gaiten") &&
                        !lower.contains("evenicleapc")
                    GameCatalog.ID_EVENICLERANCE -> (lower.contains("eveniclerance") ||
                        lower.contains("evenicle_rance") ||
                        (lower.contains("evenicle") && !lower.contains("evenicle2") && lower.contains("rance")))
                    GameCatalog.ID_EVENICLE2 -> (lower.contains("evenicle2") || lower.contains("evenicle_2")) &&
                        !lower.contains("rance")
                    GameCatalog.ID_EVENICLE2RANCE -> lower.contains("evenicle2rance") ||
                        lower.contains("evenicle2_rance") ||
                        (lower.contains("evenicle2") && lower.contains("rance"))
                    GameCatalog.ID_EVENICLEGAIDEN -> lower.contains("eveniclegaiden") ||
                        lower.contains("evenicle_gaiden") ||
                        lower.contains("eveniclegaiten") ||
                        lower.contains("evenicleapc") ||
                        lower.contains("夏娃年代记外典") ||
                        lower.contains("イブニクル外典")
                    GameCatalog.ID_SENGOKU_RANCE -> lower.contains("sengokurance") &&
                        !lower.contains("sanbiki") &&
                        !lower.contains("三匹")
                    GameCatalog.ID_SANBIKI -> lower.contains("sanbiki") ||
                        lower.contains("三匹")
                    GameCatalog.ID_RANCE01 -> (lower.contains("rance01") || lower.contains("rance_01")) &&
                        !lower.contains("digest") &&
                        !lower.contains("摘要")
                    GameCatalog.ID_RANCE1DIGEST -> lower.contains("rance1digest") ||
                        lower.contains("rance1_digest") ||
                        lower.contains("rance01digest") ||
                        lower.contains("兰斯1摘要") ||
                        lower.contains("兰斯１摘要") ||
                        lower.contains("ランス1ダイジェスト") ||
                        lower.contains("ランス１ダイジェスト")
                    GameCatalog.ID_RANCE02 -> lower.contains("rance02") || lower.contains("rance_02")
                    GameCatalog.ID_RANCE03 -> lower.contains("rance03") || lower.contains("rance_03")
                    GameCatalog.ID_RANCE04 -> lower.contains("rance04") || lower.contains("rance_04") ||
                        Regex("(?<![a-z0-9])rance_?(?:4|iv)(?![0-9])").containsMatchIn(lower)
                    GameCatalog.ID_RANCE41 -> lower.contains("rance41") || lower.contains("rance_41") ||
                        lower.contains("rance4.1") || lower.contains("rance_4_1")
                    GameCatalog.ID_RANCE42 -> lower.contains("rance42") || lower.contains("rance_42") ||
                        lower.contains("rance4.2") || lower.contains("rance_4_2")
                    GameCatalog.ID_RANCE5D -> lower.contains("rance5d") || lower.contains("rance_5d")
                    GameCatalog.ID_RANCE06 -> (lower.contains("rance06") || lower.contains("rance_06") ||
                        Regex("(?<![a-z0-9])rance_?6(?![0-9])").containsMatchIn(lower)) &&
                        !lower.contains("after") &&
                        !lower.contains("sonogo") &&
                        !lower.contains("その後") &&
                        !lower.contains("后日")
                    GameCatalog.ID_RANCE6AFTER -> lower.contains("rance6after") ||
                        lower.contains("rance6_after") ||
                        lower.contains("rance06after") ||
                        lower.contains("rancevisonogo") ||
                        lower.contains("ランス６その後") ||
                        lower.contains("ランス6その後") ||
                        lower.contains("兰斯06后日谈") ||
                        lower.contains("兰斯6后日谈")
                    GameCatalog.ID_RANCE08 -> lower.contains("rance08") || lower.contains("rance_08")
                    GameCatalog.ID_RANCE09 -> lower.contains("rance09") || lower.contains("rance_09")
                    GameCatalog.ID_KICHIKUOU -> lower.contains("kichiku")
                    GameCatalog.ID_DAIAKUJI -> lower.contains("daiakuji") ||
                        lower.contains("大悪司") ||
                        lower.contains("大恶司")
                    GameCatalog.ID_DAIBANCHOUGAIDEN -> lower.contains("daibanchougaiden") ||
                        lower.contains("daibanchou_gaiden") ||
                        lower.contains("大番長外伝") ||
                        lower.contains("大番长外传") ||
                        lower.contains("大番長外傳")
                    GameCatalog.ID_DAIBANCHOU -> (lower.contains("daibanchou") ||
                        lower.contains("大番長") ||
                        lower.contains("大番长")) &&
                        !lower.contains("gaiden") &&
                        !lower.contains("外伝") &&
                        !lower.contains("外传") &&
                        !lower.contains("外傳")
                    GameCatalog.ID_DAITEIKOKU -> lower.contains("daiteikoku") ||
                        lower.contains("大帝国") ||
                        lower.contains("大帝國")
                    GameCatalog.ID_KAERUNYO -> (lower.contains("kaerunyo") ||
                        lower.contains("かえるにょ") ||
                        lower.contains("ぱにょ")) &&
                        !lower.contains("kaerualice") &&
                        !lower.contains("国にょ") &&
                        !lower.contains("アリス") &&
                        !lower.contains("蛙国")
                    GameCatalog.ID_KAERUALICE -> lower.contains("kaerualice") ||
                        lower.contains("kaeru_alice") ||
                        lower.contains("国にょアリス") ||
                        lower.contains("蛙国爱丽丝")
                    GameCatalog.ID_ATLACHNACHA -> lower.contains("atlach") ||
                        lower.contains("アトラク")
                    GameCatalog.ID_REISHIKI -> lower.contains("reishiki") ||
                        lower.contains("零式")
                    GameCatalog.ID_HARUURARE -> lower.contains("haruurare") ||
                        lower.contains("はるうられ") ||
                        lower.contains("校内赤线")
                    GameCatalog.ID_OKUSAMAJUN -> lower.contains("okusamajun") ||
                        lower.contains("okusama_jun") ||
                        ((lower.contains("okusama") || lower.contains("太太的恢复术") || lower.contains("奥さま")) &&
                            (lower.contains("jun") || lower.contains("纯爱") || lower.contains("純愛")))
                    GameCatalog.ID_OKUSAMANTR -> lower.contains("okusamantr") ||
                        lower.contains("okusama_ntr") ||
                        ((lower.contains("okusama") || lower.contains("太太的恢复术") || lower.contains("奥さま")) &&
                            (lower.contains("ntr") || lower.contains("夺爱") || lower.contains("奪愛")))
                    GameCatalog.ID_WAIDONYO -> lower.contains("waidonyo") ||
                        lower.contains("わいどにょ") ||
                        lower.contains("异空窟")
                    GameCatalog.ID_YAKATA2 -> lower.contains("yakata2") ||
                        lower.contains("aliceの館ii") ||
                        lower.contains("aliceの館2") ||
                        lower.contains("爱丽丝之馆2")
                    GameCatalog.ID_SDPSMARIA -> lower.contains("sdpsmaria") ||
                        lower.contains("sdps_maria") ||
                        lower.contains("superdpsmaria") ||
                        lower.contains("玛利亚和坎潘")
                    GameCatalog.ID_TOUSHIN3 -> lower.contains("toushin3") ||
                        lower.contains("toushin_3") ||
                        lower.contains("toushintoshi3") ||
                        lower.contains("闘神都市3") ||
                        lower.contains("闘神都市３") ||
                        lower.contains("闘神都市iii") ||
                        lower.contains("斗神都市3") ||
                        lower.contains("斗神都市３") ||
                        lower.contains("斗神都市iii")
                    GameCatalog.ID_TOUSHIN2SP -> lower.contains("toushin2sp") ||
                        lower.contains("toushin2_sp") ||
                        lower.contains("soshite") ||
                        lower.contains("そして") ||
                        lower.contains("然后，从那以后") ||
                        lower.contains("然后从那以后")
                    GameCatalog.ID_TOUSHIN2 -> (lower.contains("toushin2") ||
                        lower.contains("toushin_2") ||
                        lower.contains("toushintoshi2") ||
                        lower.contains("闘神都市2") ||
                        lower.contains("闘神都市２") ||
                        lower.contains("闘神都市ii") ||
                        lower.contains("斗神都市2") ||
                        lower.contains("斗神都市２") ||
                        lower.contains("斗神都市ii")) &&
                        !lower.contains("sp") &&
                        !lower.contains("soshite") &&
                        !lower.contains("そして") &&
                        !lower.contains("然后")
                    GameCatalog.ID_TOUSHIN1 -> (lower.contains("toushin1") ||
                        lower.contains("toushin_1") ||
                        lower.contains("toushintoshi") ||
                        lower.contains("闘神都市") ||
                        lower.contains("斗神都市")) &&
                        !lower.contains("2") &&
                        !lower.contains("２") &&
                        !lower.contains("3") &&
                        !lower.contains("３") &&
                        !lower.contains("ii") &&
                        !lower.contains("iii")
                    GameCatalog.ID_DIABOLIQUE -> lower.contains("diabolique") ||
                        lower.contains("デアボリカ") ||
                        lower.contains("魔鬼恋曲")
                    GameCatalog.ID_GAKUENHYOURYUU -> lower.contains("gakuenhyouryuu") ||
                        lower.contains("gakuen_hyouryuu") ||
                        lower.contains("学園漂流") ||
                        lower.contains("学园漂流")
                    GameCatalog.ID_GAKUENKINGEX -> lower.contains("gakuenkingex") ||
                        lower.contains("gakuenking_ex") ||
                        lower.contains("学園king番外") ||
                        lower.contains("学園番外編") ||
                        lower.contains("学园king番外") ||
                        ((lower.contains("gakuenking") || lower.contains("gakuen_king") ||
                            lower.contains("学園king") || lower.contains("学园king")) &&
                            (lower.contains("bangai") || lower.contains("番外") || lower.contains("ex")))
                    GameCatalog.ID_GAKUENKING -> (lower.contains("gakuenking") ||
                        lower.contains("gakuen_king") ||
                        lower.contains("学園king") ||
                        lower.contains("学园king")) &&
                        !lower.contains("ex") &&
                        !lower.contains("bangai") &&
                        !lower.contains("番外")
                    GameCatalog.ID_OUDOUYUUSHA -> lower.contains("oudouyuusha") ||
                        lower.contains("oudou_yuusha") ||
                        lower.contains("王道勇者")
                    GameCatalog.ID_DARCROWS -> lower.contains("darcrows") ||
                        lower.contains("ダークロウズ") ||
                        lower.contains("暗鸦")
                    GameCatalog.ID_IKUSAMIKO -> lower.contains("ikusamiko") ||
                        lower.contains("ikusa_miko") ||
                        lower.contains("戦巫女") ||
                        lower.contains("战巫女")
                    GameCatalog.ID_ONLYYOURECROSS -> lower.contains("onlyyourecross") ||
                        lower.contains("onlyyou_recross") ||
                        lower.contains("recross") ||
                        lower.contains("re-cross") ||
                        lower.contains("リ・クルス") ||
                        lower.contains("リクルス")
                    GameCatalog.ID_ONLYYOU -> (lower.contains("onlyyou") ||
                        lower.contains("only_you") ||
                        lower.contains("世纪末的朱丽叶") ||
                        lower.contains("世紀末のジュリエット")) &&
                        !lower.contains("recross") &&
                        !lower.contains("re-cross") &&
                        !lower.contains("リ・クルス") &&
                        !lower.contains("リクルス")
                    GameCatalog.ID_MAMATOTO -> lower.contains("mamatoto") ||
                        lower.contains("ママトト") ||
                        lower.contains("妈妈托托")
                    GameCatalog.ID_MAMORIGAMI -> lower.contains("mamorigami") ||
                        lower.contains("守り神様") ||
                        lower.contains("守护神大人")
                    GameCatalog.ID_AMBIVALENZ -> lower.contains("ambivalenz") ||
                        lower.contains("二律背反")
                    GameCatalog.ID_FUNNYBEE -> lower.contains("funnybee") ||
                        lower.contains("funny_bee") ||
                        lower.contains("ファニーbee") ||
                        lower.contains("宇宙快盗")
                    GameCatalog.ID_MUGENHOUYOU -> lower.contains("mugenhouyou") ||
                        lower.contains("mugen_houyou") ||
                        lower.contains("夢幻泡影") ||
                        lower.contains("梦幻泡影")
                    GameCatalog.ID_OTOMESENKI -> lower.contains("otomesenki") ||
                        lower.contains("otome_senki") ||
                        lower.contains("乙女戦記") ||
                        lower.contains("乙女战记")
                    GameCatalog.ID_MAIDNOSUSUME -> lower.contains("maidnosusume") ||
                        lower.contains("maid_no_susume") ||
                        lower.contains("メイドのススメ") ||
                        lower.contains("女仆的推荐")
                    GameCatalog.ID_PERSIOM -> lower.contains("persiom")
                    GameCatalog.ID_SEEINAO -> lower.contains("seeinao") ||
                        lower.contains("seein_ao") ||
                        lower.contains("seein青")
                    GameCatalog.ID_HUSHABY -> lower.contains("hushaby")
                    GameCatalog.ID_AYUMI -> lower.contains("ayumichan") ||
                        lower.contains("ayumi_chan") ||
                        lower.contains("ayumi") ||
                        lower.contains("あゆみちゃん") ||
                        lower.contains("步美物语")
                    GameCatalog.ID_CIELCLAIRE -> lower.contains("cielclaire") ||
                        lower.contains("ciel_claire") ||
                        lower.contains("シェル・クレイル") ||
                        lower.contains("残酷的爱恋")
                    GameCatalog.ID_NIGHTDEMON -> lower.contains("nightdemon") ||
                        lower.contains("night_demon") ||
                        lower.contains("ナイトデーモン") ||
                        lower.contains("夢鬼") ||
                        lower.contains("梦鬼")
                    GameCatalog.ID_KATSUMI -> lower.contains("katsumi") ||
                        lower.contains("かつみ") ||
                        lower.contains("胜美大夫")
                    GameCatalog.ID_DRSTOP -> lower.contains("drstop") ||
                        lower.contains("dr_stop") ||
                        lower.contains("dr.stop")
                    GameCatalog.ID_DPSALL -> lower.contains("dpsall") ||
                        lower.contains("dps_all") ||
                        lower.contains("d.p.s.全部") ||
                        lower.contains("dps全部")
                    GameCatalog.ID_KOREDPS -> lower.contains("koredps") ||
                        lower.contains("kore_dps") ||
                        lower.contains("これd.p.s") ||
                        lower.contains("これdps") ||
                        lower.contains("这是d.p.s")
                    GameCatalog.ID_PROSTUDENTGOOD -> lower.contains("prostudentgood") ||
                        lower.contains("prostudent_good") ||
                        lower.contains("ぷろすちゅ★でんと") ||
                        lower.contains("职业学生good")
                    GameCatalog.ID_PROSTUDENTG -> (lower.contains("prostudentg") ||
                        lower.contains("prostudent_g") ||
                        lower.contains("prog_cd") ||
                        lower.contains("ぷろすちゅーでんとg") ||
                        lower.contains("职业学生g")) &&
                        !lower.contains("good")
                    GameCatalog.ID_PASCHA3 -> lower.contains("pascha3") ||
                        lower.contains("pascha_3") ||
                        lower.contains("pastelchime3") ||
                        lower.contains("パステルチャイム3") ||
                        lower.contains("パステルチャイム３") ||
                        lower.contains("粉彩铃音3") ||
                        lower.contains("粉彩铃音３")
                    GameCatalog.ID_PASCHACPP -> lower.contains("paschacpp") ||
                        lower.contains("pascha_cpp") ||
                        lower.contains("paschac++") ||
                        lower.contains("ぱすちゃc") ||
                        lower.contains("粉彩铃音c++") ||
                        lower.contains("粉彩铃音 c++")
                    GameCatalog.ID_PASTELCHIME2 -> lower.contains("pastelchime2") ||
                        lower.contains("pastelchime_2") ||
                        lower.contains("pastelchimecontinue") ||
                        lower.contains("ぱすてるチャイムcontinue") ||
                        lower.contains("粉彩铃音continue") ||
                        lower.contains("粉彩铃音 continue")
                    GameCatalog.ID_PASTELCHIME -> (lower.contains("pastelchime") ||
                        lower.contains("pastel_chime") ||
                        lower.contains("ぱすてるチャイム") ||
                        lower.contains("粉彩铃音")) &&
                        !lower.contains("2") &&
                        !lower.contains("２") &&
                        !lower.contains("3") &&
                        !lower.contains("３") &&
                        !lower.contains("continue") &&
                        !lower.contains("cpp") &&
                        !lower.contains("c++") &&
                        !lower.contains("++") &&
                        !lower.contains("bind")
                    GameCatalog.ID_YAKATA3 -> lower.contains("yakata3") ||
                        lower.contains("yakata_3") ||
                        lower.contains("aliceの館3") ||
                        lower.contains("aliceの館iii") ||
                        lower.contains("爱丽丝之馆3")
                    GameCatalog.ID_DALKGAIDEN -> lower.contains("dalkgaiden") ||
                        lower.contains("dalk_gaiden") ||
                        lower.contains("dalk外伝") ||
                        lower.contains("dalk外传") ||
                        lower.contains("dalk外傳")
                    GameCatalog.ID_DALK -> (Regex("(?<![a-z0-9])dalk(?![a-z0-9])").containsMatchIn(lower) ||
                        lower.contains("dalk_sa") || lower.contains("dalk_base")) &&
                        !lower.contains("gaiden") &&
                        !lower.contains("外伝") &&
                        !lower.contains("外传") &&
                        !lower.contains("外傳")
                    GameCatalog.ID_MAMANYONYO -> lower.contains("mamanyonyo") ||
                        lower.contains("ままにょにょ") ||
                        lower.contains("妈妈软软")
                    GameCatalog.ID_MAJONOSHOKUZAI -> lower.contains("majonoshokuzai") ||
                        lower.contains("majo_no_shokuzai") ||
                        lower.contains("魔女の贖罪") ||
                        lower.contains("魔女的赎罪")
                    GameCatalog.ID_SABOTEN -> lower.contains("saboten") ||
                        lower.contains("よくばりサボテン") ||
                        lower.contains("贪心仙人掌")
                    GameCatalog.ID_DOUBLESENSEI -> lower.contains("doublesensei") ||
                        lower.contains("double_sensei") ||
                        lower.contains("だぶる先生") ||
                        lower.contains("双倍教师生活")
                    GameCatalog.ID_OJOUSAMA -> lower.contains("ojousama") ||
                        lower.contains("お嬢様をいいなり") ||
                        lower.contains("让大小姐言听计从")
                    GameCatalog.ID_EDORANZE -> lower.contains("edoranze") ||
                        lower.contains("エドランゼ") ||
                        lower.contains("江户乱世")
                    GameCatalog.ID_VANISHPALM -> lower.contains("vanishpalm") ||
                        lower.contains("vanish_palm") ||
                        lower.contains("この手のひら") ||
                        lower.contains("ばにしゅ！番外") ||
                        lower.contains("ばにしゅ!番外") ||
                        lower.contains("掌中奇缘")
                    GameCatalog.ID_VANISH -> (lower.contains("vanish") ||
                        lower.contains("ばにしゅ")) &&
                        !lower.contains("vanishpalm") &&
                        !lower.contains("vanish_palm") &&
                        !lower.contains("この手のひら") &&
                        !lower.contains("番外") &&
                        !lower.contains("掌中")
                    GameCatalog.ID_HOKENSHITSU -> lower.contains("hokenshitsu") ||
                        lower.contains("僕だけの保健室") ||
                        lower.contains("只属于我的保健室")
                    GameCatalog.ID_KAMUNAGARA -> lower.contains("kamunagara") ||
                        lower.contains("カムナガラ") ||
                        lower.contains("神武")
                    GameCatalog.ID_ERUMEIWAKARE -> lower.contains("erumeiwakare") ||
                        lower.contains("erumei_wakare") ||
                        lower.contains("別れの夜") ||
                        lower.contains("别离之夜")
                    GameCatalog.ID_ERUMEIFLOWER -> (lower.contains("erumeiflower") ||
                        lower.contains("erumei_flower") ||
                        lower.contains("エルメイフラワー") ||
                        lower.contains("艾尔梅花")) &&
                        !lower.contains("wakare") &&
                        !lower.contains("別れ") &&
                        !lower.contains("别离")
                    GameCatalog.ID_MANANA1 -> lower.contains("manana1") || lower.contains("manana_1") || lower.contains("まななvol1")
                    GameCatalog.ID_MANANA2 -> lower.contains("manana2") || lower.contains("manana_2") || lower.contains("まななvol2")
                    GameCatalog.ID_MANANA3 -> lower.contains("manana3") || lower.contains("manana_3") || lower.contains("まななvol3")
                    GameCatalog.ID_MANANA4 -> lower.contains("manana4") || lower.contains("manana_4") || lower.contains("まななvol4")
                    GameCatalog.ID_MANANA5 -> lower.contains("manana5") || lower.contains("manana_5") || lower.contains("まななvol5")
                    GameCatalog.ID_NINGENGARI -> lower.contains("ningengari") ||
                        lower.contains("ningen_gari") ||
                        lower.contains("人間狩り") ||
                        lower.contains("人间狩猎")
                    GameCatalog.ID_OYAKORANKAN -> lower.contains("oyakorankan") ||
                        lower.contains("oyako_rankan") ||
                        lower.contains("母娘乱館") ||
                        lower.contains("母娘乱馆")
                    GameCatalog.ID_YORUGAKURU -> lower.contains("yorugakuru") ||
                        lower.contains("yoru_ga_kuru") ||
                        lower.contains("nightremaster") ||
                        lower.contains("夜が来る") ||
                        lower.contains("邪夜将至")
                    GameCatalog.ID_MAMAN -> (Regex("(?<![a-z0-9])maman(?![a-z0-9])").containsMatchIn(lower) ||
                        lower.contains("heartfulmaman") ||
                        lower.contains("heartful maman") ||
                        lower.contains("は～とふるママン") ||
                        lower.contains("再婚妈妈")) &&
                        !lower.contains("mamanyonyo")
                    GameCatalog.ID_HAHARANMAN -> lower.contains("haharanman") ||
                        lower.contains("haha_ranman") ||
                        lower.contains("母爛漫") ||
                        lower.contains("母烂漫")
                    GameCatalog.ID_TSUMAMIGUI2 -> lower.contains("tsumamigui2") ||
                        lower.contains("tsumamigui_2") ||
                        lower.contains("妻みぐい2") ||
                        lower.contains("妻みぐい２") ||
                        lower.contains("妻中蜜2") ||
                        lower.contains("妻中蜜２")
                    GameCatalog.ID_TSUMAMIGUI3JUN -> lower.contains("tsumamigui3jun") ||
                        lower.contains("tsumamigui3_jun") ||
                        lower.contains("tsuma3so_junai") ||
                        ((lower.contains("tsumamigui3") || lower.contains("妻みぐい3") || lower.contains("妻中蜜3")) &&
                            (lower.contains("jun") || lower.contains("纯爱") || lower.contains("純愛")))
                    GameCatalog.ID_TSUMAMIGUI3NTR -> lower.contains("tsumamigui3ntr") ||
                        lower.contains("tsumamigui3_ntr") ||
                        lower.contains("tsuma3so_netorare") ||
                        ((lower.contains("tsumamigui3") || lower.contains("妻みぐい3") || lower.contains("妻中蜜3")) &&
                            (lower.contains("ntr") || lower.contains("netorare") || lower.contains("寝取")))
                    GameCatalog.ID_TSUMAMIGUI -> (lower.contains("tsumamigui") ||
                        lower.contains("妻みぐい") ||
                        lower.contains("妻中蜜")) &&
                        !lower.contains("tsumamigui2") &&
                        !lower.contains("妻みぐい2") &&
                        !lower.contains("妻みぐい２") &&
                        !lower.contains("妻中蜜2") &&
                        !lower.contains("妻中蜜２") &&
                        !lower.contains("tsumamigui3") &&
                        !lower.contains("妻みぐい3") &&
                        !lower.contains("妻みぐい３") &&
                        !lower.contains("妻中蜜3") &&
                        !lower.contains("妻中蜜３")
                    GameCatalog.ID_ESCALAYER -> lower.contains("escalayer") &&
                        !lower.contains("escalayerreboot") &&
                        !lower.contains("escalayer_reboot") &&
                        !lower.contains("reboot") &&
                        !lower.contains("haruka") &&
                        !lower.contains("ハルカ")
                    GameCatalog.ID_ESCALAYER_REBOOT -> lower.contains("escalayerreboot") ||
                        lower.contains("escalayer_reboot") ||
                        (lower.contains("escalayer") && lower.contains("reboot"))
                    GameCatalog.ID_GALZOO -> lower.contains("galzoo")
                    GameCatalog.ID_IXSEAL -> lower.contains("ixseal")
                    GameCatalog.ID_HARUKAVSESCA -> lower.contains("harukavsesca") ||
                        lower.contains("haruka_vs_esca") ||
                        lower.contains("ハルカvs") ||
                        lower.contains("遥vs")
                    GameCatalog.ID_HARUKANINJA -> lower.contains("harukaninja") ||
                        lower.contains("haruka_ninja") ||
                        lower.contains("疾風") ||
                        lower.contains("疾风") ||
                        lower.contains("忍者大作戦") ||
                        lower.contains("忍者大作战")
                    GameCatalog.ID_HARUKA -> (lower.contains("haruka") ||
                        lower.contains("超昂閃忍") ||
                        lower.contains("超昂闪忍") ||
                        lower.contains("ハルカ")) &&
                        !lower.contains("harukavsesca") &&
                        !lower.contains("haruka_vs") &&
                        !lower.contains("harukaninja") &&
                        !lower.contains("haruka_ninja") &&
                        !lower.contains("escalayer") &&
                        !lower.contains("エスカレイヤー") &&
                        !lower.contains("ハルカvs") &&
                        !lower.contains("遥vs") &&
                        !lower.contains("疾風") &&
                        !lower.contains("疾风") &&
                        !lower.contains("忍者大作戦") &&
                        !lower.contains("忍者大作战")
                    GameCatalog.ID_TSUMASAKURA -> lower.contains("tsumasakura") ||
                        lower.contains("tsuma_sakura") ||
                        lower.contains("妻たちの桜貝") ||
                        lower.contains("妻们的樱贝")
                    GameCatalog.ID_SHAMANSSANCTUARY -> lower.contains("shamanssanctuary") ||
                        lower.contains("shamans_sanctuary") ||
                        lower.contains("しゃーまんず") ||
                        lower.contains("まんくちゅ") ||
                        lower.contains("巫女の聖域") ||
                        lower.contains("巫女的圣域")
                    GameCatalog.ID_ALIVEZ -> lower.contains("alivez") ||
                        lower.contains("alive_z") ||
                        lower.contains("ａｌｉｖｅｚ")
                    GameCatalog.ID_DUNGEONSDOLLS -> lower.contains("dungeonsdolls") ||
                        lower.contains("dungeons_dolls") ||
                        lower.contains("dungeons & dolls") ||
                        lower.contains("ドールズ")
                    GameCatalog.ID_SUZUMIYA -> lower.contains("suzumiya") ||
                        lower.contains("鈴宮刑務娼館") ||
                        lower.contains("铃宫刑务娼馆")
                    GameCatalog.ID_SHIMAIMA -> lower.contains("shimaima") ||
                        lower.contains("しまいま")
                    GameCatalog.ID_COSPLAYLEMON -> lower.contains("cosplaylemon") ||
                        lower.contains("cosplay_lemon") ||
                        lower.contains("こすぷれれもん") ||
                        lower.contains("cosplay柠檬")
                    GameCatalog.ID_WHIPHONEY -> lower.contains("whiphoney") ||
                        lower.contains("whip_honey") ||
                        lower.contains("ほいっぷハニー") ||
                        lower.contains("奶油哈尼")
                    GameCatalog.ID_ONIMUSU -> lower.contains("onimusu") ||
                        lower.contains("オニむス")
                    GameCatalog.ID_PROGRX -> lower.contains("progrx") ||
                        lower.contains("pro_grx") ||
                        lower.contains("ぷろgrx") ||
                        lower.contains("ぷろｇｒｘ") ||
                        lower.contains("职业grx")
                    GameCatalog.ID_DPSVERN -> lower.contains("dpsvern") ||
                        lower.contains("dps_ver_n") ||
                        lower.contains("でーぴーえす")
                    GameCatalog.ID_SUKIIRONO -> lower.contains("sukiirono") ||
                        lower.contains("sukiiro") ||
                        lower.contains("透色")
                    GameCatalog.ID_GIRLS -> Regex("(?<![a-z0-9])girls(?![a-z0-9])").containsMatchIn(lower) ||
                        lower.contains("ｇｉｒｌｓ")
                    GameCatalog.ID_DEBUT -> Regex("(?<![a-z0-9])debut(?![a-z0-9])").containsMatchIn(lower) ||
                        lower.contains("ｄｅｂｕｔ")
                    GameCatalog.ID_MEGANE -> lower.contains("megane") ||
                        lower.contains("眼鏡のメイ") ||
                        lower.contains("眼镜梅")
                    GameCatalog.ID_RACHELSAKURA -> lower.contains("rachelsakura") ||
                        lower.contains("rachel_sakura") ||
                        lower.contains("レイチェルの桜貝") ||
                        lower.contains("蕾切尔的樱贝")
                    GameCatalog.ID_SABAKUNIKA -> lower.contains("sabakunika") ||
                        lower.contains("sabaku_nika") ||
                        lower.contains("砂漠のニッカ") ||
                        lower.contains("沙漠的妮卡")
                    GameCatalog.ID_TSUMASHIBORI -> lower.contains("tsumashibori") ||
                        lower.contains("tsumasibori") ||
                        lower.contains("妻しぼり") ||
                        lower.contains("嵢偟傏傝")
                    GameCatalog.ID_TSUMAMIGUI3 -> (lower.contains("tsumamigui3") ||
                        lower.contains("妻みぐい３") ||
                        lower.contains("妻みぐい3")) &&
                        !lower.contains("jun") &&
                        !lower.contains("ntr") &&
                        !lower.contains("netorare") &&
                        !lower.contains("spinoff") &&
                        !lower.contains("番外")
                    null, "" -> true
                    else -> false
                }
                if (matches) {
                    evictFileToTrash(child)?.let { out.add(it) }
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun evictModCacheToTrash(storagePath: File, steps: List<ModReleaseEntry>): List<File> {
        val out = ArrayList<File>()
        try {
            val cacheDir = File(storagePath, ".online_mod_cache")
            if (!cacheDir.isDirectory) return out
            for (step in steps) {
                val cacheId = step.expectedSha256?.takeIf { it.length >= 16 }?.take(16)
                    ?: sha256Hex(AppSettings.canonicalCdnUrl(step.downloadUrl).toByteArray(Charsets.UTF_8)).take(16)
                val ext = extractArchiveExtension(step.fileName)
                evictFileToTrash(File(cacheDir, "mod_${cacheId}${ext}.part"))?.let { out.add(it) }
                evictFileToTrash(File(cacheDir, "mod_${cacheId}${ext}.part.meta"))?.let { out.add(it) }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun cleanupBaseStagingForGame(
        storagePath: File,
        targetGameId: String?,
        resolvedStagingDir: File?
    ) {
        for (f in evictBaseStagingToTrash(storagePath, targetGameId, resolvedStagingDir)) {
            try {
                f.deleteRecursively()
            } catch (_: Throwable) {
            }
        }
    }

    fun cleanupModCacheForSteps(storagePath: File, steps: List<ModReleaseEntry>) {
        for (f in evictModCacheToTrash(storagePath, steps)) {
            try {
                f.deleteRecursively()
            } catch (_: Throwable) {
            }
        }
        try {
            val cacheDir = File(storagePath, ".online_mod_cache")
            if (cacheDir.isDirectory && cacheDir.listFiles()?.isEmpty() == true) {
                cacheDir.delete()
            }
        } catch (_: Throwable) {
        }
    }

    fun fetchManifestTotalBytes(cdnBaseUrl: String): Long {
        val cleanBaseUrl = cdnBaseUrl.trimEnd('/')
        return fetchManifest(cleanBaseUrl, null) {}.totalBytes
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val gb = 1024.0 * 1024.0 * 1024.0
        val mb = 1024.0 * 1024.0
        val kb = 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun fetchManifest(
        cdnBaseUrl: String,
        abortHandle: InstallAbortHandle?,
        checkCancelled: () -> Unit
    ): OnlineManifest {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            checkCancelled()
            var conn: HttpURLConnection? = null
            try {
                conn = openFollowingRedirects(
                    initialUrl = "$cdnBaseUrl/manifest.json",
                    headers = mapOf(
                        "User-Agent" to DEFAULT_USER_AGENT,
                        "Accept" to "application/json",
                        "Accept-Encoding" to "identity"
                    ),
                    abortHandle = abortHandle,
                    readTimeoutMs = 15_000
                )
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Manifest HTTP $code")
                }
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return parseManifest(raw)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GameList.InstallFailureException) {
                throw e
            } catch (e: InvalidManifestException) {
                throw e
            } catch (e: Exception) {
                if (abortHandle?.isAborted == true) throw CancellationException()
                checkCancelled()
                lastErr = e
                if (attempt < 2) {
                    sleepCancellable(1200L * (attempt + 1), checkCancelled)
                }
            } finally {
                abortHandle?.unregister(conn)
                if (conn != null) {
                    if (abortHandle?.isAborted == true) {
                        InstallAbortHandle.disconnectAsync(listOf(conn))
                    } else {
                        conn.disconnect()
                    }
                }
            }
        }
        throw IOException("Failed to fetch online manifest", lastErr)
    }

    fun parseManifest(raw: String): OnlineManifest {
        val root = JSONObject(raw)
        val gameName = root.optString("gameName", "兰斯10")
        val baseName = root.optString("baseName", "Rance10")
        val filesArr = root.getJSONArray("files")
        val entries = ArrayList<ManifestEntry>(filesArr.length())
        var sumBytes = 0L
        for (i in 0 until filesArr.length()) {
            val item = filesArr.getJSONObject(i)
            val path = item.getString("path")
            val sz = item.getLong("size")
            val sha = item.getString("sha256").trim()
            if (!sha256HexRegex.matches(sha)) {
                throw InvalidManifestException("Manifest entry has invalid sha256: $path")
            }
            sumBytes += sz
            entries.add(
                ManifestEntry(
                    path = path,
                    key = item.getString("key"),
                    size = sz,
                    sha256 = sha.lowercase(Locale.ROOT),
                    extract = item.optBoolean("extract", false)
                )
            )
        }
        val totalBytes = root.optLong("totalBytes", sumBytes).takeIf { it > 0L } ?: sumBytes
        return OnlineManifest(gameName, baseName, totalBytes, entries)
    }

    fun isStrongEtag(etag: String?): Boolean =
        !etag.isNullOrBlank() && !etag.trimStart().startsWith("W/", ignoreCase = true)

    private fun estimatePartialBytesOnDisk(
        partFile: File,
        metaFile: File,
        url: String,
        expectedSize: Long,
        expectedSha256: String?
    ): Long {
        if (!partFile.isFile) return 0L
        val saved = readSavedMeta(metaFile, url, expectedSha256)
        if (saved != null && saved.segmented) {
            if (expectedSize > 0L && saved.totalBytes != expectedSize) return 0L
            return saved.segments.sumOf { it.done }.coerceIn(0L, expectedSize.coerceAtLeast(0L))
        }
        val len = partFile.length()
        return if (expectedSize > 0L && len in 1L..expectedSize) len else 0L
    }

    @JvmOverloads
    fun downloadFileWithResume(
        url: String,
        destPartFile: File,
        expectedSize: Long,
        expectedSha256: String?,
        headers: Map<String, String>,
        abortHandle: InstallAbortHandle?,
        checkCancelled: () -> Unit,
        onProgress: (doneBytes: Long, totalBytes: Long, speedBps: Long) -> Unit,
        maxConnections: Int = 1,
        minSegmentedBytes: Long = DEFAULT_MIN_SEGMENTED_BYTES
    ) {
        destPartFile.parentFile?.mkdirs()
        val metaFile = File("${destPartFile.path}.meta")
        val normalizedConns = AppSettings.normalizeDownloadConcurrency(maxConnections)
        val saved = readSavedMeta(metaFile, url, expectedSha256)
        val knownTotal = when {
            expectedSize > 0L -> expectedSize
            saved != null && saved.totalBytes > 0L -> saved.totalBytes
            else -> -1L
        }
        val alreadySegmented = saved?.segmented == true &&
            destPartFile.isFile &&
            (expectedSize <= 0L || saved.totalBytes == expectedSize)
        val curLen = if (!alreadySegmented && destPartFile.isFile) destPartFile.length() else 0L
        val remaining = if (alreadySegmented) {
            saved!!.segments.sumOf { it.length - it.done }
        } else if (knownTotal > 0L) {
            (knownTotal - curLen).coerceAtLeast(0L)
        } else {
            -1L
        }
        val wantSegmented = alreadySegmented ||
            (normalizedConns > 1 && knownTotal > 0L && computeSegmentCount(remaining, normalizedConns, minSegmentedBytes) >= 2)

        if (!wantSegmented) {
            downloadFileSingleStream(
                url = url,
                destPartFile = destPartFile,
                expectedSize = expectedSize,
                expectedSha256 = expectedSha256,
                headers = headers,
                abortHandle = abortHandle,
                checkCancelled = checkCancelled,
                onProgress = onProgress
            )
            return
        }

        val batchAbort = InstallAbortHandle()
        abortHandle?.addChild(batchAbort)
        val pool = if (normalizedConns > 1) {
            Executors.newFixedThreadPool(normalizedConns) { r ->
                Thread(r, "AliceHubSeg").apply { isDaemon = true }
            }
        } else {
            null
        }
        val remainingTasks = AtomicInteger(1)
        val allDoneLatch = CountDownLatch(1)
        val firstError = AtomicReference<Throwable?>(null)
        val progressLock = Any()
        val progressEmitLock = Any()
        var speedWindowStartMs = SystemClock.elapsedRealtime()
        var speedWindowBytes = 0L
        var currentSpeedBps = 0L
        var lastUiReportMs = 0L
        val maxReported = AtomicLong(0L)

        fun reportCombined(fileDoneBytes: Long, deltaBytes: Long, totalBytes: Long, force: Boolean = false) {
            synchronized(progressEmitLock) {
                var reportDone = 0L
                var reportSpeed = 0L
                var shouldEmit = false
                synchronized(progressLock) {
                    if (deltaBytes > 0L) speedWindowBytes += deltaBytes
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - speedWindowStartMs
                    if (elapsed >= 600L) {
                        currentSpeedBps = (speedWindowBytes * 1000L) / elapsed
                        speedWindowStartMs = now
                        speedWindowBytes = 0L
                    }
                    if (force || now - lastUiReportMs >= 150L) {
                        lastUiReportMs = now
                        val clamped = if (totalBytes > 0L) fileDoneBytes.coerceAtMost(totalBytes) else fileDoneBytes
                        reportDone = maxReported.updateAndGet { prev -> maxOf(prev, clamped) }
                        reportSpeed = currentSpeedBps
                        shouldEmit = true
                    }
                }
                if (shouldEmit) {
                    onProgress(reportDone, totalBytes, reportSpeed)
                }
            }
        }

        val workerCheckCancelled = {
            if (batchAbort.isAborted || abortHandle?.isAborted == true) {
                throw CancellationException()
            }
            checkCancelled()
        }

        fun submitSubTask(task: () -> Unit): Boolean {
            if (batchAbort.isAborted || firstError.get() != null) return false
            if (pool == null) {
                // Single-worker resume of a previously segmented file: run inline
                task()
                return true
            }
            remainingTasks.incrementAndGet()
            return try {
                pool.execute {
                    try {
                        task()
                    } catch (t: Throwable) {
                        if (firstError.compareAndSet(null, t)) {
                            batchAbort.abort()
                        }
                    } finally {
                        if (remainingTasks.decrementAndGet() == 0) {
                            allDoneLatch.countDown()
                        }
                    }
                }
                true
            } catch (_: RejectedExecutionException) {
                if (remainingTasks.decrementAndGet() == 0) {
                    allDoneLatch.countDown()
                }
                false
            }
        }

        try {
            val kickoff = {
                try {
                    startFileDownloadOnPool(
                        url = url,
                        destPartFile = destPartFile,
                        expectedSize = expectedSize,
                        expectedSha256 = expectedSha256,
                        headers = headers,
                        maxConnections = normalizedConns,
                        minSegmentedBytes = minSegmentedBytes,
                        batchAbort = batchAbort,
                        checkCancelled = workerCheckCancelled,
                        onProgressDelta = { done, delta, total ->
                            reportCombined(done, delta, total, force = (total > 0L && done >= total))
                        },
                        submitSubTask = ::submitSubTask,
                        onFileFinished = { err ->
                            if (err != null && firstError.compareAndSet(null, err)) {
                                batchAbort.abort()
                            }
                        }
                    )
                } catch (t: Throwable) {
                    if (firstError.compareAndSet(null, t)) {
                        batchAbort.abort()
                    }
                } finally {
                    if (remainingTasks.decrementAndGet() == 0) {
                        allDoneLatch.countDown()
                    }
                }
            }
            if (pool != null) {
                pool.execute(kickoff)
                while (!allDoneLatch.await(150L, TimeUnit.MILLISECONDS)) {
                    if (abortHandle?.isAborted == true) {
                        batchAbort.abort()
                        throw CancellationException()
                    }
                    try {
                        checkCancelled()
                    } catch (t: Throwable) {
                        batchAbort.abort()
                        throw t
                    }
                }
            } else {
                kickoff()
            }
        } finally {
            if (pool != null) {
                pool.shutdown()
                if (!pool.awaitTermination(5L, TimeUnit.SECONDS)) {
                    batchAbort.abort()
                    pool.shutdownNow()
                    pool.awaitTermination(3L, TimeUnit.SECONDS)
                }
            }
            abortHandle?.removeChild(batchAbort)
        }

        if (abortHandle?.isAborted == true) throw CancellationException()
        checkCancelled()
        firstError.get()?.let { err ->
            when (err) {
                is CancellationException -> throw err
                is GameList.InstallFailureException -> throw err
                is IOException -> throw err
                is RuntimeException -> throw err
                is Error -> throw err
                else -> throw IOException("Segmented download failed", err)
            }
        }
    }

    private fun startFileDownloadOnPool(
        url: String,
        destPartFile: File,
        expectedSize: Long,
        expectedSha256: String?,
        headers: Map<String, String>,
        maxConnections: Int,
        minSegmentedBytes: Long,
        batchAbort: InstallAbortHandle,
        checkCancelled: () -> Unit,
        onProgressDelta: (fileDoneBytes: Long, deltaBytes: Long, totalBytes: Long) -> Unit,
        submitSubTask: (() -> Unit) -> Boolean,
        onFileFinished: (Throwable?) -> Unit
    ) {
        destPartFile.parentFile?.mkdirs()
        val metaFile = File("${destPartFile.path}.meta")
        val hashless = expectedSha256.isNullOrBlank()
        var savedMeta = readSavedMeta(metaFile, url, expectedSha256)
        if (savedMeta?.segmented == true &&
            (!destPartFile.isFile || (expectedSize > 0L && savedMeta.totalBytes != expectedSize))
        ) {
            destPartFile.delete()
            metaFile.delete()
            savedMeta = null
        }
        if ((savedMeta == null && (metaFile.isFile || (hashless && destPartFile.isFile))) ||
            (hashless && destPartFile.isFile && !isStrongEtag(savedMeta?.etag))
        ) {
            destPartFile.delete()
            metaFile.delete()
            savedMeta = null
        }

        val knownTotal = when {
            expectedSize > 0L -> expectedSize
            savedMeta != null && savedMeta.totalBytes > 0L -> savedMeta.totalBytes
            else -> -1L
        }

        fun runSingleStreamAndFinish() {
            var lastDone = 0L
            downloadFileSingleStream(
                url = url,
                destPartFile = destPartFile,
                expectedSize = expectedSize,
                expectedSha256 = expectedSha256,
                headers = headers,
                abortHandle = batchAbort,
                checkCancelled = checkCancelled,
                onProgress = { done, total, _ ->
                    val delta = (done - lastDone).coerceAtLeast(0L)
                    lastDone = done
                    onProgressDelta(done, delta, total)
                }
            )
            onFileFinished(null)
        }

        val alreadySegmented = savedMeta?.segmented == true
        val segments: List<SegmentProgress>
        val startedWithPartial: Boolean
        if (alreadySegmented) {
            segments = savedMeta!!.segments
            startedWithPartial = true
            if (segments.all { it.isComplete } && destPartFile.isFile && destPartFile.length() == savedMeta.totalBytes) {
                if (!hashless) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                        writeSavedMeta(metaFile, url, savedMeta.etag, savedMeta.totalBytes, expectedSha256, segmented = false)
                        onProgressDelta(savedMeta.totalBytes, 0L, savedMeta.totalBytes)
                        onFileFinished(null)
                        return
                    }
                    destPartFile.delete()
                    metaFile.delete()
                    runSingleStreamAndFinish()
                    return
                } else {
                    writeSavedMeta(metaFile, url, savedMeta.etag, savedMeta.totalBytes, expectedSha256, segmented = false)
                    onProgressDelta(savedMeta.totalBytes, 0L, savedMeta.totalBytes)
                    onFileFinished(null)
                    return
                }
            }
        } else {
            var curLen = if (destPartFile.isFile) destPartFile.length() else 0L
            if (knownTotal > 0L && curLen > knownTotal) {
                destPartFile.delete()
                metaFile.delete()
                curLen = 0L
                savedMeta = null
            }
            if (knownTotal > 0L && curLen == knownTotal) {
                if (!hashless) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                        onProgressDelta(curLen, 0L, knownTotal)
                        onFileFinished(null)
                        return
                    }
                    destPartFile.delete()
                    metaFile.delete()
                    curLen = 0L
                    savedMeta = null
                } else {
                    onProgressDelta(curLen, 0L, knownTotal)
                    onFileFinished(null)
                    return
                }
            }
            val remaining = if (knownTotal > 0L) knownTotal - curLen else -1L
            val segCount = computeSegmentCount(remaining, maxConnections, minSegmentedBytes)
            if (segCount < 2 || knownTotal <= 0L) {
                runSingleStreamAndFinish()
                return
            }
            segments = buildInitialSegments(curLen, knownTotal, segCount)
            startedWithPartial = curLen > 0L
        }

        val unfinished = segments.filter { !it.isComplete }
        if (unfinished.isEmpty()) {
            runSingleStreamAndFinish()
            return
        }

        val totalBytes = if (alreadySegmented) savedMeta!!.totalBytes else knownTotal
        ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, unfinished.sumOf { it.length - it.done })

        val fileAbort = InstallAbortHandle()
        batchAbort.addChild(fileAbort)
        val fileDoneBytes = AtomicLong(segments.sumOf { it.done })
        val metaLock = Any()
        val bytesSinceMetaSave = AtomicLong(0L)
        val activeEtag = AtomicReference<String?>(savedMeta?.etag)
        val hasCurrentUrlEtag = AtomicBoolean(savedMeta != null && savedMeta.url == url && isStrongEtag(savedMeta.etag))
        val fileError = AtomicReference<Throwable?>(null)
        val needsCleanRestart = AtomicBoolean(false)
        val activeSegTasks = AtomicInteger(1)

        fun persistMeta(segmented: Boolean) {
            synchronized(metaLock) {
                writeSavedMeta(
                    metaFile = metaFile,
                    url = url,
                    etag = activeEtag.get(),
                    totalBytes = totalBytes,
                    sha256 = expectedSha256,
                    segmented = segmented,
                    segments = if (segmented) segments else emptyList()
                )
            }
        }

        fun segCheckCancelled() {
            if (fileAbort.isAborted || batchAbort.isAborted) throw CancellationException()
            checkCancelled()
        }

        fun streamIntoSegment(conn: HttpURLConnection, seg: SegmentProgress) {
            val reqStart = seg.start + seg.done
            var segRemaining = seg.length - seg.done
            var completedNormally = false
            val input = conn.inputStream
            try {
                RandomAccessFile(destPartFile, "rw").use { raf ->
                    raf.seek(reqStart)
                    val buf = ByteArray(256 * 1024)
                    var idleReadMs = 0L
                    while (segRemaining > 0L) {
                        segCheckCancelled()
                        val n = try {
                            input.read(buf)
                        } catch (ste: java.net.SocketTimeoutException) {
                            segCheckCancelled()
                            idleReadMs += READ_SLICE_TIMEOUT_MS
                            if (idleReadMs >= TOTAL_READ_TIMEOUT_MS) throw ste
                            continue
                        }
                        idleReadMs = 0L
                        segCheckCancelled()
                        if (n < 0) break
                        if (n == 0) continue
                        if (n.toLong() > segRemaining) {
                            throw IOException("Segment overflow: got $n > remaining $segRemaining")
                        }
                        raf.write(buf, 0, n)
                        seg.done += n.toLong()
                        segRemaining -= n.toLong()
                        val totalDone = fileDoneBytes.addAndGet(n.toLong())
                        onProgressDelta(totalDone, n.toLong(), totalBytes)
                        if (bytesSinceMetaSave.addAndGet(n.toLong()) >= SEGMENT_META_PERSIST_BYTES) {
                            bytesSinceMetaSave.set(0L)
                            persistMeta(true)
                        }
                    }
                    raf.fd.sync()
                }
                completedNormally = true
            } finally {
                if (completedNormally) {
                    try {
                        input.close()
                    } catch (_: Throwable) {
                    }
                }
                persistMeta(true)
            }
        }

        fun runSegmentLoop(seg: SegmentProgress) {
            var lastErr: Exception? = null
            var attempt = 0
            while (attempt < MAX_HTTP_RETRIES && !seg.isComplete) {
                segCheckCancelled()
                val reqStart = seg.start + seg.done
                val reqEnd = seg.end
                var conn: HttpURLConnection? = null
                var completedConn = false
                try {
                    val reqHeaders = LinkedHashMap<String, String>(headers)
                    reqHeaders["Accept-Encoding"] = "identity"
                    reqHeaders["Range"] = "bytes=$reqStart-$reqEnd"
                    val etag = activeEtag.get()
                    if (hasCurrentUrlEtag.get() && isStrongEtag(etag)) {
                        reqHeaders["If-Range"] = etag!!
                    }
                    conn = openFollowingRedirects(url, reqHeaders, fileAbort)
                    val code = conn.responseCode
                    conn.readTimeout = READ_SLICE_TIMEOUT_MS
                    if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                        throw IOException("HTTP $code")
                    }
                    val respEtag = conn.getHeaderField("ETag")?.trim()
                    when (code) {
                        HttpURLConnection.HTTP_PARTIAL -> {
                            val parsed = parseContentRangeFull(conn.getHeaderField("Content-Range"))
                            val validRange = parsed != null &&
                                parsed.first == reqStart &&
                                parsed.second == reqEnd &&
                                (parsed.third <= 0L || parsed.third == totalBytes)
                            val sameRemote = !hashless ||
                                (isStrongEtag(respEtag) && respEtag == activeEtag.get())
                            if (!validRange || !sameRemote) {
                                needsCleanRestart.set(true)
                                fileAbort.abort()
                                return
                            }
                            if (!respEtag.isNullOrEmpty() && !hasCurrentUrlEtag.get()) {
                                activeEtag.set(respEtag)
                                if (isStrongEtag(respEtag)) hasCurrentUrlEtag.set(true)
                            }
                            streamIntoSegment(conn, seg)
                            completedConn = true
                            if (!seg.isComplete) {
                                throw IOException("Premature EOF in segment ${seg.start}-${seg.end} at ${seg.done}/${seg.length}")
                            }
                            return
                        }
                        HttpURLConnection.HTTP_OK, 416 -> {
                            needsCleanRestart.set(true)
                            fileAbort.abort()
                            return
                        }
                        else -> throw IOException("Unexpected HTTP status $code")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GameList.InstallFailureException) {
                    throw e
                } catch (e: Exception) {
                    if (fileAbort.isAborted || batchAbort.isAborted) throw CancellationException()
                    checkCancelled()
                    lastErr = e
                    if (attempt + 1 < MAX_HTTP_RETRIES) {
                        sleepCancellable(1000L * (attempt + 1).coerceAtMost(5), ::segCheckCancelled)
                    }
                    attempt++
                } finally {
                    fileAbort.unregister(conn)
                    if (conn != null) {
                        if (!completedConn && (fileAbort.isAborted || batchAbort.isAborted)) {
                            InstallAbortHandle.disconnectAsync(listOf(conn))
                        } else {
                            conn.disconnect()
                        }
                    }
                }
            }
            if (!seg.isComplete) {
                throw lastErr ?: IOException("Segment ${seg.start}-${seg.end} incomplete")
            }
        }

        fun onSegmentTaskExited(err: Throwable?) {
            if (err != null && !needsCleanRestart.get()) {
                if (fileError.compareAndSet(null, err)) {
                    fileAbort.abort()
                }
            }
            if (activeSegTasks.decrementAndGet() != 0) return

            batchAbort.removeChild(fileAbort)
            if (batchAbort.isAborted) {
                persistMeta(true)
                onFileFinished(CancellationException())
                return
            }
            if (needsCleanRestart.get()) {
                destPartFile.delete()
                metaFile.delete()
                try {
                    runSingleStreamAndFinish()
                } catch (t: Throwable) {
                    onFileFinished(t)
                }
                return
            }
            val recordedErr = fileError.get()
            if (recordedErr != null) {
                persistMeta(true)
                onFileFinished(recordedErr)
                return
            }
            try {
                if (!segments.all { it.isComplete } || !destPartFile.isFile || destPartFile.length() != totalBytes) {
                    throw IOException("Downloaded size mismatch: expected $totalBytes, got ${destPartFile.length()}")
                }
                if (!hashless) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                        destPartFile.delete()
                        metaFile.delete()
                        if (startedWithPartial) {
                            runSingleStreamAndFinish()
                            return
                        }
                        throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                    }
                }
                persistMeta(false)
                onProgressDelta(totalBytes, 0L, totalBytes)
                onFileFinished(null)
            } catch (t: Throwable) {
                onFileFinished(t)
            }
        }

        fun spawnRemainingSegments(rest: List<SegmentProgress>) {
            for (seg in rest) {
                activeSegTasks.incrementAndGet()
                val submitted = submitSubTask {
                    var err: Throwable? = null
                    try {
                        runSegmentLoop(seg)
                    } catch (t: Throwable) {
                        err = t
                    } finally {
                        onSegmentTaskExited(err)
                    }
                }
                if (!submitted) {
                    onSegmentTaskExited(CancellationException())
                }
            }
        }

        val probeSeg = unfinished.first()
        val restSegs = unfinished.drop(1)

        if (alreadySegmented) {
            spawnRemainingSegments(restSegs)
            var err: Throwable? = null
            try {
                runSegmentLoop(probeSeg)
            } catch (t: Throwable) {
                err = t
            } finally {
                onSegmentTaskExited(err)
            }
            return
        }

        // Probe on the first unfinished segment before committing segmented mode
        var probeConn: HttpURLConnection? = null
        var probeCompleted = false
        try {
            segCheckCancelled()
            val reqStart = probeSeg.start + probeSeg.done
            val reqEnd = probeSeg.end
            val reqHeaders = LinkedHashMap<String, String>(headers)
            reqHeaders["Accept-Encoding"] = "identity"
            reqHeaders["Range"] = "bytes=$reqStart-$reqEnd"
            val resumeEtag = savedMeta?.etag
            if (startedWithPartial && hasCurrentUrlEtag.get() && isStrongEtag(resumeEtag)) {
                reqHeaders["If-Range"] = resumeEtag!!
            }
            probeConn = openFollowingRedirects(url, reqHeaders, fileAbort)
            val code = probeConn.responseCode
            probeConn.readTimeout = READ_SLICE_TIMEOUT_MS
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw IOException("HTTP $code")
            }
            val respEtag = probeConn.getHeaderField("ETag")?.trim()
            if (code == HttpURLConnection.HTTP_OK) {
                // Server doesn't support Range or If-Range invalidated -> stream full 200 OK sequentially!
                val cl = probeConn.contentLengthLong
                val enforcedExpected = if (expectedSize > 0L) expectedSize else totalBytes
                if (enforcedExpected > 0L && cl > 0L && cl != enforcedExpected) {
                    destPartFile.delete()
                    metaFile.delete()
                    throw IOException("Downloaded size mismatch: expected $enforcedExpected, got $cl")
                }
                val finalTotal = if (enforcedExpected > 0L) enforcedExpected else cl
                val existingPartBytes = if (destPartFile.isFile) destPartFile.length() else 0L
                val additionalNeeded = (finalTotal - existingPartBytes).coerceAtLeast(0L)
                if (additionalNeeded > 0L) {
                    ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, additionalNeeded)
                }
                writeSavedMeta(metaFile, url, respEtag, finalTotal, expectedSha256, segmented = false)
                val input = probeConn.inputStream
                var written = 0L
                try {
                    FileOutputStream(destPartFile, false).use { output ->
                        val buf = ByteArray(256 * 1024)
                        var idleReadMs = 0L
                        while (true) {
                            segCheckCancelled()
                            val n = try {
                                input.read(buf)
                            } catch (ste: java.net.SocketTimeoutException) {
                                segCheckCancelled()
                                idleReadMs += READ_SLICE_TIMEOUT_MS
                                if (idleReadMs >= TOTAL_READ_TIMEOUT_MS) throw ste
                                continue
                            }
                            idleReadMs = 0L
                            segCheckCancelled()
                            if (n < 0) break
                            if (n == 0) continue
                            if (enforcedExpected > 0L && written + n.toLong() > enforcedExpected) {
                                throw IOException("Downloaded size mismatch: expected $enforcedExpected, got >$enforcedExpected")
                            }
                            output.write(buf, 0, n)
                            written += n.toLong()
                            onProgressDelta(written, n.toLong(), finalTotal)
                        }
                        output.fd.sync()
                    }
                    probeCompleted = true
                } finally {
                    if (probeCompleted) {
                        try {
                            input.close()
                        } catch (_: Throwable) {
                        }
                    }
                }
                fileAbort.unregister(probeConn)
                probeConn.disconnect()
                probeConn = null
                batchAbort.removeChild(fileAbort)
                if (enforcedExpected > 0L && written != enforcedExpected) {
                    if (written < enforcedExpected && (cl <= 0L || cl == enforcedExpected)) {
                        runSingleStreamAndFinish()
                        return
                    }
                    destPartFile.delete()
                    metaFile.delete()
                    throw IOException("Downloaded size mismatch: expected $enforcedExpected, got $written")
                }
                if (finalTotal > 0L && written < finalTotal) {
                    runSingleStreamAndFinish()
                    return
                }
                if (!hashless) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                        destPartFile.delete()
                        metaFile.delete()
                        throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                    }
                }
                onProgressDelta(written, 0L, finalTotal)
                onFileFinished(null)
                return
            }

            if (code == HttpURLConnection.HTTP_PARTIAL) {
                val parsed = parseContentRangeFull(probeConn.getHeaderField("Content-Range"))
                val validRange = parsed != null &&
                    parsed.first == reqStart &&
                    parsed.second == reqEnd &&
                    (parsed.third <= 0L || parsed.third == totalBytes)
                val hashlessSafe = !hashless ||
                    (isStrongEtag(respEtag) && (!startedWithPartial || respEtag == resumeEtag))
                if (validRange && hashlessSafe) {
                    activeEtag.set(respEtag ?: activeEtag.get())
                    if (isStrongEtag(respEtag)) hasCurrentUrlEtag.set(true)
                    // Commit segmented meta BEFORE touching destPartFile so crash/cancel can never misread sparse file!
                    persistMeta(true)
                    if (!destPartFile.exists()) {
                        destPartFile.createNewFile()
                    }
                    spawnRemainingSegments(restSegs)
                    var segErr: Throwable? = null
                    try {
                        try {
                            streamIntoSegment(probeConn, probeSeg)
                            probeCompleted = true
                        } finally {
                            fileAbort.unregister(probeConn)
                            if (!probeCompleted && (fileAbort.isAborted || batchAbort.isAborted)) {
                                InstallAbortHandle.disconnectAsync(listOf(probeConn))
                            } else {
                                probeConn.disconnect()
                            }
                            probeConn = null
                        }
                        if (!probeSeg.isComplete) {
                            runSegmentLoop(probeSeg)
                        }
                    } catch (t: Throwable) {
                        segErr = t
                    } finally {
                        onSegmentTaskExited(segErr)
                    }
                    return
                }
            }

            // Fallback to single-stream for 416 / non-matching range / hashless without strong ETag
            fileAbort.unregister(probeConn)
            probeConn.disconnect()
            probeConn = null
            batchAbort.removeChild(fileAbort)
            if (code == 416) {
                destPartFile.delete()
                metaFile.delete()
            }
            runSingleStreamAndFinish()
        } catch (t: Throwable) {
            if (probeConn != null) {
                fileAbort.unregister(probeConn)
                if (!probeCompleted && (fileAbort.isAborted || batchAbort.isAborted)) {
                    InstallAbortHandle.disconnectAsync(listOf(probeConn))
                } else {
                    probeConn.disconnect()
                }
            }
            batchAbort.removeChild(fileAbort)
            val isTerminalProbeIo = t is IOException && (
                t.message == "HTTP 401" ||
                    t.message == "HTTP 403" ||
                    t.message?.startsWith("Downloaded size mismatch:") == true ||
                    t.message?.startsWith("SHA-256 verification failed") == true
                )
            if (t is CancellationException || t is GameList.InstallFailureException ||
                batchAbort.isAborted || isTerminalProbeIo
            ) {
                if (isTerminalProbeIo && t.message?.startsWith("Downloaded size mismatch:") == true) {
                    destPartFile.delete()
                    metaFile.delete()
                }
                onFileFinished(t)
            } else {
                // Transient probe network error: fall back to single-stream with full retry loop
                try {
                    runSingleStreamAndFinish()
                } catch (fallbackErr: Throwable) {
                    onFileFinished(fallbackErr)
                }
            }
        }
    }

    private fun downloadFileSingleStream(
        url: String,
        destPartFile: File,
        expectedSize: Long,
        expectedSha256: String?,
        headers: Map<String, String>,
        abortHandle: InstallAbortHandle?,
        checkCancelled: () -> Unit,
        onProgress: (doneBytes: Long, totalBytes: Long, speedBps: Long) -> Unit
    ) {
        destPartFile.parentFile?.mkdirs()
        val metaFile = File("${destPartFile.path}.meta")
        var knownTotal = expectedSize

        // Without a content hash, a partial file may only be resumed under a strong validator,
        // otherwise a changed remote file could be spliced onto the old prefix.
        val hashless = expectedSha256.isNullOrBlank()
        var savedMeta = readSavedMeta(metaFile, url, expectedSha256)
        if ((savedMeta == null && (metaFile.isFile || (hashless && destPartFile.isFile))) ||
            (hashless && destPartFile.isFile && !isStrongEtag(savedMeta?.etag))
        ) {
            destPartFile.delete()
            metaFile.delete()
            savedMeta = null
        }
        if (knownTotal <= 0L && savedMeta != null && savedMeta.totalBytes > 0L) {
            knownTotal = savedMeta.totalBytes
        }

        var lastIoError: Exception? = null
        var retriedCleanAfterCorruptPart = false
        var attempt = 0
        while (attempt < MAX_HTTP_RETRIES) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            checkCancelled()
            var curLen = if (destPartFile.isFile) destPartFile.length() else 0L
            if (knownTotal > 0L && curLen > knownTotal) {
                destPartFile.delete()
                metaFile.delete()
                curLen = 0L
            }
            if (knownTotal > 0L && curLen == knownTotal) {
                if (!expectedSha256.isNullOrBlank()) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                        onProgress(curLen, knownTotal, 0L)
                        return
                    }
                    // Pre-existing file matches expected size but failed SHA-256; delete and re-download.
                    destPartFile.delete()
                    metaFile.delete()
                    curLen = 0L
                } else {
                    lastIoError = null
                    break
                }
            }

            if (knownTotal > 0L) {
                ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, (knownTotal - curLen).coerceAtLeast(0L))
            }

            val resumeMeta = if (curLen > 0L) readSavedMeta(metaFile, url, expectedSha256) else null
            val resumeEtag = resumeMeta?.etag
            val sameResumeUrl = resumeMeta?.url == url
            if (hashless && curLen > 0L && !isStrongEtag(resumeEtag)) {
                destPartFile.delete()
                metaFile.delete()
                curLen = 0L
            }

            val startedWithPartial = curLen > 0L
            var conn: HttpURLConnection? = null
            var completedNormally = false
            try {
                val reqHeaders = LinkedHashMap<String, String>(headers)
                reqHeaders["Accept-Encoding"] = "identity"
                if (curLen > 0L) {
                    reqHeaders["Range"] = "bytes=$curLen-"
                    if (sameResumeUrl && isStrongEtag(resumeEtag)) {
                        reqHeaders["If-Range"] = resumeEtag!!
                    }
                }

                conn = openFollowingRedirects(url, reqHeaders, abortHandle)
                val code = conn.responseCode
                conn.readTimeout = READ_SLICE_TIMEOUT_MS
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw IOException("HTTP $code")
                }

                val respEtag = conn.getHeaderField("ETag")?.trim()
                val appendMode: Boolean

                when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val parsed = parseContentRange(conn.getHeaderField("Content-Range"))
                        val sameRemoteFile = !hashless ||
                            (curLen > 0L && isStrongEtag(respEtag) && respEtag == resumeEtag)
                        if (parsed == null || parsed.first != curLen || !sameRemoteFile) {
                            abortHandle?.unregister(conn)
                            conn.disconnect()
                            conn = null
                            destPartFile.delete()
                            metaFile.delete()
                            attempt++
                            continue
                        }
                        if (parsed.second > 0L) {
                            knownTotal = parsed.second
                        } else {
                            val cl = conn.contentLengthLong
                            if (cl > 0L && knownTotal <= 0L) {
                                knownTotal = curLen + cl
                            }
                        }
                        appendMode = true
                    }
                    HttpURLConnection.HTTP_OK -> {
                        curLen = 0L
                        appendMode = false
                        val cl = conn.contentLengthLong
                        if (expectedSize > 0L && cl > 0L && cl != expectedSize) {
                            destPartFile.delete()
                            metaFile.delete()
                            throw IOException("Downloaded size mismatch: expected $expectedSize, got $cl")
                        }
                        if (expectedSize <= 0L && cl > 0L) {
                            knownTotal = cl
                        }
                    }
                    416 -> {
                        val cr = conn.getHeaderField("Content-Range")
                        val slashTotal = cr?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: -1L
                        if (slashTotal > 0L && curLen == slashTotal) {
                            knownTotal = slashTotal
                            completedNormally = true
                            abortHandle?.unregister(conn)
                            conn.disconnect()
                            conn = null
                            if (!expectedSha256.isNullOrBlank()) {
                                val actualSha = computeFileSha256(destPartFile, checkCancelled)
                                if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                                    onProgress(curLen, knownTotal, 0L)
                                    return
                                }
                                destPartFile.delete()
                                metaFile.delete()
                                if (expectedSize > 0L) knownTotal = expectedSize
                                if (!retriedCleanAfterCorruptPart) {
                                    retriedCleanAfterCorruptPart = true
                                    continue
                                }
                                throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                            }
                            lastIoError = null
                            break
                        }
                        destPartFile.delete()
                        metaFile.delete()
                        attempt++
                        continue
                    }
                    else -> throw IOException("Unexpected HTTP status $code")
                }

                if (knownTotal > 0L) {
                    ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, (knownTotal - curLen).coerceAtLeast(0L))
                }
                val metaEtag = if (appendMode) respEtag ?: resumeEtag else respEtag
                writeSavedMeta(metaFile, url, metaEtag, knownTotal, expectedSha256)

                var speedWindowStartMs = SystemClock.elapsedRealtime()
                var speedWindowBytes = 0L
                var currentSpeedBps = 0L
                var lastUiReportMs = 0L

                val input = conn.inputStream
                try {
                    FileOutputStream(destPartFile, appendMode).use { output ->
                        val buf = ByteArray(256 * 1024)
                        var idleReadMs = 0L
                        while (true) {
                            if (abortHandle?.isAborted == true) throw CancellationException()
                            checkCancelled()
                            val n = try {
                                input.read(buf)
                            } catch (ste: java.net.SocketTimeoutException) {
                                if (abortHandle?.isAborted == true) throw CancellationException()
                                checkCancelled()
                                idleReadMs += READ_SLICE_TIMEOUT_MS
                                if (idleReadMs >= TOTAL_READ_TIMEOUT_MS) {
                                    throw ste
                                }
                                continue
                            }
                            idleReadMs = 0L
                            if (abortHandle?.isAborted == true) throw CancellationException()
                            checkCancelled()
                            if (n < 0) break
                            if (n == 0) continue
                            output.write(buf, 0, n)
                            curLen += n
                            speedWindowBytes += n

                            val now = SystemClock.elapsedRealtime()
                            val elapsedWindow = now - speedWindowStartMs
                            if (elapsedWindow >= 600L) {
                                currentSpeedBps = (speedWindowBytes * 1000L) / elapsedWindow
                                speedWindowStartMs = now
                                speedWindowBytes = 0L
                            }
                            if (now - lastUiReportMs >= 150L) {
                                lastUiReportMs = now
                                onProgress(curLen, knownTotal, currentSpeedBps)
                            }
                        }
                        output.fd.sync()
                    }
                    completedNormally = true
                } finally {
                    if (completedNormally) {
                        try {
                            input.close()
                        } catch (_: Throwable) {
                        }
                    }
                }

                onProgress(curLen, knownTotal, currentSpeedBps)

                if (knownTotal > 0L && curLen < knownTotal) {
                    throw IOException("Premature EOF at $curLen / $knownTotal bytes")
                }

                if (!expectedSha256.isNullOrBlank()) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                        destPartFile.delete()
                        metaFile.delete()
                        if (expectedSize > 0L) {
                            knownTotal = expectedSize
                        }
                        if (startedWithPartial && !retriedCleanAfterCorruptPart) {
                            retriedCleanAfterCorruptPart = true
                            continue
                        }
                        throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                    }
                }

                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: GameList.InstallFailureException) {
                throw e
            } catch (e: Exception) {
                if (abortHandle?.isAborted == true) throw CancellationException()
                checkCancelled()
                lastIoError = e
                if (attempt + 1 < MAX_HTTP_RETRIES) {
                    sleepCancellable(1000L * (attempt + 1).coerceAtMost(5), checkCancelled)
                }
                attempt++
            } finally {
                abortHandle?.unregister(conn)
                if (conn != null) {
                    if (!completedNormally && abortHandle?.isAborted == true) {
                        InstallAbortHandle.disconnectAsync(listOf(conn))
                    } else {
                        conn.disconnect()
                    }
                }
            }
        }

        if (lastIoError != null) {
            val finalLen = if (destPartFile.isFile) destPartFile.length() else 0L
            if (knownTotal <= 0L || finalLen < knownTotal || !expectedSha256.isNullOrBlank()) {
                throw lastIoError
            }
        }

        if (knownTotal > 0L && (!destPartFile.isFile || destPartFile.length() != knownTotal)) {
            throw IOException("Downloaded size mismatch: expected $knownTotal, got ${destPartFile.length()}")
        }

        if (!expectedSha256.isNullOrBlank()) {
            val actualSha = computeFileSha256(destPartFile, checkCancelled)
            if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                destPartFile.delete()
                metaFile.delete()
                throw IOException("SHA-256 verification failed for ${destPartFile.name}")
            }
        }
    }

    internal fun openFollowingRedirects(
        initialUrl: String,
        headers: Map<String, String>,
        abortHandle: InstallAbortHandle? = null,
        readTimeoutMs: Int = READ_SLICE_TIMEOUT_MS
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)

        for (hop in 0..MAX_REDIRECTS) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
            }
            abortHandle?.register(conn)
            val code = try {
                conn.responseCode
            } catch (t: Throwable) {
                abortHandle?.unregister(conn)
                InstallAbortHandle.disconnectAsync(listOf(conn))
                throw t
            }
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                abortHandle?.unregister(conn)
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    throw IOException("Redirect $code missing Location header")
                }
                currentUrl = URL(currentUrl, loc)
                continue
            }
            return conn
        }
        throw IOException("Too many HTTP redirects")
    }

    private fun parseContentRangeFull(header: String?): Triple<Long, Long, Long>? {
        if (header.isNullOrBlank()) return null
        val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
            .find(header.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val end = m.groupValues[2].toLongOrNull() ?: return null
        val total = m.groupValues[3].toLongOrNull() ?: -1L
        return Triple(start, end, total)
    }

    private fun parseContentRange(header: String?): Pair<Long, Long>? {
        val full = parseContentRangeFull(header) ?: return null
        return full.first to full.third
    }

    private fun ensureAvailableSpace(dir: File, neededBytes: Long) {
        try {
            val avail = StatFs(dir.path).availableBytes - HEADROOM_BYTES
            if (neededBytes > avail) {
                throw GameList.InstallFailureException(R.string.import_no_space)
            }
        } catch (e: GameList.InstallFailureException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private fun readSavedMeta(metaFile: File, expectedUrl: String, expectedSha256: String?): SavedMeta? {
        if (!metaFile.isFile) return null
        return try {
            val j = JSONObject(metaFile.readText())
            val savedSha = j.optString("sha256")
            val shaMatched = !expectedSha256.isNullOrBlank() &&
                savedSha.isNotEmpty() &&
                savedSha.equals(expectedSha256, ignoreCase = true)
            if (!shaMatched && j.optString("url") != expectedUrl) return null
            val totalBytes = j.optLong("totalBytes", -1L)
            val segmented = j.optBoolean("segmented", false)
            val segments = if (segmented) {
                if (totalBytes <= 0L) return null
                val arr = j.optJSONArray("segments") ?: return null
                if (arr.length() == 0) return null
                val list = ArrayList<SegmentProgress>(arr.length())
                var expectedStart = 0L
                for (i in 0 until arr.length()) {
                    val segArr = arr.optJSONArray(i) ?: return null
                    if (segArr.length() < 3) return null
                    val s = segArr.optLong(0, -1L)
                    val e = segArr.optLong(1, -1L)
                    val d = segArr.optLong(2, -1L)
                    if (s != expectedStart || e < s) return null
                    val len = e - s + 1L
                    if (d < 0L || d > len) return null
                    list.add(SegmentProgress(s, e, d))
                    expectedStart = e + 1L
                }
                if (expectedStart != totalBytes) return null
                list
            } else {
                emptyList()
            }
            SavedMeta(
                url = j.optString("url").takeIf { it.isNotEmpty() },
                etag = j.optString("etag").takeIf { it.isNotEmpty() },
                totalBytes = totalBytes,
                segmented = segmented,
                segments = segments
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeSavedMeta(
        metaFile: File,
        url: String,
        etag: String?,
        totalBytes: Long,
        sha256: String?,
        segmented: Boolean = false,
        segments: List<SegmentProgress> = emptyList()
    ) {
        try {
            val j = JSONObject().put("url", url)
            if (!etag.isNullOrEmpty()) j.put("etag", etag)
            if (totalBytes > 0L) j.put("totalBytes", totalBytes)
            if (!sha256.isNullOrBlank()) j.put("sha256", sha256)
            if (segmented && segments.isNotEmpty()) {
                j.put("segmented", true)
                val arr = JSONArray()
                for (seg in segments) {
                    arr.put(JSONArray().put(seg.start).put(seg.end).put(seg.done))
                }
                j.put("segments", arr)
            }
            val tmp = File("${metaFile.path}.tmp")
            FileOutputStream(tmp, false).use { out ->
                out.write(j.toString().toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(metaFile)) {
                tmp.copyTo(metaFile, overwrite = true)
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun computeFileSha256(file: File, checkCancelled: () -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                checkCancelled()
                val n = fis.read(buf)
                if (n < 0) break
                if (n > 0) md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun extractArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") -> ".tar.gz"
            lower.endsWith(".tar.xz") -> ".tar.xz"
            lower.endsWith(".rar") -> ".rar"
            lower.endsWith(".7z") -> ".7z"
            lower.endsWith(".zip") -> ".zip"
            lower.endsWith(".tgz") -> ".tgz"
            lower.endsWith(".txz") -> ".txz"
            else -> ".rar"
        }
    }

    internal fun formatSpeed(bps: Long): String {
        if (bps <= 0L) return "-- MB/s"
        val mbps = bps.toDouble() / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbps)
        } else {
            val kbps = bps.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB/s", kbps)
        }
    }

    private fun sleepCancellable(ms: Long, checkCancelled: () -> Unit) {
        var remaining = ms
        while (remaining > 0L) {
            checkCancelled()
            val step = remaining.coerceAtMost(200L)
            Thread.sleep(step)
            remaining -= step
        }
    }
}
