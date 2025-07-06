package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PassimpayWebhookDto {
    // Core fields that we need to access directly
    private Integer platformId;
    private Integer paymentId;
    private String orderId;
    private String amount;
    private String txhash;
    private String addressFrom;
    private String addressTo;

    // Dynamic properties map to store any additional fields
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    // Helper methods to access common optional fields
    public Integer getConfirmations() {
        return (Integer) additionalProperties.get("confirmations");
    }

    public Integer getDestinationTag() {
        return (Integer) additionalProperties.get("destinationTag");
    }
}

