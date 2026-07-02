package com.moneyflowbackend.transaction.audit;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.transaction.audit.dto.TransactionAuditResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionAuditController {
    private final TransactionAuditService transactionAuditService;

    public TransactionAuditController(TransactionAuditService transactionAuditService) {
        this.transactionAuditService = transactionAuditService;
    }

    @GetMapping("/{transactionId}/audit")
    public ResponseEntity<ApiResponse<List<TransactionAuditResponse>>> list(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Transaction audit loaded",
                transactionAuditService.listByTransactionId(transactionId, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
