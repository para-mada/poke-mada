module net.paramada.pokemada {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.desktop;
    requires java.net.http;
    requires jdk.crypto.ec;

    opens net.paramada.pokemada to javafx.fxml;
    exports net.paramada.pokemada;
}
