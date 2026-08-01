package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.voice.service.VoiceAudioService;
import com.moneyflowbackend.voice.storage.DisabledVoiceAudioStorageService;
import com.moneyflowbackend.voice.storage.StoredVoiceAudio;
import com.moneyflowbackend.voice.storage.StoredVoiceAudioStream;
import com.moneyflowbackend.voice.storage.VoiceAudioPlayback;
import com.moneyflowbackend.voice.storage.VoiceAudioStorageService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceAudioServiceTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void uploadReturnsStorageNotConfiguredWhenStorageIsDisabled() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1, 2, 3});

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "STORAGE_NOT_CONFIGURED");
        assertThat(ctx.voiceRecord().getOriginalTranscript()).isEqualTo("an sang 35k");
        assertThat(ctx.voiceRecord().getVoiceStatus()).isEqualTo(VoiceRecordStatus.STORAGE_FAILED);
    }

    @Test
    void uploadRejectsInvalidMimeBeforeStorageCall() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "application/octet-stream", new byte[] {1});

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "INVALID_AUDIO_MIME_TYPE");
    }

    @Test
    void uploadRejectsEmptyFile() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[0]);

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "AUDIO_FILE_EMPTY");
    }

    @Test
    void uploadRejectsOversizedFile() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[10485761]);

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "AUDIO_FILE_TOO_LARGE");
    }

    @Test
    void uploadRejectsViewer() {
        TestContext ctx = context(new FakeStorageService(), WorkspaceRole.VIEWER);
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1});

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "FORBIDDEN");
    }

    @Test
    void uploadUpdatesMetadataWhenStorageSucceeds() {
        FakeStorageService storage = new FakeStorageService();
        TestContext ctx = context(storage);
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1, 2, 3});

        var response = ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId());

        assertThat(response.isVoiceAudioAvailable()).isTrue();
        assertThat(response.getAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(response.getVoiceAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(response.getRetentionUntil()).isNull();
        assertThat(ctx.voiceRecord().getStoragePublicId())
                .startsWith("stored/06-2026/15-06-2026/");
        assertThat(ctx.voiceRecord().getStorageProvider()).isEqualTo("test");
        assertThat(ctx.voiceRecord().getStorageKey()).isEqualTo(ctx.voiceRecord().getStoragePublicId());
        assertThat(ctx.voiceRecord().getAudioStorageProvider()).isEqualTo("test");
        assertThat(ctx.voiceRecord().getAudioStorageKey()).isEqualTo(ctx.voiceRecord().getStoragePublicId());
        assertThat(ctx.voiceRecord().getAudioUrl()).isEqualTo("test:authenticated");
        assertThat(ctx.voiceRecord().getMimeType()).isEqualTo("audio/webm");
        assertThat(ctx.voiceRecord().getFileSizeBytes()).isEqualTo(3);
        assertThat(ctx.voiceRecord().getDurationSeconds()).isEqualTo(12);
        assertThat(ctx.voiceRecord().getAudioUploadedAt()).isEqualTo(Instant.parse("2026-06-15T01:00:00Z"));
        assertThat(ctx.voiceRecord().getVoiceStatus()).isEqualTo(VoiceRecordStatus.AUDIO_STORED);
    }

    @Test
    void providerFailureMarksStorageFailedWithoutDeletingTranscript() {
        TestContext ctx = context(new FailingStorageService());
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1, 2, 3});

        assertBusinessCode(
                () -> ctx.service().uploadAudio(ctx.voiceRecord().getId(), file, 12, ctx.user().getId()),
                "AUDIO_UPLOAD_FAILED");

        assertThat(ctx.voiceRecord().getStoragePublicId()).isNull();
        assertThat(ctx.voiceRecord().getOriginalTranscript()).isEqualTo("an sang 35k");
        assertThat(ctx.voiceRecord().getVoiceStatus()).isEqualTo(VoiceRecordStatus.STORAGE_FAILED);
    }

    @Test
    void playbackUrlUsesBackendAudioEndpoint() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setStoragePublicId("stored/audio");
        ctx.voiceRecord().setMimeType("audio/webm");

        var response = ctx.service().playbackUrl(ctx.voiceRecord().getId(), ctx.user().getId());

        assertThat(response.getPlaybackUrl()).isEqualTo("/api/voice-records/" + ctx.voiceRecord().getId() + "/audio");
        assertThat(response.getPlaybackUrl()).doesNotContain("cloudinary");
        assertThat(response.getExpiresAt()).isNull();
    }

    @Test
    void playbackUrlReturnsNotAvailableWhenNoAudio() {
        TestContext ctx = context(new FakeStorageService());

        assertBusiness(
                () -> ctx.service().playbackUrl(ctx.voiceRecord().getId(), ctx.user().getId()),
                "AUDIO_NOT_UPLOADED",
                HttpStatus.NOT_FOUND);
    }

    @Test
    void streamAudioReturnsStorageNotConfiguredWhenStorageIsDisabled() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        ctx.voiceRecord().setAudioStorageKey("stored/audio");
        ctx.voiceRecord().setMimeType("audio/webm");

        assertBusiness(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId()),
                "STORAGE_NOT_CONFIGURED",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void streamAudioReturnsNotAvailableWhenNoAudioMetadata() {
        TestContext ctx = context(new FakeStorageService());

        assertBusiness(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId()),
                "AUDIO_NOT_UPLOADED",
                HttpStatus.NOT_FOUND);
    }

    @Test
    void streamAudioReturnsStorageFailedWhenUploadPreviouslyFailed() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setVoiceStatus(VoiceRecordStatus.STORAGE_FAILED);

        assertBusiness(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId()),
                "AUDIO_UPLOAD_FAILED",
                HttpStatus.CONFLICT);
    }

    @Test
    void streamAudioReturnsNotAvailableWhenProviderObjectIsMissing() {
        TestContext ctx = context(new MissingStorageService());
        ctx.voiceRecord().setAudioStorageKey("stored/missing");
        ctx.voiceRecord().setMimeType("audio/webm");

        assertBusiness(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId()),
                "AUDIO_OBJECT_MISSING",
                HttpStatus.NOT_FOUND);
    }

    @Test
    void streamAudioReturnsDeletedWhenAudioWasDeleted() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setVoiceStatus(VoiceRecordStatus.AUDIO_DELETED);
        ctx.voiceRecord().setAudioStorageKey("stored/deleted");
        ctx.voiceRecord().setMimeType("audio/webm");

        assertBusiness(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId()),
                "AUDIO_DELETED",
                HttpStatus.GONE);
    }

    @Test
    void playbackRejectsOutsider() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setStoragePublicId("stored/audio");
        ctx.voiceRecord().setMimeType("audio/webm");

        assertBusinessCode(
                () -> ctx.service().playbackUrl(ctx.voiceRecord().getId(), UUID.randomUUID()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void streamAudioReturnsStoredBytesForMember() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setAudioStorageKey("stored/audio");
        ctx.voiceRecord().setMimeType("audio/webm");

        StoredVoiceAudioStream response = ctx.service().streamAudio(ctx.voiceRecord().getId(), ctx.user().getId());

        assertThat(response.bytes()).containsExactly(9, 8, 7);
        assertThat(response.mimeType()).isEqualTo("audio/webm");
    }

    @Test
    void streamAudioRejectsOutsider() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setAudioStorageKey("stored/audio");
        ctx.voiceRecord().setMimeType("audio/webm");

        assertBusinessCode(
                () -> ctx.service().streamAudio(ctx.voiceRecord().getId(), UUID.randomUUID()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void deleteRejectsOutsider() {
        TestContext ctx = context(new FakeStorageService());
        ctx.voiceRecord().setStoragePublicId("stored/audio");

        assertBusinessCode(
                () -> ctx.service().deleteVoiceAudio(ctx.voiceRecord().getId(), UUID.randomUUID()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void storageStatusRequiresWorkspaceMembership() {
        TestContext ctx = context(new FakeStorageService());

        var response = ctx.service().storageStatus(ctx.workspace().getId(), ctx.user().getId());

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getProvider()).isEqualTo("test");
        assertThat(response.getMaxBytes()).isEqualTo(10485760);
        assertBusinessCode(
                () -> ctx.service().storageStatus(ctx.workspace().getId(), UUID.randomUUID()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void deleteExpiredVoiceAudioClearsAudioMetadataButKeepsTranscript() {
        FakeStorageService storage = new FakeStorageService();
        TestContext ctx = context(storage);
        ctx.voiceRecord().setStoragePublicId("stored/audio");
        ctx.voiceRecord().setAudioUrl("private://voice");
        ctx.voiceRecord().setMimeType("audio/webm");
        ctx.voiceRecord().setFileSizeBytes(42L);
        ctx.voiceRecord().setDurationSeconds(10);
        ctx.voiceRecord().setOriginalTranscript("an sang 35k");
        ctx.voiceRecord().setRetentionUntil(LocalDate.of(2026, 6, 14));
        when(ctx.voiceRecordRepository().findAllExpiredWithStoredAudio(LocalDate.of(2026, 6, 15)))
                .thenReturn(List.of(ctx.voiceRecord()));

        int deleted = ctx.service().deleteExpiredVoiceAudio();

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deletedPublicId).isEqualTo("stored/audio");
        assertThat(ctx.voiceRecord().getStoragePublicId()).isNull();
        assertThat(ctx.voiceRecord().getStorageKey()).isNull();
        assertThat(ctx.voiceRecord().getAudioUrl()).isNull();
        assertThat(ctx.voiceRecord().getMimeType()).isNull();
        assertThat(ctx.voiceRecord().getFileSizeBytes()).isNull();
        assertThat(ctx.voiceRecord().getDurationSeconds()).isNull();
        assertThat(ctx.voiceRecord().getAudioDeletedAt()).isEqualTo(Instant.parse("2026-06-15T01:00:00Z"));
        assertThat(ctx.voiceRecord().getOriginalTranscript()).isEqualTo("an sang 35k");
        assertThat(ctx.voiceRecord().getVoiceStatus()).isEqualTo(VoiceRecordStatus.AUDIO_DELETED);
    }

    @Test
    void deleteExpiredVoiceAudioIsSafeWhenStorageIsDisabled() {
        TestContext ctx = context(new DisabledVoiceAudioStorageService());
        ctx.voiceRecord().setStoragePublicId("stored/audio");
        ctx.voiceRecord().setRetentionUntil(LocalDate.of(2026, 6, 14));
        when(ctx.voiceRecordRepository().findAllExpiredWithStoredAudio(LocalDate.of(2026, 6, 15)))
                .thenReturn(List.of(ctx.voiceRecord()));

        int deleted = ctx.service().deleteExpiredVoiceAudio();

        assertThat(deleted).isZero();
        assertThat(ctx.voiceRecord().getStoragePublicId()).isEqualTo("stored/audio");
        verify(ctx.voiceRecordRepository(), never()).save(any(VoiceRecord.class));
    }

    @Test
    void deleteExpiredVoiceAudioRetriesLaterWhenProviderDeleteFails() {
        TestContext ctx = context(new FailingStorageService());
        ctx.voiceRecord().setStoragePublicId("stored/audio");
        ctx.voiceRecord().setRetentionUntil(LocalDate.of(2026, 6, 14));
        when(ctx.voiceRecordRepository().findAllExpiredWithStoredAudio(LocalDate.of(2026, 6, 15)))
                .thenReturn(List.of(ctx.voiceRecord()));

        int deleted = ctx.service().deleteExpiredVoiceAudio();

        assertThat(deleted).isZero();
        assertThat(ctx.voiceRecord().getStoragePublicId()).isEqualTo("stored/audio");
        verify(ctx.voiceRecordRepository(), never()).save(any(VoiceRecord.class));
    }

    private static TestContext context(VoiceAudioStorageService storageService) {
        return context(storageService, WorkspaceRole.OWNER);
    }

    private static TestContext context(VoiceAudioStorageService storageService, WorkspaceRole role) {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Workspace workspace = Workspace.builder().id(workspaceId).name("Voice workspace").build();
        User user = User.builder().id(userId).username("voice_user").build();
        VoiceRecord voiceRecord = VoiceRecord.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .createdByUser(user)
                .voiceStatus(VoiceRecordStatus.CONFIRMED)
                .originalTranscript("an sang 35k")
                .build();
        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build();

        VoiceRecordRepository voiceRecordRepository = mock(VoiceRecordRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);

        when(voiceRecordRepository.findById(voiceRecord.getId())).thenReturn(Optional.of(voiceRecord));
        when(voiceRecordRepository.save(any(VoiceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE"))
                .thenReturn(Optional.of(member));
        when(transactionRepository.existsByWorkspaceIdAndVoiceRecordIdAndSourceType(
                eq(workspaceId),
                eq(voiceRecord.getId()),
                eq(TransactionSourceType.VOICE))).thenReturn(true);

        VoiceAudioService service = new VoiceAudioService(
                voiceRecordRepository,
                transactionRepository,
                workspaceRepository,
                workspaceMemberRepository,
                storageService,
                CLOCK,
                10485760,
                "audio/webm,audio/mp4,audio/mpeg,audio/wav",
                0);
        return new TestContext(service, voiceRecordRepository, voiceRecord, workspace, user);
    }

    private static void assertBusinessCode(ThrowingRunnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private static void assertBusiness(ThrowingRunnable runnable, String code, HttpStatus status) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(code);
                    assertThat(business.getStatus()).isEqualTo(status);
                });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private static class FakeStorageService implements VoiceAudioStorageService {
        private String deletedPublicId;

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public StoredVoiceAudio upload(String objectKey, org.springframework.web.multipart.MultipartFile file) {
            return new StoredVoiceAudio("test", "stored/" + objectKey, "authenticated");
        }

        @Override
        public VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType) {
            return new VoiceAudioPlayback("https://signed.example/voice", Instant.parse("2026-06-15T01:05:00Z"), mimeType);
        }

        @Override
        public StoredVoiceAudioStream open(String storagePublicId, String mimeType) {
            return new StoredVoiceAudioStream(new byte[] {9, 8, 7}, mimeType, 3);
        }

        @Override
        public void delete(String storagePublicId) {
            deletedPublicId = storagePublicId;
        }
    }

    private static final class FailingStorageService implements VoiceAudioStorageService {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public StoredVoiceAudio upload(String objectKey, org.springframework.web.multipart.MultipartFile file) {
            throw new BusinessException("AUDIO_UPLOAD_FAILED", "Upload failed");
        }

        @Override
        public VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType) {
            throw new BusinessException("AUDIO_UPLOAD_FAILED", "Upload failed");
        }

        @Override
        public StoredVoiceAudioStream open(String storagePublicId, String mimeType) {
            throw new BusinessException("AUDIO_OBJECT_MISSING", "Voice audio object is missing");
        }

        @Override
        public void delete(String storagePublicId) {
            throw new BusinessException("AUDIO_UPLOAD_FAILED", "Upload failed");
        }
    }

    private static final class MissingStorageService extends FakeStorageService {
        @Override
        public StoredVoiceAudioStream open(String storagePublicId, String mimeType) {
            throw new BusinessException("AUDIO_OBJECT_MISSING", "Voice audio object is missing", HttpStatus.NOT_FOUND);
        }
    }

    private record TestContext(
            VoiceAudioService service,
            VoiceRecordRepository voiceRecordRepository,
            VoiceRecord voiceRecord,
            Workspace workspace,
            User user) {
    }
}
