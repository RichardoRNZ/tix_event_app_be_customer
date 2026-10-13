package com.tixevents.app.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BookingOutboxDTO extends BaseDTO {
    private Long id;
    private Long bookingId;
    private Long eventId;
    private String eventType;
    private String payload;
    private String status;
}
