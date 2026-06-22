package com.printshop.infra.gateway;

import org.springframework.stereotype.Component;

/**
 * 税务发票接口防腐层占位适配器。
 */
@Component
public class TaxInvoiceAdapter {

    public String issueInvoice(String invoiceId) {
        return "TAX-" + invoiceId;
    }
}
