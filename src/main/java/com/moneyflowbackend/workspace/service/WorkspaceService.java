package com.moneyflowbackend.workspace.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.dto.UserSearchResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceMemberResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRoleRequest;
import com.moneyflowbackend.workspace.model.InvitationStatus;
import com.moneyflowbackend.workspace.model.PersonKind;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceInvitation;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspacePerson;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.model.WorkspaceType;
import com.moneyflowbackend.workspace.repository.WorkspaceInvitationRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspacePersonRepository workspacePersonRepository;
    private final UserRepository userRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            WorkspacePersonRepository workspacePersonRepository,
            UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.workspacePersonRepository = workspacePersonRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getUserWorkspaces(UUID userId) {
        return workspaceRepository.findAllByUserId(userId).stream()
                .map(ws -> mapToResponse(ws, findActiveMember(ws.getId(), userId).getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceDetails(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> notFoundWorkspace());
        return mapToResponse(ws, member.getRole());
    }

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceRequest req, UUID userId) {
        User user = findUser(userId);
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(normalizeName(req == null ? null : req.getName()))
                .workspaceType(WorkspaceType.SHARED)
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return mapToResponse(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, WorkspaceRequest req, UUID userId) {
        requireOwner(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> notFoundWorkspace());
        workspace.setName(normalizeName(req == null ? null : req.getName()));
        workspace.setUpdatedAt(Instant.now());
        return mapToResponse(workspace, WorkspaceRole.OWNER);
    }

    public void verifyMembership(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
    }

    public WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw forbiddenAction();
        }
        return member;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String username) {
        String query = username == null ? "" : username.trim();
        if (query.length() < 2) {
            return List.of();
        }
        return userRepository.searchActiveByUsernamePrefix(query).stream()
                .limit(10)
                .map(this::mapUserSearch)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return workspaceMemberRepository.findAllByWorkspaceIdAndMemberStatusOrderByJoinedAtAsc(workspaceId, "ACTIVE").stream()
                .map(this::mapMember)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse updateMemberRole(UUID workspaceId, UUID memberId, WorkspaceRoleRequest req, UUID userId) {
        requireOwner(workspaceId, userId);
        WorkspaceRole role = parseRole(req == null ? null : req.getRole());
        WorkspaceMember member = activeMemberById(workspaceId, memberId);
        if (member.getRole() == WorkspaceRole.OWNER && role != WorkspaceRole.OWNER) {
            ensureNotLastOwner(workspaceId);
        }
        member.setRole(role);
        return mapMember(member);
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId, UUID userId) {
        requireOwner(workspaceId, userId);
        WorkspaceMember member = activeMemberById(workspaceId, memberId);
        if (member.getRole() == WorkspaceRole.OWNER) {
            ensureNotLastOwner(workspaceId);
        }
        member.setMemberStatus("REMOVED");
    }

    @Transactional
    public WorkspaceInvitationResponse invite(UUID workspaceId, WorkspaceInvitationRequest req, UUID userId) {
        WorkspaceMember inviter = requireOwner(workspaceId, userId);
        String username = req == null ? "" : req.getUsername();
        User invited = userRepository.findByUsernameAndDeletedAtIsNull(username == null ? "" : username.trim())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        if (invited.getId().equals(userId)) {
            throw new BusinessException("INVITE_SELF", "Không thể mời chính bạn");
        }
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, invited.getId()).isPresent()) {
            throw new BusinessException("ALREADY_MEMBER", "Người dùng đã là thành viên");
        }
        if (workspaceInvitationRepository.existsByWorkspaceIdAndInvitedUserIdAndInvitationStatus(workspaceId, invited.getId(), InvitationStatus.PENDING)) {
            throw new BusinessException("DUPLICATE_PENDING_INVITATION", "Lời mời đang chờ đã tồn tại");
        }
        WorkspaceInvitation invitation = workspaceInvitationRepository.save(WorkspaceInvitation.builder()
                .workspace(inviter.getWorkspace())
                .invitedByUser(inviter.getUser())
                .invitedUser(invited)
                .role(parseInviteRole(req == null ? null : req.getRole()))
                .invitationStatus(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build());
        return mapInvitation(invitation);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> pendingInvitations(UUID userId) {
        Instant now = Instant.now();
        return workspaceInvitationRepository.findAllByInvitedUserIdAndInvitationStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING).stream()
                .filter(invitation -> invitation.getExpiresAt().isAfter(now))
                .map(this::mapInvitation)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> workspacePendingInvitations(UUID workspaceId, UUID userId) {
        requireOwner(workspaceId, userId);
        return workspaceInvitationRepository.findAllByWorkspaceIdAndInvitationStatusOrderByCreatedAtDesc(workspaceId, InvitationStatus.PENDING).stream()
                .map(this::mapInvitation)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse acceptInvitation(UUID invitationId, UUID userId) {
        WorkspaceInvitation invitation = requirePendingInvitationForInvitee(invitationId, userId);
        UUID workspaceId = invitation.getWorkspace().getId();
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent()) {
            throw new BusinessException("ALREADY_MEMBER", "Người dùng đã là thành viên");
        }
        WorkspacePerson person = workspacePersonRepository.findByWorkspaceIdAndLinkedUserId(workspaceId, userId)
                .orElseGet(() -> workspacePersonRepository.save(WorkspacePerson.builder()
                        .workspace(invitation.getWorkspace())
                        .linkedUser(invitation.getInvitedUser())
                        .displayName(invitation.getInvitedUser().getFullName())
                        .personKind(PersonKind.MEMBER)
                        .isActive(true)
                        .build()));
        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(invitation.getWorkspace())
                .user(invitation.getInvitedUser())
                .person(person)
                .role(invitation.getRole())
                .build());
        invitation.setInvitationStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        return mapMember(member);
    }

    @Transactional
    public void rejectInvitation(UUID invitationId, UUID userId) {
        WorkspaceInvitation invitation = requirePendingInvitationForInvitee(invitationId, userId);
        invitation.setInvitationStatus(InvitationStatus.REJECTED);
        invitation.setRespondedAt(Instant.now());
    }

    @Transactional
    public void cancelInvitation(UUID workspaceId, UUID invitationId, UUID userId) {
        requireOwner(workspaceId, userId);
        WorkspaceInvitation invitation = workspaceInvitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> new BusinessException("INVITATION_NOT_FOUND", "Không tìm thấy lời mời", HttpStatus.NOT_FOUND));
        if (invitation.getInvitationStatus() == InvitationStatus.PENDING) {
            invitation.setInvitationStatus(InvitationStatus.CANCELLED);
            invitation.setRespondedAt(Instant.now());
        }
    }

    private WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw forbiddenAction();
        }
        return member;
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)) {
            throw notFoundWorkspace();
        }
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "Bạn không có quyền truy cập không gian tài chính này", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember findActiveMember(UUID workspaceId, UUID userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "Bạn không có quyền truy cập không gian tài chính này", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember activeMemberById(UUID workspaceId, UUID memberId) {
        WorkspaceMember member = workspaceMemberRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                .orElseThrow(() -> new BusinessException("MEMBER_NOT_FOUND", "Không tìm thấy thành viên", HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException("MEMBER_NOT_FOUND", "Không tìm thấy thành viên", HttpStatus.NOT_FOUND);
        }
        return member;
    }

    private WorkspaceInvitation requirePendingInvitationForInvitee(UUID invitationId, UUID userId) {
        WorkspaceInvitation invitation = workspaceInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException("INVITATION_NOT_FOUND", "Không tìm thấy lời mời", HttpStatus.NOT_FOUND));
        if (!invitation.getInvitedUser().getId().equals(userId)) {
            throw forbiddenAction();
        }
        if (invitation.getInvitationStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("INVITATION_NOT_PENDING", "Lời mời không còn hiệu lực");
        }
        return invitation;
    }

    private void ensureNotLastOwner(UUID workspaceId) {
        if (workspaceMemberRepository.countByWorkspaceIdAndRoleAndMemberStatus(workspaceId, WorkspaceRole.OWNER, "ACTIVE") <= 1) {
            throw new BusinessException("LAST_OWNER", "Không thể xóa hoặc hạ quyền chủ sở hữu cuối cùng");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
    }

    private WorkspaceRole parseRole(String rawRole) {
        try {
            return WorkspaceRole.valueOf((rawRole == null ? "" : rawRole.trim()).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_ROLE", "Vai trò không hợp lệ");
        }
    }

    private WorkspaceRole parseInviteRole(String rawRole) {
        WorkspaceRole role = rawRole == null || rawRole.isBlank() ? WorkspaceRole.EDITOR : parseRole(rawRole);
        if (role == WorkspaceRole.OWNER) {
            throw new BusinessException("INVALID_ROLE", "Không thể mời trực tiếp vai trò chủ sở hữu");
        }
        return role;
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank() || value.length() > 120) {
            throw new BusinessException("INVALID_WORKSPACE_NAME", "Tên sổ chung không hợp lệ");
        }
        return value;
    }

    private BusinessException notFoundWorkspace() {
        return new BusinessException("WORKSPACE_NOT_FOUND", "Không tìm thấy không gian tài chính", HttpStatus.NOT_FOUND);
    }

    private BusinessException forbiddenAction() {
        return new BusinessException("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.", HttpStatus.FORBIDDEN);
    }

    private WorkspaceResponse mapToResponse(Workspace ws, WorkspaceRole role) {
        return WorkspaceResponse.builder()
                .id(ws.getId())
                .name(ws.getName())
                .workspaceType(ws.getWorkspaceType().name())
                .role(role.name())
                .currency(ws.getCurrency())
                .timezone(ws.getTimezone())
                .quickAmountUnit(ws.getQuickAmountUnit())
                .build();
    }

    private WorkspaceMemberResponse mapMember(WorkspaceMember member) {
        User user = member.getUser();
        return WorkspaceMemberResponse.builder()
                .id(member.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(member.getRole().name())
                .status(member.getMemberStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private WorkspaceInvitationResponse mapInvitation(WorkspaceInvitation invitation) {
        User invited = invitation.getInvitedUser();
        User inviter = invitation.getInvitedByUser();
        Workspace workspace = invitation.getWorkspace();
        return WorkspaceInvitationResponse.builder()
                .id(invitation.getId())
                .workspaceId(workspace.getId())
                .workspaceName(workspace.getName())
                .invitedUserId(invited.getId())
                .invitedUsername(invited.getUsername())
                .invitedDisplayName(invited.getFullName())
                .invitedByUserId(inviter.getId())
                .invitedByUsername(inviter.getUsername())
                .role(invitation.getRole().name())
                .status(invitation.getInvitationStatus().name())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }

    private UserSearchResponse mapUserSearch(User user) {
        return UserSearchResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
