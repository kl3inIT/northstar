package com.northstar.worker.brief;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Small bounded HTTP transport for public brief sources. */
@Component
class BriefHttpClient {

    private static final int MAX_BYTES = 2_000_000;
    private static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private final HttpClient http;

    BriefHttpClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    String get(URI uri, String accept, Map<String, String> headers) {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            validatePublicUrl(current);
            HttpRequest.Builder builder = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")
                    .header("User-Agent", "NorthstarMorningBrief/2.0")
                    .GET();
            headers.forEach(builder::header);
            HttpResponse<InputStream> response = send(builder.build());
            if (REDIRECTS.contains(response.statusCode())) {
                close(response.body());
                if (redirects == MAX_REDIRECTS) throw new IllegalStateException("Source exceeded redirect limit");
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("Redirect did not include Location"));
                current = current.resolve(location);
                continue;
            }
            return successful(response);
        }
        throw new IllegalStateException("Source redirect failed");
    }

    String postJson(URI uri, String body, Map<String, String> headers, Duration timeout) {
        validatePublicUrl(uri);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "NorthstarMorningBrief/2.0")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return successful(send(builder.build()));
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Source request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Source request failed", exception);
        }
    }

    private static String successful(HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            close(response.body());
            throw new IllegalStateException("Source returned HTTP " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("Source response is too large");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Source response could not be read", exception);
        }
    }

    private static void close(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // The request is already being discarded; there is no recovery action.
        }
    }

    private static void validatePublicUrl(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Brief sources must use public HTTPS URLs");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("Private source host is blocked: " + host);
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new IllegalArgumentException("Source host did not resolve: " + host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw new IllegalArgumentException("Private source host is blocked: " + host);
            }
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Source host could not be resolved", exception);
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            return a != 0 && a != 10 && a != 127
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 0 || b == 168))
                    && !(a == 198 && (b == 18 || b == 19 || b == 51))
                    && !(a == 203 && b == 0)
                    && a < 224;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean linkLocal = first == 0xfe && (second & 0xc0) == 0x80;
            boolean documentation = first == 0x20 && second == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8;
            return !uniqueLocal && !linkLocal && !documentation && first != 0xff;
        }
        return false;
    }
}
