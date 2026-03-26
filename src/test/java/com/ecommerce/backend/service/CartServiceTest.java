package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.cart.AddCartItemRequest;
import com.ecommerce.backend.dto.cart.CartResponse;
import com.ecommerce.backend.dto.cart.UpdateCartItemRequest;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.CartItemRepository;
import com.ecommerce.backend.repository.CartRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CartService cartService;

    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@example.com").build();
        cart = Cart.builder().id(100L).user(user).items(new ArrayList<>()).build();
    }

    @Test
    void addItem_ShouldAddProductToCart() {
        Product product = Product.builder()
                .id(5L)
                .name("Shoes")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .imageUrl("https://cdn.example.com/shoes.jpg")
                .build();

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(100L, 5L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            item.setId(200L);
            return item;
        });

        CartResponse response = cartService.addItem(new AddCartItemRequest(5L, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("199.98");
    }

    @Test
    void updateItem_ShouldChangeQuantity() {
        Product product = Product.builder()
                .id(5L)
                .name("Shoes")
                .price(BigDecimal.valueOf(50))
                .stockQuantity(10)
                .imageUrl("https://cdn.example.com/shoes.jpg")
                .build();

        CartItem item = CartItem.builder()
                .id(201L)
                .cart(cart)
                .product(product)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(50))
                .build();
        cart.getItems().add(item);

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(cartItemRepository.findById(201L)).thenReturn(Optional.of(item));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.updateItem(201L, new UpdateCartItemRequest(3));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("150");
    }

    @Test
    void addItem_ShouldIncreaseQuantityWhenProductAlreadyExistsInCart() {
        Product product = Product.builder()
                .id(5L)
                .name("Shoes")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .imageUrl("https://cdn.example.com/shoes.jpg")
                .build();

        CartItem existingItem = CartItem.builder()
                .id(200L)
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(99.99))
                .build();
        cart.getItems().add(existingItem);

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(100L, 5L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(new AddCartItemRequest(5L, 3));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.totalAmount()).isEqualByComparingTo("499.95");
    }
}
