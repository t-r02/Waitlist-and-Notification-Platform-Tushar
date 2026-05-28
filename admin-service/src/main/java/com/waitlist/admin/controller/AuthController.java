package com.waitlist.admin.controller;

import com.waitlist.admin.repository.AdminUserRepository;
import com.waitlist.admin.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminUserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        var user = userRepo.findByUsername(creds.get("username"));

        if (user.isEmpty() || !encoder.matches(creds.get("password"), user.get().getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        String token = jwtService.generateToken(user.get().getUsername());
        return ResponseEntity.ok(Map.of("token", token));
    }
}
