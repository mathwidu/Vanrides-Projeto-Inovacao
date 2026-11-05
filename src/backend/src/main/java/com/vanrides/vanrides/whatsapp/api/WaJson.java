package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Strict, context-local JSON; the legacy ObjectMapper is never reconfigured. */
public final class WaJson {
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private WaJson() { }
    public static ObjectNode object(byte[] bytes) {
        try {
            JsonNode value = MAPPER.readTree(bytes);
            if (!(value instanceof ObjectNode object)) throw WaProblem.invalid();
            return object;
        } catch (WaProblem e) { throw e; }
        catch (Exception e) { throw WaProblem.invalid(); }
    }
    public static ObjectNode object(String json) { return object(json.getBytes(StandardCharsets.UTF_8)); }
    public static void fields(JsonNode node, String... required) {
        if (!node.isObject() || node.size() != required.length) throw WaProblem.invalid();
        Set<String> names = Set.of(required);
        node.fieldNames().forEachRemaining(name -> { if (!names.contains(name)) throw WaProblem.invalid(); });
        for (String name : names) if (!node.hasNonNull(name)) throw WaProblem.invalid();
    }
    public static String text(JsonNode node, String name, int min, int max) {
        return text(node, name, min, max, false);
    }
    public static String text(JsonNode node, String name, int min, int max, boolean lineBreaks) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) throw WaProblem.invalid();
        String result = value.textValue();
        int length = result.codePointCount(0, result.length());
        if (length < min || length > max || !result.equals(result.strip())
                || result.codePoints().anyMatch(c -> Character.isISOControl(c) && !(lineBreaks && c == '\n'))) throw WaProblem.invalid();
        return result;
    }
    public static UUID uuid(String value) {
        try {
            UUID result = UUID.fromString(value);
            if (!result.toString().equals(value)) throw WaProblem.invalid();
            return result;
        } catch (IllegalArgumentException e) { throw WaProblem.invalid(); }
    }
    public static UUID uuid(JsonNode node, String name) { return uuid(text(node, name, 36, 36)); }
    public static long version(JsonNode node) {
        JsonNode value = node.get("expectedVersion");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0
                || value.longValue() > 9007199254740991L)
            throw WaProblem.invalid();
        return value.longValue();
    }
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("WA_JSON_SERIALIZATION_FAILED"); }
    }
    public static String canonical(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> ordered = new TreeMap<>();
            node.fields().forEachRemaining(e -> ordered.put(e.getKey(), e.getValue()));
            return "{" + String.join(",", ordered.entrySet().stream()
                .map(e -> write(e.getKey()) + ":" + canonical(e.getValue())).toList()) + "}";
        }
        if (node.isArray()) {
            java.util.List<String> values = new java.util.ArrayList<>();
            for (JsonNode child : node) values.add(canonical(child));
            return "[" + String.join(",", values) + "]";
        }
        return node.toString();
    }
}
