package com.tixevents.app.util;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JpaSpecificationBuilder<T> {

    public static <T> Specification<T> build(Map<String, Object> params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (params != null) {
                params.forEach((key, value) -> {
                    if (value != null && !value.toString().isEmpty()) {
                        // Basic equals comparison, can be extended for LIKE, IN, etc.
                        predicates.add(cb.equal(root.get(key), value));
                    }
                });
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
