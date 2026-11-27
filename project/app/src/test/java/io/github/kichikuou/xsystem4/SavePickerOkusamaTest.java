package io.github.kichikuou.xsystem4;

import android.app.Activity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SavePickerOkusamaTest {
    private static void seedFiles(File dir, String... fileNames) throws Exception {
        assertTrue(dir.isDirectory() || dir.mkdirs());
        for (String name : fileNames) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                out.write(1);
            }
        }
    }

    private static Set<String> dirNames(SaveTargets targets) {
        Set<String> names = new HashSet<>();
        for (SaveTarget t : targets.getAll()) {
            names.add(t.getDirName());
        }
        return names;
    }

    @Test
    public void neutralOkusamaBackupVisibleInBothEditionPickersOnly() throws Exception {
        try (ActivityController<Activity> controller =
                     Robolectric.buildActivity(Activity.class).create()) {
            Activity activity = controller.get();

            seedFiles(
                    AppStorage.INSTANCE.saveDir(activity, "OkusamaNtr"),
                    "MemModeInfo.asd", "CommonGameData.asd", "AFInfo.asd"
            );
            seedFiles(
                    AppStorage.INSTANCE.saveDir(activity, "OkusamaJun"),
                    "MemModeInfo.asd", "CommonGameData.asd", "AFInfo.asd"
            );
            seedFiles(
                    AppStorage.INSTANCE.saveDir(activity, "WeekendBackup"),
                    "MemModeInfo.asd", "CommonGameData.asd", "AFInfo.asd"
            );

            Set<String> ntrDirs = dirNames(SaveScreenLoaderKt.discoverSaveTargets(
                    activity, GameCatalog.ID_OKUSAMANTR, "OkusamaNtr", true
            ));
            assertTrue("NTR picker must include preferred OkusamaNtr", ntrDirs.contains("OkusamaNtr"));
            assertTrue("NTR picker must include non-preferred neutrally named WeekendBackup", ntrDirs.contains("WeekendBackup"));
            assertFalse("NTR picker must exclude explicit OkusamaJun", ntrDirs.contains("OkusamaJun"));

            Set<String> junDirs = dirNames(SaveScreenLoaderKt.discoverSaveTargets(
                    activity, GameCatalog.ID_OKUSAMAJUN, "OkusamaJun", true
            ));
            assertTrue("Jun picker must include preferred OkusamaJun", junDirs.contains("OkusamaJun"));
            assertTrue("Jun picker must include non-preferred neutrally named WeekendBackup", junDirs.contains("WeekendBackup"));
            assertFalse("Jun picker must exclude explicit OkusamaNtr", junDirs.contains("OkusamaNtr"));

            Set<String> dohnaDirs = dirNames(SaveScreenLoaderKt.discoverSaveTargets(
                    activity, GameCatalog.ID_DOHNA_DOHNA, "DohnaDohna", true
            ));
            assertFalse("Dohna Dohna picker must not claim Okusama WeekendBackup", dohnaDirs.contains("WeekendBackup"));
        }
    }
}
