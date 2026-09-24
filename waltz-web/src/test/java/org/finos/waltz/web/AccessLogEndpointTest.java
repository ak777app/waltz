package org.finos.waltz.web;

import org.finos.waltz.model.ImmutableWaltzVersionInfo;
import org.finos.waltz.model.WaltzVersionInfo;
import org.finos.waltz.model.accesslog.AccessLog;
import org.finos.waltz.model.accesslog.AccessLogSummary;
import org.finos.waltz.model.accesslog.AccessTime;
import org.finos.waltz.model.accesslog.ImmutableAccessLog;
import org.finos.waltz.model.accesslog.ImmutableAccessTime;
import org.finos.waltz.service.access_log.AccessLogService;
import org.finos.waltz.web.endpoints.api.AccessLogEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;
import spark.Response;
import spark.Route;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.finos.waltz.web.EndpointTestUtilities.captureRoutes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessLogEndpointTest {

    private static final String USER = "testUser";

    private final WaltzVersionInfo versionInfo = ImmutableWaltzVersionInfo.builder()
            .pomVersion("1.0.0")
            .timestamp("2024-01-01")
            .revision("abc123")
            .build();

    @Mock
    private AccessLogService accessLogService;
    @Mock
    private Request request;
    @Mock
    private Response response;

    private Map<String, Route> routes;

    @BeforeEach
    public void setUp() {
        routes = captureRoutes(new AccessLogEndpoint(accessLogService, versionInfo));
    }

    @Test
    void constructorRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> new AccessLogEndpoint(null, versionInfo));
        assertThrows(IllegalArgumentException.class, () -> new AccessLogEndpoint(accessLogService, null));
    }

    @Test
    void registersExpectedRoutes() {
        assertEquals(
                Set.of("GET api/access-log/user/:userId",
                        "GET api/access-log/active/:minutes",
                        "POST api/access-log/:state",
                        "GET api/access-log/counts_by_state/:days",
                        "GET api/access-log/counts_since/:days",
                        "GET api/access-log/users_since/:days",
                        "GET api/access-log/summary/year_on_year/:mode",
                        "GET api/access-log/get_years",
                        "GET api/access-log/summary/month_on_month/:mode/:year"),
                routes.keySet());
    }

    @Test
    void findForUserPassesLimitWhenPresent() throws Exception {
        List<AccessLog> expected = Collections.singletonList(mkAccessLog());
        when(request.params("userId")).thenReturn("bob");
        when(request.queryParams("limit")).thenReturn("5");
        when(accessLogService.findForUserId("bob", Optional.of(5))).thenReturn(expected);

        Object result = routes.get("GET api/access-log/user/:userId").handle(request, response);

        assertEquals(expected, result);
        verify(response).type(WebUtilities.TYPE_JSON);
    }

    @Test
    void findForUserPassesEmptyLimitWhenAbsent() throws Exception {
        when(request.params("userId")).thenReturn("bob");
        when(request.queryParams("limit")).thenReturn(null);
        when(accessLogService.findForUserId("bob", Optional.empty())).thenReturn(Collections.emptyList());

        Object result = routes.get("GET api/access-log/user/:userId").handle(request, response);

        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void findActiveUsersConvertsMinutesToDuration() throws Exception {
        AccessTime accessTime = ImmutableAccessTime.builder()
                .userId("bob")
                .createdAt(LocalDateTime.now())
                .build();
        when(request.params("minutes")).thenReturn("30");
        when(accessLogService.findActiveUsersSince(Duration.ofMinutes(30))).thenReturn(Collections.singletonList(accessTime));

        Object result = routes.get("GET api/access-log/active/:minutes").handle(request, response);

        assertEquals(Collections.singletonList(accessTime), result);
    }

    @Test
    void findActiveUsersRejectsNonNumericMinutes() {
        when(request.params("minutes")).thenReturn("soon");

        assertThrows(NumberFormatException.class,
                () -> routes.get("GET api/access-log/active/:minutes").handle(request, response));
        verifyNoInteractions(accessLogService);
    }

    @Test
    void countsByStateConvertsDaysToDuration() throws Exception {
        AccessLogSummary summary = mock(AccessLogSummary.class);
        when(request.params("days")).thenReturn("7");
        when(accessLogService.findAccessLogCountsByStateSince(Duration.ofDays(7))).thenReturn(Collections.singletonList(summary));

        Object result = routes.get("GET api/access-log/counts_by_state/:days").handle(request, response);

        assertEquals(Collections.singletonList(summary), result);
    }

    @Test
    void countsSinceDelegatesToWeeklySummary() throws Exception {
        AccessLogSummary summary = mock(AccessLogSummary.class);
        when(request.params("days")).thenReturn("14");
        when(accessLogService.findWeeklyAccessLogSummary(Duration.ofDays(14))).thenReturn(Collections.singletonList(summary));

        Object result = routes.get("GET api/access-log/counts_since/:days").handle(request, response);

        assertEquals(Collections.singletonList(summary), result);
    }

    @Test
    void usersSinceDelegatesToDailyUniqueUsers() throws Exception {
        AccessLogSummary summary = mock(AccessLogSummary.class);
        when(request.params("days")).thenReturn("3");
        when(accessLogService.findDailyUniqueUsersSince(Duration.ofDays(3))).thenReturn(Collections.singletonList(summary));

        Object result = routes.get("GET api/access-log/users_since/:days").handle(request, response);

        assertEquals(Collections.singletonList(summary), result);
    }

    @Test
    void yearOnYearPassesMode() throws Exception {
        AccessLogSummary summary = mock(AccessLogSummary.class);
        when(request.params("mode")).thenReturn("DISTINCT_USERS");
        when(accessLogService.findYearOnYearAccessLogSummary("DISTINCT_USERS")).thenReturn(Collections.singletonList(summary));

        Object result = routes.get("GET api/access-log/summary/year_on_year/:mode").handle(request, response);

        assertEquals(Collections.singletonList(summary), result);
    }

    @Test
    void getYearsReturnsServiceResult() throws Exception {
        when(accessLogService.findAccessLogYears()).thenReturn(asList(2022, 2023));

        Object result = routes.get("GET api/access-log/get_years").handle(request, response);

        assertEquals(asList(2022, 2023), result);
    }

    @Test
    void monthOnMonthPassesModeAndYear() throws Exception {
        AccessLogSummary summary = mock(AccessLogSummary.class);
        when(request.params("mode")).thenReturn("ACCESS_COUNT");
        when(request.params("year")).thenReturn("2023");
        when(accessLogService.findMonthOnMonthAccessLogSummary("ACCESS_COUNT", 2023)).thenReturn(Collections.singletonList(summary));

        Object result = routes.get("GET api/access-log/summary/month_on_month/:mode/:year").handle(request, response);

        assertEquals(Collections.singletonList(summary), result);
    }

    @Test
    void writeBuildsLogEntryFromRequestAndReturnsVersionInfo() throws Exception {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.params("state")).thenReturn("main.app.view");
        when(request.body()).thenReturn("{\"id\":1}");

        Object result = routes.get("POST api/access-log/:state").handle(request, response);

        assertSame(versionInfo, result);
        ArgumentCaptor<AccessLog> captor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService).write(captor.capture());
        assertEquals(USER, captor.getValue().userId());
        assertEquals("main.app.view", captor.getValue().state());
        assertEquals("{\"id\":1}", captor.getValue().params());
    }

    @Test
    void writeBubblesUpServiceFailure() {
        when(request.attribute("waltz-user")).thenReturn(USER);
        when(request.params("state")).thenReturn("main.app.view");
        when(request.body()).thenReturn("{}");
        when(accessLogService.write(any())).thenThrow(new IllegalStateException("db down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> routes.get("POST api/access-log/:state").handle(request, response));

        assertEquals("db down", ex.getMessage());
    }


    private static AccessLog mkAccessLog() {
        return ImmutableAccessLog.builder()
                .userId(USER)
                .state("main.home")
                .params("{}")
                .build();
    }
}
