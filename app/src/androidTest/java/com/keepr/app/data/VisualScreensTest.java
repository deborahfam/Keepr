package com.keepr.app.data;

import android.app.Activity;
import android.graphics.Bitmap;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.*;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertNotNull;

/** Opt-in screenshots, on a disposable emulator: -e visualScreens true. */
public class VisualScreensTest {
    @Test public void captureSecondaryScreens() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("visualScreens")));
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        File output=new File(context.getExternalFilesDir(null),"visual-review");
        output.mkdirs();
        Class<?>[] screens={MainActivity.class,CategoriesActivity.class,BackupActivity.class,TrashActivity.class,SettingsActivity.class,ImportPostsActivity.class};
        for(Class<?> screen:screens) {
            try(ActivityScenario<Activity> scenario=ActivityScenario.launch(new Intent(context,screen))) {
                instrumentation.waitForIdleSync();
                android.os.SystemClock.sleep(800);
                Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(screenshot);
                try(var stream=new FileOutputStream(new File(output,screen.getSimpleName()+".png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG,100,stream);
                } finally { screenshot.recycle(); }
            }
        }
    }
}
