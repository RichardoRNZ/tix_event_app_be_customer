package com.tixevents.app.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.dto.AuthDTO;
import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User; 
import com.tixevents.app.entity.UserToken;
import com.tixevents.app.mapper.UserMapper;
import com.tixevents.app.repository.UserRepository;
import com.tixevents.app.repository.UserTokenRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import org.springframework.security.authentication.BadCredentialsException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserTokenRepository userTokenRepository;
    private final OTPService otpService;

    @Transactional
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (user.getVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not verified");
        }

        if (Boolean.TRUE.equals(user.getBlocked())) {
            if (user.getBlockedTime() == null || user.getBlockedTime().plusMinutes(30).isAfter(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is blocked. Please try again later.");
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));
        } catch (BadCredentialsException e) {
            int failedCount = (user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0) + 1;
            user.setFailedLoginCount(failedCount);
            if (failedCount > 3) {
                user.setBlocked(true);
                user.setBlockedTime(LocalDateTime.now());
            }
            userRepository.save(user);
            throw e;
        }

        user.setBlocked(false);
        user.setBlockedTime(null);
        user.setFailedLoginCount(0);
        userRepository.save(user);

        userTokenRepository.findByUser(user).ifPresent(userToken -> {
            if (!jwtService.isTokenExpired(userToken.getToken())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User login on another device");
            }
        });
        List<SimpleGrantedAuthority> authorities = 
                Collections.singletonList(
                        new SimpleGrantedAuthority(
                                Boolean.TRUE.equals(user.getIsAdmin()) ? "ROLE_ADMIN" : "ROLE_USER"
                        )
                );

        var userSecurity = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                authorities);
        String token = jwtService.generateToken(userSecurity);

        userTokenRepository.deleteByUser(user);
        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setToken(token);
        userTokenRepository.save(userToken);

        return new AuthDTO.LoginResponse(
                token,
                userMapper.toDto(user),
                "Login successful");
    }

    @Transactional
    public UserDTO register(AuthDTO.RegisterRequest request) {
        Optional<User> existing = userRepository.findByEmail(request.getEmail());
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getVerifiedAt() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
            }
           
            otpService.createOTP(u);
            return userMapper.toDto(u);
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be blank");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setIsAdmin(false);
        user.setBlocked(false);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setVerifiedAt(null);
        user.setFailedLoginCount(0);

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        otpService.createOTP(savedUser);
        return userMapper.toDto(savedUser);
    }

    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user logged in");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(userMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                userTokenRepository.deleteByToken(token);
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            userRepository.findByEmail(authentication.getName()).ifPresent(userTokenRepository::deleteByUser);
        }

        SecurityContextHolder.clearContext();
    }

    @Transactional
    public int cleanupExpiredTokens() {
        List<UserToken> allTokens = userTokenRepository.findAll();
        List<UserToken> expiredTokens = allTokens.stream()
                .filter(userToken -> jwtService.isTokenExpired(userToken.getToken()))
                .toList();

        if (!expiredTokens.isEmpty()) {
            userTokenRepository.deleteAll(expiredTokens);
        }
        return expiredTokens.size();
    }
}

