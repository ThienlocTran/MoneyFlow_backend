package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.obligation.service.OccurrenceGenerationResult;
import com.moneyflowbackend.obligation.service.OccurrenceGenerationService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OccurrenceGenerationServiceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;
    @Autowired OccurrenceGenerationService generationService;

    @Test
    void activeTemplateGeneratesPendingOccurrencesWithAmountReminderAndPeriodKey() {
        TestContext ctx = createContext("gen_active");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "3500000")
                .startDate(LocalDate.of(2026, 8, 5))
                .reminderDaysBefore(3)
                .build());

        OccurrenceGenerationResult result = generationService.generateForWorkspace(
                ctx.workspace().getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 5));

        assertThat(result.generatedCount()).isEqualTo(3);
        assertThat(result.existingCount()).isZero();
        assertThat(occurrences(template))
                .extracting(ObligationOccurrence::getPeriodKey)
                .containsExactly("2026-08-05", "2026-09-05", "2026-10-05");
        ObligationOccurrence first = occurrences(template).getFirst();
        assertThat(first.getStatus()).isEqualTo(ObligationOccurrenceStatus.PENDING);
        assertThat(first.getExpectedAmount()).isEqualByComparingTo("3500000");
        assertThat(first.getActualAmount()).isNull();
        assertThat(first.getLinkedTransaction()).isNull();
        assertThat(first.getCompletedAt()).isNull();
        assertThat(first.getSkippedAt()).isNull();
        assertThat(first.getSkipReason()).isNull();
        assertThat(first.getSnoozedUntil()).isNull();
        assertThat(first.getReminderDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void pausedAndArchivedTemplatesDoNotGenerate() {
        TestContext ctx = createContext("gen_inactive");
        RecurringObligationTemplate paused = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .status(RecurringObligationStatus.PAUSED)
                .build());
        RecurringObligationTemplate archived = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .status(RecurringObligationStatus.ARCHIVED)
                .build());

        OccurrenceGenerationResult result = generationService.generateForWorkspace(
                ctx.workspace().getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31));

        assertThat(result.generatedCount()).isZero();
        assertThat(occurrences(paused)).isEmpty();
        assertThat(occurrences(archived)).isEmpty();
    }

    @Test
    void variableAmountMayBeNullOrCopied() {
        TestContext ctx = createContext("gen_variable");
        RecurringObligationTemplate nullAmount = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.VARIABLE, null)
                .name("Electricity")
                .build());
        RecurringObligationTemplate copiedAmount = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.VARIABLE, "250000")
                .name("Water")
                .build());

        generationService.generateForWorkspace(ctx.workspace().getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(occurrences(nullAmount).getFirst().getExpectedAmount()).isNull();
        assertThat(occurrences(copiedAmount).getFirst().getExpectedAmount()).isEqualByComparingTo("250000");
    }

    @Test
    void rerunAndOverlappingWindowsAreIdempotent() {
        TestContext ctx = createContext("gen_idempotent");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .build());

        generationService.generateForTemplate(ctx.workspace().getId(), template.getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        OccurrenceGenerationResult rerun = generationService.generateForTemplate(ctx.workspace().getId(), template.getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        OccurrenceGenerationResult overlap = generationService.generateForTemplate(ctx.workspace().getId(), template.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

        assertThat(rerun.generatedCount()).isZero();
        assertThat(rerun.existingCount()).isEqualTo(2);
        assertThat(overlap.generatedCount()).isEqualTo(1);
        assertThat(occurrences(template))
                .extracting(ObligationOccurrence::getPeriodKey)
                .containsExactly("2026-08-05", "2026-09-05", "2026-10-05");
    }

    @Test
    void existingMaterializedOccurrencesAreNotUpdated() {
        TestContext ctx = createContext("gen_existing");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .build());
        Transaction tx = transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(bd("100"))
                .transactionDate(LocalDate.of(2026, 8, 5))
                .walletUnknown(true)
                .historical(true)
                .affectsWalletBalance(false)
                .build());
        ObligationOccurrence confirmed = occurrence(ctx, template, "2026-08-05", LocalDate.of(2026, 8, 5));
        confirmed.setStatus(ObligationOccurrenceStatus.CONFIRMED);
        confirmed.setActualAmount(bd("100"));
        confirmed.setLinkedTransaction(tx);
        confirmed.setCompletedAt(Instant.parse("2026-08-05T00:00:00Z"));
        occurrenceRepository.saveAndFlush(confirmed);
        ObligationOccurrence skipped = occurrence(ctx, template, "2026-09-05", LocalDate.of(2026, 9, 5));
        skipped.setStatus(ObligationOccurrenceStatus.SKIPPED);
        skipped.setSkippedAt(Instant.parse("2026-09-05T00:00:00Z"));
        skipped.setSkipReason("Already paid elsewhere");
        occurrenceRepository.saveAndFlush(skipped);
        ObligationOccurrence cancelled = occurrence(ctx, template, "2026-10-05", LocalDate.of(2026, 10, 5));
        cancelled.setStatus(ObligationOccurrenceStatus.CANCELLED);
        occurrenceRepository.saveAndFlush(cancelled);

        OccurrenceGenerationResult result = generationService.generateForTemplate(
                ctx.workspace().getId(),
                template.getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 31));

        assertThat(result.generatedCount()).isZero();
        assertThat(occurrenceRepository.findByTemplateIdAndPeriodKey(template.getId(), "2026-08-05").orElseThrow().getStatus())
                .isEqualTo(ObligationOccurrenceStatus.CONFIRMED);
        assertThat(occurrenceRepository.findByTemplateIdAndPeriodKey(template.getId(), "2026-09-05").orElseThrow().getSkipReason())
                .isEqualTo("Already paid elsewhere");
        assertThat(occurrenceRepository.findByTemplateIdAndPeriodKey(template.getId(), "2026-10-05").orElseThrow().getStatus())
                .isEqualTo(ObligationOccurrenceStatus.CANCELLED);
    }

    @Test
    void sameDueDateForTwoTemplatesCreatesSeparateOccurrences() {
        TestContext ctx = createContext("gen_two_templates");
        RecurringObligationTemplate first = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .name("Rent")
                .build());
        RecurringObligationTemplate second = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "200")
                .name("Internet")
                .build());

        generationService.generateForWorkspace(ctx.workspace().getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(occurrences(first)).hasSize(1);
        assertThat(occurrences(second)).hasSize(1);
        assertThat(occurrences(first).getFirst().getPeriodKey()).isEqualTo("2026-08-05");
        assertThat(occurrences(second).getFirst().getPeriodKey()).isEqualTo("2026-08-05");
    }

    @Test
    void workspaceScopeAndEndDateAreEnforced() {
        TestContext a = createContext("gen_scope_a");
        TestContext b = createContext("gen_scope_b");
        RecurringObligationTemplate templateA = templateRepository.saveAndFlush(template(a, ObligationAmountMode.FIXED, "100")
                .endDate(LocalDate.of(2026, 9, 5))
                .build());
        RecurringObligationTemplate templateB = templateRepository.saveAndFlush(template(b, ObligationAmountMode.FIXED, "100")
                .build());

        generationService.generateForWorkspace(a.workspace().getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31));

        assertThat(occurrences(templateA))
                .extracting(ObligationOccurrence::getPeriodKey)
                .containsExactly("2026-08-05", "2026-09-05");
        assertThat(occurrences(templateB)).isEmpty();
        assertThatThrownBy(() -> generationService.generateForTemplate(a.workspace().getId(), templateB.getId(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("RECURRING_OBLIGATION_TEMPLATE_NOT_FOUND");
    }

    @Test
    void boundedWindowDoesNotBackfillOutsideRange() {
        TestContext ctx = createContext("gen_bounded");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .startDate(LocalDate.of(2020, 1, 31))
                .build());

        generationService.generateForWorkspace(ctx.workspace().getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31));

        assertThat(occurrences(template))
                .extracting(ObligationOccurrence::getPeriodKey)
                .containsExactly("2026-02-28", "2026-03-31");
    }

    @Test
    void invalidWindowIsRejected() {
        TestContext ctx = createContext("gen_bad_window");

        assertThatThrownBy(() -> generationService.generateForWorkspace(
                ctx.workspace().getId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void concurrentGenerationLeavesOneOccurrencePerPeriodKey() throws Exception {
        TestContext ctx = createContext("gen_concurrent");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, ObligationAmountMode.FIXED, "100")
                .build());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    await(start);
                    generationService.generateForTemplate(ctx.workspace().getId(), template.getId(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
                }));
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(occurrences(template))
                .extracting(ObligationOccurrence::getPeriodKey)
                .containsExactly("2026-08-05");
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Occurrence Generation Test User")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private RecurringObligationTemplate.RecurringObligationTemplateBuilder template(
            TestContext ctx,
            ObligationAmountMode amountMode,
            String defaultAmount) {
        return RecurringObligationTemplate.builder()
                .workspace(ctx.workspace())
                .name("Rent")
                .direction(ObligationDirection.PAYABLE)
                .amountMode(amountMode)
                .defaultAmount(defaultAmount == null ? null : bd(defaultAmount))
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 8, 5))
                .reminderDaysBefore(0)
                .status(RecurringObligationStatus.ACTIVE)
                .createdByUser(ctx.user());
    }

    private ObligationOccurrence occurrence(TestContext ctx, RecurringObligationTemplate template, String periodKey, LocalDate dueDate) {
        return ObligationOccurrence.builder()
                .template(template)
                .workspace(ctx.workspace())
                .periodKey(periodKey)
                .dueDate(dueDate)
                .expectedAmount(bd("100"))
                .build();
    }

    private List<ObligationOccurrence> occurrences(RecurringObligationTemplate template) {
        return occurrenceRepository.findAll().stream()
                .filter(occurrence -> occurrence.getTemplate().getId().equals(template.getId()))
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .toList();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
