package com.moneyflowbackend.receipt.session.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ReceiptImageStorageService {
    boolean isEnabled();

    StoredReceiptImage upload(String objectKey, MultipartFile file);
}
