package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.dto.CategoryKeywordRequest;
import com.moneyflowbackend.category.dto.CategoryKeywordResponse;
import com.moneyflowbackend.category.dto.CategoryRequest;
import com.moneyflowbackend.category.dto.CategoryResponse;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.category.service.CategoryKeywordService;
import com.moneyflowbackend.category.service.CategoryService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.dto.JarAllocationRequest;
import com.moneyflowbackend.jar.dto.JarListResponse;
import com.moneyflowbackend.jar.dto.JarMonthlyDetailResponse;
import com.moneyflowbackend.jar.dto.JarMonthlySummaryResponse;
import com.moneyflowbackend.jar.dto.JarReorderRequest;
import com.moneyflowbackend.jar.dto.JarRequest;
import com.moneyflowbackend.jar.dto.JarResponse;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.jar.service.JarService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JarCategoryModuleIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired JarService jarService;
    @Autowired CategoryService categoryService;
    @Autowired CategoryKeywordService keywordService;

    @Test
    void registerBootstrapsSixJarsFortyThreeCategoriesAndIsolatesWorkspaces() {
        UserResponse first = authService.register(registerRequest("jar_cat_bootstrap_a"));
        UserResponse second = authService.register(registerRequest("jar_cat_bootstrap_b"));

        Workspace firstWorkspace = workspaceRepository.findAllByUserId(first.getId()).get(0);
        Workspace secondWorkspace = workspaceRepository.findAllByUserId(second.getId()).get(0);

        List<Jar> jars = jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(firstWorkspace.getId());
        assertThat(jars).hasSize(6);
        assertThat(jars).extracting(Jar::getCode).containsExactly("NEC", "FFA", "LTSS", "EDU", "PLAY", "GIVE");
        assertThat(jars.stream().map(Jar::getAllocationPercent).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");

        List<Category> categories = categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(firstWorkspace.getId());
        assertThat(categories).hasSize(43);
        assertThat(categories.stream().filter(c -> c.getCategoryType() == CategoryType.INCOME)).allMatch(c -> c.getJar() == null);
        assertThat(categories.stream().filter(c -> c.getCategoryType() == CategoryType.EXPENSE)).allMatch(c -> c.getJar() != null && c.getJar().isActive());
        assertThat(jarRepository.countByWorkspaceId(secondWorkspace.getId())).isEqualTo(6);
        assertThat(categoryRepository.countByWorkspaceId(secondWorkspace.getId())).isEqualTo(43);
    }

    @Test
    void jarCrudAllocationReorderStatusAndAuthRulesWork() {
        TestContext owner = createContext("jar_owner", WorkspaceRole.OWNER);
        TestContext viewer = createContext("jar_viewer", WorkspaceRole.VIEWER);
        TestContext editor = createContext("jar_editor", WorkspaceRole.EDITOR);
        TestContext outsider = createContext("jar_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());

        JarResponse jar = jarService.create(owner.workspace().getId(), jarRequest("CUSTOM", "Custom", "25"), owner.user().getId());
        JarResponse second = jarService.create(owner.workspace().getId(), jarRequest("SECOND", "Second", "40"), owner.user().getId());

        JarListResponse list = jarService.list(owner.workspace().getId(), false, owner.user().getId());
        assertThat(list.getJars()).extracting(JarResponse::getId).containsExactly(jar.getId(), second.getId());
        assertThat(list.isAllocationValid()).isFalse();
        assertThat(list.getAllocationWarning()).isNotBlank();

        assertThatThrownBy(() -> jarService.create(owner.workspace().getId(), jarRequest("custom", "Other", "10"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_CODE_ALREADY_EXISTS");
        assertThatThrownBy(() -> jarService.create(owner.workspace().getId(), jarRequest("THIRD", "custom", "10"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_NAME_ALREADY_EXISTS");
        assertThatThrownBy(() -> jarService.create(owner.workspace().getId(), jarRequest("BAD", "Bad", "101"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_ALLOCATION_PERCENT");

        JarRequest update = jarRequest("IGNORED", "Custom Updated", "20");
        JarResponse updated = jarService.update(owner.workspace().getId(), jar.getId(), update, owner.user().getId());
        assertThat(updated.getCode()).isEqualTo("CUSTOM");
        assertThat(updated.getName()).isEqualTo("Custom Updated");

        JarReorderRequest reorder = new JarReorderRequest();
        JarReorderRequest.Item firstItem = new JarReorderRequest.Item();
        firstItem.setJarId(jar.getId());
        firstItem.setDisplayOrder(2);
        JarReorderRequest.Item secondItem = new JarReorderRequest.Item();
        secondItem.setJarId(second.getId());
        secondItem.setDisplayOrder(1);
        reorder.setItems(List.of(firstItem, secondItem));
        assertThat(jarService.reorder(owner.workspace().getId(), reorder, owner.user().getId()).getJars())
                .extracting(JarResponse::getId).containsExactly(second.getId(), jar.getId());

        JarAllocationRequest allocations = new JarAllocationRequest();
        JarAllocationRequest.Item allocation = new JarAllocationRequest.Item();
        allocation.setJarId(jar.getId());
        allocation.setAllocationPercent(new BigDecimal("5"));
        allocations.setItems(List.of(allocation));
        assertThat(jarService.updateAllocations(owner.workspace().getId(), allocations, owner.user().getId()).isAllocationValid()).isFalse();

        assertThatThrownBy(() -> jarService.toggleStatus(owner.workspace().getId(), second.getId(), false, owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_DEACTIVATION_BREAKS_ALLOCATION");
        JarResponse inactiveAllowed = jarService.create(owner.workspace().getId(), jarRequest("ZERO", "Zero", "0"), owner.user().getId());
        jarService.toggleStatus(owner.workspace().getId(), inactiveAllowed.getId(), false, owner.user().getId());
        assertThat(jarRepository.findById(inactiveAllowed.getId()).orElseThrow().isActive()).isFalse();

        CategoryResponse category = categoryService.create(owner.workspace().getId(), categoryRequest("Food", "EXPENSE", jar.getId()), owner.user().getId());
        assertThatThrownBy(() -> jarService.toggleStatus(owner.workspace().getId(), jar.getId(), false, owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_DEACTIVATION_BREAKS_ALLOCATION");
        assertThat(category.getJarId()).isEqualTo(jar.getId());

        assertThat(jarService.list(owner.workspace().getId(), false, viewer.user().getId()).getJars()).hasSize(2);
        assertThatThrownBy(() -> jarService.create(owner.workspace().getId(), jarRequest("VIEW", "Viewer", "1"), viewer.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> jarService.create(owner.workspace().getId(), jarRequest("EDIT", "Editor", "1"), editor.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> jarService.list(owner.workspace().getId(), false, outsider.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void categoryRulesCoverFiltersJarMappingArchiveQuickActionAndHistoricalTypeChange() {
        TestContext ctx = createContext("cat_rules", WorkspaceRole.OWNER);
        JarResponse jar = jarService.create(ctx.workspace().getId(), jarRequest("NEED", "Needs", "60"), ctx.user().getId());

        CategoryResponse income = categoryService.create(ctx.workspace().getId(), categoryRequest("Salary", "INCOME", null), ctx.user().getId());
        CategoryResponse expense = categoryService.create(ctx.workspace().getId(), categoryRequest("Lunch", "EXPENSE", jar.getId()), ctx.user().getId());

        assertThat(categoryService.list(ctx.workspace().getId(), "INCOME", null, null, null, null, false, false, ctx.user().getId()))
                .extracting(CategoryResponse::getId).containsExactly(income.getId());
        assertThat(categoryService.list(ctx.workspace().getId(), "EXPENSE", jar.getId(), null, null, null, false, false, ctx.user().getId()))
                .extracting(CategoryResponse::getId).containsExactly(expense.getId());

        assertThatThrownBy(() -> categoryService.create(ctx.workspace().getId(), categoryRequest("Bad Income", "INCOME", jar.getId()), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INCOME_CATEGORY_CANNOT_HAVE_JAR");
        assertThatThrownBy(() -> categoryService.create(ctx.workspace().getId(), categoryRequest("lunch", "EXPENSE", jar.getId()), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_NAME_ALREADY_EXISTS");

        JarResponse inactiveJar = jarService.create(ctx.workspace().getId(), jarRequest("OLD", "Old", "0"), ctx.user().getId());
        jarService.toggleStatus(ctx.workspace().getId(), inactiveJar.getId(), false, ctx.user().getId());
        assertThatThrownBy(() -> categoryService.create(ctx.workspace().getId(), categoryRequest("Old Cat", "EXPENSE", inactiveJar.getId()), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_INACTIVE");

        categoryService.toggleQuickAction(ctx.workspace().getId(), expense.getId(), true, ctx.user().getId());
        assertThat(categoryService.list(ctx.workspace().getId(), null, null, null, null, true, false, false, ctx.user().getId()))
                .extracting(CategoryResponse::getId).containsExactly(expense.getId());
        categoryService.toggleStatus(ctx.workspace().getId(), expense.getId(), false, ctx.user().getId());
        Category deactivated = categoryRepository.findById(expense.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.isQuickAction()).isFalse();
        assertThatThrownBy(() -> categoryService.toggleQuickAction(ctx.workspace().getId(), expense.getId(), true, ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_CANNOT_BE_QUICK_ACTION");

        categoryService.toggleArchived(ctx.workspace().getId(), expense.getId(), true, ctx.user().getId());
        assertThat(categoryRepository.findById(expense.getId()).orElseThrow().isArchived()).isTrue();
        assertThat(categoryService.list(ctx.workspace().getId(), null, null, null, null, null, true, true, ctx.user().getId()))
                .extracting(CategoryResponse::getId).contains(expense.getId());
        categoryService.toggleArchived(ctx.workspace().getId(), expense.getId(), false, ctx.user().getId());
        assertThat(categoryRepository.findById(expense.getId()).orElseThrow().isActive()).isFalse();

        CategoryResponse tracked = categoryService.create(ctx.workspace().getId(), categoryRequest("Tracked", "EXPENSE", jar.getId()), ctx.user().getId());
        transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .category(categoryRepository.findById(tracked.getId()).orElseThrow())
                .transactionType(TransactionType.EXPENSE)
                .amount(BigDecimal.ONE)
                .transactionDate(LocalDate.now())
                .build());
        assertThatThrownBy(() -> categoryService.update(ctx.workspace().getId(), tracked.getId(), categoryRequest("Tracked", "INCOME", null), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_TYPE_CHANGE_NOT_ALLOWED");
        assertThatThrownBy(() -> categoryService.delete(ctx.workspace().getId(), tracked.getId(), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_IN_USE");

        CategoryResponse unused = categoryService.create(ctx.workspace().getId(), categoryRequest("Unused", "EXPENSE", jar.getId()), ctx.user().getId());
        categoryService.delete(ctx.workspace().getId(), unused.getId(), ctx.user().getId());
        assertThat(categoryRepository.findById(unused.getId())).isEmpty();
    }

    @Test
    void keywordCrudNormalizesEnforcesWorkspaceUniquenessAndCategoryState() {
        TestContext ctx = createContext("keyword_rules", WorkspaceRole.OWNER);
        TestContext other = createContext("keyword_other", WorkspaceRole.OWNER);
        TestContext viewer = createContext("keyword_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(ctx.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        JarResponse jar = jarService.create(ctx.workspace().getId(), jarRequest("FOOD", "Food", "50"), ctx.user().getId());
        CategoryResponse food = categoryService.create(ctx.workspace().getId(), categoryRequest("Food", "EXPENSE", jar.getId()), ctx.user().getId());
        CategoryResponse transport = categoryService.create(ctx.workspace().getId(), categoryRequest("Transport", "EXPENSE", jar.getId()), ctx.user().getId());

        CategoryKeywordResponse keyword = keywordService.create(ctx.workspace().getId(), food.getId(), keywordRequest("  ca   phe  ", 5), ctx.user().getId());
        assertThat(keyword.getKeyword()).isEqualTo("ca phe");
        assertThat(keyword.isUserLearned()).isTrue();
        assertThat(keywordService.list(ctx.workspace().getId(), food.getId(), ctx.user().getId())).extracting(CategoryKeywordResponse::getId).containsExactly(keyword.getId());

        assertThatThrownBy(() -> keywordService.create(ctx.workspace().getId(), transport.getId(), keywordRequest("CA PHE", 1), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("KEYWORD_ALREADY_EXISTS");

        JarResponse otherJar = jarService.create(other.workspace().getId(), jarRequest("FOOD", "Food", "50"), other.user().getId());
        CategoryResponse otherFood = categoryService.create(other.workspace().getId(), categoryRequest("Food", "EXPENSE", otherJar.getId()), other.user().getId());
        assertThat(keywordService.create(other.workspace().getId(), otherFood.getId(), keywordRequest("CA PHE", 1), other.user().getId()).getKeyword()).isEqualTo("CA PHE");

        assertThatThrownBy(() -> keywordService.update(ctx.workspace().getId(), transport.getId(), keyword.getId(), keywordRequest("bus", 1), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("KEYWORD_CATEGORY_MISMATCH");

        CategoryKeywordResponse updated = keywordService.update(ctx.workspace().getId(), food.getId(), keyword.getId(), keywordRequest("coffee", 9), ctx.user().getId());
        assertThat(updated.getKeyword()).isEqualTo("coffee");
        assertThat(updated.getPriority()).isEqualTo(9);

        categoryService.toggleStatus(ctx.workspace().getId(), transport.getId(), false, ctx.user().getId());
        assertThatThrownBy(() -> keywordService.create(ctx.workspace().getId(), transport.getId(), keywordRequest("bus", 1), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_INACTIVE");
        categoryService.toggleStatus(ctx.workspace().getId(), transport.getId(), true, ctx.user().getId());
        categoryService.toggleArchived(ctx.workspace().getId(), transport.getId(), true, ctx.user().getId());
        assertThatThrownBy(() -> keywordService.create(ctx.workspace().getId(), transport.getId(), keywordRequest("bus", 1), ctx.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CATEGORY_ARCHIVED");

        assertThatThrownBy(() -> keywordService.create(ctx.workspace().getId(), food.getId(), keywordRequest("viewer", 1), viewer.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        keywordService.delete(ctx.workspace().getId(), food.getId(), keyword.getId(), ctx.user().getId());
        assertThat(categoryKeywordRepository.findAllByWorkspaceIdAndCategoryIdOrderByPriorityDescKeywordAsc(ctx.workspace().getId(), food.getId())).isEmpty();
    }

    @Test
    void monthlySummaryEvaluatesJarBudgetsAndCategoryMappingGaps() {
        TestContext ctx = createContext("jar_budget", WorkspaceRole.OWNER);
        Jar nec = jar(ctx, "NEC", "Essential", "55", 1);
        Jar ffa = jar(ctx, "FFA", "Freedom", "10", 2);
        Jar ltss = jar(ctx, "LTSS", "Saving", "10", 3);
        Jar edu = jar(ctx, "EDU", "Education", "10", 4);
        Jar play = jar(ctx, "PLAY", "Play", "10", 5);
        Jar give = jar(ctx, "GIVE", "Give", "5", 6);
        Category income = category(ctx, "Salary", CategoryType.INCOME, null, true);
        Category necCat = category(ctx, "Food", CategoryType.EXPENSE, nec, true);
        Category ffaCat = category(ctx, "Investment", CategoryType.EXPENSE, ffa, true);
        Category playCat = category(ctx, "Fun", CategoryType.EXPENSE, play, true);
        category(ctx, "Unmapped", CategoryType.EXPENSE, null, true);
        category(ctx, "PASE", CategoryType.EXPENSE, null, false);

        tx(ctx, income, TransactionType.INCOME, "1000", "2026-07-01");
        tx(ctx, necCat, TransactionType.EXPENSE, "500", "2026-07-02");
        tx(ctx, ffaCat, TransactionType.EXPENSE, "105", "2026-07-03");
        tx(ctx, playCat, TransactionType.EXPENSE, "200", "2026-07-04");

        JarMonthlySummaryResponse summary = jarService.monthlySummary(ctx.workspace().getId(), "2026-07", ctx.user().getId());

        assertThat(summary.getJars()).hasSize(6).extracting(JarMonthlySummaryResponse.Item::getJarCode)
                .containsExactly("NEC", "FFA", "LTSS", "EDU", "PLAY", "GIVE");
        assertThat(summary.getMonthlyIncome()).isEqualByComparingTo("1000");
        assertThat(summary.getJarsTotalTargetPercent()).isEqualByComparingTo("100");
        assertThat(summary.getJarsMappedCategoryCount()).isEqualTo(3);
        assertThat(summary.getUnmappedActiveExpenseCategoryCount()).isEqualTo(1);
        assertThat(summary.getInactiveUnmappedCategoryCount()).isEqualTo(1);
        assertThat(status(summary, "NEC")).isEqualTo("OK");
        assertThat(status(summary, "FFA")).isEqualTo("WARNING");
        assertThat(status(summary, "PLAY")).isEqualTo("OVER");
        assertThat(status(summary, "LTSS")).isEqualTo("NO_DATA");
        assertThat(summary.getOverallStatus()).isEqualTo("OVER");
    }

    @Test
    void monthlySummaryReturnsNoIncomeWhenIncomeIsMissing() {
        TestContext ctx = createContext("jar_no_income", WorkspaceRole.OWNER);
        Jar nec = jar(ctx, "NEC", "Essential", "55", 1);
        jar(ctx, "FFA", "Freedom", "10", 2);
        jar(ctx, "LTSS", "Saving", "10", 3);
        jar(ctx, "EDU", "Education", "10", 4);
        jar(ctx, "PLAY", "Play", "10", 5);
        jar(ctx, "GIVE", "Give", "5", 6);
        Category food = category(ctx, "Food", CategoryType.EXPENSE, nec, true);
        tx(ctx, food, TransactionType.EXPENSE, "100", "2026-07-04");

        JarMonthlySummaryResponse summary = jarService.monthlySummary(ctx.workspace().getId(), "2026-07", ctx.user().getId());

        assertThat(summary.getOverallStatus()).isEqualTo("NO_INCOME");
        assertThat(summary.getJars()).extracting(JarMonthlySummaryResponse.Item::getStatus).containsOnly("NO_INCOME");
    }

    @Test
    void monthlyDetailExplainsJarTotalsAndUsesOnlyActiveMappedCategories() {
        TestContext ctx = createContext("jar_detail", WorkspaceRole.OWNER);
        Jar nec = jar(ctx, "NEC", "Essential", "55", 1);
        Category income = category(ctx, "Salary", CategoryType.INCOME, null, true);
        Category food = category(ctx, "Food", CategoryType.EXPENSE, nec, true);
        Category rent = category(ctx, "Rent", CategoryType.EXPENSE, nec, true);
        Category old = category(ctx, "Old PASE", CategoryType.EXPENSE, nec, false);

        tx(ctx, income, TransactionType.INCOME, "1000", "2026-07-01", "Salary");
        tx(ctx, food, TransactionType.EXPENSE, "100", "2026-07-02", "Lunch");
        tx(ctx, food, TransactionType.EXPENSE, "50", "2026-07-03", "Coffee");
        tx(ctx, rent, TransactionType.EXPENSE, "300", "2026-07-04", "Rent");
        tx(ctx, old, TransactionType.EXPENSE, "999", "2026-07-05", "Historical inactive");

        JarMonthlyDetailResponse detail = jarService.monthlyDetail(ctx.workspace().getId(), nec.getId(), "2026-07", ctx.user().getId());

        assertThat(detail.getMonth()).isEqualTo("2026-07");
        assertThat(detail.getJarCode()).isEqualTo("NEC");
        assertThat(detail.getMonthlyIncome()).isEqualByComparingTo("1000");
        assertThat(detail.getTargetAmount()).isEqualByComparingTo("550");
        assertThat(detail.getActualAmount()).isEqualByComparingTo("450");
        assertThat(detail.getRemainingAmount()).isEqualByComparingTo("100");
        assertThat(detail.getOverAmount()).isEqualByComparingTo("0");
        assertThat(detail.getStatus()).isEqualTo("OK");
        assertThat(detail.getFormulaText()).contains("targetAmount", "actualAmount");
        assertThat(detail.getCategoryBreakdown()).extracting(JarMonthlyDetailResponse.CategoryBreakdown::getCategoryName)
                .containsExactly("Rent", "Food");
        assertThat(detail.getCategoryBreakdown().get(1).getTransactionCount()).isEqualTo(2);
        assertThat(detail.getCategoryBreakdown().get(1).getTotalAmount()).isEqualByComparingTo("150");
        assertThat(detail.getRecentTransactions()).extracting(JarMonthlyDetailResponse.RecentTransaction::getDescription)
                .containsExactly("Rent", "Coffee", "Lunch");
    }

    @Test
    void monthlyDetailEndpointReturnsWrappedBreakdown() throws Exception {
        String username = "jar_detail_api_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse user = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(user.getUsername()));
        Workspace workspace = workspaceRepository.findAllByUserId(user.getId()).get(0);
        Jar nec = jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspace.getId()).stream()
                .filter(jar -> "NEC".equals(jar.getCode()))
                .findFirst()
                .orElseThrow();
        Category income = categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(workspace.getId()).stream()
                .filter(category -> category.getCategoryType() == CategoryType.INCOME)
                .findFirst()
                .orElseThrow();
        Category food = categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(workspace.getId()).stream()
                .filter(category -> category.getCategoryType() == CategoryType.EXPENSE && category.getJar() != null && nec.getId().equals(category.getJar().getId()))
                .findFirst()
                .orElseThrow();
        transactionRepository.save(Transaction.builder()
                .workspace(workspace)
                .createdByUser(userRepository.findById(user.getId()).orElseThrow())
                .category(income)
                .transactionType(TransactionType.INCOME)
                .amount(new BigDecimal("1000"))
                .transactionDate(LocalDate.parse("2026-07-01"))
                .build());
        transactionRepository.save(Transaction.builder()
                .workspace(workspace)
                .createdByUser(userRepository.findById(user.getId()).orElseThrow())
                .category(food)
                .transactionType(TransactionType.EXPENSE)
                .amount(new BigDecimal("100"))
                .transactionDate(LocalDate.parse("2026-07-02"))
                .description("Lunch")
                .build());

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/jars/" + nec.getId() + "/monthly-detail")
                        .param("month", "2026-07")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-07"))
                .andExpect(jsonPath("$.data.jarCode").value("NEC"))
                .andExpect(jsonPath("$.data.categoryBreakdown[0].categoryName").value(food.getName()))
                .andExpect(jsonPath("$.data.recentTransactions[0].description").value("Lunch"));
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Jar Category Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
        return new TestContext(user, workspace);
    }

    private RegisterRequest registerRequest(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest req = new RegisterRequest();
        req.setUsername(prefix + "_" + suffix);
        req.setEmail(prefix + "_" + suffix + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Bootstrap User");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private JarRequest jarRequest(String code, String name, String allocation) {
        JarRequest req = new JarRequest();
        req.setCode(code);
        req.setName(name);
        req.setAllocationPercent(new BigDecimal(allocation));
        return req;
    }

    private CategoryRequest categoryRequest(String name, String type, UUID jarId) {
        CategoryRequest req = new CategoryRequest();
        req.setName(name);
        req.setType(type);
        req.setJarId(jarId);
        req.setIcon("folder");
        return req;
    }

    private CategoryKeywordRequest keywordRequest(String keyword, int priority) {
        CategoryKeywordRequest req = new CategoryKeywordRequest();
        req.setKeyword(keyword);
        req.setPriority(priority);
        return req;
    }

    private Jar jar(TestContext ctx, String code, String name, String allocation, int order) {
        return jarRepository.save(Jar.builder()
                .workspace(ctx.workspace())
                .code(code)
                .name(name)
                .allocationPercent(new BigDecimal(allocation))
                .displayOrder(order)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, Jar jar, boolean active) {
        return categoryRepository.save(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .jar(jar)
                .isActive(active)
                .build());
    }

    private void tx(TestContext ctx, Category category, TransactionType type, String amount, String date) {
        tx(ctx, category, type, amount, date, null);
    }

    private void tx(TestContext ctx, Category category, TransactionType type, String amount, String date, String description) {
        transactionRepository.save(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .category(category)
                .transactionType(type)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDate.parse(date))
                .description(description)
                .build());
    }

    private String status(JarMonthlySummaryResponse summary, String jarCode) {
        return summary.getJars().stream()
                .filter(item -> jarCode.equals(item.getJarCode()))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
