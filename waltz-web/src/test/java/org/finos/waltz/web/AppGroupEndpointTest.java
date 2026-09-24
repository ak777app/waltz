package org.finos.waltz.web;

import org.finos.waltz.common.exception.InsufficientPrivelegeException;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.app_group.AppGroup;
import org.finos.waltz.model.app_group.AppGroupDetail;
import org.finos.waltz.model.app_group.AppGroupEntry;
import org.finos.waltz.model.app_group.AppGroupKind;
import org.finos.waltz.model.app_group.AppGroupMember;
import org.finos.waltz.model.app_group.ImmutableAppGroup;
import org.finos.waltz.model.entity_search.EntitySearchOptions;
import org.finos.waltz.service.app_group.AppGroupService;
import org.finos.waltz.service.app_group.AppGroupSubscription;
import org.finos.waltz.web.endpoints.api.AppGroupEndpoint;
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
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.finos.waltz.web.EndpointTestUtilities.captureRoutes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppGroupEndpointTest {

    private static final String USER = "testUser";
    private static final long GROUP_ID = 10L;

    @Mock
    private AppGroupService appGroupService;
    @Mock
    private Request request;
    @Mock
    private Response response;

    private Map<String, Route> routes;

    @BeforeEach
    public void setUp() {
        routes = captureRoutes(new AppGroupEndpoint(appGroupService));
    }

    @Test
    void registersExpectedRoutes() {
        assertEquals(
                Set.of("GET api/app-group/my-group-subscriptions",
                        "GET api/app-group/id/:id/detail",
                        "POST api/app-group/id",
                        "GET api/app-group/public",
                        "GET api/app-group/private",
                        "GET api/app-group/related/:kind/:id",
                        "POST api/app-group/id/:id/subscribe",
                        "POST api/app-group/id/:id/unsubscribe",
                        "POST api/app-group/id/:id/members/owners",
                        "DELETE api/app-group/id/:id/members/owners/:ownerId",
                        "POST api/app-group/id/:id/applications",
                        "POST api/app-group/id/:id/applications/list",
                        "DELETE api/app-group/id/:id/applications/:applicationId",
                        "POST api/app-group/id/:id/applications/list/remove",
                        "POST api/app-group/id/:id/orgUnits",
                        "DELETE api/app-group/id/:id/orgUnits/:orgUnitId",
                        "POST api/app-group/id/:id/change-initiatives",
                        "DELETE api/app-group/id/:id/change-initiatives/:changeInitiativeId",
                        "POST api/app-group/id/:id/change-initiatives/list",
                        "POST api/app-group/id/:id/change-initiatives/list/remove",
                        "DELETE api/app-group/id/:id",
                        "POST api/app-group/id/:id",
                        "POST api/app-group",
                        "POST api/app-group/search"),
                routes.keySet());
    }

    @Test
    void mySubscriptionsUsesUsername() throws Exception {
        Set<AppGroupSubscription> expected = Collections.singleton(mock(AppGroupSubscription.class));
        givenUser();
        when(appGroupService.findGroupSubscriptionsForUser(USER)).thenReturn(expected);

        Object result = handle("GET api/app-group/my-group-subscriptions");

        assertSame(expected, result);
        verify(response).type(WebUtilities.TYPE_JSON);
    }

    @Test
    void mySubscriptionsPassesNullUsernameWhenAnonymous() throws Exception {
        when(request.attribute("waltz-user")).thenReturn(null);
        when(appGroupService.findGroupSubscriptionsForUser(null)).thenReturn(Collections.emptySet());

        assertEquals(Collections.emptySet(), handle("GET api/app-group/my-group-subscriptions"));
    }

    @Test
    void getDetailByIdParsesId() throws Exception {
        AppGroupDetail detail = mock(AppGroupDetail.class);
        givenGroupId();
        when(appGroupService.getGroupDetailById(GROUP_ID)).thenReturn(detail);

        assertSame(detail, handle("GET api/app-group/id/:id/detail"));
    }

    @Test
    void getDetailByIdRejectsNonNumericId() {
        when(request.params("id")).thenReturn("abc");

        assertThrows(NumberFormatException.class, () -> handle("GET api/app-group/id/:id/detail"));
        verifyNoInteractions(appGroupService);
    }

    @Test
    void findByIdsReadsIdsFromBody() throws Exception {
        List<AppGroup> expected = Collections.singletonList(mkGroup(1L));
        givenUser();
        givenBody("[1, 2, \"3\"]");
        when(appGroupService.findByIds(USER, asList(1L, 2L, 3L))).thenReturn(expected);

        Object result = handle("POST api/app-group/id");

        assertEquals(expected, result);
    }

    @Test
    void findPublicGroupsDelegates() throws Exception {
        List<AppGroup> expected = Collections.singletonList(mkGroup(1L));
        when(appGroupService.findPublicGroups()).thenReturn(expected);

        assertEquals(expected, handle("GET api/app-group/public"));
    }

    @Test
    void findPrivateGroupsUsesUsername() throws Exception {
        List<AppGroup> expected = Collections.singletonList(mkGroup(2L));
        givenUser();
        when(appGroupService.findPrivateGroupsByOwner(USER)).thenReturn(expected);

        assertEquals(expected, handle("GET api/app-group/private"));
    }

    @Test
    void findRelatedGroupsBuildsEntityReference() throws Exception {
        List<AppGroup> expected = Collections.singletonList(mkGroup(3L));
        givenUser();
        when(request.params("kind")).thenReturn("APPLICATION");
        when(request.params("id")).thenReturn("55");
        when(appGroupService.findRelatedByEntityReference(mkRef(EntityKind.APPLICATION, 55L), USER)).thenReturn(expected);

        assertEquals(expected, handle("GET api/app-group/related/:kind/:id"));
    }

    @Test
    void subscribeThenReturnsSubscriptions() throws Exception {
        Set<AppGroupSubscription> expected = Collections.singleton(mock(AppGroupSubscription.class));
        givenUser();
        givenGroupId();
        when(appGroupService.findGroupSubscriptionsForUser(USER)).thenReturn(expected);

        Object result = handle("POST api/app-group/id/:id/subscribe");

        assertSame(expected, result);
        verify(appGroupService).subscribe(USER, GROUP_ID);
    }

    @Test
    void unsubscribeThenReturnsSubscriptions() throws Exception {
        Set<AppGroupSubscription> expected = Collections.singleton(mock(AppGroupSubscription.class));
        givenUser();
        givenGroupId();
        when(appGroupService.findGroupSubscriptionsForUser(USER)).thenReturn(expected);

        Object result = handle("POST api/app-group/id/:id/unsubscribe");

        assertSame(expected, result);
        verify(appGroupService).unsubscribe(USER, GROUP_ID);
    }

    @Test
    void addOwnerReadsOwnerFromBodyAndReturnsMembers() throws Exception {
        Set<AppGroupMember> members = Collections.singleton(mock(AppGroupMember.class));
        givenUser();
        givenGroupId();
        when(request.body()).thenReturn("new.owner");
        when(appGroupService.getMembers(GROUP_ID)).thenReturn(members);

        Object result = handle("POST api/app-group/id/:id/members/owners");

        assertSame(members, result);
        verify(appGroupService).addOwner(USER, GROUP_ID, "new.owner");
    }

    @Test
    void addOwnerPropagatesPrivilegeFailure() throws Exception {
        givenUser();
        givenGroupId();
        when(request.body()).thenReturn("new.owner");
        when(appGroupService.addOwner(USER, GROUP_ID, "new.owner"))
                .thenThrow(new InsufficientPrivelegeException("not an owner"));

        assertThrows(InsufficientPrivelegeException.class, () -> handle("POST api/app-group/id/:id/members/owners"));
        verify(appGroupService, never()).getMembers(anyLong());
    }

    @Test
    void removeOwnerUsesOwnerIdParam() throws Exception {
        Set<AppGroupMember> members = Collections.emptySet();
        givenUser();
        givenGroupId();
        when(request.params("ownerId")).thenReturn("old.owner");
        when(appGroupService.getMembers(GROUP_ID)).thenReturn(members);

        Object result = handle("DELETE api/app-group/id/:id/members/owners/:ownerId");

        assertSame(members, result);
        verify(appGroupService).removeOwner(USER, GROUP_ID, "old.owner");
    }

    @Test
    void deleteGroupThenReturnsSubscriptions() throws Exception {
        Set<AppGroupSubscription> expected = Collections.emptySet();
        givenUser();
        givenGroupId();
        when(appGroupService.findGroupSubscriptionsForUser(USER)).thenReturn(expected);

        Object result = handle("DELETE api/app-group/id/:id");

        assertSame(expected, result);
        verify(appGroupService).deleteGroup(USER, GROUP_ID);
    }

    @Test
    void addApplicationReadsIdFromBody() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("123");
        when(appGroupService.addApplication(USER, GROUP_ID, 123L)).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/applications"));
    }

    @Test
    void addApplicationListReadsBulkRequest() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("{\"applicationIds\":[1,2],\"changeInitiativeIds\":[],\"unknownIdentifiers\":[\"X\"]}");
        when(appGroupService.addApplications(USER, GROUP_ID, asList(1L, 2L), Collections.singletonList("X")))
                .thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/applications/list"));
    }

    @Test
    void removeApplicationParsesApplicationIdParam() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        when(request.params("applicationId")).thenReturn("77");
        when(appGroupService.removeApplication(USER, GROUP_ID, 77L)).thenReturn(expected);

        assertEquals(expected, handle("DELETE api/app-group/id/:id/applications/:applicationId"));
    }

    @Test
    void removeApplicationListReadsIdsFromBody() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("[4,5]");
        when(appGroupService.removeApplications(USER, GROUP_ID, asList(4L, 5L))).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/applications/list/remove"));
    }

    @Test
    void addOrgUnitReadsIdFromBody() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("9");
        when(appGroupService.addOrganisationalUnit(USER, GROUP_ID, 9L)).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/orgUnits"));
    }

    @Test
    void removeOrgUnitParsesOrgUnitIdParam() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        when(request.params("orgUnitId")).thenReturn("9");
        when(appGroupService.removeOrganisationalUnit(USER, GROUP_ID, 9L)).thenReturn(expected);

        assertEquals(expected, handle("DELETE api/app-group/id/:id/orgUnits/:orgUnitId"));
    }

    @Test
    void addChangeInitiativeReadsIdFromBody() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("31");
        when(appGroupService.addChangeInitiative(USER, GROUP_ID, 31L)).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/change-initiatives"));
    }

    @Test
    void removeChangeInitiativeParsesParam() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        when(request.params("changeInitiativeId")).thenReturn("31");
        when(appGroupService.removeChangeInitiative(USER, GROUP_ID, 31L)).thenReturn(expected);

        assertEquals(expected, handle("DELETE api/app-group/id/:id/change-initiatives/:changeInitiativeId"));
    }

    @Test
    void addChangeInitiativeListReadsBulkRequest() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("{\"applicationIds\":[],\"changeInitiativeIds\":[6,7],\"unknownIdentifiers\":[]}");
        when(appGroupService.addChangeInitiatives(USER, GROUP_ID, asList(6L, 7L))).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/change-initiatives/list"));
    }

    @Test
    void removeChangeInitiativeListReadsIdsFromBody() throws Exception {
        List<AppGroupEntry> expected = mkEntries();
        givenUser();
        givenGroupId();
        givenBody("[6]");
        when(appGroupService.removeChangeInitiatives(USER, GROUP_ID, Collections.singletonList(6L))).thenReturn(expected);

        assertEquals(expected, handle("POST api/app-group/id/:id/change-initiatives/list/remove"));
    }

    @Test
    void updateOverviewDeserialisesGroup() throws Exception {
        AppGroupDetail detail = mock(AppGroupDetail.class);
        givenUser();
        givenBody("{\"id\":10,\"name\":\"Grp\",\"description\":\"d\",\"appGroupKind\":\"PUBLIC\"}");
        when(appGroupService.updateOverview(eq(USER), any(AppGroup.class))).thenReturn(detail);

        Object result = handle("POST api/app-group/id/:id");

        assertSame(detail, result);
        ArgumentCaptor<AppGroup> captor = ArgumentCaptor.forClass(AppGroup.class);
        verify(appGroupService).updateOverview(eq(USER), captor.capture());
        assertEquals("Grp", captor.getValue().name());
        assertEquals(AppGroupKind.PUBLIC, captor.getValue().appGroupKind());
        assertEquals(10L, captor.getValue().id().get());
    }

    @Test
    void createNewGroupUsesUsername() throws Exception {
        givenUser();
        when(appGroupService.createNewGroup(USER)).thenReturn(101L);

        assertEquals(101L, handle("POST api/app-group"));
    }

    @Test
    void searchBuildsOptionsFromBody() throws Exception {
        List<AppGroup> expected = Collections.singletonList(mkGroup(1L));
        givenBody("\"trading\"");
        when(appGroupService.search(any(EntitySearchOptions.class))).thenReturn(expected);

        Object result = handle("POST api/app-group/search");

        assertEquals(expected, result);
        ArgumentCaptor<EntitySearchOptions> captor = ArgumentCaptor.forClass(EntitySearchOptions.class);
        verify(appGroupService).search(captor.capture());
        assertEquals("trading", captor.getValue().searchQuery());
        assertEquals(Collections.singletonList(EntityKind.APP_GROUP), captor.getValue().entityKinds());
    }


    private Object handle(String routeKey) throws Exception {
        return routes.get(routeKey).handle(request, response);
    }


    private void givenUser() {
        when(request.attribute("waltz-user")).thenReturn(USER);
    }


    private void givenGroupId() {
        when(request.params("id")).thenReturn(String.valueOf(GROUP_ID));
    }


    private void givenBody(String body) {
        when(request.bodyAsBytes()).thenReturn(body.getBytes());
    }


    private static AppGroup mkGroup(long id) {
        return ImmutableAppGroup.builder()
                .id(id)
                .name("group" + id)
                .description("desc")
                .appGroupKind(AppGroupKind.PUBLIC)
                .build();
    }


    private static List<AppGroupEntry> mkEntries() {
        return Collections.singletonList(mock(AppGroupEntry.class));
    }
}
