//package com.papaymoni.middleware.repository;
//
//import com.papaymoni.middleware.model.Currency;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.model.WalletBalance;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Lock;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.stereotype.Repository;
//
//import javax.persistence.LockModeType;
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {
//    Optional<WalletBalance> findByUserAndCurrency(User user, Currency currency);
//
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("SELECT wb FROM WalletBalance wb WHERE wb.user = ?1 AND wb.currency = ?2")
//
//
//    Optional<WalletBalance> findByUserAndCurrencyWithLock(User user, Currency currency);
//
//    List<WalletBalance> findByCurrencyIdEquals(Long currencyId);
//
//    List<WalletBalance> findByUser(User user);
//}

package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {

    List<WalletBalance> findByUser(User user);

    Optional<WalletBalance> findByUserAndCurrency(User user, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wb FROM WalletBalance wb WHERE wb.user = :user AND wb.currency = :currency")
    Optional<WalletBalance> findByUserAndCurrencyWithLock(@Param("user") User user, @Param("currency") Currency currency);

    // New methods using IDs directly
    @Query("SELECT wb FROM WalletBalance wb WHERE wb.user.id = :userId")
    List<WalletBalance> findByUserId(@Param("userId") Long userId);

    @Query("SELECT wb FROM WalletBalance wb WHERE wb.user.id = :userId AND wb.currency.id = :currencyId")
    Optional<WalletBalance> findByUserIdAndCurrencyId(@Param("userId") Long userId, @Param("currencyId") Long currencyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wb FROM WalletBalance wb WHERE wb.user.id = :userId AND wb.currency.id = :currencyId")
    Optional<WalletBalance> findByUserIdAndCurrencyIdWithLock(@Param("userId") Long userId, @Param("currencyId") Long currencyId);
}
