package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Server-backed browser for every trainer's PC boxes and current team. */
public final class BoxesController {
    private static final int SLOTS_PER_BOX = 30;
    private static final int COLUMNS = 6;

    @FXML private ComboBox<TrainerChoice> trainerSelector;
    @FXML private ComboBox<BoxChoice> boxSelector;
    @FXML private Button teamButton;
    @FXML private GridPane boxGrid;
    @FXML private Label boxName;
    @FXML private Label occupancy;
    @FXML private Label dataStatus;

    private final PokemonSpriteCache sprites = new PokemonSpriteCache();
    private boolean loading;
    private boolean showingTeam;
    private Consumer<ServerClient.Pokemon> pokemonDetailsAction = ignored -> { };

    @FXML
    private void initialize() {
        trainerSelector.setOnAction(ignored -> trainerChanged());
        boxSelector.setOnAction(ignored -> boxChanged());
        renderSlots(List.of(), SLOTS_PER_BOX);
    }

    public void refresh() {
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank() || loading) {
            if (settings.token().isBlank()) status("Inicia sesión para consultar las cajas");
            return;
        }
        loading = true;
        status("Cargando entrenadores…");
        new ServerClient(settings.baseUrl()).trainers(settings.token()).whenComplete((trainers, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) { loading = false; status("No se pudieron cargar los entrenadores"); return; }
                    int selectedId = trainerSelector.getValue() == null ? 0 : trainerSelector.getValue().id();
                    trainerSelector.getItems().setAll(trainers.stream()
                            .map(t -> new TrainerChoice(t.id(), t.name())).toList());
                    trainerSelector.getItems().stream().filter(t -> t.id() == selectedId).findFirst()
                            .ifPresentOrElse(trainerSelector::setValue, () -> {
                                if (!trainerSelector.getItems().isEmpty()) trainerSelector.getSelectionModel().selectFirst();
                            });
                    loading = false;
                    if (trainerSelector.getValue() == null) status("No hay entrenadores disponibles"); else loadBoxes();
                }));
    }

    public void setPokemonDetailsAction(Consumer<ServerClient.Pokemon> action) {
        pokemonDetailsAction = action == null ? ignored -> { } : action;
    }

    private void trainerChanged() {
        if (!loading && trainerSelector.getValue() != null) loadBoxes();
    }

    private void loadBoxes() {
        TrainerChoice trainer = trainerSelector.getValue();
        if (trainer == null) return;
        ServerSettings settings = ServerSettings.load();
        loading = true;
        showingTeam = false;
        teamButton.setText("VER EQUIPO");
        teamButton.getStyleClass().remove("box-mode-active");
        status("Cargando cajas de " + trainer.name() + "…");
        new ServerClient(settings.baseUrl()).boxes(settings.token(), trainer.id()).whenComplete((boxes, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) { loading = false; status("No se pudo cargar la lista de cajas"); return; }
                    boxSelector.getItems().setAll(boxes.stream()
                            .map(box -> new BoxChoice(box.number(), box.name())).toList());
                    if (boxSelector.getItems().isEmpty()) {
                        loading = false;
                        renderSlots(List.of(), SLOTS_PER_BOX);
                        status("Este entrenador todavía no tiene cajas sincronizadas");
                    } else {
                        boxSelector.getSelectionModel().selectFirst();
                        loading = false;
                        loadSelectedBox();
                    }
                }));
    }

    private void boxChanged() {
        if (!loading && boxSelector.getValue() != null) loadSelectedBox();
    }

    private void loadSelectedBox() {
        TrainerChoice trainer = trainerSelector.getValue();
        BoxChoice box = boxSelector.getValue();
        if (trainer == null || box == null) return;
        showingTeam = false;
        teamButton.setText("VER EQUIPO");
        teamButton.getStyleClass().remove("box-mode-active");
        loading = true;
        status("Cargando " + box.name() + "…");
        ServerSettings settings = ServerSettings.load();
        new ServerClient(settings.baseUrl()).box(settings.token(), trainer.id(), box.number())
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    loading = false;
                    if (failure != null) { status("No se pudo cargar la caja seleccionada"); return; }
                    boxName.setText(result.name().toUpperCase());
                    renderBox(result.slots());
                    long occupied = result.slots().stream().filter(slot -> slot.pokemon() != null).count();
                    occupancy.setText(occupied + " / " + SLOTS_PER_BOX);
                    status("Viendo las cajas de " + trainer.name());
                }));
    }

    @FXML
    private void showTeam() {
        TrainerChoice trainer = trainerSelector.getValue();
        if (trainer == null || loading) return;
        if (showingTeam) {
            loadSelectedBox();
            return;
        }
        if (!teamButton.getStyleClass().contains("box-mode-active")) teamButton.getStyleClass().add("box-mode-active");
        loading = true;
        status("Cargando el equipo de " + trainer.name() + "…");
        ServerSettings settings = ServerSettings.load();
        new ServerClient(settings.baseUrl()).team(settings.token(), trainer.id())
                .whenComplete((team, failure) -> Platform.runLater(() -> {
                    loading = false;
                    if (failure != null) {
                        teamButton.getStyleClass().remove("box-mode-active");
                        status("No se pudo cargar el equipo");
                        return;
                    }
                    showingTeam = true;
                    teamButton.setText("VER CAJAS");
                    boxName.setText("EQUIPO");
                    renderSlots(team, 6);
                    occupancy.setText(team.size() + " / 6");
                    status("Viendo el equipo de " + trainer.name());
                }));
    }

    private void renderBox(List<ServerClient.BoxSlot> values) {
        ServerClient.Pokemon[] slots = new ServerClient.Pokemon[SLOTS_PER_BOX];
        for (ServerClient.BoxSlot value : values) {
            if (value.slot() >= 0 && value.slot() < slots.length) slots[value.slot()] = value.pokemon();
        }
        renderSlots(Arrays.asList(slots), SLOTS_PER_BOX);
    }

    private void renderSlots(List<ServerClient.Pokemon> values, int slotCount) {
        boxGrid.getChildren().clear();
        for (int slot = 0; slot < slotCount; slot++) {
            ServerClient.Pokemon pokemon = slot < values.size() ? values.get(slot) : null;
            boxGrid.add(pokemonCell(pokemon, slot), slot % COLUMNS, slot / COLUMNS);
        }
    }

    private StackPane pokemonCell(ServerClient.Pokemon pokemon, int slot) {
        if (pokemon == null) {
            Label empty = new Label("—");
            empty.getStyleClass().add("box-empty-mark");
            StackPane cell = new StackPane(empty);
            cell.getStyleClass().addAll("box-slot", "box-slot-empty");
            cell.setAccessibleText("Slot " + (slot + 1) + ", vacío");
            return cell;
        }
        ImageView image = new ImageView();
        image.setFitWidth(62); image.setFitHeight(56); image.setPreserveRatio(true); image.setSmooth(false);
        Label name = new Label(pokemon.name()); name.getStyleClass().add("box-pokemon-name");
        Label details = new Label("Nv. " + pokemon.level() + (pokemon.maxHp() > 0
                ? "  ·  " + pokemon.currentHp() + "/" + pokemon.maxHp() + " PS" : ""));
        details.getStyleClass().add("box-pokemon-details");
        VBox content = new VBox(0, image, name, details); content.setAlignment(Pos.CENTER);
        StackPane cell = new StackPane(content); cell.getStyleClass().addAll("box-slot", "box-slot-filled");
        cell.setAccessibleText("Slot " + (slot + 1) + ", " + pokemon.name());
        cell.getStyleClass().add("clickable-card");
        cell.setOnMouseClicked(ignored -> pokemonDetailsAction.accept(pokemon));
        sprites.load(pokemon.dexNumber()).thenAccept(result ->
                Platform.runLater(() -> result.ifPresent(image::setImage)));
        return cell;
    }

    private void status(String text) { dataStatus.setText(text); }

    private record TrainerChoice(int id, String name) { @Override public String toString() { return name; } }
    private record BoxChoice(int number, String name) { @Override public String toString() { return name; } }
}
