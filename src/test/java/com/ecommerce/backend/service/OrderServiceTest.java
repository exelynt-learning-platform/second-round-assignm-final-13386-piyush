package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.order.CreateOrderRequest;
import com.ecommerce.backend.dto.order.OrderResponse;
import com.ecommerce.backend.dto.order.ShippingDetailsRequest;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartService cartService;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("buyer@example.com").build();
        cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
    }

    @Test
    void createOrder_ShouldCreateOrderFromCartAndReduceStock() {
        Product product = Product.builder()
                .id(2L)
                .name("Jacket")
                .imageUrl("https://cdn.example.com/jacket.jpg")
                .price(BigDecimal.valueOf(75))
                .stockQuantity(5)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(22L)
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(75))
                .build();
        cart.getItems().add(cartItem);

        CreateOrderRequest request = new CreateOrderRequest(
                new ShippingDetailsRequest(
                        "221B Baker Street",
                        "",
                        "London",
                        "Greater London",
                        "NW1",
                        "UK",
                        "+44-555-0100"
                )
        );

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(cartService.getOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });
        doAnswer(invocation -> {
            Cart c = invocation.getArgument(0);
            c.getItems().clear();
            return null;
        }).when(cartService).clearCart(any(Cart.class));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalPrice()).isEqualByComparingTo("150");
        assertThat(response.items()).hasSize(1);
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(cart.getItems()).isEmpty();

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_ShouldRejectEmptyCart() {
        CreateOrderRequest request = new CreateOrderRequest(
                new ShippingDetailsRequest(
                        "221B Baker Street",
                        "",
                        "London",
                        "Greater London",
                        "NW1",
                        "UK",
                        "+44-555-0100"
                )
        );

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(cartService.getOrCreateCart(user)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty cart");
    }
}
