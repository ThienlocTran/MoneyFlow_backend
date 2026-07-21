package com.moneyflowbackend.activity;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.model.DailyClosingStatus;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditAction;
import com.moneyflowbackend.transaction.audit.TransactionAuditLog;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.BalanceSourceType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletBalanceSnapshot;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActivityTimelineControllerIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletBalanceSnapshotRepository snapshotRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransactionAuditLogRepository auditLogRepository;
    @Autowired DailyClosingRepository dailyClosingRepository;

    @Test
    void ownerEditorViewerCanReadAndNonMemberCannot() throws Exception {
        TestUser owner = registerAndLogin("activity_owner");
        TestUser editor = registerAndLogin("activity_editor");
        TestUser viewer = registerAndLogin("activity_viewer");
        TestUser outsider = registerAndLogin("activity_outsider");
        addMember(owner.workspace(), editor.user(), WorkspaceRole.EDITOR);
        addMember(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER);
        Transaction tx = transaction(owner.workspace(), owner.user(), TransactionType.EXPENSE, "100.00");
        audit(owner.workspace(), tx, owner.user(), TransactionAuditAction.CREATE, Instant.parse("2026-07-20T10:15:30Z"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("TRANSACTION_CREATED"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .header("Authorization", bearer(editor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("TRANSACTION_CREATED"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .header("Authorization", bearer(viewer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("TRANSACTION_CREATED"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void endpointReturnsCursorContractFiltersAndValidationErrors() throws Exception {
        TestUser owner = registerAndLogin("activity_contract");
        Transaction first = transaction(owner.workspace(), owner.user(), TransactionType.EXPENSE, "100.00");
        Transaction second = transaction(owner.workspace(), owner.user(), TransactionType.EXPENSE, "200.00");
        Instant same = Instant.parse("2026-07-20T10:15:30Z");
        audit(owner.workspace(), first, owner.user(), TransactionAuditAction.CREATE, same);
        audit(owner.workspace(), second, owner.user(), TransactionAuditAction.UPDATE, same);

        String firstPage = mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("size", "1")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").exists())
                .andExpect(jsonPath("$.data.items[0].source").value("TRANSACTION_AUDIT"))
                .andExpect(jsonPath("$.data.items[0].sourceRank").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].details.note").doesNotExist())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data.size").value(1))
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(firstPage, "$.data.nextCursor");
        String firstId = com.jayway.jsonpath.JsonPath.read(firstPage, "$.data.items[0].id");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("size", "1")
                        .param("cursor", cursor)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(not(firstId)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("actions", "TRANSACTION_CREATED,TRANSACTION_UPDATED")
                        .param("entityTypes", "TRANSACTION")
                        .param("actorId", owner.user().getId().toString())
                        .param("from", "2026-07-20T10:15:30Z")
                        .param("to", "2026-07-20T10:15:31Z")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", not(empty())));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("to", "2026-07-20T10:15:30Z")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("size", "0")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_PAGE_SIZE"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("size", "101")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_PAGE_SIZE"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("cursor", "not base64")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_CURSOR"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("actions", "ALL")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_FILTER"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("actorId", "not-a-uuid")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_FILTER"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("size", "many")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_PAGE_SIZE"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("from", "2026-07-21T00:00:00Z")
                        .param("to", "2026-07-21T00:00:00Z")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_DATE_RANGE"));
    }

    @Test
    void endpointReturnsDailyClosingEventsWithoutSnapshotSpam() throws Exception {
        TestUser owner = registerAndLogin("activity_closing");
        Instant completedAt = Instant.parse("2026-07-20T10:15:30Z");
        DailyClosing closing = dailyClosing(owner.workspace(), owner.user(), LocalDate.of(2026, 7, 20), completedAt);
        snapshot(owner.workspace(), owner.user(), closing);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("actions", "DAILY_CLOSING_COMPLETED")
                        .param("entityTypes", "DAILY_CLOSING")
                        .param("actorId", owner.user().getId().toString())
                        .param("from", "2026-07-20T10:15:29Z")
                        .param("to", "2026-07-20T10:15:31Z")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("DAILY_CLOSING:" + closing.getId()))
                .andExpect(jsonPath("$.data.items[0].source").value("DAILY_CLOSING"))
                .andExpect(jsonPath("$.data.items[0].sourceRank").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].action").value("DAILY_CLOSING_COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].entityType").value("DAILY_CLOSING"))
                .andExpect(jsonPath("$.data.items[0].businessDate").value("2026-07-20"))
                .andExpect(jsonPath("$.data.items[0].details.closingDate").value("2026-07-20"))
                .andExpect(jsonPath("$.data.items[0].details.snapshotCount").value(1))
                .andExpect(jsonPath("$.data.items[0].details.note").doesNotExist())
                .andExpect(jsonPath("$.data.items[1]").doesNotExist());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/activity-timeline", owner.workspace().getId())
                        .param("actions", "WALLET_SNAPSHOT_RECORDED")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Activity Timeline Test User");
        authService.register(request);
        LoginRequest login = new LoginRequest();
        login.setIdentifier(request.getUsername());
        login.setPassword("StrongPassword123");
        TokenResponse token = authService.login(login);
        Workspace workspace = workspaceRepository.findAllByUserId(token.getUser().getId()).getFirst();
        User user = User.builder().id(token.getUser().getId()).build();
        return new TestUser(user, workspace, token);
    }

    private void addMember(Workspace workspace, User user, WorkspaceRole role) {
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
    }

    private Transaction transaction(Workspace workspace, User user, TransactionType type, String amount) {
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name("Activity wallet " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .build());
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name("Activity category " + UUID.randomUUID())
                .categoryType(CategoryType.EXPENSE)
                .build());
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .wallet(wallet)
                .category(category)
                .transactionType(type)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 7, 20))
                .note("private note")
                .rawInput("private raw input")
                .sourceType(TransactionSourceType.MANUAL)
                .build());
    }

    private void audit(Workspace workspace, Transaction tx, User actor, TransactionAuditAction action, Instant createdAt) {
        auditLogRepository.saveAndFlush(TransactionAuditLog.builder()
                .workspace(workspace)
                .transaction(tx)
                .actorUser(actor)
                .action(action)
                .beforeData(Map.of("note", "private before"))
                .afterData(Map.of("rawInput", "private after", "token", "secret"))
                .createdAt(createdAt)
                .build());
    }

    private DailyClosing dailyClosing(Workspace workspace, User user, LocalDate closingDate, Instant completedAt) {
        User completedBy = userRepository.findById(user.getId()).orElseThrow();
        return dailyClosingRepository.saveAndFlush(DailyClosing.builder()
                .workspace(workspace)
                .closingDate(closingDate)
                .status(DailyClosingStatus.COMPLETED)
                .completedAt(completedAt)
                .completedBy(completedBy)
                .build());
    }

    private WalletBalanceSnapshot snapshot(Workspace workspace, User user, DailyClosing closing) {
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name("Closing snapshot wallet " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(closing.getClosingDate())
                .build());
        return snapshotRepository.saveAndFlush(WalletBalanceSnapshot.builder()
                .workspace(workspace)
                .wallet(wallet)
                .dailyClosing(closing)
                .snapshotDate(closing.getClosingDate())
                .balance(BigDecimal.TEN)
                .sourceType(BalanceSourceType.MANUAL)
                .createdBy(user)
                .createdAt(Instant.parse("2026-07-20T10:00:00Z"))
                .build());
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
