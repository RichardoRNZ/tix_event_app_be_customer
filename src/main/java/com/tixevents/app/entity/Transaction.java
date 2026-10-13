package com.tixevents.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor 
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactions_seq_gen")
    @SequenceGenerator(name = "transactions_seq_gen", sequenceName = "transactions_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @Column(name = "booking_no")
    private String bookingNo;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Column(name = "idempotency_key")
    private String idempotencyKey;
}
