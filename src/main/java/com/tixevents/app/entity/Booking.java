package com.tixevents.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor 
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bookings_seq_gen")
    @SequenceGenerator(name = "bookings_seq_gen", sequenceName = "bookings_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "no_booking", nullable = false, unique = true)
    private String noBooking;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false)
    private String status;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "event_name")
    private String eventName;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingDetail> bookingDetails;
}
