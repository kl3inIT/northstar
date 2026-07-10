package com.northstar.api.webresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northstar.core.web.WebPageProviderResult;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DirectWebPageReaderTests {

    @Test
    void rejectsPrivateAddressesBeforeSendingARequest() throws Exception {
        DirectWebPageReader reader = reader(_ -> new InetAddress[] {InetAddress.getByName("127.0.0.1")});

        assertThatThrownBy(() -> reader.validatePublicUrl(URI.create("http://internal.example/")))
                .isInstanceOfSatisfying(WebResearchException.class,
                        exception -> assertThat(exception.code()).isEqualTo(WebResearchFailureCode.BLOCKED));
    }

    @Test
    void extractsMainHtmlAndDropsNavigationAndScripts() throws Exception {
        HttpServer server = server(exchange -> {
            byte[] body = """
                    <html><head><title>Research page</title></head><body>
                    <nav>Do not include navigation</nav><main><h1>Finding</h1><p>Useful evidence.</p>
                    <script>ignoreThis()</script></main></body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            DirectWebPageReader reader = reader(_ -> new InetAddress[] {InetAddress.getByName("8.8.8.8")});
            WebPageProviderResult page = reader.read(WebPageRequest.of(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/article"));

            assertThat(page.title()).isEqualTo("Research page");
            assertThat(page.content()).contains("Finding", "Useful evidence")
                    .doesNotContain("navigation", "ignoreThis");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void revalidatesTheRedirectTarget() throws Exception {
        HttpServer server = server(exchange -> {
            exchange.getResponseHeaders().add("Location", "/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        AtomicInteger resolutions = new AtomicInteger();
        try {
            DirectWebPageReader reader = reader(_ -> new InetAddress[] {InetAddress.getByName(
                    resolutions.getAndIncrement() == 0 ? "8.8.8.8" : "127.0.0.1")});

            assertThatThrownBy(() -> reader.read(WebPageRequest.of(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/start")))
                    .isInstanceOfSatisfying(WebResearchException.class,
                            exception -> assertThat(exception.code()).isEqualTo(WebResearchFailureCode.BLOCKED));
        } finally {
            server.stop(0);
        }
    }

    private static DirectWebPageReader reader(DirectWebPageReader.HostResolver resolver) {
        return new DirectWebPageReader(new WebResearchProperties.Direct(),
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), resolver);
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }
}
