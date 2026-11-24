package org.libsdl.app;

import android.view.View;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PartsTextInputConnectionTest {
    public static class TestActivity extends SDLActivity {
        int session = 1;
        final List<String> events = new ArrayList<>();

        @Override
        protected int getPartsTextInputSession() {
            return session;
        }

        @Override
        protected void sendPartsTextInput(int session, byte[] text, boolean composing, int backspaces) {
            events.add(session + ":" + (text == null ? "back:" + backspaces
                    : (composing ? "edit:" : "commit:") + new String(text, StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void replacingDraftDoesNotSendBackspacesForUncommittedText() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).get();
        SDLInputConnection connection = new SDLInputConnection(new View(activity), true);
        assertTrue(connection.commitText("A", 1));
        assertTrue(connection.setComposingText("kana", 1));
        assertTrue(connection.commitText("仮名", 1));
        assertTrue(connection.deleteSurroundingText(1, 0));
        assertEquals(Arrays.asList("1:commit:A", "1:edit:kana", "1:commit:仮名", "1:back:1"),
                activity.events);
    }

    @Test
    public void finishCommitsTheDraftButCannotCarryItAcrossSessions() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).get();
        SDLInputConnection connection = new SDLInputConnection(new View(activity), true);
        assertTrue(connection.setComposingText("one", 1));
        assertTrue(connection.finishComposingText());
        assertTrue(connection.setComposingText("old", 1));
        activity.session++;
        assertTrue(connection.finishComposingText());
        assertTrue(connection.commitText("new", 1));
        assertEquals(Arrays.asList("1:edit:one", "1:commit:one", "1:edit:old", "2:commit:new"),
                activity.events);
    }
}
