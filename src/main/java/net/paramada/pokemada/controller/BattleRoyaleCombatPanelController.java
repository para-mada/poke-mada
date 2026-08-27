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

/** Four-owner Battle Royale presentation; unlike doubles, each active belongs to a separate team block. */
public final class BattleRoyaleCombatPanelController {
    @FXML private ImageView fighter0Sprite, fighter1Sprite, fighter2Sprite, fighter3Sprite;
    @FXML private Label fighter0Name, fighter1Name, fighter2Name, fighter3Name;
    @FXML private Label fighter0Meta, fighter1Meta, fighter2Meta, fighter3Meta;
    @FXML private Label fighter0Status, fighter1Status, fighter2Status, fighter3Status;
    @FXML private HBox fighter0Types, fighter1Types, fighter2Types, fighter3Types;
    @FXML private VBox fighter0Stats, fighter1Stats, fighter2Stats, fighter3Stats;
    @FXML private Label playerAbility, playerItem;
    @FXML private GridPane playerMoves;

    private PokemonSpriteCache sprites;
    private Runnable close = () -> { };
    private Runnable openLog = () -> { };
    @SuppressWarnings("unused") private Consumer<Integer> openPokemonDetails = ignored -> { };
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
        MainController.BattlePokemonSnapshot player = find(battle.playerTeam(), active.playerOne());
        MainController.BattlePokemonSnapshot rivalOne = find(battle.enemyTeam(), active.enemyOne());
        MainController.BattlePokemonSnapshot rivalTwo = find(battle.allyTeam(), active.playerTwo());
        MainController.BattlePokemonSnapshot rivalThree = find(battle.fourthTeam(), active.enemyTwo());
        fighter(player, fighter0Sprite, fighter0Name, fighter0Meta, fighter0Status, fighter0Types, fighter0Stats);
        fighter(rivalOne, fighter1Sprite, fighter1Name, fighter1Meta, fighter1Status, fighter1Types, fighter1Stats);
        fighter(rivalTwo, fighter2Sprite, fighter2Name, fighter2Meta, fighter2Status, fighter2Types, fighter2Stats);
        fighter(rivalThree, fighter3Sprite, fighter3Name, fighter3Meta, fighter3Status, fighter3Types, fighter3Stats);
        ownInformation(player);
        moves(player, rivalOne, rivalTwo, rivalThree);
    }

    private static MainController.BattlePokemonSnapshot find(MainController.BattlePokemonSnapshot[] team,
                                                               int species) {
        for (var pokemon : team) if (species != 0 && pokemon.species() == species) return pokemon;
        return new MainController.BattlePokemonSnapshot(0, 0, 0, 0, 0, 0, 0, 0, "Sin estado",
                new int[5], new int[5], new int[4], new int[4]);
    }

    private void fighter(MainController.BattlePokemonSnapshot pokemon, ImageView sprite, Label name,
                         Label meta, Label status, HBox types, VBox stats) {
        boolean occupied = pokemon.species() != 0;
        name.setText(occupied ? PokemonSpeciesDex.nameOrFallback(pokemon.species()) : "—");
        meta.setText(occupied ? "#%04d · Nv. %d · %d/%d PS".formatted(pokemon.species(), pokemon.level(),
                pokemon.currentHp(), pokemon.maxHp()) : "Sin combatiente");
        status.setText(pokemon.status());
        status.setManaged(occupied && !"Sin estado".equals(pokemon.status())); status.setVisible(status.isManaged());
        types.getChildren().clear();
        if (occupied) {
            types.getChildren().add(typeIcon(pokemon.typeOne()));
            if (pokemon.typeTwo() != pokemon.typeOne()) types.getChildren().add(typeIcon(pokemon.typeTwo()));
        }
        stats(stats, pokemon.species());
        if (!occupied) { sprite.setImage(null); return; }
        int expected = pokemon.species(); sprite.getProperties().put("species", expected);
        sprites.load(expected).thenAccept(result -> Platform.runLater(() -> {
            if (Objects.equals(sprite.getProperties().get("species"), expected)) sprite.setImage(result.orElse(null));
        }));
    }

    private static void stats(VBox target, int species) {
        if (Objects.equals(target.getProperties().get("species"), species)) return;
        target.getProperties().put("species", species); target.getChildren().clear();
        int[] values = species == 0 ? new int[5] : PokemonBaseStats.forSpecies(species);
        String[] names = {"ATQ", "DEF", "A.ESP", "D.ESP", "VEL"};
        for (int index = 0; index < values.length; index++) {
            Label name = new Label(names[index]); name.getStyleClass().add("stat-label"); name.setMinWidth(42);
            ProgressBar bar = new ProgressBar(Math.min(values[index], 256) / 256.0);
            bar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bar, Priority.ALWAYS);
            bar.getStyleClass().add(values[index] < 80 ? "stat-tier-low" : values[index] < 140
                    ? "stat-tier-medium" : "stat-tier-high");
            target.getChildren().add(new HBox(6, name, bar));
        }
    }

    private void ownInformation(MainController.BattlePokemonSnapshot player) {
        playerAbility.setText(PokemonAbilityDex.find(player.ability()).map(value -> "Habilidad: " + value.name())
                .orElse("Habilidad: #" + player.ability()));
        playerItem.setText(PokemonItemDex.find(player.heldItem()).map(value -> "Objeto: " + value.name())
                .orElse(player.heldItem() == 0 ? "Objeto: Ninguno" : "Objeto: #" + player.heldItem()));
    }

    private void moves(MainController.BattlePokemonSnapshot player, MainController.BattlePokemonSnapshot... rivals) {
        String signature = player.species() + ":" + player.typeOne() + ":" + player.typeTwo() + ":"
                + Arrays.toString(player.moves()) + Arrays.toString(Arrays.stream(rivals)
                .mapToInt(MainController.BattlePokemonSnapshot::species).toArray());
        if (signature.equals(playerMoves.getProperties().get("signature"))) return;
        playerMoves.getProperties().put("signature", signature); playerMoves.getChildren().clear();
        for (int slot = 0; slot < 4; slot++) {
            int moveId = player.moves()[slot]; HBox card = new HBox(7); card.getStyleClass().add("detail-move-card");
            card.setMaxWidth(Double.MAX_VALUE); GridPane.setHgrow(card, Priority.ALWAYS);
            PokemonMoveDex.find(moveId).ifPresentOrElse(move -> {
                if (isStab(move.type(), player)) card.getStyleClass().add("stab");
                card.getChildren().add(typeIcon(move.type()));
                Label title = new Label(move.name()); title.getStyleClass().add("move-card-name"); card.getChildren().add(title);
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); card.getChildren().add(spacer);
                card.getChildren().add(categoryIcon(move.category()));
                moveTooltip(card, move, rivals);
            }, () -> card.getChildren().add(new Label(moveId == 0 ? "—" : "#" + moveId)));
            playerMoves.add(card, slot % 2, slot / 2);
        }
    }

    private ImageView typeIcon(int type) {
        String[] files = {"Normal.png", "Fighting.png", "Flying.png", "Poison.png", "Ground.png", "Rock.png",
                "Bug.png", "Ghost.png", "Steel.png", "Fire.png", "Water.png", "Grass.png", "Electric.png",
                "Psychic.png", "Ice.png", "Dragon.png", "Dark.png", "Fairy.png"};
        return icon("moves/" + (type >= 0 && type < files.length ? files[type] : files[0]), 27);
    }

    private void moveTooltip(HBox card, PokemonMoveDex.MoveInfo move,
                             MainController.BattlePokemonSnapshot[] rivals) {
        Tooltip tooltip = new Tooltip(); tooltip.setShowDelay(javafx.util.Duration.millis(450));
        tooltip.setWrapText(true);
        tooltip.setText("%s\n\nTipo: %s · Categoría: %s\nPotencia: %s · Precisión: %s".formatted(
                move.description(), move.type(), move.category(), move.power() < 0 ? "—" : move.power(),
                move.accuracy() < 0 ? "—" : move.accuracy() + "%"));
        HBox targets = new HBox(22);
        for (int index = 0; index < rivals.length; index++) {
            targets.getChildren().add(effectivenessTarget(move, rivals[index], "RIVAL " + (index + 1)));
        }
        tooltip.setGraphic(new VBox(7, new Label("EFECTIVIDAD"), targets));
        Tooltip.install(card, tooltip);
        card.getChildren().forEach(child -> Tooltip.install(child, tooltip));
    }

    private VBox effectivenessTarget(PokemonMoveDex.MoveInfo move,
                                     MainController.BattlePokemonSnapshot rival, String title) {
        ImageView sprite = new ImageView(); sprite.setFitWidth(42); sprite.setFitHeight(42); sprite.setPreserveRatio(true);
        double value = rival.species() == 0 ? Double.NaN
                : MoveEffectiveness.against(move, rival.typeOne(), rival.typeTwo()).orElse(Double.NaN);
        Label name = new Label(title); name.getStyleClass().add("tooltip-target-name");
        Label multiplier = new Label(Double.isNaN(value) ? "—" : "x" + format(value));
        VBox target = new VBox(2, name, sprite, multiplier); target.setAlignment(javafx.geometry.Pos.CENTER);
        if (rival.species() != 0) sprites.load(rival.species()).thenAccept(result ->
                Platform.runLater(() -> sprite.setImage(result.orElse(null))));
        return target;
    }

    private ImageView typeIcon(String type) {
        String[] names = {"Normal", "Lucha", "Volador", "Veneno", "Tierra", "Roca", "Bicho", "Fantasma",
                "Acero", "Fuego", "Agua", "Planta", "Eléctrico", "Psíquico", "Hielo", "Dragón", "Siniestro", "Hada"};
        for (int index = 0; index < names.length; index++) if (names[index].equals(type)) return typeIcon(index);
        return typeIcon(0);
    }

    private ImageView categoryIcon(String category) {
        String asset = switch (category.toLowerCase()) {
            case "físico" -> "physical_move.png";
            case "especial" -> "special_move.png";
            default -> "status_move.png";
        };
        return icon("moves/" + asset, 21);
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

    private ImageView icon(String path, double size) {
        Image image = assets.computeIfAbsent(path, key -> new Image(Objects.requireNonNull(getClass().getResource(
                "/net/paramada/pokemada/assets/" + key)).toExternalForm()));
        ImageView view = new ImageView(image); view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true);
        return view;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
