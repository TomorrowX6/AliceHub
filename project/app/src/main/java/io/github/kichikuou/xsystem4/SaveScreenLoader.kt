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

internal data class SaveTargets(val all: List<SaveTarget>, val selected: SaveTarget)

internal fun discoverSaveTargets(
    activity: Activity,
    scopedGameId: String?,
    preferredDirName: String?,
    resolveInstallAlias: Boolean
): SaveTargets {
    val savesRoot = File(activity.filesDir, "saves")
    savesRoot.mkdirs()

    val found = LinkedHashMap<String, SaveTarget>()
    var resolvedPreferredDirName = preferredDirName
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

        savesRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.forEach { dir ->
            if (!found.containsKey(dir.name)) {
                val baseGameName = dir.name.removeSuffix(Item.DAISENGOKU_SAVE_DIR_SUFFIX)
                val extDir = activity.getExternalFilesDir(null)?.let { root ->
                    File(root, dir.name).takeIf { it.exists() }
                        ?: File(root, baseGameName).takeIf { it.exists() }
                }
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
            val iter = found.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next().value
                val classified = GameCatalog.classifyDirectory(
                    gameDir = entry.externalGameDir,
                    dirName = entry.dirName,
                    saveDir = entry.saveDir,
                    displayName = entry.displayName
                )
                if (classified != gid && entry.dirName != resolvedPreferredDirName && entry.dirName != preferredDirName) {
                    iter.remove()
                }
            }
            if (found.isEmpty()) {
                val desc = GameCatalog.descriptorFor(activity, gid, gameList)
                val defaultDir = File(savesRoot, desc.defaultDirName)
                defaultDir.mkdirs()
                val extDir = activity.getExternalFilesDir(null)?.let { File(it, desc.defaultDirName) }?.takeIf { it.exists() }
                found[desc.defaultDirName] = SaveTarget(
                    displayName = desc.defaultDirName,
                    dirName = desc.defaultDirName,
                    saveDir = defaultDir,
                    externalGameDir = extDir
                )
            }
        } else if (found.isEmpty()) {
            val defaultDir = File(savesRoot, "Rance10")
            defaultDir.mkdirs()
            val extDir = activity.getExternalFilesDir(null)?.let { File(it, "Rance10") }
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
        ?: targets.first()
    return SaveTargets(targets, currentTarget)
}
