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
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID

object AppStorage {
    private const val GAMES = "games"
    private const val SAVES = "saves"
    private const val SAVE_BACKUPS = "save_backups"
    private const val PATH_PREFS = "public_storage_paths"
    private const val MIGRATION_ROOT_KEY = "attempted_root"
    private const val MIGRATION_LAYOUT_KEY = "attempted_layout"
    private const val MIGRATION_INCOMPLETE_KEY = "migration_incomplete"
    private const val MIGRATION_RETAINED_KEY = "retained_saves"
    private const val MIGRATION_ATTEMPTED_STAGING_KEY = "attempted_staging"
    private const val MIGRATION_NOTICE_KEY = "migration_notice_shown"
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

    fun gameScanRoots(context: Context, roots: List<File> = gameRoots(context)): List<File> {
        val shared = publicGamesRoot(context) ?: return roots
        return roots.sortedBy { it == shared }
    }

    fun privateGameNamesForInstall(context: Context, destination: File): Set<String> {
        if (destination.canonicalFile != publicGamesRoot(context)?.canonicalFile) return emptySet()
        return gameRoots(context).filter { it.canonicalFile != destination.canonicalFile }
            .flatMap { it.list().orEmpty().asList() }.mapTo(mutableSetOf()) { gameDirectoryKey(it) }
    }

    fun gameDirectoryKey(name: String): String = name.lowercase(Locale.ROOT)

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
        val emptyLegacy = shared != legacy.parentFile &&
            Files.isDirectory(legacy.toPath(), LinkOption.NOFOLLOW_LINKS) && legacy.list()?.isEmpty() == true
        return if (legacy.exists() && !emptyLegacy) context.filesDir
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

    @JvmOverloads
    fun needsPublicStoragePreparation(context: Context, retry: Boolean = false): Boolean {
        val root = publicRoot(context) ?: return false
        val paths = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
        val layout = publicLayout(root)
        val preparedRoot = paths.getString(MIGRATION_ROOT_KEY, null) ?: paths.getString("completed_root", null)
        if (preparedRoot != root.absolutePath ||
            layout != paths.getString(MIGRATION_LAYOUT_KEY, "directory,directory,directory") ||
            paths.getString(MIGRATION_STAGING_KEY, null) != paths.getString(MIGRATION_ATTEMPTED_STAGING_KEY, null) ||
            (retry && paths.getBoolean(MIGRATION_INCOMPLETE_KEY, false))
        ) return true
        val retained = paths.getStringSet(MIGRATION_RETAINED_KEY, emptySet()).orEmpty()
        for (name in listOf(SAVES, SAVE_BACKUPS)) {
            val entries = migrationEntries(File(context.filesDir, name))
            if (entries == null) {
                if (!paths.getBoolean(MIGRATION_INCOMPLETE_KEY, false)) return true
                continue
            }
            if (entries.any {
                    !isEmptyDirectory(it) && (retained.isEmpty() || retainedKey(it) !in retained)
                }) return true
        }
        return false
    }

    private fun publicLayout(root: File): String = listOf(GAMES, SAVES, SAVE_BACKUPS).joinToString(",") { name ->
        val dir = File(root, name)
        when {
            dir.isDirectory && dir.canWrite() -> "directory"
            dir.isDirectory -> "read-only"
            exists(dir) -> "blocked"
            else -> "missing"
        }
    }

    private fun migrationEntries(root: File): List<File>? =
        if (!exists(root)) emptyList() else root.listFiles()?.filterNot { it.name.startsWith(".migrated-") }

    private fun isEmptyDirectory(file: File): Boolean =
        Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.list()?.isEmpty() == true

    private fun retainedKey(source: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        fun visit(file: File) {
            add(file.name)
            when {
                Files.isSymbolicLink(file.toPath()) -> add("link")
                file.isDirectory -> {
                    add("directory")
                    val children = file.listFiles()
                    if (children == null) add("unreadable") else children.sortedBy { it.name }.forEach(::visit)
                    add("end")
                }
                file.isFile -> add("file:${file.length()}:${file.lastModified()}")
                else -> add(if (exists(file)) "other" else "absent")
            }
        }
        add(source.parentFile!!.name)
        visit(source)
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    @Synchronized
    fun consumePublicStorageFailureNotice(context: Context): Boolean {
        val paths = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
        if (paths.getBoolean(MIGRATION_NOTICE_KEY, false)) return false
        return paths.edit().putBoolean(MIGRATION_NOTICE_KEY, true).commit()
    }

    data class MigrationProgress(val itemName: String, val percent: Int)

    @JvmOverloads
    fun preparePublicStorage(context: Context, onProgress: (MigrationProgress) -> Unit = {}): Boolean =
        preparePublicStorage(context, retry = false, onProgress = onProgress)

    @Synchronized
    fun preparePublicStorage(context: Context, retry: Boolean, onProgress: (MigrationProgress) -> Unit): Boolean {
        val shared = publicRoot(context) ?: return true
        val games = File(shared, GAMES)
        val backup = File(shared, SAVE_BACKUPS)
        val saves = File(shared, SAVES)
        var complete = (games.isDirectory || games.mkdirs()) && games.canWrite()
        val paths = context.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
        val previousRetained = if (!retry && paths.getString(MIGRATION_ROOT_KEY, null) == shared.absolutePath &&
            paths.getString(MIGRATION_LAYOUT_KEY, null) == publicLayout(shared)
        ) paths.getStringSet(MIGRATION_RETAINED_KEY, emptySet()).orEmpty() else emptySet()
        val retained = mutableSetOf<String>()
        fun recordAttempt(): Boolean {
            val saved = paths.edit().putString(MIGRATION_ROOT_KEY, shared.absolutePath)
                .putString(MIGRATION_LAYOUT_KEY, publicLayout(shared))
                .putBoolean(MIGRATION_INCOMPLETE_KEY, !complete)
                .putStringSet(MIGRATION_RETAINED_KEY, retained)
                .putString(MIGRATION_ATTEMPTED_STAGING_KEY, paths.getString(MIGRATION_STAGING_KEY, null))
                .commit()
            return complete && saved
        }
        var stagingReady = true
        paths.getString(MIGRATION_STAGING_KEY, null)?.let { path ->
            val staging = File(path)
            try {
                if (listOf(games, backup, saves).any { it == staging.parentFile } &&
                    staging.name.startsWith(MIGRATION_STAGING_PREFIX)
                ) deleteStaging(staging)
                forgetStaging(paths)
            } catch (e: IOException) {
                Log.w("AppStorage", "Could not remove interrupted migration staging", e)
                stagingReady = false
                complete = false
            }
        }
        val entriesToMove = mutableListOf<Pair<File, File>>()
        fun queueRoot(source: File, target: File) {
            val targetReady = (target.isDirectory || target.mkdirs()) && target.canWrite()
            if (!targetReady) complete = false
            val entries = migrationEntries(source)
            if (entries == null) {
                complete = false
                return
            }
            for (entry in entries) {
                val destination = File(target, entry.name)
                val blocked = !targetReady || !stagingReady
                val key = if (blocked || previousRetained.isNotEmpty()) retainedKey(entry) else null
                if (key != null && (blocked || key in previousRetained)) {
                    retained.add(key)
                    complete = false
                } else {
                    entriesToMove.add(entry to destination)
                }
            }
        }
        queueRoot(File(context.filesDir, SAVES), saves)
        queueRoot(File(context.filesDir, SAVE_BACKUPS), backup)
        entriesToMove.forEachIndexed { index, (entry, destination) ->
            var lastPercent = -1
            fun report(fraction: Double) {
                val percent = ((index + fraction) * 100 / entriesToMove.size).toInt()
                if (percent > lastPercent) {
                    lastPercent = percent
                    onProgress(MigrationProgress(entry.name, percent))
                }
            }
            report(0.0)
            val moved = try {
                if (destination.isDirectory && isEmptyDirectory(entry)) {
                    Files.deleteIfExists(entry.toPath())
                    true
                } else migrateEntry(entry, destination, paths, ::report)
            } catch (e: IOException) {
                Log.w("AppStorage", "Could not migrate $entry", e)
                false
            } catch (e: SecurityException) {
                Log.w("AppStorage", "Could not migrate $entry", e)
                false
            }
            if (!moved) {
                retained.add(retainedKey(entry))
                complete = false
            }
            report(1.0)
        }
        return recordAttempt()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun migrateEntry(source: File, destination: File): Boolean = migrateEntry(source, destination, null) {}

    private fun migrateEntry(
        source: File,
        destination: File,
        paths: SharedPreferences?,
        report: (Double) -> Unit
    ): Boolean {
        if (!exists(source)) return true
        if (Files.isSymbolicLink(source.toPath())) return false
        fun byteProgress(passes: Int): (Int) -> Unit {
            val total = (entrySize(source).toDouble() * passes).coerceAtLeast(1.0)
            var processed = 0L
            return { count ->
                processed += count
                report((processed / total * 0.99).coerceAtMost(0.99))
            }
        }
        if (source.isDirectory && isEmptyDirectory(destination)) {
            Files.deleteIfExists(destination.toPath())
        }
        val alreadyCopied = exists(destination)
        if (alreadyCopied && !sameContents(source, destination, byteProgress(1))) return false
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
                val countBytes = byteProgress(2)
                copyEntry(source, copied, countBytes)
                if (!sameContents(source, copied, countBytes)) return false
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

    private fun entrySize(file: File): Long = when {
        Files.isSymbolicLink(file.toPath()) -> 0L
        file.isDirectory -> file.listFiles()?.sumOf(::entrySize) ?: 0L
        else -> file.length()
    }

    private fun copyEntry(source: File, destination: File, onBytes: (Int) -> Unit) {
        when {
            Files.isSymbolicLink(source.toPath()) -> throw IOException("Unexpected symbolic link: $source")
            source.isDirectory -> {
                Files.createDirectory(destination.toPath())
                for (child in source.listFiles() ?: throw IOException("Cannot list $source")) {
                    copyEntry(child, File(destination, child.name), onBytes)
                }
                destination.setLastModified(source.lastModified())
            }
            source.isFile -> {
                source.inputStream().use { input ->
                    Files.newOutputStream(destination.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            onBytes(count)
                        }
                    }
                }
                destination.setLastModified(source.lastModified())
            }
            else -> throw IOException("Cannot copy $source")
        }
    }

    private fun sameContents(first: File, second: File, onBytes: (Int) -> Unit): Boolean {
        if (Files.isSymbolicLink(first.toPath()) || Files.isSymbolicLink(second.toPath())) return false
        if (first.isDirectory && second.isDirectory) {
            val children = first.listFiles() ?: return false
            val others = second.list()?.toSet() ?: return false
            return children.size == others.size && children.all {
                it.name in others && sameContents(it, File(second, it.name), onBytes)
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
                    onBytes(count)
                }
                a.read() == -1 && b.read() == -1
            }
        }
    }
}
