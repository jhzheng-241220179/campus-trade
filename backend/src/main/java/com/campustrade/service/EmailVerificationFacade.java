
package com.campustrade.service;

import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;
import java.util.Locale;
import com.campustrade.exception.VerificationCodeLimitException;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationFacade {

    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;

    public EmailVerificationFacade(
            UserMapper userMapper,
            VerificationCodeService verificationCodeService,
            EmailService emailService) {

        this.userMapper = userMapper;
        this.verificationCodeService = verificationCodeService;
        this.emailService = emailService;
    }


public void sendCode(String email) {

    if (email == null || email.isBlank()) {
        throw new IllegalArgumentException("邮箱不能为空");
    }

    String normalizedEmail = email.trim()
            .toLowerCase(Locale.ROOT);

    User user = userMapper.findByEmail(normalizedEmail);

    if (user == null) {
        return;
    }

    if (!"PENDING_VERIFICATION".equals(user.getStatus())
            || !Boolean.FALSE.equals(user.getEmailVerified())) {
        return;
    }

    VerificationCodeService.IssuedCode issued;

    try {
        issued = verificationCodeService.issueCode(user.getId());
    } catch (VerificationCodeLimitException ex) {
        return;
    }

    emailService.sendVerificationCode(
            user.getEmail(),
            issued.code()
    );
}


    public VerificationCodeService.VerifyResult verifyEmail(
            String email, String code) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }

        User user = userMapper.findByEmail(email.trim());

        if (user == null) {
            return VerificationCodeService.VerifyResult.USER_NOT_FOUND;
        }

        return verificationCodeService.verifyCode(
                user.getId(), code
        );
    }
}
