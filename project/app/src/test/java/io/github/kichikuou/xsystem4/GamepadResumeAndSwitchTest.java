package io.github.kichikuou.xsystem4;

import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GamepadResumeAndSwitchTest {

    private static List<Switch> collectSwitches(View root) {
        List<Switch> out = new ArrayList<>();
        collectSwitchesInto(root, out);
        return out;
    }

    private static void collectSwitchesInto(View view, List<Switch> out) {
        if (view instanceof Switch) {
            out.add((Switch) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectSwitchesInto(vg.getChildAt(i), out);
            }
        }
    }

    @Test public void uiKitTintSwitchAndKeyPickerDisableShowText() {
        GamepadSettingsActivity host = Robolectric.buildActivity(GamepadSettingsActivity.class).create().get();
        Switch sw = new Switch(host);
        sw.setShowText(true);
        UiKit.INSTANCE.tintSwitch(sw, 0xFF336699);
        assertFalse(sw.getShowText());

        KeyPickerDialog.INSTANCE.showActionPicker(
                host,
                "Action",
                new GamepadActionSpec(0, 0, 0, false),
                true,
                0xFF336699,
                spec -> Unit.INSTANCE
        );
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        List<Switch> pickerSwitches = collectSwitches(dialog.getWindow().getDecorView());
        assertFalse(pickerSwitches.isEmpty());
        for (Switch s : pickerSwitches) {
            assertFalse("KeyPickerDialog switch leaked showText=true", s.getShowText());
        }
        dialog.dismiss();
    }

    @Test public void scopeCardUsesFullRowTargetAndEditorSwitchesDisableShowText() {
        Intent settingsIntent = new Intent(
                RuntimeEnvironment.getApplication(),
                GamepadSettingsActivity.class
        ).putExtra(GamepadSettingsActivity.EXTRA_PROFILE_KEY, "test-game");
        GamepadSettingsActivity settings = Robolectric.buildActivity(GamepadSettingsActivity.class, settingsIntent)
                .create().get();
        List<Switch> settingsSwitches = collectSwitches(settings.getWindow().getDecorView());
        assertFalse(settingsSwitches.isEmpty());
        for (Switch s : settingsSwitches) {
            assertFalse("GamepadSettings switch leaked showText=true", s.getShowText());
        }
        Switch scopeSwitch = settingsSwitches.get(0);
        View row = (View) scopeSwitch.getParent();
        assertTrue(row.isClickable());
        assertTrue(row.isFocusable());
        assertTrue(row.getMinimumHeight() > 0);
        assertFalse(scopeSwitch.isClickable());
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        row.onInitializeAccessibilityNodeInfo(info);
        assertEquals(Switch.class.getName(), info.getClassName());
        assertTrue(info.isCheckable());
        boolean initial = scopeSwitch.isChecked();
        row.performClick();
        assertEquals(!initial, scopeSwitch.isChecked());

        Intent editorIntent = GamepadEditorActivity.Companion.createIntent(
                settings, "test-game", "Test", 0, "test-game"
        );
        GamepadEditorActivity editor = Robolectric.buildActivity(GamepadEditorActivity.class, editorIntent)
                .create().get();
        List<Switch> editorSwitches = collectSwitches(editor.getWindow().getDecorView());
        assertFalse(editorSwitches.isEmpty());
        for (Switch s : editorSwitches) {
            assertFalse("GamepadEditorActivity switch leaked showText=true", s.getShowText());
        }
    }
}
