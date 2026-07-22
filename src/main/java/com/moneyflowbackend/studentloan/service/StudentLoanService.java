package com.moneyflowbackend.studentloan.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.studentloan.dto.StudentLoanPageResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanRequest;
import com.moneyflowbackend.studentloan.dto.StudentLoanResponse;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import com.moneyflowbackend.studentloan.repository.StudentLoanRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class StudentLoanService {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_LENDER_LENGTH = 160;
    private static final int MAX_PAGE_SIZE = 100;

    private final StudentLoanRepository studentLoanRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final StudentLoanProjectionCalculator projectionCalculator = new StudentLoanProjectionCalculator();

    public StudentLoanService(
            StudentLoanRepository studentLoanRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository) {
        this.studentLoanRepository = studentLoanRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public StudentLoanPageResponse list(UUID workspaceId, StudentLoanStatus status, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        StudentLoanStatus effectiveStatus = status == null ? StudentLoanStatus.ACTIVE : status;
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id")));
        Page<StudentLoan> loans = studentLoanRepository.findAllByWorkspaceIdAndStatus(workspaceId, effectiveStatus, pageRequest);
        return StudentLoanPageResponse.builder()
                .items(loans.stream().map(this::mapToResponse).toList())
                .page(loans.getNumber())
                .size(loans.getSize())
                .totalElements(loans.getTotalElements())
                .totalPages(loans.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public StudentLoanResponse get(UUID workspaceId, UUID loanId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return mapToResponse(findInWorkspace(workspaceId, loanId));
    }

    @Transactional(readOnly = true)
    public com.moneyflowbackend.studentloan.dto.StudentLoanProjectionResponse projection(
            UUID workspaceId, UUID loanId, boolean includeSchedule, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        StudentLoan loan = findInWorkspace(workspaceId, loanId);
        return projectionCalculator.project(
                loan.getId(),
                loan.getCurrentPrincipal(),
                loan.getAnnualInterestRate(),
                loan.getMinimumMonthlyPayment(),
                loan.getPlannedExtraMonthlyPayment(),
                loan.getStartDate(),
                includeSchedule,
                page,
                size);
    }

    @Transactional
    public StudentLoanResponse create(UUID workspaceId, StudentLoanRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedRequest v = validate(req);
        StudentLoan loan = StudentLoan.builder()
                .workspace(member.getWorkspace())
                .name(v.name())
                .lender(v.lender())
                .originalPrincipal(v.originalPrincipal())
                .currentPrincipal(v.currentPrincipal())
                .annualInterestRate(v.annualInterestRate())
                .minimumMonthlyPayment(v.minimumMonthlyPayment())
                .plannedExtraMonthlyPayment(v.plannedExtraMonthlyPayment())
                .startDate(v.startDate())
                .targetPayoffDate(v.targetPayoffDate())
                .status(StudentLoanStatus.ACTIVE)
                .createdByUser(user)
                .build();
        return mapToResponse(studentLoanRepository.saveAndFlush(loan));
    }

    @Transactional
    public StudentLoanResponse update(UUID workspaceId, UUID loanId, StudentLoanRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        StudentLoan loan = findInWorkspaceForUpdate(workspaceId, loanId);
        if (loan.getStatus() == StudentLoanStatus.ARCHIVED) {
            throw new BusinessException("STUDENT_LOAN_ARCHIVED", "Archived student loan cannot be updated", HttpStatus.CONFLICT);
        }
        if (loan.getStatus() == StudentLoanStatus.PAID_OFF) {
            throw new BusinessException("STUDENT_LOAN_PAID_OFF", "Paid-off student loan cannot be updated", HttpStatus.CONFLICT);
        }
        ValidatedRequest v = validate(req);
        loan.setName(v.name());
        loan.setLender(v.lender());
        loan.setOriginalPrincipal(v.originalPrincipal());
        loan.setCurrentPrincipal(v.currentPrincipal());
        loan.setAnnualInterestRate(v.annualInterestRate());
        loan.setMinimumMonthlyPayment(v.minimumMonthlyPayment());
        loan.setPlannedExtraMonthlyPayment(v.plannedExtraMonthlyPayment());
        loan.setStartDate(v.startDate());
        loan.setTargetPayoffDate(v.targetPayoffDate());
        loan.setUpdatedAt(Instant.now());
        return mapToResponse(studentLoanRepository.saveAndFlush(loan));
    }

    @Transactional
    public StudentLoanResponse transitionStatus(UUID workspaceId, UUID loanId, StudentLoanStatus status, UUID userId) {
        requireWritableMember(workspaceId, userId);
        StudentLoan loan = findInWorkspaceForUpdate(workspaceId, loanId);
        if (loan.getStatus() == StudentLoanStatus.ARCHIVED) {
            throw new BusinessException("STUDENT_LOAN_ARCHIVED", "Archived student loan status cannot be changed", HttpStatus.CONFLICT);
        }
        if (loan.getStatus() == StudentLoanStatus.PAID_OFF) {
            throw new BusinessException("STUDENT_LOAN_PAID_OFF", "Paid-off student loan status cannot be changed", HttpStatus.CONFLICT);
        }
        if (status == StudentLoanStatus.ACTIVE || status == StudentLoanStatus.PAUSED) {
            loan.setStatus(status);
            loan.setUpdatedAt(Instant.now());
            return mapToResponse(studentLoanRepository.saveAndFlush(loan));
        }
        throw new BusinessException("INVALID_STUDENT_LOAN_STATUS", "Student loan status transition is invalid");
    }

    @Transactional
    public StudentLoanResponse markPaidOff(UUID workspaceId, UUID loanId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        StudentLoan loan = findInWorkspaceForUpdate(workspaceId, loanId);
        if (loan.getStatus() == StudentLoanStatus.ARCHIVED) {
            throw new BusinessException("STUDENT_LOAN_ARCHIVED", "Archived student loan cannot be marked paid off", HttpStatus.CONFLICT);
        }
        loan.setCurrentPrincipal(BigDecimal.ZERO);
        loan.setStatus(StudentLoanStatus.PAID_OFF);
        loan.setUpdatedAt(Instant.now());
        return mapToResponse(studentLoanRepository.saveAndFlush(loan));
    }

    @Transactional
    public StudentLoanResponse archive(UUID workspaceId, UUID loanId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        StudentLoan loan = findInWorkspaceForUpdate(workspaceId, loanId);
        loan.setStatus(StudentLoanStatus.ARCHIVED);
        loan.setUpdatedAt(Instant.now());
        return mapToResponse(studentLoanRepository.saveAndFlush(loan));
    }

    public StudentLoanStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StudentLoanStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_STUDENT_LOAN_STATUS", "Student loan status is invalid");
        }
    }

    private ValidatedRequest validate(StudentLoanRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        String name = normalize(req.getName());
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw validation("name", "Student loan name is invalid");
        }
        String lender = normalize(req.getLender());
        if (lender.isBlank()) {
            lender = null;
        } else if (lender.length() > MAX_LENDER_LENGTH) {
            throw validation("lender", "Student loan lender is invalid");
        }
        BigDecimal currentPrincipal = requirePositive(req.getCurrentPrincipal(), "currentPrincipal");
        BigDecimal annualInterestRate = requireNonNegative(req.getAnnualInterestRate(), "annualInterestRate");
        BigDecimal minimumMonthlyPayment = requirePositive(req.getMinimumMonthlyPayment(), "minimumMonthlyPayment");
        BigDecimal originalPrincipal = optionalNonNegative(req.getOriginalPrincipal(), "originalPrincipal");
        BigDecimal plannedExtraMonthlyPayment = optionalNonNegative(req.getPlannedExtraMonthlyPayment(), "plannedExtraMonthlyPayment");
        if (req.getTargetPayoffDate() != null && req.getStartDate() != null && req.getTargetPayoffDate().isBefore(req.getStartDate())) {
            throw validation("targetPayoffDate", "Target payoff date must be on or after start date");
        }
        return new ValidatedRequest(name, lender, originalPrincipal, currentPrincipal, annualInterestRate,
                minimumMonthlyPayment, plannedExtraMonthlyPayment, req.getStartDate(), req.getTargetPayoffDate());
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw validation(field, field + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw validation(field, field + " must be zero or greater");
        }
        return value;
    }

    private BigDecimal optionalNonNegative(BigDecimal value, String field) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw validation(field, field + " must be zero or greater");
        }
        return value;
    }

    private BusinessException validation(String field, String message) {
        return new BusinessException("VALIDATION_ERROR", message, Map.of(field, message));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private StudentLoan findInWorkspace(UUID workspaceId, UUID loanId) {
        return studentLoanRepository.findByIdAndWorkspaceId(loanId, workspaceId)
                .orElseThrow(() -> notFound());
    }

    private StudentLoan findInWorkspaceForUpdate(UUID workspaceId, UUID loanId) {
        return studentLoanRepository.findByIdAndWorkspaceIdForUpdate(loanId, workspaceId)
                .orElseThrow(() -> notFound());
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspace.getId(), userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify student loans", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private BusinessException notFound() {
        return new BusinessException("STUDENT_LOAN_NOT_FOUND", "Student loan not found", HttpStatus.NOT_FOUND);
    }

    private StudentLoanResponse mapToResponse(StudentLoan loan) {
        return StudentLoanResponse.builder()
                .id(loan.getId())
                .workspaceId(loan.getWorkspace().getId())
                .name(loan.getName())
                .lender(loan.getLender())
                .originalPrincipal(loan.getOriginalPrincipal())
                .currentPrincipal(loan.getCurrentPrincipal())
                .annualInterestRate(loan.getAnnualInterestRate())
                .minimumMonthlyPayment(loan.getMinimumMonthlyPayment())
                .plannedExtraMonthlyPayment(loan.getPlannedExtraMonthlyPayment())
                .startDate(loan.getStartDate())
                .targetPayoffDate(loan.getTargetPayoffDate())
                .status(loan.getStatus())
                .createdByUserId(loan.getCreatedByUser().getId())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .version(loan.getVersion())
                .build();
    }

    private record ValidatedRequest(
            String name,
            String lender,
            BigDecimal originalPrincipal,
            BigDecimal currentPrincipal,
            BigDecimal annualInterestRate,
            BigDecimal minimumMonthlyPayment,
            BigDecimal plannedExtraMonthlyPayment,
            java.time.LocalDate startDate,
            java.time.LocalDate targetPayoffDate) {
    }
}
