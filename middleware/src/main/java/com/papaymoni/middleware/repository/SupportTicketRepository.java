package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.SupportTicket;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByEmail(String email);
    List<SupportTicket> findByUser(User user);
    List<SupportTicket> findByStatus(String status);
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
}
