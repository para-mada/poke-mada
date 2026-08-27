package net.paramada.pokemada.controller;

import javafx.fxml.FXML;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;

import java.util.Objects;
import java.util.function.Consumer;

/** Explicit three-owner mode: player plus an external ally against one opposing team. */
public final class AllyCombatPanelController {
    @FXML private DoubleCombatPanelController battlePanelController;

    public void configure(PokemonSpriteCache sprites, Runnable close, Runnable openLog,
                          Consumer<Integer> openPokemonDetails) {
        battlePanelController.configure(Objects.requireNonNull(sprites), Objects.requireNonNull(close),
                Objects.requireNonNull(openLog), Objects.requireNonNull(openPokemonDetails));
    }

    public void render(MainController.PartySnapshot[] party, MainController.BattleSnapshot battle,
                       MainController.ActiveSnapshot active) {
        battlePanelController.render(party, battle, active);
    }
}
