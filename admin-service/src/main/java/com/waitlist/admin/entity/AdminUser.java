package com.waitlist.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "admin_users")
public class AdminUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;
}
