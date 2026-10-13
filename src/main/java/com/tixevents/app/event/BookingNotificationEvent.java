package com.tixevents.app.event;

import java.math.BigDecimal;
import java.util.List;

import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.Transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingNotificationEvent {

    private String email;
    private String status;
    private Transaction transaction;
    private Booking booking;
    private String customerName;
    private String bookingNo;
    private String eventName;
    private List<Long> seatDetailsIds;
    private String seats;
    private BigDecimal totalPrice;

    public BookingNotificationEvent(String email, String status, Transaction transaction) {
        this.email = email;
        this.status = status;
        this.transaction = transaction;
    }
}
