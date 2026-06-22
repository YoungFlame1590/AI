package com.printshop.ord.repository;

import com.printshop.ord.dto.Order;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * ORD 模块 v1 内存仓储。
 */
@Repository
public class InMemoryOrderRepository {

    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.orderId(), order);
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public Collection<Order> findAll() {
        return orders.values();
    }
}
