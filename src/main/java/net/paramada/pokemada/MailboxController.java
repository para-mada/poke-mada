package net.paramada.pokemada;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;
import net.paramada.pokemada.game.assets.ProfileImageCache;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Account connection and reward mailbox backed by the private PokeMada server. */
public final class MailboxController {
    private static final System.Logger LOGGER = System.getLogger(MailboxController.class.getName());

    @FXML private VBox loginSection;
    @FXML private VBox accountSection;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Button loginButton;
    @FXML private Button refreshButton;
    @FXML private Label accountLabel;
    @FXML private Label statusLabel;
    @FXML private VBox rewardsContainer;

    private final AtomicBoolean busy = new AtomicBoolean();
    private ServerSettings settings;
    private ServerClient client;
    private Consumer<Boolean> sessionListener = ignored -> { };
    private Consumer<String> trainerListener = ignored -> { };
    private Consumer<Image> profileImageListener = ignored -> { };
    private final ProfileImageCache profileImageCache = new ProfileImageCache();
    private boolean authenticated;
    private String trainerName = "Entrenador";

    @FXML
    private void initialize() {
        ServerSettings storedSettings = ServerSettings.load();
        settings = storedSettings.baseUrl().equals(ServerSettings.DEFAULT_BASE_URL)
                ? storedSettings
                : new ServerSettings(ServerSettings.DEFAULT_BASE_URL, storedSettings.username(), "");
        if (settings != storedSettings) saveSettings();
        username.setText(settings.username());
        client = new ServerClient(settings.baseUrl());
        showAuthenticated(false);
        if (!settings.token().isBlank()) {
            loginLog("Validando sesión guardada");
            status("Validando sesión…", false);
            refresh();
        }
    }

    @FXML
    private void login() {
        if (!startOperation()) return;
        String user = username.getText() == null ? "" : username.getText().trim();
        String secret = password.getText() == null ? "" : password.getText();
        if (user.isBlank() || secret.isBlank()) {
            finishOperation();
            status("Escribe tu usuario y contraseña.", true);
            return;
        }
        loginLog("Intento de inicio de sesión para usuario: " + user);
        try {
            settings = new ServerSettings(ServerSettings.DEFAULT_BASE_URL, user, "");
            client = new ServerClient(settings.baseUrl());
        } catch (IllegalArgumentException invalidUrl) {
            finishOperation();
            status("No fue posible iniciar sesión.", true);
            return;
        }
        status("Conectando…", false);
        client.login(user, secret).thenCompose(token -> {
            settings = new ServerSettings(settings.baseUrl(), user, token);
            saveSettings();
            return client.profile(token);
        }).thenCompose(profile -> {
            loginLog("Perfil aceptado para usuario: " + user);
            Platform.runLater(() -> {
                password.clear();
                updateProfile(profile, false);
                showAuthenticated(true);
            });
            return client.rewards(settings.token());
        }).whenComplete((rewards, failure) -> Platform.runLater(() -> {
            finishOperation();
            if (failure != null) {
                loginFailure("Flujo de inicio de sesión fallido", failure);
                clearSessionIfUnauthorized(failure);
                status(message(failure), true);
                return;
            }
            loginLog("Inicio de sesión completado para usuario: " + user);
            renderRewards(rewards);
            status("Buzón actualizado.", false);
        }));
    }

    @FXML
    public void refresh() {
        refresh(false);
    }

    public void refreshProfile() {
        refresh(true);
    }

    private void refresh(boolean forceProfileImage) {
        if (settings == null || settings.token().isBlank() || !startOperation()) return;
        status("Actualizando buzón…", false);
        client.profile(settings.token()).thenCombine(client.rewards(settings.token()), Result::new)
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    finishOperation();
                    if (failure != null) {
                        loginFailure("Validación de sesión fallida", failure);
                        clearSessionIfUnauthorized(failure);
                        status(message(failure), true);
                        return;
                    }
                    updateProfile(result.profile(), forceProfileImage);
                    renderRewards(result.rewards());
                    showAuthenticated(true);
                    loginLog("Sesión validada correctamente");
                    status("Buzón actualizado.", false);
                }));
    }

    public void setSessionListener(Consumer<Boolean> listener) {
        sessionListener = listener == null ? ignored -> { } : listener;
        sessionListener.accept(authenticated);
    }

    public void setTrainerListener(Consumer<String> listener) {
        trainerListener = listener == null ? ignored -> { } : listener;
        trainerListener.accept(trainerName);
    }

    public void setProfileImageListener(Consumer<Image> listener) {
        profileImageListener = listener == null ? ignored -> { } : listener;
    }

    public void logoutSession() {
        settings = settings.withoutToken();
        saveSettings();
        rewardsContainer.getChildren().clear();
        password.clear();
        updateTrainerName("Entrenador");
        profileImageListener.accept(null);
        showAuthenticated(false);
        status("Sesión cerrada.", false);
    }

    private void claim(ServerClient.RewardBundle reward, Button button) {
        if (!startOperation()) return;
        button.setDisable(true);
        status("Reclamando «" + reward.name() + "»…", false);
        client.claim(settings.token(), reward.id()).thenCompose(ignored -> client.rewards(settings.token()))
                .whenComplete((rewards, failure) -> Platform.runLater(() -> {
                    finishOperation();
                    if (failure != null) {
                        button.setDisable(false);
                        clearSessionIfUnauthorized(failure);
                        status(message(failure), true);
                        return;
                    }
                    renderRewards(rewards);
                    status("Paquete reclamado.", false);
                }));
    }

    private void renderRewards(List<ServerClient.RewardBundle> rewards) {
        rewardsContainer.getChildren().clear();
        if (rewards.isEmpty()) {
            VBox empty = new VBox(10);
            empty.getStyleClass().add("mail-empty-state");
            Label icon = new Label("✉");
            icon.getStyleClass().add("mail-empty-icon");
            Label title = new Label("Aún no tienes recompensas en tu buzón");
            title.getStyleClass().add("legacy-dark-heading");
            Label note = new Label("Los nuevos paquetes del torneo aparecerán aquí.");
            note.getStyleClass().add("legacy-placeholder-note");
            empty.getChildren().addAll(icon, title, note);
            rewardsContainer.getChildren().add(empty);
            return;
        }
        for (ServerClient.RewardBundle reward : rewards) rewardsContainer.getChildren().add(rewardRow(reward));
    }

    private HBox rewardRow(ServerClient.RewardBundle reward) {
        Label type = new Label(bundleType(reward.type()));
        type.getStyleClass().add("mail-type-chip");
        VBox copy = new VBox(4);
        Label title = new Label(reward.name());
        title.getStyleClass().add("mail-reward-title");
        Label details = new Label(reward.description().isBlank() ? rewardSummary(reward) : reward.description());
        details.setWrapText(true);
        details.getStyleClass().add("mail-reward-details");
        copy.getChildren().addAll(title, details);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label sender = new Label(reward.sender());
        sender.getStyleClass().add("mail-reward-sender");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button claim = new Button("RECLAMAR");
        claim.getStyleClass().add("legacy-primary-button");
        boolean requiresGameWrite = reward.rewards().stream().anyMatch(item -> item.type() == 0 || item.type() == 3);
        if (requiresGameWrite) {
            claim.setText("REQUIERE JUEGO");
            claim.setDisable(true);
            claim.setTooltip(new javafx.scene.control.Tooltip(
                    "Este paquete contiene objetos o Pokémon y aún no puede aplicarse de forma segura."));
        } else {
            claim.setOnAction(ignored -> claim(reward, claim));
        }
        HBox row = new HBox(16, type, copy, sender, spacer, claim);
        row.getStyleClass().add("mail-reward-row");
        return row;
    }

    private static String rewardSummary(ServerClient.RewardBundle bundle) {
        if (bundle.rewards().isEmpty()) return "Paquete de recompensa";
        int total = bundle.rewards().stream().mapToInt(item -> Math.max(1, item.quantity())).sum();
        return total + (total == 1 ? " recompensa" : " recompensas");
    }

    private static String bundleType(int type) {
        return switch (type) {
            case 1 -> "SOBRE";
            case 2 -> "COMODÍN";
            default -> "PREMIO";
        };
    }

    private void showAuthenticated(boolean authenticated) {
        boolean changed = this.authenticated != authenticated;
        this.authenticated = authenticated;
        loginSection.setManaged(!authenticated);
        loginSection.setVisible(!authenticated);
        accountSection.setManaged(authenticated);
        accountSection.setVisible(authenticated);
        if (changed) sessionListener.accept(authenticated);
    }

    private void updateTrainerName(String name) {
        trainerName = name == null || name.isBlank() ? "Entrenador" : name;
        accountLabel.setText(trainerName);
        trainerListener.accept(trainerName);
    }

    private void updateProfile(ServerClient.Profile profile, boolean forceImageRefresh) {
        updateTrainerName(profile.name());
        if (profile.pictureUrl() == null) {
            profileImageListener.accept(null);
            return;
        }
        var imageLoad = forceImageRefresh
                ? profileImageCache.refresh(profile.pictureUrl(), settings.token(), client)
                : profileImageCache.load(profile.pictureUrl(), settings.token(), client);
        imageLoad
                .thenAccept(image -> Platform.runLater(() -> profileImageListener.accept(image.orElse(null))));
    }

    private boolean startOperation() {
        if (!busy.compareAndSet(false, true)) return false;
        loginButton.setDisable(true);
        refreshButton.setDisable(true);
        return true;
    }

    private void finishOperation() {
        busy.set(false);
        loginButton.setDisable(false);
        refreshButton.setDisable(false);
    }

    private void clearSessionIfUnauthorized(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ServerClient.ServerException serverFailure
                && (serverFailure.statusCode() == 401 || serverFailure.statusCode() == 403)) {
            settings = settings.withoutToken();
            saveSettings();
            updateTrainerName("Entrenador");
            profileImageListener.accept(null);
            showAuthenticated(false);
        }
    }

    private void saveSettings() {
        try {
            settings.save();
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not persist server settings", exception);
        }
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().remove("error");
        if (error) statusLabel.getStyleClass().add("error");
    }

    private static String message(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ServerClient.ServerException serverFailure) {
            return switch (serverFailure.statusCode()) {
                case 400, 401, 403 -> "Usuario, contraseña o sesión no válidos.";
                case 404 -> "El recurso solicitado no está disponible.";
                default -> "Ocurrió un problema. Inténtalo de nuevo.";
            };
        }
        return "No fue posible iniciar sesión. Revisa tu conexión.";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while (result instanceof CompletionException && result.getCause() != null) result = result.getCause();
        return result;
    }

    private static void loginLog(String message) {
        System.out.println("[LOGIN] " + message);
    }

    private static void loginFailure(String message, Throwable failure) {
        Throwable cause = unwrap(failure);
        String detail = cause instanceof ServerClient.ServerException serverFailure
                ? "HTTP " + serverFailure.statusCode()
                : cause.getClass().getSimpleName();
        System.out.println("[LOGIN] " + message + " (" + detail + ")");
    }

    private record Result(ServerClient.Profile profile, List<ServerClient.RewardBundle> rewards) { }
}
