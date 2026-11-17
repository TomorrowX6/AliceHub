package io.github.kichikuou.xsystem4

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class VirtualControlKind(val id: Int) {
    BUTTON(0),
    DPAD(1),
    STICK(2);

    companion object {
        fun fromId(id: Int): VirtualControlKind =
            values().firstOrNull { it.id == id } ?: BUTTON
    }
}

enum class VirtualButtonShape(val id: Int) {
    CIRCLE(0),
    PILL(1),
    SQUARE(2);

    companion object {
        fun fromId(id: Int): VirtualButtonShape =
            values().firstOrNull { it.id == id } ?: CIRCLE
    }
}

data class GamepadActionSpec(
    val type: Int = GamepadBridge.ACTION_NONE,
    val code: Int = 0,
    val code2: Int = 0,
    val toggleMode: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("code", code)
        .put("code2", code2)
        .put("toggle", toggleMode)

    companion object {
        fun fromJson(obj: JSONObject?): GamepadActionSpec {
            if (obj == null) return GamepadActionSpec()
            return GamepadActionSpec(
                type = obj.optInt("type", GamepadBridge.ACTION_NONE),
                code = obj.optInt("code", 0),
                code2 = obj.optInt("code2", 0),
                toggleMode = obj.optBoolean("toggle", false)
            )
        }
    }
}

data class VirtualControlSpec(
    val id: String,
    val slotId: Int,
    val kind: VirtualControlKind,
    val label: String,
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val visible: Boolean = true,
    val shape: VirtualButtonShape = VirtualButtonShape.CIRCLE,
    val stickMode: Int = GamepadBridge.STICK_NONE,
    val action: GamepadActionSpec = GamepadActionSpec(),
    val isCustom: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("slotId", slotId)
        .put("kind", kind.id)
        .put("label", label)
        .put("xNorm", xNorm.toDouble())
        .put("yNorm", yNorm.toDouble())
        .put("scale", scale.toDouble())
        .put("opacity", opacity.toDouble())
        .put("visible", visible)
        .put("shape", shape.id)
        .put("stickMode", stickMode)
        .put("action", action.toJson())
        .put("isCustom", isCustom)

    companion object {
        fun fromJson(obj: JSONObject): VirtualControlSpec {
            return VirtualControlSpec(
                id = obj.optString("id", "btn"),
                slotId = obj.optInt("slotId", 0),
                kind = VirtualControlKind.fromId(obj.optInt("kind", 0)),
                label = obj.optString("label", ""),
                xNorm = obj.optDouble("xNorm", 0.5).toFloat().coerceIn(0.03f, 0.97f),
                yNorm = obj.optDouble("yNorm", 0.5).toFloat().coerceIn(0.04f, 0.96f),
                scale = obj.optDouble("scale", 1.0).toFloat().coerceIn(0.55f, 1.8f),
                opacity = obj.optDouble("opacity", 1.0).toFloat().coerceIn(0.2f, 1.0f),
                visible = obj.optBoolean("visible", true),
                shape = VirtualButtonShape.fromId(obj.optInt("shape", 0)),
                stickMode = obj.optInt("stickMode", GamepadBridge.STICK_NONE),
                action = GamepadActionSpec.fromJson(obj.optJSONObject("action")),
                isCustom = obj.optBoolean("isCustom", false)
            )
        }
    }
}

data class PhysicalPadConfig(
    val enabled: Boolean = true,
    val autoHideVirtualPad: Boolean = true,
    val swapAbXy: Boolean = false,
    val leftStickMode: Int = GamepadBridge.STICK_MOUSE,
    val rightStickMode: Int = GamepadBridge.STICK_WHEEL,
    val dpadMode: Int = GamepadBridge.STICK_ARROWS,
    val deadzonePct: Int = 18,
    val cursorSpeedPct: Int = 100,
    val rumbleEnabled: Boolean = true,
    val bindings: Map<Int, GamepadActionSpec> = GamepadBridge.defaultPhysicalBindings()
) {
    fun toJson(): JSONObject {
        val bindObj = JSONObject()
        for ((slot, spec) in bindings) {
            bindObj.put(slot.toString(), spec.toJson())
        }
        return JSONObject()
            .put("enabled", enabled)
            .put("autoHideVirtualPad", autoHideVirtualPad)
            .put("swapAbXy", swapAbXy)
            .put("leftStickMode", leftStickMode)
            .put("rightStickMode", rightStickMode)
            .put("dpadMode", dpadMode)
            .put("deadzonePct", deadzonePct.coerceIn(5, 45))
            .put("cursorSpeedPct", cursorSpeedPct.coerceIn(30, 250))
            .put("rumbleEnabled", rumbleEnabled)
            .put("bindings", bindObj)
    }

    companion object {
        fun fromJson(obj: JSONObject?): PhysicalPadConfig {
            if (obj == null) return PhysicalPadConfig()
            val defaults = GamepadBridge.defaultPhysicalBindings().toMutableMap()
            val bindObj = obj.optJSONObject("bindings")
            if (bindObj != null) {
                for (slot in 0 until GamepadBridge.NR_PAD_SLOTS) {
                    val b = bindObj.optJSONObject(slot.toString())
                    if (b != null) {
                        defaults[slot] = GamepadActionSpec.fromJson(b)
                    }
                }
            }
            return PhysicalPadConfig(
                enabled = obj.optBoolean("enabled", true),
                autoHideVirtualPad = obj.optBoolean("autoHideVirtualPad", true),
                swapAbXy = obj.optBoolean("swapAbXy", false),
                leftStickMode = obj.optInt("leftStickMode", GamepadBridge.STICK_MOUSE),
                rightStickMode = obj.optInt("rightStickMode", GamepadBridge.STICK_WHEEL),
                dpadMode = obj.optInt("dpadMode", GamepadBridge.STICK_ARROWS),
                deadzonePct = obj.optInt("deadzonePct", 18).coerceIn(5, 45),
                cursorSpeedPct = obj.optInt("cursorSpeedPct", 100).coerceIn(30, 250),
                rumbleEnabled = obj.optBoolean("rumbleEnabled", true),
                bindings = defaults
            )
        }
    }
}

data class VirtualPadConfig(
    val enabled: Boolean = true,
    val presetId: String = GamepadBridge.PRESET_VN_MOUSE,
    val globalScalePct: Int = 100,
    val globalOpacityPct: Int = 58,
    val hapticFeedback: Boolean = true,
    val gridSnap: Boolean = true,
    val controls: List<VirtualControlSpec> = GamepadBridge.buildPresetControls(GamepadBridge.PRESET_VN_MOUSE)
) {
    val leftStickMode: Int
        get() = controls.firstOrNull { it.id == "stick_l" && it.visible }?.stickMode
            ?: GamepadBridge.STICK_MOUSE

    val rightStickMode: Int
        get() = controls.firstOrNull { it.id == "stick_r" && it.visible }?.stickMode
            ?: GamepadBridge.STICK_NONE

    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (c in controls) {
            arr.put(c.toJson())
        }
        return JSONObject()
            .put("enabled", enabled)
            .put("presetId", presetId)
            .put("globalScalePct", globalScalePct.coerceIn(60, 150))
            .put("globalOpacityPct", globalOpacityPct.coerceIn(20, 100))
            .put("hapticFeedback", hapticFeedback)
            .put("gridSnap", gridSnap)
            .put("leftStickMode", leftStickMode)
            .put("rightStickMode", rightStickMode)
            .put("controls", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject?): VirtualPadConfig {
            if (obj == null) return VirtualPadConfig()
            val preset = obj.optString("presetId", GamepadBridge.PRESET_VN_MOUSE)
            val arr = obj.optJSONArray("controls")
            val parsedControls = if (arr != null && arr.length() > 0) {
                val list = ArrayList<VirtualControlSpec>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    list.add(VirtualControlSpec.fromJson(item))
                }
                list
            } else {
                GamepadBridge.buildPresetControls(preset)
            }
            return VirtualPadConfig(
                enabled = obj.optBoolean("enabled", true),
                presetId = preset,
                globalScalePct = obj.optInt("globalScalePct", 100).coerceIn(60, 150),
                globalOpacityPct = obj.optInt("globalOpacityPct", 58).coerceIn(20, 100),
                hapticFeedback = obj.optBoolean("hapticFeedback", true),
                gridSnap = obj.optBoolean("gridSnap", true),
                controls = parsedControls
            )
        }
    }
}

data class GamepadProfile(
    val version: Int = 1,
    val seq: Long = 0L,
    val perGameOverride: Boolean = false,
    val virtual: VirtualPadConfig = VirtualPadConfig(),
    val physical: PhysicalPadConfig = PhysicalPadConfig()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("seq", seq)
        .put("perGameOverride", perGameOverride)
        .put("virtual", virtual.toJson())
        .put("physical", physical.toJson())

    companion object {
        fun fromJson(obj: JSONObject?): GamepadProfile {
            if (obj == null) return GamepadProfile()
            return GamepadProfile(
                version = obj.optInt("version", 1),
                seq = obj.optLong("seq", 0L),
                perGameOverride = obj.optBoolean("perGameOverride", false),
                virtual = VirtualPadConfig.fromJson(obj.optJSONObject("virtual")),
                physical = PhysicalPadConfig.fromJson(obj.optJSONObject("physical"))
            )
        }
    }
}

data class ConnectedControllerInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val descriptor: String
)

data class KeyActionEntry(
    val labelZh: String,
    val labelJa: String,
    val labelEn: String,
    val shortBadge: String,
    val action: GamepadActionSpec
)

object GamepadBridge {
    private const val PROFILE_DIR = "gamepad_profiles"
    private const val DEFAULT_PROFILE_FILE = "default.json"

    // Action types (keep in sync with xsystem4/include/input.h)
    const val ACTION_NONE = 0
    const val ACTION_VK = 1
    const val ACTION_MOUSE_BTN = 2
    const val ACTION_MOUSE_WHEEL = 3
    const val ACTION_CTRL_TOGGLE = 4
    const val ACTION_JOYPAD_BTN = 5
    const val ACTION_COMBO = 6
    const val ACTION_EMU_CMD = 7

    // Stick / D-Pad modes (keep in sync with xsystem4/include/input.h)
    const val STICK_NONE = 0
    const val STICK_MOUSE = 1
    const val STICK_ARROWS = 2
    const val STICK_WASD = 3
    const val STICK_WHEEL = 4
    const val STICK_JOYPAD = 5

    // Emulator UI commands
    const val EMU_TOGGLE_VPAD = 1
    const val EMU_OPEN_MENU = 2
    const val EMU_TOGGLE_CURSOR = 3
    const val EMU_SCREENSHOT = 4
    const val EMU_OPEN_EDITOR = 5

    // Physical controller slot indices (keep in sync with xsystem4/include/input.h)
    const val PAD_A = 0
    const val PAD_B = 1
    const val PAD_X = 2
    const val PAD_Y = 3
    const val PAD_BACK = 4
    const val PAD_GUIDE = 5
    const val PAD_START = 6
    const val PAD_L3 = 7
    const val PAD_R3 = 8
    const val PAD_LB = 9
    const val PAD_RB = 10
    const val PAD_DPAD_UP = 11
    const val PAD_DPAD_DOWN = 12
    const val PAD_DPAD_LEFT = 13
    const val PAD_DPAD_RIGHT = 14
    const val PAD_LT = 15
    const val PAD_RT = 16
    const val NR_PAD_SLOTS = 17

    // Windows VK_* virtual key codes (keep in sync with xsystem4/include/input.h)
    const val VK_LBUTTON = 0x01
    const val VK_RBUTTON = 0x02
    const val VK_MBUTTON = 0x04
    const val VK_BACK = 0x08
    const val VK_TAB = 0x09
    const val VK_RETURN = 0x0D
    const val VK_SHIFT = 0x10
    const val VK_CONTROL = 0x11
    const val VK_MENU = 0x12
    const val VK_ESCAPE = 0x1B
    const val VK_SPACE = 0x20
    const val VK_PRIOR = 0x21
    const val VK_NEXT = 0x22
    const val VK_END = 0x23
    const val VK_HOME = 0x24
    const val VK_LEFT = 0x25
    const val VK_UP = 0x26
    const val VK_RIGHT = 0x27
    const val VK_DOWN = 0x28
    const val VK_INSERT = 0x2D
    const val VK_DELETE = 0x2E

    const val PRESET_VN_MOUSE = "vn_mouse"
    const val PRESET_RPG_DUNGEON = "rpg_dungeon"
    const val PRESET_QUICK_BAR = "quick_bar"

    const val MAX_VPAD_SLOTS = 32

    @JvmStatic
    external fun nativeSetGamepadConfigJson(json: String)

    @JvmStatic
    external fun nativeSetVirtualStick(stickId: Int, x: Float, y: Float)

    @JvmStatic
    external fun nativeSetVirtualDPad(
        stickMode: Int,
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean
    )

    @JvmStatic
    external fun nativeSetVirtualButton(
        slotId: Int,
        actionType: Int,
        code: Int,
        code2: Int,
        toggleMode: Boolean,
        pressed: Boolean
    )

    @JvmStatic
    external fun nativeClearVirtualInputs()

    @JvmStatic
    external fun nativeSetAndroidControllerKey(deviceId: Int, keycode: Int, pressed: Boolean)

    @JvmStatic
    external fun nativeSetAndroidControllerMotion(
        deviceId: Int,
        lx: Float,
        ly: Float,
        rx: Float,
        ry: Float,
        lt: Float,
        rt: Float,
        hatX: Float,
        hatY: Float
    )

    @JvmStatic
    external fun nativeGetControllerStatusJson(): String

    @JvmStatic
    external fun nativeResetGamepadCursor(visible: Boolean)

    fun tr(context: Context, zh: String, ja: String, en: String): String {
        val lang = AppSettings.resolveLocale(context).language
        return when {
            lang.startsWith("zh") -> zh
            lang.startsWith("ja") -> ja
            else -> en
        }
    }

    fun normalizeGameProfileKey(gameRootOrId: String?): String? {
        if (gameRootOrId.isNullOrBlank()) return null
        val trimmed = gameRootOrId.trim().trimEnd('/')
        val base = File(trimmed).name.ifBlank { trimmed }
        val sanitized = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return sanitized.takeIf { it.isNotBlank() }
    }

    private fun profileDir(context: Context): File =
        File(context.filesDir, PROFILE_DIR).apply { mkdirs() }

    private fun defaultProfileFile(context: Context): File =
        File(profileDir(context), DEFAULT_PROFILE_FILE)

    private fun gameProfileFile(context: Context, profileKey: String): File =
        File(profileDir(context), "game_$profileKey.json")

    private fun readProfileFile(file: File): GamepadProfile? {
        if (!file.isFile) return null
        return try {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) null else GamepadProfile.fromJson(JSONObject(raw))
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeProfileFile(file: File, profile: GamepadProfile) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(profile.toJson().toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(profile.toJson().toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun hasPerGameOverride(context: Context, profileKey: String?): Boolean {
        val key = normalizeGameProfileKey(profileKey) ?: return false
        val p = readProfileFile(gameProfileFile(context, key))
        return p != null && p.perGameOverride
    }

    fun loadProfile(context: Context, profileKey: String? = null): GamepadProfile {
        val key = normalizeGameProfileKey(profileKey)
        if (key != null) {
            val gameProfile = readProfileFile(gameProfileFile(context, key))
            if (gameProfile != null && gameProfile.perGameOverride) {
                return gameProfile
            }
        }
        return readProfileFile(defaultProfileFile(context)) ?: GamepadProfile()
    }

    fun saveProfile(
        context: Context,
        profile: GamepadProfile,
        profileKey: String? = null,
        saveAsPerGame: Boolean = false
    ): GamepadProfile {
        val nextSeq = maxOf(System.currentTimeMillis(), profile.seq + 1L)
        val stamped = profile.copy(
            seq = nextSeq,
            perGameOverride = saveAsPerGame && normalizeGameProfileKey(profileKey) != null
        )
        val key = normalizeGameProfileKey(profileKey)
        if (saveAsPerGame && key != null) {
            writeProfileFile(gameProfileFile(context, key), stamped)
        } else {
            if (key != null) {
                gameProfileFile(context, key).delete()
            }
            writeProfileFile(defaultProfileFile(context), stamped.copy(perGameOverride = false))
        }
        return stamped
    }

    fun clearPerGameOverride(context: Context, profileKey: String?) {
        val key = normalizeGameProfileKey(profileKey) ?: return
        gameProfileFile(context, key).delete()
    }

    fun syncProfileToNative(profile: GamepadProfile) {
        if (!GameModifierBridge.ensureLoaded()) return
        try {
            nativeSetGamepadConfigJson(profile.toJson().toString())
        } catch (_: Throwable) {
        }
    }

    fun loadAndSyncToNative(context: Context, profileKey: String? = null): GamepadProfile {
        val p = loadProfile(context, profileKey)
        syncProfileToNative(p)
        return p
    }

    fun defaultPhysicalBindings(): Map<Int, GamepadActionSpec> = linkedMapOf(
        PAD_A to GamepadActionSpec(ACTION_MOUSE_BTN, VK_LBUTTON),
        PAD_B to GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON),
        PAD_X to GamepadActionSpec(ACTION_VK, VK_SPACE),
        PAD_Y to GamepadActionSpec(ACTION_VK, VK_RETURN),
        PAD_LB to GamepadActionSpec(ACTION_MOUSE_WHEEL, 1),
        PAD_RB to GamepadActionSpec(ACTION_MOUSE_WHEEL, -1),
        PAD_LT to GamepadActionSpec(ACTION_VK, VK_ESCAPE),
        PAD_RT to GamepadActionSpec(ACTION_CTRL_TOGGLE, 0),
        PAD_BACK to GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_CURSOR),
        PAD_START to GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_MENU),
        PAD_GUIDE to GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_VPAD),
        PAD_L3 to GamepadActionSpec(ACTION_VK, VK_SHIFT),
        PAD_R3 to GamepadActionSpec(ACTION_MOUSE_BTN, VK_MBUTTON),
        PAD_DPAD_UP to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_DOWN to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_LEFT to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_RIGHT to GamepadActionSpec(ACTION_NONE, 0)
    )

    fun rpgPhysicalBindings(): Map<Int, GamepadActionSpec> = linkedMapOf(
        PAD_A to GamepadActionSpec(ACTION_VK, VK_RETURN),
        PAD_B to GamepadActionSpec(ACTION_VK, VK_ESCAPE),
        PAD_X to GamepadActionSpec(ACTION_VK, VK_SPACE),
        PAD_Y to GamepadActionSpec(ACTION_VK, VK_TAB),
        PAD_LB to GamepadActionSpec(ACTION_VK, VK_PRIOR),
        PAD_RB to GamepadActionSpec(ACTION_VK, VK_NEXT),
        PAD_LT to GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON),
        PAD_RT to GamepadActionSpec(ACTION_CTRL_TOGGLE, 0),
        PAD_BACK to GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_CURSOR),
        PAD_START to GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_MENU),
        PAD_GUIDE to GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_VPAD),
        PAD_L3 to GamepadActionSpec(ACTION_VK, VK_SHIFT),
        PAD_R3 to GamepadActionSpec(ACTION_MOUSE_BTN, VK_LBUTTON),
        PAD_DPAD_UP to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_DOWN to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_LEFT to GamepadActionSpec(ACTION_NONE, 0),
        PAD_DPAD_RIGHT to GamepadActionSpec(ACTION_NONE, 0)
    )

    fun buildPresetControls(presetId: String): List<VirtualControlSpec> {
        return when (presetId) {
            PRESET_RPG_DUNGEON -> listOf(
                VirtualControlSpec("dpad", 0, VirtualControlKind.DPAD, "D-PAD", 0.12f, 0.68f, 1.05f, 1.0f, true, VirtualButtonShape.SQUARE, STICK_ARROWS),
                VirtualControlSpec("stick_l", 1, VirtualControlKind.STICK, "L", 0.23f, 0.44f, 0.85f, 0.85f, false, VirtualButtonShape.CIRCLE, STICK_MOUSE),
                VirtualControlSpec("btn_l2", 2, VirtualControlKind.BUTTON, "L2", 0.07f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON)),
                VirtualControlSpec("btn_l1", 3, VirtualControlKind.BUTTON, "L1", 0.16f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, 1)),
                VirtualControlSpec("btn_r1", 4, VirtualControlKind.BUTTON, "R1", 0.84f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, -1)),
                VirtualControlSpec("btn_r2", 5, VirtualControlKind.BUTTON, "Ctrl", 0.93f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_CTRL_TOGGLE, 0)),
                VirtualControlSpec("btn_y", 6, VirtualControlKind.BUTTON, "Y", 0.87f, 0.48f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_TAB)),
                VirtualControlSpec("btn_x", 7, VirtualControlKind.BUTTON, "X", 0.80f, 0.64f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_SPACE)),
                VirtualControlSpec("btn_b", 8, VirtualControlKind.BUTTON, "B", 0.94f, 0.64f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_ESCAPE)),
                VirtualControlSpec("btn_a", 9, VirtualControlKind.BUTTON, "A", 0.87f, 0.80f, 1.02f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_RETURN)),
                VirtualControlSpec("btn_select", 10, VirtualControlKind.BUTTON, "SEL", 0.34f, 0.92f, 0.85f, 0.9f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_VK, VK_SHIFT, toggleMode = true)),
                VirtualControlSpec("btn_start", 11, VirtualControlKind.BUTTON, "MENU", 0.66f, 0.92f, 0.85f, 0.9f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_MENU))
            )
            PRESET_QUICK_BAR -> listOf(
                VirtualControlSpec("dpad", 0, VirtualControlKind.DPAD, "D-PAD", 0.11f, 0.46f, 0.90f, 1.0f, false, VirtualButtonShape.SQUARE, STICK_ARROWS),
                VirtualControlSpec("stick_l", 1, VirtualControlKind.STICK, "L", 0.13f, 0.76f, 0.95f, 1.0f, false, VirtualButtonShape.CIRCLE, STICK_MOUSE),
                VirtualControlSpec("btn_l2", 2, VirtualControlKind.BUTTON, "Esc", 0.06f, 0.88f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_VK, VK_ESCAPE)),
                VirtualControlSpec("btn_l1", 3, VirtualControlKind.BUTTON, "回看↑", 0.14f, 0.88f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, 1)),
                VirtualControlSpec("btn_r1", 4, VirtualControlKind.BUTTON, "下页↓", 0.85f, 0.88f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, -1)),
                VirtualControlSpec("btn_r2", 5, VirtualControlKind.BUTTON, "Ctrl", 0.93f, 0.88f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_CTRL_TOGGLE, 0)),
                VirtualControlSpec("btn_y", 6, VirtualControlKind.BUTTON, "隐藏", 0.93f, 0.73f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_VK, VK_SPACE)),
                VirtualControlSpec("btn_x", 7, VirtualControlKind.BUTTON, "确定", 0.93f, 0.58f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_VK, VK_RETURN)),
                VirtualControlSpec("btn_b", 8, VirtualControlKind.BUTTON, "B", 0.94f, 0.65f, 0.95f, 1.0f, false, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON)),
                VirtualControlSpec("btn_a", 9, VirtualControlKind.BUTTON, "A", 0.87f, 0.81f, 1.0f, 1.0f, false, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_LBUTTON)),
                VirtualControlSpec("btn_select", 10, VirtualControlKind.BUTTON, "布局", 0.22f, 0.88f, 0.85f, 0.9f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_EDITOR)),
                VirtualControlSpec("btn_start", 11, VirtualControlKind.BUTTON, "右键", 0.06f, 0.73f, 0.92f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON))
            )
            else -> listOf( // PRESET_VN_MOUSE
                VirtualControlSpec("dpad", 0, VirtualControlKind.DPAD, "D-PAD", 0.11f, 0.45f, 0.90f, 1.0f, true, VirtualButtonShape.SQUARE, STICK_ARROWS),
                VirtualControlSpec("stick_l", 1, VirtualControlKind.STICK, "鼠标", 0.13f, 0.77f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, STICK_MOUSE),
                VirtualControlSpec("btn_l2", 2, VirtualControlKind.BUTTON, "Esc", 0.06f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_VK, VK_ESCAPE)),
                VirtualControlSpec("btn_l1", 3, VirtualControlKind.BUTTON, "回看↑", 0.15f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, 1)),
                VirtualControlSpec("btn_r1", 4, VirtualControlKind.BUTTON, "滚轮↓", 0.85f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_MOUSE_WHEEL, -1)),
                VirtualControlSpec("btn_r2", 5, VirtualControlKind.BUTTON, "Ctrl", 0.94f, 0.22f, 0.90f, 1.0f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_CTRL_TOGGLE, 0)),
                VirtualControlSpec("btn_y", 6, VirtualControlKind.BUTTON, "Y", 0.87f, 0.49f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_RETURN)),
                VirtualControlSpec("btn_x", 7, VirtualControlKind.BUTTON, "X", 0.80f, 0.65f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_VK, VK_SPACE)),
                VirtualControlSpec("btn_b", 8, VirtualControlKind.BUTTON, "B", 0.94f, 0.65f, 0.96f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON)),
                VirtualControlSpec("btn_a", 9, VirtualControlKind.BUTTON, "A", 0.87f, 0.81f, 1.02f, 1.0f, true, VirtualButtonShape.CIRCLE, action = GamepadActionSpec(ACTION_MOUSE_BTN, VK_LBUTTON)),
                VirtualControlSpec("btn_select", 10, VirtualControlKind.BUTTON, "光标", 0.31f, 0.92f, 0.85f, 0.9f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_CURSOR)),
                VirtualControlSpec("btn_start", 11, VirtualControlKind.BUTTON, "菜单", 0.69f, 0.92f, 0.85f, 0.9f, true, VirtualButtonShape.PILL, action = GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_MENU))
            )
        }
    }

    fun nextFreeCustomSlotId(controls: List<VirtualControlSpec>): Int {
        val used = controls.map { it.slotId }.toSet()
        for (id in 12 until MAX_VPAD_SLOTS) {
            if (!used.contains(id)) return id
        }
        return -1
    }

    fun buildKeyActionCatalog(): List<Pair<String, List<KeyActionEntry>>> {
        val common = listOf(
            KeyActionEntry("鼠标左键 (点击/确定)", "左クリック", "Mouse Left Click", "LClick", GamepadActionSpec(ACTION_MOUSE_BTN, VK_LBUTTON)),
            KeyActionEntry("鼠标右键 (取消/菜单)", "右クリック", "Mouse Right Click", "RClick", GamepadActionSpec(ACTION_MOUSE_BTN, VK_RBUTTON)),
            KeyActionEntry("鼠标中键", "中クリック", "Mouse Middle Click", "MClick", GamepadActionSpec(ACTION_MOUSE_BTN, VK_MBUTTON)),
            KeyActionEntry("滚轮向上 (历史记录)", "ホイール上 (ログ)", "Wheel Up (Backlog)", "轮↑", GamepadActionSpec(ACTION_MOUSE_WHEEL, 1)),
            KeyActionEntry("滚轮向下", "ホイール下", "Wheel Down", "轮↓", GamepadActionSpec(ACTION_MOUSE_WHEEL, -1)),
            KeyActionEntry("Ctrl 快进 (点按锁定开关)", "Ctrl 早送り切替", "Ctrl Skip (Toggle)", "Ctrl", GamepadActionSpec(ACTION_CTRL_TOGGLE, 0)),
            KeyActionEntry("按住 Ctrl", "Ctrl ホールド", "Hold Ctrl", "Ctrl", GamepadActionSpec(ACTION_VK, VK_CONTROL)),
            KeyActionEntry("Enter 回车", "Enter", "Enter", "Enter", GamepadActionSpec(ACTION_VK, VK_RETURN)),
            KeyActionEntry("Esc 退出/系统菜单", "Esc", "Escape", "Esc", GamepadActionSpec(ACTION_VK, VK_ESCAPE)),
            KeyActionEntry("Space 空格 (隐藏对话框)", "Space", "Space", "Space", GamepadActionSpec(ACTION_VK, VK_SPACE)),
            KeyActionEntry("Shift", "Shift", "Shift", "Shift", GamepadActionSpec(ACTION_VK, VK_SHIFT)),
            KeyActionEntry("Tab", "Tab", "Tab", "Tab", GamepadActionSpec(ACTION_VK, VK_TAB)),
            KeyActionEntry("Alt", "Alt", "Alt", "Alt", GamepadActionSpec(ACTION_VK, VK_MENU)),
            KeyActionEntry("Backspace 退格", "Backspace", "Backspace", "Bksp", GamepadActionSpec(ACTION_VK, VK_BACK))
        )

        val nav = listOf(
            KeyActionEntry("方向键 上 ↑", "上矢印 ↑", "Arrow Up", "↑", GamepadActionSpec(ACTION_VK, VK_UP)),
            KeyActionEntry("方向键 下 ↓", "下矢印 ↓", "Arrow Down", "↓", GamepadActionSpec(ACTION_VK, VK_DOWN)),
            KeyActionEntry("方向键 左 ←", "左矢印 ←", "Arrow Left", "←", GamepadActionSpec(ACTION_VK, VK_LEFT)),
            KeyActionEntry("方向键 右 →", "右矢印 →", "Arrow Right", "→", GamepadActionSpec(ACTION_VK, VK_RIGHT)),
            KeyActionEntry("PageUp 上一页", "PageUp", "PageUp", "PgUp", GamepadActionSpec(ACTION_VK, VK_PRIOR)),
            KeyActionEntry("PageDown 下一页", "PageDown", "PageDown", "PgDn", GamepadActionSpec(ACTION_VK, VK_NEXT)),
            KeyActionEntry("Home", "Home", "Home", "Home", GamepadActionSpec(ACTION_VK, VK_HOME)),
            KeyActionEntry("End", "End", "End", "End", GamepadActionSpec(ACTION_VK, VK_END)),
            KeyActionEntry("Insert", "Insert", "Insert", "Ins", GamepadActionSpec(ACTION_VK, VK_INSERT)),
            KeyActionEntry("Delete", "Delete", "Delete", "Del", GamepadActionSpec(ACTION_VK, VK_DELETE))
        )

        val fkeys = (1..12).map { n ->
            KeyActionEntry("F$n", "F$n", "F$n", "F$n", GamepadActionSpec(ACTION_VK, 0x6F + n))
        }

        val lettersAndNums = ArrayList<KeyActionEntry>().apply {
            for (c in 'A'..'Z') {
                add(KeyActionEntry("按键 $c", "キー $c", "Key $c", c.toString(), GamepadActionSpec(ACTION_VK, c.code)))
            }
            for (d in '0'..'9') {
                add(KeyActionEntry("数字 $d", "数字 $d", "Digit $d", d.toString(), GamepadActionSpec(ACTION_VK, d.code)))
            }
        }

        val emuAndCombos = listOf(
            KeyActionEntry("显示 / 隐藏虚拟手柄", "仮想パッド表示切替", "Toggle Virtual Gamepad", "手柄", GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_VPAD)),
            KeyActionEntry("打开修改器与设置菜单", "メニューを開く", "Open Modifier Menu", "菜单", GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_MENU)),
            KeyActionEntry("打开屏幕布局编辑器", "レイアウト編集", "Edit Touch Layout", "布局", GamepadActionSpec(ACTION_EMU_CMD, EMU_OPEN_EDITOR)),
            KeyActionEntry("显示 / 隐藏摇杆光标指针", "カーソル表示切替", "Toggle Stick Cursor", "光标", GamepadActionSpec(ACTION_EMU_CMD, EMU_TOGGLE_CURSOR)),
            KeyActionEntry("立即截图 (Screenshot)", "スクリーンショット", "Save Screenshot", "截图", GamepadActionSpec(ACTION_EMU_CMD, EMU_SCREENSHOT)),
            KeyActionEntry("组合键 Ctrl + S", "Ctrl + S", "Combo Ctrl + S", "C+S", GamepadActionSpec(ACTION_COMBO, 'S'.code, VK_CONTROL)),
            KeyActionEntry("组合键 Ctrl + L", "Ctrl + L", "Combo Ctrl + L", "C+L", GamepadActionSpec(ACTION_COMBO, 'L'.code, VK_CONTROL)),
            KeyActionEntry("组合键 Shift + Enter", "Shift + Enter", "Combo Shift + Enter", "S+Ent", GamepadActionSpec(ACTION_COMBO, VK_RETURN, VK_SHIFT)),
            KeyActionEntry("原生手柄 A (SACT Joypad)", "Joypad A", "Native Joypad A", "JA", GamepadActionSpec(ACTION_JOYPAD_BTN, PAD_A)),
            KeyActionEntry("原生手柄 B (SACT Joypad)", "Joypad B", "Native Joypad B", "JB", GamepadActionSpec(ACTION_JOYPAD_BTN, PAD_B)),
            KeyActionEntry("无动作 (禁用)", "なし (無効)", "None (Unbound)", "无", GamepadActionSpec(ACTION_NONE, 0))
        )

        return listOf(
            "鼠键与常用" to common,
            "方向与翻页" to nav,
            "功能键 F1-F12" to fkeys,
            "字母与数字" to lettersAndNums,
            "模拟器与组合键" to emuAndCombos
        )
    }

    fun formatActionLabel(context: Context, spec: GamepadActionSpec): String {
        val base = when (spec.type) {
            ACTION_NONE -> tr(context, "未绑定", "未設定", "Unbound")
            ACTION_MOUSE_BTN -> when (spec.code) {
                VK_RBUTTON -> tr(context, "鼠标右键", "右クリック", "Right Click")
                VK_MBUTTON -> tr(context, "鼠标中键", "中クリック", "Middle Click")
                else -> tr(context, "鼠标左键", "左クリック", "Left Click")
            }
            ACTION_MOUSE_WHEEL -> if (spec.code >= 0) {
                tr(context, "滚轮上 (回看)", "ホイール上", "Wheel Up")
            } else {
                tr(context, "滚轮下", "ホイール下", "Wheel Down")
            }
            ACTION_CTRL_TOGGLE -> tr(context, "Ctrl 快进开关", "Ctrl 早送り切替", "Ctrl Skip Toggle")
            ACTION_JOYPAD_BTN -> "Joypad #${spec.code}"
            ACTION_COMBO -> "${vkName(spec.code2)} + ${vkName(spec.code)}"
            ACTION_EMU_CMD -> when (spec.code) {
                EMU_TOGGLE_VPAD -> tr(context, "显示/隐藏虚拟手柄", "仮想パッド切替", "Toggle Virtual Pad")
                EMU_OPEN_MENU -> tr(context, "修改器菜单", "メニュー", "Modifier Menu")
                EMU_TOGGLE_CURSOR -> tr(context, "切换光标指针", "カーソル切替", "Toggle Cursor")
                EMU_SCREENSHOT -> tr(context, "截图", "スクショ", "Screenshot")
                EMU_OPEN_EDITOR -> tr(context, "布局编辑器", "レイアウト編集", "Layout Editor")
                else -> "Emu#${spec.code}"
            }
            ACTION_VK -> vkName(spec.code)
            else -> tr(context, "未知", "不明", "Unknown")
        }
        return if (spec.toggleMode && spec.type in listOf(ACTION_VK, ACTION_MOUSE_BTN, ACTION_COMBO)) {
            "$base (${tr(context, "开关", "トグル", "Toggle")})"
        } else {
            base
        }
    }

    fun vkName(vk: Int): String = when (vk) {
        VK_LBUTTON -> "LClick"
        VK_RBUTTON -> "RClick"
        VK_MBUTTON -> "MClick"
        VK_BACK -> "Backspace"
        VK_TAB -> "Tab"
        VK_RETURN -> "Enter"
        VK_SHIFT -> "Shift"
        VK_CONTROL -> "Ctrl"
        VK_MENU -> "Alt"
        VK_ESCAPE -> "Esc"
        VK_SPACE -> "Space"
        VK_PRIOR -> "PageUp"
        VK_NEXT -> "PageDown"
        VK_END -> "End"
        VK_HOME -> "Home"
        VK_LEFT -> "← Left"
        VK_UP -> "↑ Up"
        VK_RIGHT -> "→ Right"
        VK_DOWN -> "↓ Down"
        VK_INSERT -> "Insert"
        VK_DELETE -> "Delete"
        in '0'.code..'9'.code -> vk.toChar().toString()
        in 'A'.code..'Z'.code -> vk.toChar().toString()
        in 0x70..0x7B -> "F${vk - 0x6F}"
        else -> String.format(Locale.US, "VK(0x%02X)", vk)
    }

    fun stickModeLabel(context: Context, mode: Int): String = when (mode) {
        STICK_MOUSE -> tr(context, "控制鼠标光标", "マウスカーソル移動", "Mouse Cursor")
        STICK_ARROWS -> tr(context, "方向键 ↑↓←→", "方向キー", "Arrow Keys")
        STICK_WASD -> "WASD"
        STICK_WHEEL -> tr(context, "滚轮上下翻页", "ホイール上下", "Mouse Wheel")
        STICK_JOYPAD -> tr(context, "原生手柄摇杆", "純正ジョイパッド", "Native Joypad")
        else -> tr(context, "禁用", "無効", "Disabled")
    }

    fun physicalSlotName(context: Context, slot: Int): String = when (slot) {
        PAD_A -> "A (Cross / ✕)"
        PAD_B -> "B (Circle / ○)"
        PAD_X -> "X (Square / □)"
        PAD_Y -> "Y (Triangle / △)"
        PAD_LB -> "L1 / LB"
        PAD_RB -> "R1 / RB"
        PAD_LT -> "L2 / LT"
        PAD_RT -> "R2 / RT"
        PAD_BACK -> "Select / Back"
        PAD_START -> "Start / Options"
        PAD_GUIDE -> "Home / Guide"
        PAD_L3 -> "L3 (Stick Click)"
        PAD_R3 -> "R3 (Stick Click)"
        PAD_DPAD_UP -> tr(context, "十字键 ↑", "十字キー ↑", "D-Pad Up")
        PAD_DPAD_DOWN -> tr(context, "十字键 ↓", "十字キー ↓", "D-Pad Down")
        PAD_DPAD_LEFT -> tr(context, "十字键 ←", "十字キー ←", "D-Pad Left")
        PAD_DPAD_RIGHT -> tr(context, "十字键 →", "十字キー →", "D-Pad Right")
        else -> "Btn#$slot"
    }

    fun isPhysicalGamepadDevice(deviceId: Int): Boolean {
        if (deviceId < 0) return false
        val dev = InputDevice.getDevice(deviceId) ?: return false
        if (dev.isVirtual) return false
        val src = dev.sources
        val isGamepad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        val isDpad = (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (!isGamepad && !isJoystick && !isDpad) return false
        val name = dev.name.orEmpty().lowercase(Locale.ROOT)
        if (name.contains("accelerometer") || name.contains("sensor") || name.contains("fingerprint") || name.contains("gpio")) {
            return false
        }
        return true
    }

    fun isGamepadKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isPadKey = KeyEvent.isGamepadButton(code) ||
            code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_CENTER
        val fromPadSrc = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (fromPadSrc || isPadKey) {
            return isPhysicalGamepadDevice(event.deviceId) || KeyEvent.isGamepadButton(code)
        }
        return false
    }

    fun queryConnectedControllers(context: Context): List<ConnectedControllerInfo> {
        val out = ArrayList<ConnectedControllerInfo>()
        try {
            val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return out
            for (id in im.inputDeviceIds) {
                if (!isPhysicalGamepadDevice(id)) continue
                val dev = InputDevice.getDevice(id) ?: continue
                // Only count devices that actually have joystick motion ranges or gamepad capability
                val hasGamepad = (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                val hasJoystick = (dev.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (!hasGamepad && !hasJoystick) continue
                out.add(
                    ConnectedControllerInfo(
                        deviceId = id,
                        name = dev.name ?: "Game Controller",
                        vendorId = dev.vendorId,
                        productId = dev.productId,
                        descriptor = dev.descriptor ?: ""
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun androidKeyCodeToPadSlot(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_DPAD_CENTER -> PAD_A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_2 -> PAD_B
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_3 -> PAD_X
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_4 -> PAD_Y
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_5 -> PAD_LB
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_6 -> PAD_RB
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_7 -> PAD_LT
        KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_8 -> PAD_RT
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_9 -> PAD_BACK
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_10 -> PAD_START
        KeyEvent.KEYCODE_BUTTON_MODE -> PAD_GUIDE
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_11 -> PAD_L3
        KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_12 -> PAD_R3
        KeyEvent.KEYCODE_DPAD_UP -> PAD_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> PAD_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> PAD_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> PAD_DPAD_RIGHT
        else -> null
    }

    fun extractMotionAxes(ev: MotionEvent): FloatArray? {
        if ((ev.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return null
        if (ev.action != MotionEvent.ACTION_MOVE) return null
        if (!isPhysicalGamepadDevice(ev.deviceId)) return null
        val dev = ev.device ?: InputDevice.getDevice(ev.deviceId)
        val lx = ev.getAxisValue(MotionEvent.AXIS_X)
        val ly = ev.getAxisValue(MotionEvent.AXIS_Y)
        val hasZ = dev?.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) != null
        val hasRx = dev?.getMotionRange(MotionEvent.AXIS_RX, InputDevice.SOURCE_JOYSTICK) != null
        val rx = if (hasZ || !hasRx) ev.getAxisValue(MotionEvent.AXIS_Z) else ev.getAxisValue(MotionEvent.AXIS_RX)
        val hasRz = dev?.getMotionRange(MotionEvent.AXIS_RZ, InputDevice.SOURCE_JOYSTICK) != null
        val hasRy = dev?.getMotionRange(MotionEvent.AXIS_RY, InputDevice.SOURCE_JOYSTICK) != null
        val ry = if (hasRz || !hasRy) ev.getAxisValue(MotionEvent.AXIS_RZ) else ev.getAxisValue(MotionEvent.AXIS_RY)
        val lt = maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        val hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        return floatArrayOf(lx, ly, rx, ry, lt, rt, hatX, hatY)
    }
}
