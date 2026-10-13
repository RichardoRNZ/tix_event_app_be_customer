package com.tixevents.app.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionDTO extends BaseDTO {
    private Long id;
    private Long bookingId;
    private String paymentMethod;
    private String paymentStatus;
    private String bookingNo;
    private BigDecimal totalPrice;
    private String idempotencyKey;
}
