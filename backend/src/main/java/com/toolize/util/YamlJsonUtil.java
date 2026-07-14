package com.toolize.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class YamlJsonUtil {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private YamlJsonUtil() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
