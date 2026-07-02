package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.RegisterRequest;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JarCategoryModuleIntegrationTests {

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

        jarService.toggleStatus(owner.workspace().getId(), second.getId(), false, owner.user().getId());
        assertThat(jarRepository.findById(second.getId()).orElseThrow().isActive()).isFalse();
        jarService.toggleStatus(owner.workspace().getId(), second.getId(), true, owner.user().getId());

        CategoryResponse category = categoryService.create(owner.workspace().getId(), categoryRequest("Food", "EXPENSE", jar.getId()), owner.user().getId());
        assertThatThrownBy(() -> jarService.toggleStatus(owner.workspace().getId(), jar.getId(), false, owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("JAR_HAS_ACTIVE_CATEGORIES");
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

        JarResponse inactiveJar = jarService.create(ctx.workspace().getId(), jarRequest("OLD", "Old", "1"), ctx.user().getId());
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

    private record TestContext(User user, Workspace workspace) {
    }
}
