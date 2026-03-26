package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.order.CreateOrderRequest;
import com.ecommerce.backend.dto.order.OrderResponse;
import com.ecommerce.backend.dto.order.ShippingDetailsRequest;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.OrderItem;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.ShippingDetails;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.entity.enums.OrderStatus;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.mapper.OrderMapper;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.backend.util.ValidationUtils.isBlank;
import static com.ecommerce.backend.util.ValidationUtils.requireNonBlank;
import static com.ecommerce.backend.util.ValidationUtils.requireNonNull;
import static com.ecommerce.backend.util.ValidationUtils.requirePositive;
import static com.ecommerce.backend.util.ValidationUtils.trimToNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final UserService userService;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        ShippingDetailsRequest shippingDetails = validateCreateOrderRequest(request);

        User user = userService.getCurrentAuthenticatedUser();
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot create order from an empty cart");
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingDetails(mapShipping(shippingDetails))
                .items(new ArrayList<>())
                .totalPrice(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        List<CartItem> cartItems = new ArrayList<>(cart.getItems());

        try {
            for (CartItem cartItem : cartItems) {
                validateCartItem(cartItem);

                Product lockedProduct = productRepository.findByIdForUpdate(cartItem.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + cartItem.getProduct().getId()));

                int requestedQuantity = cartItem.getQuantity();
                int currentStock = safeStock(lockedProduct);
                BigDecimal unitPrice = safePrice(lockedProduct);

                if (currentStock < requestedQuantity) {
                    throw new BadRequestException("Insufficient stock for product: " + lockedProduct.getName());
                }

                lockedProduct.setStockQuantity(currentStock - requestedQuantity);
                productRepository.save(lockedProduct);

                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .product(lockedProduct)
                        .quantity(requestedQuantity)
                        .unitPrice(unitPrice)
                        .build();

                order.getItems().add(orderItem);
                total = total.add(unitPrice.multiply(BigDecimal.valueOf(requestedQuantity)));
            }
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new BadRequestException("Inventory changed, please retry checkout");
        }

        order.setTotalPrice(total);

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(cart);

        log.info("Order created: orderId={}, userId={}, total={}", savedOrder.getId(), user.getId(), savedOrder.getTotalPrice());
        return OrderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        User user = userService.getCurrentAuthenticatedUser();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(Long orderId) {
        User user = userService.getCurrentAuthenticatedUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Order getMyOrderEntity(Long orderId) {
        User user = userService.getCurrentAuthenticatedUser();
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByPaymentSessionId(String sessionId) {
        return orderRepository.findByPaymentSessionId(sessionId);
    }

    @Transactional
    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public void markOrderPaidBySessionId(String sessionId, String paymentIntentId) {
        String normalizedSessionId = requireNonBlank(sessionId, "Payment session id is required");
        Order order = orderRepository.findByPaymentSessionId(normalizedSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for payment session: " + normalizedSessionId));
        markOrderPaid(order, paymentIntentId);
    }

    @Transactional
    public void markOrderFailedBySessionId(String sessionId) {
        String normalizedSessionId = requireNonBlank(sessionId, "Payment session id is required");
        Order order = orderRepository.findByPaymentSessionId(normalizedSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for payment session: " + normalizedSessionId));
        markOrderFailed(order);
    }

    @Transactional
    public void markOrderPaid(Order order, String paymentIntentId) {
        requireNonNull(order, "Order is required");
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            throw new BadRequestException("Cannot mark a failed order as paid");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaymentIntentId(paymentIntentId);
        orderRepository.save(order);
        log.info("Order marked paid: orderId={}, paymentIntentId={}", order.getId(), paymentIntentId);
    }

    @Transactional
    public void markOrderFailed(Order order) {
        requireNonNull(order, "Order is required");
        if (order.getStatus() == OrderStatus.FAILED) {
            return;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BadRequestException("Cannot mark a paid order as failed");
        }

        restoreStock(order);
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.info("Order marked failed: orderId={}", order.getId());
    }

    private void restoreStock(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getProduct().getId() == null) {
                throw new BadRequestException("Order item product is invalid");
            }
            int quantityToRestore = requirePositive(item.getQuantity(), "Order item quantity is invalid");

            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + item.getProduct().getId()));

            int currentStock = safeStock(product);
            product.setStockQuantity(currentStock + quantityToRestore);
            productRepository.save(product);
        }
    }

    private int safeStock(Product product) {
        if (product == null) {
            throw new BadRequestException("Product is invalid");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            throw new BadRequestException("Invalid stock configured for product: " + product.getName());
        }
        return product.getStockQuantity();
    }

    private BigDecimal safePrice(Product product) {
        if (product == null) {
            throw new BadRequestException("Product is invalid");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invalid price configured for product: " + product.getName());
        }
        return product.getPrice();
    }

    private ShippingDetails mapShipping(ShippingDetailsRequest shipping) {
        return ShippingDetails.builder()
                .addressLine1(requireNonBlank(shipping.addressLine1(), "Shipping details are incomplete"))
                .addressLine2(trimToNull(shipping.addressLine2()))
                .city(requireNonBlank(shipping.city(), "Shipping details are incomplete"))
                .state(requireNonBlank(shipping.state(), "Shipping details are incomplete"))
                .postalCode(requireNonBlank(shipping.postalCode(), "Shipping details are incomplete"))
                .country(requireNonBlank(shipping.country(), "Shipping details are incomplete"))
                .phone(trimToNull(shipping.phone()))
                .build();
    }

    private ShippingDetailsRequest validateCreateOrderRequest(CreateOrderRequest request) {
        CreateOrderRequest createOrderRequest = requireNonNull(request, "Create order request is required");
        ShippingDetailsRequest shipping = requireNonNull(createOrderRequest.shippingDetails(), "Shipping details are required");

        if (isBlank(shipping.addressLine1())
                || isBlank(shipping.city())
                || isBlank(shipping.state())
                || isBlank(shipping.postalCode())
                || isBlank(shipping.country())) {
            throw new BadRequestException("Shipping details are incomplete");
        }
        return shipping;
    }

    private void validateCartItem(CartItem cartItem) {
        if (cartItem == null || cartItem.getProduct() == null || cartItem.getProduct().getId() == null) {
            throw new BadRequestException("Cart contains an invalid product item");
        }
        requirePositive(cartItem.getQuantity(), "Cart contains an invalid quantity");
    }
}

