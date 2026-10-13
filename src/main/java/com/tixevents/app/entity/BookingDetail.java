package com.tixevents.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "booking_details")
@Data
@NoArgsConstructor 
public class BookingDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "booking_details_seq_gen")
    @SequenceGenerator(name = "booking_details_seq_gen", sequenceName = "booking_details_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "seat_details_id", nullable = false)
    private Long seatDetailsId;

    @Column(name = "seat_number")
    private String seatNumber;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
