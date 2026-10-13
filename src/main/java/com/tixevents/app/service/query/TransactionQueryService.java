package com.tixevents.app.service.query;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Map;
import com.tixevents.app.entity.Transaction;
import com.tixevents.app.repository.TransactionRepository;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {
    
    private final TransactionRepository repository;

    public Page<Transaction> findAll(Map<String, Object> params, Pageable pageable) {
        // Implementasi JPA Specification dapat dipanggil di sini
        return null;
    }
}
