package com.keepr.app.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrivacyReleaseTest {
    @Test public void cleartextAndAutomaticBackupAreDisabled() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"));
        assertEquals(0,app.getApplicationInfo().flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
    }
    @Test public void completedMigrationRemovesOldPrivateCopy() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=app.getSharedPreferences("keepr",Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean("sqlite_migrated",true).putString("links","private old text").commit());
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(app)) { repository.initialize(); }
        assertFalse(prefs.contains("links"));
    }
}
