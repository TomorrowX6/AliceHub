package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.util.UUID

/** Copies a SAF tree into app storage; the selected documents are only read. */
object NativeGameImport {
    private data class Document(val uri: Uri, val name: String, val folder: Boolean, val size: Long)

    data class TreeEntry<T, D>(val id: T, val name: String, val folder: Boolean, val payload: D)

    data class DiscoveredImportSources<T, D>(
        val primaryEntries: List<TreeEntry<T, D>>?,
        val sourceTrees: List<Triple<T, List<TreeEntry<T, D>>, String>>,
        val ixsealPatchTrees: List<List<TreeEntry<T, D>>>
    )

    fun skip(path: String): Boolean {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        val fileName = parts.last()
        // System 3.x games (e.g. Kichikuou Rance CN) rely on .xsys35rc for playlist/font/encoding config.
        val isXsys35RcLeaf = fileName.equals(".xsys35rc", true)
        if (parts.dropLast(1).any {
                it.startsWith('.') ||
                    it.equals("android-port", true) ||
                    it.equals("SaveData", true) ||
                    it.equals("DirectX", true)
            }) {
            return true
        }
        if ((fileName.startsWith('.') && !isXsys35RcLeaf) ||
            fileName.equals("android-port", true) ||
            fileName.equals("SaveData", true) ||
            fileName.equals("DirectX", true)
        ) {
            return true
        }
        val lowerName = fileName.lowercase()
        if (lowerName == "opensavefolder.exe" || lowerName == "resetconfig.exe" || lowerName == "uninstaller.exe")
            return true
        return fileName.substringAfterLast('.', "").lowercase() in
                setOf("dll", "lnk", "bat", "cmd", "ps1", "mds")
    }

    private fun children(context: Context, tree: Uri, parent: Uri): List<Document> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE)
        val result = mutableListOf<Document>()
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
            ?: throw IOException("Cannot read selected directory")
        cursor.use {
            while (it.moveToNext()) {
                result.add(Document(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)),
                    it.getString(1), it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (it.isNull(3)) -1 else it.getLong(3)))
            }
        }
        return result
    }

    private fun <T, D> hasConfiguredTreeScript(
        codeName: String,
        docs: List<TreeEntry<T, D>>,
        listChildren: ((T) -> List<TreeEntry<T, D>>)?
    ): Boolean {
        val path = codeName.replace('\\', '/')
        if (path.startsWith("/") || path.contains('\u0000')) return false
        val components = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (components.isEmpty() || components.first().contains(':') || components.any { it == ".." }) return false
        val leafLower = components.last().lowercase()
        val validLeaf = (leafLower.endsWith(".ain") && leafLower != "system39.ain") ||
            (leafLower.endsWith(".chs") && leafLower != "alicestart.chs")
        if (!validLeaf) return false

        var current = docs
        for (i in components.indices) {
            val part = components[i]
            val match = current.firstOrNull { !skip(it.name) && it.name == part }
                ?: current.firstOrNull { !skip(it.name) && it.name.equals(part, ignoreCase = true) }
                ?: current.firstOrNull {
                    !skip(it.name) && ArchiveExtractor.repairSjisAsGbkMojibake(it.name).equals(part, ignoreCase = true)
                }
                ?: return false
            if (i == components.lastIndex) {
                return !match.folder
            }
            if (!match.folder || listChildren == null) return false
            current = listChildren(match.id)
        }
        return false
    }

    private fun <T, D> isSys4RootEntries(
        docs: List<TreeEntry<T, D>>,
        readSmallEntry: ((TreeEntry<T, D>) -> ByteArray?)? = null,
        listChildren: ((T) -> List<TreeEntry<T, D>>)? = null
    ): Boolean {
        val activeDocs = docs.filter { !it.folder && !skip(it.name) }
        val activeNames = activeDocs.map { it.name }
        val hasIni = activeNames.any { GameCatalog.isSystem4LaunchIni(it) }
        val readSmallByName: ((String) -> ByteArray?)? = readSmallEntry?.let { reader ->
            { targetName ->
                activeDocs.firstOrNull { it.name.equals(targetName, true) }?.let(reader)
            }
        }
        if (hasIni && activeNames.any { GameCatalog.isSystem4CodeFile(it, activeNames, readSmallByName) }) {
            return true
        }
        if (readSmallEntry != null) {
            for (doc in activeDocs) {
                if (!System4BootEntry.isCandidateIniFile(doc.name)) continue
                val raw = runCatching { readSmallEntry(doc) }.getOrNull() ?: continue
                if (System4BootEntry.extractCodeNames(raw).any { hasConfiguredTreeScript(it, docs, listChildren) }) {
                    return true
                }
            }
        }
        return false
    }

    private fun <T, D> isIxsealPatchRootEntries(
        docs: List<TreeEntry<T, D>>,
        readSmallEntry: ((TreeEntry<T, D>) -> ByteArray?)? = null,
        listChildren: ((T) -> List<TreeEntry<T, D>>)? = null
    ): Boolean {
        if (isSys4RootEntries(docs, readSmallEntry, listChildren)) return true
        return docs.any {
            !it.folder && !skip(it.name) &&
                (it.name.equals("Ixseal.ain", true) || it.name.equals("IxsealCG.afa", true))
        }
    }

    private fun <T, D> isSys3RootEntries(
        docs: List<TreeEntry<T, D>>,
        readSmallEntry: ((TreeEntry<T, D>) -> ByteArray?)? = null,
        listChildren: ((T) -> List<TreeEntry<T, D>>)? = null
    ): Boolean {
        if (isSys4RootEntries(docs, readSmallEntry, listChildren)) return false
        val names = docs.filter { !it.folder && !skip(it.name) }.map { it.name }
        return names.any(GameCatalog::isSystem35Scenario) || GameCatalog.isLegacySystem3GameFiles(names)
    }

    @JvmOverloads
    fun <T, D> discoverImportSources(
        rootId: T,
        rootEntries: List<TreeEntry<T, D>>,
        listChildren: (T) -> List<TreeEntry<T, D>>,
        readSmallEntry: ((TreeEntry<T, D>) -> ByteArray?)? = null
    ): DiscoveredImportSources<T, D> {
        var sourceTrees: List<Triple<T, List<TreeEntry<T, D>>, String>> = emptyList()
        var primaryEntries: List<TreeEntry<T, D>>? = null
        val ixsealPatchTrees = mutableListOf<List<TreeEntry<T, D>>>()
        val seenPatchIds = HashSet<T>()

        fun isBackupOrPatchContainer(name: String): Boolean {
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(name)
            return name.equals("backup", ignoreCase = true) ||
                name.equals("BackData", ignoreCase = true) ||
                name == "补丁" ||
                name == "汉化补丁" ||
                name == "解答集" ||
                name == "备份" ||
                name == "备份文件" ||
                name == "原版有码备份" ||
                name == "无码补丁备份" ||
                name == "汉化补丁备份" ||
                name == "特典相关" ||
                name == "全CG存档" ||
                name == "広告" ||
                name == "广告" ||
                repaired == "広告" ||
                name.equals("patchWM", ignoreCase = true)
        }

        fun collectPatchCn(entries: List<TreeEntry<T, D>>) {
            val patchQueue = ArrayDeque<Pair<T, Int>>()
            entries.filter { it.folder && !skip(it.name) && it.name.equals("patchCN", ignoreCase = true) }
                .sortedBy { it.name }
                .forEach { patchQueue.add(it.id to 1) }
            while (patchQueue.isNotEmpty()) {
                val (dirId, depth) = patchQueue.removeFirst()
                val docs = listChildren(dirId)
                if (isIxsealPatchRootEntries(docs, readSmallEntry, listChildren)) {
                    if (seenPatchIds.add(dirId)) {
                        ixsealPatchTrees.add(docs)
                    }
                } else if (depth < 3) {
                    docs.filter { it.folder && !skip(it.name) }
                        .sortedBy { it.name }
                        .forEach { patchQueue.add(it.id to (depth + 1)) }
                }
            }
        }

        if (isSys4RootEntries(rootEntries, readSmallEntry, listChildren) || isSys3RootEntries(rootEntries, readSmallEntry, listChildren)) {
            primaryEntries = rootEntries
            sourceTrees = listOf(Triple(rootId, rootEntries, ""))
            if (isSys4RootEntries(rootEntries, readSmallEntry, listChildren)) {
                collectPatchCn(rootEntries)
            }
        } else {
            // Also support picking a container folder holding Disc1/GAMEDATA .. Disc3/GAMEDATA
            // or sibling companion folders such as GALZOO/ + 三合一/Data/.
            val discRoots = mutableListOf<Triple<T, List<TreeEntry<T, D>>, String>>()
            val queue = ArrayDeque<Triple<T, String, Int>>()
            collectPatchCn(rootEntries)
            for (d in rootEntries) {
                if (!d.folder || skip(d.name)) continue
                if (
                    !d.name.equals("patchCN", ignoreCase = true) &&
                    !d.name.equals("DLL", ignoreCase = true) &&
                    !d.name.equals("OLD", ignoreCase = true) &&
                    !isBackupOrPatchContainer(d.name)
                ) {
                    queue.add(Triple(d.id, d.name, 1))
                }
            }
            while (queue.isNotEmpty()) {
                val (dirId, dirName, depth) = queue.removeFirst()
                val docs = listChildren(dirId)
                if (isSys4RootEntries(docs, readSmallEntry, listChildren) || isSys3RootEntries(docs, readSmallEntry, listChildren)) {
                    if (primaryEntries == null && GameCatalog.isLegacySystem3GameFiles(docs.filter { !it.folder }.map { it.name })) {
                        return DiscoveredImportSources(docs, listOf(Triple(dirId, docs, "")), emptyList())
                    }
                    if (primaryEntries == null) primaryEntries = docs
                    discRoots.add(Triple(dirId, docs, ""))
                    if (isSys4RootEntries(docs, readSmallEntry, listChildren)) {
                        collectPatchCn(docs)
                    }
                } else if (dirName.equals("Data", ignoreCase = true) &&
                    docs.any {
                        !it.folder && !skip(it.name) && (
                            it.name.equals("PolyObj.lin", true) ||
                                it.name.equals("Event.alk", true) ||
                                it.name.endsWith(".dgn", true)
                            )
                    }
                ) {
                    discRoots.add(Triple(dirId, docs, "Data"))
                } else if (docs.any {
                        !it.folder && !skip(it.name) && (it.name.endsWith(".ald", true) ||
                            it.name.endsWith(".bgi", true) || it.name.endsWith(".alk", true))
                    }
                ) {
                    discRoots.add(Triple(dirId, docs, ""))
                } else if (depth < 3) {
                    collectPatchCn(docs)
                    for (sub in docs) {
                        if (sub.folder && !skip(sub.name) &&
                            !sub.name.equals("DLL", ignoreCase = true) &&
                            !sub.name.equals("OLD", ignoreCase = true) &&
                            !isBackupOrPatchContainer(sub.name) &&
                            !sub.name.equals("patchCN", ignoreCase = true)
                        ) {
                            queue.add(Triple(sub.id, sub.name, depth + 1))
                        }
                    }
                }
            }
            if (primaryEntries != null) {
                sourceTrees = discRoots
            }
        }

        return DiscoveredImportSources(primaryEntries, sourceTrees, ixsealPatchTrees)
    }

    @JvmOverloads
    fun resolveCopyTreeBaseName(
        primaryFileNames: Collection<String>,
        detectedGameId: String?,
        targetGameId: String? = null,
        isMultiDiscSys3: Boolean = false,
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): String {
        val isDaiakujiImport = targetGameId == GameCatalog.ID_DAIAKUJI ||
            detectedGameId == GameCatalog.ID_DAIAKUJI
        val isHarukaImport = targetGameId == GameCatalog.ID_HARUKA ||
            detectedGameId == GameCatalog.ID_HARUKA
        return when {
            isDaiakujiImport -> "Daiakuji"
            isHarukaImport -> "Haruka"
            detectedGameId == GameCatalog.ID_YAKATA2 -> "Yakata2"
            detectedGameId == GameCatalog.ID_YAKATA1 -> "Yakata1"
            detectedGameId == GameCatalog.ID_LITTLEVAMPIRE -> "LittleVampire"
            detectedGameId == GameCatalog.ID_INTRUDER -> "Intruder"
            detectedGameId == GameCatalog.ID_CRESCENT -> "CrescentMoonGirl"
            detectedGameId == GameCatalog.ID_TENGU -> "AbunaiTengu"
            detectedGameId == GameCatalog.ID_DPS -> "DPS"
            detectedGameId == GameCatalog.ID_SDPSTONO -> "SuperDpsTono"
            detectedGameId == GameCatalog.ID_SDPSKAIZOKU -> "SuperDpsKaizoku"
            detectedGameId == GameCatalog.ID_SDPSMARIA -> "SuperDpsMaria"
            detectedGameId == GameCatalog.ID_GAKUENKING -> "GakuenKING"
            detectedGameId == GameCatalog.ID_ONLYYOU -> "OnlyYou"
            detectedGameId == GameCatalog.ID_AYUMILIVE -> "AyumiLive"
            detectedGameId == GameCatalog.ID_AYUMIHINT -> "AyumiHint"
            detectedGameId == GameCatalog.ID_AYUMI -> "AyumiChan"
            detectedGameId == GameCatalog.ID_DPSALL -> "DpsAll"
            detectedGameId == GameCatalog.ID_PROGOMAKE -> "ProstudentGOmake"
            detectedGameId == GameCatalog.ID_PROSTUDENTG -> "ProstudentG"
            detectedGameId == GameCatalog.ID_NISENAGURI -> "NiseNaguri"
            detectedGameId == GameCatalog.ID_YAKATA3 -> "Yakata3"
            detectedGameId == GameCatalog.ID_AMBIVALENZ -> "AmbivalenZ"
            detectedGameId == GameCatalog.ID_FUNNYBEE -> "FunnyBee"
            detectedGameId == GameCatalog.ID_TOUSHIN2SP -> "Toushin2Sp"
            detectedGameId == GameCatalog.ID_MUGENHOUYOU -> "MugenHouyou"
            detectedGameId == GameCatalog.ID_OTOMESENKI -> "OtomeSenki"
            detectedGameId == GameCatalog.ID_ERUMEIWAKARE -> "ErumeiWakare"
            detectedGameId == GameCatalog.ID_ERUMEIFLOWER -> "ErumeiFlower"
            GameCatalog.isLegacySystem3GameFiles(primaryFileNames) -> {
                val ini = primaryFileNames.firstOrNull { it.equals("system3.ini", true) }
                OnlineInstaller.sanitizeInstallDirName(
                    GameCatalog.legacySystem3Title(ini?.let { readSmallFile?.invoke(it) }).orEmpty().take(64),
                    fallback = "System3"
                )
            }
            else -> resolveImportBaseNameFromFileNames(primaryFileNames, isMultiDiscSys3, readSmallFile, getFileSize)
        }
    }

    @JvmOverloads
    fun resolveImportBaseNameFromFileNames(
        fileNames: Collection<String>,
        isMultiDiscSys3: Boolean = false,
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): String {
        val activeFiles = fileNames.filter { !skip(it) }
        if (activeFiles.any { it.equals("DOKI_SA.ALD", true) }) {
            return "Escalayer"
        }
        if (activeFiles.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("KichikuRanceSA.ALD", true) ||
                    it.equals("KichikuRanceGA.ald", true) ||
                    repaired.equals("鬼畜王SA.ALD", true) ||
                    it.equals("鬼畜王SA.ALD", true)
            }
        ) {
            return "KichikuRance"
        }
        if (GameCatalog.isDaiakujiGameFiles(activeFiles)) {
            return "Daiakuji"
        }
        if (GameCatalog.isEvenicle2RanceGameFiles(activeFiles)) {
            return "Evenicle2Rance"
        }
        if (GameCatalog.isEvenicleRanceGameFiles(activeFiles)) {
            return "EvenicleRance"
        }
        if (GameCatalog.isEvenicleGaidenGameFiles(activeFiles)) {
            return "EvenicleGaiden"
        }
        if (activeFiles.any { it.equals("Evenicle2.ain", true) }) {
            return "Evenicle2"
        }
        if (activeFiles.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("EscalayerReboot.ain", true) ||
                    repaired.equals("EscalayerReboot.ain", true) ||
                    it.equals("エスカレイヤー・リブート.ain", true) ||
                    it.equals("エスカレイヤーリブート.ain", true)
            }
        ) {
            return "EscalayerReboot"
        }
        if (GameCatalog.isIxsealGameFiles(activeFiles)) {
            return "Ixseal"
        }
        if (GameCatalog.isHarukaVsEscaGameFiles(activeFiles, readSmallFile)) {
            return "HarukaVsEsca"
        }
        if (GameCatalog.isHarukaNinjaGameFiles(activeFiles, readSmallFile)) {
            return "HarukaNinja"
        }
        if (GameCatalog.isHarukaGameFiles(activeFiles)) {
            return "Haruka"
        }
        if (GameCatalog.isTsumamigui3JunGameFiles(activeFiles)) {
            return "Tsumamigui3Jun"
        }
        if (GameCatalog.isTsumamigui3NtrGameFiles(activeFiles)) {
            return "Tsumamigui3Ntr"
        }
        if (GameCatalog.isTsumamigui3GameFiles(activeFiles)) {
            return "Tsumamigui3"
        }
        if (GameCatalog.isTsumashiboriGameFiles(activeFiles)) {
            return "Tsumashibori"
        }
        if (GameCatalog.isDaibanchouGaidenGameFiles(activeFiles, readSmallFile)) {
            return "DaibanchouGaiden"
        }
        if (GameCatalog.isDaibanchouGameFiles(activeFiles)) {
            return "Daibanchou"
        }
        if (GameCatalog.isDaiteikokuGameFiles(activeFiles)) {
            return "Daiteikoku"
        }
        if (GameCatalog.isHaruurareGameFiles(activeFiles)) {
            return "Haruurare"
        }
        if (GameCatalog.isOkusamaJunGameFiles(activeFiles)) {
            return "OkusamaJun"
        }
        if (GameCatalog.isOkusamaNtrGameFiles(activeFiles)) {
            return "OkusamaNtr"
        }
        if (GameCatalog.isWaidonyoGameFiles(activeFiles)) {
            return "Waidonyo"
        }
        if (GameCatalog.isRance6AfterGameFiles(activeFiles)) {
            return "Rance6After"
        }
        if (GameCatalog.isToushin3GameFiles(activeFiles)) {
            return "Toushin3"
        }
        if (GameCatalog.isOyakorankanGameFiles(activeFiles)) {
            return "Oyakorankan"
        }
        if (GameCatalog.isYorugakuruGameFiles(activeFiles)) {
            return "Yorugakuru"
        }
        if (GameCatalog.isRance1DigestGameFiles(activeFiles)) {
            return "Rance1Digest"
        }
        if (GameCatalog.isMamanGameFiles(activeFiles)) {
            return "Maman"
        }
        if (GameCatalog.isHaharanmanGameFiles(activeFiles)) {
            return "Haharanman"
        }
        if (GameCatalog.isTsumamigui2GameFiles(activeFiles)) {
            return "Tsumamigui2"
        }
        if (GameCatalog.isTsumamiguiGameFiles(activeFiles)) {
            return "Tsumamigui"
        }
        if (GameCatalog.isDiaboliqueGameFiles(activeFiles, readSmallFile)) {
            return "DiaboLiQuE"
        }
        if (GameCatalog.isGakuenKingExGameFiles(activeFiles, readSmallFile)) {
            return "GakuenKINGEx"
        }
        if (GameCatalog.isGakuenKingGameFiles(activeFiles, readSmallFile)) {
            return "GakuenKING"
        }
        if (GameCatalog.isOudouYuushaGameFiles(activeFiles, readSmallFile)) {
            return "OudouYuusha"
        }
        if (GameCatalog.isToushin2GameFiles(activeFiles, readSmallFile)) {
            return "Toushin2"
        }
        if (GameCatalog.isToushin1GameFiles(activeFiles, readSmallFile)) {
            return "Toushin1"
        }
        if (GameCatalog.isDarcrowsGameFiles(activeFiles, readSmallFile)) {
            return "DARCROWS"
        }
        if (GameCatalog.isIkusaMikoGameFiles(activeFiles, readSmallFile)) {
            return "IkusaMiko"
        }
        if (GameCatalog.isOnlyYouReCrossGameFiles(activeFiles, readSmallFile)) {
            return "OnlyYouReCross"
        }
        if (GameCatalog.isOnlyYouGameFiles(activeFiles, readSmallFile)) {
            return "OnlyYou"
        }
        if (GameCatalog.isMamatotoGameFiles(activeFiles, readSmallFile)) {
            return "Mamatoto"
        }
        if (GameCatalog.isMamorigamiGameFiles(activeFiles, readSmallFile)) {
            return "Mamorigami"
        }
        if (GameCatalog.isAmbivalenzGameFiles(activeFiles, readSmallFile)) {
            return "AmbivalenZ"
        }
        if (GameCatalog.isFunnyBeeGameFiles(activeFiles, readSmallFile)) {
            return "FunnyBee"
        }
        if (GameCatalog.isToushin2SpGameFiles(activeFiles, readSmallFile)) {
            return "Toushin2Sp"
        }
        if (GameCatalog.isMugenHouyouGameFiles(activeFiles, readSmallFile)) {
            return "MugenHouyou"
        }
        if (GameCatalog.isOtomeSenkiGameFiles(activeFiles, readSmallFile)) {
            return "OtomeSenki"
        }
        if (GameCatalog.isGakuenHyouryuu2GameFiles(activeFiles, readSmallFile)) {
            return "GakuenHyouryuu2"
        }
        if (GameCatalog.isGakuenHyouryuuGameFiles(activeFiles, readSmallFile)) {
            return "GakuenHyouryuu"
        }
        if (GameCatalog.isMaidNoSusumeGameFiles(activeFiles, readSmallFile)) {
            return "MaidNoSusume"
        }
        if (GameCatalog.isDalkGaidenGameFiles(activeFiles, readSmallFile)) {
            return "DalkGaiden"
        }
        if (GameCatalog.isMamanyonyoGameFiles(activeFiles, readSmallFile)) {
            return "Mamanyonyo"
        }
        if (GameCatalog.isMajoNoShokuzaiGameFiles(activeFiles, readSmallFile)) {
            return "MajoNoShokuzai"
        }
        if (GameCatalog.isSabotenGameFiles(activeFiles, readSmallFile)) {
            return "Saboten"
        }
        if (GameCatalog.isDoubleSenseiGameFiles(activeFiles, readSmallFile)) {
            return "DoubleSensei"
        }
        if (GameCatalog.isOjousamaGameFiles(activeFiles, readSmallFile)) {
            return "Ojousama"
        }
        if (GameCatalog.isEdoranzeGameFiles(activeFiles, readSmallFile)) {
            return "Edoranze"
        }
        if (GameCatalog.isVanishPalmGameFiles(activeFiles, readSmallFile)) {
            return "VanishPalm"
        }
        if (GameCatalog.isVanishGameFiles(activeFiles, readSmallFile)) {
            return "Vanish"
        }
        if (GameCatalog.isHokenshitsuGameFiles(activeFiles, readSmallFile)) {
            return "Hokenshitsu"
        }
        if (GameCatalog.isKamunagaraGameFiles(activeFiles, readSmallFile)) {
            return "Kamunagara"
        }
        if (GameCatalog.isErumeiWakareGameFiles(activeFiles, readSmallFile, getFileSize)) {
            return "ErumeiWakare"
        }
        if (GameCatalog.isErumeiFlowerGameFiles(activeFiles, readSmallFile, getFileSize)) {
            return "ErumeiFlower"
        }
        if (GameCatalog.isManana1GameFiles(activeFiles, readSmallFile)) {
            return "Manana1"
        }
        if (GameCatalog.isManana2GameFiles(activeFiles, readSmallFile)) {
            return "Manana2"
        }
        if (GameCatalog.isManana3GameFiles(activeFiles, readSmallFile)) {
            return "Manana3"
        }
        if (GameCatalog.isManana4GameFiles(activeFiles, readSmallFile)) {
            return "Manana4"
        }
        if (GameCatalog.isManana5GameFiles(activeFiles, readSmallFile)) {
            return "Manana5"
        }
        if (GameCatalog.isTsumaSakuraGameFiles(activeFiles, readSmallFile)) {
            return "TsumaSakura"
        }
        if (GameCatalog.isShamansSanctuaryGameFiles(activeFiles, readSmallFile)) {
            return "ShamansSanctuary"
        }
        if (GameCatalog.isAliveZGameFiles(activeFiles, readSmallFile)) {
            return "AliveZ"
        }
        if (GameCatalog.isDungeonsDollsGameFiles(activeFiles, readSmallFile)) {
            return "DungeonsDolls"
        }
        if (GameCatalog.isSuzumiyaGameFiles(activeFiles, readSmallFile)) {
            return "Suzumiya"
        }
        if (GameCatalog.isShimaimaGameFiles(activeFiles, readSmallFile)) {
            return "Shimaima"
        }
        if (GameCatalog.isCosplayLemonGameFiles(activeFiles, readSmallFile)) {
            return "CosplayLemon"
        }
        if (GameCatalog.isWhipHoneyGameFiles(activeFiles, readSmallFile)) {
            return "WhipHoney"
        }
        if (GameCatalog.isOnimusuGameFiles(activeFiles, readSmallFile)) {
            return "Onimusu"
        }
        if (GameCatalog.isProGrxGameFiles(activeFiles, readSmallFile)) {
            return "ProGrx"
        }
        if (GameCatalog.isDpsVerNGameFiles(activeFiles, readSmallFile)) {
            return "DpsVerN"
        }
        if (GameCatalog.isSukiironoGameFiles(activeFiles, readSmallFile)) {
            return "Sukiirono"
        }
        if (GameCatalog.isGirlsGameFiles(activeFiles, readSmallFile)) {
            return "Girls"
        }
        if (GameCatalog.isDebutGameFiles(activeFiles, readSmallFile)) {
            return "Debut"
        }
        if (GameCatalog.isMeganeGameFiles(activeFiles, readSmallFile)) {
            return "Megane"
        }
        if (GameCatalog.isRachelSakuraGameFiles(activeFiles, readSmallFile)) {
            return "RachelSakura"
        }
        if (GameCatalog.isSabakuNikaGameFiles(activeFiles, readSmallFile)) {
            return "SabakuNika"
        }
        if (GameCatalog.isBladeBridersGameFiles(activeFiles, readSmallFile)) {
            return "BladeBriders"
        }
        if (GameCatalog.isDorapekoGameFiles(activeFiles, readSmallFile)) {
            return "Dorapeko"
        }
        if (GameCatalog.isHaniwaPetitGameFiles(activeFiles, readSmallFile)) {
            return "HaniwaPetit"
        }
        if (GameCatalog.isYakata456GameFiles(activeFiles, readSmallFile)) {
            return "Yakata456"
        }
        if (GameCatalog.isUmiNiOchiteGameFiles(activeFiles, readSmallFile)) {
            return "UmiNiOchite"
        }
        if (GameCatalog.isYouseiGameFiles(activeFiles, readSmallFile)) {
            return "Yousei"
        }
        if (GameCatalog.isTobiraGameFiles(activeFiles, readSmallFile)) {
            return "Tobira"
        }
        if (GameCatalog.isBuroburoGameFiles(activeFiles, readSmallFile)) {
            return "Buroburo"
        }
        if (GameCatalog.isKudakeroGameFiles(activeFiles, readSmallFile)) {
            return "Kudakero"
        }
        if (GameCatalog.isCarolGameFiles(activeFiles, readSmallFile)) {
            return "Carol"
        }
        if (GameCatalog.isOthellotGameFiles(activeFiles, readSmallFile)) {
            return "Othellot"
        }
        if (GameCatalog.isMasqueradeGameFiles(activeFiles, readSmallFile)) {
            return "Masquerade"
        }
        if (GameCatalog.isDekirukanaGameFiles(activeFiles, readSmallFile)) {
            return "Dekirukana"
        }
        if (GameCatalog.isTatakikoroshiGameFiles(activeFiles, readSmallFile)) {
            return "Tatakikoroshi"
        }
        if (GameCatalog.isBalloonPopGameFiles(activeFiles, readSmallFile)) {
            return "BalloonPop"
        }
        if (GameCatalog.isHiyoko2GameFiles(activeFiles, readSmallFile)) {
            return "Hiyoko2"
        }
        if (GameCatalog.isSelfGameFiles(activeFiles, readSmallFile)) {
            return "SelfGame"
        }
        if (GameCatalog.isSanbikiGameFiles(activeFiles, readSmallFile)) {
            return "Sanbiki"
        }
        if (GameCatalog.isPersiomGameFiles(activeFiles, readSmallFile)) {
            return "PERSIOM"
        }
        if (GameCatalog.isSeeInAoGameFiles(activeFiles, readSmallFile)) {
            return "SeeInAO"
        }
        if (GameCatalog.isHushabyGameFiles(activeFiles, readSmallFile)) {
            return "HushabyBaby"
        }
        if (GameCatalog.isAyumiGameFiles(activeFiles, readSmallFile)) {
            return "AyumiChan"
        }
        if (GameCatalog.isCielClaireGameFiles(activeFiles, readSmallFile)) {
            return "CielClaire"
        }
        if (GameCatalog.isNightDemonGameFiles(activeFiles, readSmallFile)) {
            return "NightDemon"
        }
        if (GameCatalog.isKatsumiGameFiles(activeFiles, readSmallFile)) {
            return "Katsumi"
        }
        if (GameCatalog.isDrStopGameFiles(activeFiles, readSmallFile)) {
            return "DrStop"
        }
        if (GameCatalog.isKoreDpsGameFiles(activeFiles, readSmallFile)) {
            return "KoreDps"
        }
        if (GameCatalog.isDpsAllGameFiles(activeFiles, readSmallFile)) {
            return "DpsAll"
        }
        if (GameCatalog.isKaeruAliceGameFiles(activeFiles, readSmallFile)) {
            return "KaeruAlice"
        }
        if (GameCatalog.isProstudentGoodGameFiles(activeFiles, readSmallFile)) {
            return "ProstudentGood"
        }
        if (GameCatalog.isProstudentGGameFiles(activeFiles, readSmallFile)) {
            return "ProstudentG"
        }
        if (GameCatalog.isPascha3GameFiles(activeFiles, readSmallFile)) {
            return "Pascha3"
        }
        if (GameCatalog.isPaschaCppGameFiles(activeFiles, readSmallFile)) {
            return "PaschaCpp"
        }
        if (GameCatalog.isPastelChime2GameFiles(activeFiles, readSmallFile)) {
            return "PastelChime2"
        }
        if (GameCatalog.isPastelChimeGameFiles(activeFiles, readSmallFile)) {
            return "PastelChime"
        }
        if (GameCatalog.isYakata3GameFiles(activeFiles, readSmallFile)) {
            return "Yakata3"
        }
        if (GameCatalog.isDalkGameFiles(activeFiles, readSmallFile)) {
            return "DALK"
        }
        if (GameCatalog.isNingenGariGameFiles(activeFiles, readSmallFile)) {
            return "NingenGari"
        }
        if (GameCatalog.isKaerunyoGameFiles(activeFiles, readSmallFile)) {
            return "Kaerunyo"
        }
        if (GameCatalog.isAtlachnachaGameFiles(activeFiles)) {
            return "AtlachNacha"
        }
        if (GameCatalog.isReishikiGameFiles(activeFiles)) {
            return "Reishiki"
        }
        if (GameCatalog.isRance5dGameFiles(activeFiles)) {
            return "Rance5D"
        }
        if (GameCatalog.isRance06GameFiles(activeFiles)) {
            return "Rance06"
        }
        if (GameCatalog.isGalzooGameFiles(activeFiles)) {
            return "GALZOO"
        }
        val sys4Ain = activeFiles.firstOrNull { GameCatalog.isSystem4AinFile(it) }
            ?: activeFiles.firstOrNull { GameCatalog.isSystem4CodeFile(it, activeFiles, readSmallFile) }
        if (sys4Ain != null) {
            val raw = ArchiveExtractor.repairSjisAsGbkMojibake(sys4Ain.substringBeforeLast('.'))
            return OnlineInstaller.sanitizeInstallDirName(raw, fallback = "Game")
        }
        val sa = activeFiles.firstOrNull {
            it.endsWith("sa.ald", true) || it.endsWith("sa_chs.ald", true) || it.equals("adisk.ald", true)
        }
        if (sa != null) {
            val raw = ArchiveExtractor.repairSjisAsGbkMojibake(sa.substringBeforeLast('.'))
            return OnlineInstaller.sanitizeInstallDirName(raw, fallback = "System3")
        }
        return if (isMultiDiscSys3) "System3" else "Game"
    }

    fun boundedPrimaryReadLimitBytes(fileName: String, docSize: Long): Int? {
        return when {
            fileName.equals("ADISK.DAT", ignoreCase = true) ||
                fileName.equals("BDISK.DAT", ignoreCase = true) -> 256
            docSize < 0L || docSize <= 256L * 1024L -> 256 * 1024
            else -> null
        }
    }

    private fun readBoundedDocumentBytes(context: Context, uri: Uri, maxBytes: Int = 256 * 1024): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < maxBytes) {
                    val toRead = minOf(buf.size, maxBytes - total)
                    val n = input.read(buf, 0, toRead)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun copyTree(
        context: Context,
        tree: Uri,
        storage: File,
        expectedAinName: String? = null,
        targetGameId: String? = null,
        progress: suspend (String) -> Unit
    ): File {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rootFiles = children(context, tree, root)
        val discovered = discoverImportSources(
            rootId = root,
            rootEntries = rootFiles.map { TreeEntry(it.uri, it.name, it.folder, it) },
            listChildren = { uri -> children(context, tree, uri).map { TreeEntry(it.uri, it.name, it.folder, it) } },
            readSmallEntry = { entry ->
                val doc = entry.payload
                if (doc.folder) {
                    null
                } else {
                    val limit = boundedPrimaryReadLimitBytes(
                        ArchiveExtractor.repairSjisAsGbkMojibake(doc.name),
                        doc.size
                    )
                    if (limit != null) readBoundedDocumentBytes(context, doc.uri, limit) else null
                }
            }
        )
        val primaryDocs = discovered.primaryEntries?.map { it.payload }
        val sourceTrees = discovered.sourceTrees.map { (uri, entries, sub) ->
            Triple(uri, entries.map { it.payload }, sub)
        }
        val ixsealPatchTrees = discovered.ixsealPatchTrees.map { entries ->
            entries.map { it.payload }
        }

        val resolvedPrimary = primaryDocs ?: throw GameList.InstallFailureException(R.string.directory_no_game)
        if (!expectedAinName.isNullOrBlank() &&
            resolvedPrimary.none { !it.folder && !skip(it.name) && it.name.equals(expectedAinName, ignoreCase = true) }
        ) {
            val primaryTreeEntries = resolvedPrimary.map { TreeEntry(it.uri, it.name, it.folder, it) }
            val hasNestedExpected = primaryTreeEntries.any { entry ->
                if (entry.folder || skip(entry.name) || !System4BootEntry.isCandidateIniFile(entry.name)) {
                    return@any false
                }
                val doc = entry.payload
                val limit = boundedPrimaryReadLimitBytes(doc.name, doc.size) ?: return@any false
                val bytes = readBoundedDocumentBytes(context, doc.uri, limit) ?: return@any false
                System4BootEntry.extractCodeNames(bytes).any { codeName ->
                    val leaf = codeName.replace('\\', '/').substringAfterLast('/')
                    leaf.equals(expectedAinName, ignoreCase = true) &&
                        hasConfiguredTreeScript(codeName, primaryTreeEntries) { dirUri ->
                            children(context, tree, dirUri).map { TreeEntry(it.uri, it.name, it.folder, it) }
                        }
                }
            }
            if (!hasNestedExpected) {
                throw GameList.InstallFailureException(R.string.import_wrong_game)
            }
        }
        val primaryFileNames = resolvedPrimary
            .filter { !it.folder && !skip(it.name) }
            .map { ArchiveExtractor.repairSjisAsGbkMojibake(it.name) }
        val bgmFileNames = if (targetGameId == GameCatalog.ID_KICHIKUOU ||
            targetGameId == GameCatalog.ID_DAIAKUJI ||
            (targetGameId.isNullOrBlank() && GameCatalog.isSystem3xGameFiles(primaryFileNames))
        ) {
            val bgmDoc = resolvedPrimary.firstOrNull { it.folder && it.name.equals("bgm", ignoreCase = true) }
            if (bgmDoc != null) {
                children(context, tree, bgmDoc.uri).filter { !it.folder }.map { it.name }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
        val readPrimarySmallFile: (String) -> ByteArray? = { fileName ->
            val doc = resolvedPrimary.firstOrNull {
                !it.folder && !skip(it.name) &&
                    ArchiveExtractor.repairSjisAsGbkMojibake(it.name).equals(fileName, ignoreCase = true)
            }
            val limit = if (doc != null) boundedPrimaryReadLimitBytes(fileName, doc.size) else null
            if (doc != null && limit != null) readBoundedDocumentBytes(context, doc.uri, limit) else null
        }
        val primaryFileSize: (String) -> Long? = { fileName ->
            resolvedPrimary.firstOrNull {
                !it.folder && !skip(it.name) &&
                    ArchiveExtractor.repairSjisAsGbkMojibake(it.name).equals(fileName, ignoreCase = true)
            }?.size?.takeIf { it > 0L }
        }
        val detectedGameId = GameCatalog.classifyGameRootFiles(
            fileNames = primaryFileNames,
            bgmFileNames = bgmFileNames,
            readSmallFile = readPrimarySmallFile,
            getFileSize = primaryFileSize
        )
        val matchesTarget = if (targetGameId.isNullOrBlank() || targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM)) {
            true
        } else {
            detectedGameId == targetGameId
        }
        if (!matchesTarget) {
            throw GameList.InstallFailureException(R.string.import_wrong_game)
        }
        val isEscalayerReboot = targetGameId == GameCatalog.ID_ESCALAYER_REBOOT ||
            detectedGameId == GameCatalog.ID_ESCALAYER_REBOOT
        val isRance06Import = targetGameId == GameCatalog.ID_RANCE06 ||
            detectedGameId == GameCatalog.ID_RANCE06
        val isDaiakujiImport = targetGameId == GameCatalog.ID_DAIAKUJI ||
            detectedGameId == GameCatalog.ID_DAIAKUJI
        val isGalzooImport = targetGameId == GameCatalog.ID_GALZOO ||
            detectedGameId == GameCatalog.ID_GALZOO
        val isIxsealImport = targetGameId == GameCatalog.ID_IXSEAL ||
            detectedGameId == GameCatalog.ID_IXSEAL
        val isHarukaImport = targetGameId == GameCatalog.ID_HARUKA ||
            detectedGameId == GameCatalog.ID_HARUKA
        val isTsumashiboriImport = targetGameId == GameCatalog.ID_TSUMASHIBORI ||
            detectedGameId == GameCatalog.ID_TSUMASHIBORI
        val isTsumamigui3Import = targetGameId == GameCatalog.ID_TSUMAMIGUI3 ||
            detectedGameId == GameCatalog.ID_TSUMAMIGUI3
        val hasPrimaryDDemo = primaryFileNames.any { it.equals("dDemo.alk", ignoreCase = true) }
        val baseName = resolveCopyTreeBaseName(
            primaryFileNames = primaryFileNames,
            detectedGameId = detectedGameId,
            targetGameId = targetGameId,
            isMultiDiscSys3 = sourceTrees.size > 1,
            readSmallFile = readPrimarySmallFile,
            getFileSize = primaryFileSize
        )
        val storageCanonical = storage.canonicalFile
        val staging = File(storageCanonical, ".import-${UUID.randomUUID()}").canonicalFile
        if (staging.parentFile != storageCanonical || !staging.mkdir())
            throw IOException("Cannot create import directory")
        var copied = 0L
        var lastUpdate = 0L
        try {
            suspend fun copy(documents: List<Document>, destination: File, relPrefix: String = "") {
                for (document in documents) {
                    currentCoroutineContext().ensureActive()
                    val name = ArchiveExtractor.repairSjisAsGbkMojibake(document.name)
                    if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\\' in name)
                        throw IOException("Invalid document name")
                    val relPath = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                    if (skip(document.name) ||
                        (isEscalayerReboot && isEscalayerRebootBloatRelPath(relPath)) ||
                        (isRance06Import && isRance06BloatRelPath(relPath, skipPatchSubdir = true)) ||
                        (isDaiakujiImport && isDaiakujiBloatRelPath(relPath, skipDDemo0Backup = hasPrimaryDDemo)) ||
                        (isGalzooImport && isGalzooBloatRelPath(relPath)) ||
                        (isIxsealImport && isIxsealBloatRelPath(relPath, skipPatchCnSubdir = true)) ||
                        (isHarukaImport && isHarukaBloatRelPath(relPath)) ||
                        (isTsumashiboriImport && isTsumashiboriBloatRelPath(relPath)) ||
                        (isTsumamigui3Import && isTsumamigui3BloatRelPath(relPath))
                    ) {
                        continue
                    }
                    val target = File(destination, name)
                    if (document.folder) {
                        if (!target.exists() && !target.mkdir()) throw IOException("Cannot create directory: $name")
                        copy(children(context, tree, document.uri), target, relPath)
                        continue
                    }
                    if (target.exists()) continue
                    if (document.size > StatFs(storage.path).availableBytes - 32L * 1024 * 1024)
                        throw GameList.InstallFailureException(R.string.import_no_space)
                    var fileBytes = 0L
                    val input = context.contentResolver.openInputStream(document.uri)
                        ?: throw IOException("Cannot open $name")
                    input.use { source ->
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                fileBytes += count
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 200) {
                                    progress(context.getString(R.string.import_copy_progress, copied / (1024 * 1024), name))
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                    if (document.size >= 0 && document.size != fileBytes)
                        throw IOException("Incomplete document: $name")
                }
            }
            if (isIxsealImport && ixsealPatchTrees.isNotEmpty()) {
                for (patchDocs in ixsealPatchTrees) {
                    copy(patchDocs, staging)
                }
            }
            for ((_, docs, targetSubdir) in sourceTrees) {
                if (targetSubdir.isEmpty()) {
                    copy(docs, staging)
                } else if (isGalzooImport) {
                    val subDest = File(staging, targetSubdir)
                    if (!subDest.exists() && !subDest.mkdirs()) throw IOException("Cannot create directory: $targetSubdir")
                    copy(docs, subDest, targetSubdir)
                }
            }
            currentCoroutineContext().ensureActive()
            if (isRance06Import) normalizeRance06DungeonData(staging)
            if (!GameCatalog.validateImportedGameDir(staging, expectedAinName, targetGameId)) {
                throw GameList.InstallFailureException(R.string.import_wrong_game)
            }
            var destination = File(storageCanonical, baseName).canonicalFile
            var index = 1
            while (destination.exists()) {
                destination = File(storageCanonical, "$baseName-${index++}").canonicalFile
            }
            if (destination.parentFile != storageCanonical) {
                throw IOException("Invalid destination directory")
            }
            if (!OnlineInstaller.moveFileOrDir(staging, destination)) {
                throw IOException("Cannot finish game import")
            }
            return destination
        } finally {
            // Staging is a newly created direct child of this app's storage.
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    suspend fun copyModTree(context: Context, tree: Uri, storage: File, progress: suspend (String) -> Unit): File {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = children(context, tree, root)
        val staging = File(storage, ".mod-import-${UUID.randomUUID()}").canonicalFile
        if (staging.parentFile != storage.canonicalFile || !staging.mkdir())
            throw IOException("Cannot create MOD staging directory")
        var copied = 0L
        var lastUpdate = 0L
        try {
            suspend fun copy(documents: List<Document>, destination: File) {
                for (document in documents) {
                    currentCoroutineContext().ensureActive()
                    val name = ArchiveExtractor.repairSjisAsGbkMojibake(document.name)
                    if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\\' in name)
                        throw IOException("Invalid document name")
                    if (skip(document.name)) continue
                    val target = File(destination, name)
                    if (document.folder) {
                        if (!target.mkdir()) throw IOException("Cannot create directory: $name")
                        copy(children(context, tree, document.uri), target)
                        continue
                    }
                    if (document.size > StatFs(storage.path).availableBytes - 32L * 1024 * 1024)
                        throw GameList.InstallFailureException(R.string.import_no_space)
                    var fileBytes = 0L
                    val input = context.contentResolver.openInputStream(document.uri)
                        ?: throw IOException("Cannot open $name")
                    input.use { source ->
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                fileBytes += count
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 200) {
                                    progress(context.getString(R.string.import_copy_progress, copied / (1024 * 1024), name))
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                    if (document.size >= 0 && document.size != fileBytes)
                        throw IOException("Incomplete document: $name")
                }
            }
            copy(files, staging)
            currentCoroutineContext().ensureActive()
            return staging
        } catch (t: Throwable) {
            if (staging.exists()) staging.deleteRecursively()
            throw t
        }
    }
}
