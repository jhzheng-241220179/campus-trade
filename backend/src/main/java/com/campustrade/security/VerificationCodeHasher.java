package com.campustrade.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class VerificationCodeHasher {

    private final byte[] secretKey;

    public VerificationCodeHasher(
            @Value("${app.verification.hmac-secret}")
            String secret) {

        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "验证码 HMAC 密钥至少需要 32 字节"
            );
        }

        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(Long userId, String code) {

        if (userId == null || code == null
                || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("验证码参数不正确");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    secretKey,
                    "HmacSHA256"
            ));

            String message = userId + ":" + code;

            byte[] result = mac.doFinal(
                    message.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(result);

        } catch (Exception ex) {
            throw new IllegalStateException("验证码摘要计算失败", ex);
        }
    }

    public boolean matches(
            Long userId,
            String code,
            String expectedHash) {

        if (expectedHash == null) {
            return false;
        }

        String actualHash = hash(userId, code);

        byte[] actual = HexFormat.of()
                .parseHex(actualHash);

        byte[] expected;

        try {
            expected = HexFormat.of()
                    .parseHex(expectedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        return MessageDigest.isEqual(actual, expected);
    }
}