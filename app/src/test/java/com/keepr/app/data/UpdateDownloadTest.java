package com.keepr.app.data;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import java.io.File;
import static org.junit.Assert.*;
public class UpdateDownloadTest {
 @Rule public TemporaryFolder temporary=new TemporaryFolder();
 @Test public void verifiesAndPromotesCompleteDownload() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setBody("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\n"));
   server.enqueue(new MockResponse().setBody("abc")); server.start();
   File directory=temporary.newFolder(); long[] progress={0};
   File file=UpdateDownload.fetchVerified(new AppUpdates.Release("1.0.0","","",3),directory,(read,total)->progress[0]=read,new OkHttpClient(),server.url("/app.apk").toString());
   assertEquals("abc",new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8)); assertEquals(3,progress[0]); assertFalse(new File(directory,"update.part").exists());
  }
 }
 @Test public void rejectsTamperingAndRemovesPartialFile() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setBody("0".repeat(64))); server.enqueue(new MockResponse().setBody("abc")); server.start(); File directory=temporary.newFolder();
   try { UpdateDownload.fetchVerified(new AppUpdates.Release("1.0.0","","",3),directory,(r,t)->{},new OkHttpClient(),server.url("/app.apk").toString()); fail(); } catch(java.io.IOException expected) { }
   assertFalse(new File(directory,"update.apk").exists()); assertFalse(new File(directory,"update.part").exists());
  }
 }
}
