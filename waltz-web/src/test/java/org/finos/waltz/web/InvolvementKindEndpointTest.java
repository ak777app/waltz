package org.finos.waltz.web;

import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.command.CommandResponse;
import org.finos.waltz.model.involvement_kind.InvolvementKind;
import org.finos.waltz.model.involvement_kind.InvolvementKindChangeCommand;
import org.finos.waltz.model.involvement_kind.InvolvementKindCreateCommand;
import org.finos.waltz.model.involvement_kind.InvolvementKindUsageStat;
import org.finos.waltz.service.involvement_kind.InvolvementKindService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.endpoints.api.InvolvementKindEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.finos.waltz.web.EndpointTestUtilities.captureRoutes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvolvementKindEndpointTest {

    private static final String USER = "testUser";

    @Mock
    private InvolvementKindService involvementKindService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private Request request;
    @Mock
    private Response response;

    private Map<String, Route> routes;

    @BeforeEach
    public void setUp() {
        routes = captureRoutes(new InvolvementKindEndpoint(involvementKindService, userRoleService));
    }

    @Test
    void constructorRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> new InvolvementKindEndpoint(null, userRoleService));
        assertThrows(IllegalArgumentException.class, () -> new InvolvementKindEndpoint(involvementKindService, null));
    }

    @Test
    void registersExpectedRoutes() {
        assertEquals(
                Set.of("GET api/involvement-kind",
                        "GET api/involvement-kind/key-involvement-kinds/:kind",
                        "GET api/involvement-kind/usage-stats",
                        "GET api/involvement-kind/usage-stats/kind/:id",
                        "GET api/involvement-kind/id/:id",
                        "GET api/involvement-kind/external-id/:externalId",
                        "POST api/involvement-kind/update",
                        "PUT api/involvement-kind/update",
                        "DELETE api/involvement-kind/:id"),
                routes.keySet());
    }

    @Test
    void findAllReturnsServiceResult() throws Exception {
        InvolvementKind kind = mock(InvolvementKind.class);
        when(involvementKindService.findAll()).thenReturn(Collections.singletonList(kind));

        Object result = routes.get("GET api/involvement-kind").handle(request, response);

        assertEquals(Collections.singletonList(kind), result);
        verify(response).type(WebUtilities.TYPE_JSON);
    }

    @Test
    void keyInvolvementKindsParsesEntityKind() throws Exception {
        InvolvementKind kind = mock(InvolvementKind.class);
        when(request.params("kind")).thenReturn("APPLICATION");
        when(involvementKindService.findKeyInvolvementKindsByEntityKind(EntityKind.APPLICATION))
                .thenReturn(Collections.singletonList(kind));

        Object result = routes.get("GET api/involvement-kind/key-involvement-kinds/:kind").handle(request, response);

        assertEquals(Collections.singletonList(kind), result);
    }

    @Test
    void keyInvolvementKindsRejectsUnknownEntityKind() {
        when(request.params("kind")).thenReturn("NOT_A_KIND");

        assertThrows(IllegalArgumentException.class,
                () -> routes.get("GET api/involvement-kind/key-involvement-kinds/:kind").handle(request, response));
        verifyNoInteractions(involvementKindService);
    }

    @Test
    void usageStatsReturnsServiceResult() throws Exception {
        InvolvementKindUsageStat stat = mock(InvolvementKindUsageStat.class);
        when(involvementKindService.loadUsageStats()).thenReturn(Collections.singleton(stat));

        Object result = routes.get("GET api/involvement-kind/usage-stats").handle(request, response);

        assertEquals(Collections.singleton(stat), result);
    }

    @Test
    void usageStatsForKindParsesId() throws Exception {
        InvolvementKindUsageStat stat = mock(InvolvementKindUsageStat.class);
        when(request.params("id")).thenReturn("3");
        when(involvementKindService.loadUsageStatsForKind(3L)).thenReturn(stat);

        Object result = routes.get("GET api/involvement-kind/usage-stats/kind/:id").handle(request, response);

        assertSame(stat, result);
    }

    @Test
    void getByIdParsesId() throws Exception {
        InvolvementKind kind = mock(InvolvementKind.class);
        when(request.params("id")).thenReturn("12");
        when(involvementKindService.getById(12L)).thenReturn(kind);

        Object result = routes.get("GET api/involvement-kind/id/:id").handle(request, response);

        assertSame(kind, result);
    }

    @Test
    void getByIdRejectsNonNumericId() {
        when(request.params("id")).thenReturn("twelve");

        assertThrows(NumberFormatException.class,
                () -> routes.get("GET api/involvement-kind/id/:id").handle(request, response));
        verifyNoInteractions(involvementKindService);
    }

    @Test
    void getByExternalIdPassesParam() throws Exception {
        InvolvementKind kind = mock(InvolvementKind.class);
        when(request.params("externalId")).thenReturn("EXT-1");
        when(involvementKindService.getByExternalId("EXT-1")).thenReturn(kind);

        Object result = routes.get("GET api/involvement-kind/external-id/:externalId").handle(request, response);

        assertSame(kind, result);
    }

    @Test
    void createReadsBodyAndRequiresAdminRole() throws Exception {
        String body = "{\"name\":\"Owner\",\"description\":\"desc\",\"subjectKind\":\"APPLICATION\"}";
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.bodyAsBytes()).thenReturn(body.getBytes());
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(involvementKindService.create(any(InvolvementKindCreateCommand.class), eq(USER))).thenReturn(77L);

        Object result = routes.get("POST api/involvement-kind/update").handle(request, response);

        assertEquals(77L, result);
        ArgumentCaptor<InvolvementKindCreateCommand> captor = ArgumentCaptor.forClass(InvolvementKindCreateCommand.class);
        verify(involvementKindService).create(captor.capture(), eq(USER));
        assertEquals("Owner", captor.getValue().name());
        assertEquals(EntityKind.APPLICATION, captor.getValue().subjectKind());
    }

    @Test
    void createRejectsUserWithoutAdminRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(false);

        assertThrows(NotAuthorizedException.class,
                () -> routes.get("POST api/involvement-kind/update").handle(request, response));
        verifyNoInteractions(involvementKindService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReadsBodyAndDelegates() throws Exception {
        String body = "{\"id\":4,\"description\":{\"oldVal\":\"x\",\"newVal\":\"y\"}}";
        CommandResponse<InvolvementKindChangeCommand> expected = mock(CommandResponse.class);
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.bodyAsBytes()).thenReturn(body.getBytes());
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(involvementKindService.update(any(InvolvementKindChangeCommand.class), eq(USER))).thenReturn(expected);

        Object result = routes.get("PUT api/involvement-kind/update").handle(request, response);

        assertSame(expected, result);
        ArgumentCaptor<InvolvementKindChangeCommand> captor = ArgumentCaptor.forClass(InvolvementKindChangeCommand.class);
        verify(involvementKindService).update(captor.capture(), eq(USER));
        assertEquals(4L, captor.getValue().id());
        assertEquals("y", captor.getValue().description().get().newVal());
        assertFalse(captor.getValue().name().isPresent());
    }

    @Test
    void updateRejectsAnonymousUser() {
        when(request.attribute("waltz-user")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> routes.get("PUT api/involvement-kind/update").handle(request, response));

        assertEquals("Not logged in", ex.getMessage());
        verifyNoInteractions(involvementKindService, userRoleService);
    }

    @Test
    void deleteDelegatesWhenAuthorised() throws Exception {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.params("id")).thenReturn("8");
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(involvementKindService.delete(8L)).thenReturn(true);

        Object result = routes.get("DELETE api/involvement-kind/:id").handle(request, response);

        assertEquals(true, result);
    }

    @Test
    void deleteRejectsUserWithoutAdminRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(false);

        assertThrows(NotAuthorizedException.class,
                () -> routes.get("DELETE api/involvement-kind/:id").handle(request, response));
        verify(involvementKindService, never()).delete(anyLong());
    }
}
