package com.papaymoni.middleware.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {
    private Long userId;
    private String type;
    private String title;
    private String message;
}
