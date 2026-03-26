package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeEventRepository extends JpaRepository<StripeEvent, Long> {

    Optional<StripeEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}