package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.dto.LoginRequest;
import com.medplus.marketing_automation_backend.dto.LoginResponse;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider   = tokenProvider;
    }

    public LoginResponse login(LoginRequest req) {
        log.info("LOGIN attempt | email={}", req.getEmail());

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> {
                    log.warn("LOGIN failed — no account found | email={}", req.getEmail());
                    return new BadCredentialsException("Invalid email or password");
                });

        if (RecordStatus.ACTIVE != user.getStatus()) {
            log.warn("LOGIN failed — account disabled | email={} userId={}", req.getEmail(), user.getUserId());
            throw new BadCredentialsException("Account is disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("LOGIN failed — wrong password | email={} userId={}", req.getEmail(), user.getUserId());
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(
                user.getUserId(), user.getEmail(), user.getRoleNames(),
                user.getDepartmentName(), user.getDepartmentId(), user.getFullName());

        log.info("LOGIN success | email={} userId={} roles={} designation={}",
                user.getEmail(), user.getUserId(), user.getRoleNames(), user.getDesignationName());

        LoginResponse.UserSummary summary = LoginResponse.UserSummary.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getPrimaryRoleName())
                .roles(user.getRoleNames())
                .roleIds(user.getRoleIds())
                .departmentId(user.getDepartmentId())
                .department(user.getDepartmentName())
                .designationId(user.getDesignationId())
                .designation(user.getDesignationName())
                .build();

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(expirationMs)
                .user(summary)
                .build();
    }
}
