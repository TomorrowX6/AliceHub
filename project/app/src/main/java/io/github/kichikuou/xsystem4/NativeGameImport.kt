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

    private fun <T, D> isSys4RootEntries(docs: List<TreeEntry<T, D>>): Boolean {
        val hasIni = docs.any {
            !it.folder && !skip(it.name) &&
                (it.name.equals("AliceStart.ini", true) || it.name.equals("System40.ini", true))
        }
        val hasAin = docs.any {
            !it.folder && !skip(it.name) &&
                it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true)
        }
        return hasIni && hasAin
    }

    private fun <T, D> isIxsealPatchRootEntries(docs: List<TreeEntry<T, D>>): Boolean {
        if (isSys4RootEntries(docs)) return true
        return docs.any {
            !it.folder && !skip(it.name) &&
                (it.name.equals("Ixseal.ain", true) || it.name.equals("IxsealCG.afa", true))
        }
    }

    private fun <T, D> isSys3RootEntries(docs: List<TreeEntry<T, D>>): Boolean {
        if (isSys4RootEntries(docs)) return false
        val names = docs.filter { !it.folder && !skip(it.name) }.map { it.name }
        return names.any(GameCatalog::isSystem35Scenario) || GameCatalog.isLegacySystem3GameFiles(names)
    }

    fun <T, D> discoverImportSources(
        rootId: T,
        rootEntries: List<TreeEntry<T, D>>,
        listChildren: (T) -> List<TreeEntry<T, D>>
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
                if (isIxsealPatchRootEntries(docs)) {
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

        if (isSys4RootEntries(rootEntries) || isSys3RootEntries(rootEntries)) {
            primaryEntries = rootEntries
            sourceTrees = listOf(Triple(rootId, rootEntries, ""))
            if (isSys4RootEntries(rootEntries)) {
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
                if (isSys4RootEntries(docs) || isSys3RootEntries(docs)) {
                    if (primaryEntries == null && GameCatalog.isLegacySystem3GameFiles(docs.filter { !it.folder }.map { it.name })) {
                        return DiscoveredImportSources(docs, listOf(Triple(dirId, docs, "")), emptyList())
                    }
                    if (primaryEntries == null) primaryEntries = docs
                    discRoots.add(Triple(dirId, docs, ""))
                    if (isSys4RootEntries(docs)) {
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
    fun resolveImportBaseNameFromFileNames(
        fileNames: Collection<String>,
        isMultiDiscSys3: Boolean = false
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
        if (GameCatalog.isHarukaGameFiles(activeFiles)) {
            return "Haruka"
        }
        if (GameCatalog.isTsumamigui3GameFiles(activeFiles)) {
            return "Tsumamigui3"
        }
        if (GameCatalog.isTsumashiboriGameFiles(activeFiles)) {
            return "Tsumashibori"
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
        val sys4Ain = activeFiles.firstOrNull {
            it.endsWith(".ain", true) && !it.equals("System39.ain", true)
        }
        if (sys4Ain != null) {
            val raw = ArchiveExtractor.repairSjisAsGbkMojibake(sys4Ain.substringBeforeLast('.'))
            return OnlineInstaller.sanitizeInstallDirName(raw, fallback = "Game")
        }
        val sa = activeFiles.firstOrNull {
            it.endsWith("sa.ald", true) || it.equals("adisk.ald", true)
        }
        if (sa != null) {
            val raw = ArchiveExtractor.repairSjisAsGbkMojibake(sa.substringBeforeLast('.'))
            return OnlineInstaller.sanitizeInstallDirName(raw, fallback = "System3")
        }
        return if (isMultiDiscSys3) "System3" else "Game"
    }

    private fun resolveImportBaseName(docs: List<Document>, isMultiDiscSys3: Boolean): String =
        resolveImportBaseNameFromFileNames(
            docs.filter { !it.folder }.map { it.name },
            isMultiDiscSys3
        )

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
            listChildren = { uri -> children(context, tree, uri).map { TreeEntry(it.uri, it.name, it.folder, it) } }
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
            throw GameList.InstallFailureException(R.string.import_wrong_game)
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
            if (doc != null && (doc.size < 0 || doc.size <= 256L * 1024L)) {
                readBoundedDocumentBytes(context, doc.uri)
            } else {
                null
            }
        }
        val detectedGameId = GameCatalog.classifyGameRootFiles(
            fileNames = primaryFileNames,
            bgmFileNames = bgmFileNames,
            readSmallFile = readPrimarySmallFile
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
        val baseName = when {
            isDaiakujiImport -> "Daiakuji"
            isHarukaImport -> "Haruka"
            GameCatalog.isLegacySystem3GameFiles(primaryFileNames) -> {
                val ini = primaryFileNames.firstOrNull { it.equals("system3.ini", true) }
                val iniBytes = ini?.let(readPrimarySmallFile)
                when {
                    GameCatalog.isRance41GameFiles(primaryFileNames) { if (it.equals("system3.ini", true)) iniBytes else null } -> "Rance41"
                    GameCatalog.isRance42GameFiles(primaryFileNames) { if (it.equals("system3.ini", true)) iniBytes else null } -> "Rance42"
                    else -> OnlineInstaller.sanitizeInstallDirName(
                        GameCatalog.legacySystem3Title(iniBytes).orEmpty().take(64),
                        fallback = "System3"
                    )
                }
            }
            else -> resolveImportBaseName(resolvedPrimary, sourceTrees.size > 1)
        }
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
