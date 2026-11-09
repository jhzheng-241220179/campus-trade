
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
    private static final int LOCK_MINUTES = 15;

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
                Long id,
            String code,
            LocalDateTime expiresAt
    ) {
    }

    /**
     * 签发邮箱验证码。
     *
     * 规则：
     * 1. 仅待验证账户允许申请。
     * 2. 账户锁定期间拒绝申请。
     * 3. 锁定到期后，允许恢复并申请新验证码。
     * 4. 两次申请至少间隔 60 秒。
     * 5. 每小时最多签发 5 次。
     * 6. 新验证码签发后，旧验证码失效。
     */
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
        LocalDateTime lockedUntil =
                user.getVerificationLockedUntil();

        // 1. 锁定期间，不允许申请新验证码
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            throw new VerificationCodeLimitException(
                    "验证码验证暂时锁定，请稍后再试");
        }

        // 2. 锁定已到期：准备恢复账户
        boolean lockExpired = lockedUntil != null;

        if (!lockExpired
                && userMapper.getVerificationFailureCount(userId)
                        >= MAX_ATTEMPTS) {
            throw new VerificationCodeLimitException(
                    "验证码错误次数已达上限");
        }

        // 3. 检查 60 秒冷却时间
        LocalDateTime lastIssuedAt =
                codeMapper.findLastIssuedAt(userId);

        if (lastIssuedAt != null
                && lastIssuedAt.plusSeconds(ISSUE_COOLDOWN_SECONDS)
                        .isAfter(now)) {
            throw new VerificationCodeLimitException(
                    "验证码申请过于频繁，请稍后重试");
        }

        // 4. 检查每小时申请次数
        int issuedCount = codeMapper.countIssuedSince(
                userId,
                now.minusHours(1)
        );

        if (issuedCount >= MAX_ISSUES_PER_HOUR) {
            throw new VerificationCodeLimitException(
                    "一小时内验证码申请次数已达上限");
        }

        // 5. 所有限制检查通过后，恢复已到期的账户
        if (lockExpired) {

            int resetRows =
                    userMapper.resetExpiredVerificationLock(
                            userId, now
                    );

            if (resetRows != 1) {
                throw new IllegalStateException(
                        "账户锁定状态恢复失败");
            }
        }

        // 6. 作废之前所有未使用的验证码
        codeMapper.invalidateUnused(userId, now);

        // 7. 生成新验证码
        String plainCode = generator.generate();

        EmailVerificationCode record =
                new EmailVerificationCode();

        record.setUserId(userId);
        record.setCodeHash(hasher.hash(userId, plainCode));
        record.setExpiresAt(now.plusMinutes(5));
        record.setAttemptCount(0);

        // 8. 保存新验证码
        int rows = codeMapper.insert(record);

        if (rows != 1 || record.getId() == null) {
            throw new IllegalStateException("验证码创建失败");
        }

        return new IssuedCode(
                record.getId(),
                plainCode,
                record.getExpiresAt()
        );
    }

    /**
     * 校验邮箱验证码。
     *
     * 验证成功后：
     * - 将验证码标记为已使用
     * - 激活邮箱
     * - 清除账户错误计数及锁定状态
     *
     * 验证错误时：
     * - 增加单条验证码错误次数
     * - 增加账户累计错误次数
     * - 第五次错误时锁定账户 15 分钟
     */
    @Transactional
public void invalidateFailedDelivery(Long userId, Long codeId) {

    if (userId == null || codeId == null) {
        throw new IllegalArgumentException("验证码记录参数不能为空");
    }

    codeMapper.invalidateById(
            codeId,
            userId,
            LocalDateTime.now()
    );
}
    @Transactional
    public VerifyResult verifyCode(
            Long userId, String inputCode) {

        if (userId == null || inputCode == null) {
            throw new IllegalArgumentException(
                    "验证码参数不能为空");
        }

        User user = userMapper.findByIdForUpdate(userId);

        if (user == null) {
            return VerifyResult.USER_NOT_FOUND;
        }

        if (!"PENDING_VERIFICATION".equals(user.getStatus())
                || !Boolean.FALSE.equals(user.getEmailVerified())) {
            return VerifyResult.USER_NOT_PENDING;
        }

        // 锁定期间以及锁定到期但尚未重新申请时，
        // 都不允许验证旧验证码
        if (user.getVerificationLockedUntil() != null) {
            return VerifyResult.TOO_MANY_ATTEMPTS;
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

        boolean validFormat =
                inputCode.matches("[0-9]{6}");

        boolean correct = validFormat
                && hasher.matches(
                        userId,
                        inputCode,
                        record.getCodeHash()
                );

        if (!correct) {

            // 1. 增加本条验证码的失败次数
            int rows = codeMapper.incrementAttemptCount(
                    record.getId(),
                    now
            );

            if (rows != 1) {
                throw new IllegalStateException(
                        "验证码错误次数更新失败");
            }

            // 2. 增加账户累计失败次数
            int updated =
                    userMapper.incrementVerificationFailureCount(
                            userId
                    );

            if (updated != 1) {
                throw new IllegalStateException(
                        "账户验证码错误次数更新失败");
            }

            // 3. 达到 5 次时锁定账户
            int failureCount =
                    userMapper.getVerificationFailureCount(
                            userId
                    );

            if (failureCount >= MAX_ATTEMPTS) {

                int lockedRows =
                        userMapper.lockVerification(
                                userId,
                                now.plusMinutes(LOCK_MINUTES)
                        );

                if (lockedRows != 1) {
                    throw new IllegalStateException(
                            "验证码锁定失败");
                }
            }

            return VerifyResult.WRONG_CODE;
        }

        // 4. 验证成功后，将验证码标记为已使用
        int usedRows = codeMapper.markUsed(
                record.getId(),
                now
        );

        if (usedRows != 1) {
            throw new IllegalStateException(
                    "验证码状态更新失败");
        }

        // 5. 激活账户
        int activatedRows =
                userMapper.activateEmail(userId);

        if (activatedRows != 1) {
            throw new IllegalStateException(
                    "用户激活失败");
        }

        return VerifyResult.SUCCESS;
    }
}
