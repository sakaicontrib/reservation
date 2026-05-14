package org.sakaiproject.reserva;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomFieldSerializer {

    // -------------------------------------------------------------------------
    // Field definitions
    // -------------------------------------------------------------------------

    public static String toJson(List<CustomField> fields) {
        if (fields == null || fields.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            CustomField f = fields.get(i);
            sb.append("{");
            sb.append("\"id\":\"").append(escape(f.getId())).append("\",");
            sb.append("\"label\":\"").append(escape(f.getLabel())).append("\",");
            sb.append("\"type\":\"").append(escape(f.getType())).append("\",");
            sb.append("\"required\":").append(f.isRequired()).append(",");
            sb.append("\"options\":[");
            List<String> opts = f.getOptions();
            for (int j = 0; j < opts.size(); j++) {
                sb.append("\"").append(escape(opts.get(j))).append("\"");
                if (j < opts.size() - 1) sb.append(",");
            }
            sb.append("]}");
            if (i < fields.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<CustomField> fromJson(String json) {
        List<CustomField> fields = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) return fields;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);
        for (String obj : splitJsonObjects(json)) {
            CustomField f = parseObject(obj.trim());
            if (f != null) fields.add(f);
        }
        return fields;
    }

    // -------------------------------------------------------------------------
    // Field values (Map<fieldId, value>)
    // -------------------------------------------------------------------------

    public static String valuesToJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static Map<String, String> valuesFromJson(String json, List<CustomField> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (fields == null) return result;
        for (CustomField f : fields) {
            result.put(f.getId(), extractString(json != null ? json : "{}", f.getId()));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static List<String> splitJsonObjects(String json) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0) result.add(json.substring(start, i + 1)); }
        }
        return result;
    }

    private static CustomField parseObject(String obj) {
        CustomField f = new CustomField();
        f.setId(extractString(obj, "id"));
        f.setLabel(extractString(obj, "label"));
        f.setType(extractString(obj, "type"));
        f.setRequired("true".equals(extractValue(obj, "required")));
        f.setOptions(extractArray(obj, "options"));
        return f;
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '\\') { end += 2; continue; }
            if (json.charAt(end) == '"') break;
            end++;
        }
        if (end >= json.length()) return "";
        return unescape(json.substring(start, end));
    }

    private static String extractValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        if (end < 0) return "";
        return json.substring(start, end).trim();
    }

    private static List<String> extractArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\":[";
        int start = json.indexOf(search);
        if (start < 0) return result;
        start += search.length();
        int end = json.indexOf("]", start);
        if (end < 0) return result;
        String arr = json.substring(start, end);
        for (String item : arr.split(",")) {
            item = item.trim();
            if (item.startsWith("\"")) item = item.substring(1);
            if (item.endsWith("\""))   item = item.substring(0, item.length() - 1);
            if (!item.isEmpty()) result.add(unescape(item));
        }
        return result;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}