package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.paramada.pokemada.game.assets.MoveEffectiveness;
import net.paramada.pokemada.game.assets.PokemonBaseStats;
import net.paramada.pokemada.game.assets.PokemonAbilityDex;
import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;

import java.util.Objects;
import java.util.HashMap;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Independent presentation controller for the four-position double battle layout. */
public final class DoubleCombatPanelController {
    @FXML private ScrollPane doubleCombatPanel;
    @FXML private VBox doubleCombatContent;
    @FXML private VBox enemy0Card, enemy1Card, player0Card, player1Card;
    @FXML private VBox playerTeamCard, enemyTeamCard;
    @FXML private ImageView enemy0Sprite, enemy1Sprite, player0Sprite, player1Sprite;
    @FXML private Label enemy0Name, enemy1Name, player0Name, player1Name;
    @FXML private Label player1HeaderTitle;
    @FXML private Label enemy0Meta, enemy1Meta, player0Meta, player1Meta;
    @FXML private Label enemy0Status, enemy1Status, player0Status, player1Status;
    @FXML private Label player0Ability, player1Ability, player0Item, player1Item;
    @FXML private VBox enemy0Stats, enemy1Stats, player0Stats, player1Stats;
    @FXML private GridPane player0Moves, player1Moves;
    @FXML private VBox player1MovesArea;
    @FXML private GridPane playerTeam, enemyTeam;
    @FXML private HBox enemy0Types, enemy1Types, player0Types, player1Types;
    private Runnable close = () -> {};
    private Runnable openLog = () -> {};
    private Consumer<Integer> openPokemonDetails = ignored -> {};
    private PokemonSpriteCache sprites;
    private final Map<String, Image> assets = new HashMap<>();
    private final Map<GridPane, String> moveSignatures = new IdentityHashMap<>();
    private final Map<GridPane, String> teamSignatures = new IdentityHashMap<>();
    private final Map<VBox, Integer> statSpecies = new IdentityHashMap<>();
    private final Map<HBox, String> typeSignatures = new IdentityHashMap<>();
    private final Map<ImageView, Integer> spriteSpecies = new IdentityHashMap<>();

    public void configure(PokemonSpriteCache sprites, Runnable close, Runnable openLog,
                          Consumer<Integer> openPokemonDetails) {
        this.sprites = Objects.requireNonNull(sprites); this.close = Objects.requireNonNull(close);
        this.openLog = Objects.requireNonNull(openLog); this.openPokemonDetails = Objects.requireNonNull(openPokemonDetails);
    }
    @FXML private void initialize() {
        doubleCombatContent.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, doubleCombatPanel.getViewportBounds().getWidth()),
                doubleCombatPanel.viewportBoundsProperty()));
        DoubleBinding fighterWidth = Bindings.createDoubleBinding(
                () -> Math.max(320, (doubleCombatPanel.getViewportBounds().getWidth() - 84) / 2.0),
                doubleCombatPanel.viewportBoundsProperty());
        bindWidth(fighterWidth, enemy0Card, enemy1Card, player0Card, player1Card);
        DoubleBinding teamWidth = Bindings.createDoubleBinding(
                () -> Math.max(240, (doubleCombatPanel.getViewportBounds().getWidth() - 338) / 2.0),
                doubleCombatPanel.viewportBoundsProperty());
        bindWidth(teamWidth, playerTeamCard, enemyTeamCard);
        configureMoveGrid(player0Moves);
        configureMoveGrid(player1Moves);
    }

    private static void bindWidth(DoubleBinding width, VBox... cards) {
        for (VBox card : cards) {
            card.prefWidthProperty().bind(width);
            card.maxWidthProperty().bind(width);
        }
    }
    @FXML private void close() { close.run(); }
    @FXML private void openLog() { openLog.run(); }

    public void render(MainController.PartySnapshot[] party, MainController.BattleSnapshot battle, MainController.ActiveSnapshot active) {
        MainController.BattlePokemonSnapshot p0 = active(battle.playerTeam(), active.playerOne());
        MainController.BattlePokemonSnapshot p1 = active(battle.playerTeam(), active.playerTwo());
        MainController.BattlePokemonSnapshot ally = active(battle.allyTeam(), active.playerTwo());
        boolean hasExternalAlly = active.playerTwo() != 0 && ally.species() != 0;
        if (hasExternalAlly) p1 = ally;
        player1HeaderTitle.setText(hasExternalAlly ? "Pokémon Aliado" : "Tu Pokémon");
        setVisible(player1Ability, !hasExternalAlly);
        setVisible(player1Item, !hasExternalAlly);
        setVisible(player1MovesArea, !hasExternalAlly);
        MainController.BattlePokemonSnapshot e0 = active(battle.enemyTeam(), active.enemyOne());
        MainController.BattlePokemonSnapshot e1 = active(battle.enemyTeam(), active.enemyTwo());
        fighter(p0, player0Sprite, player0Name, player0Meta, player0Status, player0Stats, player0Types); fighter(p1, player1Sprite, player1Name, player1Meta, player1Status, player1Stats, player1Types);
        fighter(e0, enemy0Sprite, enemy0Name, enemy0Meta, enemy0Status, enemy0Stats, enemy0Types); fighter(e1, enemy1Sprite, enemy1Name, enemy1Meta, enemy1Status, enemy1Stats, enemy1Types);
        renderOwnInformation(p0, player0Ability, player0Item);
        if (!hasExternalAlly) renderOwnInformation(p1, player1Ability, player1Item);
        moves(player0Moves, p0, e0, e1);
        if (!hasExternalAlly) moves(player1Moves, p1, e0, e1);
        team(playerTeam, party, false); team(enemyTeam, battle.enemyTeam(), true);
    }
    private static void setVisible(javafx.scene.Node node, boolean visible) {
        node.setManaged(visible); node.setVisible(visible);
    }
    private static MainController.BattlePokemonSnapshot active(MainController.BattlePokemonSnapshot[] team, int species) { for (var p : team) if (p.species() == species) return p; return new MainController.BattlePokemonSnapshot(0, 0, 0, 0, 0, 0, 0, 0, "Sin estado", new int[5], new int[5], new int[4], new int[4]); }
    private static void renderOwnInformation(MainController.BattlePokemonSnapshot pokemon, Label abilityLabel, Label itemLabel) {
        if (pokemon.species() == 0) {
            abilityLabel.setText("Habilidad: —"); itemLabel.setText("Objeto: —");
            return;
        }
        PokemonAbilityDex.find(pokemon.ability()).ifPresentOrElse(ability -> {
            abilityLabel.setText("Habilidad: " + ability.name());
            installDescriptionTooltip(abilityLabel, ability.name(), ability.description());
        }, () -> abilityLabel.setText("Habilidad: #" + pokemon.ability()));
        PokemonItemDex.find(pokemon.heldItem()).ifPresentOrElse(item -> {
            itemLabel.setText("Objeto: " + item.name());
            installDescriptionTooltip(itemLabel, item.name(), item.description());
        }, () -> itemLabel.setText(pokemon.heldItem() == 0 ? "Objeto: Ninguno" : "Objeto: #" + pokemon.heldItem()));
    }
    private static void installDescriptionTooltip(Label label, String name, String description) {
        Tooltip tooltip = (Tooltip) label.getProperties().get("detail-description-tooltip");
        if (tooltip == null) {
            tooltip = new Tooltip(); tooltip.setShowDelay(Duration.millis(450)); tooltip.setWrapText(true); tooltip.setMaxWidth(380);
            Tooltip.install(label, tooltip); label.getProperties().put("detail-description-tooltip", tooltip);
        }
        tooltip.setText(name + "\n\n" + (description.isBlank() ? "Sin descripción disponible en Gen VII." : description));
    }
    private void fighter(MainController.BattlePokemonSnapshot p, ImageView sprite, Label name, Label meta, Label status, VBox stats, HBox types) {
        name.setText(p.species() == 0 ? "—" : PokemonSpeciesDex.nameOrFallback(p.species())); meta.setText(p.species() == 0 ? "Sin Pokémon activo" : "#%04d · Nv. %d".formatted(p.species(), p.level())); status.setText(p.status()); status.setVisible(p.species()!=0 && !p.status().equals("Sin estado")); status.setManaged(status.isVisible());
        if (!Objects.equals(statSpecies.get(stats), p.species())) {
            statSpecies.put(stats, p.species());
            stats.getChildren().clear();
            int[] values = p.species()==0 ? new int[5] : PokemonBaseStats.forSpecies(p.species());
            for (int i=0;i<5;i++) {
                Label statName = new Label(new String[]{"ATQ", "DEF", "A.ESP", "D.ESP", "VEL"}[i]);
                statName.getStyleClass().add("stat-label"); statName.setMinWidth(42);
                ProgressBar bar = new ProgressBar(Math.min(values[i],256)/256.0);
                bar.setMaxWidth(Double.MAX_VALUE); bar.setPrefHeight(10); HBox.setHgrow(bar, Priority.ALWAYS);
                bar.getStyleClass().add(values[i] < 80 ? "stat-tier-low" : values[i] < 140 ? "stat-tier-medium" : "stat-tier-high");
                Tooltip statTooltip = new Tooltip(Integer.toString(values[i])); statTooltip.setShowDelay(Duration.millis(450)); Tooltip.install(bar, statTooltip);
                HBox row = new HBox(6, statName, bar); row.setMaxWidth(Double.MAX_VALUE); stats.getChildren().add(row);
            }
        }
        renderTypes(types, p.typeOne(), p.typeTwo(), p.species() != 0);
        if (p.species() == 0) {
            spriteSpecies.remove(sprite);
            sprite.setImage(null);
            return;
        }
        if (Objects.equals(spriteSpecies.get(sprite), p.species())) return;
        spriteSpecies.put(sprite, p.species());
        int expectedSpecies = p.species();
        sprites.load(expectedSpecies).thenAccept(result -> Platform.runLater(() -> {
            if (Objects.equals(spriteSpecies.get(sprite), expectedSpecies)) {
                sprite.setImage(result.orElse(null));
            }
        }));
    }
    private void moves(GridPane grid, MainController.BattlePokemonSnapshot attacker,
                       MainController.BattlePokemonSnapshot enemyOne, MainController.BattlePokemonSnapshot enemyTwo) {
        String signature = attacker.species() + ":" + attacker.typeOne() + ":" + attacker.typeTwo() + '|'
                + Arrays.toString(attacker.moves()) + '|' + enemyOne.species() + ':'
                + enemyOne.typeOne() + ':' + enemyOne.typeTwo() + '|' + enemyTwo.species() + ':'
                + enemyTwo.typeOne() + ':' + enemyTwo.typeTwo();
        if (signature.equals(moveSignatures.get(grid))) return;
        moveSignatures.put(grid, signature);
        grid.getChildren().clear();
        for (int slot = 0; slot < 4; slot++) {
            int id = attacker.moves()[slot];
            HBox card = new HBox(6);
            card.getStyleClass().add("detail-move-card");
            card.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);
            PokemonMoveDex.find(id).ifPresentOrElse(value -> {
                if (isStab(value.type(), attacker)) card.getStyleClass().add("stab");
                ImageView type = icon("moves/" + typeAsset(value.type()), 22);
                Label move = new Label(value.name()); move.getStyleClass().add("move-card-name");
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                card.getChildren().addAll(type, move, spacer, icon("moves/" + categoryAsset(value.category()), 21));
                moveTooltip(card, value, enemyOne, enemyTwo);
            }, () -> card.getChildren().add(new Label(id == 0 ? "—" : "#" + id)));
            grid.add(card, slot % 2, slot / 2);
        }
    }

    private static void configureMoveGrid(GridPane grid) {
        for (int column = 0; column < 2; column++) {
            javafx.scene.layout.ColumnConstraints constraints = new javafx.scene.layout.ColumnConstraints();
            constraints.setPercentWidth(50); constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
    }

    private void renderTypes(HBox types, int first, int second, boolean occupied) {
        String signature = occupied + ":" + first + ":" + second;
        if (signature.equals(typeSignatures.get(types))) return;
        typeSignatures.put(types, signature);
        types.getChildren().clear();
        if (!occupied) return;
        if (first >= 0 && first <= 17) types.getChildren().add(typeIcon(first));
        if (second >= 0 && second <= 17 && second != first) types.getChildren().add(typeIcon(second));
    }

    private ImageView icon(String asset, double size) { ImageView view = new ImageView(asset(asset)); view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true); return view; }
    private ImageView typeIcon(int type) { ImageView view = icon("moves/" + typeAsset(type), 30); Tooltip tooltip = new Tooltip(typeName(type)); tooltip.setShowDelay(Duration.millis(450)); Tooltip.install(view, tooltip); return view; }
    private Image asset(String path) { return assets.computeIfAbsent(path, key -> new Image(Objects.requireNonNull(getClass().getResource("/net/paramada/pokemada/assets/" + key)).toExternalForm())); }
    private static String categoryAsset(String category) { return switch (category.toLowerCase()) { case "físico" -> "physical_move.png"; case "especial" -> "special_move.png"; default -> "status_move.png"; }; }
    private static boolean isStab(String moveType, MainController.BattlePokemonSnapshot attacker) {
        int type = typeId(moveType);
        return attacker.species() != 0 && (attacker.typeOne() == type || attacker.typeTwo() == type);
    }
    private static int typeId(String name) {
        return switch (name) { case "Lucha" -> 1; case "Volador" -> 2; case "Veneno" -> 3;
            case "Tierra" -> 4; case "Roca" -> 5; case "Bicho" -> 6; case "Fantasma" -> 7;
            case "Acero" -> 8; case "Fuego" -> 9; case "Agua" -> 10; case "Planta" -> 11;
            case "Eléctrico" -> 12; case "Psíquico" -> 13; case "Hielo" -> 14; case "Dragón" -> 15;
            case "Siniestro" -> 16; case "Hada" -> 17; default -> 0; };
    }
    private static String typeAsset(String type) { return switch(type) { case "Lucha" -> "Fighting.png"; case "Volador" -> "Flying.png"; case "Veneno" -> "Poison.png"; case "Tierra" -> "Ground.png"; case "Roca" -> "Rock.png"; case "Bicho" -> "Bug.png"; case "Fantasma" -> "Ghost.png"; case "Acero" -> "Steel.png"; case "Fuego" -> "Fire.png"; case "Agua" -> "Water.png"; case "Planta" -> "Grass.png"; case "Eléctrico" -> "Electric.png"; case "Psíquico" -> "Psychic.png"; case "Hielo" -> "Ice.png"; case "Dragón" -> "Dragon.png"; case "Siniestro" -> "Dark.png"; case "Hada" -> "Fairy.png"; default -> "Normal.png"; }; }
    private static String typeAsset(int type) { return switch(type) { case 1 -> "Fighting.png"; case 2 -> "Flying.png"; case 3 -> "Poison.png"; case 4 -> "Ground.png"; case 5 -> "Rock.png"; case 6 -> "Bug.png"; case 7 -> "Ghost.png"; case 8 -> "Steel.png"; case 9 -> "Fire.png"; case 10 -> "Water.png"; case 11 -> "Grass.png"; case 12 -> "Electric.png"; case 13 -> "Psychic.png"; case 14 -> "Ice.png"; case 15 -> "Dragon.png"; case 16 -> "Dark.png"; case 17 -> "Fairy.png"; default -> "Normal.png"; }; }
    private static String typeName(int type) { return switch(type) { case 1 -> "Lucha"; case 2 -> "Volador"; case 3 -> "Veneno"; case 4 -> "Tierra"; case 5 -> "Roca"; case 6 -> "Bicho"; case 7 -> "Fantasma"; case 8 -> "Acero"; case 9 -> "Fuego"; case 10 -> "Agua"; case 11 -> "Planta"; case 12 -> "Eléctrico"; case 13 -> "Psíquico"; case 14 -> "Hielo"; case 15 -> "Dragón"; case 16 -> "Siniestro"; case 17 -> "Hada"; default -> "Normal"; }; }

    private void moveTooltip(HBox card, net.paramada.pokemada.game.assets.PokemonMoveDex.MoveInfo move,
                             MainController.BattlePokemonSnapshot enemyOne, MainController.BattlePokemonSnapshot enemyTwo) {
        Tooltip tooltip = new Tooltip(); tooltip.setShowDelay(Duration.millis(450)); tooltip.setWrapText(true);
        String details = "%s\n\nTipo: %s · Categoría: %s\nPotencia: %s · Precisión: %s"
                .formatted(move.description(), move.type(), move.category(), move.power() < 0 ? "—" : move.power(), move.accuracy() < 0 ? "—" : move.accuracy() + "%");
        tooltip.setText(details);
        HBox targets = new HBox(18, effectivenessTarget(move, enemyOne), effectivenessTarget(move, enemyTwo));
        VBox graphic = new VBox(6, new Label("EFECTIVIDAD"), targets);
        tooltip.setGraphic(graphic); Tooltip.install(card, tooltip); card.setPickOnBounds(true);
        card.getChildren().forEach(child -> Tooltip.install(child, tooltip));
    }

    private VBox effectivenessTarget(net.paramada.pokemada.game.assets.PokemonMoveDex.MoveInfo move, MainController.BattlePokemonSnapshot enemy) {
        ImageView sprite = new ImageView(); sprite.setFitWidth(38); sprite.setFitHeight(38); sprite.setPreserveRatio(true);
        double value = enemy.species() == 0 ? Double.NaN
                : MoveEffectiveness.against(move, enemy.typeOne(), enemy.typeTwo()).orElse(Double.NaN);
        Label multiplier = new Label(Double.isNaN(value) ? "—"
                : "x" + (value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value)));
        VBox target = new VBox(2, sprite, multiplier); target.setAlignment(javafx.geometry.Pos.CENTER);
        if (enemy.species() != 0) sprites.load(enemy.species()).thenAccept(result -> Platform.runLater(() -> sprite.setImage(result.orElse(null))));
        return target;
    }
    private void team(GridPane pane, MainController.PartySnapshot[] team, boolean hidden) {
        String signature = hidden + ":" + Arrays.toString(Arrays.stream(team)
                .mapToInt(MainController.PartySnapshot::species).toArray());
        if (signature.equals(teamSignatures.get(pane))) return;
        teamSignatures.put(pane, signature);
        pane.getChildren().clear();
        for (var p : team) {
            int slot = pane.getChildren().size();
            ImageView image = new ImageView(); image.setFitWidth(72); image.setFitHeight(56); image.setPreserveRatio(true); pane.add(image, slot % 3, slot / 3);
            if (p.species() != 0) { image.getStyleClass().add("clickable-card"); image.setOnMouseClicked(ignored -> openPokemonDetails.accept(slot)); }
            if (hidden || p.species() == 0) image.setImage(asset(p.species() == 0 ? "missingno.png" : "enemy-team-pokeball.png"));
            else sprites.load(p.species()).thenAccept(value -> Platform.runLater(() -> image.setImage(value.orElse(null))));
        }
    }
    private void team(GridPane pane, MainController.BattlePokemonSnapshot[] team, boolean hidden) {
        String signature = hidden + ":" + Arrays.toString(Arrays.stream(team)
                .mapToInt(MainController.BattlePokemonSnapshot::species).toArray());
        if (signature.equals(teamSignatures.get(pane))) return;
        teamSignatures.put(pane, signature);
        pane.getChildren().clear();
        for (int slot = 0; slot < team.length; slot++) {
            var p = team[slot];
            ImageView image = new ImageView(); image.setFitWidth(72); image.setFitHeight(56); image.setPreserveRatio(true); pane.add(image, slot % 3, slot / 3);
            image.setImage(asset(p.species() == 0 ? "missingno.png" : "enemy-team-pokeball.png"));
        }
    }

}
