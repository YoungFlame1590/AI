package com.printshop.fin.repository;

import com.printshop.fin.dto.Invoice;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * FIN 模块 v1 内存仓储。
 */
@Repository
public class InMemoryInvoiceRepository {

    private final ConcurrentHashMap<String, Invoice> invoices = new ConcurrentHashMap<>();

    public Invoice save(Invoice invoice) {
        invoices.put(invoice.invoiceId(), invoice);
        return invoice;
    }

    public Optional<Invoice> findById(String invoiceId) {
        return Optional.ofNullable(invoices.get(invoiceId));
    }
}
