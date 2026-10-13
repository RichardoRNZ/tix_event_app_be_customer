package com.tixevents.app.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OtpIssuedEvent {
    private String email;
    private String otpCode;
}
