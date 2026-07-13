package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.GeneratedImage;
import com.northstar.core.ai.ImageGenerationException;
import com.northstar.core.ai.ImageGenerationGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Direct OpenAI Image API plus 9Router's OpenAI-compatible binary endpoint. */
@Component
public class OpenAiCompatibleImageGenerationGateway implements ImageGenerationGateway {

    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    private final AiGatewayConnectionResolver gateways;
    private final RestClient.Builder restClient;

    @Autowired
    OpenAiCompatibleImageGenerationGateway(AiGatewayRegistry gateways,
            RestClient.Builder restClient) {
        this((AiGatewayConnectionResolver) gateways, restClient);
    }

    public OpenAiCompatibleImageGenerationGateway(AiGatewayConnectionResolver gateways,
            RestClient.Builder restClient) {
        this.gateways = gateways;
        this.restClient = restClient;
    }

    @Override
    public GeneratedImage generate(AiRoute route, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("image prompt is required");
        }
        AiGatewayConnection gateway = gateways.require(route.gatewayId());
        if (!gateway.supports(AiGatewayCapability.IMAGE_GENERATION)) {
            throw new IllegalArgumentException("Gateway " + route.gatewayId()
                    + " does not support image generation");
        }
        try {
            return gateway.type() == AiGatewayType.NINE_ROUTER
                    ? generateNineRouter(gateway, route, prompt.strip())
                    : generateOpenAi(gateway, route, prompt.strip());
        } catch (ImageGenerationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ImageGenerationException(
                    "Image generation failed for gateway " + route.gatewayId(), exception);
        }
    }

    private GeneratedImage generateNineRouter(AiGatewayConnection gateway, AiRoute route, String prompt) {
        ResponseEntity<byte[]> response = client(gateway).post()
                .uri("/images/generations?response_format=binary")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG,
                        MediaType.parseMediaType("image/webp"))
                .body(body(route, prompt, false))
                .retrieve().toEntity(byte[].class);
        return checked(response.getBody(), response.getHeaders().getContentType());
    }

    private GeneratedImage generateOpenAi(AiGatewayConnection gateway, AiRoute route, String prompt) {
        ImageResponse response = client(gateway).post().uri("/images/generations")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(body(route, prompt, true)).retrieve().body(ImageResponse.class);
        String encoded = response == null || response.data() == null || response.data().isEmpty()
                ? null : response.data().getFirst().b64_json();
        if (encoded == null || encoded.isBlank()) {
            throw new ImageGenerationException("Image provider returned no base64 image");
        }
        try {
            return checked(Base64.getDecoder().decode(encoded), null);
        } catch (IllegalArgumentException exception) {
            throw new ImageGenerationException("Image provider returned invalid base64", exception);
        }
    }

    private static Map<String, Object> body(AiRoute route, String prompt, boolean directOpenAi) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", route.modelId());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", route.options().getOrDefault("size", "1024x1024"));
        if (directOpenAi) {
            body.put("quality", route.options().getOrDefault("quality", "low"));
        }
        return body;
    }

    private static GeneratedImage checked(byte[] bytes, MediaType declared) {
        if (bytes == null || bytes.length == 0) {
            throw new ImageGenerationException("Image provider returned empty image data");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new ImageGenerationException("Generated image exceeds 12MB");
        }
        String sniffed = sniff(bytes);
        if (sniffed == null) {
            throw new ImageGenerationException("Generated output is not PNG, JPEG, or WebP");
        }
        if (declared != null && !declared.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)
                && !declared.toString().equalsIgnoreCase(sniffed)) {
            throw new ImageGenerationException("Generated image type does not match its bytes");
        }
        return new GeneratedImage(bytes, sniffed);
    }

    private static String sniff(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    private RestClient client(AiGatewayConnection gateway) {
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(gateway.timeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (gateway.baseUrl().regionMatches(true, 0, "http://", 0, 7)) {
            // Internal gateways such as 9Router are clear-text HTTP/1.1 services.
            // Avoid an HTTP/2 preference/fallback handshake on long binary responses.
            http.version(HttpClient.Version.HTTP_1_1);
        }
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http.build());
        requests.setReadTimeout(gateway.timeout());
        return restClient.clone().baseUrl(gateway.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gateway.apiKey())
                .requestFactory(requests).build();
    }

    private record ImageResponse(List<ImageData> data) {
    }

    private record ImageData(String b64_json) {
    }
}
