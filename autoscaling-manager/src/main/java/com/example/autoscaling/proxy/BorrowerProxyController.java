package com.example.autoscaling.proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;

/**
 * HTTP reverse proxy for BorrowDesk. Spring Cloud LoadBalancer selects a
 * Borrower Service instance from the Eureka registry for every request.
 */
@RestController
public class BorrowerProxyController {

    private static final String BORROWER_SERVICE_ID = "borrower-service";
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final LoadBalancerClient loadBalancerClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public BorrowerProxyController(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    @RequestMapping(
            path = "/borrower/**",
            method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
                    RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
    )
    public void proxy(HttpServletRequest incoming, HttpServletResponse outgoing) throws IOException {
        ServiceInstance instance = loadBalancerClient.choose(BORROWER_SERVICE_ID);
        if (instance == null) {
            outgoing.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Eureka has no available Borrower Service instances.");
            return;
        }

        String forwardedPath = incoming.getRequestURI().substring("/borrower".length());
        String query = incoming.getQueryString();
        String baseUri = instance.getUri().toString().replaceAll("/+$", "");
        URI targetUri = URI.create(baseUri + forwardedPath
                + (query == null || query.isBlank() ? "" : "?" + query));

        HttpRequest.BodyPublisher body = hasRequestBody(incoming.getMethod())
                ? HttpRequest.BodyPublishers.ofByteArray(incoming.getInputStream().readAllBytes())
                : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofSeconds(90))
                .method(incoming.getMethod(), body);

        Enumeration<String> headerNames = incoming.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = incoming.getHeaders(name);
            while (values.hasMoreElements()) {
                requestBuilder.header(name, values.nextElement());
            }
        }

        try {
            HttpResponse<byte[]> backend = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            backend.headers().map().forEach((name, values) -> {
                if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())
                        || name.toLowerCase().startsWith("access-control-")) {
                    return;
                }
                for (String value : values) {
                    outgoing.addHeader(name, value);
                }
            });

            outgoing.setStatus(backend.statusCode());
            if (!"HEAD".equalsIgnoreCase(incoming.getMethod())
                    && backend.statusCode() != 204
                    && backend.statusCode() != 304) {
                outgoing.getOutputStream().write(backend.body());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            outgoing.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Borrower Service request was interrupted.");
        } catch (java.net.http.HttpTimeoutException timeout) {
            outgoing.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    "Borrower Service request timed out.");
        } catch (IOException ioException) {
            outgoing.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Unable to reach the selected Borrower Service instance.");
        } catch (IllegalArgumentException invalidRequest) {
            outgoing.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proxied request.");
        }
    }

    private boolean hasRequestBody(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }
}
