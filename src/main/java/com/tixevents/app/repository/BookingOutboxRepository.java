package com.tixevents.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tixevents.app.entity.BookingOutbox;

@Repository
public interface BookingOutboxRepository extends JpaRepository<BookingOutbox, Long> {
}
