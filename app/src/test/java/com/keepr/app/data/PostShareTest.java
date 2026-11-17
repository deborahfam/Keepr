package com.keepr.app.data;

import com.keepr.app.domain.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class PostShareTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private SavedItem item() { return new SavedItem("private-id","https://example.com/post","Apartment","Caption with phone","Private site","https://example.com/private-image","1200","USD","legacy-contact","Private notes","Private folder",SavedItem.Status.REACHED_OUT,SavedItem.PreviewState.READY,"private-error",1,1,"Private raw text",List.of("private-tag"),List.of(),0,0,0,"San Juan","Home","month","private-source",new ContactDetails("Ana","+17875551234","ana@example.com","+17875551234","private-source")); }
    private byte[] export(PostShare.Options options) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); PostShare.write(List.of(item()),options,temp.getRoot(),out); return out.toByteArray();
    }
    private JSONObject manifest(byte[] archive) throws Exception { try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) { zip.getNextEntry(); return new JSONObject(new String(zip.readAllBytes(),StandardCharsets.UTF_8)); } }
    private byte[] archive(JSONObject manifest,String extra,byte[] bytes) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); try(ZipOutputStream zip=new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            if(extra!=null) { zip.putNextEntry(new ZipEntry(extra)); zip.write(bytes); zip.closeEntry(); }
        } return out.toByteArray();
    }
    private void rejects(byte[] data) throws Exception {
        int before=Objects.requireNonNull(temp.getRoot().list()).length;
        try { PostShare.Preview preview=PostShare.read(new ByteArrayInputStream(data),temp.getRoot()); preview.close(); fail("Accepted invalid archive"); } catch(IOException | JSONException | IllegalArgumentException expected) { }
        assertEquals(before,Objects.requireNonNull(temp.getRoot().list()).length);
    }
    @Test public void defaultsExcludePrivateDataAndCaption() throws Exception {
        PostShare.Options options=new PostShare.Options(false,false,false); byte[] archive=export(options); String json=manifest(archive).toString();
        for(String privateValue:List.of("private-","Private","legacy-contact","ana@example.com","17875551234","Caption","REACHED_OUT")) assertFalse(json.contains(privateValue));
        String summary=PostShare.summary(List.of(item()),options); assertTrue(summary.contains("1200")); assertFalse(summary.contains("Private")); assertFalse(summary.contains("Ana"));
        try(PostShare.Preview preview=PostShare.read(new ByteArrayInputStream(archive),temp.getRoot())) {
            assertEquals("Apartment",preview.posts().get(0).title()); assertTrue(preview.posts().get(0).contacts().isEmpty()); assertEquals("month",preview.posts().get(0).period());
        }
    }
    @Test public void explicitOptionsIncludeReviewedContent() throws Exception {
        PostShare.Options options=new PostShare.Options(true,true,true);
        try(PostShare.Preview preview=PostShare.read(new ByteArrayInputStream(export(options)),temp.getRoot())) {
            assertEquals("Private notes",preview.posts().get(0).notes()); assertEquals("ana@example.com",preview.posts().get(0).contacts().email()); assertEquals("Caption with phone",preview.posts().get(0).description());
        }
        assertTrue(PostShare.summary(List.of(item()),options).contains("Private notes"));
    }
    @Test public void rejectsBackupUnknownVersionAndUnsafeLinks() throws Exception {
        JSONObject manifest=manifest(export(new PostShare.Options(false,false,false)));
        manifest.put("format","keepr"); rejects(archive(manifest,null,null)); manifest.put("format","keepr-posts").put("version",2); rejects(archive(manifest,null,null));
        manifest.put("version",1); manifest.getJSONArray("posts").getJSONObject(0).put("url","file:///secret"); rejects(archive(manifest,null,null));
    }
    @Test public void rejectsTraversalUnexpectedAndMissingPhotos() throws Exception {
        JSONObject manifest=manifest(export(new PostShare.Options(false,false,false)));
        rejects(archive(manifest,"../escape",new byte[]{1})); rejects(archive(manifest,"unexpected",new byte[]{1}));
        manifest.getJSONArray("posts").getJSONObject(0).getJSONArray("photos").put(new JSONObject().put("entry","photo-0.jpg").put("bytes",1).put("sha256","0".repeat(64)));
        rejects(archive(manifest,null,null)); rejects(archive(manifest,"photo-0.jpg",new byte[]{1})); rejects(archive(manifest,"photo-0.jpg",new byte[]{1,2}));
    }
    @Test public void rejectsOversizedAndRepeatedPhotoDeclarations() throws Exception {
        JSONObject manifest=manifest(export(new PostShare.Options(false,false,false))); JSONArray photos=manifest.getJSONArray("posts").getJSONObject(0).getJSONArray("photos");
        JSONObject photo=new JSONObject().put("entry","photo-0.jpg").put("bytes",PostShare.MAX_PHOTO_BYTES+1).put("sha256","0".repeat(64)); photos.put(photo); rejects(archive(manifest,null,null));
        photo.put("bytes",1); photos.put(photo); rejects(archive(manifest,null,null));
    }
    @Test public void acceptsVerifiedPhotoAndRemovesStagingOnClose() throws Exception {
        JSONObject manifest=manifest(export(new PostShare.Options(false,false,false)));
        byte[] bytes="abc".getBytes(StandardCharsets.UTF_8);
        StringBuilder hash=new StringBuilder(); for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format(Locale.ROOT,"%02x",b&255));
        manifest.getJSONArray("posts").getJSONObject(0).getJSONArray("photos").put(new JSONObject().put("entry","photo-0.jpg").put("bytes",3).put("sha256",hash.toString()));
        File directory;
        try(PostShare.Preview preview=PostShare.read(new ByteArrayInputStream(archive(manifest,"photo-0.jpg",bytes)),temp.getRoot())) {
            directory=preview.directory(); assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(new File(directory,"photo-0.jpg").toPath()));
        }
        assertFalse(directory.exists());
    }
}
