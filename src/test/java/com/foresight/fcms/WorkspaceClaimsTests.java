package com.foresight.fcms;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.WorkspaceClaims;
import com.foresight.fcms.auth.service.UserPermissionService.Permissions;

import org.junit.jupiter.api.Test;

import java.util.Set;

class WorkspaceClaimsTests {
    @Test
    void staffCapabilitiesCannotWidenTheirWorkspaceOrRecordScope() {
        assertEquals(Set.of("workspace.staff", "task.read", "task.write", "client.lookup"),
                WorkspaceClaims.effective(Set.of("workspace.staff", "task.read", "task.write",
                        "task.read.all", "task.manage", "client.read", "client.lookup", "expense.approve")));
        assertEquals(Set.of("workspace.staff"), WorkspaceClaims.effective(Set.of()));
    }

    @Test
    void administrativeWorkspaceRequiresItsSeparateCapabilityClaims() {
        assertEquals(Set.of("workspace.admin", "task.read"),
                WorkspaceClaims.effective(Set.of("workspace.admin", "workspace.staff", "task.read")));
        assertFalse(WorkspaceClaims.effective(Set.of("workspace.admin")).contains("accounting.read"));
    }

    @Test
    void exclusionsWinOverRoleGrantsAndWorkspaceFallbackWithoutWideningPersonalScope() {
        var staff = new Permissions(Set.of("workspace.staff", "leave.write", "client.read"),
                Set.of("workspace.staff", "leave.write"));
        assertEquals(Set.of("client.read"), staff.selected());
        assertTrue(staff.effective().isEmpty());
        var business = new Permissions(Set.of("workspace.accounting", "accounting.write", "account.write"),
                Set.of("accounting.write"));
        assertEquals(Set.of("workspace.accounting", "account.write"), business.effective());
        var withoutWorkspace = new Permissions(business.assigned(), Set.of("workspace.accounting"));
        assertEquals(Set.of("workspace.staff", "account.write"), withoutWorkspace.effective());
    }
}
