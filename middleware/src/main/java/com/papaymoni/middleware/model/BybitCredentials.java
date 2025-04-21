//package com.papaymoni.middleware.model;
//
//import lombok.Data;
//
//import javax.persistence.*;
//import java.time.LocalDateTime;
//
//@Data
//@Entity
//@Table(name = "bybit_credentials")
//public class BybitCredentials {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @OneToOne
//    @JoinColumn(name = "user_id", nullable = false)
//    private User user;
//
//    @Column(nullable = false)
//    private String apiKey;
//
//    @Column(nullable = false)
//    private String apiSecret;
//
//    private boolean verified;
//    private LocalDateTime lastVerified;
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

@Data
@Entity
@Table(name = "bybit_credentials")
public class BybitCredentials {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String apiSecret;

    private boolean verified;
    private LocalDateTime lastVerified;

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
        return "BybitCredentials{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", apiKey='" + apiKey + '\'' +
                // Do not include apiSecret in toString for security reasons
                ", verified=" + verified +
                ", lastVerified=" + lastVerified +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}