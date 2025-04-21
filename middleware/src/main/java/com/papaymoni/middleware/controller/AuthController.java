package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.JwtAuthResponse;
import com.papaymoni.middleware.dto.UserLoginDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.JwtTokenProvider;
import com.papaymoni.middleware.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> registerUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        // Check if username is already taken
        if (userService.existsByUsername(registrationDto.getUsername())) {
            return new ResponseEntity<>(ApiResponse.error("Username is already taken!"), HttpStatus.BAD_REQUEST);
        }

        // Check if email is already taken
        if (userService.existsByEmail(registrationDto.getEmail())) {
            return new ResponseEntity<>(ApiResponse.error("Email is already in use!"), HttpStatus.BAD_REQUEST);
        }

        // Create user
        User user = userService.registerUser(registrationDto);

        return new ResponseEntity<>(ApiResponse.success("User registered successfully", user), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> authenticateUser(@Valid @RequestBody UserLoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.getUsernameOrEmail(),
                        loginDto.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);

        return ResponseEntity.ok(ApiResponse.success("User logged in successfully", new JwtAuthResponse(jwt)));
    }
}
