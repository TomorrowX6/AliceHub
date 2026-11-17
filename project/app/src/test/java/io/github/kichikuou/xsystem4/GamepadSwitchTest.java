package io.github.kichikuou.xsystem4;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

import static org.junit.Assert.*;

/** Headless Android layout/interaction tests; not device screenshot tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GamepadSwitchTest {
    private static List<Switch> switches(View root) {
        List<Switch> found = new ArrayList<>();
        if (root instanceof Switch) found.add((Switch) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) found.addAll(switches(group.getChildAt(i)));
        }
        return found;
    }

    private static int switchStyle(Context context) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(android.R.attr.switchStyle, value, true));
        return value.resourceId;
    }

    private static GamepadConfigController controller(Context context, String key) {
        return new GamepadConfigController(context, key, null, null, Color.MAGENTA,
                false, null, null, profile -> Unit.INSTANCE);
    }

    @Test public void legacyGameHostUsesTheSameMaterialSwitchAsSettings() {
        Activity game = Robolectric.buildActivity(Activity.class).setup().get();
        game.setTheme(R.style.AppTheme);
        Context settings = new ContextThemeWrapper(game, R.style.Theme_Launcher);
        GamepadConfigController controller = controller(game, "switch-regression");
        assertEquals(switchStyle(settings), switchStyle(controller.getUiCtx()));
        assertNotEquals(switchStyle(game), switchStyle(controller.getUiCtx()));
        List<Switch> switches = switches(controller.buildRootView());
        assertTrue(switches.size() >= 3); // Includes the independent per-game switch.
        for (Switch sw : switches) {
            assertNotNull("Material thumb missing", sw.getThumbDrawable());
            assertNotNull("Material track missing", sw.getTrackDrawable());
            assertFalse(sw.getShowText());
            assertEquals(1f, sw.getScaleX(), 0f);
            assertEquals(1f, sw.getScaleY(), 0f);
        }
        game.finish();
    }

    @Test public void rowsWrapAtSmallWidthsAndToggleExactlyOnceWithAccessibleState() {
        Activity game = Robolectric.buildActivity(Activity.class).setup().get();
        game.setTheme(R.style.AppTheme);
        for (String language : new String[]{"zh", "ja", "en"}) {
            AppSettings.INSTANCE.setLanguageMode(game, language);
            for (int night : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                for (float fontScale : new float[]{1f, 2f}) {
                    Configuration config = new Configuration(game.getResources().getConfiguration());
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
                    config.fontScale = fontScale;
                    Context context = game.createConfigurationContext(config);
                    AppSettings.INSTANCE.setThemeMode(context, night == Configuration.UI_MODE_NIGHT_YES
                            ? AppSettings.THEME_DARK : AppSettings.THEME_LIGHT);
                    GamepadConfigController controller = controller(context, null);
                    View root = controller.buildRootView();
                    int dp48 = UiKit.INSTANCE.dpInt(controller.getUiCtx(), 48);
                    // Even a 320dp-wide dialog inside a landscape game must not squeeze the control.
                    int width = UiKit.INSTANCE.dpInt(controller.getUiCtx(), 320);
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(UiKit.INSTANCE.dpInt(context, 240), View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                    List<Switch> switches = switches(root);
                    assertTrue(switches.size() >= 2);
                    assertFalse(controller.isDirty()); // Initial sync is not a user change.
                    for (Switch sw : switches) {
                        View row = (View) sw.getParent();
                        assertTrue(sw.getWidth() >= dp48);
                        assertTrue(sw.getHeight() >= dp48);
                        assertTrue(row.getHeight() >= dp48);
                        assertTrue(sw.getRight() <= row.getWidth());
                        assertTrue(((ViewGroup) row).getChildAt(0).getRight() <= sw.getLeft());
                        assertFalse(sw.isClickable());
                        assertTrue(row.isClickable());
                        assertTrue(row.isFocusable());
                        AccessibilityNodeInfo info = row.createAccessibilityNodeInfo();
                        assertTrue(info.isCheckable());
                        assertEquals(Switch.class.getName(), info.getClassName());
                        assertEquals(sw.isChecked(), info.isChecked());
                        assertNotNull(info.getContentDescription());
                        boolean before = sw.isChecked();
                        row.performClick();
                        assertEquals(!before, sw.isChecked());
                        assertTrue(controller.isDirty());
                        assertEquals(sw.isChecked(), row.createAccessibilityNodeInfo().isChecked());
                        row.performClick();
                        assertEquals(before, sw.isChecked());
                    }
                }
            }
        }
        game.finish();
    }
}
