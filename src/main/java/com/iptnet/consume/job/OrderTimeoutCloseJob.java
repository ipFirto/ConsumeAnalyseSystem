package com.iptnet.consume.job;

import com.iptnet.consume.service.transactionOrder.TransactionOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutCloseJob {

    private final TransactionOrderService transactionOrderService;

    @Scheduled(fixedDelayString = "${business.order.close-scan-ms:30000}")
    public void closeExpiredOrders() {
        int rows = transactionOrderService.closeExpiredOrders();
        if (rows > 0) {
            log.info("scheduled timeout close finished, rows={}", rows);
        }
    }
}
