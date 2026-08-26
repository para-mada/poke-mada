package net.paramada.pokemada;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/** Server-authoritative Booster Pack catalog, opening reveal and history. */
public final class BoosterPacksController {
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter
            .ofPattern("dd MMM · HH:mm", new Locale("es", "MX")).withZone(ZoneId.systemDefault());

    @FXML private VBox packList;
    @FXML private VBox detailPanel;
    @FXML private VBox emptyState;
    @FXML private ImageView packArt;
    @FXML private Label packName;
    @FXML private Label packDescription;
    @FXML private Label quantityLabel;
    @FXML private Label contentsLabel;
    @FXML private Label guaranteeLabel;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private Button openButton;
    @FXML private Button revealAllButton;
    @FXML private VBox revealContainer;
    @FXML private VBox oddsContainer;
    @FXML private VBox historyContainer;

    private List<ServerClient.BoosterPackSummary> packs = List.of();
    private ServerClient.BoosterPackSummary selectedPack;
    private List<ServerClient.PackOpeningResult> pendingReveal = List.of();
    private Timeline revealTimeline;
    private UUID pendingIdempotencyKey;
    private Runnable mailboxRefresh = () -> { };
    private boolean loading;
    private long selectionGeneration;

    @FXML
    private void initialize() {
        showDetail(false);
        revealAllButton.setManaged(false);
        revealAllButton.setVisible(false);
        status("Inicia sesión para consultar tus sobres.", false);
    }

    public void configure(Runnable mailboxRefresh) {
        this.mailboxRefresh = mailboxRefresh == null ? () -> { } : mailboxRefresh;
    }

    @FXML
    public void refresh() {
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank()) {
            packs = List.of();
            packList.getChildren().clear();
            showDetail(false);
            status("Inicia sesión para consultar tus sobres.", true);
            return;
        }
        if (loading) return;
        loading = true;
        refreshButton.setDisable(true);
        status("Actualizando sobres…", false);
        ServerClient client = new ServerClient(settings.baseUrl());
        client.boosterPacks(settings.token()).whenComplete((result, failure) -> Platform.runLater(() -> {
            loading = false;
            refreshButton.setDisable(false);
            if (failure != null) {
                status(message(failure), true);
                return;
            }
            packs = result;
            renderPackList();
            if (packs.isEmpty()) {
                showDetail(false);
                status("No hay sobres disponibles en este tramo.", false);
                return;
            }
            ServerClient.BoosterPackSummary next = packs.stream()
                    .filter(pack -> selectedPack != null && pack.code().equals(selectedPack.code()))
                    .findFirst().orElse(packs.getFirst());
            selectPack(next);
            status("Sobres actualizados.", false);
        }));
    }

    private void renderPackList() {
        packList.getChildren().clear();
        for (ServerClient.BoosterPackSummary pack : packs) {
            Button button = new Button(pack.name() + "\n" + availableText(pack.quantity()));
            button.setMaxWidth(Double.MAX_VALUE);
            button.setWrapText(true);
            button.getStyleClass().add("booster-pack-selector");
            if (selectedPack != null && selectedPack.code().equals(pack.code())) {
                button.getStyleClass().add("selected");
            }
            button.setOnAction(ignored -> selectPack(pack));
            packList.getChildren().add(button);
        }
    }

    private void selectPack(ServerClient.BoosterPackSummary pack) {
        selectedPack = pack;
        selectionGeneration++;
        stopReveal();
        renderPackList();
        showDetail(true);
        packName.setText(pack.name());
        packDescription.setText(pack.description().isBlank() ? "Sobre del torneo Master V" : pack.description());
        updateQuantity(pack.quantity());
        contentsLabel.setText(pack.cardsPerPack() + (pack.cardsPerPack() == 1 ? " recompensa" : " recompensas"));
        guaranteeLabel.setText(pack.guaranteeLabel().isBlank() ? "Contenido configurado por el torneo"
                : pack.guaranteeLabel());
        revealContainer.getChildren().clear();
        loadArt(pack.artUrl(), selectionGeneration);
        loadDetail(pack, selectionGeneration);
    }

    private void loadDetail(ServerClient.BoosterPackSummary pack, long generation) {
        ServerSettings settings = ServerSettings.load();
        new ServerClient(settings.baseUrl()).boosterPack(settings.token(), pack.code())
                .whenComplete((detail, failure) -> Platform.runLater(() -> {
                    if (generation != selectionGeneration || selectedPack == null
                            || !selectedPack.code().equals(pack.code())) return;
                    if (failure != null) {
                        oddsContainer.getChildren().setAll(note("No se pudieron cargar las probabilidades."));
                        return;
                    }
                    renderOdds(detail.slots());
                }));
    }

    private void loadArt(String url, long generation) {
        packArt.setImage(null);
        if (url == null || url.isBlank()) return;
        ServerSettings settings = ServerSettings.load();
        new ServerClient(settings.baseUrl()).image(url, settings.token()).whenComplete((bytes, failure) -> {
            if (failure != null || bytes == null || bytes.length == 0) return;
            Image image = new Image(new ByteArrayInputStream(bytes));
            Platform.runLater(() -> {
                if (generation == selectionGeneration) packArt.setImage(image);
            });
        });
    }

    @FXML
    private void openPack() {
        if (loading || selectedPack == null) return;
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank()) {
            status("Inicia sesión para abrir sobres.", true);
            return;
        }
        if (selectedPack.quantity() < 1 && pendingIdempotencyKey == null) {
            status("No tienes unidades de este sobre.", true);
            return;
        }
        if (pendingIdempotencyKey == null) pendingIdempotencyKey = UUID.randomUUID();
        UUID key = pendingIdempotencyKey;
        loading = true;
        openButton.setDisable(true);
        refreshButton.setDisable(true);
        status("Abriendo sobre…", false);
        ServerClient client = new ServerClient(settings.baseUrl());
        client.openBoosterPack(settings.token(), selectedPack.code(), key)
                .whenComplete((opening, failure) -> Platform.runLater(() -> {
                    loading = false;
                    refreshButton.setDisable(false);
                    if (failure != null) {
                        openButton.setDisable(selectedPack == null || selectedPack.quantity() < 1);
                        status(message(failure) + " Puedes reintentar sin perder el resultado.", true);
                        return;
                    }
                    pendingIdempotencyKey = null;
                    updateQuantity(opening.remainingQuantity());
                    startReveal(opening.results());
                    mailboxRefresh.run();
                    status(opening.replayed() ? "Apertura recuperada." : "¡Sobre abierto! Recompensas al buzón.", false);
                }));
    }

    private void startReveal(List<ServerClient.PackOpeningResult> results) {
        stopReveal();
        revealContainer.getChildren().clear();
        pendingReveal = List.copyOf(results);
        if (pendingReveal.isEmpty()) {
            revealContainer.getChildren().add(note("El sobre no devolvió recompensas visibles."));
            return;
        }
        revealAllButton.setManaged(true);
        revealAllButton.setVisible(true);
        final int[] index = {0};
        revealTimeline = new Timeline(new KeyFrame(Duration.millis(430), ignored -> {
            if (index[0] >= pendingReveal.size()) {
                stopReveal();
                return;
            }
            addReveal(pendingReveal.get(index[0]++), true);
            if (index[0] >= pendingReveal.size()) stopReveal();
        }));
        revealTimeline.setCycleCount(pendingReveal.size());
        revealTimeline.play();
    }

    @FXML
    private void revealAll() {
        if (pendingReveal.isEmpty()) return;
        if (revealTimeline != null) revealTimeline.stop();
        revealContainer.getChildren().clear();
        pendingReveal.forEach(result -> addReveal(result, false));
        stopReveal();
    }

    private void addReveal(ServerClient.PackOpeningResult result, boolean animate) {
        Label position = new Label(String.format("%02d", result.position()));
        position.getStyleClass().add("booster-result-position");
        VBox copy = new VBox(2);
        Label name = new Label(result.name());
        name.getStyleClass().add("booster-result-name");
        Label rarity = new Label(rarityName(result.rarity()));
        rarity.getStyleClass().addAll("booster-result-rarity", "rarity-" + result.rarity().toLowerCase(Locale.ROOT));
        copy.getChildren().addAll(name, rarity);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, position, copy, spacer);
        row.getStyleClass().addAll("booster-result-card", "rarity-border-" + result.rarity().toLowerCase(Locale.ROOT));
        if (animate) {
            row.setOpacity(0);
            revealContainer.getChildren().add(row);
            FadeTransition fade = new FadeTransition(Duration.millis(260), row);
            fade.setToValue(1);
            fade.play();
        } else {
            revealContainer.getChildren().add(row);
        }
    }

    private void stopReveal() {
        if (revealTimeline != null) revealTimeline.stop();
        revealTimeline = null;
        revealAllButton.setManaged(false);
        revealAllButton.setVisible(false);
    }

    private void renderOdds(List<ServerClient.PackOddsSlot> slots) {
        oddsContainer.getChildren().clear();
        for (ServerClient.PackOddsSlot slot : slots) {
            String heading = slot.label().isBlank() ? "Posición " + slot.position()
                    : "Posición " + slot.position() + " · " + slot.label();
            Label title = new Label(heading);
            title.getStyleClass().add("booster-subheading");
            oddsContainer.getChildren().add(title);
            for (ServerClient.PackOddsEntry entry : slot.entries()) {
                Label name = new Label(entry.name());
                name.getStyleClass().add("booster-odds-name");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label chance = new Label(String.format(Locale.ROOT, "%.2f%%", entry.probability()));
                chance.getStyleClass().add("booster-odds-value");
                HBox row = new HBox(8, name, spacer, chance);
                row.getStyleClass().add("booster-odds-row");
                oddsContainer.getChildren().add(row);
            }
        }
    }

    @FXML
    private void loadHistory() {
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank() || loading) return;
        loading = true;
        status("Cargando historial…", false);
        new ServerClient(settings.baseUrl()).packOpenings(settings.token())
                .whenComplete((openings, failure) -> Platform.runLater(() -> {
                    loading = false;
                    if (failure != null) {
                        status(message(failure), true);
                        return;
                    }
                    renderHistory(openings);
                    status("Historial actualizado.", false);
                }));
    }

    private void renderHistory(List<ServerClient.PackOpening> openings) {
        historyContainer.getChildren().clear();
        if (openings.isEmpty()) {
            historyContainer.getChildren().add(note("Todavía no has abierto sobres."));
            return;
        }
        for (ServerClient.PackOpening opening : openings) {
            Label title = new Label(opening.packName());
            title.getStyleClass().add("booster-history-name");
            String when = opening.createdAt() == null ? "Fecha desconocida" : HISTORY_DATE.format(opening.createdAt());
            Label meta = new Label(when + " · " + opening.results().size() + " recompensas");
            meta.getStyleClass().add("booster-history-meta");
            VBox row = new VBox(2, title, meta);
            row.getStyleClass().add("booster-history-row");
            historyContainer.getChildren().add(row);
        }
    }

    private void updateQuantity(int quantity) {
        quantityLabel.setText(availableText(quantity));
        openButton.setDisable(loading || quantity < 1);
        if (selectedPack != null) {
            selectedPack = new ServerClient.BoosterPackSummary(
                    selectedPack.code(), selectedPack.name(), selectedPack.description(), selectedPack.artUrl(),
                    quantity, selectedPack.cardsPerPack(), selectedPack.guaranteeLabel(),
                    selectedPack.configurationVersion());
        }
    }

    private void showDetail(boolean show) {
        detailPanel.setManaged(show);
        detailPanel.setVisible(show);
        emptyState.setManaged(!show);
        emptyState.setVisible(!show);
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().remove("error");
        if (error) statusLabel.getStyleClass().add("error");
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("booster-note");
        return label;
    }

    private static String availableText(int quantity) {
        return quantity + (quantity == 1 ? " sobre disponible" : " sobres disponibles");
    }

    private static String rarityName(String rarity) {
        return switch (rarity) {
            case "RARE" -> "RARA";
            case "EPIC" -> "ÉPICA";
            case "MASTER" -> "MAESTRA";
            default -> "COMÚN";
        };
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof ServerClient.ServerException serverFailure) {
            return switch (serverFailure.statusCode()) {
                case 401, 403 -> "La sesión no permite realizar esta acción.";
                case 404 -> "El sobre ya no está disponible.";
                case 409 -> "No se pudo abrir el sobre con el estado actual.";
                default -> "El servidor no pudo procesar los sobres.";
            };
        }
        return "No se pudo conectar con el servidor.";
    }
}
