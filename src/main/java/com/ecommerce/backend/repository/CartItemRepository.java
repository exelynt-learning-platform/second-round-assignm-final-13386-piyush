package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ci from CartItem ci where ci.cart.id = :cartId and ci.product.id = :productId")
    Optional<CartItem> findByCartIdAndProductIdForUpdate(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ci from CartItem ci join fetch ci.cart c join fetch c.user where ci.id = :itemId")
    Optional<CartItem> findByIdForUpdate(@Param("itemId") Long itemId);
}
