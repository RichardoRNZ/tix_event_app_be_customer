package com.tixevents.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor 
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq_gen")
    @SequenceGenerator(name = "users_seq_gen", sequenceName = "users_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "is_admin")
    private Boolean isAdmin = false;


    private Boolean blocked = false;

    @Column(name = "blocked_time")
    private LocalDateTime blockedTime;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "failed_login_count")
    private Integer failedLoginCount = 0;
}
