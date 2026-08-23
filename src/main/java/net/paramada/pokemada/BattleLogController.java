package net.paramada.pokemada;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.paramada.pokemada.battlelog.BattleLogEvent;
import net.paramada.pokemada.battlelog.BattleLogManager;
import net.paramada.pokemada.battlelog.BattleLogSession;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class BattleLogController {
    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter
            .ofPattern("dd MMM uuuu · HH:mm", new Locale("es", "MX"));
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML private StackPane root;
    @FXML private Label title;
    @FXML private Label meta;
    @FXML private VBox events;
    @FXML private ScrollPane eventScroll;
    private boolean showingActive;

    public void show(BattleLogSession session) {
        showingActive = false;
        render(session.startedAt(), session.endedAt(), session.events(), false, true);
    }

    public void showActive(Instant startedAt, List<BattleLogEvent> activeEvents) {
        showingActive = true;
        render(startedAt, Instant.now(), activeEvents, true, true);
    }

    public void updateActive(Instant startedAt, List<BattleLogEvent> activeEvents) {
        if (showingActive && root.isVisible()) {
            render(startedAt, Instant.now(), activeEvents, true, false);
        }
    }

    public void closeActive() {
        if (showingActive) close();
    }

    private void render(Instant startedAt, Instant endedAt, List<BattleLogEvent> entries,
                        boolean active, boolean reveal) {
        ZoneId zone = ZoneId.systemDefault();
        title.setText(active ? "Registro en vivo" : "Combate · " + TITLE_TIME.format(startedAt.atZone(zone)));
        long seconds = Math.max(0, endedAt.getEpochSecond() - startedAt.getEpochSecond());
        meta.setText((active ? "EN CURSO" : "FINALIZADO") + "  ·  " + entries.size()
                + (entries.size() == 1 ? " evento" : " eventos") + "  ·  " + formatDuration(seconds));
        events.getChildren().clear();
        if (entries.isEmpty()) {
            Label empty = new Label("Aún no se han detectado mensajes de combate.");
            empty.getStyleClass().add("battle-log-modal-empty");
            events.getChildren().add(empty);
        } else {
            for (BattleLogEvent entry : entries) events.getChildren().add(eventRow(entry, zone));
        }
        if (reveal) {
            root.setManaged(true);
            root.setVisible(true);
            Platform.runLater(root::requestFocus);
        }
        if (active) Platform.runLater(() -> eventScroll.setVvalue(1));
    }

    private static HBox eventRow(BattleLogEvent event, ZoneId zone) {
        Label time = new Label(EVENT_TIME.format(event.timestamp().atZone(zone)));
        time.getStyleClass().add("battle-log-modal-time");
        Label message = new Label(event.message());
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(message, javafx.scene.layout.Priority.ALWAYS);
        message.getStyleClass().add("battle-log-modal-message");
        HBox row = new HBox(14, time, message);
        row.getStyleClass().add(BattleLogManager.isTurnMarker(event.message())
                ? "battle-log-modal-turn" : "battle-log-modal-event");
        return row;
    }

    private static String formatDuration(long seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    @FXML private void close() {
        showingActive = false;
        root.setManaged(false);
        root.setVisible(false);
    }

    @FXML private void closeFromBackdrop(MouseEvent event) {
        close();
        event.consume();
    }

    @FXML private void consumeCardClick(MouseEvent event) {
        event.consume();
    }

    @FXML private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            close();
            event.consume();
        }
    }
}
