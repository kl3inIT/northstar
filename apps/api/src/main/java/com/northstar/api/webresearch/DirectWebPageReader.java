package com.northstar.api.webresearch;

import com.northstar.core.web.WebPageProviderResult;
import com.northstar.core.web.WebPageReader;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import java.io.ByteArrayInputStream;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class DirectWebPageReader implements WebPageReader {

    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Set<String> HTML_TYPES = Set.of("text/html", "application/xhtml+xml");
    private static final Set<String> PLAIN_TYPES = Set.of(
            "text/plain", "application/json", "application/xml", "text/xml");
    private static final Pattern CHARSET = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    private final DirectWebPageReaderProperties properties;
    private final HttpClient http;
    private final HostResolver resolver;

    @Autowired
    DirectWebPageReader(DirectWebPageReaderProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), InetAddress::getAllByName);
    }

    DirectWebPageReader(DirectWebPageReaderProperties properties, HttpClient http, HostResolver resolver) {
        this.properties = properties;
        this.http = http;
        this.resolver = resolver;
    }

    @Override
    public String id() {
        return "direct";
    }

    @Override
    public String displayName() {
        return "Direct page reader";
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public boolean supports(URI url) {
        return url != null && ("http".equalsIgnoreCase(url.getScheme())
                || "https".equalsIgnoreCase(url.getScheme()));
    }

    @Override
    public WebPageProviderResult read(WebPageRequest request) {
        URI current = request.url();
        for (int redirects = 0; redirects <= properties.maxRedirects(); redirects++) {
            validatePublicUrl(current);
            HttpResponse<InputStream> response = send(current);
            if (REDIRECTS.contains(response.statusCode())) {
                close(response.body());
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                                "Redirect response did not include a Location header"));
                if (redirects == properties.maxRedirects()) {
                    throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                            "Page exceeded the redirect limit");
                }
                current = current.resolve(location);
                continue;
            }
            return consume(current, response);
        }
        throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE, "Page redirect failed");
    }

    private HttpResponse<InputStream> send(URI url) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(properties.requestTimeout())
                .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json,application/xml;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "NorthstarWebResearch/1.0")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Page request was interrupted", exception);
        } catch (IOException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Page could not be fetched", exception);
        }
    }

    private WebPageProviderResult consume(URI finalUrl, HttpResponse<InputStream> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            close(response.body());
            WebResearchFailureCode code = status >= 500
                    ? WebResearchFailureCode.UNAVAILABLE : WebResearchFailureCode.INVALID_REQUEST;
            throw new WebResearchException(code, "Page returned HTTP " + status);
        }
        long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
        if (declared > properties.maxBytes()) {
            close(response.body());
            throw new WebResearchException(WebResearchFailureCode.RESPONSE_TOO_LARGE,
                    "Page is larger than the configured byte limit");
        }
        String rawType = response.headers().firstValue("content-type").orElse("text/plain");
        String contentType = rawType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (!HTML_TYPES.contains(contentType) && !PLAIN_TYPES.contains(contentType)) {
            close(response.body());
            throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                    "Unsupported page content type: " + contentType);
        }
        byte[] bytes;
        try (InputStream input = response.body()) {
            bytes = input.readNBytes(properties.maxBytes() + 1);
        } catch (IOException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Page body could not be read", exception);
        }
        if (bytes.length > properties.maxBytes()) {
            throw new WebResearchException(WebResearchFailureCode.RESPONSE_TOO_LARGE,
                    "Page is larger than the configured byte limit");
        }

        String title = finalUrl.getHost();
        String content;
        if (HTML_TYPES.contains(contentType)) {
            try {
                Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, finalUrl.toString());
                if (!document.title().isBlank()) title = document.title().strip();
                document.select("script,style,noscript,template,svg,canvas,form,nav,footer").remove();
                Element root = document.selectFirst("main");
                if (root == null) root = document.selectFirst("article");
                if (root == null) root = document.body();
                content = root == null ? "" : root.text();
            } catch (IOException exception) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                        "HTML page could not be parsed", exception);
            }
        } else {
            content = new String(bytes, charset(rawType));
        }
        content = content.strip().replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
        boolean truncated = content.length() > properties.maxCharacters();
        if (truncated) content = content.substring(0, properties.maxCharacters()).stripTrailing();
        return new WebPageProviderResult(finalUrl, title, content, contentType, truncated);
    }

    void validatePublicUrl(URI url) {
        if (!supports(url) || url.getHost() == null || url.getHost().isBlank() || url.getUserInfo() != null) {
            throw new WebResearchException(WebResearchFailureCode.BLOCKED,
                    "Only public HTTP(S) URLs without embedded credentials are allowed");
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw blocked(host);
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) throw blocked(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw blocked(host);
            }
        } catch (UnknownHostException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Page host could not be resolved", exception);
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

    private static Charset charset(String contentType) {
        Matcher matcher = CHARSET.matcher(contentType);
        if (!matcher.find()) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(matcher.group(1).replace("\"", ""));
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private static WebResearchException blocked(String host) {
        return new WebResearchException(WebResearchFailureCode.BLOCKED,
                "Requests to private or local host are blocked: " + host);
    }

    private static void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already being discarded.
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
