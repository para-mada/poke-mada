package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.paramada.pokemada.game.assets.MoveEffectiveness;
import net.paramada.pokemada.game.assets.PokemonAbilityDex;
import net.paramada.pokemada.game.assets.PokemonBaseStats;
import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Presentation controller for a one-versus-two wild SOS encounter. */
public final class SOSCombatPanelController {
    @FXML private ImageView enemy0Sprite, enemy1Sprite, playerSprite;
    @FXML private Label enemy0Name, enemy1Name, playerName;
    @FXML private Label enemy0Meta, enemy1Meta, playerMeta;
    @FXML private Label enemy0Status, enemy1Status, playerStatus, playerAbility, playerItem;
    @FXML private HBox enemy0Types, enemy1Types, playerTypes;
    @FXML private VBox enemy0Stats, enemy1Stats, playerStats;
    @FXML private GridPane playerMoves, playerTeam, enemyTeam;

    private PokemonSpriteCache sprites;
    private Runnable close = () -> { };
    private Runnable openLog = () -> { };
    private Consumer<Integer> openPokemonDetails = ignored -> { };
    private final Map<String, Image> assets = new HashMap<>();

    public void configure(PokemonSpriteCache sprites, Runnable close, Runnable openLog,
                          Consumer<Integer> openPokemonDetails) {
        this.sprites = Objects.requireNonNull(sprites);
        this.close = Objects.requireNonNull(close);
        this.openLog = Objects.requireNonNull(openLog);
        this.openPokemonDetails = Objects.requireNonNull(openPokemonDetails);
    }

    @FXML private void close() { close.run(); }
    @FXML private void openLog() { openLog.run(); }

    public void render(MainController.PartySnapshot[] party, MainController.BattleSnapshot battle,
                       MainController.ActiveSnapshot active) {
        MainController.BattlePokemonSnapshot player = active(battle.playerTeam(), active.playerOne());
        MainController.BattlePokemonSnapshot enemy0 = active(battle.enemyTeam(), active.enemyOne());
        MainController.BattlePokemonSnapshot enemy1 = active(battle.enemyTeam(), active.enemyTwo());
        fighter(enemy0, enemy0Sprite, enemy0Name, enemy0Meta, enemy0Status, enemy0Types, enemy0Stats);
        fighter(enemy1, enemy1Sprite, enemy1Name, enemy1Meta, enemy1Status, enemy1Types, enemy1Stats);
        fighter(player, playerSprite, playerName, playerMeta, playerStatus, playerTypes, playerStats);
        ownInformation(player);
        moves(player, enemy0, enemy1);
        team(playerTeam, party);
        enemyTeam(enemyTeam, battle.enemyTeam());
    }

    private static MainController.BattlePokemonSnapshot active(MainController.BattlePokemonSnapshot[] team,
                                                                 int species) {
        for (var pokemon : team) if (pokemon.species() == species) return pokemon;
        return new MainController.BattlePokemonSnapshot(0, 0, 0, 0, 0, 0, 0, 0, "Sin estado",
                new int[5], new int[5], new int[4], new int[4]);
    }

    private void fighter(MainController.BattlePokemonSnapshot pokemon, ImageView sprite, Label name,
                         Label meta, Label status, HBox types, VBox stats) {
        boolean occupied = pokemon.species() != 0;
        name.setText(occupied ? PokemonSpeciesDex.nameOrFallback(pokemon.species()) : "—");
        meta.setText(occupied ? "#%04d · Nv. %d".formatted(pokemon.species(), pokemon.level()) : "Sin Pokémon activo");
        status.setText(pokemon.status());
        status.setManaged(occupied && !"Sin estado".equals(pokemon.status()));
        status.setVisible(status.isManaged());
        renderTypes(types, pokemon.typeOne(), pokemon.typeTwo(), occupied);
        renderStats(stats, pokemon.species());
        if (!occupied) { sprite.setImage(null); return; }
        int expected = pokemon.species();
        sprite.getProperties().put("species", expected);
        sprites.load(expected).thenAccept(result -> Platform.runLater(() -> {
            if (Objects.equals(sprite.getProperties().get("species"), expected)) sprite.setImage(result.orElse(null));
        }));
    }

    private static void renderStats(VBox target, int species) {
        if (Objects.equals(target.getProperties().get("species"), species)) return;
        target.getProperties().put("species", species);
        target.getChildren().clear();
        int[] values = species == 0 ? new int[5] : PokemonBaseStats.forSpecies(species);
        String[] names = {"ATQ", "DEF", "A.ESP", "D.ESP", "VEL"};
        for (int index = 0; index < values.length; index++) {
            Label label = new Label(names[index]); label.getStyleClass().add("stat-label"); label.setMinWidth(42);
            ProgressBar bar = new ProgressBar(Math.min(values[index], 256) / 256.0);
            bar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bar, Priority.ALWAYS);
            bar.getStyleClass().add(values[index] < 80 ? "stat-tier-low" : values[index] < 140
                    ? "stat-tier-medium" : "stat-tier-high");
            HBox row = new HBox(6, label, bar); target.getChildren().add(row);
        }
    }

    private void ownInformation(MainController.BattlePokemonSnapshot pokemon) {
        PokemonAbilityDex.find(pokemon.ability()).ifPresentOrElse(value -> {
            playerAbility.setText("Habilidad: " + value.name());
            description(playerAbility, value.description());
        }, () -> playerAbility.setText("Habilidad: #" + pokemon.ability()));
        PokemonItemDex.find(pokemon.heldItem()).ifPresentOrElse(value -> {
            playerItem.setText("Objeto: " + value.name());
            description(playerItem, value.description());
        }, () -> playerItem.setText(pokemon.heldItem() == 0 ? "Objeto: Ninguno" : "Objeto: #" + pokemon.heldItem()));
    }

    private static void description(Label label, String text) {
        Tooltip tooltip = new Tooltip(text == null || text.isBlank() ? "Sin descripción disponible." : text);
        tooltip.setShowDelay(Duration.millis(450)); tooltip.setWrapText(true); tooltip.setMaxWidth(380);
        Tooltip.install(label, tooltip);
    }

    private void moves(MainController.BattlePokemonSnapshot attacker,
                       MainController.BattlePokemonSnapshot enemy0,
                       MainController.BattlePokemonSnapshot enemy1) {
        String signature = attacker.species() + ":" + attacker.typeOne() + ":" + attacker.typeTwo() + ":"
                + Arrays.toString(attacker.moves()) + enemy0.species() + ":" + enemy1.species();
        if (signature.equals(playerMoves.getProperties().get("signature"))) return;
        playerMoves.getProperties().put("signature", signature);
        playerMoves.getChildren().clear();
        for (int slot = 0; slot < 4; slot++) {
            int moveId = attacker.moves()[slot];
            HBox card = new HBox(7); card.getStyleClass().add("detail-move-card");
            card.setMaxWidth(Double.MAX_VALUE); GridPane.setHgrow(card, Priority.ALWAYS);
            PokemonMoveDex.find(moveId).ifPresentOrElse(move -> {
                if (isStab(move.type(), attacker)) card.getStyleClass().add("stab");
                ImageView type = icon("moves/" + typeAsset(move.type()), 22);
                Label title = new Label(move.name()); title.getStyleClass().add("move-card-name");
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                card.getChildren().addAll(type, title, spacer,
                        icon("moves/" + categoryAsset(move.category()), 21));
                moveTooltip(card, move, enemy0, enemy1);
            }, () -> card.getChildren().add(new Label(moveId == 0 ? "—" : "#" + moveId)));
            playerMoves.add(card, slot % 2, slot / 2);
        }
    }

    private void moveTooltip(HBox card, PokemonMoveDex.MoveInfo move,
                             MainController.BattlePokemonSnapshot enemy0,
                             MainController.BattlePokemonSnapshot enemy1) {
        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(450)); tooltip.setWrapText(true);
        tooltip.setText("%s\n\nTipo: %s · Categoría: %s\nPotencia: %s · Precisión: %s".formatted(
                move.description(), move.type(), move.category(), move.power() < 0 ? "—" : move.power(),
                move.accuracy() < 0 ? "—" : move.accuracy() + "%"));
        HBox targets = new HBox(22, effectivenessTarget(move, enemy0, "ENEMIGO"),
                effectivenessTarget(move, enemy1, "ALIADO SOS"));
        VBox graphic = new VBox(7, new Label("EFECTIVIDAD"), targets);
        tooltip.setGraphic(graphic); Tooltip.install(card, tooltip);
        card.getChildren().forEach(child -> Tooltip.install(child, tooltip));
    }

    private VBox effectivenessTarget(PokemonMoveDex.MoveInfo move,
                                     MainController.BattlePokemonSnapshot enemy, String title) {
        ImageView sprite = new ImageView(); sprite.setFitWidth(42); sprite.setFitHeight(42); sprite.setPreserveRatio(true);
        double value = enemy.species() == 0 ? Double.NaN
                : MoveEffectiveness.against(move, enemy.typeOne(), enemy.typeTwo()).orElse(Double.NaN);
        Label name = new Label(title); name.getStyleClass().add("tooltip-target-name");
        Label multiplier = new Label(Double.isNaN(value) ? "—" : "x" + format(value));
        VBox target = new VBox(2, name, sprite, multiplier); target.setAlignment(javafx.geometry.Pos.CENTER);
        if (enemy.species() != 0) sprites.load(enemy.species()).thenAccept(result ->
                Platform.runLater(() -> sprite.setImage(result.orElse(null))));
        return target;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    private void team(GridPane pane, MainController.PartySnapshot[] team) {
        pane.getChildren().clear();
        for (int slot = 0; slot < team.length; slot++) {
            var pokemon = team[slot];
            ImageView image = rosterImage(); pane.add(image, slot % 3, slot / 3);
            if (pokemon.species() == 0) image.setImage(asset("missingno.png"));
            else {
                int selected = slot;
                image.getStyleClass().add("clickable-card");
                image.setOnMouseClicked(ignored -> openPokemonDetails.accept(selected));
                sprites.load(pokemon.species()).thenAccept(value -> Platform.runLater(() -> image.setImage(value.orElse(null))));
            }
        }
    }

    private void enemyTeam(GridPane pane, MainController.BattlePokemonSnapshot[] team) {
        pane.getChildren().clear();
        for (int slot = 0; slot < team.length; slot++) {
            ImageView image = rosterImage(); pane.add(image, slot % 3, slot / 3);
            image.setImage(asset(team[slot].species() == 0 ? "missingno.png" : "enemy-team-pokeball.png"));
        }
    }

    private static ImageView rosterImage() {
        ImageView image = new ImageView(); image.setFitWidth(72); image.setFitHeight(56); image.setPreserveRatio(true);
        return image;
    }

    private void renderTypes(HBox target, int first, int second, boolean occupied) {
        target.getChildren().clear();
        if (!occupied) return;
        target.getChildren().add(icon("moves/" + typeAsset(first), 30));
        if (second != first) target.getChildren().add(icon("moves/" + typeAsset(second), 30));
    }

    private ImageView icon(String path, double size) {
        ImageView image = new ImageView(asset(path)); image.setFitWidth(size); image.setFitHeight(size); image.setPreserveRatio(true);
        return image;
    }

    private Image asset(String path) {
        return assets.computeIfAbsent(path, key -> new Image(Objects.requireNonNull(getClass().getResource(
                "/net/paramada/pokemada/assets/" + key)).toExternalForm()));
    }

    private static String typeAsset(String type) {
        return switch (type) { case "Lucha" -> "Fighting.png"; case "Volador" -> "Flying.png";
            case "Veneno" -> "Poison.png"; case "Tierra" -> "Ground.png"; case "Roca" -> "Rock.png";
            case "Bicho" -> "Bug.png"; case "Fantasma" -> "Ghost.png"; case "Acero" -> "Steel.png";
            case "Fuego" -> "Fire.png"; case "Agua" -> "Water.png"; case "Planta" -> "Grass.png";
            case "Eléctrico" -> "Electric.png"; case "Psíquico" -> "Psychic.png"; case "Hielo" -> "Ice.png";
            case "Dragón" -> "Dragon.png"; case "Siniestro" -> "Dark.png"; case "Hada" -> "Fairy.png";
            default -> "Normal.png"; };
    }

    private static String categoryAsset(String category) {
        return switch (category.toLowerCase()) {
            case "físico" -> "physical_move.png";
            case "especial" -> "special_move.png";
            default -> "status_move.png";
        };
    }

    private static boolean isStab(String moveType, MainController.BattlePokemonSnapshot attacker) {
        int type = typeId(moveType);
        return attacker.species() != 0 && (attacker.typeOne() == type || attacker.typeTwo() == type);
    }

    private static int typeId(String name) {
        String[] names = {"Normal", "Lucha", "Volador", "Veneno", "Tierra", "Roca", "Bicho", "Fantasma",
                "Acero", "Fuego", "Agua", "Planta", "Eléctrico", "Psíquico", "Hielo", "Dragón", "Siniestro", "Hada"};
        for (int index = 0; index < names.length; index++) if (names[index].equals(name)) return index;
        return -1;
    }

    private static String typeAsset(int type) {
        String[] files = {"Normal.png", "Fighting.png", "Flying.png", "Poison.png", "Ground.png", "Rock.png",
                "Bug.png", "Ghost.png", "Steel.png", "Fire.png", "Water.png", "Grass.png", "Electric.png",
                "Psychic.png", "Ice.png", "Dragon.png", "Dark.png", "Fairy.png"};
        return type >= 0 && type < files.length ? files[type] : files[0];
    }
}
