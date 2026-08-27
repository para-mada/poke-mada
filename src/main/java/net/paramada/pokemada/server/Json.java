package net.paramada.pokemada.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, dependency-free JSON reader for the server's response DTOs. */
public final class Json {
    private final String source;
    private int position;

    private Json(String source) {
        this.source = source;
    }

    public static Object parse(String source) {
        Json reader = new Json(source == null ? "" : source);
        Object value = reader.value();
        reader.whitespace();
        if (reader.position != reader.source.length()) reader.fail("unexpected trailing data");
        return value;
    }

    private Object value() {
        whitespace();
        if (position >= source.length()) return fail("expected a value");
        return switch (source.charAt(position)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        position++;
        Map<String, Object> result = new LinkedHashMap<>();
        whitespace();
        if (take('}')) return result;
        while (true) {
            whitespace();
            if (position >= source.length() || source.charAt(position) != '"') fail("expected an object key");
            String key = string();
            whitespace();
            if (!take(':')) fail("expected ':'");
            result.put(key, value());
            whitespace();
            if (take('}')) return result;
            if (!take(',')) fail("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        position++;
        List<Object> result = new ArrayList<>();
        whitespace();
        if (take(']')) return result;
        while (true) {
            result.add(value());
            whitespace();
            if (take(']')) return result;
            if (!take(',')) fail("expected ',' or ']'");
        }
    }

    private String string() {
        position++;
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (character == '"') return result.toString();
            if (character != '\\') {
                if (character < 0x20) fail("control character in string");
                result.append(character);
                continue;
            }
            if (position >= source.length()) fail("unfinished escape");
            char escaped = source.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (position + 4 > source.length()) fail("unfinished unicode escape");
                    try {
                        result.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                    } catch (NumberFormatException invalid) {
                        fail("invalid unicode escape");
                    }
                    position += 4;
                }
                default -> fail("unknown escape");
            }
        }
        return fail("unfinished string");
    }

    private Object number() {
        int start = position;
        if (take('-')) { /* sign */ }
        while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        }
        if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
            decimal = true;
            position++;
            if (!take('+')) take('-');
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        }
        if (start == position) return fail("expected a value");
        String raw = source.substring(start, position);
        try {
            if (decimal) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException invalid) {
            return fail("invalid number");
        }
    }

    private Object literal(String text, Object value) {
        if (!source.startsWith(text, position)) return fail("invalid literal");
        position += text.length();
        return value;
    }

    private boolean take(char expected) {
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void whitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
    }

    private <T> T fail(String message) {
        throw new IllegalArgumentException(message + " at JSON offset " + position);
    }
}
