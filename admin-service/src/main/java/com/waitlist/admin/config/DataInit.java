package com.waitlist.admin.config;

import com.waitlist.admin.entity.AdminUser;
import com.waitlist.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInit {
    @Bean
    public CommandLineRunner initAdmin(AdminUserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByUsername("admin").isEmpty()) {
                var admin = new AdminUser();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                repo.save(admin);
            }
        };
    }
}
