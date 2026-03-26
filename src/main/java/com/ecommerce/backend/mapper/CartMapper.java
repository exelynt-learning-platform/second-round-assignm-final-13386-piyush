package com.ecommerce.backend.mapper;

import com.ecommerce.backend.dto.cart.CartItemResponse;
import com.ecommerce.backend.dto.cart.CartResponse;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public final class CartMapper {

    private CartMapper() {
    }

    public static CartResponse toResponse(Cart cart) {
        List<CartItem> items = cart.getItems() == null ? List.of() : cart.getItems();
        List<CartItemResponse> itemResponses = items.stream()
                .map(CartMapper::toItemResponse)
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                cart.getId(),
                cart.getUser() != null ? cart.getUser().getId() : null,
                itemResponses,
                total,
                cart.getUpdatedAt()
        );
    }

    private static CartItemResponse toItemResponse(CartItem item) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
        String productName = item.getProduct() != null ? item.getProduct().getName() : null;
        String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;

        return new CartItemResponse(
                item.getId(),
                productId,
                productName,
                imageUrl,
                unitPrice,
                quantity,
                lineTotal
        );
    }
}
