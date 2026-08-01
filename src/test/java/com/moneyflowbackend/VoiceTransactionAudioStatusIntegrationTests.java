package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VoiceTransactionAudioStatusIntegrationTests {

    @Autowired TransactionService transactionService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;

    @Test
    void storedVoiceAudioIsExposedOnTransactionDetailAndList() {
        TestContext ctx = createContext("voice_audio_stored");
        TransactionResponse created = createVoiceTransaction(ctx);
        Instant uploadedAt = Instant.parse("2026-07-28T02:00:00Z");

        VoiceRecord voiceRecord = voiceRecordRepository.findById(created.getVoiceRecordId()).orElseThrow();
        voiceRecord.setAudioStorageProvider("test");
        voiceRecord.setAudioStorageKey("stored/audio.webm");
        voiceRecord.setStorageProvider("test");
        voiceRecord.setStorageKey("stored/audio.webm");
        voiceRecord.setStoragePublicId("stored/audio.webm");
        voiceRecord.setMimeType("audio/webm");
        voiceRecord.setFileSizeBytes(3L);
        voiceRecord.setAudioUploadedAt(uploadedAt);
        voiceRecord.setVoiceStatus(VoiceRecordStatus.AUDIO_STORED);
        voiceRecordRepository.saveAndFlush(voiceRecord);

        assertAudioStored(transactionService.getDetails(ctx.workspace().getId(), created.getId(), false, ctx.user().getId()), uploadedAt);
        TransactionResponse listed = transactionService.list(
                        ctx.workspace().getId(), null, null, null, null, null, null, null,
                        TransactionSourceType.VOICE, null, false, 0, 20, null, ctx.user().getId())
                .getContent().stream()
                .filter(tx -> tx.getId().equals(created.getId()))
                .findFirst()
                .orElseThrow();
        assertAudioStored(listed, uploadedAt);
    }

    @Test
    void failedVoiceAudioUploadLeavesTransactionSavedButNotPlayable() {
        TestContext ctx = createContext("voice_audio_failed");
        TransactionResponse created = createVoiceTransaction(ctx);

        VoiceRecord voiceRecord = voiceRecordRepository.findById(created.getVoiceRecordId()).orElseThrow();
        voiceRecord.setVoiceStatus(VoiceRecordStatus.STORAGE_FAILED);
        voiceRecordRepository.saveAndFlush(voiceRecord);

        TransactionResponse detail = transactionService.getDetails(ctx.workspace().getId(), created.getId(), false, ctx.user().getId());

        assertThat(detail.getVoiceRecordId()).isEqualTo(created.getVoiceRecordId());
        assertThat(detail.getVoiceTranscript()).isEqualTo("coffee 35k cash");
        assertThat(detail.isHasVoiceAudio()).isFalse();
        assertThat(detail.isVoiceAudioAvailable()).isFalse();
        assertThat(detail.isPlaybackAvailable()).isFalse();
        assertThat(detail.getAudioStatus()).isEqualTo("STORAGE_FAILED");
        assertThat(detail.getVoiceAudioStatus()).isEqualTo("STORAGE_FAILED");
        assertThat(detail.getAudioMimeType()).isNull();
        assertThat(detail.getAudioSizeBytes()).isNull();
        assertThat(detail.getAudioUploadedAt()).isNull();
    }

    private void assertAudioStored(TransactionResponse response, Instant uploadedAt) {
        assertThat(response.getVoiceRecordId()).isNotNull();
        assertThat(response.getVoiceTranscript()).isEqualTo("coffee 35k cash");
        assertThat(response.isHasVoiceAudio()).isTrue();
        assertThat(response.isVoiceAudioAvailable()).isTrue();
        assertThat(response.isPlaybackAvailable()).isTrue();
        assertThat(response.getAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(response.getVoiceAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(response.getAudioMimeType()).isEqualTo("audio/webm");
        assertThat(response.getAudioSizeBytes()).isEqualTo(3L);
        assertThat(response.getAudioUploadedAt()).isEqualTo(uploadedAt);
    }

    private TransactionResponse createVoiceTransaction(TestContext ctx) {
        Wallet wallet = wallet(ctx, "Cash");
        Category category = category(ctx, "Food");
        VoiceRecord voiceRecord = voiceRecordRepository.saveAndFlush(VoiceRecord.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .originalTranscript("coffee 35k cash")
                .editedTranscript("coffee 35k cash")
                .voiceStatus(VoiceRecordStatus.CONFIRMED)
                .build());

        TransactionRequest request = new TransactionRequest();
        request.setType(TransactionType.EXPENSE);
        request.setStatus(TransactionStatus.POSTED);
        request.setAmount(new BigDecimal("35000"));
        request.setWalletId(wallet.getId());
        request.setCategoryId(category.getId());
        request.setTransactionDate(LocalDate.of(2026, 7, 28));
        request.setDescription("Coffee");

        return transactionService.createWithSource(
                ctx.workspace().getId(),
                request,
                ctx.user().getId(),
                TransactionSourceType.VOICE,
                "coffee 35k cash",
                voiceRecord.getId());
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Voice Audio User")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .isActive(true)
                .isArchived(false)
                .isQuickAction(false)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
