//package com.papaymoni.middleware.model;
//
//import lombok.Data;
//
//import javax.persistence.*;
//import java.time.LocalDateTime;
//import java.util.Set;
//
//@Data
//@Entity
//@Table(name = "users")
//public class User {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @Column(unique = true, nullable = false)
//    private String username;
//
//    @Column(unique = true, nullable = false)
//    private String email;
//
//    @Column(nullable = false)
//    private String password;
//
//    private String firstName;
//    private String lastName;
//    private String phoneNumber;
//    private boolean emailVerified;
//    private boolean phoneVerified;
//    private String referralCode;
//    private String referredBy;
//
//    @OneToOne(cascade = CascadeType.ALL, mappedBy = "user")
//    private BybitCredentials bybitCredentials;
//
////    @OneToMany(mappedBy = "user")
////    private Set<VirtualAccount> virtualAccounts;
//
//    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
//    private VirtualAccount virtualAccount;
//
//    @OneToMany(mappedBy = "user")
//    private Set<Transaction> transactions;
//
//    @OneToMany(mappedBy = "user")
//    private Set<Order> orders;
//
//    private LocalDateTime createdAt;
//    private LocalDateTime updatedAt;
//
//    @PrePersist
//    protected void onCreate() {
//        createdAt = LocalDateTime.now();
//        updatedAt = LocalDateTime.now();
//    }
//
//    @PreUpdate
//    protected void onUpdate() {
//        updatedAt = LocalDateTime.now();
//    }
//}



package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String referralCode;
    private String referredBy;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<VirtualAccount> virtualAccounts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Custom toString method to avoid circular reference
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", emailVerified=" + emailVerified +
                ", phoneVerified=" + phoneVerified +
                ", referralCode='" + referralCode + '\'' +
                ", referredBy='" + referredBy + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}