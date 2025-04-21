package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUser(User user);
    List<Order> findByUserAndStatus(User user, int status);
    List<Order> findByUserAndStatusIn(User user, List<Integer> statuses);
    List<Order> findByStatusAndSide(int status, int side);
    List<Order> findBySideAndStatus(int side, int status);
    List<Order> findByStatus(int status);
}
