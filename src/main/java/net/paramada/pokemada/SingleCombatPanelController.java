package net.paramada.pokemada;

import javafx.scene.Node;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import net.paramada.pokemada.game.assets.PokemonAbilityDex;
import net.paramada.pokemada.game.assets.PokemonBaseStats;
import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.assets.MoveEffectiveness;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;

import java.util.Objects;
import java.util.HashMap;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Coordinates the standalone single-combat panel.
 *
 * Rendering remains owned by {@link MainController} because it shares the
 * live snapshots and caches, while this controller owns the panel lifecycle
 * and its navigation callbacks. Keeping this boundary here prevents the
 * panel from becoming coupled to the rest of the view hierarchy.
 */
public final class SingleCombatPanelController {
    private static final int TRACE_ABILITY_ID = 36;
    private static final int FRISK_ABILITY_ID = 119;

    @FXML
    private ScrollPane singleCombatPanel;
    private Runnable closeAction = () -> { };
    private Runnable battleLogAction = () -> { };
    private Consumer<MouseEvent> pokemonDetailsAction = ignored -> { };
    private PokemonSpriteCache spriteCache;
    private final Map<String, Image> assetCache = new HashMap<>();
    private final Map<ImageView, Integer> renderedSprites = new IdentityHashMap<>();
    private final int[] playerTeamSpecies = new int[6];
    private String moveSignature = "";

    public void bind(Runnable closeAction,
                     Runnable battleLogAction,
                     Consumer<MouseEvent> pokemonDetailsAction) {
        this.closeAction = Objects.requireNonNull(closeAction);
        this.battleLogAction = Objects.requireNonNull(battleLogAction);
        this.pokemonDetailsAction = Objects.requireNonNull(pokemonDetailsAction);
    }

    public void configure(PokemonSpriteCache spriteCache) {
        this.spriteCache = Objects.requireNonNull(spriteCache);
    }

    /** Renders the detailed single-battle view from the latest live snapshot. */
    public void render(MainController.PartySnapshot[] party, MainController.BattleSnapshot battle) {
        renderFighter("detailPlayer", battle.player(), true, true);
        renderFighter("detailEnemy", battle.enemy(),
                playerHasAbility(party, battle.player(), TRACE_ABILITY_ID),
                playerHasAbility(party, battle.player(), FRISK_ABILITY_ID));
        renderMoves(battle.player(), battle.enemy());
        for (int slot = 0; slot < 6; slot++) {
            MainController.PartySnapshot pokemon = party[slot];
            StackPane playerCard = node("detailPlayerTeamCard" + slot, StackPane.class);
            boolean occupied = pokemon.species() != 0;
            playerCard.setMouseTransparent(!occupied);
            playerCard.getStyleClass().removeAll("clickable-card", "team-pokemon-preview-empty");
            playerCard.getStyleClass().add(occupied ? "clickable-card" : "team-pokemon-preview-empty");
            loadSprite(node("detailPlayerTeam" + slot, ImageView.class), pokemon.species(), playerTeamSpecies, slot);
            int enemySpecies = battle.enemyTeam()[slot].species();
            ImageView enemySlot = node("detailEnemyTeam" + slot, ImageView.class);
            enemySlot.setImage(image(enemySpecies == 0 ? "missingno.png" : "enemy-team-pokeball.png"));
            enemySlot.setFitWidth(enemySpecies == 0 ? 76 : 56);
            enemySlot.setFitHeight(enemySpecies == 0 ? 68 : 50);
        }
    }

    private void renderFighter(String prefix, MainController.BattlePokemonSnapshot pokemon,
                               boolean showAbility, boolean showItem) {
        Label name = node(prefix + "Name", Label.class);
        Label meta = node(prefix + "Meta", Label.class);
        Label status = node(prefix + "Status", Label.class);
        Label ability = node(prefix + "Ability", Label.class);
        Label item = node(prefix + "Item", Label.class);
        ImageView sprite = node(prefix + "Sprite", ImageView.class);
        ability.setManaged(showAbility); ability.setVisible(showAbility);
        item.setManaged(showItem); item.setVisible(showItem);
        if (pokemon.species() == 0) {
            name.setText("—"); meta.setText("Sin Pokémon activo"); sprite.setImage(null);
            status.setManaged(false); status.setVisible(false);
            ability.setText("Habilidad: —"); item.setText("Objeto: —");
            renderTypes(prefix, -1, -1);
            for (int i = 0; i < 5; i++) stat(prefix, i, 0, 0);
            return;
        }
        name.setText(PokemonSpeciesDex.nameOrFallback(pokemon.species()));
        meta.setText("#%04d  ·  Nv. %d".formatted(pokemon.species(), pokemon.level()));
        renderStatus(status, pokemon.status());
        PokemonAbilityDex.find(pokemon.ability()).ifPresentOrElse(value -> {
            ability.setText("Habilidad: " + value.name()); installTooltip(ability, value.name() + "\n\n" + value.description());
        }, () -> ability.setText("Habilidad: #" + pokemon.ability()));
        PokemonItemDex.find(pokemon.heldItem()).ifPresentOrElse(value -> {
            item.setText("Objeto: " + value.name()); installTooltip(item, value.name() + "\n\n" + value.description());
        }, () -> item.setText(pokemon.heldItem() == 0 ? "Objeto: Ninguno" : "Objeto: #" + pokemon.heldItem()));
        int[] base = PokemonBaseStats.forSpecies(pokemon.species());
        for (int i = 0; i < 5; i++) stat(prefix, i, base[i], pokemon.boosts()[i]);
        renderTypes(prefix, pokemon.typeOne(), pokemon.typeTwo());
        loadSprite(sprite, pokemon.species(), null, -1);
    }

    private static boolean playerHasAbility(MainController.PartySnapshot[] party,
                                            MainController.BattlePokemonSnapshot active,
                                            int abilityId) {
        for (MainController.PartySnapshot pokemon : party) {
            if (pokemon.species() == active.species() && pokemon.ability() == abilityId) return true;
        }
        return active.ability() == abilityId;
    }

    private void stat(String prefix, int index, int value, int boost) {
        ProgressBar bar = node(prefix + "Stat" + index, ProgressBar.class);
        Label label = node(prefix + "Stat" + index + "Label", Label.class);
        Label badge = node(prefix + "Boost" + index, Label.class);
        label.setText(new String[]{"Ataque", "Defensa", "At. Esp.", "Def. Esp.", "Velocidad"}[index]);
        bar.setProgress(Math.min(value, 256) / 256.0);
        bar.getStyleClass().removeAll("stat-tier-very-low", "stat-tier-low", "stat-tier-medium", "stat-tier-high", "stat-tier-very-high", "stat-tier-exceptional", "stat-tier-extreme");
        bar.getStyleClass().add(value < 50 ? "stat-tier-very-low" : value < 80 ? "stat-tier-low" : value < 110 ? "stat-tier-medium" : value < 140 ? "stat-tier-high" : value < 180 ? "stat-tier-very-high" : value < 220 ? "stat-tier-exceptional" : "stat-tier-extreme");
        installTooltip(bar, Integer.toString(value));
        badge.setManaged(boost != 0); badge.setVisible(boost != 0); badge.setText(boost > 0 ? "+" + boost : Integer.toString(boost));
        badge.getStyleClass().removeAll("positive", "negative"); if (boost != 0) badge.getStyleClass().add(boost > 0 ? "positive" : "negative");
    }

    private void renderMoves(MainController.BattlePokemonSnapshot pokemon,
                             MainController.BattlePokemonSnapshot enemy) {
        String signature = Arrays.toString(pokemon.moves()) + '|' + pokemon.typeOne() + ':'
                + pokemon.typeTwo() + '|' + enemy.species() + ':' + enemy.typeOne() + ':'
                + enemy.typeTwo();
        if (signature.equals(moveSignature)) return;
        moveSignature = signature;
        for (int slot = 0; slot < 4; slot++) {
            HBox card = node("detailMove" + slot, HBox.class);
            card.getChildren().clear();
            card.getStyleClass().remove("stab");
            int moveId = pokemon.moves()[slot];
            if (moveId == 0) {
                card.getChildren().add(new Label("—"));
                continue;
            }
            PokemonMoveDex.find(moveId).ifPresentOrElse(move -> {
                if (hasStab(move.type(), pokemon)) card.getStyleClass().add("stab");
                Label name = new Label(move.name());
                name.getStyleClass().add("move-card-name");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                card.getChildren().addAll(typeIcon(move.type(), 27), name, spacer);
                if (enemy.species() != 0) {
                    MoveEffectiveness.against(move, enemy.typeOne(), enemy.typeTwo()).ifPresent(multiplier -> {
                        Label badge = new Label("x" + formatMultiplier(multiplier));
                        badge.getStyleClass().addAll("move-multiplier", multiplier > 1 ? "effective"
                                : multiplier < 1 ? "resisted" : "neutral");
                        card.getChildren().add(badge);
                    });
                }
                // The spacer keeps the category icon flush to the right edge.
                card.getChildren().add(categoryIcon(move.category()));
                installTooltip(card, moveTooltipText(move));
            }, () -> card.getChildren().add(new Label("Movimiento #" + moveId)));
        }
    }

    private static String moveTooltipText(PokemonMoveDex.MoveInfo move) {
        return "%s\n\nTipo: %s · Categoría: %s\nPotencia: %s · Precisión: %s · PP: %d"
                .formatted(move.description(), move.type(), move.category(),
                        move.power() < 0 ? "—" : move.power(),
                        move.accuracy() < 0 ? "—" : move.accuracy() + "%",
                        move.pp());
    }

    private static boolean hasStab(String moveType, MainController.BattlePokemonSnapshot pokemon) {
        return pokemon.species() != 0 && (moveType.equals(typeName(pokemon.typeOne()))
                || moveType.equals(typeName(pokemon.typeTwo())));
    }

    private static String formatMultiplier(double multiplier) {
        return multiplier == Math.rint(multiplier)
                ? Integer.toString((int) multiplier) : Double.toString(multiplier);
    }

    private void renderTypes(String prefix, int first, int second) {
        ImageView one = node(prefix + "TypeOne", ImageView.class);
        ImageView two = node(prefix + "TypeTwo", ImageView.class);
        type(one, first, first >= 0 && first <= 17);
        type(two, second, second >= 0 && second <= 17 && second != first);
    }

    private void type(ImageView view, int value, boolean visible) {
        view.setManaged(visible); view.setVisible(visible);
        view.setImage(visible ? image("moves/" + typeAsset(value)) : null);
        installTooltip(view, visible ? typeName(value) : "");
    }

    private ImageView typeIcon(String type, double size) {
        ImageView view = new ImageView(image("moves/" + typeAsset(type)));
        view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true);
        return view;
    }

    private ImageView categoryIcon(String category) {
        String asset = switch (category.toLowerCase()) {
            case "físico" -> "physical_move.png";
            case "especial" -> "special_move.png";
            default -> "status_move.png";
        };
        ImageView view = new ImageView(image("moves/" + asset));
        view.setFitWidth(25); view.setFitHeight(25); view.setPreserveRatio(true);
        return view;
    }

    private void loadSprite(ImageView view, int species, int[] speciesCache, int slot) {
        if (species == 0) {
            renderedSprites.remove(view);
            if (speciesCache != null) speciesCache[slot] = 0;
            view.setImage(image("missingno.png"));
            return;
        }
        if (spriteCache == null) return;
        if (Objects.equals(renderedSprites.get(view), species)) return;
        renderedSprites.put(view, species);
        if (speciesCache != null) speciesCache[slot] = species;
        spriteCache.load(species).thenAccept(result -> Platform.runLater(() -> {
            if (Objects.equals(renderedSprites.get(view), species)
                    && (speciesCache == null || speciesCache[slot] == species)) {
                view.setImage(result.orElse(null));
            }
        }));
    }

    private Image image(String asset) {
        return assetCache.computeIfAbsent(asset, key -> new Image(Objects.requireNonNull(
                SingleCombatPanelController.class.getResource("/net/paramada/pokemada/assets/" + key)).toExternalForm()));
    }

    private static void installTooltip(Node node, String text) {
        Tooltip tooltip = (Tooltip) node.getProperties().get("combat-tooltip");
        if (tooltip == null) {
            tooltip = new Tooltip(); tooltip.setShowDelay(Duration.millis(450)); tooltip.setWrapText(true); tooltip.setMaxWidth(380);
            Tooltip.install(node, tooltip); node.getProperties().put("combat-tooltip", tooltip);
        }
        tooltip.setText(text == null || text.isBlank() ? "Sin descripción disponible en Gen VII." : text);
    }

    private static void renderStatus(Label badge, String status) {
        String style = switch (status) { case "PAR" -> "paralyzed"; case "DOR" -> "asleep"; case "CON" -> "frozen"; case "QUE" -> "burned"; case "ENV" -> "poisoned"; default -> null; };
        badge.getStyleClass().removeAll("paralyzed", "asleep", "frozen", "burned", "poisoned");
        badge.setManaged(style != null); badge.setVisible(style != null);
        if (style != null) { badge.getStyleClass().add(style); badge.setText(switch (status) { case "PAR" -> "PARALIZADO"; case "DOR" -> "DORMIDO"; case "CON" -> "CONGELADO"; case "QUE" -> "QUEMADO"; default -> "ENVENENADO"; }); }
    }

    private static String typeName(int type) { return switch (type) { case 1 -> "Lucha"; case 2 -> "Volador"; case 3 -> "Veneno"; case 4 -> "Tierra"; case 5 -> "Roca"; case 6 -> "Bicho"; case 7 -> "Fantasma"; case 8 -> "Acero"; case 9 -> "Fuego"; case 10 -> "Agua"; case 11 -> "Planta"; case 12 -> "Eléctrico"; case 13 -> "Psíquico"; case 14 -> "Hielo"; case 15 -> "Dragón"; case 16 -> "Siniestro"; case 17 -> "Hada"; default -> "Normal"; }; }
    private static String typeAsset(int type) { return switch (type) { case 1 -> "Fighting.png"; case 2 -> "Flying.png"; case 3 -> "Poison.png"; case 4 -> "Ground.png"; case 5 -> "Rock.png"; case 6 -> "Bug.png"; case 7 -> "Ghost.png"; case 8 -> "Steel.png"; case 9 -> "Fire.png"; case 10 -> "Water.png"; case 11 -> "Grass.png"; case 12 -> "Electric.png"; case 13 -> "Psychic.png"; case 14 -> "Ice.png"; case 15 -> "Dragon.png"; case 16 -> "Dark.png"; case 17 -> "Fairy.png"; default -> "Normal.png"; }; }
    private static String typeAsset(String type) { return switch (type) { case "Lucha" -> "Fighting.png"; case "Volador" -> "Flying.png"; case "Veneno" -> "Poison.png"; case "Tierra" -> "Ground.png"; case "Roca" -> "Rock.png"; case "Bicho" -> "Bug.png"; case "Fantasma" -> "Ghost.png"; case "Acero" -> "Steel.png"; case "Fuego" -> "Fire.png"; case "Agua" -> "Water.png"; case "Planta" -> "Grass.png"; case "Eléctrico" -> "Electric.png"; case "Psíquico" -> "Psychic.png"; case "Hielo" -> "Ice.png"; case "Dragón" -> "Dragon.png"; case "Siniestro" -> "Dark.png"; case "Hada" -> "Fairy.png"; default -> "Normal.png"; }; }

    @FXML
    public void closeCombatDetails() {
        closeAction.run();
    }

    @FXML
    public void openCurrentBattleLog() {
        battleLogAction.run();
    }

    @FXML
    public void openPokemonDetails(MouseEvent event) {
        if (event.getSource() instanceof Node node && !node.isMouseTransparent()) {
            pokemonDetailsAction.accept(event);
        }
    }

    /**
     * Gives the host access to a node owned by this FXML document. This keeps
     * the included view self-contained while the live-data renderer is moved
     * out incrementally from MainController.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> T node(String fxId, Class<T> type) {
        Node node = find(fxId);
        if (node == null || !type.isInstance(node)) {
            throw new IllegalStateException("Single combat panel is missing node: " + fxId);
        }
        return (T) node;
    }

    Node find(String fxId) {
        return singleCombatPanel.lookup("#" + fxId);
    }
}
