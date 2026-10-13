package com.tixevents.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class BookingRequest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBookingRequest {
        @NotEmpty(message = "Seat details IDs cannot be empty")
        private List<Long> seatDetailsIds;

        @NotNull(message = "Idempotency key is required")
        private String idempotencyKey;

        private String eventName;

        private List<String> seatNumbers;

        private BigDecimal price;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRequest {
        @NotBlank(message = "No booking is required")
        private String noBooking;

        @NotNull(message = "isPaid is required")
        private Boolean isPaid;

        @NotBlank(message = "Idempotency key is required")
        private String idempotencyKey;

        private String paymentMethod;

        private BigDecimal totalPrice;
    }
}
