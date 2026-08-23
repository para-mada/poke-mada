package net.paramada.pokemada;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

/** Presentation controller for PC boxes loaded from a game save, never from live RAM polling. */
public final class BoxesController {
    private static final int BOX_COUNT = 32;
    private static final int SLOTS_PER_BOX = 30;
    private static final int COLUMNS = 6;

    @FXML private GridPane boxGrid;
    @FXML private Label boxName;
    @FXML private Label occupancy;
    @FXML private Label dataStatus;

    private int selectedBox;

    @FXML
    private void initialize() {
        renderEmptyBox();
    }

    @FXML
    private void previousBox() {
        selectedBox = Math.floorMod(selectedBox - 1, BOX_COUNT);
        renderEmptyBox();
    }

    @FXML
    private void nextBox() {
        selectedBox = (selectedBox + 1) % BOX_COUNT;
        renderEmptyBox();
    }

    private void renderEmptyBox() {
        boxName.setText("CAJA " + (selectedBox + 1));
        occupancy.setText("0 / " + SLOTS_PER_BOX);
        dataStatus.setText("Selecciona o carga un guardado de Pokémon Sun / Moon");
        boxGrid.getChildren().clear();
        for (int slot = 0; slot < SLOTS_PER_BOX; slot++) {
            Label empty = new Label("—");
            empty.getStyleClass().add("box-empty-mark");
            StackPane cell = new StackPane(empty);
            cell.getStyleClass().addAll("box-slot", "box-slot-empty");
            cell.setAccessibleText("Slot " + (slot + 1) + ", vacío");
            boxGrid.add(cell, slot % COLUMNS, slot / COLUMNS);
        }
    }
}
