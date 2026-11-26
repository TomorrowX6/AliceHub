package io.github.kichikuou.xsystem4;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SaveScreenLoadingTest {
    @Test
    public void saveManagerLoadsFilesForScopedTarget() throws Exception {
        verifyLoad(SaveManagerActivity.class, R.id.btn_switch_game, R.id.save_file_list, R.id.save_empty_view, true);
    }

    @Test
    public void saveManagerShowsEmptyStateForEmptyTarget() throws Exception {
        verifyLoad(SaveManagerActivity.class, R.id.btn_switch_game, R.id.save_file_list, R.id.save_empty_view, false);
    }

    @Test
    public void modifierLoadsProgressSlotsForScopedTarget() throws Exception {
        verifyLoad(GameModifierActivity.class, R.id.btn_mod_switch_dir, R.id.mod_save_list, R.id.mod_empty_view, true);
    }

    @Test
    public void modifierShowsEmptyStateForEmptyTarget() throws Exception {
        verifyLoad(GameModifierActivity.class, R.id.btn_mod_switch_dir, R.id.mod_save_list, R.id.mod_empty_view, false);
    }

    @Test
    public void failedReadRunsOffMainAndReportsErrorWithoutRendering() {
        CoroutineScope scope = CoroutineScopeKt.CoroutineScope(Dispatchers.getMain());
        SaveScreenLoader loader = new SaveScreenLoader(scope);
        AtomicBoolean readOffMain = new AtomicBoolean();
        AtomicBoolean rendered = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        try {
            loader.load(() -> {
                readOffMain.set(Looper.myLooper() != Looper.getMainLooper());
                throw new IllegalStateException("Test read failure");
            }, value -> {
                rendered.set(true);
                return Unit.INSTANCE;
            }, () -> {
                failed.set(Looper.myLooper() == Looper.getMainLooper());
                return Unit.INSTANCE;
            });
            await(failed::get);
            assertTrue(readOffMain.get());
            assertFalse(rendered.get());
        } finally {
            CoroutineScopeKt.cancel(scope, null);
        }
    }

    private void verifyLoad(Class<? extends Activity> activityClass, int actionId, int listId,
                            int emptyId, boolean withSave) throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File target = new File(context.getFilesDir(), "saves/Rance10");
        assertTrue(target.mkdirs());
        if (withSave) {
            File nested = new File(target, "nested");
            assertTrue(nested.mkdir());
            File slot = new File(nested, "LocalSave103.asd");
            assertTrue(slot.createNewFile());
            if (activityClass == GameModifierActivity.class) {
                GameModifierBridge.INSTANCE.backupSaveFileBeforeMod(slot);
            }
        }
        Intent intent = new Intent(context, activityClass)
                .putExtra(SaveManagerActivity.EXTRA_GAME_ID, GameCatalog.ID_RANCE10)
                .putExtra(SaveManagerActivity.EXTRA_DIR_NAME, target.getName());
        ActivityController<? extends Activity> controller = Robolectric.buildActivity(activityClass, intent).create();
        try {
            Activity activity = controller.get();
            View action = activity.findViewById(actionId);
            assertFalse(action.isEnabled());
            controller.start().resume();
            await(action::isEnabled);
            ListView list = activity.findViewById(listId);
            assertEquals(withSave ? 1 : 0, list.getCount() - list.getHeaderViewsCount());
            assertEquals(withSave ? View.GONE : View.VISIBLE, activity.findViewById(emptyId).getVisibility());
            if (activityClass == GameModifierActivity.class && withSave) {
                ModSaveSlotEntry slot = (ModSaveSlotEntry) list.getItemAtPosition(list.getHeaderViewsCount());
                assertTrue(slot.getHasBackup());
            }
        } finally {
            controller.pause().stop().destroy();
        }
    }

    static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.yield();
        }
        assertTrue("Save screen did not finish loading", condition.getAsBoolean());
    }
}
