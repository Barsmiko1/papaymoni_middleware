package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.PassimpayWallet;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassimpayWalletRepository extends JpaRepository<PassimpayWallet, Long> {

    @Query("SELECT pw FROM PassimpayWallet pw JOIN FETCH pw.user WHERE pw.user = :user AND pw.currency = :currency")
    Optional<PassimpayWallet> findByUserAndCurrency(@Param("user") User user, @Param("currency") String currency);

    @Query("SELECT pw FROM PassimpayWallet pw JOIN FETCH pw.user WHERE pw.user = :user")
    List<PassimpayWallet> findAllByUser(@Param("user") User user);

    @Query("SELECT pw FROM PassimpayWallet pw JOIN FETCH pw.user WHERE pw.orderId = :orderId")
    Optional<PassimpayWallet> findByOrderId(@Param("orderId") String orderId);

    @Query("SELECT pw FROM PassimpayWallet pw JOIN FETCH pw.user WHERE pw.address = :address")
    Optional<PassimpayWallet> findByAddress(@Param("address") String address);
}
