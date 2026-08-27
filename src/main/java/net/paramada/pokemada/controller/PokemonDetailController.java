package net.paramada.pokemada.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import net.paramada.pokemada.game.assets.MoveEffectiveness;
import net.paramada.pokemada.game.assets.PokemonAbilityDex;
import net.paramada.pokemada.game.assets.PokemonBaseStats;
import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.PokemonTypeDex;

public final class PokemonDetailController {
    private static final String[] STAT_NAMES = {"PS", "Ataque", "Defensa", "At. Especial", "Def. Especial", "Velocidad"};
    private static final String[] TYPE_NAMES = {"Normal", "Lucha", "Volador", "Veneno", "Tierra", "Roca", "Bicho", "Fantasma", "Acero", "Fuego", "Agua", "Planta", "Eléctrico", "Psíquico", "Hielo", "Dragón", "Siniestro", "Hada"};
    private static final String[] NATURES = {"Fuerte", "Huraña", "Audaz", "Firme", "Pícara", "Osada", "Dócil", "Plácida", "Agitada", "Floja", "Miedosa", "Activa", "Seria", "Alegre", "Ingenua", "Modesta", "Afable", "Mansa", "Tímida", "Alocada", "Serena", "Amable", "Grosera", "Cauta", "Rara"};

    @FXML private StackPane root;
    @FXML private ImageView sprite;
    @FXML private ImageView typeOne;
    @FXML private ImageView typeTwo;
    @FXML private Label name;
    @FXML private Label meta;
    @FXML private Label nature;
    @FXML private Label ability;
    @FXML private Label item;
    @FXML private VBox stats;
    @FXML private FlowPane matchups;
    @FXML private FlowPane moves;
    private final PokemonSpriteCache spriteCache = new PokemonSpriteCache();
    private int renderedSpecies;
    private Tooltip abilityTooltip;
    private Tooltip itemTooltip;

    @FXML
    private void initialize() {
        abilityTooltip = descriptionTooltip(ability);
        itemTooltip = descriptionTooltip(item);
    }

    public void show(int species, String nickname, int level, int natureId, int abilityId, int itemId,
                     int[] realStats, int[] moveIds) {
        show(species, nickname, level,
                natureId >= 0 && natureId < NATURES.length ? NATURES[natureId] : "#" + natureId,
                abilityId, itemId, realStats, moveIds);
    }

    public void show(int species, String nickname, int level, String natureName, int abilityId, int itemId,
                     int[] realStats, int[] moveIds) {
        show(species, "0", nickname, level, natureName, abilityId, itemId, realStats, moveIds);
    }

    public void show(int species, String form, String nickname, int level, String natureName, int abilityId, int itemId,
                     int[] realStats, int[] moveIds) {
        renderedSpecies = species;
        root.setManaged(true);
        root.setVisible(true);
        javafx.application.Platform.runLater(root::requestFocus);
        name.setText(nickname == null || nickname.isBlank() ? speciesName(species) : nickname);
        meta.setText("#%04d  ·  %s  ·  Nv. %d".formatted(species, speciesName(species), level));
        nature.setText("Naturaleza: " + (natureName == null || natureName.isBlank() ? "Desconocida" : natureName));
        PokemonAbilityDex.find(abilityId).ifPresentOrElse(value -> {
            ability.setText("Habilidad: " + value.name());
            abilityTooltip.setText(value.name() + "\n\n" + value.description());
        }, () -> {
            ability.setText("Habilidad: #" + abilityId);
            abilityTooltip.setText("Sin descripción disponible.");
        });
        if (itemId == 0) {
            item.setText("Objeto: Ninguno");
            itemTooltip.setText("Este Pokémon no lleva ningún objeto.");
        } else PokemonItemDex.find(itemId).ifPresentOrElse(value -> {
            item.setText("Objeto: " + value.name());
            itemTooltip.setText(value.name() + "\n\n" + value.description());
        }, () -> {
            item.setText("Objeto: #" + itemId);
            itemTooltip.setText("Sin descripción disponible.");
        });
        int[] types = PokemonTypeDex.forSpecies(species, form);
        setTypeIcon(typeOne, types[0], true);
        setTypeIcon(typeTwo, types[1], types[1] != types[0]);
        renderStats(PokemonBaseStats.allForSpecies(species, form), realStats);
        renderMatchups(types[0], types[1]);
        renderMoves(moveIds);
        spriteCache.load(species).thenAccept(image -> javafx.application.Platform.runLater(() -> {
            if (renderedSpecies == species) sprite.setImage(image.orElse(null));
        }));
    }

    @FXML
    private void close() {
        root.setManaged(false);
        root.setVisible(false);
    }

    @FXML
    private void closeFromBackdrop(MouseEvent event) {
        close();
        event.consume();
    }

    @FXML
    private void consumeCardClick(MouseEvent event) {
        event.consume();
    }

    @FXML
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            close();
            event.consume();
        }
    }

    private void renderStats(int[] base, int[] real) {
        stats.getChildren().clear();
        int maximum = 255;
        for (int value : real) maximum = Math.max(maximum, value);
        for (int index = 0; index < STAT_NAMES.length; index++) {
            Label title = new Label(STAT_NAMES[index]);
            title.getStyleClass().add("modal-stat-name");
            Region track = new Region();
            track.getStyleClass().add("modal-stat-track");
            ProgressBar baseBar = bar((double) base[index] / maximum, "modal-stat-base");
            ProgressBar realBar = bar((double) real[index] / maximum, "modal-stat-real");
            StackPane graph = new StackPane(track, realBar, baseBar);
            HBox.setHgrow(graph, Priority.ALWAYS);
            if (real[index] <= base[index]) realBar.toFront(); else baseBar.toFront();
            Label values = new Label(base[index] + " / " + real[index]);
            values.getStyleClass().add("modal-stat-values");
            stats.getChildren().add(new HBox(8, title, graph, values));
        }
    }

    private static ProgressBar bar(double value, String style) {
        ProgressBar bar = new ProgressBar(Math.min(1, value));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(style);
        return bar;
    }

    private void renderMatchups(int one, int two) {
        matchups.getChildren().clear();
        double[] values = MoveEffectiveness.defensiveMultipliers(one, two);
        for (int type = 0; type < values.length; type++) {
            if (values[type] == 1) continue;
            ImageView icon = typeIcon(type, 32);
            installTypeTooltip(icon, type);
            Label multiplier = new Label("x" + format(values[type]));
            multiplier.getStyleClass().add(values[type] > 1 ? "matchup-weak" : values[type] == 0 ? "matchup-immune" : "matchup-resist");
            HBox chip = new HBox(5, icon, multiplier);
            chip.getStyleClass().add("matchup-chip");
            matchups.getChildren().add(chip);
        }
    }

    private void renderMoves(int[] moveIds) {
        moves.getChildren().clear();
        for (int id : moveIds) {
            if (id == 0) continue;
            PokemonMoveDex.find(id).ifPresentOrElse(move -> {
                HBox card = new HBox(7, typeIcon(typeId(move.type()), 25), new Label(move.name()));
                card.getStyleClass().add("modal-move-card");
                moves.getChildren().add(card);
            }, () -> moves.getChildren().add(new Label("Movimiento #" + id)));
        }
    }

    private void setTypeIcon(ImageView view, int type, boolean shown) {
        view.setManaged(shown);
        view.setVisible(shown);
        view.setImage(shown ? typeIcon(type, 38).getImage() : null);
        if (shown) installTypeTooltip(view, type);
    }

    private static Tooltip descriptionTooltip(javafx.scene.Node node) {
        Tooltip tooltip = new Tooltip();
        tooltip.getStyleClass().add("description-tooltip");
        tooltip.setShowDelay(javafx.util.Duration.millis(450));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(380);
        Tooltip.install(node, tooltip);
        return tooltip;
    }

    private static void installTypeTooltip(ImageView icon, int type) {
        Object stored = icon.getProperties().get("type-tooltip");
        Tooltip tooltip;
        if (stored instanceof Tooltip existing) {
            tooltip = existing;
        } else {
            tooltip = new Tooltip();
            tooltip.getStyleClass().add("type-tooltip");
            tooltip.setShowDelay(javafx.util.Duration.millis(450));
            Tooltip.install(icon, tooltip);
            icon.getProperties().put("type-tooltip", tooltip);
        }
        tooltip.setText(type >= 0 && type < TYPE_NAMES.length ? TYPE_NAMES[type] : "Tipo desconocido");
    }

    private static ImageView typeIcon(int type, double size) {
        ImageView view = new ImageView(new Image(PokemonDetailController.class.getResource(
                "/net/paramada/pokemada/assets/moves/" + typeFile(type)).toExternalForm()));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        return view;
    }

    private static String typeFile(int type) {
        String[] files = {"Normal.png", "Fighting.png", "Flying.png", "Poison.png", "Ground.png", "Rock.png", "Bug.png", "Ghost.png", "Steel.png", "Fire.png", "Water.png", "Grass.png", "Electric.png", "Psychic.png", "Ice.png", "Dragon.png", "Dark.png", "Fairy.png"};
        return type >= 0 && type < files.length ? files[type] : files[0];
    }

    private static int typeId(String name) {
        for (int index = 0; index < TYPE_NAMES.length; index++) if (TYPE_NAMES[index].equals(name)) return index;
        return 0;
    }

    private static String speciesName(int species) {
        return PokemonSpeciesDex.nameOrFallback(species);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) return Integer.toString((int) value);
        return Double.toString(value);
    }
}
