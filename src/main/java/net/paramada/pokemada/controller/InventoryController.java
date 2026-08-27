package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;
import net.paramada.pokemada.game.PokemonGameConfig;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.game.assets.VirtualItemSpriteCache;
import net.paramada.pokemada.game.save.SmSavePartyReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

/** Server-authoritative virtual backpack UI and command coordinator. */
public final class InventoryController {
    @FXML private Button refreshButton;
    @FXML private Button useButton;
    @FXML private Label statusLabel;
    @FXML private Label pendingLabel;
    @FXML private Label pocketTitle;
    @FXML private VBox teamContainer;
    @FXML private VBox itemsContainer;
    @FXML private ToggleButton mintsPocket;
    @FXML private ToggleButton supplementsPocket;
    @FXML private ToggleButton tmsPocket;
    @FXML private ToggleButton medicinePocket;
    @FXML private ToggleButton eventPocket;

    private final Map<Integer, CommandTarget> targets = new HashMap<>();
    private final PokemonSpriteCache sprites = new PokemonSpriteCache();
    private final VirtualItemSpriteCache itemSprites = new VirtualItemSpriteCache();
    private final Map<String, ToggleButton> pocketButtons = new java.util.LinkedHashMap<>();
    private List<ServerClient.VirtualItemStack> inventory = List.of();
    private String selectedPocket = "supplements";
    private CommandTarget selectedTarget;
    private ServerClient.VirtualItemStack selectedItem;
    private BiFunction<ServerClient.ClientCommand, CommandTarget, CompletableFuture<String>> commandExecutor =
            (command, target) -> CompletableFuture.failedFuture(
                    new IllegalStateException("El ejecutor RAM no está configurado"));
    private boolean loading;

    @FXML
    private void initialize() {
        pocketButtons.put("mints", mintsPocket);
        pocketButtons.put("supplements", supplementsPocket);
        pocketButtons.put("tms", tmsPocket);
        pocketButtons.put("medicine", medicinePocket);
        pocketButtons.put("event", eventPocket);
        pocketButtons.forEach((pocket, button) -> button.setTooltip(new Tooltip(pocketName(pocket))));
    }

    public void configure(BiFunction<ServerClient.ClientCommand, CommandTarget,
            CompletableFuture<String>> executor) {
        commandExecutor = executor == null ? commandExecutor : executor;
    }

    @FXML
    public void refresh() {
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank()) {
            status("Inicia sesión para abrir la mochila", true);
            return;
        }
        if (loading) return;
        loading = true;
        refreshButton.setDisable(true);
        status("Actualizando mochila…", false);
        ServerClient client = new ServerClient(settings.baseUrl());
        CompletableFuture<List<SmSavePartyReader.PartyPokemon>> team = CompletableFuture.supplyAsync(() -> {
            try {
                return new SmSavePartyReader().read(PokemonGameConfig.pokemonMoon().save().file());
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
        team.thenCombine(client.virtualInventory(settings.token()), Result::new)
                .thenCombine(client.pendingVirtualItemCommands(settings.token()), FullResult::new)
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    loading = false;
                    refreshButton.setDisable(false);
                    if (failure != null) {
                        status(message(failure), true);
                        return;
                    }
                    renderTeam(result.inventory().team());
                    renderItems(result.inventory().items());
                    int pending = result.pending().size();
                    pendingLabel.setText(pending == 0 ? "Sin operaciones pendientes"
                            : pending + (pending == 1 ? " operación pendiente de revisión"
                            : " operaciones pendientes de revisión"));
                    status("Mochila actualizada", false);
                }));
    }

    private void renderTeam(List<SmSavePartyReader.PartyPokemon> team) {
        targets.clear();
        teamContainer.getChildren().clear();
        selectedTarget = null;
        for (SmSavePartyReader.PartyPokemon pokemon : team) {
            CommandTarget target = new CommandTarget(pokemon.slot(), pokemon);
            targets.put(pokemon.slot(), target);
            HBox row = teamRow(target);
            teamContainer.getChildren().add(row);
            if (selectedTarget == null) selectTarget(target, row);
        }
    }

    private HBox teamRow(CommandTarget target) {
        ImageView sprite = new ImageView();
        sprite.setFitWidth(42);
        sprite.setFitHeight(42);
        sprite.setPreserveRatio(true);
        sprite.setSmooth(false);
        VBox copy = new VBox(1);
        Label name = new Label(target.pokemon().name());
        name.getStyleClass().add("inventory-team-name");
        Label level = new Label("Nv. " + target.pokemon().level() + "   PS "
                + target.pokemon().currentHp() + "/" + target.pokemon().maxHp());
        level.getStyleClass().add("inventory-team-meta");
        copy.getChildren().addAll(name, level);
        HBox row = new HBox(8, sprite, copy);
        row.getStyleClass().add("inventory-team-row");
        row.setOnMouseClicked(ignored -> selectTarget(target, row));
        sprites.load(target.pokemon().species()).thenAccept(image ->
                Platform.runLater(() -> sprite.setImage(image.orElse(null))));
        return row;
    }

    private void selectTarget(CommandTarget target, HBox row) {
        selectedTarget = target;
        teamContainer.getChildren().forEach(node -> node.getStyleClass().remove("selected"));
        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
        updateUseButton();
    }

    private void renderItems(List<ServerClient.VirtualItemStack> items) {
        inventory = List.copyOf(items);
        pocketButtons.forEach((pocket, button) -> button.setDisable(
                inventory.stream().noneMatch(item -> pocket.equals(item.pocket()))));
        if (inventory.stream().noneMatch(item -> selectedPocket.equals(item.pocket()))) {
            selectedPocket = pocketButtons.keySet().stream()
                    .filter(pocket -> inventory.stream().anyMatch(item -> pocket.equals(item.pocket())))
                    .findFirst().orElse("supplements");
        }
        ToggleButton selected = pocketButtons.get(selectedPocket);
        if (selected != null && !selected.isDisable()) selected.setSelected(true);
        renderSelectedPocket();
    }

    @FXML
    private void selectPocket(javafx.event.ActionEvent event) {
        ToggleButton selected = (ToggleButton) event.getSource();
        if (!selected.isSelected()) {
            selected.setSelected(true);
            return;
        }
        selectedPocket = String.valueOf(selected.getUserData());
        renderSelectedPocket();
    }

    private void renderSelectedPocket() {
        itemsContainer.getChildren().clear();
        pocketTitle.setText(pocketName(selectedPocket).toUpperCase());
        List<ServerClient.VirtualItemStack> items = inventory.stream()
                .filter(item -> selectedPocket.equals(item.pocket())).toList();
        if (items.isEmpty()) {
            selectedItem = null;
            updateUseButton();
            Label empty = new Label(inventory.isEmpty() ? "Tu mochila todavía está vacía"
                    : "No tienes objetos en esta bolsa");
            empty.getStyleClass().add("inventory-empty");
            itemsContainer.getChildren().add(empty);
            return;
        }
        selectedItem = items.getFirst();
        for (ServerClient.VirtualItemStack item : items) {
            HBox row = itemRow(item);
            itemsContainer.getChildren().add(row);
            if (item == selectedItem) selectItem(item, row);
        }
    }

    private static String pocketName(String pocket) {
        return switch (pocket) {
            case "mints" -> "Mentas";
            case "supplements" -> "Suplementos";
            case "tms" -> "MTs";
            case "medicine" -> "Botiquín";
            case "event" -> "Evento";
            default -> "Otros";
        };
    }

    private HBox itemRow(ServerClient.VirtualItemStack item) {
        ImageView sprite = new ImageView();
        sprite.setFitWidth(28);
        sprite.setFitHeight(28);
        sprite.setPreserveRatio(true);
        sprite.setSmooth(false);
        sprite.getStyleClass().add("inventory-item-sprite");
        loadItemSprite(item, sprite);
        VBox copy = new VBox(4);
        Label name = new Label(item.name());
        name.getStyleClass().add("inventory-item-name");
        Label description = new Label(item.description());
        description.setWrapText(true);
        description.getStyleClass().add("inventory-item-description");
        copy.getChildren().addAll(name, description);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label quantity = new Label("× " + item.availableQuantity());
        quantity.getStyleClass().add("inventory-list-quantity");
        HBox row = new HBox(10, sprite, copy, spacer, quantity);
        row.getStyleClass().add("inventory-item-row");
        row.setOnMouseClicked(ignored -> selectItem(item, row));
        return row;
    }

    private void loadItemSprite(ServerClient.VirtualItemStack item, ImageView target) {
        if (item.spriteUrl() == null || item.spriteUrl().isBlank()) return;
        ServerSettings settings = ServerSettings.load();
        ServerClient client = new ServerClient(settings.baseUrl());
        itemSprites.load(item.spriteUrl(), settings.token(), client)
                .thenAccept(image -> Platform.runLater(() -> target.setImage(image.orElse(null))));
    }

    private void selectItem(ServerClient.VirtualItemStack item, HBox row) {
        selectedItem = item;
        itemsContainer.getChildren().forEach(node -> node.getStyleClass().remove("selected"));
        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
        updateUseButton();
    }

    private void updateUseButton() {
        useButton.setDisable(selectedItem == null || !selectedItem.directlyUsable()
                || selectedItem.availableQuantity() < 1
                || (selectedItem.requiresTargetPokemon() && selectedTarget == null));
    }

    @FXML
    private void useSelected() {
        if (selectedItem != null) use(selectedItem, useButton);
    }

    private void use(ServerClient.VirtualItemStack item, Button button) {
        if (item.requiresTargetPokemon() && selectedTarget == null) {
            status("Selecciona un Pokémon", true);
            return;
        }
        ServerSettings settings = ServerSettings.load();
        ServerClient client = new ServerClient(settings.baseUrl());
        button.setDisable(true);
        status("Preparando " + item.name() + "…", false);
        int partySlot = selectedTarget == null ? -1 : selectedTarget.slot();
        int species = selectedTarget == null ? 0 : selectedTarget.pokemon().species();
        client.useVirtualItem(settings.token(), item.code(), partySlot, species, 1, UUID.randomUUID())
                .thenCompose(operation -> executeCommands(client, settings.token(), operation.commands()))
                .whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    button.setDisable(false);
                    if (failure != null) {
                        status(message(failure), true);
                    } else {
                        status(item.name() + " aplicada correctamente", false);
                        refresh();
                    }
                }));
    }

    private CompletableFuture<Void> executeCommands(ServerClient client, String token,
                                                     List<ServerClient.ClientCommand> commands) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ServerClient.ClientCommand command : commands) {
            chain = chain.thenCompose(ignored -> executeCommand(client, token, command));
        }
        return chain;
    }

    private CompletableFuture<Void> executeCommand(ServerClient client, String token,
                                                   ServerClient.ClientCommand command) {
        int partySlot = command.payload().get("target_party_slot") instanceof Number number
                ? number.intValue() : -1;
        CommandTarget target = targets.get(partySlot);
        if (target == null) return failAndAcknowledge(client, token, command,
                "El Pokémon destino ya no está en el save local");
        return commandExecutor.apply(command, target)
                .thenCompose(detail -> client.acknowledgeVirtualItemCommand(
                        token, command.id(), true, detail).<Void>thenApply(ignored -> null))
                .exceptionallyCompose(failure -> failAndAcknowledge(
                        client, token, command, rootMessage(failure)));
    }

    private CompletableFuture<Void> failAndAcknowledge(ServerClient client, String token,
                                                       ServerClient.ClientCommand command, String detail) {
        return client.acknowledgeVirtualItemCommand(token, command.id(), false, detail)
                .handle((ignored, ackFailure) -> {
                    throw new CompletionException(new IllegalStateException(detail));
                });
    }

    private void status(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().remove("error");
        if (error) statusLabel.getStyleClass().add("error");
    }

    private static String message(Throwable failure) {
        Throwable root = root(failure);
        if (root instanceof ServerClient.ServerException server) {
            return switch (server.statusCode()) {
                case 400 -> "La solicitud no cumple las reglas del objeto";
                case 401, 403 -> "La sesión ya no es válida";
                case 404 -> "El objeto o Pokémon ya no existe";
                case 409 -> "El objeto no puede usarse en este momento";
                default -> "El servidor rechazó la operación";
            };
        }
        return root.getMessage() == null ? "No se pudo usar el objeto" : root.getMessage();
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = root(failure);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static Throwable root(Throwable failure) {
        Throwable result = failure;
        while (result instanceof CompletionException && result.getCause() != null) result = result.getCause();
        return result;
    }

    public record CommandTarget(int slot, SmSavePartyReader.PartyPokemon pokemon) { }

    private record Result(List<SmSavePartyReader.PartyPokemon> team,
                          List<ServerClient.VirtualItemStack> items) { }
    private record FullResult(Result inventory, List<ServerClient.ClientCommand> pending) { }
}
