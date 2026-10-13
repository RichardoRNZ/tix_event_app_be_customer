package com.tixevents.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BookingDTO extends BaseDTO {
    private Long id;
    private Long userId;
    private String noBooking;
    private LocalDateTime date;
    private String status;
    private String userName;
    private String eventName;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private BigDecimal totalPrice;
    private List<BookingDetailDTO> bookingDetails;

    public BigDecimal getTotalPrice() {
        if (totalPrice != null) {
            return totalPrice;
        }
        if (bookingDetails != null) {
            return bookingDetails.stream()
                    .map(BookingDetailDTO::getPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return BigDecimal.ZERO;
    }
}
