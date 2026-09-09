package org.finos.waltz.web.endpoints.api;

import org.finos.waltz.common.exception.InsufficientPrivelegeException;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.Operation;
import org.finos.waltz.service.external_identifier.ExternalIdentifierService;
import org.finos.waltz.service.permission.permission_checker.FlowPermissionChecker;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;

import java.util.Collections;
import java.util.Set;

import static org.finos.waltz.common.SetUtilities.asSet;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalIdentifierEndpointTest {

    private static final String USER = "bob";
    private static final String EXTERNAL_ID = "ext-1";

    @Mock
    private ExternalIdentifierService externalIdentifierService;
    @Mock
    private FlowPermissionChecker flowPermissionChecker;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private Request request;

    private ExternalIdentifierEndpoint endpoint;


    @BeforeEach
    void setUp() {
        endpoint = new ExternalIdentifierEndpoint(
                externalIdentifierService,
                flowPermissionChecker,
                userRoleService);
    }


    @Test
    void createOnNonFlowEntityIsRejectedWhenUserLacksRole() {
        setupRequest("APPLICATION", 1L);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.create(request));

        verifyNoInteractions(externalIdentifierService);
    }


    @Test
    void deleteOnNonFlowEntityIsRejectedWhenUserLacksRole() {
        setupRequest("APPLICATION", 1L);
        when(request.params("system")).thenReturn("SYS");
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.delete(request));

        verifyNoInteractions(externalIdentifierService);
    }


    @Test
    void createOnNonFlowEntityIsAllowedWhenUserHasRole() throws InsufficientPrivelegeException {
        setupRequest("APPLICATION", 1L);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(true);
        when(externalIdentifierService.create(mkRef(EntityKind.APPLICATION, 1L), EXTERNAL_ID, USER))
                .thenReturn(1);

        assertEquals(1, endpoint.create(request));
    }


    @Test
    void deleteOnNonFlowEntityIsAllowedWhenUserHasRole() throws InsufficientPrivelegeException {
        setupRequest("APPLICATION", 1L);
        when(request.params("system")).thenReturn("SYS");
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(true);
        when(externalIdentifierService.delete(mkRef(EntityKind.APPLICATION, 1L), EXTERNAL_ID, "SYS", USER))
                .thenReturn(1);

        assertEquals(1, endpoint.delete(request));
    }


    @Test
    void createOnFlowEntityUsesFlowPermissionChecker() throws InsufficientPrivelegeException {
        setupRequest("LOGICAL_DATA_FLOW", 7L);
        Set<Operation> perms = asSet(Operation.ADD);
        when(flowPermissionChecker.findPermissionsForFlow(7L, USER)).thenReturn(perms);

        endpoint.create(request);

        verify(flowPermissionChecker).verifyEditPerms(perms, EntityKind.EXTERNAL_IDENTIFIER, USER);
        verify(userRoleService, never()).hasRole(anyString(), any(Set.class));
    }


    @Test
    void createOnFlowEntityIsRejectedWhenFlowPermsAreInsufficient() throws InsufficientPrivelegeException {
        setupRequest("LOGICAL_DATA_FLOW", 7L);
        Set<Operation> perms = Collections.emptySet();
        when(flowPermissionChecker.findPermissionsForFlow(anyLong(), anyString())).thenReturn(perms);
        Mockito
                .doThrow(new InsufficientPrivelegeException("nope"))
                .when(flowPermissionChecker)
                .verifyEditPerms(any(), any(), anyString());

        assertThrows(
                InsufficientPrivelegeException.class,
                () -> endpoint.create(request));

        verifyNoInteractions(externalIdentifierService);
    }


    private void setupRequest(String kind, long id) {
        when(request.params("kind")).thenReturn(kind);
        when(request.params("id")).thenReturn(Long.toString(id));
        when(request.splat()).thenReturn(new String[]{EXTERNAL_ID});
        when(request.attribute("waltz-user")).thenReturn(USER);
    }
}
