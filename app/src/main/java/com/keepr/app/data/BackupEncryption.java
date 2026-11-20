package com.keepr.app.data;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned, authenticated 64 KiB frames; a mandatory final frame prevents truncation. */
public final class BackupEncryption {
    private static final byte[] MAGIC={'K','E','E','P','R','E','N',1};
    private static final int CHUNK=65536, ITERATIONS=600000;
    private static final long LIMIT=4L*1024*1024*1024+32L*1024*1024;
    private BackupEncryption() {}
    private static SecretKeySpec key(char[] password,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        byte[] encoded=null;
        try { encoded=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); return new SecretKeySpec(encoded,"AES"); }
        finally { spec.clearPassword(); if(encoded!=null) Arrays.fill(encoded,(byte)0); }
    }
    private static byte[] crypt(int mode,SecretKeySpec key,byte[] header,int sequence,int length,byte[] data) throws GeneralSecurityException {
        byte[] iv=ByteBuffer.allocate(12).put(header,24,8).putInt(sequence).array();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode,key,new GCMParameterSpec(128,iv));
        cipher.updateAAD(header); cipher.updateAAD(ByteBuffer.allocate(8).putInt(sequence).putInt(length).array());
        return cipher.doFinal(data);
    }
    public static OutputStream encrypt(OutputStream output,char[] password) throws IOException {
        if(password.length<12) throw new IOException("Use a password of at least 12 characters.");
        try {
            byte[] header=new byte[32]; new SecureRandom().nextBytes(header); System.arraycopy(MAGIC,0,header,0,MAGIC.length);
            SecretKeySpec key=key(password,Arrays.copyOfRange(header,8,24));
            DataOutputStream out=new DataOutputStream(output); out.write(header);
            return new OutputStream() {
                final byte[] buffer=new byte[CHUNK]; int used,sequence; long total; boolean closed;
                private void frame(int length) throws IOException {
                    try { byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,key,header,sequence++,length,Arrays.copyOf(buffer,length)); out.writeInt(length); out.write(encrypted); }
                    catch(GeneralSecurityException error) { throw new IOException("Backup encryption failed",error); }
                    finally { Arrays.fill(buffer,(byte)0); used=0; }
                }
                @Override public void write(int value) throws IOException { write(new byte[]{(byte)value},0,1); }
                @Override public void write(byte[] bytes,int offset,int count) throws IOException {
                    if(closed) throw new IOException("Closed backup");
                    if(offset<0 || count<0 || offset>bytes.length-count) throw new IndexOutOfBoundsException();
                    total+=count; if(total>LIMIT) throw new IOException("Backup too large");
                    while(count>0) { int n=Math.min(count,CHUNK-used); System.arraycopy(bytes,offset,buffer,used,n); used+=n; offset+=n; count-=n; if(used==CHUNK) frame(used); }
                }
                @Override public void close() throws IOException {
                    if(closed) return; closed=true;
                    try { if(used>0) frame(used); frame(0); out.flush(); }
                    finally { Arrays.fill(buffer,(byte)0); out.close(); }
                }
            };
        } catch(GeneralSecurityException error) { throw new IOException("Backup encryption unavailable",error); }
    }
    /** Caller must stage output privately and use it only after this method returns successfully. */
    public static void decrypt(InputStream input,OutputStream output,char[] password) throws IOException {
        DataInputStream in=new DataInputStream(input); byte[] header=new byte[32]; in.readFully(header);
        if(!Arrays.equals(MAGIC,Arrays.copyOf(header,8))) throw new IOException("Not an encrypted Keepr backup");
        try {
            SecretKeySpec key=key(password,Arrays.copyOfRange(header,8,24)); long total=0; int sequence=0;
            while(true) {
                int length=in.readInt(); if(length<0 || length>CHUNK) throw new IOException("Invalid backup frame");
                total+=length; if(total>LIMIT) throw new IOException("Backup too large");
                byte[] encrypted=new byte[length+16]; in.readFully(encrypted);
                byte[] plain=crypt(Cipher.DECRYPT_MODE,key,header,sequence++,length,encrypted);
                try {
                    if(length==0) { if(in.read()!=-1) throw new IOException("Unexpected backup data"); return; }
                    output.write(plain);
                } finally { Arrays.fill(plain,(byte)0); }
            }
        } catch(GeneralSecurityException error) { throw new IOException("Wrong password or damaged backup",error); }
    }
}
