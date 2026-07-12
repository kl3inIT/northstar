package com.northstar.integration.news.huggingnews;

import com.northstar.core.brief.BriefFeedException;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Resolves SvelteKit/devalue's flattened reference array into an ordinary JSON tree. */
final class HuggingNewsDataDecoder {

    private final ObjectMapper json;

    HuggingNewsDataDecoder(ObjectMapper json) {
        this.json = json;
    }

    JsonNode decode(String body) {
        try {
            JsonNode nodes = json.readTree(body).path("nodes");
            for (JsonNode node : nodes) {
                JsonNode data = node.path("data");
                if (!data.isArray() || data.isEmpty() || !data.path(0).isObject()) continue;
                JsonNode first = data.path(0);
                if (first.has("feedLive") || first.has("focusedStory")) {
                    return new Resolver((ArrayNode) data).resolve(0);
                }
            }
            throw new BriefFeedException("HuggingNews page data did not contain a feed payload");
        } catch (BriefFeedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BriefFeedException("HuggingNews page data could not be decoded", exception);
        }
    }

    private final class Resolver {

        private final ArrayNode values;
        private final Map<Integer, JsonNode> resolved = new HashMap<>();

        private Resolver(ArrayNode values) {
            this.values = values;
        }

        private JsonNode resolve(int index) {
            if (index < 0) return json.nullNode();
            if (index >= values.size()) throw new BriefFeedException("HuggingNews page-data reference is out of range");
            JsonNode cached = resolved.get(index);
            if (cached != null) return cached;
            JsonNode value = values.get(index);
            if (value.isObject()) {
                ObjectNode object = json.createObjectNode();
                resolved.put(index, object);
                for (Map.Entry<String, JsonNode> property : value.properties()) {
                    object.set(property.getKey(), resolve(reference(property.getValue())));
                }
                return object;
            }
            if (value.isArray()) {
                ArrayNode array = json.createArrayNode();
                resolved.put(index, array);
                for (JsonNode item : value) array.add(resolve(reference(item)));
                return array;
            }
            resolved.put(index, value);
            return value;
        }

        private int reference(JsonNode node) {
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new BriefFeedException("HuggingNews page-data reference was not an integer");
            }
            return node.intValue();
        }
    }
}
