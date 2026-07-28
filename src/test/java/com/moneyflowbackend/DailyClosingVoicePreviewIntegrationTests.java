package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.closing.dto.DailyClosingVoicePreviewRequest;
import com.moneyflowbackend.closing.dto.DailyClosingVoicePreviewResponse;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.closing.service.DailyClosingVoicePreviewService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyClosingVoicePreviewIntegrationTests {
    @Autowired DailyClosingVoicePreviewService previewService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired DailyClosingRepository dailyClosingRepository;
    @Autowired WalletBalanceSnapshotRepository snapshotRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void previewsMultipleWalletBalancesWithoutPersisting() {
        TestContext ctx = context("dc_voice_multi");
        LocalDate closingDate = LocalDate.of(2026, 7, 28);
        Wallet cash = wallet(ctx, "Tiền mặt của anh", closingDate);
        Wallet mb = wallet(ctx, "MB Bank", closingDate);
        Wallet cake = wallet(ctx, "Cake", closingDate);
        long closingCount = dailyClosingRepository.count();
        long snapshotCount = snapshotRepository.count();
        long transactionCount = transactionRepository.count();

        DailyClosingVoicePreviewResponse response = previewService.preview(ctx.workspace().getId(),
                request("Tiền mặt còn 500, MB còn 4 triệu 8, Cake còn 200", closingDate), ctx.user().getId());

        assertThat(response.getCandidateStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(response.getWalletBalanceCandidates()).hasSize(3);
        assertCandidate(response, cash, "500000", "NEEDS_REVIEW");
        assertCandidate(response, mb, "4800000", "READY");
        assertCandidate(response, cake, "200000", "NEEDS_REVIEW");
        assertThat(response.getWalletBalanceCandidates().getFirst().getInferenceNotes()).contains("Hiểu '500' là 500000đ");
        assertThat(dailyClosingRepository.count()).isEqualTo(closingCount);
        assertThat(snapshotRepository.count()).isEqualTo(snapshotCount);
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
    }

    @Test
    void explicitUnitAndFullPhraseResolveWalletBalanceSnapshot() {
        TestContext ctx = context("dc_voice_unit");
        LocalDate closingDate = LocalDate.of(2026, 7, 28);
        Wallet cash = wallet(ctx, "Tiền mặt của anh", closingDate);

        DailyClosingVoicePreviewResponse explicit = previewService.preview(ctx.workspace().getId(),
                request("Tiền mặt còn 500 nghìn", closingDate), ctx.user().getId());
        DailyClosingVoicePreviewResponse full = previewService.preview(ctx.workspace().getId(),
                request("Ví tiền mặt của anh còn 2 triệu", closingDate), ctx.user().getId());

        assertCandidate(explicit, cash, "500000", "READY");
        assertCandidate(full, cash, "2000000", "READY");
        assertThat(full.getWalletBalanceCandidates().getFirst().getIntentType().name()).isEqualTo("WALLET_BALANCE_SNAPSHOT");
        assertThat(full.getWalletBalanceCandidates().getFirst().getLedgerEffect().name()).isEqualTo("DOES_NOT_AFFECT_WALLET");
        assertThat(full.getWalletBalanceCandidates().getFirst().getTargetModule()).isEqualTo("DAILY_CLOSING");
    }

    @Test
    void skipUnknownAmbiguousAndInvalidAmountStayReviewable() {
        TestContext ctx = context("dc_voice_review");
        LocalDate closingDate = LocalDate.of(2026, 7, 28);
        Wallet momo = wallet(ctx, "MoMo", closingDate);
        wallet(ctx, "Tiền mặt của anh", closingDate);
        wallet(ctx, "Tiền mặt của em", closingDate);
        wallet(ctx, "MB Bank", closingDate);

        DailyClosingVoicePreviewResponse skip = previewService.preview(ctx.workspace().getId(),
                request("MoMo bỏ qua", closingDate), ctx.user().getId());
        DailyClosingVoicePreviewResponse unknown = previewService.preview(ctx.workspace().getId(),
                request("Ví Techcom còn 1 triệu", closingDate), ctx.user().getId());
        DailyClosingVoicePreviewResponse ambiguous = previewService.preview(ctx.workspace().getId(),
                request("tiền mặt còn 500", closingDate), ctx.user().getId());
        DailyClosingVoicePreviewResponse invalid = previewService.preview(ctx.workspace().getId(),
                request("MB còn abc", closingDate), ctx.user().getId());

        assertThat(skip.getSkippedWallets()).singleElement().satisfies(wallet -> {
            assertThat(wallet.getWalletId()).isEqualTo(momo.getId());
            assertThat(wallet.getReason()).isEqualTo("Người dùng nói bỏ qua");
        });
        assertThat(unknown.getWalletBalanceCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getStatus()).isEqualTo("UNKNOWN_WALLET"));
        assertThat(ambiguous.getWalletBalanceCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getStatus()).isEqualTo("AMBIGUOUS_WALLET");
            assertThat(candidate.getWalletOptions()).hasSize(2);
        });
        assertThat(invalid.getWalletBalanceCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getStatus()).isEqualTo("INVALID_AMOUNT"));
    }

    @Test
    void previewUsesOnlyCurrentWorkspaceWalletsAndRejectsBlankTranscript() {
        TestContext owner = context("dc_voice_owner");
        TestContext other = context("dc_voice_other");
        LocalDate closingDate = LocalDate.of(2026, 7, 28);
        wallet(owner, "Cash", closingDate);
        wallet(other, "Techcom", closingDate);

        DailyClosingVoicePreviewResponse response = previewService.preview(owner.workspace().getId(),
                request("Techcom còn 1 triệu", closingDate), owner.user().getId());

        assertThat(response.getWalletBalanceCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getStatus()).isEqualTo("UNKNOWN_WALLET"));
        assertThatThrownBy(() -> previewService.preview(owner.workspace().getId(), request(" ", closingDate), owner.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TRANSCRIPT_REQUIRED");
        assertThatThrownBy(() -> previewService.preview(owner.workspace().getId(), request("Techcom còn 1 triệu", closingDate), other.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FORBIDDEN");
    }

    private void assertCandidate(DailyClosingVoicePreviewResponse response, Wallet wallet, String amount, String status) {
        assertThat(response.getWalletBalanceCandidates())
                .filteredOn(candidate -> wallet.getId().equals(candidate.getWalletId()))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getWalletName()).isEqualTo(wallet.getName());
                    assertThat(candidate.getActualBalance()).isEqualByComparingTo(amount);
                    assertThat(candidate.getStatus()).isEqualTo(status);
                    assertThat(candidate.getCurrencyCode()).isEqualTo("VND");
                });
    }

    private DailyClosingVoicePreviewRequest request(String transcript, LocalDate closingDate) {
        DailyClosingVoicePreviewRequest req = new DailyClosingVoicePreviewRequest();
        req.setTranscript(transcript);
        req.setClosingDate(closingDate);
        return req;
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Daily Closing Voice Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, LocalDate openingDate) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(openingDate)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
