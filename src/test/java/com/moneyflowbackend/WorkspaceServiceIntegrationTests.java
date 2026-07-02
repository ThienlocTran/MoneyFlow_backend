package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceMemberResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRoleRequest;
import com.moneyflowbackend.workspace.model.InvitationStatus;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceInvitation;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceInvitationRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkspaceServiceIntegrationTests {

    @Autowired WorkspaceService workspaceService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WorkspaceInvitationRepository workspaceInvitationRepository;

    @Test
    void workspaceCreate_createsSharedWorkspaceAndOwnerMembership() {
        User owner = user("m03_owner");
        WorkspaceRequest req = workspaceReq("Sổ chung");

        WorkspaceResponse response = workspaceService.createWorkspace(req, owner.getId());

        assertThat(response.getWorkspaceType()).isEqualTo("SHARED");
        assertThat(response.getRole()).isEqualTo("OWNER");
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndMemberStatus(response.getId(), owner.getId(), "ACTIVE")).isTrue();
    }

    @Test
    void workspaceList_returnsOnlyMembershipWorkspaces() {
        User owner = user("m03_list_owner");
        User other = user("m03_list_other");
        Workspace owned = workspace(owner, "Owned");
        workspace(other, "Other");

        assertThat(workspaceService.getUserWorkspaces(owner.getId()))
                .extracting(WorkspaceResponse::getId)
                .containsExactly(owned.getId());
    }

    @Test
    void workspaceSecurity_blocksCrossWorkspaceRead() {
        User owner = user("m03_sec_owner");
        User outsider = user("m03_sec_outsider");
        Workspace workspace = workspace(owner, "Private");

        assertBusinessCode(() -> workspaceService.getWorkspaceDetails(workspace.getId(), outsider.getId()), "FORBIDDEN");
    }

    @Test
    void userSearch_byUsernameReturnsSafeFieldsOnly() {
        User user = user("m03_search");

        assertThat(workspaceService.searchUsers("m03_sea"))
                .anySatisfy(result -> {
                    assertThat(result.getId()).isEqualTo(user.getId());
                    assertThat(result.getUsername()).isEqualTo(user.getUsername());
                    assertThat(result.getDisplayName()).isEqualTo(user.getFullName());
                    assertThat(result).hasNoNullFieldsOrPropertiesExcept("avatarUrl");
                });
    }

    @Test
    void invitation_ownerCanInviteByUsername() {
        TestContext ctx = context("m03_invite_owner");
        User invitee = user("m03_invitee");

        var invitation = workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "EDITOR"), ctx.user().getId());

        assertThat(invitation.getInvitedUserId()).isEqualTo(invitee.getId());
        assertThat(invitation.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void invitation_rejectsSelfInvite() {
        TestContext ctx = context("m03_self");

        assertBusinessCode(() -> workspaceService.invite(ctx.workspace().getId(), inviteReq(ctx.user().getUsername(), "EDITOR"), ctx.user().getId()), "INVITE_SELF");
    }

    @Test
    void invitation_rejectsAlreadyMember() {
        TestContext ctx = context("m03_member");
        User member = user("m03_existing_member");
        addMember(ctx.workspace(), member, WorkspaceRole.VIEWER);

        assertBusinessCode(() -> workspaceService.invite(ctx.workspace().getId(), inviteReq(member.getUsername(), "EDITOR"), ctx.user().getId()), "ALREADY_MEMBER");
    }

    @Test
    void invitation_rejectsDuplicatePending() {
        TestContext ctx = context("m03_dup");
        User invitee = user("m03_dup_invitee");

        workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "VIEWER"), ctx.user().getId());

        assertBusinessCode(() -> workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "VIEWER"), ctx.user().getId()), "DUPLICATE_PENDING_INVITATION");
    }

    @Test
    void invitation_inviteeCanAccept() {
        TestContext ctx = context("m03_accept");
        User invitee = user("m03_accept_invitee");
        var invitation = workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "EDITOR"), ctx.user().getId());

        WorkspaceMemberResponse member = workspaceService.acceptInvitation(invitation.getId(), invitee.getId());

        assertThat(member.getUserId()).isEqualTo(invitee.getId());
        assertThat(member.getRole()).isEqualTo("EDITOR");
    }

    @Test
    void invitation_acceptCreatesMembershipAtomically() {
        TestContext ctx = context("m03_atomic");
        User invitee = user("m03_atomic_invitee");
        var invitation = workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "VIEWER"), ctx.user().getId());

        workspaceService.acceptInvitation(invitation.getId(), invitee.getId());

        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndMemberStatus(ctx.workspace().getId(), invitee.getId(), "ACTIVE")).isTrue();
        assertThat(workspaceInvitationRepository.findById(invitation.getId()).orElseThrow().getInvitationStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void invitation_inviteeCanReject() {
        TestContext ctx = context("m03_reject");
        User invitee = user("m03_reject_invitee");
        var invitation = workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "VIEWER"), ctx.user().getId());

        workspaceService.rejectInvitation(invitation.getId(), invitee.getId());

        assertThat(workspaceInvitationRepository.findById(invitation.getId()).orElseThrow().getInvitationStatus()).isEqualTo(InvitationStatus.REJECTED);
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndMemberStatus(ctx.workspace().getId(), invitee.getId(), "ACTIVE")).isFalse();
    }

    @Test
    void invitation_ownerCanCancelPending() {
        TestContext ctx = context("m03_cancel");
        User invitee = user("m03_cancel_invitee");
        var invitation = workspaceService.invite(ctx.workspace().getId(), inviteReq(invitee.getUsername(), "VIEWER"), ctx.user().getId());

        workspaceService.cancelInvitation(ctx.workspace().getId(), invitation.getId(), ctx.user().getId());

        assertThat(workspaceInvitationRepository.findById(invitation.getId()).orElseThrow().getInvitationStatus()).isEqualTo(InvitationStatus.CANCELLED);
    }

    @Test
    void member_ownerCanChangeRole() {
        TestContext ctx = context("m03_role");
        WorkspaceMember member = addMember(ctx.workspace(), user("m03_role_member"), WorkspaceRole.VIEWER);

        WorkspaceMemberResponse response = workspaceService.updateMemberRole(ctx.workspace().getId(), member.getId(), roleReq("EDITOR"), ctx.user().getId());

        assertThat(response.getRole()).isEqualTo("EDITOR");
    }

    @Test
    void member_viewerCannotChangeRole() {
        TestContext ctx = context("m03_viewer");
        WorkspaceMember viewer = addMember(ctx.workspace(), user("m03_viewer_user"), WorkspaceRole.VIEWER);

        assertBusinessCode(() -> workspaceService.updateMemberRole(ctx.workspace().getId(), viewer.getId(), roleReq("EDITOR"), viewer.getUser().getId()), "FORBIDDEN");
    }

    @Test
    void member_cannotDemoteLastOwner() {
        TestContext ctx = context("m03_last_demote");
        WorkspaceMember ownerMember = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(ctx.workspace().getId(), ctx.user().getId(), "ACTIVE").orElseThrow();

        assertBusinessCode(() -> workspaceService.updateMemberRole(ctx.workspace().getId(), ownerMember.getId(), roleReq("EDITOR"), ctx.user().getId()), "LAST_OWNER");
    }

    @Test
    void member_cannotRemoveLastOwner() {
        TestContext ctx = context("m03_last_remove");
        WorkspaceMember ownerMember = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(ctx.workspace().getId(), ctx.user().getId(), "ACTIVE").orElseThrow();

        assertBusinessCode(() -> workspaceService.removeMember(ctx.workspace().getId(), ownerMember.getId(), ctx.user().getId()), "LAST_OWNER");
    }

    private TestContext context(String prefix) {
        User user = user(prefix);
        return new TestContext(user, workspace(user, prefix + " workspace"));
    }

    private User user(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName(prefix + " User")
                .build());
    }

    private Workspace workspace(User owner, String name) {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(name)
                .createdByUser(owner)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .build());
        return workspace;
    }

    private WorkspaceMember addMember(Workspace workspace, User user, WorkspaceRole role) {
        return workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
    }

    private WorkspaceRequest workspaceReq(String name) {
        WorkspaceRequest req = new WorkspaceRequest();
        req.setName(name);
        return req;
    }

    private WorkspaceInvitationRequest inviteReq(String username, String role) {
        WorkspaceInvitationRequest req = new WorkspaceInvitationRequest();
        req.setUsername(username);
        req.setRole(role);
        return req;
    }

    private WorkspaceRoleRequest roleReq(String role) {
        WorkspaceRoleRequest req = new WorkspaceRoleRequest();
        req.setRole(role);
        return req;
    }

    private void assertBusinessCode(Runnable action, String code) {
        try {
            action.run();
        } catch (BusinessException ex) {
            assertThat(ex.getCode()).isEqualTo(code);
            return;
        }
        throw new AssertionError("Expected BusinessException " + code);
    }

    private record TestContext(User user, Workspace workspace) {}
}
