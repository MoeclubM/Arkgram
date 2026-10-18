package org.telegram.messenger.voip;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GroupCallInitializationTest {
    @Test
    public void createsGroupAndConferenceOnNativeAudioThreads() throws Exception {
        for (boolean conference : new boolean[]{false, true}) {
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<String> joinPayload = new AtomicReference<>();
            File log = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                    "group-call-initialization-" + conference + ".log");
            NativeInstance instance = NativeInstance.makeGroup(log.getAbsolutePath(), 0, false, true,
                    (ssrc, payload) -> {
                        joinPayload.set(payload);
                        ready.countDown();
                    },
                    (uids, levels, voice) -> {},
                    (task, ssrcs) -> {},
                    (timestamp, duration, channel, quality) -> {},
                    (timestamp, duration, channel, quality) -> {},
                    task -> {}, conference);
            try {
                instance.resetGroupInstance(true, false);
                assertTrue("Native group call did not produce a join payload; conference=" + conference,
                        ready.await(30, TimeUnit.SECONDS));
                JSONObject payload = new JSONObject(joinPayload.get());
                assertNotEquals(0, payload.getInt("ssrc"));
                assertTrue(payload.getString("ufrag").length() > 0);
                assertTrue(payload.getString("pwd").length() > 0);
            } finally {
                instance.stopGroup();
            }
        }
    }
}
