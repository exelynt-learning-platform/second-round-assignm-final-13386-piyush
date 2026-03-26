package com.ecommerce.backend.payment;

import com.stripe.StripeClient;

public interface StripeClientProvider {

    StripeClient getDefaultClient();

    StripeClient getClient(String apiKey);
}
