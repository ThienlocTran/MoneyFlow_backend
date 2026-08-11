package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryGroupReorderRequest;
import com.moneyflowbackend.categoryboard.dto.CategoryMoveRequest;
import com.moneyflowbackend.categoryboard.dto.JarBoardReorderRequest;
import com.moneyflowbackend.categoryboard.service.CategoryBoardMutationService;
import com.moneyflowbackend.categoryboard.service.CategoryBoardService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryBoardApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CategoryBoardService categoryBoardService;
    @Autowired CategoryBoardMutationService categoryBoardMutationService;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void boardEndpointReturnsGroupedJarsAndUncategorizedCategories() throws Exception {
        UserResponse registered = authService.register(registerRequest("board_api"));
        TokenResponse token = authService.login(loginRequest(registered.getUsername()));
        User user = userRepository.findById(registered.getId()).orElseThrow();
        Workspace workspace = workspace(user, "board api workspace");
        Jar food = jar(workspace, "FOOD", "Ăn uống", 1, true);
        Jar travel = jar(workspace, "TRAVEL", "Di chuyển", 2, true);
        category(workspace, food, "Cà phê", 2, true, false);
        category(workspace, food, "Ăn sáng", 1, true, false);
        category(workspace, travel, "Xăng xe", 1, true, false);
        category(workspace, null, "Chưa phân loại", 1, true, false);

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/category-board")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceId").value(workspace.getId().toString()))
                .andExpect(jsonPath("$.data.jars[0].name").value("Ăn uống"))
                .andExpect(jsonPath("$.data.jars[0].categories[0].name").value("Ăn sáng"))
                .andExpect(jsonPath("$.data.jars[0].categories[1].name").value("Cà phê"))
                .andExpect(jsonPath("$.data.jars[1].name").value("Di chuyển"))
                .andExpect(jsonPath("$.data.uncategorizedGroup.groupKey").value("UNCATEGORIZED"))
                .andExpect(jsonPath("$.data.uncategorizedGroup.name").value("Chưa gắn hũ"))
                .andExpect(jsonPath("$.data.uncategorizedGroup.categories[0].name").value("Chưa phân loại"))
                .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    void boardSupportsEmptyJarArchivedStatsWarningOrderingAndCanFlags() {
        TestContext ctx = context("board_options");
        Jar empty = jar(ctx.workspace(), "EMPTY", "Empty", 1, true);
        Jar food = jar(ctx.workspace(), "FOOD", "Food", 2, true);
        Category active = category(ctx.workspace(), food, "Active", 2, true, false);
        Category first = category(ctx.workspace(), food, "First", 1, true, false);
        Category archived = category(ctx.workspace(), food, "Archived", 3, false, true);

        CategoryBoardResponse defaultBoard = categoryBoardService.getBoard(
                ctx.workspace().getId(), false, true, true, true, null, null, null, ctx.user().getId());

        assertThat(defaultBoard.warnings()).contains("HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE");
        assertThat(defaultBoard.period().label()).isEqualTo("month");
        assertThat(defaultBoard.jars()).extracting("jarId").containsExactly(empty.getId(), food.getId());
        assertThat(defaultBoard.jars().get(1).categories()).extracting("categoryId").containsExactly(first.getId(), active.getId());
        assertThat(defaultBoard.jars().get(1).categories()).allSatisfy(item -> {
            assertThat(item.canMove()).isTrue();
            assertThat(item.canArchive()).isTrue();
            assertThat(item.canDelete()).isFalse();
        });

        CategoryBoardResponse withoutEmpty = categoryBoardService.getBoard(
                ctx.workspace().getId(), false, false, true, false, null, null, null, ctx.user().getId());
        assertThat(withoutEmpty.jars()).extracting("jarId").containsExactly(food.getId());
        assertThat(withoutEmpty.boardStats()).isNull();

        CategoryBoardResponse withArchived = categoryBoardService.getBoard(
                ctx.workspace().getId(), true, true, true, false, null, null, null, ctx.user().getId());
        assertThat(withArchived.jars().get(1).categories()).extracting("categoryId")
                .containsExactly(first.getId(), active.getId(), archived.getId());
        assertThat(withArchived.jars().get(1).categories().get(2).canMove()).isFalse();
    }

    @Test
    void boardStatsAggregatePeriodRowsAndExcludeNonPostedExpenseNoise() {
        TestContext ctx = context("board_stats");
        Jar food = jar(ctx.workspace(), "FOOD", "Ăn uống", 1, true);
        Jar travel = jar(ctx.workspace(), "TRAVEL", "Di chuyển", 2, true);
        Category coffee = category(ctx.workspace(), food, "Cà phê", 1, true, false);
        Category breakfast = category(ctx.workspace(), food, "Ăn sáng", 2, true, false);
        Category fuel = category(ctx.workspace(), travel, "Xăng xe", 1, true, false);
        Category noJar = category(ctx.workspace(), null, "Chưa gắn", 1, true, false);
        Category income = category(ctx.workspace(), null, "Lương", 3, true, false, CategoryType.INCOME);
        TestContext other = context("board_stats_other");
        Category otherCategory = category(other.workspace(), null, "Other", 1, true, false);

        tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.POSTED, "25000", "2026-08-02", false);
        tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.POSTED, "30000", "2026-08-10", false);
        tx(ctx, breakfast, TransactionType.EXPENSE, TransactionStatus.POSTED, "45000", "2026-08-03", false);
        tx(ctx, fuel, TransactionType.EXPENSE, TransactionStatus.POSTED, "100000", "2026-08-04", false);
        tx(ctx, noJar, TransactionType.EXPENSE, TransactionStatus.POSTED, "20000", "2026-08-05", false);
        tx(ctx, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "15000", "2026-08-06", false);
        tx(ctx, income, TransactionType.INCOME, TransactionStatus.POSTED, "1000000", "2026-08-07", false);
        tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.DRAFT, "999999", "2026-08-08", false);
        tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.POSTED, "999999", "2026-08-08", true);
        tx(ctx, coffee, TransactionType.TRANSFER, TransactionStatus.POSTED, "999999", "2026-08-08", false);
        tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.POSTED, "999999", "2026-07-31", false);
        tx(other, otherCategory, TransactionType.EXPENSE, TransactionStatus.POSTED, "999999", "2026-08-02", false);

        CategoryBoardResponse board = categoryBoardService.getBoard(
                ctx.workspace().getId(), false, true, true, true, "2026-08-01", "2026-08-31", null, ctx.user().getId());

        assertThat(board.period().from()).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(board.period().to()).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(board.boardStats().totalExpense()).isEqualByComparingTo("235000");
        assertThat(board.boardStats().totalIncome()).isEqualByComparingTo("1000000");
        assertThat(board.boardStats().categorizedExpense()).isEqualByComparingTo("200000");
        assertThat(board.boardStats().uncategorizedExpense()).isEqualByComparingTo("35000");
        assertThat(board.boardStats().uncategorizedCount()).isEqualTo(2);

        var foodGroup = board.jars().get(0);
        assertThat(foodGroup.stats().totalExpense()).isEqualByComparingTo("100000");
        assertThat(foodGroup.stats().expenseTransactionCount()).isEqualTo(3);
        assertThat(foodGroup.stats().percentageOfTotalExpense()).isEqualByComparingTo("42.55");
        assertThat(foodGroup.stats().lastUsedAt()).isEqualTo(LocalDate.parse("2026-08-10"));
        assertThat(foodGroup.stats().usedCategoryCount()).isEqualTo(2);
        assertThat(foodGroup.categories().get(0).stats().totalExpense()).isEqualByComparingTo("55000");
        assertThat(foodGroup.categories().get(0).stats().expenseTransactionCount()).isEqualTo(2);
        assertThat(foodGroup.categories().get(0).stats().percentageOfTotalExpense()).isEqualByComparingTo("23.40");
        assertThat(foodGroup.categories().get(0).stats().lastUsedAt()).isEqualTo(LocalDate.parse("2026-08-10"));

        assertThat(board.uncategorizedGroup().stats().totalExpense()).isEqualByComparingTo("35000");
        assertThat(board.uncategorizedGroup().stats().transactionCount()).isEqualTo(2);
        assertThat(board.uncategorizedGroup().categories().get(0).stats().totalExpense()).isEqualByComparingTo("20000");
    }

    @Test
    void boardStatsDefaultRangeZeroTotalsAndDateValidationWork() {
        TestContext ctx = context("board_stats_zero");
        Jar jar = jar(ctx.workspace(), "ZERO", "Zero", 1, true);
        category(ctx.workspace(), jar, "Unused", 1, true, false);

        CategoryBoardResponse board = categoryBoardService.getBoard(
                ctx.workspace().getId(), false, true, true, true, null, null, null, ctx.user().getId());

        assertThat(board.period().from()).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(board.period().to()).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(board.boardStats().totalExpense()).isEqualByComparingTo("0");
        assertThat(board.jars().get(0).stats().percentageOfTotalExpense()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> categoryBoardService.getBoard(
                ctx.workspace().getId(), false, true, true, true, "2026-08-31", "2026-08-01", null, ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_DATE_RANGE");
        assertThatThrownBy(() -> categoryBoardService.getBoard(
                ctx.workspace().getId(), false, true, true, true, null, null, "forever", ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
    }

    @Test
    void moveCategoryPersistsTargetGroupOrderAndKeepsHistoricalTransactions() {
        TestContext ctx = context("board_move");
        Jar food = jar(ctx.workspace(), "FOOD", "Ăn uống", 1, true);
        Jar personal = jar(ctx.workspace(), "PERSONAL", "Cá nhân", 2, true);
        Category coffee = category(ctx.workspace(), food, "Cà phê", 1, true, false);
        Category a = category(ctx.workspace(), personal, "A", 0, true, false);
        Category b = category(ctx.workspace(), personal, "B", 1, true, false);
        Category c = category(ctx.workspace(), personal, "C", 2, true, false);
        Transaction tx = tx(ctx, coffee, TransactionType.EXPENSE, TransactionStatus.POSTED, "25000", "2026-08-02", false);
        long txCount = transactionRepository.count();

        CategoryBoardResponse board = categoryBoardMutationService.moveCategory(
                ctx.workspace().getId(), coffee.getId(), new CategoryMoveRequest(personal.getId(), 1, true), ctx.user().getId());

        assertThat(categoryRepository.findById(coffee.getId()).orElseThrow().getJar().getId()).isEqualTo(personal.getId());
        assertThat(transactionRepository.findById(tx.getId()).orElseThrow().getCategory().getId()).isEqualTo(coffee.getId());
        assertThat(transactionRepository.count()).isEqualTo(txCount);
        assertThat(board.jars().get(1).categories()).extracting("categoryId")
                .containsExactly(a.getId(), coffee.getId(), b.getId(), c.getId());
        assertThat(board.jars().get(1).stats().totalExpense()).isEqualByComparingTo("25000");
        assertThat(board.warnings()).contains("HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE");

        CategoryBoardResponse uncategorized = categoryBoardMutationService.moveCategory(
                ctx.workspace().getId(), coffee.getId(), new CategoryMoveRequest(null, null, false), ctx.user().getId());
        assertThat(categoryRepository.findById(coffee.getId()).orElseThrow().getJar()).isNull();
        assertThat(uncategorized.uncategorizedGroup().categories()).extracting("categoryId").contains(coffee.getId());
    }

    @Test
    void reorderJarsAndCategoriesPersistBoardOrderWithoutChangingStats() {
        TestContext ctx = context("board_reorder");
        Jar first = jar(ctx.workspace(), "A", "A", 0, true);
        Jar second = jar(ctx.workspace(), "B", "B", 1, true);
        Jar third = jar(ctx.workspace(), "C", "C", 2, true);
        Category a = category(ctx.workspace(), first, "A", 0, true, false);
        Category b = category(ctx.workspace(), first, "B", 1, true, false);
        Category c = category(ctx.workspace(), first, "C", 2, true, false);
        Category u1 = category(ctx.workspace(), null, "U1", 0, true, false);
        Category u2 = category(ctx.workspace(), null, "U2", 1, true, false);
        tx(ctx, a, TransactionType.EXPENSE, TransactionStatus.POSTED, "10000", "2026-08-02", false);
        tx(ctx, b, TransactionType.EXPENSE, TransactionStatus.POSTED, "20000", "2026-08-02", false);

        CategoryBoardResponse jars = categoryBoardMutationService.reorderJars(
                ctx.workspace().getId(), new JarBoardReorderRequest(List.of(third.getId(), first.getId(), second.getId()), true), ctx.user().getId());
        assertThat(jars.jars()).extracting("jarId").containsExactly(third.getId(), first.getId(), second.getId());
        assertThat(jars.boardStats().totalExpense()).isEqualByComparingTo("30000");

        CategoryBoardResponse categories = categoryBoardMutationService.reorderCategories(
                ctx.workspace().getId(), new CategoryGroupReorderRequest(first.getId(), List.of(b.getId(), c.getId(), a.getId()), true), ctx.user().getId());
        assertThat(categories.jars().get(1).categories()).extracting("categoryId")
                .containsExactly(b.getId(), c.getId(), a.getId());
        assertThat(categories.boardStats().totalExpense()).isEqualByComparingTo("30000");

        CategoryBoardResponse uncategorized = categoryBoardMutationService.reorderCategories(
                ctx.workspace().getId(), new CategoryGroupReorderRequest(null, List.of(u2.getId(), u1.getId()), false), ctx.user().getId());
        assertThat(uncategorized.uncategorizedGroup().categories()).extracting("categoryId")
                .containsExactly(u2.getId(), u1.getId());
    }

    @Test
    void moveAndReorderRejectUnsafeRequestsWithoutPartialUpdates() {
        TestContext ctx = context("board_validation");
        TestContext other = context("board_validation_other");
        Jar food = jar(ctx.workspace(), "FOOD", "Food", 0, true);
        Jar target = jar(ctx.workspace(), "TARGET", "Target", 1, true);
        Jar inactive = jar(ctx.workspace(), "OLD", "Old", 2, false);
        jar(ctx.workspace(), "EXTRA", "Extra", 3, true);
        Jar otherJar = jar(other.workspace(), "OTHER", "Other", 0, true);
        Category active = category(ctx.workspace(), food, "Active", 0, true, false);
        Category archived = category(ctx.workspace(), food, "Archived", 1, false, true);
        Category otherCategory = category(other.workspace(), otherJar, "Other", 0, true, false);
        UUID originalJar = active.getJar().getId();

        assertThatThrownBy(() -> categoryBoardMutationService.moveCategory(
                ctx.workspace().getId(), archived.getId(), new CategoryMoveRequest(target.getId(), null, false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_ARCHIVED");
        assertThatThrownBy(() -> categoryBoardMutationService.moveCategory(
                ctx.workspace().getId(), active.getId(), new CategoryMoveRequest(inactive.getId(), null, false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_ARCHIVED");
        assertThatThrownBy(() -> categoryBoardMutationService.moveCategory(
                ctx.workspace().getId(), active.getId(), new CategoryMoveRequest(otherJar.getId(), null, false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_MOVE_TARGET_JAR_NOT_FOUND");
        assertThat(categoryRepository.findById(active.getId()).orElseThrow().getJar().getId()).isEqualTo(originalJar);

        assertThatThrownBy(() -> categoryBoardMutationService.reorderJars(
                ctx.workspace().getId(), new JarBoardReorderRequest(List.of(food.getId(), food.getId(), target.getId()), false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_REORDER_DUPLICATE_JAR");
        assertThatThrownBy(() -> categoryBoardMutationService.reorderJars(
                ctx.workspace().getId(), new JarBoardReorderRequest(List.of(food.getId(), target.getId()), false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_REORDER_INCOMPLETE");
        assertThatThrownBy(() -> categoryBoardMutationService.reorderCategories(
                ctx.workspace().getId(), new CategoryGroupReorderRequest(food.getId(), List.of(active.getId(), otherCategory.getId()), false), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isIn("CATEGORY_NOT_FOUND", "CATEGORY_REORDER_INCOMPLETE_GROUP");
        assertThat(categoryRepository.findById(active.getId()).orElseThrow().getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void boardIsWorkspaceScopedAndReadOnlyWhenRelationPointsOutsideWorkspace() {
        TestContext owner = context("board_owner");
        TestContext other = context("board_other");
        Jar ownJar = jar(owner.workspace(), "OWN", "Own", 1, true);
        Jar otherJar = jar(other.workspace(), "OTHER", "Other", 1, true);
        Category own = category(owner.workspace(), ownJar, "Own category", 1, true, false);
        Category broken = category(owner.workspace(), otherJar, "Broken relation", 2, true, false);
        category(other.workspace(), otherJar, "Other category", 1, true, false);
        transactionRepository.save(Transaction.builder()
                .workspace(owner.workspace())
                .createdByUser(owner.user())
                .category(own)
                .transactionType(TransactionType.EXPENSE)
                .amount(BigDecimal.ONE)
                .transactionDate(LocalDate.parse("2026-08-01"))
                .build());
        long categoryCount = categoryRepository.countByWorkspaceId(owner.workspace().getId());
        long jarCount = jarRepository.countByWorkspaceId(owner.workspace().getId());
        long transactionCount = transactionRepository.count();

        CategoryBoardResponse board = categoryBoardService.getBoard(
                owner.workspace().getId(), false, true, true, false, null, null, null, owner.user().getId());

        assertThat(board.jars()).extracting("jarId").containsExactly(ownJar.getId());
        assertThat(board.jars().getFirst().categories()).extracting("categoryId").containsExactly(own.getId());
        assertThat(board.uncategorizedGroup().categories()).extracting("categoryId").containsExactly(broken.getId());
        assertThat(board.warnings()).contains("CATEGORY_BOARD_PARTIAL_DATA");
        assertThat(board.toString()).doesNotContain(otherJar.getName(), "Other category");
        assertThat(categoryRepository.countByWorkspaceId(owner.workspace().getId())).isEqualTo(categoryCount);
        assertThat(jarRepository.countByWorkspaceId(owner.workspace().getId())).isEqualTo(jarCount);
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
        assertThat(categoryRepository.findById(broken.getId()).orElseThrow().getJar().getId()).isEqualTo(otherJar.getId());
    }

    private TestContext context(String prefix) {
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + UUID.randomUUID().toString().substring(0, 8))
                .email(prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .fullName("Board User")
                .build());
        Workspace workspace = workspace(user, prefix + " workspace");
        return new TestContext(user, workspace);
    }

    private Workspace workspace(User user, String name) {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(name)
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return workspace;
    }

    private Jar jar(Workspace workspace, String code, String name, int order, boolean active) {
        return jarRepository.save(Jar.builder()
                .workspace(workspace)
                .code(code)
                .name(name)
                .allocationPercent(BigDecimal.ZERO)
                .displayOrder(order)
                .isActive(active)
                .build());
    }

    private Category category(Workspace workspace, Jar jar, String name, int order, boolean active, boolean archived) {
        return category(workspace, jar, name, order, active, archived, CategoryType.EXPENSE);
    }

    private Category category(Workspace workspace, Jar jar, String name, int order, boolean active, boolean archived, CategoryType type) {
        return categoryRepository.save(Category.builder()
                .workspace(workspace)
                .jar(jar)
                .name(name)
                .categoryType(type)
                .icon("folder")
                .displayOrder(order)
                .isActive(active)
                .isArchived(archived)
                .build());
    }

    private Transaction tx(
            TestContext ctx,
            Category category,
            TransactionType type,
            TransactionStatus status,
            String amount,
            String date,
            boolean deleted) {
        return transactionRepository.save(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .category(category)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDate.parse(date))
                .deletedAt(deleted ? Instant.parse("2026-08-09T00:00:00Z") : null)
                .build());
    }

    private RegisterRequest registerRequest(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest req = new RegisterRequest();
        req.setUsername(prefix + "_" + suffix);
        req.setEmail(prefix + "_" + suffix + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Board User");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
