package net.paramada.pokemada;

import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ApplicationFontTest {
    @Test
    void loadsLegacyPokemonCardTitleFont() throws Exception {
        try (var stream = Objects.requireNonNull(
                MainApplication.class.getResourceAsStream("fonts/PKMN-RBYGSC.ttf"))) {
            Font font = Objects.requireNonNull(Font.loadFont(stream, 16));
            assertEquals("PKMN RBYGSC", font.getFamily());
        }
    }
}
