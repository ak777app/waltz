package org.finos.waltz.web.endpoints.api;

import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.Operation;
import org.finos.waltz.model.entity_relationship.EntityRelationshipChangeCommand;
import org.finos.waltz.model.entity_relationship.ImmutableEntityRelationshipChangeCommand;
import org.finos.waltz.model.entity_relationship.RelationshipKind;
import org.finos.waltz.service.change_initiative.ChangeInitiativeService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;

import java.util.Set;

import static org.finos.waltz.model.EntityReference.mkRef;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeInitiativeEndpointTest {

    private static final String USER = "bob";
    private static final long CI_ID = 42L;

    @Mock
    private ChangeInitiativeService service;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private Request request;

    private ChangeInitiativeEndpoint endpoint;


    @BeforeEach
    void setUp() {
        endpoint = new ChangeInitiativeEndpoint(service, userRoleService);
    }


    @Test
    void addIsRejectedWhenUserLacksRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.changeEntityRelationship(request, mkCommand(Operation.ADD)));

        verifyNoInteractions(service);
    }


    @Test
    void removeIsRejectedWhenUserLacksRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.changeEntityRelationship(request, mkCommand(Operation.REMOVE)));

        verifyNoInteractions(service);
    }


    @Test
    void addIsAllowedWhenUserHasRole() {
        setupAuthorisedRequest();
        EntityRelationshipChangeCommand cmd = mkCommand(Operation.ADD);
        when(service.addEntityRelationship(CI_ID, cmd, USER)).thenReturn(true);

        assertTrue(endpoint.changeEntityRelationship(request, cmd));
    }


    @Test
    void removeIsAllowedWhenUserHasRole() {
        setupAuthorisedRequest();
        EntityRelationshipChangeCommand cmd = mkCommand(Operation.REMOVE);
        when(service.removeEntityRelationship(CI_ID, cmd, USER)).thenReturn(true);

        assertTrue(endpoint.changeEntityRelationship(request, cmd));
    }


    private void setupAuthorisedRequest() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.params("id")).thenReturn(Long.toString(CI_ID));
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(true);
    }


    private EntityRelationshipChangeCommand mkCommand(Operation operation) {
        return ImmutableEntityRelationshipChangeCommand
                .builder()
                .operation(operation)
                .entityReference(mkRef(EntityKind.APPLICATION, 1L))
                .relationship(RelationshipKind.RELATES_TO)
                .build();
    }
}
