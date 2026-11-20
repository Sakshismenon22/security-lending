package com.example.autoscaling.proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.RestController;
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
 * Lightweight HTTP reverse proxy for BorrowDesk's Borrower Service API calls.
 * Browser requests to /borrower/** are forwarded to a healthy JVM selected
 * by BorrowerLoadBalancer. WebSocket upgrades are intentionally not handled here.
 */
@RestController
public class BorrowerProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final BorrowerLoadBalancer loadBalancer;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public BorrowerProxyController(BorrowerLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    @RequestMapping(
            path = "/borrower/**",
            method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
                    RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
    )
    public void proxy(HttpServletRequest incoming, HttpServletResponse outgoing) throws IOException {
        String requestUri = incoming.getRequestURI();
        String forwardedPath = requestUri.substring("/borrower".length());
        String query = incoming.getQueryString();
        int targetPort = loadBalancer.nextPort();

        URI targetUri = URI.create("http://127.0.0.1:" + targetPort + forwardedPath
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
                if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
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
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException unavailable) {
            outgoing.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "No Borrower Service instance is currently reachable.");
        } catch (java.net.http.HttpTimeoutException timeout) {
            outgoing.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    "Borrower Service request timed out.");
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
