package com.ecommerce.backend.service;

import com.ecommerce.backend.config.StripeProperties;
import com.ecommerce.backend.dto.payment.CreatePaymentSessionRequest;
import com.ecommerce.backend.dto.payment.PaymentSessionResponse;
import com.ecommerce.backend.dto.payment.PaymentStatusResponse;
import com.ecommerce.backend.dto.payment.StripeWebhookResponse;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.OrderItem;
import com.ecommerce.backend.entity.StripeEvent;
import com.ecommerce.backend.entity.enums.OrderStatus;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.PaymentException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.StripeEventRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeProperties stripeProperties;
    private final OrderService orderService;
    private final StripeEventRepository stripeEventRepository;

    @PostConstruct
    public void init() {
        if (stripeProperties.secretKey() != null && !stripeProperties.secretKey().isBlank()) {
            Stripe.apiKey = stripeProperties.secretKey();
        }
    }

    @Transactional
    public PaymentSessionResponse createPaymentSession(CreatePaymentSessionRequest request) {
        validateCreatePaymentSessionRequest(request);

        if (stripeProperties.secretKey() == null || stripeProperties.secretKey().isBlank()) {
            throw new PaymentException("Stripe secret key is not configured");
        }

        Order order = orderService.getMyOrderEntity(request.orderId());

        if (order.getStatus() == OrderStatus.PAID) {
            throw new BadRequestException("Order is already paid");
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            throw new BadRequestException("Failed order cannot create a payment session");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BadRequestException("Order does not contain any items");
        }

        String successUrl = request.successUrl().trim();
        String cancelUrl = request.cancelUrl().trim();

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}&order_id=" + order.getId())
                .setCancelUrl(cancelUrl + "?order_id=" + order.getId())
                .putMetadata("orderId", String.valueOf(order.getId()));

        for (OrderItem item : order.getItems()) {
            if (item == null || item.getProduct() == null) {
                throw new BadRequestException("Order contains invalid items");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Order item quantity is invalid");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Order item price is invalid");
            }

            long unitAmountInCents = toCents(item.getUnitPrice());
            String productName = isBlank(item.getProduct().getName())
                    ? "Product-" + item.getProduct().getId()
                    : item.getProduct().getName();

            SessionCreateParams.LineItem.PriceData.ProductData.Builder productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(productName);

            if (item.getProduct().getImageUrl() != null && !item.getProduct().getImageUrl().isBlank()) {
                productData.addImage(item.getProduct().getImageUrl());
            }

            params.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(item.getQuantity().longValue())
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("usd")
                                            .setUnitAmount(unitAmountInCents)
                                            .setProductData(productData.build())
                                            .build()
                            )
                            .build()
            );
        }

        try {
            Session session = Session.create(params.build());
            order.setPaymentSessionId(session.getId());
            orderService.saveOrder(order);

            log.info("Payment session created: orderId={}, sessionId={}", order.getId(), session.getId());

            return new PaymentSessionResponse(
                    session.getId(),
                    session.getUrl(),
                    order.getId(),
                    order.getStatus().name()
            );
        } catch (StripeException ex) {
            throw new PaymentException("Unable to create Stripe checkout session", ex);
        }
    }

    @Transactional
    public PaymentStatusResponse handlePaymentSuccess(Long orderId, String sessionId) {
        Order order = orderService.getMyOrderEntity(orderId);

        if (order.getStatus() == OrderStatus.PAID) {
            return new PaymentStatusResponse(order.getId(), OrderStatus.PAID.name(), "Order is already paid");
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            throw new BadRequestException("Failed order cannot be marked as paid");
        }
        if (isBlank(sessionId)) {
            throw new BadRequestException("Session id is required");
        }

        if (order.getPaymentSessionId() == null || !order.getPaymentSessionId().equals(sessionId)) {
            throw new BadRequestException("Invalid payment session for this order");
        }
        if (stripeProperties.secretKey() == null || stripeProperties.secretKey().isBlank()) {
            throw new PaymentException("Stripe secret key is not configured");
        }

        try {
            Session session = Session.retrieve(sessionId);
            if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BadRequestException("Payment is not confirmed by Stripe");
            }

            orderService.markOrderPaid(order, session.getPaymentIntent());
            return new PaymentStatusResponse(order.getId(), OrderStatus.PAID.name(), "Payment successful");
        } catch (StripeException ex) {
            throw new PaymentException("Unable to verify Stripe payment session", ex);
        }
    }

    @Transactional
    public PaymentStatusResponse handlePaymentFailure(Long orderId, String sessionId) {
        Order order = orderService.getMyOrderEntity(orderId);

        if (order.getStatus() == OrderStatus.PAID) {
            throw new BadRequestException("Paid order cannot be marked as failed");
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            return new PaymentStatusResponse(order.getId(), OrderStatus.FAILED.name(), "Order is already marked as failed");
        }

        if (!isBlank(sessionId)) {
            if (isBlank(order.getPaymentSessionId()) || !order.getPaymentSessionId().equals(sessionId)) {
                throw new BadRequestException("Invalid payment session for this order");
            }
        }

        orderService.markOrderFailed(order);
        return new PaymentStatusResponse(order.getId(), OrderStatus.FAILED.name(), "Payment failed or cancelled");
    }

    @Transactional
    public StripeWebhookResponse handleWebhookEvent(String payload, String signatureHeader) {
        if (stripeProperties.webhookSecret() == null || stripeProperties.webhookSecret().isBlank()) {
            throw new PaymentException("Stripe webhook secret is not configured");
        }
        if (isBlank(payload)) {
            throw new PaymentException("Webhook payload is required");
        }
        if (isBlank(signatureHeader)) {
            throw new PaymentException("Stripe signature header is required");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret());
        } catch (Exception ex) {
            throw new PaymentException("Invalid Stripe webhook signature", ex);
        }

        if (stripeEventRepository.existsByEventId(event.getId())) {
            return new StripeWebhookResponse(event.getType(), "ignored", "Event already processed");
        }

        StripeEvent stripeEvent = StripeEvent.builder()
                .eventId(event.getId())
                .type(event.getType())
                .payload(payload)
                .processed(false)
                .build();
        try {
            stripeEventRepository.save(stripeEvent);
        } catch (DataIntegrityViolationException ex) {
            return new StripeWebhookResponse(event.getType(), "ignored", "Event already processed");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        String status = "processed";
        String message = "Webhook processed successfully";

        Optional<com.stripe.model.StripeObject> deserializedObject = deserializer.getObject();
        if (deserializedObject.isEmpty() || !(deserializedObject.get() instanceof Session)) {
            status = "ignored";
            message = "Unsupported webhook payload";
            markStripeEventProcessed(stripeEvent);
            return new StripeWebhookResponse(event.getType(), status, message);
        }
        Session session = (Session) deserializedObject.get();

        String sessionId = session.getId();
        String paymentIntentId = session.getPaymentIntent();

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
                        orderService.markOrderPaidBySessionId(sessionId, paymentIntentId);
                    } else {
                        status = "ignored";
                        message = "Checkout session completed without paid status";
                    }
                }
                case "checkout.session.expired", "checkout.session.async_payment_failed" ->
                        orderService.markOrderFailedBySessionId(sessionId);
                default -> {
                    status = "ignored";
                    message = "Event type not handled";
                }
            }
        } catch (ResourceNotFoundException | BadRequestException ex) {
            status = "ignored";
            message = ex.getMessage();
        }

        markStripeEventProcessed(stripeEvent);

        log.info("Stripe webhook processed: eventType={}, eventId={}", event.getType(), event.getId());
        return new StripeWebhookResponse(event.getType(), status, message);
    }

    private long toCents(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Order item price has invalid precision");
        }
    }

    private void validateCreatePaymentSessionRequest(CreatePaymentSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("Create payment session request is required");
        }
        if (request.orderId() == null) {
            throw new BadRequestException("Order id is required");
        }
        if (isBlank(request.successUrl())) {
            throw new BadRequestException("Success URL is required");
        }
        if (isBlank(request.cancelUrl())) {
            throw new BadRequestException("Cancel URL is required");
        }
    }

    private void markStripeEventProcessed(StripeEvent stripeEvent) {
        stripeEvent.setProcessed(true);
        stripeEvent.setProcessedAt(Instant.now());
        stripeEventRepository.save(stripeEvent);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
