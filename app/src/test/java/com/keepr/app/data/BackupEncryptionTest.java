package com.keepr.app.data;

import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackupEncryptionTest {
    private static final char[] PASSWORD="test-only long password".toCharArray();
    private byte[] encrypt(byte[] plain) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(OutputStream encrypted=BackupEncryption.encrypt(out,PASSWORD)) { encrypted.write(plain); }
        return out.toByteArray();
    }
    private byte[] decrypt(byte[] encrypted,char[] password) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); BackupEncryption.decrypt(new ByteArrayInputStream(encrypted),out,password); return out.toByteArray();
    }
    @Test public void multipleFramesRoundTripAndRandomize() throws Exception {
        byte[] plain=new byte[140000]; new java.util.Random(7).nextBytes(plain);
        byte[] encrypted=encrypt(plain); assertArrayEquals(plain,decrypt(encrypted,PASSWORD));
        assertFalse(Arrays.equals(encrypted,encrypt(plain)));
        assertArrayEquals(new byte[0],decrypt(encrypt(new byte[0]),PASSWORD));
    }
    @Test public void rejectsWrongPasswordTamperingTruncationReorderingAndAppend() throws Exception {
        byte[] encrypted=encrypt(new byte[140000]);
        try { decrypt(encrypted,"wrong-password".toCharArray()); fail(); } catch(IOException expected) {}
        byte[] tampered=encrypted.clone(); tampered[50]^=1;
        byte[] header=encrypted.clone(); header[10]^=1;
        byte[] reordered=encrypted.clone(); int frame=65536+20;
        System.arraycopy(encrypted,32+frame,reordered,32,frame);
        System.arraycopy(encrypted,32,reordered,32+frame,frame);
        for(byte[] broken:new byte[][]{tampered,header,reordered,Arrays.copyOf(encrypted,encrypted.length-1),Arrays.copyOf(encrypted,encrypted.length-20),Arrays.copyOf(encrypted,encrypted.length+1)}) {
            try { decrypt(broken,PASSWORD); fail("Unauthenticated backup accepted"); } catch(IOException expected) {}
        }
    }
    @Test public void rejectsWeakExportPassword() throws Exception {
        try { BackupEncryption.encrypt(new ByteArrayOutputStream(),"short".toCharArray()); fail(); } catch(IOException expected) {}
    }
}
