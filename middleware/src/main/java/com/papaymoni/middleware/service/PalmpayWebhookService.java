package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;

public interface PalmpayWebhookService {
    /**
     * Process a pay-in webhook from Palmpay
     * @param webhookDto The webhook data from Palmpay
     * @return true if processed successfully
     */
    boolean processPayinWebhook(PalmpayPayinWebhookDto webhookDto);

    /**
     * Verify the signature from Palmpay webhook
     * @param webhookDto The webhook data containing the signature
     * @return true if signature is valid
     */
    boolean verifyWebhookSignature(PalmpayPayinWebhookDto webhookDto);
}