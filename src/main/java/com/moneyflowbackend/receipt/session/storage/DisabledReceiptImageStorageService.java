package com.moneyflowbackend.receipt.session.storage;

import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public class DisabledReceiptImageStorageService implements ReceiptImageStorageService {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public StoredReceiptImage upload(String objectKey, MultipartFile file) {
        throw new BusinessException("RECEIPT_STORAGE_NOT_CONFIGURED", "Receipt image storage is not configured", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
