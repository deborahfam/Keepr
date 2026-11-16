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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ListingMetadataTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SavedItem post() { return new SavedItem("listing","","Apartment $1,850/month","Location: San Juan","","","","","","","Other",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",1,1,"",List.of(),List.of()); }
    private PreviewMetadata preview() { return new PreviewMetadata("https://example.com/post","Apartment","","","","2000","USD","",List.of(),0,"Ponce","Apartment","month","Public page data"); }
    @Test public void textExtractionPersistsAndManualCorrectionsSurviveRecapture() throws Exception {
        String name="listing-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository repo=new SQLiteSavedItemRepository(context,name)) {
            repo.add(post()); SavedItem item=repo.find("listing");
            assertEquals("1850",item.price()); assertEquals("month",item.pricePeriod()); assertEquals("San Juan",item.location()); assertEquals("Apartment",item.productType());
            repo.applyPreview(item.id(),preview()); assertEquals("Ponce",repo.find(item.id()).location());
            repo.applyPreview(item.id(),new PreviewMetadata("https://example.com/post","","","","","","",""));
            assertEquals("2000",repo.find(item.id()).price()); assertEquals("Ponce",repo.find(item.id()).location());
            repo.edit(item.id(),item.title(),"","Other","",new ListingFacts("1700","USD","month","Mayagüez","Apartment"));
            repo.applyPreview(item.id(),preview());
            assertEquals("1700",repo.find(item.id()).price()); assertEquals("Mayagüez",repo.find(item.id()).location());
            assertEquals(1,CollectionQuery.apply(repo.load(),"All","Mayagüez",null,"","",Long.MIN_VALUE,Long.MAX_VALUE,CollectionQuery.Sort.NEWEST).size());
            assertEquals(1,repo.page(new FeedQuery("All","Mayagüez",null,"","",Long.MIN_VALUE,Long.MAX_VALUE,CollectionQuery.Sort.NEWEST),0,10).total());
        } finally { context.deleteDatabase(name); }
    }
    @Test public void migrationPreservesVersionSixRows() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null); VaultDatabase helper=new VaultDatabase(context)) {
            db.execSQL("CREATE TABLE SavedItem(id TEXT PRIMARY KEY,title TEXT,price TEXT)");
            db.execSQL("INSERT INTO SavedItem VALUES ('old','My saved post','25')"); helper.onUpgrade(db,6,7);
            try(Cursor c=db.rawQuery("SELECT title,price,location,metadata_edited FROM SavedItem",null)) {
                assertTrue(c.moveToFirst()); assertEquals("My saved post",c.getString(0)); assertEquals("25",c.getString(1)); assertEquals("",c.getString(2)); assertEquals(0,c.getInt(3));
            }
        }
    }
    @Test public void backupRoundTripKeepsDetailsAndSupportsOlderArchives() throws Exception {
        String source="facts-src-"+UUID.randomUUID()+".db",target="facts-target-"+UUID.randomUUID()+".db",legacy="facts-old-"+UUID.randomUUID()+".db";
        try(SQLiteSavedItemRepository src=new SQLiteSavedItemRepository(context,source); SQLiteSavedItemRepository dst=new SQLiteSavedItemRepository(context,target); SQLiteSavedItemRepository old=new SQLiteSavedItemRepository(context,legacy)) {
            src.add(post()); src.edit("listing","Apartment","","Other","",new ListingFacts("1850","USD","month","San Juan","Apartment"));
            VaultBackup backup=new VaultBackup(context); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); backup.exportTo(src,bytes);
            backup.restoreFrom(dst,new ByteArrayInputStream(bytes.toByteArray())); assertEquals("San Juan",dst.find("listing").location());
            dst.applyPreview("listing",preview()); assertEquals("1850",dst.find("listing").price());
            JSONObject manifest;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { assertEquals("manifest.json",zip.getNextEntry().getName()); ByteArrayOutputStream json=new ByteArrayOutputStream(); zip.transferTo(json); manifest=new JSONObject(json.toString("UTF-8")); }
            manifest.put("version",2); JSONObject row=manifest.getJSONArray("SavedItem").getJSONObject(0);
            for(String key:new String[]{"location","product_type","price_period","metadata_source","metadata_edited"}) row.remove(key);
            ByteArrayOutputStream older=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(older)) { zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
            backup.restoreFrom(old,new ByteArrayInputStream(older.toByteArray())); assertEquals("1850",old.find("listing").price()); assertEquals("",old.find("listing").location());
        } finally { context.deleteDatabase(source);context.deleteDatabase(target);context.deleteDatabase(legacy); }
    }
}
