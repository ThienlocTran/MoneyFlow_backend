package com.moneyflowbackend.studentloan.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanPageResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanProjectionResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanRequest;
import com.moneyflowbackend.studentloan.dto.StudentLoanResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanStatusRequest;
import com.moneyflowbackend.studentloan.service.StudentLoanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/student-loans")
public class StudentLoanController {
    private final StudentLoanService studentLoanService;

    public StudentLoanController(StudentLoanService studentLoanService) {
        this.studentLoanService = studentLoanService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StudentLoanPageResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        StudentLoanPageResponse response = studentLoanService.list(
                workspaceId, studentLoanService.parseStatus(status), page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Student loans loaded", response));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<ApiResponse<StudentLoanResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan loaded",
                studentLoanService.get(workspaceId, loanId, currentUserId())));
    }

    @GetMapping("/{loanId}/projection")
    public ResponseEntity<ApiResponse<StudentLoanProjectionResponse>> projection(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId,
            @RequestParam(defaultValue = "false") boolean includeSchedule,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan projection loaded",
                studentLoanService.projection(workspaceId, loanId, includeSchedule, page, size, currentUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentLoanResponse>> create(
            @PathVariable UUID workspaceId,
            @RequestBody StudentLoanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan created",
                studentLoanService.create(workspaceId, request, currentUserId())));
    }

    @PutMapping("/{loanId}")
    public ResponseEntity<ApiResponse<StudentLoanResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId,
            @RequestBody StudentLoanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan updated",
                studentLoanService.update(workspaceId, loanId, request, currentUserId())));
    }

    @PostMapping("/{loanId}/status")
    public ResponseEntity<ApiResponse<StudentLoanResponse>> status(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId,
            @RequestBody StudentLoanStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan status updated",
                studentLoanService.transitionStatus(workspaceId, loanId, studentLoanService.parseStatus(request.getStatus()), currentUserId())));
    }

    @PostMapping("/{loanId}/mark-paid-off")
    public ResponseEntity<ApiResponse<StudentLoanResponse>> markPaidOff(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan marked paid off",
                studentLoanService.markPaidOff(workspaceId, loanId, currentUserId())));
    }

    @PostMapping("/{loanId}/archive")
    public ResponseEntity<ApiResponse<StudentLoanResponse>> archive(
            @PathVariable UUID workspaceId,
            @PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Student loan archived",
                studentLoanService.archive(workspaceId, loanId, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
