package com.example.tkey_android;

import static android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;


import androidx.annotation.RequiresApi;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EnCryptor {
    private static final String TRANSFORMATION =  "AES/GCM/NoPadding"; // "AES/CBC/PKCS5Padding"; // pkcs5 padding,  16 bytes iv,

    private static final String TRANSFORMATION_OLDER_API = "RSA/ECB/PKCS1Padding"; // pkcs5 padding,  16 bytes iv,

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private byte[] encryption;
    private byte[] iv;

//    init with key pair
    EnCryptor(byte[] encryption) {
        this.encryption = encryption;
    }

    byte[] encryptText(final String alias, final String textToEncrypt, KeyStore keyStore) throws NoSuchPaddingException, NoSuchAlgorithmException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchProviderException, InvalidKeyException, KeyStoreException {
//        if(importedKey != null) {
//            byte[] keyBytes = Base64.decode(importedKey, Base64.DEFAULT);
//            KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(new SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES));
//            keyStore.setEntry(alias, secretKeyEntry, null);
//        }

        Cipher cipher = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            cipher = Cipher.getInstance(TRANSFORMATION);
//            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias));

            KeyPair keyPair = generateKeyPair(alias);
            PublicKey publicKey = keyPair.getPublic();
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        } else {
            KeyPair keyPair = getSecretKeyForOlderAPI(alias);
            cipher = Cipher.getInstance(TRANSFORMATION_OLDER_API);
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());

        }
        iv = cipher.getIV();
        return (encryption = cipher.doFinal(textToEncrypt.getBytes("UTF-8")));
    }

    private KeyPair getSecretKeyForOlderAPI (final String alias) throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
        keyPairGenerator.initialize(128);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private KeyPair generateKeyPair(final String alias) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
        keyPairGenerator.initialize(new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build());
        return keyPairGenerator.generateKeyPair();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getSecretKey(final String alias) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        final KeyGenerator keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

        keyGenerator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());

        return keyGenerator.generateKey();
    }

    byte[] getEncryption() {
        return encryption;
    }

    byte[] getIv() {
        return iv;
    }

}
