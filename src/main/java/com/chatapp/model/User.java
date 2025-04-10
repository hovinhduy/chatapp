package com.chatapp.model;

import com.chatapp.enums.Gender;
import com.chatapp.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, unique = true, length = 100)
    private String phone;

    @Column( unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String password;

    private String avatarUrl;

    private LocalDateTime lastLogin;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sender")
    private Set<Message> sentMessages = new HashSet<>();

    @OneToMany(mappedBy = "receiver")
    private Set<Message> receivedMessages = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<GroupMember> groupMemberships = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = UserStatus.OFFLINE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}