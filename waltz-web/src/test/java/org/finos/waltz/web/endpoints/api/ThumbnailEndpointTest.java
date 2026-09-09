package org.finos.waltz.web.endpoints.api;

import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.thumbnail.ImmutableThumbnailSaveCommand;
import org.finos.waltz.model.thumbnail.ThumbnailSaveCommand;
import org.finos.waltz.service.thumbnail.ThumbnailService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailEndpointTest {

    private static final String USER = "bob";

    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private Request request;

    private ThumbnailEndpoint endpoint;


    @BeforeEach
    void setUp() {
        endpoint = new ThumbnailEndpoint(thumbnailService, userRoleService);
    }


    @Test
    void deleteIsRejectedWhenUserLacksRole() {
        setupRequest("APPLICATION", 1L);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.delete(request));

        verifyNoInteractions(thumbnailService);
    }


    @Test
    void deleteIsAllowedWhenUserHasRole() {
        setupRequest("APPLICATION", 1L);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(true);
        when(thumbnailService.deleteByReference(mkRef(EntityKind.APPLICATION, 1L), USER)).thenReturn(true);

        assertTrue(endpoint.delete(request));
    }


    @Test
    void saveIsRejectedWhenUserLacksRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(false);

        assertThrows(
                NotAuthorizedException.class,
                () -> endpoint.save(request, mkCommand()));

        verifyNoInteractions(thumbnailService);
    }


    @Test
    void saveIsAllowedWhenUserHasRole() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(userRoleService.hasRole(eq(USER), any(Set.class))).thenReturn(true);

        ThumbnailSaveCommand cmd = mkCommand();
        assertTrue(endpoint.save(request, cmd));

        verify(thumbnailService).save(cmd, USER);
    }


    private ThumbnailSaveCommand mkCommand() {
        return ImmutableThumbnailSaveCommand
                .builder()
                .parentEntityReference(mkRef(EntityKind.APPLICATION, 1L))
                .mimeType("image/png")
                .blob(new byte[]{1, 2, 3})
                .lastUpdatedBy(USER)
                .build();
    }


    private void setupRequest(String kind, long id) {
        when(request.params("kind")).thenReturn(kind);
        when(request.params("id")).thenReturn(Long.toString(id));
        when(request.attribute("waltz-user")).thenReturn(USER);
    }
}
