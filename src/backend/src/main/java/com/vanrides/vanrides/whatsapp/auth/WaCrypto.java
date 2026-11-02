package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class WaCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "BCDFGHJKMNPQRSTVWXYZ";
    private final WaSettings settings;
    public WaCrypto(WaSettings settings) { this.settings = settings; }
    public static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return base64(bytes);
    }
    public static String userCode() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) out.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return out.toString();
    }
    public static String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    public static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception e) { throw new IllegalStateException("WA_CRYPTO_UNAVAILABLE"); }
    }
    public static String digest(String value) { return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8))); }
    public static boolean same(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(
            first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
    public String secretHash(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.hashKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("WA_CRYPTO_UNAVAILABLE"); }
    }
    public byte[] encrypt(String context, String value) {
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (Exception e) { throw new IllegalStateException("WA_CRYPTO_UNAVAILABLE"); }
    }
    public String decrypt(String context, byte[] value) {
        try {
            if (value.length < 28 || value.length > 32768) throw new IllegalArgumentException();
            ByteBuffer in = ByteBuffer.wrap(value); byte[] nonce = new byte[12]; in.get(nonce);
            byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, context).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("WA_REPLAY_UNAVAILABLE"); }
    }
    private Cipher cipher(int mode, byte[] nonce, String context) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(settings.responseKey(), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD((settings.environment() + "\0" + context).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
