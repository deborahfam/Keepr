package com.keepr.app.data;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.keepr.app.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ContactMetadataTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem item() { return new SavedItem("contact","","Casa #venta","Casa de tres plantas en La Vivora, 10 de octubre\nContacto: Ana\nTeléfono: +53 5555 1234\nCorreo: ana@example.com\nWhatsApp: +53 5555 5678 #habana","","","125000","USD","","","Other",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",1,1,"",List.of("my-tag"),List.of()); }
    @Test public void savedContactsCanBeClearedWithoutReturningOnRefresh() throws Exception {
        String name="contacts-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,name)) {
            repo.add(item()); var saved=repo.find("contact"); assertEquals("Casa",saved.title()); assertFalse(saved.description().contains("#")); assertEquals(List.of("my-tag"),saved.tags());
            assertEquals("Ana",saved.contacts().name()); assertEquals("+5355551234",saved.contacts().phone()); assertEquals("ana@example.com",saved.contacts().email()); assertEquals("+5355555678",saved.contacts().whatsapp());
            assertEquals("La Víbora, Diez de Octubre, La Habana, Cuba",saved.location());
            repo.editContacts("contact",new ContactDetails("Ana","","","",""));
            var preview=new MetadataParser().parse("<meta property='og:description' content='Phone: +5355559999; admin@example.com'><meta property='product:price:amount' content='100000'>","https://example.com/post");
            repo.applyPreview("contact",preview); saved=repo.find("contact"); assertEquals("",saved.contacts().phone()); assertEquals("",saved.contacts().email()); assertEquals("100000",saved.price());
            repo.load(); assertEquals("",repo.find("contact").contacts().phone());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void existingSavedTextGetsHavanaLocationAndLegacyContact() throws Exception {
        String name="backfill-contact-"+UUID.randomUUID()+".db";
        try(var repo=new SQLiteSavedItemRepository(context,name)) {
            repo.add(item()); repo.backupDatabase().execSQL("UPDATE SavedItem SET location='',location_version=0,contact_source='',contact_phone='',contact='7875551234',title='Casa',description='Casa de tres plantas en La Vivora, 10 de octubre'");
            var saved=repo.load().get(0); assertEquals("La Víbora, Diez de Octubre, La Habana, Cuba",saved.location()); assertEquals("7875551234",saved.contacts().phone());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void migrationAddsChannelsWithoutLosingLegacyContact() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null); VaultDatabase helper=new VaultDatabase(context)) {
            db.execSQL("CREATE TABLE SavedItem(id TEXT PRIMARY KEY,title TEXT,contact TEXT)"); db.execSQL("INSERT INTO SavedItem VALUES ('old','Keep','old@example.com')"); helper.onUpgrade(db,7,8);
            try(Cursor c=db.rawQuery("SELECT title,contact,contact_email,contact_edited,location_version FROM SavedItem",null)) { assertTrue(c.moveToFirst()); assertEquals("Keep",c.getString(0)); assertEquals("old@example.com",c.getString(1)); assertEquals("",c.getString(2)); assertEquals(0,c.getInt(3)); assertEquals(0,c.getInt(4)); }
        }
    }
    @Test public void backupKeepsChannelsAndAcceptsVersionThree() throws Exception {
        String a="contact-a-"+UUID.randomUUID()+".db",b="contact-b-"+UUID.randomUUID()+".db",c="contact-c-"+UUID.randomUUID()+".db";
        try(var src=new SQLiteSavedItemRepository(context,a);var dst=new SQLiteSavedItemRepository(context,b);var old=new SQLiteSavedItemRepository(context,c)) {
            src.add(item()); src.editContacts("contact",new ContactDetails("Ana","+5355551234","ana@example.com","+5355555678",""));
            VaultBackup backup=new VaultBackup(context); ByteArrayOutputStream output=new ByteArrayOutputStream(); backup.exportTo(src,output);
            backup.restoreFrom(dst,new ByteArrayInputStream(output.toByteArray())); assertEquals("+5355555678",dst.find("contact").contacts().whatsapp());
            try(Cursor cursor=dst.backupDatabase().rawQuery("SELECT contact_edited FROM SavedItem",null)) { assertTrue(cursor.moveToFirst()); assertEquals(1,cursor.getInt(0)); }
            JSONObject manifest;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) { zip.getNextEntry(); ByteArrayOutputStream json=new ByteArrayOutputStream(); zip.transferTo(json); manifest=new JSONObject(json.toString("UTF-8")); }
            manifest.put("version",3); JSONObject row=manifest.getJSONArray("SavedItem").getJSONObject(0);
            for(String key:new String[]{"contact_name","contact_phone","contact_email","contact_whatsapp","contact_source","contact_edited","location_version"}) row.remove(key);
            ByteArrayOutputStream older=new ByteArrayOutputStream(); try(ZipOutputStream zip=new ZipOutputStream(older)) { zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); }
            backup.restoreFrom(old,new ByteArrayInputStream(older.toByteArray())); assertEquals("Casa",old.find("contact").title()); assertEquals("",old.find("contact").contacts().phone());
        } finally { context.deleteDatabase(a);context.deleteDatabase(b);context.deleteDatabase(c); }
    }
}
