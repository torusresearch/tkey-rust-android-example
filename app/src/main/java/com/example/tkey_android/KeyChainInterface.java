package com.example.tkey_android;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class KeyChainInterface {

    private KeyStore keyStore;
    private EnCryptor encryptor;
    private DeCryptor decryptor;

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";


    KeyChainInterface() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        encryptor = new EnCryptor();
        decryptor = new DeCryptor();
        initKeyStore();
    }

    private void initKeyStore() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
    }

    byte[] save(String alias, String textToSave, String importedKey) {
        try {
            return encryptor.encryptText(alias, textToSave, importedKey, keyStore);
        } catch (KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | NoSuchProviderException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    String fetch(String alias) {
        try {
            return decryptor.decryptData(alias, encryptor.getEncryption(), encryptor.getIv(), keyStore);
        } catch ( UnrecoverableEntryException | KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
//            throw new RuntimeException(e);
            return null;
        }
    }

    void deleteEntry(String alias) {
        try {
            decryptor.deleteEntry(alias, keyStore);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }
}
