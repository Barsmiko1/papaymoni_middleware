package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class BybitApiResponse<T> {
    private int retCode;
    private String retMsg;
    private T result;
    private String timeNow;

    @JsonIgnore
    public boolean isSuccess() {
        return retCode == 0;
    }
}
