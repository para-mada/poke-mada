package net.paramada.pokemada.battlelog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleMessageSanitizerTest {
    @Test
    void rejectsNumericGarbageAtBattleStart() {
        assertEquals("", BattleMessageSanitizer.sanitize("12345678901234567890123"));
    }

    @Test
    void truncatesNonLatinMemoryTailAfterAValidSpanishMessage() {
        assertEquals("El entrenador Tilo te desafía",
                BattleMessageSanitizer.sanitize("El entrenador Tilo te desafía敗鬥㐀�"));
    }

    @Test
    void preservesSpanishPunctuationAccentsAndPokemonSymbols() {
        assertEquals("¿Qué debería hacer Nidoran♀? ¡Es supereficaz!",
                BattleMessageSanitizer.sanitize("¿Qué debería hacer Nidoran♀? ¡Es supereficaz!"));
    }

    @Test
    void ignoresGameFormattingGlyphsWithoutRemovingTrainerChallenge() {
        assertEquals("El entrenador Tilo te desafía",
                BattleMessageSanitizer.sanitize("\ue000El entrenador \ue001Tilo te desafía敗鬥"));
    }
}
