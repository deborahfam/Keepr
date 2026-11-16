package com.keepr.app.data;

import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.keepr.app.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsTest {
    @Test public void settingsOnlyOffersNewerApkAndRetainsResultAcrossRotation() {
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity->{
                assertEquals(View.GONE,activity.findViewById(R.id.download_update).getVisibility());
                Object job=field(activity,"job");
                set(job,"release",new AppUpdates.Release("99.0.0",AppUpdates.RELEASES,"https://github.com/deborahfam/Keepr/releases/download/v99.0.0/Keepr-99.0.0.apk",3000000));
                set(job,"message","An update is available."); render(activity);
                assertEquals(View.VISIBLE,activity.findViewById(R.id.download_update).getVisibility());
            });
            scenario.recreate();
            scenario.onActivity(activity->{
                assertEquals(View.VISIBLE,activity.findViewById(R.id.download_update).getVisibility());
                assertTrue(((TextView)activity.findViewById(R.id.download_update)).getText().toString().contains("99.0.0"));
                Object job=field(activity,"job");
                set(job,"release",new AppUpdates.Release("0.1.0",AppUpdates.RELEASES,"https://github.com/deborahfam/Keepr/releases/download/v0.1.0/Keepr-0.1.0.apk",3000000)); render(activity);
                assertEquals(View.GONE,activity.findViewById(R.id.download_update).getVisibility());
                set(job,"release",new AppUpdates.Release("99.0.0",AppUpdates.RELEASES,"",0)); render(activity);
                assertEquals(View.GONE,activity.findViewById(R.id.download_update).getVisibility());
            });
        }
    }
    private Object field(Object object,String name) { try { var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); } catch(Exception error) { throw new AssertionError(error); } }
    private void set(Object object,String name,Object value) { try { var field=object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value); } catch(Exception error) { throw new AssertionError(error); } }
    private void render(SettingsActivity activity) { try { var method=SettingsActivity.class.getDeclaredMethod("render"); method.setAccessible(true); method.invoke(activity); } catch(Exception error) { throw new AssertionError(error); } }
}
