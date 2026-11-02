package com.vanrides.vanrides.whatsapp.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates only the OpenAPI 3.0 Schema Object subset used by this contract's fixtures. */
public final class ContractFixtureValidator {

    private static final Set<String> SUPPORTED = Set.of(
            "$ref", "title", "description", "type", "format", "pattern", "minLength", "maxLength",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "nullable", "enum",
            "properties", "required", "additionalProperties", "oneOf", "items", "minItems", "maxItems",
            "uniqueItems", "discriminator");

    private ContractFixtureValidator() {}

    public static List<String> validate(JsonNode document, String schemaName, JsonNode value) {
        JsonNode schema = document.at("/components/schemas/" + schemaName);
        if (schema.isMissingNode()) {
            throw new IllegalArgumentException("Missing local schema: " + schemaName);
        }
        return validate(document, schema, value, "$", 0);
    }

    static void assertLocalReferences(JsonNode document) {
        inspectReferences(document, document);
    }

    private static void inspectReferences(JsonNode document, JsonNode node) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String reference = node.get("$ref").asText();
                if (!reference.startsWith("#/components/")) {
                    throw new IllegalArgumentException("Only local component references are permitted");
                }
                if (document.at(reference.substring(1)).isMissingNode()) {
                    throw new IllegalArgumentException("Unresolved local reference: " + reference);
                }
            }
            node.elements().forEachRemaining(child -> inspectReferences(document, child));
        } else if (node.isArray()) {
            node.forEach(child -> inspectReferences(document, child));
        }
    }

    private static List<String> validate(
            JsonNode document, JsonNode original, JsonNode value, String path, int depth) {
        if (depth > 60) {
            throw new IllegalArgumentException("Cyclic or excessively deep contract schema");
        }
        JsonNode schema = original;
        if (schema.has("$ref")) {
            String reference = schema.get("$ref").asText();
            if (!reference.startsWith("#/components/schemas/")) {
                throw new IllegalArgumentException("Fixture schema reference must be local");
            }
            schema = document.at(reference.substring(1));
            if (schema.isMissingNode()) {
                throw new IllegalArgumentException("Unresolved fixture schema reference");
            }
            return validate(document, schema, value, path, depth + 1);
        }
        Iterator<String> keywords = schema.fieldNames();
        while (keywords.hasNext()) {
            String keyword = keywords.next();
            if (!keyword.startsWith("x-") && !SUPPORTED.contains(keyword)) {
                throw new IllegalArgumentException("Fixture validator does not support keyword: " + keyword);
            }
        }
        List<String> errors = new ArrayList<>();
        if (value.isNull()) {
            if (!schema.path("nullable").asBoolean(false)) {
                errors.add(path + " must not be null");
            }
            return errors;
        }
        if (schema.has("oneOf")) {
            int matches = 0;
            for (JsonNode option : schema.get("oneOf")) {
                if (validate(document, option, value, path, depth + 1).isEmpty()) {
                    matches++;
                }
            }
            if (matches != 1) {
                errors.add(path + " must match exactly one alternative; matched " + matches);
            }
            return errors;
        }
        String type = schema.path("type").asText("");
        boolean correctType = switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber() && Double.isFinite(value.doubleValue());
            case "boolean" -> value.isBoolean();
            default -> throw new IllegalArgumentException("Unknown or missing fixture schema type: " + type);
        };
        if (!correctType) {
            errors.add(path + " has wrong type; expected " + type);
            return errors;
        }
        if (schema.has("enum")) {
            boolean allowed = false;
            for (JsonNode option : schema.get("enum")) {
                allowed |= option.equals(value);
            }
            if (!allowed) {
                errors.add(path + " is outside the closed enum");
            }
        }
        switch (type) {
            case "object" -> validateObject(document, schema, value, path, depth, errors);
            case "array" -> validateArray(document, schema, value, path, depth, errors);
            case "string" -> validateString(schema, value.asText(), path, errors);
            case "integer", "number" -> validateNumber(schema, value.doubleValue(), path, errors);
            default -> {
                // Boolean values have already passed their type/enum checks.
            }
        }
        return errors;
    }

    private static void validateObject(JsonNode document, JsonNode schema, JsonNode value,
            String path, int depth, List<String> errors) {
        for (JsonNode required : schema.path("required")) {
            if (!value.has(required.asText())) {
                errors.add(path + "." + required.asText() + " is required");
            }
        }
        JsonNode properties = schema.path("properties");
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (properties.has(field.getKey())) {
                errors.addAll(validate(document, properties.get(field.getKey()), field.getValue(),
                        path + "." + field.getKey(), depth + 1));
            } else if (!schema.path("additionalProperties").asBoolean(true)) {
                errors.add(path + "." + field.getKey() + " is forbidden");
            }
        }
    }

    private static void validateArray(JsonNode document, JsonNode schema, JsonNode value,
            String path, int depth, List<String> errors) {
        if (value.size() < schema.path("minItems").asInt(0)
                || value.size() > schema.path("maxItems").asInt(Integer.MAX_VALUE)) {
            errors.add(path + " has invalid item count");
        }
        Set<JsonNode> seen = new HashSet<>();
        for (int i = 0; i < value.size(); i++) {
            JsonNode item = value.get(i);
            if (schema.path("uniqueItems").asBoolean(false) && !seen.add(item)) {
                errors.add(path + " contains duplicate items");
            }
            errors.addAll(validate(document, schema.path("items"), item, path + "[" + i + "]", depth + 1));
        }
    }

    private static void validateString(JsonNode schema, String value, String path, List<String> errors) {
        int length = value.codePointCount(0, value.length());
        if (length < schema.path("minLength").asInt(0)
                || length > schema.path("maxLength").asInt(Integer.MAX_VALUE)) {
            errors.add(path + " has invalid string length");
        }
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(value).find()) {
            errors.add(path + " does not match the required pattern");
        }
        try {
            switch (schema.path("format").asText("")) {
                case "uuid" -> {
                    if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                        errors.add(path + " is not a canonical UUID");
                    }
                }
                case "date-time" -> Instant.parse(value);
                case "uri" -> {
                    if (!URI.create(value).isAbsolute()) {
                        errors.add(path + " is not an absolute URI");
                    }
                }
                case "" -> {
                    // No additional format assertion.
                }
                default -> throw new IllegalArgumentException("Unsupported fixture string format");
            }
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
            errors.add(path + " has invalid format");
        }
    }

    private static void validateNumber(JsonNode schema, double value, String path, List<String> errors) {
        if (schema.has("minimum")) {
            double minimum = schema.get("minimum").doubleValue();
            if (value < minimum || (value == minimum && schema.path("exclusiveMinimum").asBoolean(false))) {
                errors.add(path + " is below the permitted minimum");
            }
        }
        if (schema.has("maximum")) {
            double maximum = schema.get("maximum").doubleValue();
            if (value > maximum || (value == maximum && schema.path("exclusiveMaximum").asBoolean(false))) {
                errors.add(path + " exceeds the permitted maximum");
            }
        }
    }
}
