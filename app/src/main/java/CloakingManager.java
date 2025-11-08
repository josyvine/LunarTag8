package com.hfm.app;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CloakingManager {

    private static final String TAG = "CloakingManager";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String STATIC_SALT = "hfm_messenger_drop_salt";
    private static final byte[] STATIC_IV = "hfm_static_iv_16".getBytes(StandardCharsets.UTF_8);

    private static SecretKeySpec generateKey(String secret) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        KeySpec spec = new PBEKeySpec(secret.toCharArray(), STATIC_SALT.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    public static File cloakFile(Context context, File inputFile, String secret) {
        File encryptedTempFile = null;
        File cloakedFile = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        CipherOutputStream cos = null;
        FileInputStream encryptedFis = null;
        FileOutputStream cloakedFos = null;

        try {
            encryptedTempFile = new File(context.getCacheDir(), "encrypted_" + System.currentTimeMillis() + ".tmp");
            SecretKeySpec secretKey = generateKey(secret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec iv = new IvParameterSpec(STATIC_IV);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(encryptedTempFile);
            cos = new CipherOutputStream(fos, cipher);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
            }
            cos.flush();
            cos.close(); // Close CipherOutputStream to finalize encryption
            fos.close();
            fis.close();

            Log.d(TAG, "File encrypted successfully to temp file.");

            cloakedFile = new File(context.getCacheDir(), "cloaked_" + System.currentTimeMillis() + ".log");
            encryptedFis = new FileInputStream(encryptedTempFile);
            cloakedFos = new FileOutputStream(cloakedFile, false);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                OutputStream base64OutputStream = Base64.getEncoder().wrap(cloakedFos);
                buffer = new byte[8192];
                while ((bytesRead = encryptedFis.read(buffer)) != -1) {
                    base64OutputStream.write(buffer, 0, bytesRead);
                }
                base64OutputStream.close();
            } else {
                // --- THIS IS THE FIX for the slow cloaking ---
                // This logic now streams the file instead of loading it all into memory.
                // It reads a chunk, encodes it, and writes it, which is much faster.
                InputStream in = new FileInputStream(encryptedTempFile);
                OutputStream out = new FileOutputStream(cloakedFile);
                OutputStream base64Out = new android.util.Base64OutputStream(out, android.util.Base64.NO_WRAP);
                
                buffer = new byte[8192];
                while ((bytesRead = in.read(buffer)) != -1) {
                    base64Out.write(buffer, 0, bytesRead);
                }
                base64Out.close(); // This is crucial to flush the final encoded block.
                in.close();
                // We no longer use cloakedFos directly in this `else` block.
            }

            Log.d(TAG, "Encrypted file has been Base64 encoded and cloaked as a .log file.");
            return cloakedFile;

        } catch (Exception e) {
            Log.e(TAG, "Cloaking process failed.", e);
            return null;
        } finally {
            try { if (fis != null) fis.close(); } catch (Exception e) { /* ignore */ }
            try { if (fos != null) fos.close(); } catch (Exception e) { /* ignore */ }
            try { if (cos != null) cos.close(); } catch (Exception e) { /* ignore */ }
            try { if (encryptedFis != null) encryptedFis.close(); } catch (Exception e) { /* ignore */ }
            try { if (cloakedFos != null) cloakedFos.close(); } catch (Exception e) { /* ignore */ }

            if (encryptedTempFile != null && encryptedTempFile.exists()) {
                encryptedTempFile.delete();
            }
        }
    }

    public static boolean restoreFile(File cloakedFile, File outputFile, String secret) {
        FileInputStream fis = null;
        CipherInputStream cis = null;
        FileOutputStream fos = null;
        InputStream base64InputStream = null;

        try {
            SecretKeySpec secretKey = generateKey(secret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec iv = new IvParameterSpec(STATIC_IV);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

            fis = new FileInputStream(cloakedFile);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                base64InputStream = Base64.getDecoder().wrap(fis);
            } else {
                // --- FIX for potential memory issues on older APIs during restore ---
                // Use a streaming decoder instead of loading the whole file into a byte array.
                base64InputStream = new android.util.Base64InputStream(fis, android.util.Base64.NO_WRAP);
            }

            cis = new CipherInputStream(base64InputStream, cipher);

            fos = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = cis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();

            Log.d(TAG, "File restored and decrypted successfully.");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Restoration process failed.", e);
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            return false;
        } finally {
            try { if (fis != null) fis.close(); } catch (Exception e) { /* ignore */ }
            try { if (cis != null) cis.close(); } catch (Exception e) { /* ignore */ }
            try { if (fos != null) fos.close(); } catch (Exception e) { /* ignore */ }
        }
    }
}