package com.keepr.app;

import android.net.Uri;
import androidx.core.content.FileProvider;
import com.keepr.app.data.PostShare;

/** Preserve the Keepr MIME type when another app inspects a granted sharing URI. */
public final class SharedPostsProvider extends FileProvider {
    @Override public String getType(Uri uri) { super.getType(uri); return PostShare.MIME; }
}
