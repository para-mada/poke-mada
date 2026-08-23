module net.paramada.pokemada {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;

    opens net.paramada.pokemada to javafx.fxml;
    exports net.paramada.pokemada;
}
