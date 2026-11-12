package me.itstautvydas.velcmdforw;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class MessageCrypto {

    static final String SECRET_FILE = "secret.key";
    static final long MAX_AGE_MILLIS = 30000;
    static final int NONCE_LEN = 12;

    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public MessageCrypto(String secret) throws GeneralSecurityException {
        this.key = new SecretKeySpec(MessageDigest
                .getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public byte[] decrypt(byte[] message) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, message, 0, NONCE_LEN));
        return cipher.doFinal(message, NONCE_LEN, message.length - NONCE_LEN);
    }
}