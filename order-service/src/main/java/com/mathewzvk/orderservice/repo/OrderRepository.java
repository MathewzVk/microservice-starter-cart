package com.mathewzvk.orderservice.repo;

import com.mathewzvk.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
