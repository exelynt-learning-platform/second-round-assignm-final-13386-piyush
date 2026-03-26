package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.cart.AddCartItemRequest;
import com.ecommerce.backend.dto.cart.CartResponse;
import com.ecommerce.backend.dto.cart.UpdateCartItemRequest;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.mapper.CartMapper;
import com.ecommerce.backend.repository.CartItemRepository;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ProductRepository;
import com.ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public CartResponse getMyCart() {
        User user = userService.getCurrentAuthenticatedUser();
        Cart cart = getOrCreateCart(user);
        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        validateAddItemRequest(request);

        User user = userService.getCurrentAuthenticatedUser();
        Cart cart = getOrCreateCartForUpdate(user);
        ensureCartItemsInitialized(cart);

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));

        ensureProductPurchasable(product);

        CartItem item = cartItemRepository.findByCartIdAndProductIdForUpdate(cart.getId(), product.getId())
                .orElseGet(() -> newCartItem(cart, product));
        int updatedQuantity = safeQuantity(item.getQuantity()) + request.quantity();
        ensureQuantityWithinStock(updatedQuantity, product);

        item.setQuantity(updatedQuantity);
        item.setUnitPrice(product.getPrice());
        CartItem savedItem = cartItemRepository.save(item);
        attachItemIfMissing(cart, savedItem);

        Cart updatedCart = cartRepository.findByUserId(user.getId()).orElse(cart);
        log.info("Cart item added/updated: userId={}, productId={}, quantity={}", user.getId(), product.getId(), updatedQuantity);
        return CartMapper.toResponse(updatedCart);
    }

    @Transactional
    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        validateUpdateItemRequest(request);

        User user = userService.getCurrentAuthenticatedUser();
        CartItem item = getItemForCurrentUser(itemId, user.getId(), true);

        ensureProductPurchasable(item.getProduct());
        ensureQuantityWithinStock(request.quantity(), item.getProduct());

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        Cart updatedCart = cartRepository.findByUserId(user.getId()).orElse(item.getCart());
        log.info("Cart item updated: userId={}, itemId={}, quantity={}", user.getId(), itemId, request.quantity());
        return CartMapper.toResponse(updatedCart);
    }

    @Transactional
    public CartResponse removeItem(Long itemId) {
        User user = userService.getCurrentAuthenticatedUser();
        CartItem item = getItemForCurrentUser(itemId, user.getId(), true);

        ensureCartItemsInitialized(item.getCart());
        item.getCart().getItems().remove(item);
        cartItemRepository.delete(item);

        Cart updatedCart = cartRepository.findByUserId(user.getId()).orElse(item.getCart());
        log.info("Cart item removed: userId={}, itemId={}", user.getId(), itemId);
        return CartMapper.toResponse(updatedCart);
    }

    @Transactional
    public void clearCurrentUserCart() {
        User user = userService.getCurrentAuthenticatedUser();
        Cart cart = getOrCreateCart(user);
        clearCart(cart);
        log.info("Cart cleared: userId={}", user.getId());
    }

    @Transactional
    public void clearCart(Cart cart) {
        if (cart == null) {
            throw new BadRequestException("Cart is required");
        }
        ensureCartItemsInitialized(cart);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    public Cart saveCart(Cart cart) {
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart getOrCreateCart(User user) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));
        ensureCartItemsInitialized(cart);
        return cart;
    }

    @Transactional
    public Cart getCurrentCartEntity() {
        User user = userService.getCurrentAuthenticatedUser();
        return getOrCreateCart(user);
    }

    private Cart getOrCreateCartForUpdate(User user) {
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));
        Cart cart = cartRepository.findByUserIdForUpdate(lockedUser.getId())
                .orElseGet(() -> createCart(lockedUser));
        ensureCartItemsInitialized(cart);
        return cart;
    }

    private Cart createCart(User user) {
        log.info("Creating cart for userId={}", user.getId());
        return cartRepository.save(Cart.builder().user(user).build());
    }

    private CartItem getItemForCurrentUser(Long itemId, Long userId, boolean forUpdate) {
        Optional<CartItem> itemQueryResult = forUpdate
                ? cartItemRepository.findByIdForUpdate(itemId)
                : cartItemRepository.findById(itemId);

        CartItem item = itemQueryResult
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));

        if (item.getCart() == null || item.getCart().getUser() == null || !item.getCart().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found with id: " + itemId);
        }

        return item;
    }

    private void ensureProductPurchasable(Product product) {
        if (product == null) {
            throw new BadRequestException("Product is invalid");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            throw new BadRequestException("Invalid stock configured for product: " + product.getName());
        }
        BigDecimal price = product.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invalid price configured for product: " + product.getName());
        }
    }

    private void validateAddItemRequest(AddCartItemRequest request) {
        if (request == null) {
            throw new BadRequestException("Add cart item request is required");
        }
        if (request.productId() == null) {
            throw new BadRequestException("Product id is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("Quantity must be at least 1");
        }
    }

    private void validateUpdateItemRequest(UpdateCartItemRequest request) {
        if (request == null) {
            throw new BadRequestException("Update cart item request is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("Quantity must be at least 1");
        }
    }

    private int safeQuantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private void ensureQuantityWithinStock(int quantity, Product product) {
        if (quantity > product.getStockQuantity()) {
            throw new BadRequestException("Insufficient stock for product: " + product.getName());
        }
    }

    private CartItem newCartItem(Cart cart, Product product) {
        return CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(0)
                .unitPrice(product.getPrice())
                .build();
    }

    private void attachItemIfMissing(Cart cart, CartItem item) {
        boolean alreadyAttached = cart.getItems().stream().anyMatch(existing ->
                existing == item || (existing.getId() != null && existing.getId().equals(item.getId())));
        if (!alreadyAttached) {
            cart.getItems().add(item);
        }
    }

    private void ensureCartItemsInitialized(Cart cart) {
        if (cart == null) {
            throw new BadRequestException("Cart is required");
        }
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }
    }
}
