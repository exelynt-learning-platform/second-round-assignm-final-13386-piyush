package com.ecommerce.backend.service;

import com.ecommerce.backend.config.StripeProperties;
import com.ecommerce.backend.dto.payment.CreatePaymentSessionRequest;
import com.ecommerce.backend.dto.payment.PaymentStatusResponse;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.enums.OrderStatus;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.PaymentException;
import com.ecommerce.backend.payment.StripeClientProvider;
import com.ecommerce.backend.repository.StripeEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private StripeEventRepository stripeEventRepository;

    @Mock
    private StripeClientProvider stripeClientProvider;

    @Test
    void createPaymentSession_ShouldFailWhenStripeKeyMissing() {
        PaymentService paymentService = new PaymentService(
                new StripeProperties("", "whsec_test"),
                stripeClientProvider,
                orderService,
                stripeEventRepository
        );
        when(stripeClientProvider.getDefaultClient())
                .thenThrow(new PaymentException("Stripe secret key is not configured"));

        assertThatThrownBy(() -> paymentService.createPaymentSession(
                new CreatePaymentSessionRequest(1L, "http://localhost/success", "http://localhost/cancel")
        )).isInstanceOf(PaymentException.class)
          .hasMessageContaining("Stripe secret key is not configured");
    }

    @Test
    void handlePaymentFailure_ShouldMarkOrderFailed() {
        Order order = Order.builder()
                .id(15L)
                .status(OrderStatus.PENDING)
                .paymentSessionId("cs_test_123")
                .build();

        PaymentService paymentService = new PaymentService(
                new StripeProperties("sk_test_123", "whsec_test"),
                stripeClientProvider,
                orderService,
                stripeEventRepository
        );

        when(orderService.getMyOrderEntity(15L)).thenReturn(order);

        PaymentStatusResponse response = paymentService.handlePaymentFailure(15L, "cs_test_123");

        assertThat(response.orderId()).isEqualTo(15L);
        assertThat(response.status()).isEqualTo("FAILED");
        verify(orderService).markOrderFailed(order);
    }

    @Test
    void handleWebhookEvent_ShouldFailWhenWebhookSecretMissing() {
        PaymentService paymentService = new PaymentService(
                new StripeProperties("sk_test_123", ""),
                stripeClientProvider,
                orderService,
                stripeEventRepository
        );

        assertThatThrownBy(() -> paymentService.handleWebhookEvent("{}", "sig"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Stripe webhook secret is not configured");
    }

    @Test
    void handlePaymentFailure_ShouldRejectPaidOrder() {
        Order order = Order.builder()
                .id(15L)
                .status(OrderStatus.PAID)
                .paymentSessionId("cs_test_123")
                .build();

        PaymentService paymentService = new PaymentService(
                new StripeProperties("sk_test_123", "whsec_test"),
                stripeClientProvider,
                orderService,
                stripeEventRepository
        );

        when(orderService.getMyOrderEntity(15L)).thenReturn(order);

        assertThatThrownBy(() -> paymentService.handlePaymentFailure(15L, "cs_test_123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Paid order cannot be marked as failed");
    }
}
