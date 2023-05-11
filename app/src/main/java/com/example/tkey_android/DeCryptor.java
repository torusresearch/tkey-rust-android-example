package com.example.tkey_android;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class DeCryptor {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private KeyStore keyStore;

    DeCryptor() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        initKeyStore();
    }

    private void initKeyStore() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
    }

    String decryptData(final String alias, final byte[] encryptedData, final byte[] encryptionIv) throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, UnrecoverableEntryException, KeyStoreException, InvalidAlgorithmParameterException, InvalidKeyException {
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        final GCMParameterSpec spec = new GCMParameterSpec(128, encryptionIv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), spec);
        return new String(cipher.doFinal(encryptedData), "UTF-8");
    }

    private SecretKey getSecretKey(final String alias) throws UnrecoverableEntryException, KeyStoreException, NoSuchAlgorithmException {
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
    }

    void deleteEntry(String alias) throws KeyStoreException {
        keyStore.deleteEntry(alias);
    }
}
