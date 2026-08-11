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
import com.moneyflowbackend.categoryboard.service.CategoryBoardService;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                ctx.workspace().getId(), false, true, true, true, ctx.user().getId());

        assertThat(defaultBoard.warnings()).contains("CATEGORY_BOARD_STATS_NOT_IMPLEMENTED", "HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE");
        assertThat(defaultBoard.jars()).extracting("jarId").containsExactly(empty.getId(), food.getId());
        assertThat(defaultBoard.jars().get(1).categories()).extracting("categoryId").containsExactly(first.getId(), active.getId());
        assertThat(defaultBoard.jars().get(1).categories()).allSatisfy(item -> {
            assertThat(item.canMove()).isTrue();
            assertThat(item.canArchive()).isTrue();
            assertThat(item.canDelete()).isFalse();
        });

        CategoryBoardResponse withoutEmpty = categoryBoardService.getBoard(
                ctx.workspace().getId(), false, false, true, false, ctx.user().getId());
        assertThat(withoutEmpty.jars()).extracting("jarId").containsExactly(food.getId());

        CategoryBoardResponse withArchived = categoryBoardService.getBoard(
                ctx.workspace().getId(), true, true, true, false, ctx.user().getId());
        assertThat(withArchived.jars().get(1).categories()).extracting("categoryId")
                .containsExactly(first.getId(), active.getId(), archived.getId());
        assertThat(withArchived.jars().get(1).categories().get(2).canMove()).isFalse();
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
                owner.workspace().getId(), false, true, true, false, owner.user().getId());

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
        return categoryRepository.save(Category.builder()
                .workspace(workspace)
                .jar(jar)
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .icon("folder")
                .displayOrder(order)
                .isActive(active)
                .isArchived(archived)
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
