package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.obligation.model.*;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecurringObligationPersistenceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;

    @Test
    void persistTemplateAndFoundationQueries() {
        TestContext ctx = createContext("ob_template");
        Wallet wallet = wallet(ctx);
        Category category = category(ctx);

        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, wallet, category));

        assertThat(template.getId()).isNotNull();
        assertThat(template.getVersion()).isNotNull();
        assertThat(templateRepository.findAllByWorkspaceIdAndStatusOrderByStartDateAsc(ctx.workspace().getId(), RecurringObligationStatus.ACTIVE))
                .extracting(RecurringObligationTemplate::getId)
                .containsExactly(template.getId());
        assertThat(templateRepository.existsByIdAndWorkspaceId(template.getId(), ctx.workspace().getId())).isTrue();
    }

    @Test
    void invalidTemplateConstraintsAreRejected() {
        TestContext ctx = createContext("ob_template_bad");

        assertRejected(() -> {
            RecurringObligationTemplate template = template(ctx, null, null);
            template.setDefaultAmount(null);
            templateRepository.saveAndFlush(template);
        });
        assertRejected(() -> {
            RecurringObligationTemplate template = template(ctx, null, null);
            template.setIntervalCount(0);
            templateRepository.saveAndFlush(template);
        });
        assertRejected(() -> {
            RecurringObligationTemplate template = template(ctx, null, null);
            template.setEndDate(template.getStartDate().minusDays(1));
            templateRepository.saveAndFlush(template);
        });
    }

    @Test
    void persistOccurrenceAndFoundationQueries() {
        TestContext ctx = createContext("ob_occurrence");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, null, null));

        ObligationOccurrence occurrence = occurrenceRepository.saveAndFlush(occurrence(ctx, template, "2026-08", LocalDate.of(2026, 8, 5)));

        assertThat(occurrence.getId()).isNotNull();
        assertThat(occurrence.getVersion()).isNotNull();
        assertThat(occurrenceRepository.findByTemplateIdAndPeriodKey(template.getId(), "2026-08")).isPresent();
        assertThat(occurrenceRepository.findTopByTemplateIdOrderByDueDateDesc(template.getId()).orElseThrow().getId()).isEqualTo(occurrence.getId());
        assertThat(occurrenceRepository.findAllByWorkspaceIdAndStatusAndDueDateBetweenOrderByDueDateAsc(
                ctx.workspace().getId(),
                ObligationOccurrenceStatus.PENDING,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)))
                .extracting(ObligationOccurrence::getId)
                .containsExactly(occurrence.getId());
    }

    @Test
    void duplicateOccurrenceKeysAndStateIntegrityAreRejected() {
        TestContext ctx = createContext("ob_occurrence_bad");
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, null, null));
        occurrenceRepository.saveAndFlush(occurrence(ctx, template, "2026-08", LocalDate.of(2026, 8, 5)));

        assertRejected(() -> occurrenceRepository.saveAndFlush(occurrence(ctx, template, "2026-08", LocalDate.of(2026, 8, 5))));
        assertRejected(() -> {
            ObligationOccurrence confirmed = occurrence(ctx, template, "2026-09", LocalDate.of(2026, 9, 5));
            confirmed.setStatus(ObligationOccurrenceStatus.CONFIRMED);
            occurrenceRepository.saveAndFlush(confirmed);
        });
        assertRejected(() -> {
            ObligationOccurrence skipped = occurrence(ctx, template, "2026-10", LocalDate.of(2026, 10, 5));
            skipped.setStatus(ObligationOccurrenceStatus.SKIPPED);
            occurrenceRepository.saveAndFlush(skipped);
        });
    }

    @Test
    void linkedTransactionIsUniqueAndHistoricalTransactionsAreUnaffected() {
        TestContext ctx = createContext("ob_linked_tx");
        Wallet wallet = wallet(ctx);
        Category category = category(ctx);
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, wallet, category));
        Transaction tx = transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(bd("100000"))
                .transactionDate(LocalDate.of(2026, 8, 5))
                .build());
        Transaction historical = transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .category(category)
                .transactionType(TransactionType.INCOME)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(bd("200000"))
                .transactionDate(LocalDate.of(2026, 1, 31))
                .walletUnknown(true)
                .historical(true)
                .affectsWalletBalance(false)
                .build());

        ObligationOccurrence first = confirmedOccurrence(ctx, template, "2026-08", tx);
        occurrenceRepository.saveAndFlush(first);

        assertThat(occurrenceRepository.findByLinkedTransactionId(tx.getId())).isPresent();
        assertRejected(() -> occurrenceRepository.saveAndFlush(confirmedOccurrence(ctx, template, "2026-09", tx)));
        assertThat(transactionRepository.findById(historical.getId()).orElseThrow().isHistorical()).isTrue();
        assertThat(transactionRepository.findById(historical.getId()).orElseThrow().isAffectsWalletBalance()).isFalse();
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Obligation Test User")
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

    private RecurringObligationTemplate template(TestContext ctx, Wallet wallet, Category category) {
        return RecurringObligationTemplate.builder()
                .workspace(ctx.workspace())
                .name("Rent")
                .direction(ObligationDirection.PAYABLE)
                .amountMode(ObligationAmountMode.FIXED)
                .defaultAmount(bd("3500000"))
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 8, 5))
                .reminderDaysBefore(3)
                .defaultWallet(wallet)
                .defaultCategory(category)
                .createdByUser(ctx.user())
                .build();
    }

    private ObligationOccurrence occurrence(TestContext ctx, RecurringObligationTemplate template, String periodKey, LocalDate dueDate) {
        return ObligationOccurrence.builder()
                .template(template)
                .workspace(ctx.workspace())
                .periodKey(periodKey)
                .dueDate(dueDate)
                .expectedAmount(bd("3500000"))
                .build();
    }

    private ObligationOccurrence confirmedOccurrence(TestContext ctx, RecurringObligationTemplate template, String periodKey, Transaction tx) {
        ObligationOccurrence occurrence = occurrence(ctx, template, periodKey, tx.getTransactionDate());
        occurrence.setStatus(ObligationOccurrenceStatus.CONFIRMED);
        occurrence.setLinkedTransaction(tx);
        occurrence.setActualAmount(tx.getAmount());
        occurrence.setCompletedAt(Instant.now());
        return occurrence;
    }

    private Wallet wallet(TestContext ctx) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name("Cash")
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .build());
    }

    private Category category(TestContext ctx) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name("Rent")
                .categoryType(CategoryType.EXPENSE)
                .build());
    }

    private void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
