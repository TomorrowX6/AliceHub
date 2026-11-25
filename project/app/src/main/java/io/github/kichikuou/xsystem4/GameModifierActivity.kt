package io.github.kichikuou.xsystem4

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ModSaveSlotEntry(
    val file: File,
    val relativePath: String,
    val slotNum: Int,
    val tag: String,
    val sizeBytes: Long,
    val lastModified: Long
)

private class ModSaveSlotAdapter(
    private val activity: Activity,
    private var entries: List<ModSaveSlotEntry>,
    private val backupLabel: String
) : BaseAdapter() {

    var accentColor: Int? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun update(newEntries: List<ModSaveSlotEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): ModSaveSlotEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(activity)
            .inflate(R.layout.list_item_entry, parent, false)
        val entry = entries[position]

        view.findViewById<TextView>(R.id.title).text = entry.relativePath
        val primaryTone = slotTone(entry.slotNum)
        val chips = mutableListOf(UiKit.Chip(entry.tag, primaryTone))
        if (GameModifierBridge.hasModBackup(entry.file)) {
            chips.add(UiKit.Chip(backupLabel, UiKit.Tone.SUCCESS))
        }
        UiKit.bindChips(view, chips)
        val customAccent = accentColor
        if (primaryTone == UiKit.Tone.PRIMARY && customAccent != null) {
            view.findViewById<TextView>(R.id.chip1)?.let { UiKit.styleAccentChip(it, customAccent) }
        }
        val sizeStr = String.format(Locale.US, "%.1f KB", entry.sizeBytes / 1024.0)
        val dateStr = dateFormat.format(Date(entry.lastModified))
        view.findViewById<TextView>(R.id.meta).text = "$sizeStr · $dateStr"
        return view
    }

    /** Same slot ranges as the tag labels: checkpoint / cleared run / auto / normal. */
    private fun slotTone(slotNum: Int): UiKit.Tone = when {
        slotNum >= 5000 -> UiKit.Tone.WARNING
        slotNum >= 1000 -> UiKit.Tone.PRIMARY
        slotNum >= 100 -> UiKit.Tone.INFO
        else -> UiKit.Tone.NEUTRAL
    }
}

// Status-card views live in game_modifier_header, added with addHeaderView. Lint only
// sees the activity layout, so those ids look missing.
@SuppressLint("MissingInflatedId")
class GameModifierActivity : ThemedActivity() {
    companion object {
        const val EXTRA_DIR_NAME = "EXTRA_DIR_NAME"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
    }

    private var scopedGameId: String? = null
    private var targets: MutableList<SaveTarget> = mutableListOf()
    private lateinit var currentTarget: SaveTarget
    private lateinit var adapter: ModSaveSlotAdapter
    private var currentEntries: List<ModSaveSlotEntry> = emptyList()
    private var updatingSwitches = false
    private val trackedDialogs = mutableListOf<android.app.Dialog>()

    private fun trackDialog(dlg: android.app.Dialog) {
        trackedDialogs.removeAll { !it.isShowing }
        if (!trackedDialogs.contains(dlg)) {
            trackedDialogs.add(dlg)
        }
    }

    private fun dismissTrackedDialogs() {
        val copy = trackedDialogs.toList()
        trackedDialogs.clear()
        for (dlg in copy) {
            try {
                if (dlg.isShowing) dlg.dismiss()
            } catch (_: Throwable) {
            }
        }
    }

    private fun dialogBuilder(): AlertDialog.Builder =
        object : AlertDialog.Builder(this) {
            override fun create(): AlertDialog {
                val dlg = super.create()
                trackDialog(dlg)
                return dlg
            }

            override fun show(): AlertDialog {
                val dlg = super.show()
                trackDialog(dlg)
                return dlg
            }
        }

    private val isZh: Boolean
        get() = AppSettings.resolveLocale(this).language.startsWith("zh")
    private val isJa: Boolean
        get() = AppSettings.resolveLocale(this).language.startsWith("ja")

    private fun tr(zh: String, ja: String, en: String): String = when {
        isZh -> zh
        isJa -> ja
        else -> en
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_modifier)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // The info card, switches and buttons scroll with the list as its header. It has to be
        // added before the adapter is set; its views are then found by findViewById() as usual.
        val listView = findViewById<ListView>(R.id.mod_save_list)
        listView.addHeaderView(
            layoutInflater.inflate(R.layout.game_modifier_header, listView, false), null, false
        )

        scopedGameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
        val modLabel = tr("游戏修改器", "ゲーム改造", "Game Modifier")
        title = scopedGameId?.let { gid ->
            "${GameCatalog.descriptorFor(this, gid).title} · $modLabel"
        } ?: modLabel

        findViewById<TextView>(R.id.label_floating_modifier).text = tr("悬浮窗修改器", "フローティング改造", "Floating Modifier")
        findViewById<TextView>(R.id.label_ctrl_overlay).text = tr("Ctrl 快进键", "Ctrl 早送りキー", "Ctrl Skip Key")
        findViewById<Button>(R.id.btn_mod_full_unlock).text = tr("全解锁存档", "全解放セーブ", "Full Unlock")
        findViewById<Button>(R.id.btn_mod_global_save).text = tr("全局存档", "全体セーブ", "Global Save")
        findViewById<Button>(R.id.btn_mod_floating_cfg).text = tr("实时锁定", "リアルタイムロック", "Live Locks")
        refreshUiScaleButtons()

        val floatingSwitch = findViewById<Switch>(R.id.switch_floating_modifier)
        val ctrlOverlaySwitch = findViewById<Switch>(R.id.switch_ctrl_overlay)
        syncTopSwitches()

        floatingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            GameModifierBridge.setFloatingEnabled(this, isChecked)
            refreshSummaryText()
        }

        ctrlOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            GameModifierBridge.setCtrlOverlayEnabled(this, isChecked)
            refreshSummaryText()
        }

        adapter = ModSaveSlotAdapter(this, emptyList(), tr("已备份", "バックアップ済", "Backed up"))
        listView.adapter = adapter

        discoverSaveTargets(intent.getStringExtra(EXTRA_DIR_NAME))

        findViewById<View>(R.id.btn_mod_switch_dir).setOnClickListener { showSwitchTargetDialog() }
        findViewById<View>(R.id.btn_mod_full_unlock).setOnClickListener {
            ensureGameNotRunning(this) { showFullUnlockDialog() }
        }
        findViewById<View>(R.id.btn_mod_global_save).setOnClickListener {
            ensureGameNotRunning(this) { showGlobalSaveMenuDialog() }
        }
        findViewById<View>(R.id.btn_mod_floating_cfg).setOnClickListener { showFloatingLocksDialog() }
        findViewById<View>(R.id.btn_mod_font_size).setOnClickListener { showFontScaleDialog() }
        findViewById<View>(R.id.btn_mod_scrollbar).setOnClickListener { showScrollbarScaleDialog() }
        findViewById<View>(R.id.btn_mod_gamepad)?.setOnClickListener { openGamepadSettings() }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val index = pos - listView.headerViewsCount
            if (index in 0 until adapter.count) {
                val slot = adapter.getItem(index)
                ensureGameNotRunning(this) { showLocalSaveMenuDialog(slot) }
            }
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        syncTopSwitches()
        if (::currentTarget.isInitialized) {
            discoverSaveTargets(currentTarget.dirName)
            refreshList()
        }
    }

    override fun onDestroy() {
        dismissTrackedDialogs()
        super.onDestroy()
    }

    private fun syncTopSwitches() {
        updatingSwitches = true
        try {
            findViewById<Switch>(R.id.switch_floating_modifier)?.isChecked =
                GameModifierBridge.isFloatingEnabled(this)
            findViewById<Switch>(R.id.switch_ctrl_overlay)?.isChecked =
                GameModifierBridge.isCtrlOverlayEnabled(this)
        } finally {
            updatingSwitches = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun discoverSaveTargets(preferredDirName: String? = null) {
        val savesRoot = File(filesDir, "saves")
        savesRoot.mkdirs()

        val found = LinkedHashMap<String, SaveTarget>()
        var resolvedPreferredDirName = preferredDirName
        val gameList = GameList(this)
        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val saveDir = item.savedir ?: Item.resolveSaveDir(filesDir, item.path)
            val saveKey = saveDir.name
            saveDir.mkdirs()
            if (preferredDirName != null && item.path.name == preferredDirName) {
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
                val extDir = getExternalFilesDir(null)?.let { root ->
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
                val desc = GameCatalog.descriptorFor(this, gid, gameList)
                val defaultDir = File(savesRoot, desc.defaultDirName)
                defaultDir.mkdirs()
                val extDir = getExternalFilesDir(null)?.let { File(it, desc.defaultDirName) }?.takeIf { it.exists() }
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
            val extDir = getExternalFilesDir(null)?.let { File(it, "Rance10") }
            found["Rance10"] = SaveTarget(
                displayName = "Rance10",
                dirName = "Rance10",
                saveDir = defaultDir,
                externalGameDir = extDir
            )
        }

        targets = found.values.toMutableList()
        currentTarget = targets.firstOrNull { it.dirName == resolvedPreferredDirName }
            ?: targets.firstOrNull { it.dirName == preferredDirName }
            ?: targets.first()
    }

    private fun isOtherScopedGame(expectedGameId: String): Boolean {
        val id = scopedGameId ?: return false
        return id != expectedGameId && (
            id == GameCatalog.ID_RANCE10 ||
                id == GameCatalog.ID_DOHNA_DOHNA ||
                id == GameCatalog.ID_EVENICLE ||
                id == GameCatalog.ID_EVENICLE2 ||
                id == GameCatalog.ID_SENGOKU_RANCE ||
                id == GameCatalog.ID_RANCE01 ||
                id == GameCatalog.ID_RANCE02 ||
                id == GameCatalog.ID_RANCE03 ||
                id == GameCatalog.ID_RANCE04 ||
                id == GameCatalog.ID_RANCE41 ||
                id == GameCatalog.ID_RANCE42 ||
                id == GameCatalog.ID_RANCE5D ||
                id == GameCatalog.ID_RANCE06 ||
                id == GameCatalog.ID_RANCE08 ||
                id == GameCatalog.ID_RANCE09 ||
                id == GameCatalog.ID_KICHIKUOU ||
                id == GameCatalog.ID_DAIAKUJI ||
                id == GameCatalog.ID_DAIBANCHOU ||
                id == GameCatalog.ID_DAITEIKOKU ||
                id == GameCatalog.ID_ESCALAYER ||
                id == GameCatalog.ID_ESCALAYER_REBOOT ||
                id == GameCatalog.ID_GALZOO ||
                id == GameCatalog.ID_IXSEAL ||
                id == GameCatalog.ID_HARUKA ||
                id == GameCatalog.ID_TSUMASHIBORI ||
                id == GameCatalog.ID_TSUMAMIGUI ||
                id == GameCatalog.ID_TSUMAMIGUI3 ||
                id == GameCatalog.ID_KAERUNYO ||
                id == GameCatalog.ID_ATLACHNACHA ||
                id == GameCatalog.ID_REISHIKI ||
                id == GameCatalog.ID_HARUURARE ||
                id == GameCatalog.ID_OKUSAMAJUN ||
                id == GameCatalog.ID_OKUSAMANTR ||
                id == GameCatalog.ID_EVENICLE2RANCE ||
                id == GameCatalog.ID_EVENICLEGAIDEN ||
                id == GameCatalog.ID_WAIDONYO ||
                id == GameCatalog.ID_RANCE6AFTER ||
                id == GameCatalog.ID_YAKATA2 ||
                id == GameCatalog.ID_SDPSMARIA ||
                id == GameCatalog.ID_TOUSHIN3 ||
                id == GameCatalog.ID_OYAKORANKAN ||
                id == GameCatalog.ID_YORUGAKURU ||
                id == GameCatalog.ID_RANCE1DIGEST ||
                id == GameCatalog.ID_MAMAN ||
                id == GameCatalog.ID_EVENICLERANCE ||
                id == GameCatalog.ID_HAHARANMAN ||
                id == GameCatalog.ID_TSUMAMIGUI2 ||
                id == GameCatalog.ID_TSUMAMIGUI3JUN ||
                id == GameCatalog.ID_TSUMAMIGUI3NTR ||
                id == GameCatalog.ID_DIABOLIQUE ||
                id == GameCatalog.ID_GAKUENKING ||
                id == GameCatalog.ID_GAKUENKINGEX ||
                id == GameCatalog.ID_OUDOUYUUSHA ||
                id == GameCatalog.ID_TOUSHIN1 ||
                id == GameCatalog.ID_TOUSHIN2 ||
                id == GameCatalog.ID_DARCROWS ||
                id == GameCatalog.ID_IKUSAMIKO ||
                id == GameCatalog.ID_ONLYYOU ||
                id == GameCatalog.ID_SANBIKI ||
                id == GameCatalog.ID_PERSIOM ||
                id == GameCatalog.ID_SEEINAO ||
                id == GameCatalog.ID_HUSHABY ||
                id == GameCatalog.ID_AYUMI ||
                id == GameCatalog.ID_CIELCLAIRE ||
                id == GameCatalog.ID_NIGHTDEMON ||
                id == GameCatalog.ID_KATSUMI ||
                id == GameCatalog.ID_DRSTOP ||
                id == GameCatalog.ID_DPSALL ||
                id == GameCatalog.ID_KOREDPS ||
                id == GameCatalog.ID_KAERUALICE ||
                id == GameCatalog.ID_PROSTUDENTG ||
                id == GameCatalog.ID_PROSTUDENTGOOD ||
                id == GameCatalog.ID_PASTELCHIME ||
                id == GameCatalog.ID_PASTELCHIME2 ||
                id == GameCatalog.ID_YAKATA3 ||
                id == GameCatalog.ID_DALK ||
                id == GameCatalog.ID_NINGENGARI ||
                id == GameCatalog.ID_PASCHACPP ||
                id == GameCatalog.ID_PASCHA3 ||
                id == GameCatalog.ID_MAMATOTO ||
                id == GameCatalog.ID_ONLYYOURECROSS ||
                id == GameCatalog.ID_MAMORIGAMI ||
                id == GameCatalog.ID_AMBIVALENZ ||
                id == GameCatalog.ID_FUNNYBEE ||
                id == GameCatalog.ID_TOUSHIN2SP ||
                id == GameCatalog.ID_MUGENHOUYOU ||
                id == GameCatalog.ID_OTOMESENKI ||
                id == GameCatalog.ID_GAKUENHYOURYUU ||
                id == GameCatalog.ID_MAIDNOSUSUME ||
                id == GameCatalog.ID_DALKGAIDEN ||
                id == GameCatalog.ID_MAMANYONYO ||
                id == GameCatalog.ID_MAJONOSHOKUZAI ||
                id == GameCatalog.ID_SABOTEN ||
                id == GameCatalog.ID_DOUBLESENSEI ||
                id == GameCatalog.ID_OJOUSAMA ||
                id == GameCatalog.ID_EDORANZE ||
                id == GameCatalog.ID_VANISH ||
                id == GameCatalog.ID_HOKENSHITSU ||
                id == GameCatalog.ID_KAMUNAGARA ||
                id == GameCatalog.ID_ERUMEIFLOWER ||
                id == GameCatalog.ID_ERUMEIWAKARE ||
                id == GameCatalog.ID_MANANA1 ||
                id == GameCatalog.ID_MANANA2 ||
                id == GameCatalog.ID_MANANA3 ||
                id == GameCatalog.ID_MANANA4 ||
                id == GameCatalog.ID_MANANA5 ||
                id == GameCatalog.ID_TSUMASAKURA ||
                id == GameCatalog.ID_SHAMANSSANCTUARY ||
                id == GameCatalog.ID_ALIVEZ ||
                id == GameCatalog.ID_DUNGEONSDOLLS ||
                id == GameCatalog.ID_SUZUMIYA ||
                id == GameCatalog.ID_SHIMAIMA ||
                id == GameCatalog.ID_DAIBANCHOUGAIDEN ||
                id == GameCatalog.ID_COSPLAYLEMON ||
                id == GameCatalog.ID_WHIPHONEY ||
                id == GameCatalog.ID_ONIMUSU ||
                id == GameCatalog.ID_PROGRX ||
                id == GameCatalog.ID_DPSVERN ||
                id == GameCatalog.ID_SUKIIRONO ||
                id == GameCatalog.ID_GIRLS ||
                id == GameCatalog.ID_DEBUT ||
                id == GameCatalog.ID_HARUKAVSESCA ||
                id == GameCatalog.ID_HARUKANINJA ||
                id == GameCatalog.ID_MEGANE ||
                id == GameCatalog.ID_RACHELSAKURA ||
                id == GameCatalog.ID_SABAKUNIKA ||
                id == GameCatalog.ID_VANISHPALM ||
                id == GameCatalog.ID_BLADEBRIDERS ||
                id == GameCatalog.ID_DORAPEKO ||
                id == GameCatalog.ID_HANIWAPETIT ||
                id == GameCatalog.ID_YAKATA456 ||
                id == GameCatalog.ID_UMINIOCHITE ||
                id == GameCatalog.ID_GAKUENHYOURYUU2 ||
                id == GameCatalog.ID_YOUSEI ||
                id == GameCatalog.ID_TOBIRA ||
                id == GameCatalog.ID_BUROBURO ||
                id == GameCatalog.ID_KUDAKERO ||
                id == GameCatalog.ID_LITTLEVAMPIRE ||
                id == GameCatalog.ID_INTRUDER ||
                id == GameCatalog.ID_CRESCENT ||
                id == GameCatalog.ID_TENGU ||
                id == GameCatalog.ID_DPS ||
                id == GameCatalog.ID_YAKATA1 ||
                id == GameCatalog.ID_SDPSTONO ||
                id == GameCatalog.ID_SDPSKAIZOKU ||
                id == GameCatalog.ID_PROGOMAKE ||
                id == GameCatalog.ID_AYUMIHINT ||
                id == GameCatalog.ID_AYUMILIVE ||
                id == GameCatalog.ID_NISENAGURI ||
                id == GameCatalog.ID_CAROL ||
                id == GameCatalog.ID_OTHELLOT ||
                id == GameCatalog.ID_MASQUERADE ||
                id == GameCatalog.ID_DEKIRUKANA ||
                id == GameCatalog.ID_TATAKIKOROSHI ||
                id == GameCatalog.ID_BALLOONPOP ||
                id == GameCatalog.ID_HIYOKO2 ||
                id == GameCatalog.ID_SELFGAME ||
                id == GameCatalog.ID_MOMOIROGUARDIAN ||
                id == GameCatalog.ID_RANCE1CLASSIC ||
                id == GameCatalog.ID_RANCE2CLASSIC ||
                id == GameCatalog.ID_RANCE3CLASSIC ||
                id == GameCatalog.ID_DPSSGFAHREN ||
                id == GameCatalog.ID_DPSSGKATEI ||
                id == GameCatalog.ID_DPSSGNOBUNAGA ||
                id == GameCatalog.ID_DPSSG2ANTIQUE ||
                id == GameCatalog.ID_DPSSG2IKENAI ||
                id == GameCatalog.ID_DPSSG2AKAI ||
                id == GameCatalog.ID_DPSSG3RABBIT ||
                id == GameCatalog.ID_DPSSG3SHINKON ||
                id == GameCatalog.ID_DPSSG3SOTSUGYOU ||
                id.startsWith(GameCatalog.PREFIX_CUSTOM)
            )
    }

    private fun isCurrentTargetRance10(): Boolean {
        if (isOtherScopedGame(GameCatalog.ID_RANCE10)) {
            return false
        }
        if (scopedGameId == GameCatalog.ID_RANCE10) {
            return true
        }
        val extDir = currentTarget.externalGameDir
        if (GameCatalog.isSystem3xGameDir(extDir)) {
            return false
        }
        if (extDir == null &&
            File(currentTarget.saveDir, "GlobalSaveData.asd").isFile &&
            !File(currentTarget.saveDir, "GlobalSave.asd").isFile
        ) {
            return false
        }
        return GameModifierBridge.isRance10GameDir(extDir, currentTarget.dirName, currentTarget.saveDir)
    }

    private fun isCurrentTargetDohnaDohna(): Boolean {
        if (scopedGameId == GameCatalog.ID_DOHNA_DOHNA) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_DOHNA_DOHNA)) {
            return false
        }
        return GameModifierBridge.isDohnaDohnaGameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetEvenicle(): Boolean {
        if (scopedGameId == GameCatalog.ID_EVENICLE) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_EVENICLE)) {
            return false
        }
        return GameModifierBridge.isEvenicleGameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetSengokuRance(): Boolean {
        if (scopedGameId == GameCatalog.ID_SENGOKU_RANCE) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_SENGOKU_RANCE)) {
            return false
        }
        return GameModifierBridge.isSengokuRanceGameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetRance01(): Boolean {
        if (scopedGameId == GameCatalog.ID_RANCE01) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_RANCE01)) {
            return false
        }
        return GameModifierBridge.isRance01GameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetRance02(): Boolean {
        if (scopedGameId == GameCatalog.ID_RANCE02) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_RANCE02)) {
            return false
        }
        return GameModifierBridge.isRance02GameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetRance03(): Boolean {
        if (scopedGameId == GameCatalog.ID_RANCE03) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_RANCE03)) {
            return false
        }
        return GameModifierBridge.isRance03GameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetRance08(): Boolean {
        if (scopedGameId == GameCatalog.ID_RANCE08) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_RANCE08)) {
            return false
        }
        return GameModifierBridge.isRance08GameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetRance09(): Boolean {
        if (scopedGameId == GameCatalog.ID_RANCE09) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_RANCE09)) {
            return false
        }
        return GameModifierBridge.isRance09GameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun isCurrentTargetEscalayer(): Boolean {
        if (scopedGameId == GameCatalog.ID_ESCALAYER) {
            return true
        }
        if (isOtherScopedGame(GameCatalog.ID_ESCALAYER)) {
            return false
        }
        return GameModifierBridge.isEscalayerGameDir(
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
    }

    private fun currentUiScaleGameKey(): String? =
        GameModifierBridge.normalizeUiScaleGameKey(
            if (::currentTarget.isInitialized) currentTarget.dirName else intent.getStringExtra(EXTRA_DIR_NAME)
        )

    private fun refreshUiScaleButtons() {
        val gameKey = currentUiScaleGameKey()
        val fontPct = GameModifierBridge.getFontScalePercent(this, gameKey)
        val sbPct = GameModifierBridge.getScrollbarScalePercent(this, gameKey)
        findViewById<Button>(R.id.btn_mod_font_size)?.text = tr(
            "字体大小 · $fontPct%",
            "文字サイズ · $fontPct%",
            "Font · $fontPct%"
        )
        findViewById<Button>(R.id.btn_mod_scrollbar)?.text = tr(
            "滚动条 · $sbPct%",
            "スクロールバー · $sbPct%",
            "Scrollbar · $sbPct%"
        )
    }

    private fun refreshTargetControlsVisibility(
        isRance10: Boolean,
        isDohnaDohna: Boolean,
        isEvenicle: Boolean,
        isSengokuRance: Boolean,
        isRance01: Boolean,
        isRance02: Boolean,
        isRance03: Boolean,
        isRance08: Boolean,
        isRance09: Boolean,
        isEscalayer: Boolean
    ) {
        val fullUnlockBtn = findViewById<View>(R.id.btn_mod_full_unlock)
        (fullUnlockBtn?.parent as? View)?.visibility = if (isRance10) View.VISIBLE else View.GONE
        val floatingCfgBtn = findViewById<View>(R.id.btn_mod_floating_cfg)
        val switchDirBtn = findViewById<View>(R.id.btn_mod_switch_dir)
        val showLocks = isRance10 || isDohnaDohna || isEvenicle || isSengokuRance || isRance01 || isRance02 || isRance03 || isRance08 || isRance09 || isEscalayer
        val resolvedGameId = resolvedGameIdForCurrentTarget()
        val isSys3x = isEscalayer ||
            resolvedGameId == GameCatalog.ID_KICHIKUOU ||
            resolvedGameId == GameCatalog.ID_DAIAKUJI ||
            GameCatalog.isSystem3xGameDir(currentTarget.externalGameDir)
        (findViewById<View>(R.id.btn_mod_font_size)?.parent as? View)?.visibility =
            if (isSys3x) View.GONE else View.VISIBLE
        floatingCfgBtn?.visibility = if (showLocks) View.VISIBLE else View.GONE
        (switchDirBtn?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.marginStart = if (showLocks) dp(4) else 0
            switchDirBtn.layoutParams = lp
        }
    }

    private fun refreshSummaryText() {
        val saveDir = currentTarget.saveDir
        val isRance10 = isCurrentTargetRance10()
        val isDohnaDohna = !isRance10 && isCurrentTargetDohnaDohna()
        val isEvenicle = !isRance10 && !isDohnaDohna && isCurrentTargetEvenicle()
        val isSengokuRance = !isRance10 && !isDohnaDohna && !isEvenicle && isCurrentTargetSengokuRance()
        val isRance01 = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && isCurrentTargetRance01()
        val isRance02 = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance01 && isCurrentTargetRance02()
        val isRance03 = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance01 && !isRance02 && isCurrentTargetRance03()
        val isRance08 = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance01 && !isRance02 && !isRance03 && isCurrentTargetRance08()
        val isRance09 = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance01 && !isRance02 && !isRance03 && !isRance08 && isCurrentTargetRance09()
        val isEscalayer = !isRance10 && !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance01 && !isRance02 && !isRance03 && !isRance08 && !isRance09 && isCurrentTargetEscalayer()
        refreshUiScaleButtons()
        refreshTargetControlsVisibility(isRance10, isDohnaDohna, isEvenicle, isSengokuRance, isRance01, isRance02, isRance03, isRance08, isRance09, isEscalayer)
        applyThemeAccent()

        findViewById<TextView>(R.id.mod_target_title).text =
            getString(R.string.save_manager_current_dir, currentTarget.dirName)
        if (!isRance10) {
            val saveCount = saveDir.walkTopDown()
                .filter {
                    it.isFile && (
                        it.name.endsWith(".asd", ignoreCase = true) ||
                            ((isEvenicle || isRance01 || isRance03 || isRance08 || isRance09) && it.name.endsWith(".sav", ignoreCase = true))
                        )
                }
                .count()
            when {
                isDohnaDohna -> {
                    val ddLockCount = GameModifierBridge.getDohnaLiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $ddLockCount 项",
                        "セーブ $saveCount 個 · ロック $ddLockCount 項目",
                        "$saveCount saves · Locks: $ddLockCount"
                    )
                }
                isEvenicle -> {
                    val evLockCount = GameModifierBridge.getEvenicleLiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $evLockCount 项",
                        "セーブ $saveCount 個 · ロック $evLockCount 項目",
                        "$saveCount saves · Locks: $evLockCount"
                    )
                }
                isSengokuRance -> {
                    val srLockCount = GameModifierBridge.getSrLiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $srLockCount 项",
                        "セーブ $saveCount 個 · ロック $srLockCount 項目",
                        "$saveCount saves · Locks: $srLockCount"
                    )
                }
                isRance01 -> {
                    val r01LockCount = GameModifierBridge.getR01LiveLockCount(this)
                    tr(
                        "存档 $saveCount 个 · 锁定 $r01LockCount 项",
                        "セーブ $saveCount 個 · ロック $r01LockCount 項目",
                        "$saveCount saves · Locks: $r01LockCount"
                    )
                }
                isRance02 -> {
                    val r02LockCount = GameModifierBridge.getR02LiveLockCount(this)
                    tr(
                        "存档 $saveCount 个 · 锁定 $r02LockCount 项",
                        "セーブ $saveCount 個 · ロック $r02LockCount 項目",
                        "$saveCount saves · Locks: $r02LockCount"
                    )
                }
                isRance03 -> {
                    val r03LockCount = GameModifierBridge.getR03LiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $r03LockCount 项",
                        "セーブ $saveCount 個 · ロック $r03LockCount 項目",
                        "$saveCount saves · Locks: $r03LockCount"
                    )
                }
                isRance08 -> {
                    val r08LockCount = GameModifierBridge.getR08LiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $r08LockCount 项",
                        "セーブ $saveCount 個 · ロック $r08LockCount 項目",
                        "$saveCount saves · Locks: $r08LockCount"
                    )
                }
                isRance09 -> {
                    val r09LockCount = GameModifierBridge.getR09LiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $r09LockCount 项",
                        "セーブ $saveCount 個 · ロック $r09LockCount 項目",
                        "$saveCount saves · Locks: $r09LockCount"
                    )
                }
                isEscalayer -> {
                    val escLockCount = GameModifierBridge.getEscLiveLockCount(this)
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个 · 锁定 $escLockCount 项",
                        "セーブ $saveCount 個 · ロック $escLockCount 項目",
                        "$saveCount saves · Locks: $escLockCount"
                    )
                }
                else -> {
                    findViewById<TextView>(R.id.mod_target_summary).text = tr(
                        "存档 $saveCount 个",
                        "セーブ $saveCount 個",
                        "$saveCount saves"
                    )
                }
            }
            return
        }

        val hasGlobal = GameModifierBridge.hasGlobalSaveFiles(saveDir)
        val lockCount = GameModifierBridge.getLiveLockCount(this)
        findViewById<TextView>(R.id.mod_target_summary).text = tr(
            "进度存档 ${currentEntries.size} 个 · 全局存档：${if (hasGlobal) "有" else "无"} · 锁定 $lockCount 项",
            "進行セーブ ${currentEntries.size} 個 · 全体：${if (hasGlobal) "あり" else "なし"} · ロック $lockCount 項目",
            "${currentEntries.size} progress saves · Global: ${if (hasGlobal) "Yes" else "No"} · Locks: $lockCount"
        )
    }

    private fun resolvedGameIdForCurrentTarget(): String? =
        scopedGameId ?: if (::currentTarget.isInitialized) {
            GameCatalog.classifyDirectory(
                gameDir = currentTarget.externalGameDir,
                dirName = currentTarget.dirName,
                saveDir = currentTarget.saveDir,
                displayName = currentTarget.displayName
            )
        } else {
            null
        }

    private fun applyThemeAccent() {
        if (!::currentTarget.isInitialized) return
        val accent = UiKit.resolveGameAccentColor(
            this,
            scopedGameId,
            currentTarget.externalGameDir,
            currentTarget.dirName,
            currentTarget.saveDir
        )
        adapter.accentColor = accent
        findViewById<View>(R.id.mod_header_card)?.background =
            UiKit.accentCard(this, accent, R.dimen.ui_radius_card, clickable = false)
        findViewById<Switch>(R.id.switch_floating_modifier)?.let { UiKit.tintSwitch(it, accent) }
        findViewById<Switch>(R.id.switch_ctrl_overlay)?.let { UiKit.tintSwitch(it, accent) }
        listOf(
            R.id.btn_mod_full_unlock,
            R.id.btn_mod_global_save,
            R.id.btn_mod_floating_cfg,
            R.id.btn_mod_switch_dir,
            R.id.btn_mod_font_size,
            R.id.btn_mod_scrollbar,
            R.id.btn_mod_gamepad
        ).forEach { id ->
            findViewById<Button>(id)?.let { UiKit.styleTonalAccentButton(it, accent) }
        }
    }

    private fun openGamepadSettings() {
        val dirName = if (::currentTarget.isInitialized) currentTarget.dirName else null
        val gid = resolvedGameIdForCurrentTarget()
        val displayTitle = gid?.let { GameCatalog.descriptorFor(this, it).title } ?: dirName
        val i = Intent(this, GamepadSettingsActivity::class.java)
            .putExtra(GamepadSettingsActivity.EXTRA_PROFILE_KEY, dirName)
            .putExtra(GamepadSettingsActivity.EXTRA_GAME_ID, gid)
            .putExtra(GamepadSettingsActivity.EXTRA_GAME_TITLE, displayTitle)
        startActivity(i)
    }

    private fun refreshList() {
        val saveDir = currentTarget.saveDir
        saveDir.mkdirs()

        val emptyView = findViewById<TextView>(R.id.mod_empty_view)
        if (!isCurrentTargetRance10()) {
            currentEntries = emptyList()
            adapter.update(emptyList())
            refreshSummaryText()
            emptyView.visibility = View.GONE
            return
        }

        val regex = Regex("localsave(\\d+)\\.asd", RegexOption.IGNORE_CASE)
        val slots = mutableListOf<ModSaveSlotEntry>()
        saveDir.walkTopDown().forEach { f ->
            if (f.isFile) {
                val m = regex.matchEntire(f.name)
                if (m != null) {
                    val num = m.groupValues[1].toIntOrNull() ?: 0
                    val rel = f.relativeTo(saveDir).path.replace('\\', '/')
                    val tag = when {
                        num >= 5000 -> tr("节点存档", "拠点", "Checkpoint")
                        num >= 1000 -> tr("周回存档", "周回", "Clear")
                        num >= 100 -> tr("自动存档", "オート", "Auto")
                        else -> tr("普通存档", "通常", "Normal")
                    }
                    slots.add(ModSaveSlotEntry(f, rel, num, tag, f.length(), f.lastModified()))
                }
            }
        }
        slots.sortBy { it.slotNum }
        currentEntries = slots
        adapter.update(slots)
        refreshSummaryText()

        if (slots.isEmpty()) {
            UiKit.bindEmptyState(
                emptyView,
                R.drawable.ic_save,
                tr("当前无进度存档", "進行セーブなし", "No progress saves")
            )
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
        }
    }

    private fun showSwitchTargetDialog() {
        discoverSaveTargets(currentTarget.dirName)
        val labels = targets.map { target ->
            val count = target.saveDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".asd", ignoreCase = true) }
                .count()
            getString(R.string.save_dir_item_count, target.dirName, count)
        }.toMutableList()
        labels.add(getString(R.string.save_dir_new_option))

        dialogBuilder()
            .setTitle(R.string.save_dir_select_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == targets.size) {
                    showCreateTargetDialog()
                } else {
                    currentTarget = targets[which]
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateTargetDialog() {
        val defaultPrefix = scopedGameId?.let { GameCatalog.descriptorFor(this, it).defaultDirName } ?: "Rance10"
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.save_dir_create_hint)
            setText(defaultPrefix)
        }
        dialogBuilder()
            .setTitle(R.string.save_dir_create_title)
            .setView(UiKit.dialogContent(this, input))
            .setPositiveButton(R.string.ok) { _, _ ->
                val rawName = input.text.toString().trim()
                val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (safeName.isEmpty() || safeName.startsWith(".")) {
                    Toast.makeText(this, R.string.save_dir_invalid_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newDir = File(File(filesDir, "saves"), safeName)
                newDir.mkdirs()
                discoverSaveTargets(safeName)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // =========================================================================
    // 1. Full Unlock Dialog
    // =========================================================================
    private fun showFullUnlockDialog() {
        if (!isCurrentTargetRance10()) return
        val saveDir = currentTarget.saveDir
        val hasBak = GameModifierBridge.hasFullUnlockBackup(saveDir)
        val options = mutableListOf(
            tr("写入全解锁存档", "全解放セーブを適用", "Install Full Unlock Save")
        )
        if (hasBak) {
            options.add(tr("恢复解锁前备份", "解放前のバックアップを復元", "Restore Pre-Unlock Backup"))
        }

        dialogBuilder()
            .setTitle(tr("全解锁存档", "全解放セーブ", "Full Unlock Save"))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    val count = GameModifierBridge.installFullUnlockSaves(this, saveDir)
                    refreshList()
                    Toast.makeText(
                        this,
                        tr("已写入全解锁存档 · $count 个文件", "全解放セーブを適用しました", "Installed $count unlock files"),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (which == 1 && hasBak) {
                    val restored = GameModifierBridge.restoreFullUnlockBackup(saveDir)
                    refreshList()
                    Toast.makeText(this, tr("已恢复 $restored 个备份文件", "$restored 個のファイルを復元しました", "Restored $restored files"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // =========================================================================
    // 2. Global Save Menu & Editor
    // =========================================================================
    private fun showGlobalSaveMenuDialog() {
        if (!isCurrentTargetRance10()) return
        if (!GameModifierBridge.ensureLoaded()) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            return
        }
        val saveDir = currentTarget.saveDir
        if (!GameModifierBridge.hasGlobalSaveFiles(saveDir)) {
            dialogBuilder()
                .setTitle(tr("无全局存档", "全体セーブなし", "No Global Save"))
                .setMessage(tr("当前无全局存档，是否写入全解锁存档？", "先に全解放セーブを適用しますか？", "Install Full Unlock Save first?"))
                .setPositiveButton(R.string.ok) { _, _ ->
                    GameModifierBridge.installFullUnlockSaves(this, saveDir)
                    refreshList()
                    showGlobalSaveMenuDialog()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val info = try {
            JSONObject(GameModifierBridge.readGlobalSaveSummaryForDir(saveDir))
        } catch (_: Throwable) {
            JSONObject()
        }
        val cp = info.optInt("totalClearPoint", 0)
        val medal = info.optInt("AutoBattleMedal", 0)
        val expUp = info.optInt("trophyExpBonus", 0)
        val hasBak = GameModifierBridge.hasGlobalSaveModBackup(saveDir)

        val options = mutableListOf(
            tr("一键满 CP / 勋章 / 实绩加成", "CP・メダル・実績最大", "Max CP, Medals & Bonuses"),
            tr("自定义数值", "数値を編集", "Edit Values")
        )
        if (hasBak) {
            options.add(tr("恢复备份", "バックアップを復元", "Restore Backup"))
        }

        dialogBuilder()
            .setTitle(tr("全局存档 · CP:$cp · 勋章:$medal · 经验+$expUp%", "全体セーブ · CP:$cp · メダル:$medal", "Global Save · CP:$cp"))
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val mod = JSONObject().apply {
                            put("totalClearPoint", 999)
                            put("clearBPointMax", 999)
                            put("TotalClearCount", 99)
                            put("Clear_A_Count", 99)
                            put("Clear_B_Count", 99)
                            put("Clear_C_Count", 99)
                            put("AutoBattleMedal", 999)
                            put("AchievementPoint", 999)
                            put("BestScore", 999999)
                            put("LeaderSmallestTurn", 1)
                            put("RealSmallestTurn", 1)
                            put("CollectionClearB", 1)
                            put("OpenMonsterpedia", 1)
                            put("TrophyComplete", 1)
                            put("CardComplete", 1)
                            put("gamePlayCount", 50)
                            put("itemStarRank", 20)
                            put("setAllCharRank", 10)
                            put("setTakeOverCardCount", 99)
                            put("leaderAtkRatio", 10.0)
                            put("trophyExpBonus", 9999)
                            put("trophyStatusMaxPer", 999)
                        }
                        applyGlobalSaveMod(mod)
                    }
                    1 -> showGlobalSaveCustomDialog(info)
                    2 -> {
                        if (GameModifierBridge.restoreGlobalSaveModBackup(saveDir)) {
                            refreshList()
                            Toast.makeText(this, tr("已恢复备份", "復元しました", "Backup restored"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private data class GlobalFieldSpec(
        val key: String,
        val label: String,
        val initialText: String,
        val hintText: String = "",
        val isDecimal: Boolean = false,
        val isFlag: Boolean = false
    )

    private fun showGlobalSaveCustomDialog(info: JSONObject) {
        val ratioVal = info.optDouble("leaderAtkRatio", 1.0)
        val ratioStr = if (ratioVal == ratioVal.toLong().toDouble()) {
            String.format(Locale.US, "%.1f", ratioVal)
        } else {
            String.format(Locale.US, "%.2f", ratioVal)
        }
        val charCount = info.optInt("charRankCount", 0)
        val cardCount = info.optInt("takeOverCardCount", 0)
        val specs = listOf(
            GlobalFieldSpec("totalClearPoint", tr("周回CP点", "周回CP", "Clear CP"), info.optInt("totalClearPoint", 0).toString()),
            GlobalFieldSpec("clearBPointMax", tr("第二部最高CP", "第2部最大CP", "Part 2 Max CP"), info.optInt("clearBPointMax", 0).toString()),
            GlobalFieldSpec("AutoBattleMedal", tr("自动战斗勋章", "自動戦闘メダル", "Auto Medals"), info.optInt("AutoBattleMedal", 0).toString()),
            GlobalFieldSpec("AchievementPoint", tr("实绩点数", "実績ポイント", "Achievement Pt"), info.optInt("AchievementPoint", 0).toString()),
            GlobalFieldSpec("TotalClearCount", tr("总通关次数", "総クリア回数", "Total Clears"), info.optInt("TotalClearCount", 0).toString()),
            GlobalFieldSpec("Clear_A_Count", tr("A路线通关数", "Aクリア回数", "Route A Clears"), info.optInt("Clear_A_Count", 0).toString()),
            GlobalFieldSpec("Clear_B_Count", tr("B路线通关数", "Bクリア回数", "Route B Clears"), info.optInt("Clear_B_Count", 0).toString()),
            GlobalFieldSpec("Clear_C_Count", tr("C路线通关数", "Cクリア回数", "Route C Clears"), info.optInt("Clear_C_Count", 0).toString()),
            GlobalFieldSpec("BestScore", tr("最高得分", "最高スコア", "Best Score"), info.optInt("BestScore", 0).toString()),
            GlobalFieldSpec("LeaderSmallestTurn", tr("魔王最少回合", "魔王最小ターン", "Demon Lord Min Turn"), info.optInt("LeaderSmallestTurn", 0).toString()),
            GlobalFieldSpec("RealSmallestTurn", tr("第二部最少回合", "第2部最小ターン", "Part 2 Min Turn"), info.optInt("RealSmallestTurn", 0).toString()),
            GlobalFieldSpec("CollectionClearB", tr("第二部通关标记 0/1", "第2部クリア 0/1", "Part 2 Clear 0/1"), info.optInt("CollectionClearB", 0).toString(), isFlag = true),
            GlobalFieldSpec("OpenMonsterpedia", tr("魔物图鉴开启 0/1", "魔物図鑑解放 0/1", "Monsterpedia 0/1"), info.optInt("OpenMonsterpedia", 0).toString(), isFlag = true),
            GlobalFieldSpec("TrophyComplete", tr("实绩全达成标记 0/1", "実績コンプ 0/1", "Trophy Complete 0/1"), info.optInt("TrophyComplete", 0).toString(), isFlag = true),
            GlobalFieldSpec("CardComplete", tr("卡牌全收集标记 0/1", "カードコンプ 0/1", "Card Complete 0/1"), info.optInt("CardComplete", 0).toString(), isFlag = true),
            GlobalFieldSpec("gamePlayCount", tr("全局周回计数", "周回プレイ数", "Global Play Count"), info.optInt("gamePlayCount", 0).toString()),
            GlobalFieldSpec("itemStarRank", tr("全局装备星级上限", "装備★ランク", "Global Item Star Rank"), info.optInt("itemStarRank", 0).toString()),
            GlobalFieldSpec("leaderAtkRatio", tr("食券/首领攻击倍率", "リーダー攻撃倍率", "Leader ATK Ratio"), ratioStr, isDecimal = true),
            GlobalFieldSpec("trophyExpBonus", tr("实绩经验加成%", "経験値ボーナス%", "EXP Bonus %"), info.optInt("trophyExpBonus", 0).toString()),
            GlobalFieldSpec("trophyStatusMaxPer", tr("属性上限加成%", "ステータス上限%", "Stat Cap %"), info.optInt("trophyStatusMaxPer", 0).toString()),
            GlobalFieldSpec(
                "setAllCharRank",
                tr("批量设全角色阶级", "全キャラランク一括", "Set All Char Ranks"),
                "",
                hintText = tr("留空不改 ($charCount)", "空欄で維持 ($charCount)", "Blank=keep ($charCount)")
            ),
            GlobalFieldSpec(
                "setTakeOverCardCount",
                tr("批量设继承卡张数", "引継カード枚数一括", "Set Takeover Cards"),
                "",
                hintText = tr("留空不改 ($cardCount)", "空欄で維持 ($cardCount)", "Blank=keep ($cardCount)")
            )
        )

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(24), dp(8), dp(20), dp(4))
        }
        val inputs = LinkedHashMap<GlobalFieldSpec, EditText>()
        for (spec in specs) {
            if (!GameModifierBridge.isGlobalSaveModFieldAvailable(currentTarget.saveDir, spec.key)) continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
            }
            val tv = TextView(this).apply {
                text = spec.label
                setTextAppearance(R.style.TextAppearance_Launcher_Body)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            val et = EditText(this).apply {
                inputType = if (spec.isDecimal) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                }
                if (spec.hintText.isNotEmpty()) {
                    hint = spec.hintText
                }
                if (spec.initialText.isNotEmpty()) {
                    setText(spec.initialText)
                }
                setSingleLine(true)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                    marginStart = dp(12)
                }
            }
            inputs[spec] = et
            row.addView(tv)
            row.addView(et)
            container.addView(row)
        }
        scroll.addView(container)

        dialogBuilder()
            .setTitle(tr("自定义全局存档", "全体セーブ編集", "Edit Global Save"))
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                val mod = JSONObject()
                for ((spec, et) in inputs) {
                    val raw = et.text.toString().trim()
                    if (raw.isEmpty()) continue
                    if (spec.isFlag) {
                        val flag = try {
                            GameModifierBridge.parseGlobalSaveFlagEdit(raw, spec.initialText)
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(this, tr("解锁标记只能为 0 或 1", "解放フラグは 0 または 1", "Unlock flags must be 0 or 1"), Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        if (flag != null) mod.put(spec.key, flag)
                    } else if (spec.isDecimal) {
                        val d = raw.toDoubleOrNull()
                        if (d != null && d.isFinite()) mod.put(spec.key, d)
                    } else {
                        val v = raw.toIntOrNull()
                        if (v != null) mod.put(spec.key, v)
                    }
                }
                applyGlobalSaveMod(mod)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyGlobalSaveMod(mod: JSONObject) {
        val saveDir = currentTarget.saveDir
        for (key in mod.keys().asSequence().toList()) {
            if (!GameModifierBridge.isGlobalSaveModFieldAvailable(saveDir, key)) mod.remove(key)
        }
        if (mod.length() == 0) return
        GameModifierBridge.backupGlobalSaveFilesBeforeMod(saveDir)
        val ok = GameModifierBridge.writeGlobalSaveModForDir(saveDir, mod.toString())
        if (ok) {
            refreshSummaryText()
        }
        Toast.makeText(
            this,
            if (ok) tr("全局存档修改成功", "全体セーブを更新しました", "Global save updated")
            else tr("修改失败", "更新失敗", "Failed"),
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================================
    // 3. In-Game Live Locks Dialog
    // =========================================================================
    private fun showBitmaskLockDialog(
        lockDefs: List<Pair<Int, String>>,
        initialMask: Int,
        saveMask: (Int) -> Unit
    ) {
        var lockMask = initialMask
        val labels = lockDefs.map { it.second }.toTypedArray()
        val checked = BooleanArray(lockDefs.size) { idx ->
            (lockMask and lockDefs[idx].first) != 0
        }
        dialogBuilder()
            .setTitle(tr("实时锁定", "リアルタイムロック", "Live Locks"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val bit = lockDefs[which].first
                lockMask = if (isChecked) (lockMask or bit) else (lockMask and bit.inv())
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                saveMask(lockMask)
                syncTopSwitches()
                refreshSummaryText()
                Toast.makeText(this, tr("锁定设置已保存", "設定を保存しました", "Locks saved"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(tr("全部关闭", "すべて解除", "Clear All")) { _, _ ->
                saveMask(0)
                syncTopSwitches()
                refreshSummaryText()
                Toast.makeText(this, tr("已关闭全部锁定", "すべてのロックを解除しました", "All locks cleared"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFloatingLocksDialog() {
        if (!isCurrentTargetRance10() && isCurrentTargetDohnaDohna()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_DD_ALLY_MAX_HP to tr("我方全员满 HP", "味方全員HP最大", "Party Max HP"),
                GameModifierBridge.LOCK_DD_ALLY_MAX_MP to tr("我方全员满 MP", "味方全員MP最大", "Party Max MP"),
                GameModifierBridge.LOCK_DD_ALLY_MAX_VP to tr("我方全员满怒气 VP", "味方全員VP最大", "Party Max VP"),
                GameModifierBridge.LOCK_DD_ONE_HIT_KILL to tr("敌方 1 血秒杀", "敵全員HP1", "Enemy 1 HP"),
                GameModifierBridge.LOCK_DD_INFINITE_MONEY to tr("无限资金", "資金無限", "Infinite Money"),
                GameModifierBridge.LOCK_DD_INFINITE_TP to tr("无限调教点", "調教P無限", "Infinite TP"),
                GameModifierBridge.LOCK_DD_WORKER_BEST_COND to tr("人材最佳状态", "人材状態維持", "Keep Workers Healthy"),
                GameModifierBridge.LOCK_DD_WORK_FREEZE_TIMER to tr("春销时间锁定", "ハルウリ時間固定", "Freeze Brothel Timer")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedDohnaLockMask(this)
            ) { GameModifierBridge.setSavedDohnaLockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetEvenicle()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_EV_NO_ENCOUNT to tr("禁止随机遇敌", "ランダムエンカウント無効", "Disable Random Encounters"),
                GameModifierBridge.LOCK_EV_ALLY_FULL_HP to tr("我方全员满 HP", "味方全員HP最大", "Party Full HP"),
                GameModifierBridge.LOCK_EV_INF_SKILL_P to tr("无限技能点 SkillP", "スキルP無限", "Infinite Skill Points"),
                GameModifierBridge.LOCK_EV_INF_BP to tr("无限 BP", "BP無限", "Infinite BP"),
                GameModifierBridge.LOCK_EV_ONE_HIT_KILL to tr("战斗敌方 1 血秒杀", "敵全員HP1", "Enemy 1 HP"),
                GameModifierBridge.LOCK_EV_INFINITE_MONEY to tr("无限所持金", "所持金無限", "Infinite Gold")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedEvenicleLockMask(this)
            ) { GameModifierBridge.setSavedEvenicleLockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetSengokuRance()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_SR_INF_GOLD to tr("无限金钱 · 保底 999999", "金無限・999999維持", "Infinite Gold"),
                GameModifierBridge.LOCK_SR_INF_AP to tr("无限行动次数 · 锁定满行动点", "行動回数無限", "Infinite Actions (AP)"),
                GameModifierBridge.LOCK_SR_MAX_SAT to tr("最大满足度 · 保底 999", "満足度999維持", "Max Satisfaction"),
                GameModifierBridge.LOCK_SR_ALLY_FULL_HP to tr("我方合战满兵力与技数", "味方兵数・技回数維持", "Party Full Troops & Skills"),
                GameModifierBridge.LOCK_SR_ONE_HIT_KILL to tr("敌方 1 兵秒杀", "敵兵数1", "Enemy 1 Troop"),
                GameModifierBridge.LOCK_SR_UNLIM_ACTION to tr("我方全员无限行动", "味方全員無限行動", "Unlimited Char Actions")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedSrLockMask(this)
            ) { GameModifierBridge.setSavedSrLockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetRance01()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_R01_FULL_HP to tr("满血锁血 · 保持最大 HP", "HP最大維持", "Keep Full HP"),
                GameModifierBridge.LOCK_R01_ONE_HIT_KILL to tr("战斗秒杀 · 敌人 1 血", "敵HP1・一撃撃破", "One-Hit Kill"),
                GameModifierBridge.LOCK_R01_INF_GOLD to tr("无限金钱 · 保底 888888", "所持金888888維持", "Infinite Gold"),
                GameModifierBridge.LOCK_R01_DROP_100 to tr("100% 宝箱与卡片掉落", "ドロップ率100%", "100% Drop Rate"),
                GameModifierBridge.LOCK_R01_NO_DEBUFF to tr("免疫异常状态", "状態異常無効", "Immunity to Debuffs")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedR01LockMask(this)
            ) { GameModifierBridge.setSavedR01LockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetRance02()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_R02_ALLY_FULL_HP_SP to tr("我方全员满 HP / 满 SP", "味方HP・SP最大維持", "Party Full HP & SP"),
                GameModifierBridge.LOCK_R02_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill"),
                GameModifierBridge.LOCK_R02_INF_GOLD to tr("无限金钱 · 保底 888888", "所持金888888維持", "Infinite Gold"),
                GameModifierBridge.LOCK_R02_INF_ITEMS to tr("无限常用消耗道具 (全99/世界药9)", "消耗アイテム補充維持", "Infinite Consumable Items")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedR02LockMask(this)
            ) { GameModifierBridge.setSavedR02LockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetRance03()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_R03_HP to tr("无限体力 · 我方全员 HP 不减", "味方HP減らない", "Party Infinite HP"),
                GameModifierBridge.LOCK_R03_NO_CD to tr("技能零冷却 · 无回合 CD", "スキルCT0", "Zero Skill Cooldown"),
                GameModifierBridge.LOCK_R03_INF_ITEMS to tr("无限道具 · 消耗道具不减", "アイテム減らない", "Infinite Items"),
                GameModifierBridge.LOCK_R03_DROP_100 to tr("战利品 100% 掉落", "ドロップ率100%", "100% Item Drop"),
                GameModifierBridge.LOCK_R03_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedR03LockMask(this)
            ) { GameModifierBridge.setSavedR03LockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetRance08()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_R08_ALLY_FULL_HP to tr("无限体力 · 我方全员满 HP", "味方HP最大維持", "Party Full HP"),
                GameModifierBridge.LOCK_R08_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill"),
                GameModifierBridge.LOCK_R08_INF_SKILLS to tr("无限技能 · 使用次数不减", "スキル回数無限", "Infinite Skills"),
                GameModifierBridge.LOCK_R08_MAGNUM_MAX to tr("魔剑蓄力 · 万能威力 MAX", "マグナム力最大", "Magnum Force Max"),
                GameModifierBridge.LOCK_R08_INF_GOLD to tr("无限金钱 · 保底 9999999", "所持金無限", "Infinite Gold")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedR08LockMask(this)
            ) { GameModifierBridge.setSavedR08LockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetRance09()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_R09_ALLY_GOD to tr("无限防御 · 我方全员无伤满血", "味方無敵・HP最大", "Party God Mode & Full HP"),
                GameModifierBridge.LOCK_R09_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill"),
                GameModifierBridge.LOCK_R09_INF_ACTION to tr("无限行动 · 回合不消耗行动", "無限行動", "Infinite Actions"),
                GameModifierBridge.LOCK_R09_DROP_100 to tr("金币必掉 · 战利金币高额掉落", "所持金ドロップ最大", "Max Gold Drop"),
                GameModifierBridge.LOCK_R09_INF_RESOURCES to tr("无限资源 · 金币与圣魔点保底", "資源無限維持", "Infinite Gold & Seima")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedR09LockMask(this)
            ) { GameModifierBridge.setSavedR09LockMask(this, it) }
            return
        }
        if (!isCurrentTargetRance10() && isCurrentTargetEscalayer()) {
            val lockDefs = listOf(
                GameModifierBridge.LOCK_ESC_HP_FULL to tr("锁定满 HP", "HP最大ロック", "Lock Full HP"),
                GameModifierBridge.LOCK_ESC_LIBIDO_MAX to tr("锁定满 Libido (必杀槽)", "リビドー最大ロック", "Lock Max Libido"),
                GameModifierBridge.LOCK_ESC_ONEHIT to tr("战斗敌方 1 血秒杀", "敵HP1・一撃撃破", "Enemy 1 HP"),
                GameModifierBridge.LOCK_ESC_LOVE_MAX to tr("锁定满爱情度", "愛情度維持", "Keep High Love")
            )
            showBitmaskLockDialog(
                lockDefs,
                GameModifierBridge.getSavedEscLockMask(this)
            ) { GameModifierBridge.setSavedEscLockMask(this, it) }
            return
        }

        val lockDefs = listOf(
            GameModifierBridge.LOCK_PARTY_MAX_HP to tr("锁定我方满生命", "味方HP最大ロック", "Lock Party Max HP"),
            GameModifierBridge.LOCK_INFINITE_AP to tr("锁定无限AP", "AP 99ロック", "Lock Infinite AP"),
            GameModifierBridge.LOCK_BATTLE_COMBO to tr("锁定999连击加成", "999コンボロック", "Lock 999 Combo"),
            GameModifierBridge.LOCK_BATTLE_ROUND_1 to tr("锁定战斗回合为1", "戦闘ラウンド1固定", "Lock Round to 1"),
            GameModifierBridge.LOCK_ONE_HIT_KILL to tr("敌方1血秒杀", "敵HP1・一撃撃破", "One-Hit Kill"),
            GameModifierBridge.LOCK_ENEMY_ZERO_ATK to tr("敌方0攻击0防御", "敵の攻防0化", "Enemy 0 ATK/DEF"),
            GameModifierBridge.LOCK_LEADER_BOOST_CRIT to tr("全员蓄力必暴连击", "全員ブースト・必殺", "All Leaders Boost & Crit"),
            GameModifierBridge.LOCK_INFINITE_SKILL_USE to tr("技能无限使用次数", "スキル回数無限", "Infinite Skill Uses"),
            GameModifierBridge.LOCK_INFINITE_ZIPPO to tr("无限悔棋打火机", "やり直しジッポ無限", "Infinite Retry Zippo"),
            GameModifierBridge.LOCK_MAX_RESOURCES to tr("资源不低于999", "チケット・勲章999維持", "Keep Resources >= 999"),
            GameModifierBridge.LOCK_TREASURE_100 to tr("宝箱100%掉落", "宝箱100%ドロップ", "100% Treasure Drop")
        )

        var lockMask = GameModifierBridge.getSavedLockMask(this) and GameModifierBridge.LOCK_DISABLE_STREAMER_MODE.inv()
        val labels = lockDefs.map { it.second }.toTypedArray()
        val checked = BooleanArray(lockDefs.size) { idx ->
            (lockMask and lockDefs[idx].first) != 0
        }

        dialogBuilder()
            .setTitle(tr("实时锁定", "リアルタイムロック", "Live Locks"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val bit = lockDefs[which].first
                lockMask = if (isChecked) (lockMask or bit) else (lockMask and bit.inv())
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                GameModifierBridge.setLiveLocksOnlyMask(this, lockMask)
                syncTopSwitches()
                refreshSummaryText()
                Toast.makeText(this, tr("锁定设置已保存", "設定を保存しました", "Locks saved"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(tr("全部关闭", "すべて解除", "Clear All")) { _, _ ->
                GameModifierBridge.setLiveLocksOnlyMask(this, 0)
                syncTopSwitches()
                refreshSummaryText()
                Toast.makeText(this, tr("已关闭全部锁定", "すべてのロックを解除しました", "All locks cleared"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // =========================================================================
    // 4. LocalSave*.asd Slot Menu & Editors
    // =========================================================================
    private fun showLocalSaveMenuDialog(entry: ModSaveSlotEntry) {
        if (!isCurrentTargetRance10()) return
        if (!GameModifierBridge.ensureLoaded()) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            return
        }
        val info = try {
            JSONObject(GameModifierBridge.nativeReadLocalSaveSummary(entry.file.absolutePath))
        } catch (_: Throwable) {
            JSONObject()
        }
        if (!info.optBoolean("ok", false)) {
            Toast.makeText(this, tr("无法解析该存档文件", "セーブを解析できません", "Cannot parse save file"), Toast.LENGTH_SHORT).show()
            return
        }

        val turn = info.optInt("turn", 0)
        val food = info.optInt("foodTicket", 0)
        val honor = info.optInt("honor", 0)
        val hp = info.optInt("partyHp", 0)
        val maxHp = info.optInt("partyMaxHp", 0)
        val hasBak = GameModifierBridge.hasModBackup(entry.file)

        val options = mutableListOf(
            tr("一键满资源与属性", "リソース・部隊ステータス最大", "Max Resources & Stats"),
            tr("一键宝箱100%掉落", "宝箱100%ドロップ化", "100% Treasure Drop"),
            tr("一键全卡99叠与5星", "全カード99枚＋全キャラ★5", "All Cards x99 & 5★"),
            tr("一键满兵力与零伤亡", "四国兵力最大＋人類被害ゼロ", "Max Armies & 0 Casualties"),
            tr("资源与回合…", "リソース・ターン…", "Resources & Turn…"),
            tr("部队与属性…", "部隊・ステータス…", "Party & Stats…"),
            tr("四国与人口…", "四国・人口…", "World & Armies…"),
            tr("卡牌与角色…", "カード・キャラ…", "Cards & Chars…")
        )
        if (hasBak) {
            options.add(tr("恢复备份", "バックアップを復元", "Restore Backup"))
        }

        dialogBuilder()
            .setTitle("${entry.file.name} · ${tr("回合", "T", "Turn")} $turn · ${tr("食券", "食券", "Food")} $food · ${tr("勋章", "勲章", "Medal")} $honor · HP $hp/$maxHp")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val mod = JSONObject().apply {
                            put("difficulty", 0)
                            put("foodTicket", 999)
                            put("honor", 999)
                            put("friendPoint", 999)
                            put("totalExp", 99999999)
                            put("partyRank", 999)
                            put("treasureNotGetCount", 99)
                            put("itemStar", 99)
                            put("isUsedHannyZippo", 0)
                            put("hannyZippo", 99)
                            put("partyBonusMaxPoint", 99)
                            put("partyHp", 999999)
                            put("partyMaxHp", 999999)
                            put("partyAvailCount", 9)
                            put("partyIsFixStatus", 1)
                            put("partyAtk", 99999)
                            put("allStatusPer", 999)
                        }
                        applyLocalSaveMod(entry, mod)
                    }
                    1 -> applyLocalSaveMod(entry, GameModifierBridge.createTreasure100ActionJson())
                    2 -> {
                        val mod = JSONObject().apply {
                            put("setCardCount", 99)
                            put("cardAtkBonus", 999)
                            put("cardHpBonus", 999)
                            put("setCharStar", 5)
                            put("setCharExp", 999999)
                        }
                        applyLocalSaveMod(entry, mod)
                    }
                    3 -> {
                        val mod = JSONObject().apply {
                            put("humanCount", 350000)
                            put("humanDead", 0)
                            put("humanAlivePer", 100)
                            put("humanDeadPer", 0)
                            put("curseTurn", 0)
                            put("rareLuck", 999)
                            put("seaPlayerArmy", 99999)
                            put("seaEnemyArmy", 0)
                            put("leazasState", 0)
                            put("helmanState", 0)
                            put("zethState", 0)
                            put("freeCitiesState", 0)
                            put("leazasPArmy", 99999)
                            put("helmanPArmy", 99999)
                            put("zethPArmy", 99999)
                            put("freeCitiesPArmy", 99999)
                            put("leazasEArmy", 100)
                            put("helmanEArmy", 100)
                            put("zethEArmy", 100)
                            put("freeCitiesEArmy", 100)
                            put("subRemnant", 99999)
                            put("subDwarf", 99999)
                            put("subGeneral", 99999)
                            put("subHanny", 99999)
                        }
                        applyLocalSaveMod(entry, mod)
                    }
                    4 -> showLocalSaveResourcesDialog(entry, info)
                    5 -> showLocalSavePartyDialog(entry, info)
                    6 -> showLocalSaveWorldDialog(entry, info)
                    7 -> showLocalSaveCardsDialog(entry)
                    8 -> {
                        if (GameModifierBridge.restoreModBackup(entry.file)) {
                            refreshList()
                            Toast.makeText(this, tr("已恢复备份", "バックアップを復元しました", "Backup restored"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLocalSaveResourcesDialog(entry: ModSaveSlotEntry, info: JSONObject) {
        val fields = listOf(
            Triple("turn", tr("当前回合", "現在ターン", "Current Turn"), info.optInt("turn", 1)),
            Triple("chapter", tr("当前章节", "現在チャプター", "Chapter"), info.optInt("chapter", 0)),
            Triple("difficulty", tr("难度 0~10", "難易度 0~10", "Difficulty 0~10"), info.optInt("difficulty", 1)),
            Triple("foodTicket", tr("食券数量", "食券", "Food Tickets"), info.optInt("foodTicket", 0)),
            Triple("honor", tr("勋章数量", "勲章", "Medals"), info.optInt("honor", 0)),
            Triple("friendPoint", tr("友情点数", "友好ポイント", "Friend Points"), info.optInt("friendPoint", 0)),
            Triple("totalExp", tr("累计经验值", "累計経験値", "Total EXP"), info.optInt("totalExp", 0)),
            Triple("partyRank", tr("部队等级", "部隊ランク", "Party Rank"), info.optInt("partyRank", 1)),
            Triple("itemStar", tr("装备星级加成", "装備★加算", "Item Star Bonus"), info.optInt("itemStar", 0)),
            Triple("treasureNotGetCount", tr("稀有保底计数", "レア宝箱カウント", "Rare Pity Count"), info.optInt("treasureNotGetCount", 0)),
            Triple("hannyZippo", tr("悔棋打火机次数", "ジッポ回数", "Zippo Count"), info.optInt("hannyZippo", 0))
        )
        showNumberEditDialog("${entry.file.name} - ${tr("资源与进度", "リソース・進行", "Resources")}", fields) { mod ->
            applyLocalSaveMod(entry, mod)
        }
    }

    private fun showLocalSavePartyDialog(entry: ModSaveSlotEntry, info: JSONObject) {
        val fields = listOf(
            Triple("partyHp", tr("部队当前生命", "現在HP", "Party HP"), info.optInt("partyHp", 1000)),
            Triple("partyMaxHp", tr("部队最大生命", "最大HP", "Party Max HP"), info.optInt("partyMaxHp", 1000)),
            Triple("partyAvailCount", tr("每回合出击人数", "出撃枠数", "Deploy Slots"), info.optInt("partyAvailCount", 4)),
            Triple("partyIsFixStatus", tr("锁定属性不被重算 0/1", "ステータス固定 0/1", "Lock Stats 0/1"), info.optInt("partyIsFixStatus", 0)),
            Triple("partyAtk", tr("部队总攻击力", "総攻撃力", "Total ATK"), info.optInt("partyAtk", 500)),
            Triple("atkPerPhys", tr("物理攻击加成%", "物理攻撃%", "Phys ATK %"), info.optInt("atkPerPhys", 0)),
            Triple("atkPerMag", tr("魔法攻击加成%", "魔法攻撃%", "Mag ATK %"), info.optInt("atkPerMag", 0)),
            Triple("defPerPhys", tr("物理防御加成%", "物理防御%", "Phys DEF %"), info.optInt("defPerPhys", 0)),
            Triple("defPerMag", tr("魔法防御加成%", "魔法防御%", "Mag DEF %"), info.optInt("defPerMag", 0)),
            Triple("perCrit", tr("暴击率加成%", "クリティカル%", "Crit %"), info.optInt("perCrit", 0)),
            Triple("perMulti", tr("连击率加成%", "連撃率%", "Multi-Hit %"), info.optInt("perMulti", 0)),
            Triple("perExpUp", tr("经验获取加成%", "経験値%", "EXP Bonus %"), info.optInt("perExpUp", 0)),
            Triple("perFindTreasure", tr("宝箱掉落率%", "宝箱発見率%", "Treasure Drop %"), info.optInt("perFindTreasure", 0))
        )
        showNumberEditDialog("${entry.file.name} - ${tr("部队与属性", "部隊・ステータス", "Party & Stats")}", fields) { mod ->
            applyLocalSaveMod(entry, mod)
        }
    }

    private fun showLocalSaveWorldDialog(entry: ModSaveSlotEntry, info: JSONObject) {
        val fields = listOf(
            Triple("humanCount", tr("人类存活人口", "人類生存人口", "Human Alive"), info.optInt("humanCount", 300000)),
            Triple("humanDead", tr("人类死亡人口", "人類死亡人口", "Human Dead"), info.optInt("humanDead", 0)),
            Triple("curseTurn", tr("大诅咒回合数", "大呪いターン", "Curse Turns"), info.optInt("curseTurn", 0)),
            Triple("rareLuck", tr("稀有运加成", "レア運", "Rare Luck"), info.optInt("rareLuck", 0)),
            Triple("seaPlayerArmy", tr("兰斯船团兵力", "ランス船団兵力", "Sea Fleet Army"), info.optInt("seaPlayerArmy", 0)),
            Triple("leazasPArmy", tr("利萨斯我军兵力", "リーザス味方兵力", "Leazas Player Army"), info.optInt("leazasPArmy", 300)),
            Triple("helmanPArmy", tr("赫尔曼我军兵力", "ヘルマン味方兵力", "Helman Player Army"), info.optInt("helmanPArmy", 300)),
            Triple("zethPArmy", tr("赛斯我军兵力", "ゼス味方兵力", "Zeth Player Army"), info.optInt("zethPArmy", 300)),
            Triple("freeCitiesPArmy", tr("自由都市我军兵力", "自由都市味方兵力", "Free Cities Player Army"), info.optInt("freeCitiesPArmy", 300)),
            Triple("leazasEArmy", tr("利萨斯魔军兵力", "リーザス魔軍兵力", "Leazas Enemy Army"), info.optInt("leazasEArmy", 500)),
            Triple("helmanEArmy", tr("赫尔曼魔军兵力", "ヘルマン魔軍兵力", "Helman Enemy Army"), info.optInt("helmanEArmy", 500)),
            Triple("zethEArmy", tr("赛斯魔军兵力", "ゼス魔軍兵力", "Zeth Enemy Army"), info.optInt("zethEArmy", 500)),
            Triple("freeCitiesEArmy", tr("自由都市魔军兵力", "自由都市魔軍兵力", "Free Cities Enemy Army"), info.optInt("freeCitiesEArmy", 500))
        )
        showNumberEditDialog("${entry.file.name} - ${tr("四国与人口", "四国・人口", "World & Armies")}", fields) { mod ->
            applyLocalSaveMod(entry, mod)
        }
    }

    private fun showLocalSaveCardsDialog(entry: ModSaveSlotEntry) {
        val fields = listOf(
            Triple("setCardCount", tr("全卡牌叠卡设为N张", "全カード枚数指定", "Set All Cards Count"), 99),
            Triple("cardAtkBonus", tr("全卡牌攻击加成", "全カード攻撃加算", "All Cards ATK Bonus"), 999),
            Triple("cardHpBonus", tr("全卡牌生命加成", "全カードHP加算", "All Cards HP Bonus"), 999),
            Triple("setCharStar", tr("全角色星级 1~5", "全キャラ★ 1~5", "All Chars Star 1~5"), 5),
            Triple("setCharExp", tr("全角色经验值", "全キャラ経験値", "All Chars EXP"), 999999)
        )
        showNumberEditDialog("${entry.file.name} - ${tr("卡牌与角色", "カード・キャラ", "Cards & Chars")}", fields) { mod ->
            applyLocalSaveMod(entry, mod)
        }
    }

    private fun applyLocalSaveMod(entry: ModSaveSlotEntry, mod: JSONObject) {
        GameModifierBridge.backupSaveFileBeforeMod(entry.file)
        val ok = GameModifierBridge.nativeWriteLocalSaveMod(entry.file.absolutePath, mod.toString())
        refreshList()
        Toast.makeText(
            this,
            if (ok) tr("已修改 ${entry.file.name}", "${entry.file.name} を更新しました", "Updated ${entry.file.name}")
            else tr("修改失败", "更新失敗", "Failed"),
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================================
    // Helper for Compact Native Number Edit Dialog
    // =========================================================================
    private fun showNumberEditDialog(
        title: String,
        fields: List<Triple<String, String, Int>>,
        onConfirm: (JSONObject) -> Unit
    ) {
        val scroll = ScrollView(this)
        // The EditText underline is inset by 4dp, so 20dp at the end lines it up with the
        // labels' 24dp (the dialog title's inset).
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(24), dp(8), dp(20), dp(4))
        }
        val inputs = LinkedHashMap<String, EditText>()
        for ((key, label, defVal) in fields) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
            }
            val tv = TextView(this).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Launcher_Body)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setText(defVal.toString())
                setSingleLine(true)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                    marginStart = dp(12)
                }
            }
            inputs[key] = et
            row.addView(tv)
            row.addView(et)
            container.addView(row)
        }
        scroll.addView(container)

        dialogBuilder()
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                val mod = JSONObject()
                for ((key, et) in inputs) {
                    val v = et.text.toString().trim().toIntOrNull()
                    if (v != null) mod.put(key, v)
                }
                onConfirm(mod)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // =========================================================================
    // 5. Font Size & Scrollbar Scale Dialogs
    // =========================================================================
    private fun showFontScaleDialog() {
        val gameKey = currentUiScaleGameKey() ?: return
        showScaleSliderDialog(
            title = tr("字体大小", "文字サイズ", "Font Size"),
            description = tr(
                "仅对当前游戏生效 · 范围 75%~160%，默认 100%",
                "このゲームのみ適用 · 範囲 75%~160% · 標準 100%",
                "Current game only · Range 75%~160% · Default 100%"
            ),
            minPct = GameModifierBridge.MIN_FONT_SCALE_PCT,
            maxPct = GameModifierBridge.MAX_FONT_SCALE_PCT,
            defaultPct = GameModifierBridge.DEFAULT_FONT_SCALE_PCT,
            stepPct = 5,
            currentPct = GameModifierBridge.getFontScalePercent(this, gameKey),
            presets = listOf(80, 90, 100, 115, 130, 150),
            onApply = { pct, isReset ->
                GameModifierBridge.setFontScalePercent(this, gameKey, pct)
                refreshSummaryText()
                val msg = if (isReset) {
                    tr("已恢复默认 100%", "100% に戻しました", "Reset to 100%")
                } else {
                    tr("已设为 $pct%", "$pct% に設定しました", "Set to $pct%")
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showScrollbarScaleDialog() {
        val gameKey = currentUiScaleGameKey() ?: return
        showScaleSliderDialog(
            title = tr("滚动条", "スクロールバー", "Scrollbar"),
            description = tr(
                "仅对当前游戏生效 · 范围 100%~300%，默认 100%",
                "このゲームのみ適用 · 範囲 100%~300% · 標準 100%",
                "Current game only · Range 100%~300% · Default 100%"
            ),
            minPct = GameModifierBridge.MIN_SCROLLBAR_SCALE_PCT,
            maxPct = GameModifierBridge.MAX_SCROLLBAR_SCALE_PCT,
            defaultPct = GameModifierBridge.DEFAULT_SCROLLBAR_SCALE_PCT,
            stepPct = 10,
            currentPct = GameModifierBridge.getScrollbarScalePercent(this, gameKey),
            presets = listOf(100, 130, 160, 200, 250, 300),
            onApply = { pct, isReset ->
                GameModifierBridge.setScrollbarScalePercent(this, gameKey, pct)
                refreshSummaryText()
                val msg = if (isReset) {
                    tr("已恢复默认 100%", "100% に戻しました", "Reset to 100%")
                } else {
                    tr("已设为 $pct%", "$pct% に設定しました", "Set to $pct%")
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showScaleSliderDialog(
        title: String,
        description: String,
        minPct: Int,
        maxPct: Int,
        defaultPct: Int,
        stepPct: Int,
        currentPct: Int,
        presets: List<Int>,
        onApply: (Int, Boolean) -> Unit
    ) {
        var selectedPct = currentPct.coerceIn(minPct, maxPct)
        var updatingBar = false

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(8))
            // Lets the slider thumb overhang into the padding at both ends of the track.
            clipToPadding = false
        }

        val descTv = TextView(this).apply {
            text = description
            setTextAppearance(R.style.TextAppearance_Launcher_Caption)
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, 0, 0, dp(14))
        }
        container.addView(descTv)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val valueTv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(R.style.TextAppearance_Launcher_CardTitle)
        }
        // Negative margins cancel the padding that makes room for the thumb, so the track
        // spans the full content width.
        val seekBar = SeekBar(this).apply {
            max = maxPct - minPct
            progress = selectedPct - minPct
            setPadding(dp(8), dp(14), dp(8), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = -dp(8)
                marginEnd = -dp(8)
            }
        }

        fun updateSelected(newPct: Int, syncSeek: Boolean) {
            selectedPct = newPct.coerceIn(minPct, maxPct)
            valueTv.text = tr(
                "当前比例：$selectedPct%",
                "現在倍率：$selectedPct%",
                "Current: $selectedPct%"
            )
            if (syncSeek) {
                updatingBar = true
                seekBar.progress = selectedPct - minPct
                updatingBar = false
            }
        }

        fun stepButton(label: String, delta: Int) =
            Button(this, null, 0, R.style.Widget_Launcher_Button_Small).apply {
                text = label
                minWidth = dp(60)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                setOnClickListener { updateSelected(selectedPct + delta, true) }
            }
        val minusBtn = stepButton("-$stepPct%", -stepPct)
        val plusBtn = stepButton("+$stepPct%", stepPct)
        headerRow.addView(valueTv)
        headerRow.addView(minusBtn)
        headerRow.addView(plusBtn)
        container.addView(headerRow)
        container.addView(seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingBar) return
                updateSelected(minPct + progress, false)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        for ((rowIndex, chunk) in presets.chunked(3).withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (rowIndex > 0) dp(8) else 0 }
            }
            for ((index, preset) in chunk.withIndex()) {
                val btn = Button(this).apply {
                    text = "$preset%"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = if (index > 0) dp(8) else 0
                    }
                    setOnClickListener { updateSelected(preset, true) }
                }
                row.addView(btn)
            }
            container.addView(row)
        }

        updateSelected(selectedPct, true)
        scroll.addView(container)

        dialogBuilder()
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                onApply(selectedPct, false)
            }
            .setNeutralButton(tr("恢复默认 $defaultPct%", "標準 $defaultPct%", "Reset $defaultPct%")) { _, _ ->
                onApply(defaultPct, true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
