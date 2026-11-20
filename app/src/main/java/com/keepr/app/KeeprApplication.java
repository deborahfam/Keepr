package com.keepr.app;

import android.app.Application;
import java.io.File;

/** Remove plaintext restore staging left by an interrupted prior process. */
public final class KeeprApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        File[] cached=getCacheDir().listFiles();
        if(cached!=null) for(File file:cached) {
            if(file.isFile() && file.getName().startsWith("decrypted-backup-")) file.delete();
        }
    }
}
