package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.VirtualAccountResponse;
import com.papaymoni.middleware.model.User;
import org.springframework.stereotype.Service;

@Service
public class VirtualAccountProviderService {

    public VirtualAccountResponse createVirtualAccount(User user, String currency) {
        // In a real implementation, this would call a third-party virtual account provider API
        // For now, we'll just return a mock response
        VirtualAccountResponse response = new VirtualAccountResponse();
        response.setAccountNumber("VA" + System.currentTimeMillis());
        response.setBankCode("057");
        response.setBankName("Papay Moni Bank");
        response.setAccountName(user.getFirstName() + " " + user.getLastName());
        return response;
    }
}
