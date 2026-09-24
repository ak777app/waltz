package org.finos.waltz.web;

import org.finos.waltz.web.endpoints.Endpoint;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import spark.ResponseTransformer;
import spark.Route;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Records the routes an {@link Endpoint} registers (keyed by {@code "METHOD path"}) without
 * starting Spark, so handlers can be invoked directly with mocked request/response objects.
 */
public final class EndpointTestUtilities {

    private EndpointTestUtilities() {
    }


    public static Map<String, Route> captureRoutes(Endpoint endpoint) {
        Map<String, Route> routes = new HashMap<>();
        try (MockedStatic<Spark> spark = Mockito.mockStatic(Spark.class)) {
            spark.when(() -> Spark.get(anyString(), any(Route.class), any(ResponseTransformer.class)))
                    .thenAnswer(record(routes, "GET"));
            spark.when(() -> Spark.post(anyString(), any(Route.class), any(ResponseTransformer.class)))
                    .thenAnswer(record(routes, "POST"));
            spark.when(() -> Spark.put(anyString(), any(Route.class), any(ResponseTransformer.class)))
                    .thenAnswer(record(routes, "PUT"));
            spark.when(() -> Spark.delete(anyString(), any(Route.class), any(ResponseTransformer.class)))
                    .thenAnswer(record(routes, "DELETE"));
            endpoint.register();
        }
        return routes;
    }


    public static String routeKey(String method, String path) {
        return method + " " + path;
    }


    private static Answer<Void> record(Map<String, Route> routes, String method) {
        return invocation -> {
            String path = invocation.getArgument(0);
            Route route = invocation.getArgument(1);
            routes.put(routeKey(method, path), route);
            return null;
        };
    }
}
