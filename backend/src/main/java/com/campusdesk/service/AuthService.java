package com.campusdesk.service;

import com.campusdesk.domain.Role;
import com.campusdesk.domain.User;
import com.campusdesk.dto.AuthRequest;
import com.campusdesk.dto.AuthResponse;
import com.campusdesk.dto.RegisterRequest;
import com.campusdesk.repository.UserRepository;
import com.campusdesk.security.AuthRequestDiagnostics;
import com.campusdesk.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            AuthRequestDiagnostics.logCurrent(LOGGER, HttpStatus.CONFLICT.value());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            AuthRequestDiagnostics.logCurrent(LOGGER, HttpStatus.CONFLICT.value());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.STUDENT);
        userRepository.save(user);

        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setUsername(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    public AuthResponse login(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            AuthRequestDiagnostics.logCurrent(LOGGER, HttpStatus.UNAUTHORIZED.value());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> {
                AuthRequestDiagnostics.logCurrent(LOGGER, HttpStatus.UNAUTHORIZED.value());
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            });

        AuthRequestDiagnostics.logCurrent(LOGGER, HttpStatus.OK.value());
        return new AuthResponse(jwtTokenService.generateToken(user), user.getUsername(), user.getRole().name());
    }
}
