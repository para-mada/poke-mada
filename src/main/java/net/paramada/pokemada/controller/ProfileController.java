package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

/** Current segment controls; mutations are always reconciled with server state. */
public final class ProfileController {
    @FXML private Label trainerName, segmentLabel, pokemonState, skipState, statusLabel;
    @FXML private ImageView portrait, pokemonSprite;
    @FXML private TextField searchField, routeField;
    @FXML private StackPane profileRoot, pokemonSelector;
    @FXML private ScrollPane profileContent;
    @FXML private VBox selectorCard;
    @FXML private TableView<Species> speciesTable;
    @FXML private TableColumn<Species, Species> spriteColumn;
    @FXML private TableColumn<Species, Integer> numberColumn;
    @FXML private TableColumn<Species, String> nameColumn;
    @FXML private Label selectionLabel, resultsLabel;
    @FXML private Button registerButton;
    @FXML private TextField deathCountField;
    @FXML private Label deathCountState;
    @FXML private Button decreaseDeathsButton, increaseDeathsButton, saveDeathsButton;
    @FXML private Button chooseButton, skipButton, refreshButton;
    private final PokemonSpriteCache sprites = new PokemonSpriteCache();
    private List<Species> species = List.of();
    private ServerClient.CommunityProfile profile;
    private ServerClient.CommunityPermissions permissions;
    private long generation;
    private long spriteGeneration;
    private boolean busy;

    @FXML private void initialize() {
        deathCountField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,10}") ? change : null));
        deathCountField.textProperty().addListener((o, old, value) -> updateControls());
        searchField.textProperty().addListener((o, old, value) -> filterSpecies());
        speciesTable.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> {
            selectionLabel.setText(value == null ? "Selecciona un Pokémon de la lista" : value.toString());
            updateControls();
        });
        spriteColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue()));
        numberColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().id()));
        nameColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().name()));
        spriteColumn.setCellFactory(column -> new TableCell<>() {
            private final ImageView image = new ImageView();
            private long revision;
            { image.setFitWidth(48); image.setFitHeight(48); image.setPreserveRatio(true); image.setSmooth(false); }
            @Override protected void updateItem(Species value, boolean empty) {
                super.updateItem(value, empty);
                long request = ++revision;
                image.setImage(null);
                setText(null);
                setGraphic(empty || value == null ? null : image);
                if (!empty && value != null) sprites.load(value.id()).thenAccept(result -> Platform.runLater(() -> {
                    if (revision == request) image.setImage(result.orElse(null));
                }));
            }
        });
        speciesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        speciesTable.setPlaceholder(new Label("No se encontraron Pokémon. Prueba otro nombre o número."));
        selectorCard.maxHeightProperty().bind(profileRoot.heightProperty().subtract(48));
        pokemonSelector.setOnMouseClicked(event -> {
            if (event.getTarget() == pokemonSelector) closePokemonSelector();
        });
        pokemonSelector.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) { closePokemonSelector(); event.consume(); }
        });
        routeField.textProperty().addListener((o, old, value) -> updateControls());
        updateControls();
    }

    public void setIdentity(String name, Image image) {
        trainerName.setText(name);
        portrait.setImage(image);
    }

    public void reset() {
        generation++;
        busy = false;
        profile = null;
        permissions = null;
        closePokemonSelector();
        routeField.clear();
        showSprite(0);
        segmentLabel.setText("Tramo —");
        pokemonState.setText("Sin consultar");
        skipState.setText("Sin consultar");
        statusLabel.setText("");
        deathCountField.clear();
        deathCountState.setText("Sin consultar");
        updateControls();
    }

    @FXML public void refresh() {
        if (busy) return;
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank()) { reset(); return; }
        closePokemonSelector();
        long request = ++generation;
        busy = true;
        profile = null;
        permissions = null;
        updateControls();
        statusLabel.setText("Consultando perfil…");
        ServerClient client = new ServerClient(settings.baseUrl());
        // Optional permission discovery must not hide otherwise readable profile data.
        client.communityPermissions(settings.token()).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (request != generation) return;
            permissions = failure == null ? result : null;
            updateControls();
        }));
        client.communityProfile(settings.token()).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (request != generation) return;
            busy = false;
            if (failure != null) {
                deathCountField.clear();
                deathCountState.setText("No disponible");
                pokemonState.setText("No disponible");
                skipState.setText("No disponible");
                showSprite(0);
                statusLabel.setText(errorMessage(failure));
            } else {
                profile = result;
                deathCountField.setText(result.deathCount() == null ? "" : result.deathCount().toString());
                segmentLabel.setText("Tramo " + result.segment());
                pokemonState.setText(result.pokemon() == null ? "Pendiente de elección"
                        : PokemonSpeciesDex.nameOrFallback(result.pokemon()) + " · #" + result.pokemon());
                skipState.setText(result.skipAvailable() == null ? "Sin información"
                        : result.skipAvailable() ? "Disponible" : "Utilizado en este tramo");
                showSprite(result.pokemon() == null ? 0 : result.pokemon());
                statusLabel.setText("Perfil actualizado.");
            }
            updateControls();
        }));
    }

    private void filterSpecies() {
        Species selected = speciesTable.getSelectionModel().getSelectedItem();
        String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
        boolean numberSearch = query.matches("#?\\d+");
        String number = query.replaceFirst("^#?0*", "");
        speciesTable.getItems().setAll(species.stream().filter(s ->
                numberSearch ? Integer.toString(s.id()).equals(number)
                        : s.name().toLowerCase(Locale.ROOT).contains(query)).toList());
        speciesTable.getSelectionModel().select(speciesTable.getItems().contains(selected) ? selected : null);
        resultsLabel.setText(speciesTable.getItems().size() + " Pokémon");
        speciesTable.scrollTo(0);
    }

    @FXML private void openPokemonSelector() {
        if (chooseButton.isDisabled()) return;
        species = IntStream.rangeClosed(1, 821).filter(PokemonSpeciesDex::isBaseStage).mapToObj(id -> new Species(id,
                PokemonSpeciesDex.nameOrFallback(id))).toList();
        searchField.clear();
        filterSpecies();
        speciesTable.getSelectionModel().clearSelection();
        pokemonSelector.setVisible(true);
        pokemonSelector.setManaged(true);
        profileContent.setDisable(true);
        searchField.requestFocus();
    }

    @FXML private void closePokemonSelector() {
        pokemonSelector.setVisible(false);
        pokemonSelector.setManaged(false);
        profileContent.setDisable(false);
        speciesTable.getSelectionModel().clearSelection();
        speciesTable.getItems().clear();
        chooseButton.requestFocus();
    }

    private void updateControls() {
        boolean select = !busy && profile != null && profile.pokemon() == null
                && permissions != null && permissions.selectPokemon();
        boolean skip = !busy && profile != null && Boolean.TRUE.equals(profile.skipAvailable())
                && permissions != null && permissions.useSkip();
        boolean manageDeaths = !busy && profile != null && profile.deathCount() != null
                && permissions != null && permissions.manageDeaths();
        Integer deaths = enteredDeaths();
        deathCountField.setDisable(!manageDeaths);
        decreaseDeathsButton.setDisable(!manageDeaths || deaths == null || deaths == 0);
        increaseDeathsButton.setDisable(!manageDeaths || deaths == null || deaths == Integer.MAX_VALUE);
        saveDeathsButton.setDisable(!manageDeaths || deaths == null || deaths.equals(profile.deathCount()));
        if (profile != null) {
            deathCountState.setText(profile.deathCount() == null
                    ? "El servidor no proporciona el contador del overlay."
                    : permissions != null && !permissions.manageDeaths() ? "No tienes permiso para editar este contador."
                    : deaths == null ? "Escribe un número entre 0 y 2147483647."
                    : "Guardado: " + profile.deathCount()
                            + (deaths.equals(profile.deathCount()) ? "" : " · Cambios sin guardar"));
        }
        searchField.setDisable(!select);
        speciesTable.setDisable(!select);
        chooseButton.setDisable(!select);
        registerButton.setDisable(!select || speciesTable.getSelectionModel().getSelectedItem() == null);
        routeField.setDisable(!skip);
        skipButton.setDisable(!skip || routeField.getText().isBlank() || routeField.getText().strip().length() > 255);
        refreshButton.setDisable(busy);
        chooseButton.setText(permissions == null ? "PERMISOS SIN VERIFICAR"
                : !permissions.selectPokemon() ? "SIN PERMISO" : "SELECCIONAR POKÉMON");
        skipButton.setText(permissions == null ? "PERMISOS SIN VERIFICAR"
                : !permissions.useSkip() ? "SIN PERMISO" : "USAR SKIP");
    }

    @FXML private void choosePokemon() {
        if (registerButton.isDisabled()) return;
        Species selected = speciesTable.getSelectionModel().getSelectedItem();
        long expected = generation;
        if (!confirm("Fijar Pokémon de comunidad", selected + " quedará elegido para el tramo "
                + profile.segment() + ". No podrás cambiarlo desde el perfil.")) return;
        if (expected != generation || registerButton.isDisabled()) return;
        closePokemonSelector();
        mutate((client, settings) -> client.declareCommunityPokemon(settings.token(), selected.id()));
    }

    @FXML private void useSkip() {
        if (skipButton.isDisabled()) return;
        String route = routeField.getText().strip();
        long expected = generation;
        if (!confirm("Usar skip de comunidad", "Se consumirá el skip del tramo " + profile.segment()
                + " en «" + route + "». Solo está disponible una vez por tramo.")) return;
        if (expected != generation || skipButton.isDisabled()) return;
        mutate((client, settings) -> client.useCommunitySkip(settings.token(), route));
    }

    private Integer enteredDeaths() {
        try {
            int count = Integer.parseInt(deathCountField.getText());
            return count >= 0 ? count : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    @FXML private void decreaseDeaths() {
        if (!decreaseDeathsButton.isDisabled()) deathCountField.setText(Integer.toString(enteredDeaths() - 1));
    }

    @FXML private void increaseDeaths() {
        if (!increaseDeathsButton.isDisabled()) deathCountField.setText(Integer.toString(enteredDeaths() + 1));
    }

    @FXML private void saveDeaths() {
        if (saveDeathsButton.isDisabled()) return;
        int deaths = enteredDeaths();
        mutate((client, settings) -> client.updateDeathCount(settings.token(), deaths));
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(statusLabel.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void mutate(java.util.function.BiFunction<ServerClient, ServerSettings, CompletableFuture<Void>> command) {
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank()) { reset(); return; }
        busy = true;
        long request = ++generation;
        updateControls();
        statusLabel.setText("Guardando…");
        command.apply(new ServerClient(settings.baseUrl()), settings).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (request != generation) return;
            busy = false;
            // Never blindly retry a one-time action after an ambiguous transport failure.
            if (failure != null) {
                profile = null;
                updateControls();
                statusLabel.setText(errorMessage(failure) + " Actualiza el perfil para comprobar el estado antes de continuar.");
            } else {
                routeField.clear();
                refresh();
            }
        }));
    }

    private void showSprite(int id) {
        long request = ++spriteGeneration;
        pokemonSprite.setImage(null);
        if (id > 0) sprites.load(id).thenAccept(image -> Platform.runLater(() -> {
            if (request == spriteGeneration) pokemonSprite.setImage(image.orElse(null));
        }));
    }

    private static String errorMessage(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof ServerClient.ServerException server) {
            return switch (server.statusCode()) {
                case 401 -> "La sesión ha caducado. Vuelve a iniciar sesión.";
                case 403 -> "Tu cuenta no tiene permiso para esta acción.";
                case 400 -> "La acción no está disponible en el tramo actual.";
                default -> "No se pudo consultar o guardar el perfil (HTTP " + server.statusCode() + ").";
            };
        }
        return "No se pudo conectar con el servidor.";
    }

    private record Species(int id, String name) {
        @Override public String toString() { return "#" + id + " · " + name; }
    }
}
