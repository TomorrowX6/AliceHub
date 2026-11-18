package io.github.kichikuou.xsystem4;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

import static org.junit.Assert.*;

/**
 * In-game floating overlay and pad-command paths must not open button/layout
 * editors; launcher/settings/hub editors must keep working. Headless Robolectric
 * only; not a device or emulator visual test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GamepadInGameEditRestrictionTest {

    @After
    public void tearDown() {
        GamepadConfigDialog.INSTANCE.dismissActive();
        ShadowToast.reset();
        ShadowAlertDialog.reset();
    }

    @Test
    public void outOfGameHostsCanEditButtonsAndLayout() {
        Application app = RuntimeEnvironment.getApplication();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(app));

        SettingsActivity settings = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(settings));
        Context wrappedSettings = new ContextThemeWrapper(settings, R.style.Theme_Launcher);
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(wrappedSettings));

        GameHubActivity hub = Robolectric.buildActivity(GameHubActivity.class).setup().get();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(hub));

        GamepadSettingsActivity padSettings = Robolectric.buildActivity(GamepadSettingsActivity.class).setup().get();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(padSettings));

        Intent editorIntent = GamepadEditorActivity.Companion.createIntent(
                padSettings, "restriction-test", "Restriction", GamepadEditorActivity.TAB_VIRTUAL, null);
        GamepadEditorActivity editor = Robolectric.buildActivity(GamepadEditorActivity.class, editorIntent)
                .setup().get();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(editor));
        assertFalse(editor.isFinishing());

        // A plain Activity is not a running game session.
        Activity plain = Robolectric.buildActivity(Activity.class).setup().get();
        assertTrue(GamepadBridge.INSTANCE.canEditButtonsAndLayout(plain));

        settings.finish();
        hub.finish();
        padSettings.finish();
        editor.finish();
        plain.finish();
    }

    @Test
    public void runningGameSessionCannotEditButtonsAndLayout() {
        // Avoid XSystem4Activity.onCreate (SDL/native). Policy keys off the activity type.
        XSystem4Activity game = Robolectric.buildActivity(XSystem4Activity.class).get();
        assertFalse(GamepadBridge.INSTANCE.canEditButtonsAndLayout(game));
        Context wrapped = new ContextThemeWrapper(game, R.style.Theme_Launcher);
        assertFalse(GamepadBridge.INSTANCE.canEditButtonsAndLayout(wrapped));
        assertSame(game, GamepadBridge.INSTANCE.resolveActivity(wrapped));
    }

    @Test
    public void inGameConfigDialogAndEditorLaunchersAreBlockedWithoutResettingProfiles() {
        XSystem4Activity game = Robolectric.buildActivity(XSystem4Activity.class).get();
        GamepadProfile before = GamepadBridge.INSTANCE.loadProfile(game, "restriction-test");
        assertNotNull(before);

        ShadowToast.reset();
        GamepadConfigDialog.INSTANCE.show(game, "restriction-test", null, null, null, profile -> Unit.INSTANCE, null);
        assertNull("in-game GamepadConfigDialog.show must not open a dialog",
                ShadowAlertDialog.getLatestAlertDialog());
        String toast = ShadowToast.getTextOfLatestToast();
        assertNotNull(toast);
        assertTrue(toast, toast.contains("Settings → Gamepad")
                || toast.contains("设置 → 手柄设置")
                || toast.contains("設定 → パッド設定"));

        ShadowToast.reset();
        game.openGamepadConfigDialog();
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
        assertNotNull(ShadowToast.getTextOfLatestToast());

        ShadowToast.reset();
        game.openGamepadEditorActivity(GamepadEditorActivity.TAB_VIRTUAL);
        assertNull(Shadows.shadowOf(game).getNextStartedActivity());
        assertNotNull(ShadowToast.getTextOfLatestToast());

        ShadowToast.reset();
        game.enterLayoutEditorMode();
        assertNull(Shadows.shadowOf(game).getNextStartedActivity());
        assertNotNull(ShadowToast.getTextOfLatestToast());

        GamepadProfile after = GamepadBridge.INSTANCE.loadProfile(game, "restriction-test");
        assertEquals(before.getVirtual().getEnabled(), after.getVirtual().getEnabled());
        assertEquals(before.getVirtual().getPresetId(), after.getVirtual().getPresetId());
        assertEquals(before.getVirtual().getControls().size(), after.getVirtual().getControls().size());
        assertEquals(before.getPhysical().getEnabled(), after.getPhysical().getEnabled());
        assertEquals(before.getPhysical().getBindings(), after.getPhysical().getBindings());
        assertEquals(before.getPerGameOverride(), after.getPerGameOverride());
    }

    @Test
    public void blockedMessageFollowsAppLanguage() {
        XSystem4Activity game = Robolectric.buildActivity(XSystem4Activity.class).get();

        AppSettings.INSTANCE.setLanguageMode(game, "zh");
        ShadowToast.reset();
        GamepadBridge.INSTANCE.notifyButtonsAndLayoutEditingBlocked(game);
        assertEquals("游戏进行中无法自定义按键与布局，请先退出游戏，在「设置 → 手柄设置」中修改。",
                ShadowToast.getTextOfLatestToast());

        AppSettings.INSTANCE.setLanguageMode(game, "ja");
        ShadowToast.reset();
        GamepadBridge.INSTANCE.notifyButtonsAndLayoutEditingBlocked(game);
        assertEquals("プレイ中はキー配置とレイアウトを編集できません。ゲームを終了し、「設定 → パッド設定」から変更してください。",
                ShadowToast.getTextOfLatestToast());

        AppSettings.INSTANCE.setLanguageMode(game, "en");
        ShadowToast.reset();
        GamepadBridge.INSTANCE.notifyButtonsAndLayoutEditingBlocked(game);
        assertEquals("Buttons and layout can't be customized while a game is running. Exit the game and change them in Settings → Gamepad.",
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void outOfGameConfigDialogStillOpensCustomizeKeysAndLayout() {
        GamepadSettingsActivity host = Robolectric.buildActivity(GamepadSettingsActivity.class).setup().get();
        ShadowAlertDialog.reset();
        GamepadConfigDialog.INSTANCE.show(host, "restriction-test", null, "Restriction", null,
                profile -> Unit.INSTANCE, null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("settings GamepadConfigDialog.show must still open", dialog);
        assertTrue(dialog.isShowing());
        TextView customize = findText(dialog.findViewById(android.R.id.content),
                "自定义按键与布局", "キー配置・レイアウト編集", "Customize Keys & Layout");
        assertNotNull(customize);
        customize.performClick();
        Intent launched = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(GamepadEditorActivity.class.getName(), launched.getComponent().getClassName());
        dialog.dismiss();
        host.finish();
    }

    @Test
    public void gamepadSettingsPageStillLaunchesEditor() {
        GamepadSettingsActivity host = Robolectric.buildActivity(GamepadSettingsActivity.class).setup().get();
        TextView customize = findText(host.getWindow().getDecorView(),
                "自定义按键与布局", "キー配置・レイアウト編集", "Customize Keys & Layout");
        assertNotNull(customize);
        customize.performClick();
        Intent launched = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(GamepadEditorActivity.class.getName(), launched.getComponent().getClassName());
        host.finish();
    }

    private static TextView findText(View root, String... labels) {
        List<TextView> views = new ArrayList<>();
        collectText(root, views);
        for (TextView tv : views) {
            CharSequence text = tv.getText();
            if (text == null) continue;
            String value = text.toString();
            for (String label : labels) {
                if (label.equals(value)) return tv;
            }
        }
        return null;
    }

    private static void collectText(View view, List<TextView> out) {
        if (view instanceof TextView) out.add((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), out);
            }
        }
    }
}
