package com.tixevents.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDTO {
    private String to;
    private String subject;
    private String text;
    private boolean useHtml;
}
