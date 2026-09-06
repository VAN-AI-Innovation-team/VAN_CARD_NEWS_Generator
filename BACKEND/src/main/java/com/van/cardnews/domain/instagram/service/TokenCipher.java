package com.van.cardnews.domain.instagram.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 토큰을 AES/GCM으로 암복호화합니다. 자바 표준 {@code javax.crypto}만 쓰므로 의존성이 늘지 않습니다.
 *
 * 저장 형식은 {@code base64(iv || ciphertext+tag)}입니다. IV는 매 암호화마다 새로 뽑기 때문에
 * 같은 토큰을 두 번 암호화해도 결과가 다릅니다.
 *
 * 예외 메시지에는 평문도 암호문도 싣지 않습니다. 토큰이 로그로 새어 나가는 가장 흔한 경로가
 * "복호화 실패 + 값 첨부" 예외입니다.
 */
@Component
public class TokenCipher {

    /**
     * dev 프로필에서 목업 토큰을 다루기 위한 기본 키입니다.
     * 값이 리포에 있으므로 보호 효과가 없고, prod에서 이 키가 쓰이면 기동을 막습니다.
     */
    public static final String DEV_DEFAULT_KEY = "ZGV2LW9ubHkta2V5LWRvLW5vdC11c2UtaW4tcHJvZCE=";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public TokenCipher(
            @Value("${app.instagram.token.encryption-key}") String base64Key,
            Environment environment
    ) {
        if (environment.matchesProfiles("prod") && DEV_DEFAULT_KEY.equals(base64Key)) {
            throw new IllegalStateException(
                    "prod 프로필에서 dev 기본 암호화 키를 쓸 수 없습니다. "
                            + "INSTAGRAM_TOKEN_ENCRYPTION_KEY를 주입하십시오.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.instagram.token.encryption-key가 base64가 아닙니다.");
        }

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.instagram.token.encryption-key는 base64로 인코딩된 32바이트여야 합니다. "
                            + "현재 " + keyBytes.length + "바이트");
        }

        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토큰 암호화에 실패했습니다.");
        }
    }

    public String decrypt(String encoded) {
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("저장된 토큰이 base64 형식이 아닙니다.");
        }

        if (combined.length <= IV_LENGTH) {
            throw new IllegalStateException("저장된 토큰의 길이가 올바르지 않습니다.");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, Arrays.copyOf(combined, IV_LENGTH)));

            byte[] plaintext = cipher.doFinal(
                    Arrays.copyOfRange(combined, IV_LENGTH, combined.length));

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토큰 복호화에 실패했습니다. 암호화 키가 바뀌었을 수 있습니다.");
        }
    }
}
