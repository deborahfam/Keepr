package com.keepr.app.data;

import com.keepr.app.domain.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Portable, allowlisted publication format, deliberately separate from a vault backup. */
public final class PostShare {
    public static final String MIME="application/vnd.keepr.posts+zip";
    public static final int MAX_POSTS=100, MAX_PHOTOS=1000, MAX_PHOTO_BYTES=8*1024*1024, MAX_MANIFEST=2*1024*1024;
    public static final long MAX_BYTES=256L*1024*1024;
    public record Options(boolean notes,boolean contacts,boolean caption) {}
    public record Photo(String entry,long bytes,String sha256) {}
    public record Post(String url,String title,String description,String price,String currency,String period,
                       String location,String productType,String notes,ContactDetails contacts,List<Photo> photos) {}
    public record Preview(File directory,List<Post> posts) implements AutoCloseable {
        @Override public void close() { remove(directory); }
    }
    private PostShare() {}

    public static String summary(List<SavedItem> items,Options options) {
        StringBuilder out=new StringBuilder();
        for(SavedItem item:items) {
            if(out.length()>0) out.append("\n\n——\n\n");
            line(out,item.title());
            if(!item.price().isEmpty()) line(out,(item.currency()+" "+item.price()).trim()+(item.pricePeriod().isEmpty()?"":" / "+item.pricePeriod()));
            line(out,item.location()); line(out,item.productType()); line(out,item.url());
            if(options.caption()) line(out,caption(item));
            if(options.notes() && !item.notes().isEmpty()) line(out,"Notes: "+item.notes());
            if(options.contacts()) { ContactDetails c=item.contacts(); line(out,c.name()); line(out,c.phone()); line(out,c.email()); line(out,c.whatsapp().isEmpty()?"":"WhatsApp: "+c.whatsapp()); }
        }
        if(out.length()>200_000) throw new IllegalArgumentException("Summary is too long. Select fewer posts or omit captions and notes.");
        return out.toString().trim();
    }
    private static void line(StringBuilder out,String value) { if(!value.isEmpty()) out.append(value).append('\n'); }
    private static String caption(SavedItem item) { return item.description().isEmpty()?item.sharedText():item.description(); }

    public static void write(List<SavedItem> items,Options options,File files,OutputStream output) throws Exception {
        if(items.isEmpty() || items.size()>MAX_POSTS) throw new IOException("Select 1–100 posts.");
        JSONArray posts=new JSONArray(); List<File> sources=new ArrayList<>(); List<Photo> photos=new ArrayList<>(); long total=0;
        for(SavedItem item:items) {
            JSONObject post=new JSONObject();
            post.put("url",item.url()); post.put("title",item.title()); post.put("description",options.caption()?caption(item):"");
            post.put("price",item.price()); post.put("currency",item.currency()); post.put("period",item.pricePeriod());
            post.put("location",item.location()); post.put("productType",item.productType()); post.put("notes",options.notes()?item.notes():"");
            ContactDetails c=options.contacts()?item.contacts():ContactDetails.EMPTY;
            post.put("contacts",new JSONObject().put("name",c.name()).put("phone",c.phone()).put("email",c.email()).put("whatsapp",c.whatsapp()));
            JSONArray media=new JSONArray();
            for(CachedMedia image:item.media()) {
                File source=new File(files,image.relativePath()).getCanonicalFile();
                File mediaRoot=new File(files,"media").getCanonicalFile();
                if(!source.toPath().startsWith(mediaRoot.toPath()) || !source.isFile() || source.length()>MAX_PHOTO_BYTES || source.length()!=image.byteCount()) throw new IOException("A saved photo is missing or too large.");
                total+=source.length(); if(total>MAX_BYTES || photos.size()>=MAX_PHOTOS) throw new IOException("Selection exceeds the sharing limit. Select fewer posts.");
                Photo photo=new Photo("photo-"+photos.size()+".jpg",source.length(),image.sha256()); photos.add(photo); sources.add(source);
                media.put(new JSONObject().put("entry",photo.entry()).put("bytes",photo.bytes()).put("sha256",photo.sha256()));
            }
            post.put("photos",media); posts.put(post);
        }
        byte[] manifest=new JSONObject().put("format","keepr-posts").put("version",1).put("posts",posts).toString().getBytes(StandardCharsets.UTF_8);
        if(manifest.length>MAX_MANIFEST || total+manifest.length>MAX_BYTES) throw new IOException("Selection is too large.");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry();
            for(int i=0;i<photos.size();i++) {
                zip.putNextEntry(new ZipEntry(photos.get(i).entry()));
                try(InputStream input=new FileInputStream(sources.get(i))) { verifyCopy(input,zip,photos.get(i)); }
                zip.closeEntry();
            }
        }
    }

    /** Stage and verify the entire archive before allowing any database writes. */
    public static Preview read(InputStream input,File cache) throws Exception {
        File directory=new File(cache,"keepr-import-"+UUID.randomUUID());
        if(!directory.mkdirs()) throw new IOException("Could not prepare import.");
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry first=zip.getNextEntry();
            if(first==null || !first.getName().equals("manifest.json")) throw new IOException("Choose a Keepr sharing file, not a backup.");
            byte[] json=bounded(zip,MAX_MANIFEST);
            JSONObject manifest=new JSONObject(new String(json,StandardCharsets.UTF_8));
            if(!manifest.getString("format").equals("keepr-posts") || manifest.getInt("version")!=1) throw new IOException("Unsupported Keepr sharing format.");
            JSONArray rows=manifest.getJSONArray("posts");
            if(rows.length()==0 || rows.length()>MAX_POSTS) throw new IOException("Invalid post count.");
            List<Post> posts=new ArrayList<>(); Map<String,Photo> expected=new LinkedHashMap<>(); long total=json.length;
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); String url=string(row,"url",8192);
                if(!url.isEmpty()) url=UrlExtractor.normalize(url);
                JSONObject c=row.getJSONObject("contacts");
                ContactDetails contacts=ContactParser.validated(string(c,"name",100),string(c,"phone",40),string(c,"email",254),string(c,"whatsapp",100));
                JSONArray media=row.getJSONArray("photos"); List<Photo> photos=new ArrayList<>();
                for(int p=0;p<media.length();p++) {
                    JSONObject m=media.getJSONObject(p); String name=string(m,"entry",40),hash=string(m,"sha256",64); long size=m.getLong("bytes");
                    if(!name.matches("photo-[0-9]{1,4}\\.jpg") || !hash.matches("[a-f0-9]{64}") || size<1 || size>MAX_PHOTO_BYTES) throw new IOException("Invalid photo metadata.");
                    Photo photo=new Photo(name,size,hash);
                    if(expected.put(name,photo)!=null || expected.size()>MAX_PHOTOS) throw new IOException("Repeated photo or too many photos.");
                    total+=size; if(total>MAX_BYTES) throw new IOException("Sharing file exceeds 256 MiB."); photos.add(photo);
                }
                posts.add(new Post(url,string(row,"title",65536),string(row,"description",65536),string(row,"price",128),string(row,"currency",32),string(row,"period",100),string(row,"location",1000),string(row,"productType",1000),string(row,"notes",65536),contacts,List.copyOf(photos)));
            }
            Set<String> seen=new HashSet<>(); ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                Photo photo=expected.get(entry.getName());
                if(photo==null || entry.isDirectory() || !seen.add(entry.getName())) throw new IOException("Unexpected or repeated archive entry.");
                try(OutputStream out=new FileOutputStream(new File(directory,photo.entry()))) { verifyCopy(zip,out,photo); }
            }
            if(seen.size()!=expected.size()) throw new IOException("The sharing file is missing photos.");
            return new Preview(directory,List.copyOf(posts));
        } catch(Exception error) { remove(directory); throw error; }
    }
    private static String string(JSONObject object,String key,int limit) throws Exception {
        Object value=object.get(key); if(!(value instanceof String) || ((String)value).length()>limit) throw new IOException("Invalid or oversized field: "+key); return (String)value;
    }
    private static byte[] bounded(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while((count=in.read(buffer,0,Math.min(buffer.length,max+1-out.size())))!=-1) {
            out.write(buffer,0,count); if(out.size()>max) throw new IOException("Manifest is too large.");
        } return out.toByteArray();
    }
    private static void verifyCopy(InputStream in,OutputStream out,Photo photo) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[8192]; long bytes=0; int count;
        while((count=in.read(buffer,0,(int)Math.min(buffer.length,photo.bytes()+1-bytes)))!=-1) {
            bytes+=count; if(bytes>photo.bytes()) throw new IOException("Photo is larger than declared."); digest.update(buffer,0,count); out.write(buffer,0,count);
        }
        StringBuilder hash=new StringBuilder(); for(byte b:digest.digest()) hash.append(String.format(Locale.ROOT,"%02x",b&255));
        if(bytes!=photo.bytes() || !hash.toString().equals(photo.sha256())) throw new IOException("Photo is missing or damaged.");
    }
    private static void remove(File directory) { File[] children=directory.listFiles(); if(children!=null) for(File child:children) child.delete(); directory.delete(); }
}
