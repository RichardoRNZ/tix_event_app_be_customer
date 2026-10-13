package com.tixevents.app.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BookingDetailDTO extends BaseDTO {
    private Long id;
    private Long bookingId;
    private Long seatDetailsId;
    private String seatNumber;
    private BigDecimal price;
    private Boolean isActive;
}
