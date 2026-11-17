package de.pdfsofortdruck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sehr kleiner JSON-Reader für die flache config.json.
 * Bei Dateipfaden werden einzelne Backslashes (z. B. C:\Users\...) tolerant
 * als literale Zeichen akzeptiert, damit die Konfiguration nicht stolpert.
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    static Map<String, Object> parseObject(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        parser.expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        parser.skipWhitespace();
        if (parser.peek() == '}') {
            parser.pos++;
            return result;
        }
        while (true) {
            parser.skipWhitespace();
            String key = parser.parseString();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();
            result.put(key, parser.parseValue());
            parser.skipWhitespace();
            char c = parser.nextChar();
            if (c == '}') {
                break;
            }
            if (c != ',') {
                throw parser.error("',' oder '}' erwartet");
            }
        }
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw parser.error("unexpected text after object");
        }
        return result;
    }

    private Object parseValue() {
        char c = peek();
        switch (c) {
            case '{':
                return parseNestedObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                return parseNumber();
        }
    }

    private Map<String, Object> parseNestedObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, parseValue());
            skipWhitespace();
            char c = nextChar();
            if (c == '}') {
                return result;
            }
            if (c != ',') {
                throw error("',' oder '}' erwartet");
            }
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            char c = nextChar();
            if (c == ']') {
                return result;
            }
            if (c != ',') {
                throw error("',' oder ']' erwartet");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unvollständige Escape-Sequenz");
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("unvollständige Unicode-Sequenz");
                    }
                    try {
                        value.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("ungültige Unicode-Sequenz");
                    }
                    pos += 4;
                }
                default -> {
                    // Windows-Pfade wie "C:\Downloads" bleiben erhalten.
                    value.append('\\').append(escaped);
                }
            }
        }
        throw error("Zeichenkette nicht geschlossen");
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("Wert erwartet");
        }
        String number = text.substring(start, pos);
        try {
            if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
                return Long.parseLong(number);
            }
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw error("ungültige Zahl: " + number);
        }
    }

    private void expectLiteral(String literal) {
        if (!text.regionMatches(pos, literal, 0, literal.length())) {
            throw error(literal + " erwartet");
        }
        pos += literal.length();
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("'" + expected + "' erwartet");
        }
        pos++;
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Dateiende unerwartet");
        }
        return text.charAt(pos);
    }

    private char nextChar() {
        if (pos >= text.length()) {
            throw error("Dateiende unerwartet");
        }
        return text.charAt(pos++);
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("config.json, Position " + pos + ": " + message);
    }
}
