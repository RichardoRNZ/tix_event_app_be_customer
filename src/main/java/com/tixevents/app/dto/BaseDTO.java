package com.tixevents.app.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public abstract class BaseDTO {
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private String deletedBy;
    private LocalDateTime deletedAt;
}
