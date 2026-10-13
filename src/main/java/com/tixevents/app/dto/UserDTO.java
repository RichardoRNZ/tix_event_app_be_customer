package com.tixevents.app.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserDTO extends BaseDTO {
    private Long id;
    private String name;
    private String email;
    // Password deliberately omitted for security reasons
    private Boolean isAdmin;
    private Boolean blocked;
    private LocalDateTime blockedTime;
    private LocalDateTime verifiedAt;
    private Integer failedLoginCount;
}
