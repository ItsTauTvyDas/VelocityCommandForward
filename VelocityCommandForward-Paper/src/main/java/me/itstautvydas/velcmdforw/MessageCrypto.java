package me.itstautvydas.velcmdforw;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class MessageCrypto {

    static final String SECRET_FILE = "secret.key";
    static final int NONCE_LEN = 12;

    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public MessageCrypto(String secret) throws GeneralSecurityException {
        this.key = new SecretKeySpec(MessageDigest
                .getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LEN];
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ct = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(NONCE_LEN + ct.length).put(nonce).put(ct).array();
    }
}