package com.toolize.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class YamlJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
