package com.spendly.backend.auth;

import com.spendly.backend.common.ApiException;
import com.spendly.backend.user.User;
import com.spendly.backend.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public UserResponse register(CredentialsRequest request) {
        // 이메일 중복 검사
        if (users.existsByEmail(request.email())) { throw duplicateEmail(); }
        try {
            User user = users.saveAndFlush(new User(request.email(), passwords.encode(request.password())));
            return new UserResponse(user.getId(), user.getEmail());
        } catch (DataIntegrityViolationException exception) {
            // DB에서 발생한 오류가 이메일 중복 때문인지 확인
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uk_users_email".equals(constraint.getConstraintName())) {
                    throw duplicateEmail();
                }
            }
            throw exception;
        }
    }

    private ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
    }
}
