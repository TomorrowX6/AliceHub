package io.github.kichikuou.xsystem4

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale
import java.util.UUID

object AppStorage {
    private const val GAMES = "games"
    private const val SAVES = "saves"
    private const val SAVE_BACKUPS = "save_backups"
    private const val PATH_PREFS = "public_storage_paths"
    private const val MIGRATION_ROOT_KEY = "completed_root"
    private const val MIGRATION_STAGING_KEY = "active_staging"
    private const val MIGRATION_STAGING_PREFIX = ".alicehub-migration-"

    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager(Environment.getExternalStorageDirectory())
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun publicRoot(context: Context): File? =
        if (AppSettings.prefersPublicStorage(context) && hasPermission(context) &&
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        ) {
            sharedStorageDirectory()
        } else {
            null
        }

    private fun sharedStorageDirectory(): File = File(Environment.getExternalStorageDirectory(), "AliceHub")

    fun isSharedSaveDocument(uri: Uri, tree: Boolean = false): Boolean {
        if (uri.authority != "com.android.externalstorage.documents") return false
        val id = if (tree) DocumentsContract.getTreeDocumentId(uri) else DocumentsContract.getDocumentId(uri)
        if (!id.startsWith("primary:")) return false
        val relativePath = id.removePrefix("primary:")
        if (relativePath.isEmpty()) return false
        val destination = File(Environment.getExternalStorageDirectory(), relativePath).canonicalPath.lowercase(Locale.ROOT)
        val saves = File(sharedStorageDirectory(), SAVES).canonicalPath.lowercase(Locale.ROOT)
        return destination == saves || destination.startsWith(saves + File.separator)
    }

    private fun mountedLegacyGameRoots(context: Context): List<File> =
        context.getExternalFilesDirs(null).filterNotNull().filter {
            val state = Environment.getExternalStorageState(it)
            state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
        }

    fun gameRoots(context: Context): List<File> {
        val legacy = mountedLegacyGameRoots(context)
        val internal = File(context.filesDir, GAMES)
        val shared = publicGamesRoot(context)
        val first = shared ?: legacy.firstOrNull {
            Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        } ?: internal.apply { mkdirs() }
        return (listOf(first) + legacy + listOfNotNull(internal.takeIf { it.isDirectory })).distinct()
    }

    fun publicGamesRoot(context: Context): File? =
        publicRoot(context)?.let { File(it, GAMES) }?.takeIf { it.isDirectory }

    fun savesRoot(context: Context): File = dataRoot(context, SAVES)

    fun saveRoots(context: Context): List<File> =
        listOf(File(context.filesDir, SAVES), savesRoot(context)).distinct()

    fun saveDir(context: Context, name: String): File = File(dataHome(context, SAVES, name), "$SAVES/$name")

    fun backupDir(context: Context, name: String): File {
        val safeName = File(name).name
        return File(dataHome(context, SAVE_BACKUPS, safeName), "$SAVE_BACKUPS/$safeName")
    }

    private fun dataRoot(context: Context, name: String): File =
        publicRoot(context)?.let { File(it, name) }?.takeIf { it.isDirectory } ?: File(context.filesDir, name)

    private fun dataHome(context: Context, root: String, name: String): File {
        val legacy = File(context.filesDir, "$root/$name")
        val shared = dataRoot(context, root)
        val emptyLegacyWithSharedData = shared != legacy.parentFile &&
            File(shared, name).list()?.isNotEmpty() == true && legacy.list()?.isEmpty() == true
        return if (legacy.exists() && !emptyLegacyWithSharedData) context.filesDir
        else shared.parentFile ?: context.filesDir
    }

    fun resolveGamePath(context: Context, path: String): String {
        if (publicRoot(context) == null || File(path).exists()) return path
        val moved = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE).getString(path, null)
        return moved?.takeIf { File(it).isDirectory } ?: path
    }

    fun resolveSavePath(context: Context, path: String): String {
        val from = File(context.filesDir, SAVES).toPath()
        val candidate = File(path).toPath().normalize()
        if (!candidate.startsWith(from) || candidate == from) return path
        val suffix = from.relativize(candidate)
        val saveName = suffix.getName(0).toString()
        val resolved = saveDir(context, saveName).toPath()
        return if (suffix.nameCount == 1) resolved.toString()
        else resolved.resolve(suffix.subpath(1, suffix.nameCount)).toString()
    }

    fun needsPublicStoragePreparation(context: Context): Boolean {
        val root = publicRoot(context) ?: return false
        val preparedRoot = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
            .getString(MIGRATION_ROOT_KEY, null)
        return preparedRoot != root.absolutePath || !publicLayoutExists(context)
    }

    fun publicLayoutExists(context: Context): Boolean = publicRoot(context)?.let { root ->
        listOf(GAMES, SAVES, SAVE_BACKUPS).all { File(root, it).isDirectory }
    } == true

    fun resetPublicStoragePreparation(context: Context) {
        val prefs = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(MIGRATION_ROOT_KEY)) prefs.edit().remove(MIGRATION_ROOT_KEY).apply()
    }

    @Synchronized
    fun preparePublicStorage(context: Context): Boolean {
        val shared = publicRoot(context) ?: return true
        val games = File(shared, GAMES)
        val backup = File(shared, SAVE_BACKUPS)
        val saves = File(shared, SAVES)
        var complete = true
        val paths = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
        paths.getString(MIGRATION_STAGING_KEY, null)?.let { path ->
            val staging = File(path)
            try {
                if (staging.parentFile in listOf(games, backup, saves) &&
                    staging.name.startsWith(MIGRATION_STAGING_PREFIX)
                ) deleteStaging(staging)
                forgetStaging(paths)
            } catch (e: IOException) {
                Log.w("AppStorage", "Could not remove interrupted migration staging", e)
                return false
            }
        }
        fun migrateRoot(source: File, target: File, include: (File) -> Boolean = { true }) {
            if (!target.isDirectory && !target.mkdirs()) {
                complete = false
                return
            }
            if (!source.exists()) return
            val entries = source.listFiles()
            if (entries == null) {
                complete = false
                return
            }
            for (entry in entries.filter { !it.name.startsWith(".migrated-") && include(it) }) {
                val destination = File(target, entry.name)
                try {
                    if (target != games && destination.isDirectory &&
                        Files.isDirectory(entry.toPath(), LinkOption.NOFOLLOW_LINKS) && entry.list()?.isEmpty() == true
                    ) {
                        Files.deleteIfExists(entry.toPath())
                        continue
                    }
                    val migrated = migrateEntry(entry, destination, paths) {
                        if (target == games && !entry.name.startsWith('.')) {
                            System4BootEntry.copySelectedEntry(context, entry, destination)
                            if (!paths.edit().putString(entry.absolutePath, destination.absolutePath).commit()) {
                                throw IOException("Could not record migrated game path")
                            }
                        }
                    }
                    if (!migrated) complete = false
                } catch (e: IOException) {
                    Log.w("AppStorage", "Could not migrate $entry", e)
                    complete = false
                } catch (e: SecurityException) {
                    Log.w("AppStorage", "Could not migrate $entry", e)
                    complete = false
                }
            }
        }
        migrateRoot(File(context.filesDir, SAVES), saves)
        migrateRoot(File(context.filesDir, SAVE_BACKUPS), backup)
        val internalGames = File(context.filesDir, GAMES)
        for (legacy in mountedLegacyGameRoots(context) + internalGames) {
            if (legacy.isDirectory && legacy.canWrite()) OnlineInstaller.recoverInterruptedReinstalls(legacy)
            migrateRoot(legacy, games) {
                (it.isDirectory && !it.name.startsWith('.') &&
                    (legacy == internalGames || it.name != Environment.DIRECTORY_PICTURES)) ||
                    it.name == ".xsystem4" || it.name.startsWith(".online_base_staging_") ||
                    it.name == ".online_mod_cache" || it.name == UncensoredPatchManager.CACHE_DIR_NAME
            }
        }
        if (complete) paths.edit().putString(MIGRATION_ROOT_KEY, shared.absolutePath).apply()
        return complete
    }

    @JvmStatic
    @Throws(IOException::class)
    fun migrateEntry(source: File, destination: File): Boolean = migrateEntry(source, destination, null) {}

    private fun migrateEntry(
        source: File,
        destination: File,
        paths: SharedPreferences?,
        beforeMove: () -> Unit
    ): Boolean {
        if (!exists(source)) return true
        if (Files.isSymbolicLink(source.toPath())) return false
        val alreadyCopied = exists(destination)
        if (alreadyCopied && !sameContents(source, destination)) return false
        beforeMove()
        if (!alreadyCopied) {
            val sameFileStore = runCatching {
                Files.getFileStore(source.toPath()) == Files.getFileStore(destination.parentFile!!.toPath())
            }.getOrDefault(false)
            if (sameFileStore) {
                try {
                    Files.move(source.toPath(), destination.toPath())
                    return true
                } catch (e: IOException) {
                    if (exists(destination) || !exists(source)) throw e
                }
            }
            val staging = createStaging(destination.parentFile!!, paths)
            try {
                val copied = File(staging, "data")
                copyEntry(source, copied)
                if (!sameContents(source, copied)) return false
                Files.move(copied.toPath(), destination.toPath())
            } finally {
                deleteStaging(staging)
                if (paths != null) forgetStaging(paths)
            }
        }
        val retired = Files.createTempDirectory(source.parentFile!!.toPath(), ".migrated-").toFile()
        try {
            Files.move(source.toPath(), File(retired, "data").toPath())
        } finally {
            retired.deleteRecursively()
        }
        return true
    }

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun createStaging(parent: File, paths: SharedPreferences?): File {
        if (paths == null) return Files.createTempDirectory(parent.toPath(), MIGRATION_STAGING_PREFIX).toFile()
        val staging = File(parent, MIGRATION_STAGING_PREFIX + UUID.randomUUID())
        if (exists(staging) || paths.contains(MIGRATION_STAGING_KEY) ||
            !paths.edit().putString(MIGRATION_STAGING_KEY, staging.absolutePath).commit()
        ) throw IOException("Could not record migration staging")
        try {
            Files.createDirectory(staging.toPath())
        } catch (e: IOException) {
            forgetStaging(paths)
            throw e
        }
        return staging
    }

    private fun forgetStaging(paths: SharedPreferences) {
        if (!paths.edit().remove(MIGRATION_STAGING_KEY).commit()) throw IOException("Could not clear migration staging")
    }

    private fun deleteStaging(file: File) {
        if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            for (child in file.listFiles() ?: throw IOException("Cannot list $file")) deleteStaging(child)
        }
        Files.deleteIfExists(file.toPath())
    }

    private fun copyEntry(source: File, destination: File) {
        when {
            Files.isSymbolicLink(source.toPath()) -> throw IOException("Unexpected symbolic link: $source")
            source.isDirectory -> {
                Files.createDirectory(destination.toPath())
                for (child in source.listFiles() ?: throw IOException("Cannot list $source")) {
                    copyEntry(child, File(destination, child.name))
                }
                destination.setLastModified(source.lastModified())
            }
            source.isFile -> {
                Files.copy(source.toPath(), destination.toPath())
                destination.setLastModified(source.lastModified())
            }
            else -> throw IOException("Cannot copy $source")
        }
    }

    private fun sameContents(first: File, second: File): Boolean {
        if (Files.isSymbolicLink(first.toPath()) || Files.isSymbolicLink(second.toPath())) return false
        if (first.isDirectory && second.isDirectory) {
            val children = first.listFiles() ?: return false
            val others = second.list()?.toSet() ?: return false
            return children.size == others.size && children.all {
                it.name in others && sameContents(it, File(second, it.name))
            }
        }
        if (!first.isFile || !second.isFile || first.length() != second.length()) return false
        return DataInputStream(first.inputStream().buffered()).use { a ->
            DataInputStream(second.inputStream().buffered()).use { b ->
                val expected = ByteArray(65536)
                val actual = ByteArray(expected.size)
                var left = first.length()
                while (left > 0) {
                    val count = minOf(left, expected.size.toLong()).toInt()
                    a.readFully(expected, 0, count)
                    b.readFully(actual, 0, count)
                    if (!expected.contentEquals(actual)) return false
                    left -= count
                }
                a.read() == -1 && b.read() == -1
            }
        }
    }
}
