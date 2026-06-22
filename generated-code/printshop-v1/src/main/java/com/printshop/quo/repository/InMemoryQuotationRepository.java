package com.printshop.quo.repository;

import com.printshop.quo.dto.Quotation;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * QUO 模块 v1 内存仓储。
 */
@Repository
public class InMemoryQuotationRepository {

    private final ConcurrentHashMap<String, Quotation> quotations = new ConcurrentHashMap<>();

    public Quotation save(Quotation quotation) {
        quotations.put(quotation.quotationId(), quotation);
        return quotation;
    }

    public Optional<Quotation> findById(String quotationId) {
        return Optional.ofNullable(quotations.get(quotationId));
    }
}
