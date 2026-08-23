package net.paramada.pokemada.battlelog;

import java.text.Normalizer;

/** Removes stale binary/text tails from the fixed-size Sun/Moon battle text buffers. */
public final class BattleMessageSanitizer {
    private static final int MAX_MESSAGE_LENGTH = 160;

    private BattleMessageSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\n', ' ').replace('\r', ' ').strip();
        StringBuilder clean = new StringBuilder(Math.min(normalized.length(), MAX_MESSAGE_LENGTH));
        int letters = 0;
        for (int offset = 0; offset < normalized.length() && clean.length() < MAX_MESSAGE_LENGTH;) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isIgnorableGameControl(codePoint)) continue;
            if (!isAllowed(codePoint)) break;
            clean.appendCodePoint(codePoint);
            if (Character.isLetter(codePoint)) letters++;
        }
        String result = clean.toString().replaceAll("\\s+", " ").strip();
        return letters >= 2 ? result : "";
    }

    private static boolean isAllowed(int codePoint) {
        if (Character.isWhitespace(codePoint) || Character.isDigit(codePoint)) return true;
        if (Character.isLetter(codePoint)) return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
        if (Character.getType(codePoint) == Character.NON_SPACING_MARK
                || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK) return true;
        return switch (codePoint) {
            case '.', ',', ';', ':', '!', '¡', '?', '¿', '\'', '’', '“', '”', '"',
                    '-', '—', '–', '…', '(', ')', '[', ']', '/', '\\', '+', '%',
                    '#', '&', '×', '♀', '♂' -> true;
            default -> false;
        };
    }

    private static boolean isIgnorableGameControl(int codePoint) {
        return codePoint == 0xfffd || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.PRIVATE_USE;
    }
}
