package com.tixevents.app.service.query;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.entity.User;
import com.tixevents.app.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserQueryService {
    
    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "name", "email", "blocked", "isAdmin");
    private final UserRepository repository;

    private Specification<User> buildSpecification(Map<String, Object> params) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (ALLOWED_FIELDS.contains(entry.getKey()) && entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Page<User> findAll(Map<String, Object> params, Pageable pageable) {
        Specification<User> spec = buildSpecification(params);
        return repository.findAll(spec, pageable);
    }

    public User getUser(Map<String, Object> params) {
        Specification<User> spec = buildSpecification(params);
        return repository.findOne(spec)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with given criteria"));
    }
}
