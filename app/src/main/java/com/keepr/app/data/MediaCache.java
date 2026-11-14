package com.keepr.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.keepr.app.domain.CachedMedia;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;

/** Bounded, app-private image snapshots. No broad storage permission is needed. */
public final class MediaCache {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final Context context;
    public MediaCache(Context context) { this.context = context.getApplicationContext(); }
    public CachedMedia copyShared(String itemId, Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme())) throw new IOException("Only shared content images are accepted");
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Shared photo is unavailable");
            return store(itemId, uri.toString(), readBounded(stream, MAX_BYTES));
        }
    }
    static byte[] readBounded(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int read;
        while ((read = stream.read(block, 0, Math.min(block.length, limit + 1 - output.size()))) > 0) {
            output.write(block, 0, read);
            if (output.size() > limit) throw new IOException("Photo exceeds 8 MiB");
        }
        return output.toByteArray();
    }
    public CachedMedia store(String itemId, String source, byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || (long)options.outWidth * options.outHeight > 100_000_000L)
            throw new IOException("Unsupported photo dimensions");
        options.inJustDecodeBounds = false; options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Could not decode photo");
        String id = UUID.randomUUID().toString(); String relative = "media/" + id + ".jpg";
        File directory = new File(context.getFilesDir(), "media");
        if (!directory.isDirectory() && !directory.mkdirs()) { bitmap.recycle(); throw new IOException("Could not create photo storage"); }
        File file = new File(context.getFilesDir(), relative);
        try {
            try (FileOutputStream stream = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) throw new IOException("Could not save photo");
                stream.getFD().sync();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = new FileInputStream(file)) {
                byte[] block = new byte[8192]; int count;
                while ((count = stream.read(block)) != -1) digest.update(block, 0, count);
            }
            StringBuilder hash = new StringBuilder(); for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return new CachedMedia(id, itemId, source, relative, "image/jpeg", file.length(), hash.toString(), System.currentTimeMillis());
        } catch (Exception error) {
            file.delete(); throw new IOException("Could not save photo", error);
        } finally { bitmap.recycle(); }
    }
}
