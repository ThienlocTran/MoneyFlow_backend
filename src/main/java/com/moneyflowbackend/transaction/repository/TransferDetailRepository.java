package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.TransferDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferDetailRepository extends JpaRepository<TransferDetail, UUID> {
}
