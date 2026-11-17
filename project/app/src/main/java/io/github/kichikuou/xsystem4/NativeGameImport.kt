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

    fun skip(path: String): Boolean {
        val parts = path.replace('\\', '/').split('/')
        if (parts.any {
                it.startsWith('.') ||
                    it.equals("android-port", true) ||
                    it.equals("SaveData", true) ||
                    it.equals("DirectX", true)
            })
            return true
        val fileName = parts.last()
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

    private fun isSys4Root(docs: List<Document>): Boolean {
        val hasIni = docs.any {
            !it.folder && (it.name.equals("AliceStart.ini", true) || it.name.equals("System40.ini", true))
        }
        val hasAin = docs.any {
            !it.folder && it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true)
        }
        return hasIni && hasAin
    }

    private fun isSys3Root(docs: List<Document>): Boolean {
        if (isSys4Root(docs)) return false
        return docs.any { !it.folder && (it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true)) }
    }

    private fun resolveImportBaseName(docs: List<Document>, isMultiDiscSys3: Boolean): String {
        if (docs.any { !it.folder && it.name.equals("DOKI_SA.ALD", true) }) return "Escalayer"
        val sys4Ain = docs.firstOrNull {
            !it.folder && it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true)
        }
        if (sys4Ain != null) {
            return ArchiveExtractor.repairSjisAsGbkMojibake(sys4Ain.name.substringBeforeLast('.')).ifEmpty { "Game" }
        }
        val sa = docs.firstOrNull { !it.folder && (it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true)) }
        if (sa != null) {
            return ArchiveExtractor.repairSjisAsGbkMojibake(sa.name.substringBeforeLast('.')).ifEmpty { "System3" }
        }
        return if (isMultiDiscSys3) "System3" else "Game"
    }

    suspend fun copyTree(
        context: Context,
        tree: Uri,
        storage: File,
        expectedAinName: String? = null,
        progress: suspend (String) -> Unit
    ): File {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rootFiles = children(context, tree, root)
        var sourceTrees: List<Pair<Uri, List<Document>>> = emptyList()
        var primaryDocs: List<Document>? = null

        if (isSys4Root(rootFiles) || isSys3Root(rootFiles)) {
            primaryDocs = rootFiles
            sourceTrees = listOf(root to rootFiles)
        } else {
            // Also support picking a container folder holding Disc1/GAMEDATA .. Disc3/GAMEDATA
            val discRoots = mutableListOf<Pair<Uri, List<Document>>>()
            val queue = ArrayDeque<Pair<Uri, Int>>()
            for (d in rootFiles) {
                if (d.folder && !skip(d.name)) queue.add(d.uri to 1)
            }
            while (queue.isNotEmpty()) {
                val (dirUri, depth) = queue.removeFirst()
                val docs = children(context, tree, dirUri)
                if (isSys4Root(docs) || isSys3Root(docs)) {
                    primaryDocs = docs
                    discRoots.add(dirUri to docs)
                } else if (docs.any {
                        !it.folder && (it.name.endsWith(".ald", true) ||
                            it.name.endsWith(".bgi", true) || it.name.endsWith(".alk", true))
                    }
                ) {
                    discRoots.add(dirUri to docs)
                } else if (depth < 2) {
                    for (sub in docs) {
                        if (sub.folder && !skip(sub.name)) queue.add(sub.uri to (depth + 1))
                    }
                }
            }
            if (primaryDocs != null) {
                sourceTrees = discRoots
            }
        }

        val resolvedPrimary = primaryDocs ?: throw GameList.InstallFailureException(R.string.directory_no_game)
        if (!expectedAinName.isNullOrBlank() &&
            resolvedPrimary.none { !it.folder && it.name.equals(expectedAinName, ignoreCase = true) }
        ) {
            throw GameList.InstallFailureException(R.string.import_wrong_game)
        }
        val baseName = resolveImportBaseName(resolvedPrimary, sourceTrees.size > 1)
        val staging = File(storage, ".import-${UUID.randomUUID()}").canonicalFile
        if (staging.parentFile != storage.canonicalFile || !staging.mkdir())
            throw IOException("Cannot create import directory")
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
                        if (!target.exists() && !target.mkdir()) throw IOException("Cannot create directory: $name")
                        copy(children(context, tree, document.uri), target)
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
            for ((_, docs) in sourceTrees) {
                copy(docs, staging)
            }
            currentCoroutineContext().ensureActive()
            var destination = File(storage, baseName)
            var index = 1
            while (destination.exists()) destination = File(storage, "$baseName-${index++}")
            if (!staging.renameTo(destination)) throw IOException("Cannot finish game import")
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
