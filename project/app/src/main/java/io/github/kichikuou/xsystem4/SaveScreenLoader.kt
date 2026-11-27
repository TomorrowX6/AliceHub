package io.github.kichikuou.xsystem4

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal class SaveScreenLoader(private val scope: CoroutineScope) {
    companion object {
        private val diskLock = Mutex()
    }

    private var job: Job? = null
    val isLoading: Boolean get() = job?.isActive == true

    fun <T> load(read: () -> T, render: (T) -> Unit, onError: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { diskLock.withLock { read() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SaveScreenLoader", "Could not load save screen", e)
                onError()
                return@launch
            }
            render(result)
        }
    }

    suspend fun awaitLatest() {
        while (true) {
            val pending = job ?: return
            pending.join()
            if (pending === job) return
        }
    }
}

data class SaveTargets(val all: List<SaveTarget>, val selected: SaveTarget)

fun discoverSaveTargets(
    activity: Activity,
    scopedGameId: String?,
    preferredDirName: String?,
    resolveInstallAlias: Boolean
): SaveTargets {
    val gameRoots = AppStorage.gameScanRoots(activity)
    fun findGameDir(vararg names: String): File? = gameRoots.firstNotNullOfOrNull { root ->
        names.firstNotNullOfOrNull { name -> File(root, name).takeIf { it.isDirectory } }
    }

    val found = LinkedHashMap<String, SaveTarget>()
    var resolvedPreferredDirName = preferredDirName
    var scopedDefaultDirName: String? = null
    val gameList = GameList(activity)
    try {
        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val saveDir = item.savedir ?: Item.resolveSaveDir(activity, item.path)
            val saveKey = saveDir.name
            saveDir.mkdirs()
            if (resolveInstallAlias && preferredDirName != null && item.path.name == preferredDirName) {
                resolvedPreferredDirName = saveKey
            }
            found[saveKey] = SaveTarget(
                displayName = saveKey,
                dirName = saveKey,
                saveDir = saveDir,
                externalGameDir = item.path
            )
        }

        AppStorage.saveRoots(activity).flatMap { it.listFiles().orEmpty().asList() }
            .filter { it.isDirectory && !it.name.startsWith('.') && it == AppStorage.saveDir(activity, it.name) }
            .forEach { dir ->
                if (!found.containsKey(dir.name)) {
                    val baseGameName = dir.name.removeSuffix(Item.DAISENGOKU_SAVE_DIR_SUFFIX)
                    val extDir = findGameDir(dir.name, baseGameName)
                    found[dir.name] = SaveTarget(
                        displayName = dir.name,
                        dirName = dir.name,
                        saveDir = dir,
                        externalGameDir = extDir
                    )
                }
            }

        val gid = scopedGameId
        if (!gid.isNullOrBlank()) {
            var hasNonAmbiguousTarget = false
            val iter = found.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next().value
                val isPreferred = entry.dirName == resolvedPreferredDirName || entry.dirName == preferredDirName
                val matchesScope = GameCatalog.matchesScopedSaveDirectory(
                    scopedGameId = gid,
                    gameDir = entry.externalGameDir,
                    dirName = entry.dirName,
                    saveDir = entry.saveDir,
                    displayName = entry.displayName
                )
                if (!matchesScope && !isPreferred) {
                    iter.remove()
                } else if (isPreferred || !GameCatalog.isAmbiguousOkusamaSaveDir(
                        gameDir = entry.externalGameDir,
                        dirName = entry.dirName,
                        saveDir = entry.saveDir,
                        displayName = entry.displayName
                    )
                ) {
                    hasNonAmbiguousTarget = true
                }
            }
            if (!hasNonAmbiguousTarget) {
                val desc = GameCatalog.descriptorFor(activity, gid, gameList)
                scopedDefaultDirName = desc.defaultDirName
                if (!found.containsKey(desc.defaultDirName)) {
                    val defaultDir = AppStorage.saveDir(activity, desc.defaultDirName)
                    defaultDir.mkdirs()
                    val extDir = findGameDir(desc.defaultDirName)
                    found[desc.defaultDirName] = SaveTarget(
                        displayName = desc.defaultDirName,
                        dirName = desc.defaultDirName,
                        saveDir = defaultDir,
                        externalGameDir = extDir
                    )
                }
            }
        } else if (found.isEmpty()) {
            val defaultDir = AppStorage.saveDir(activity, "Rance10")
            defaultDir.mkdirs()
            val extDir = findGameDir("Rance10")
            found["Rance10"] = SaveTarget(
                displayName = "Rance10",
                dirName = "Rance10",
                saveDir = defaultDir,
                externalGameDir = extDir
            )
        }
    } finally {
        gameList.close()
    }

    val targets = found.values.toList()
    val currentTarget = targets.firstOrNull { it.dirName == resolvedPreferredDirName }
        ?: targets.firstOrNull { it.dirName == preferredDirName }
        ?: scopedDefaultDirName?.let { def -> targets.firstOrNull { it.dirName == def } }
        ?: targets.firstOrNull {
            !GameCatalog.isAmbiguousOkusamaSaveDir(it.externalGameDir, it.dirName, it.saveDir, it.displayName)
        }
        ?: targets.first()
    return SaveTargets(targets, currentTarget)
}
