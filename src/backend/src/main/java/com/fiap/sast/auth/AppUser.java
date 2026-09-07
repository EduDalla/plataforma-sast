package com.fiap.sast.auth;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 254) public String email;
    @Column(nullable = false, length = 100) public String passwordHash;
}
