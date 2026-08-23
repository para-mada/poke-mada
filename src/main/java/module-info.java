module net.paramada.pokemada {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;

    opens net.paramada.pokemada to javafx.fxml;
    exports net.paramada.pokemada;
}