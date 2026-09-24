package org.finos.waltz.web;

import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.actor.Actor;
import org.finos.waltz.model.actor.ActorChangeCommand;
import org.finos.waltz.model.actor.ActorCreateCommand;
import org.finos.waltz.model.actor.ImmutableActor;
import org.finos.waltz.model.command.CommandResponse;
import org.finos.waltz.service.actor.ActorService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.endpoints.api.ActorEndpoint;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.finos.waltz.model.EntityKind.APPLICATION;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.finos.waltz.web.EndpointTestUtilities.captureRoutes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActorEndpointTest {

    private static final String USER = "testUser";

    @Mock
    private ActorService actorService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private Request request;
    @Mock
    private Response response;

    private Map<String, Route> routes;

    @BeforeEach
    public void setUp() {
        routes = captureRoutes(new ActorEndpoint(actorService, userRoleService));
    }

    @Test
    void registersExpectedRoutes() {
        assertEquals(
                Set.of("GET api/actor/search/:query",
                        "GET api/actor",
                        "GET api/actor/id/:id",
                        "POST api/actor/update",
                        "PUT api/actor/update",
                        "DELETE api/actor/:id"),
                routes.keySet());
    }

    @Test
    void searchDelegatesQueryParam() throws Exception {
        List<EntityReference> expected = Collections.singletonList(mkRef(APPLICATION, 1L, "app"));
        when(request.params("query")).thenReturn("app");
        when(actorService.search("app")).thenReturn(expected);

        Object result = routes.get("GET api/actor/search/:query").handle(request, response);

        assertEquals(expected, result);
        verify(response).type(WebUtilities.TYPE_JSON);
    }

    @Test
    void findAllReturnsServiceResult() throws Exception {
        Actor actor = mkActor(7L);
        when(actorService.findAll()).thenReturn(Collections.singletonList(actor));

        Object result = routes.get("GET api/actor").handle(request, response);

        assertEquals(Collections.singletonList(actor), result);
    }

    @Test
    void getByIdParsesIdParam() throws Exception {
        Actor actor = mkActor(42L);
        when(request.params("id")).thenReturn("42");
        when(actorService.getById(42L)).thenReturn(actor);

        Object result = routes.get("GET api/actor/id/:id").handle(request, response);

        assertEquals(actor, result);
    }

    @Test
    void getByIdRejectsNonNumericId() {
        when(request.params("id")).thenReturn("abc");

        assertThrows(NumberFormatException.class,
                () -> routes.get("GET api/actor/id/:id").handle(request, response));
        verifyNoInteractions(actorService);
    }

    @Test
    void createReadsBodyAndRequiresAdminRole() throws Exception {
        String body = "{\"name\":\"Trading Desk\",\"description\":\"desc\",\"isExternal\":true}";
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.bodyAsBytes()).thenReturn(body.getBytes());
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(actorService.create(any(ActorCreateCommand.class), eq(USER))).thenReturn(99L);

        Object result = routes.get("POST api/actor/update").handle(request, response);

        assertEquals(99L, result);
        ArgumentCaptor<ActorCreateCommand> captor = ArgumentCaptor.forClass(ActorCreateCommand.class);
        verify(actorService).create(captor.capture(), eq(USER));
        assertEquals("Trading Desk", captor.getValue().name());
        assertTrue(captor.getValue().isExternal());
    }

    @Test
    void createRejectsUserWithoutAdminRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(false);

        assertThrows(NotAuthorizedException.class,
                () -> routes.get("POST api/actor/update").handle(request, response));
        verifyNoInteractions(actorService);
    }

    @Test
    void createRejectsAnonymousUser() {
        when(request.attribute("waltz-user")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> routes.get("POST api/actor/update").handle(request, response));

        assertEquals("Not logged in", ex.getMessage());
        verifyNoInteractions(actorService, userRoleService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReadsBodyAndDelegates() throws Exception {
        String body = "{\"id\":5,\"name\":{\"oldVal\":\"a\",\"newVal\":\"b\"}}";
        CommandResponse<ActorChangeCommand> expected = mock(CommandResponse.class);
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.bodyAsBytes()).thenReturn(body.getBytes());
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(actorService.update(any(ActorChangeCommand.class), eq(USER))).thenReturn(expected);

        Object result = routes.get("PUT api/actor/update").handle(request, response);

        assertSame(expected, result);
        ArgumentCaptor<ActorChangeCommand> captor = ArgumentCaptor.forClass(ActorChangeCommand.class);
        verify(actorService).update(captor.capture(), eq(USER));
        assertEquals(5L, captor.getValue().id());
        assertEquals("b", captor.getValue().name().get().newVal());
    }

    @Test
    void deleteDelegatesWhenAuthorised() throws Exception {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.params("id")).thenReturn("11");
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(true);
        when(actorService.delete(11L)).thenReturn(true);

        Object result = routes.get("DELETE api/actor/:id").handle(request, response);

        assertEquals(true, result);
    }

    @Test
    void deleteRejectsUserWithoutAdminRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), anySet())).thenReturn(false);

        assertThrows(NotAuthorizedException.class,
                () -> routes.get("DELETE api/actor/:id").handle(request, response));
        verify(actorService, never()).delete(anyLong());
    }


    private static Actor mkActor(long id) {
        return ImmutableActor.builder()
                .id(id)
                .name("actor" + id)
                .description("desc")
                .isExternal(false)
                .lastUpdatedBy(USER)
                .build();
    }
}
