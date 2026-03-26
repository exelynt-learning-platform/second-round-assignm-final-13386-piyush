package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.common.ApiResponse;
import com.ecommerce.backend.dto.payment.CreatePaymentSessionRequest;
import com.ecommerce.backend.dto.payment.PaymentSessionResponse;
import com.ecommerce.backend.dto.payment.PaymentStatusResponse;
import com.ecommerce.backend.dto.payment.StripeWebhookResponse;
import com.ecommerce.backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/session")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentSessionResponse>> createPaymentSession(
            @Valid @RequestBody CreatePaymentSessionRequest request
    ) {
        PaymentSessionResponse response = paymentService.createPaymentSession(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment session created", response));
    }

    @PostMapping("/{orderId}/success")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> handleSuccess(
            @PathVariable Long orderId,
            @RequestParam String sessionId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payment status updated",
                paymentService.handlePaymentSuccess(orderId, sessionId)));
    }

    @PostMapping("/{orderId}/failure")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> handleFailure(
            @PathVariable Long orderId,
            @RequestParam(required = false) String sessionId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payment status updated",
                paymentService.handlePaymentFailure(orderId, sessionId)));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<StripeWebhookResponse>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        StripeWebhookResponse response = paymentService.handleWebhookEvent(payload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook handled", response));
    }
}
