package net.paramada.pokemada;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import net.paramada.pokemada.controller.MainController;

import java.io.IOException;
import java.util.Objects;

public final class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Font.loadFont(Objects.requireNonNull(
                MainApplication.class.getResourceAsStream("fonts/PKMN-RBYGSC.ttf")), 16);
        FXMLLoader fxmlLoader = new FXMLLoader(
                Objects.requireNonNull(MainApplication.class.getResource("main-view.fxml")));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 800);
        MainController controller = fxmlLoader.getController();
        scene.getStylesheets().add(Objects.requireNonNull(
                MainApplication.class.getResource("styles/main.css")).toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.R) {
                controller.refreshProfile();
                event.consume();
            }
        });

        stage.setTitle("Master V Tournament");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                MainApplication.class.getResourceAsStream("assets/master-v-emblem.png"))));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.setOnCloseRequest(ignored -> controller.shutdown());
        stage.show();
    }
}
