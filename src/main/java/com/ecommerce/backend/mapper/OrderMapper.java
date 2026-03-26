package com.ecommerce.backend.mapper;

import com.ecommerce.backend.dto.order.OrderItemResponse;
import com.ecommerce.backend.dto.order.OrderResponse;
import com.ecommerce.backend.dto.order.ShippingDetailsResponse;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
        List<OrderItemResponse> items = orderItems.stream()
                .map(OrderMapper::toItemResponse)
                .toList();

        String addressLine1 = order.getShippingDetails() != null ? order.getShippingDetails().getAddressLine1() : null;
        String addressLine2 = order.getShippingDetails() != null ? order.getShippingDetails().getAddressLine2() : null;
        String city = order.getShippingDetails() != null ? order.getShippingDetails().getCity() : null;
        String state = order.getShippingDetails() != null ? order.getShippingDetails().getState() : null;
        String postalCode = order.getShippingDetails() != null ? order.getShippingDetails().getPostalCode() : null;
        String country = order.getShippingDetails() != null ? order.getShippingDetails().getCountry() : null;
        String phone = order.getShippingDetails() != null ? order.getShippingDetails().getPhone() : null;

        ShippingDetailsResponse shippingDetails = new ShippingDetailsResponse(
                addressLine1,
                addressLine2,
                city,
                state,
                postalCode,
                country,
                phone
        );

        return new OrderResponse(
                order.getId(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice(),
                shippingDetails,
                items,
                order.getPaymentSessionId(),
                order.getCreatedAt()
        );
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
        String productName = item.getProduct() != null ? item.getProduct().getName() : null;
        String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;

        return new OrderItemResponse(
                item.getId(),
                productId,
                productName,
                imageUrl,
                quantity,
                unitPrice,
                lineTotal
        );
    }
}
