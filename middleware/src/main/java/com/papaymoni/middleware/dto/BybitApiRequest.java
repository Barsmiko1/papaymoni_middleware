package com.papaymoni.middleware.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BybitApiRequest {
    private String endpoint;
    private String method;
    private Map<String, Object> payload;
}
