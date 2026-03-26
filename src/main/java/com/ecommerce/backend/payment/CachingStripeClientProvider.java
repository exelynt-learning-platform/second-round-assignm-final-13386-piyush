package com.ecommerce.backend.payment;

import com.ecommerce.backend.config.StripeProperties;
import com.ecommerce.backend.exception.PaymentException;
import com.stripe.StripeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ecommerce.backend.util.ValidationUtils.isBlank;

@Component
@RequiredArgsConstructor
public class CachingStripeClientProvider implements StripeClientProvider {

    private final StripeProperties stripeProperties;
    private final Map<String, StripeClient> clients = new ConcurrentHashMap<>();

    @Override
    public StripeClient getDefaultClient() {
        return getClient(stripeProperties.secretKey());
    }

    @Override
    public StripeClient getClient(String apiKey) {
        if (isBlank(apiKey)) {
            throw new PaymentException("Stripe secret key is not configured");
        }
        return clients.computeIfAbsent(apiKey.trim(), StripeClient::new);
    }
}
