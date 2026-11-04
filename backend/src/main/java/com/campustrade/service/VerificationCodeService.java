
package com.campustrade.service;
import com.campustrade.exception.VerificationCodeLimitException;
import com.campustrade.mapper.EmailVerificationCodeMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.EmailVerificationCode;
import com.campustrade.model.User;
import com.campustrade.security.VerificationCodeGenerator;
import com.campustrade.security.VerificationCodeHasher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VerificationCodeService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ISSUES_PER_HOUR = 5;
    private static final int ISSUE_COOLDOWN_SECONDS = 60;

    private final UserMapper userMapper;
    private final EmailVerificationCodeMapper codeMapper;
    private final VerificationCodeGenerator generator;
    private final VerificationCodeHasher hasher;

    public VerificationCodeService(
            UserMapper userMapper,
            EmailVerificationCodeMapper codeMapper,
            VerificationCodeGenerator generator,
            VerificationCodeHasher hasher) {

        this.userMapper = userMapper;
        this.codeMapper = codeMapper;
        this.generator = generator;
        this.hasher = hasher;
    }

    public enum VerifyResult {
        SUCCESS,
        USER_NOT_FOUND,
        USER_NOT_PENDING,
        CODE_NOT_FOUND,
        CODE_EXPIRED,
        TOO_MANY_ATTEMPTS,
        WRONG_CODE
    }

    public record IssuedCode(
            String code,
            LocalDateTime expiresAt
    ) {}

    @Transactional
    public IssuedCode issueCode(Long userId) {

        User user = userMapper.findByIdForUpdate(userId);

        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!"PENDING_VERIFICATION".equals(user.getStatus())
                || !Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("账户当前不能签发验证码");
        }

        LocalDateTime now = LocalDateTime.now();

        if (userMapper.getVerificationFailureCount(userId)
                >= MAX_ATTEMPTS) {
            throw new VerificationCodeLimitException("验证码错误次数已达上限");
        }

        LocalDateTime lastIssuedAt =
                codeMapper.findLastIssuedAt(userId);

        if (lastIssuedAt != null
                && lastIssuedAt.plusSeconds(ISSUE_COOLDOWN_SECONDS)
                        .isAfter(now)) {
            throw new VerificationCodeLimitException(
                    "验证码申请过于频繁，请稍后重试");
        }

        int issuedCount = codeMapper.countIssuedSince(
                userId,
                now.minusHours(1)
        );

        if (issuedCount >= MAX_ISSUES_PER_HOUR) {
            throw new VerificationCodeLimitException(
                    "一小时内验证码申请次数已达上限");
        }

        codeMapper.invalidateUnused(userId, now);

        String plainCode = generator.generate();

        EmailVerificationCode record =
                new EmailVerificationCode();

        record.setUserId(userId);
        record.setCodeHash(hasher.hash(userId, plainCode));
        record.setExpiresAt(now.plusMinutes(5));
        record.setAttemptCount(0);

        int rows = codeMapper.insert(record);

        if (rows != 1 || record.getId() == null) {
            throw new IllegalStateException("验证码创建失败");
        }

        return new IssuedCode(
                plainCode,
                record.getExpiresAt()
        );
    }

    @Transactional
    public VerifyResult verifyCode(Long userId, String inputCode) {

        if (userId == null || inputCode == null) {
            throw new IllegalArgumentException("验证码参数不能为空");
        }

        User user = userMapper.findByIdForUpdate(userId);

        if (user == null) {
            return VerifyResult.USER_NOT_FOUND;
        }

        if (!"PENDING_VERIFICATION".equals(user.getStatus())
                || !Boolean.FALSE.equals(user.getEmailVerified())) {
            return VerifyResult.USER_NOT_PENDING;
        }

        if (userMapper.getVerificationFailureCount(userId)
                >= MAX_ATTEMPTS) {
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }

        EmailVerificationCode record =
                codeMapper.findLatestUnusedForUpdate(userId);

        if (record == null) {
            return VerifyResult.CODE_NOT_FOUND;
        }

        LocalDateTime now = LocalDateTime.now();

        if (!record.getExpiresAt().isAfter(now)) {
            return VerifyResult.CODE_EXPIRED;
        }

        if (record.getAttemptCount() >= MAX_ATTEMPTS) {
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }

        boolean validFormat = inputCode.matches("[0-9]{6}");

        boolean correct = validFormat
                && hasher.matches(
                        userId,
                        inputCode,
                        record.getCodeHash()
                );

        if (!correct) {

            int rows = codeMapper.incrementAttemptCount(
                    record.getId(),
                    now
            );

            if (rows != 1) {
                throw new IllegalStateException(
                        "验证码错误次数更新失败");
            }

            int updated =
                    userMapper.incrementVerificationFailureCount(userId);

            if (updated != 1) {
                throw new IllegalStateException(
                        "账户验证码错误次数更新失败");
            }

            return VerifyResult.WRONG_CODE;
        }

        int usedRows = codeMapper.markUsed(
                record.getId(),
                now
        );

        if (usedRows != 1) {
            throw new IllegalStateException("验证码状态更新失败");
        }

        int activatedRows = userMapper.activateEmail(userId);

        if (activatedRows != 1) {
            throw new IllegalStateException("用户激活失败");
        }

        return VerifyResult.SUCCESS;
    }
}
