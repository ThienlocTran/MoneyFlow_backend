package com.moneyflowbackend.auth.service;

import com.moneyflowbackend.auth.dto.*;
import com.moneyflowbackend.auth.model.*;
import com.moneyflowbackend.auth.repository.*;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.security.JwtTokenProvider;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.*;
import com.moneyflowbackend.workspace.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Email/username hoặc mật khẩu không chính xác.";

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspacePersonRepository workspacePersonRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JarRepository jarRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            WorkspaceRepository workspaceRepository,
            WorkspacePersonRepository workspacePersonRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            JarRepository jarRepository,
            CategoryRepository categoryRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspacePersonRepository = workspacePersonRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.jarRepository = jarRepository;
        this.categoryRepository = categoryRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String username = req.getUsername().trim().toLowerCase();
        String email = req.getEmail().trim().toLowerCase();
        String fullName = req.getFullName().trim();

        if (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "Username đã tồn tại", Map.of("username", "Username đã tồn tại"));
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email đã tồn tại", Map.of("email", "Email đã tồn tại"));
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        AuthAccount authAcc = AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.LOCAL)
                .providerSubject(username)
                .build();
        authAccountRepository.save(authAcc);

        Workspace workspace = Workspace.builder()
                .name("Tài chính cá nhân của " + user.getFullName())
                .workspaceType(WorkspaceType.PERSONAL)
                .currency("VND")
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .createdByUser(user)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspacePerson person = WorkspacePerson.builder()
                .workspace(workspace)
                .linkedUser(user)
                .displayName(user.getFullName())
                .personKind(PersonKind.MEMBER)
                .isActive(true)
                .build();
        person = workspacePersonRepository.save(person);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .person(person)
                .role(WorkspaceRole.OWNER)
                .memberStatus("ACTIVE")
                .build();
        workspaceMemberRepository.save(member);

        List<Jar> defaultJars = new ArrayList<>();
        defaultJars.add(createJar(workspace, "NEC", "Thiết yếu", 55, 1));
        defaultJars.add(createJar(workspace, "FFA", "Tự do tài chính", 10, 2));
        defaultJars.add(createJar(workspace, "LTSS", "Tiết kiệm dài hạn", 10, 3));
        defaultJars.add(createJar(workspace, "EDU", "Giáo dục", 10, 4));
        defaultJars.add(createJar(workspace, "PLAY", "Hưởng thụ", 10, 5));
        defaultJars.add(createJar(workspace, "GIVE", "Cho đi", 5, 6));
        defaultJars = jarRepository.saveAll(defaultJars);

        Jar nec = findJar(defaultJars, "NEC");
        Jar ffa = findJar(defaultJars, "FFA");
        Jar ltss = findJar(defaultJars, "LTSS");
        Jar edu = findJar(defaultJars, "EDU");
        Jar play = findJar(defaultJars, "PLAY");
        Jar give = findJar(defaultJars, "GIVE");

        List<Category> cats = new ArrayList<>();
        cats.add(createCategory(workspace, null, "Lương", CategoryType.INCOME, "salary", 1));
        cats.add(createCategory(workspace, null, "Gia đình chu cấp", CategoryType.INCOME, "family", 2));
        cats.add(createCategory(workspace, null, "Freelance", CategoryType.INCOME, "freelance", 3));
        cats.add(createCategory(workspace, null, "Kinh doanh", CategoryType.INCOME, "business", 4));
        cats.add(createCategory(workspace, null, "Thưởng", CategoryType.INCOME, "bonus", 5));
        cats.add(createCategory(workspace, null, "Hoàn tiền", CategoryType.INCOME, "refund", 6));
        cats.add(createCategory(workspace, null, "Được tặng", CategoryType.INCOME, "gift", 7));
        cats.add(createCategory(workspace, null, "Khác", CategoryType.INCOME, "other", 8));

        cats.add(createCategory(workspace, nec, "Ăn uống", CategoryType.EXPENSE, "food", 9));
        cats.add(createCategory(workspace, nec, "Xăng xe", CategoryType.EXPENSE, "car", 10));
        cats.add(createCategory(workspace, nec, "Gửi xe", CategoryType.EXPENSE, "parking", 11));
        cats.add(createCategory(workspace, nec, "Đi chợ", CategoryType.EXPENSE, "grocery", 12));
        cats.add(createCategory(workspace, nec, "Tiền trọ", CategoryType.EXPENSE, "rent", 13));
        cats.add(createCategory(workspace, nec, "Tiền điện", CategoryType.EXPENSE, "electric", 14));
        cats.add(createCategory(workspace, nec, "Tiền nước", CategoryType.EXPENSE, "water", 15));
        cats.add(createCategory(workspace, nec, "Internet/Wifi", CategoryType.EXPENSE, "wifi", 16));
        cats.add(createCategory(workspace, nec, "Điện thoại/4G", CategoryType.EXPENSE, "mobile", 17));
        cats.add(createCategory(workspace, nec, "Y tế", CategoryType.EXPENSE, "medical", 18));
        cats.add(createCategory(workspace, nec, "Đồ dùng trong nhà", CategoryType.EXPENSE, "household", 19));

        cats.add(createCategory(workspace, ffa, "Đầu tư", CategoryType.EXPENSE, "invest", 20));
        cats.add(createCategory(workspace, ffa, "Vốn kinh doanh", CategoryType.EXPENSE, "capital", 21));
        cats.add(createCategory(workspace, ffa, "Tài sản", CategoryType.EXPENSE, "asset", 22));

        cats.add(createCategory(workspace, ltss, "Quỹ khẩn cấp", CategoryType.EXPENSE, "emergency", 23));
        cats.add(createCategory(workspace, ltss, "Mục tiêu tiết kiệm", CategoryType.EXPENSE, "saving-goal", 24));
        cats.add(createCategory(workspace, ltss, "Mua sắm lớn", CategoryType.EXPENSE, "big-buy", 25));

        cats.add(createCategory(workspace, edu, "Học phí", CategoryType.EXPENSE, "tuition", 26));
        cats.add(createCategory(workspace, edu, "Sách và tài liệu", CategoryType.EXPENSE, "books", 27));
        cats.add(createCategory(workspace, edu, "Khóa học", CategoryType.EXPENSE, "courses", 28));
        cats.add(createCategory(workspace, edu, "Học tiếng Anh", CategoryType.EXPENSE, "english", 29));
        cats.add(createCategory(workspace, edu, "Học lập trình", CategoryType.EXPENSE, "coding", 30));

        cats.add(createCategory(workspace, play, "Cafe", CategoryType.EXPENSE, "coffee", 31));
        cats.add(createCategory(workspace, play, "Ăn ngoài", CategoryType.EXPENSE, "dining", 32));
        cats.add(createCategory(workspace, play, "Đi chơi", CategoryType.EXPENSE, "hangout", 33));
        cats.add(createCategory(workspace, play, "Xem phim", CategoryType.EXPENSE, "movies", 34));
        cats.add(createCategory(workspace, play, "Game", CategoryType.EXPENSE, "games", 35));
        cats.add(createCategory(workspace, play, "Quần áo", CategoryType.EXPENSE, "clothes", 36));
        cats.add(createCategory(workspace, play, "Mỹ phẩm", CategoryType.EXPENSE, "makeup", 37));
        cats.add(createCategory(workspace, play, "Du lịch", CategoryType.EXPENSE, "travel", 38));

        cats.add(createCategory(workspace, give, "Gia đình", CategoryType.EXPENSE, "family-help", 39));
        cats.add(createCategory(workspace, give, "Quà tặng", CategoryType.EXPENSE, "gifts", 40));
        cats.add(createCategory(workspace, give, "Từ thiện", CategoryType.EXPENSE, "charity", 41));
        cats.add(createCategory(workspace, give, "Lì xì", CategoryType.EXPENSE, "lixi", 42));
        cats.add(createCategory(workspace, give, "Hỗ trợ người khác", CategoryType.EXPENSE, "support", 43));
        categoryRepository.saveAll(cats);

        Wallet wallet = Wallet.builder()
                .workspace(workspace)
                .name("Tiền mặt")
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .includeInTotal(true)
                .build();
        walletRepository.save(wallet);

        return mapToUserResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String identifier = req.getIdentifier().trim().toLowerCase();
        User user = userRepository.findByUsernameOrEmailIgnoreCaseAndDeletedAtIsNull(identifier)
                .orElseThrow(this::invalidCredentials);

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException("ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa", HttpStatus.FORBIDDEN);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        String refreshToken = req.getRefreshToken();
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED);
        }

        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED));

        if (storedToken.getRevokedAt() != null) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token đã bị thu hồi", HttpStatus.UNAUTHORIZED);
        }
        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("REFRESH_TOKEN_EXPIRED", "Refresh token đã hết hạn", HttpStatus.UNAUTHORIZED);
        }

        User user = storedToken.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED);
        }

        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED));
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED);
        }
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UserResponse req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND));

        user.setFullName(req.getFullName().trim());
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        return mapToUserResponse(user);
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        RefreshToken rfToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(tokenProvider.getRefreshTokenExpiresAt())
                .build();
        refreshTokenRepository.save(rfToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpiresInSeconds())
                .user(mapToUserResponse(user))
                .build();
    }

    private Jar findJar(List<Jar> jars, String code) {
        return jars.stream()
                .filter(j -> j.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing default jar " + code));
    }

    private Jar createJar(Workspace workspace, String code, String name, int percent, int order) {
        return Jar.builder()
                .workspace(workspace)
                .code(code)
                .name(name)
                .allocationPercent(BigDecimal.valueOf(percent))
                .displayOrder(order)
                .isActive(true)
                .build();
    }

    private Category createCategory(Workspace workspace, Jar jar, String name, CategoryType type, String icon, int order) {
        return Category.builder()
                .workspace(workspace)
                .jar(jar)
                .name(name)
                .categoryType(type)
                .icon(icon)
                .displayOrder(order)
                .isActive(true)
                .build();
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", INVALID_CREDENTIALS_MESSAGE, HttpStatus.UNAUTHORIZED);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus().name())
                .build();
    }
}
