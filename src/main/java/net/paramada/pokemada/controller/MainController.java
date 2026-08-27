package net.paramada.pokemada.controller;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.scene.shape.Circle;
import javafx.geometry.Insets;
import javafx.util.Duration;
import net.paramada.pokemada.battlelog.BattleLogEvent;
import net.paramada.pokemada.battlelog.BattleLogManager;
import net.paramada.pokemada.battlelog.BattleLogSession;
import net.paramada.pokemada.battlelog.BattleLogStore;
import net.paramada.pokemada.features.vial.PokeVial;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.assets.GameDataCatalogSync;
import net.paramada.pokemada.game.PokemonGameConfig;
import net.paramada.pokemada.game.save.SaveFileWatcher;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import net.paramada.pokemada.game.official.sm.SmBattleTextReader;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import net.paramada.pokemada.game.official.sm.SmPartyEditor;
import net.paramada.pokemada.game.official.sm.SmPartyHealer;
import net.paramada.pokemada.game.save.SmSaveEditor;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;
import net.paramada.pokemada.platform.WindowsNotificationService;
import net.paramada.pokemada.server.NotificationConnection;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainController {
    private static final System.Logger LOGGER = System.getLogger(MainController.class.getName());
    private static final PokemonGameConfig GAME_CONFIG = PokemonGameConfig.pokemonMoon();
    private static final String[] STAT_TIER_CLASSES = {
            "stat-tier-very-low", "stat-tier-low", "stat-tier-medium", "stat-tier-high",
            "stat-tier-very-high", "stat-tier-exceptional", "stat-tier-extreme"
    };

    @FXML private VBox sidebar;
    @FXML private VBox sidebarFooter;
    @FXML private VBox navigation;
    @FXML private ToggleGroup navigationGroup;
    @FXML private ToggleButton homeNavigationButton;
    @FXML private ImageView sidebarWordmark;
    @FXML private ImageView sidebarAppIcon;
    @FXML private Button sidebarCollapseButton;
    @FXML private VBox emulatorConnectionDetails;
    @FXML private Button emulatorRetryButton;
    @FXML private VBox trainerDetails;
    @FXML private Button logoutButton;

    @FXML
    private ScrollPane homeView;
    @FXML
    private ScrollPane liveView;
    @FXML
    private ScrollPane combatDetailView;
    @FXML private ScrollPane doubleCombatDetailView;
    @FXML private ScrollPane sosCombatDetailView;
    @FXML private ScrollPane battleRoyaleCombatDetailView;
    @FXML private StackPane allyCombatDetailView;
    @FXML
    private SingleCombatPanelController combatDetailViewController;
    @FXML private DoubleCombatPanelController doubleCombatDetailViewController;
    @FXML private SOSCombatPanelController sosCombatDetailViewController;
    @FXML private BattleRoyaleCombatPanelController battleRoyaleCombatDetailViewController;
    @FXML private AllyCombatPanelController allyCombatDetailViewController;
    @FXML
    private ScrollPane boxesView;
    @FXML private BoxesController boxesViewController;
    @FXML private ScrollPane wildcardsView;
    @FXML private ScrollPane inventoryView;
    @FXML private InventoryController inventoryViewController;
    @FXML private ScrollPane mailboxView;
    @FXML private MailboxController mailboxViewController;
    @FXML private ScrollPane boosterPacksView;
    @FXML private BoosterPacksController boosterPacksViewController;
    @FXML private HBox emulatorConnectionRow;
    @FXML private Label emulatorConnectionStatus;
    @FXML private ImageView emulatorConnectionIcon;
    @FXML private ImageView profilePicture;
    @FXML private Label profileTrainerName;
    @FXML private VBox notificationEntries;
    @FXML private VBox notificationEmptyState;
    @FXML private ScrollPane notificationScroll;
    @FXML private PokemonDetailController pokemonDetailController;
    @FXML private BattleLogController battleLogController;
    @FXML
    private Label battleModeLabel;
    @FXML
    private Label battleSummaryMessage;
    @FXML private VBox battleSummaryCard;
    @FXML private Label battleSummaryLink;
    @FXML
    private Label playerActiveLabel;
    @FXML
    private Label enemyActiveLabel;
    @FXML
    private Label playerHpLabel;
    @FXML
    private Label enemyHpLabel;
    @FXML
    private Label playerActiveMetaLabel;
    @FXML
    private Label enemyActiveMetaLabel;
    @FXML
    private Label playerBattleDetailsLabel;
    @FXML
    private Label enemyBattleDetailsLabel;
    @FXML
    private ImageView playerActiveSprite;
    @FXML
    private ImageView enemyActiveSprite;
    @FXML
    private Label playerMove0Label;
    @FXML
    private Label playerMove1Label;
    @FXML
    private Label playerMove2Label;
    @FXML
    private Label playerMove3Label;
    @FXML
    private ProgressBar playerHpBar;
    @FXML
    private ProgressBar enemyHpBar;
    @FXML
    private Label combatLogLabel;
    @FXML private Label combatLogModeChip;
    @FXML private VBox combatLogEntries;
    @FXML
    private Label teamSummaryLabel;
    @FXML private Button pokeVialButton;
    @FXML private Label pokeVialStatus;
    @FXML private HBox partySlot0Card;
    @FXML private HBox partySlot1Card;
    @FXML private HBox partySlot2Card;
    @FXML private HBox partySlot3Card;
    @FXML private HBox partySlot4Card;
    @FXML private HBox partySlot5Card;
    @FXML
    private Label partySlot0Name;
    @FXML
    private Label partySlot1Name;
    @FXML
    private Label partySlot2Name;
    @FXML
    private Label partySlot3Name;
    @FXML
    private Label partySlot4Name;
    @FXML
    private Label partySlot5Name;
    @FXML
    private Label partySlot0Details;
    @FXML
    private Label partySlot1Details;
    @FXML
    private Label partySlot2Details;
    @FXML
    private Label partySlot3Details;
    @FXML
    private Label partySlot4Details;
    @FXML
    private Label partySlot5Details;
    @FXML
    private Label partySlot0Hp;
    @FXML
    private Label partySlot1Hp;
    @FXML
    private Label partySlot2Hp;
    @FXML
    private Label partySlot3Hp;
    @FXML
    private Label partySlot4Hp;
    @FXML
    private Label partySlot5Hp;
    @FXML
    private ImageView partySlot0Sprite;
    @FXML
    private ImageView partySlot1Sprite;
    @FXML
    private ImageView partySlot2Sprite;
    @FXML
    private ImageView partySlot3Sprite;
    @FXML
    private ImageView partySlot4Sprite;
    @FXML
    private ImageView partySlot5Sprite;

    private final AtomicBoolean refreshingLiveData = new AtomicBoolean();
    private final AtomicBoolean usingPokeVial = new AtomicBoolean();
    private final AtomicBoolean pollingBattleText = new AtomicBoolean();
    private final Object liveClientLock = new Object();
    private final BattleLogManager battleLogManager = new BattleLogManager(new BattleLogStore());
    private final PokeVial pokeVial = new PokeVial();
    private final PokemonSpriteCache spriteCache = new PokemonSpriteCache();
    private final SaveFileWatcher saveFileWatcher = new SaveFileWatcher(GAME_CONFIG);
    private final WindowsNotificationService windowsNotifications = new WindowsNotificationService();
    private final Image emulatorConnectedIcon = bundledImage("madalime.png");
    private final Image emulatorDisconnectedIcon = bundledImage("madalime_off.png");
    private final Image defaultProfilePicture = bundledImage("profile.png");
    private AudioClip pokeVialSound;
    private AudioClip notificationSound;
    private NotificationConnection notificationConnection;
    private long notificationGeneration;
    private final Set<Long> displayedNotificationIds = new HashSet<>();
    private final Map<String, Long> recentRealtimeNotifications = new LinkedHashMap<>();
    private Timeline liveRefreshTimeline;
    private Timeline battleTextTimeline;
    private CitraUdpClient liveClient;
    private static final long GAME_PROBE_INTERVAL_NANOS = 3_000_000_000L;
    private static final long GAME_WARMUP_NANOS = 2_000_000_000L;
    private volatile boolean liveSessionReady;
    private volatile boolean emulatorConnected;
    private volatile long nextGameProbeNanos;
    private volatile long gameWarmupUntilNanos;
    private volatile boolean battleActive;
    private boolean authenticated;
    private volatile boolean singleBattleActive;
    private volatile boolean sosBattleActive;
    private volatile boolean battleRoyaleActive;
    private volatile boolean allyBattleActive;
    private volatile String latestBattleText = "";
    private volatile String primaryTurnPokemon = "";
    private Label[] partyNames;
    private Label[] partyDetails;
    private Label[] partyHp;
    private HBox[] partyCards;
    private ImageView[] partySprites;
    private final int[] partySpriteSpecies = new int[6];
    private final int[] activeSpriteSpecies = new int[2];
    private Label[] playerMoveLabels;
    private final int[] detailPlayerTeamItems = new int[6];
    private final int[] detailPlayerTeamSpecies = new int[6];
    private final int[] detailEnemyTeamSpecies = new int[6];
    private PartySnapshot[] lastLoggedParty = new PartySnapshot[0];
    private PartySnapshot[] latestParty = new PartySnapshot[0];
    private boolean sidebarCollapsed;
    private static final DateTimeFormatter BATTLE_HISTORY_TIME = DateTimeFormatter
            .ofPattern("dd MMM · HH:mm", new Locale("es", "MX"));

    @FXML
    private void initialize() {
        profilePicture.setClip(new Circle(21, 21, 21));
        combatDetailViewController.bind(this::closeCombatDetails,
                this::openCurrentBattleLog, this::openPokemonDetails);
        combatDetailViewController.configure(spriteCache);
        doubleCombatDetailViewController.configure(spriteCache, this::closeCombatDetails,
                this::openCurrentBattleLog, this::openPokemonDetails);
        sosCombatDetailViewController.configure(spriteCache, this::closeCombatDetails,
                this::openCurrentBattleLog, this::openPokemonDetails);
        battleRoyaleCombatDetailViewController.configure(spriteCache, this::closeCombatDetails,
                this::openCurrentBattleLog, this::openPokemonDetails);
        allyCombatDetailViewController.configure(spriteCache, this::closeCombatDetails,
                this::openCurrentBattleLog, this::openPokemonDetails);
        boxesViewController.setPokemonDetailsAction(this::openServerPokemonDetails);
        partyNames = new Label[]{partySlot0Name, partySlot1Name, partySlot2Name,
                partySlot3Name, partySlot4Name, partySlot5Name};
        partyDetails = new Label[]{partySlot0Details, partySlot1Details, partySlot2Details,
                partySlot3Details, partySlot4Details, partySlot5Details};
        partyHp = new Label[]{partySlot0Hp, partySlot1Hp, partySlot2Hp,
                partySlot3Hp, partySlot4Hp, partySlot5Hp};
        partyCards = new HBox[]{partySlot0Card, partySlot1Card, partySlot2Card,
                partySlot3Card, partySlot4Card, partySlot5Card};
        partySprites = new ImageView[]{partySlot0Sprite, partySlot1Sprite, partySlot2Sprite,
                partySlot3Sprite, partySlot4Sprite, partySlot5Sprite};
        for (int slot = 0; slot < partyCards.length; slot++) {
            updatePartySlotInteraction(slot, false);
        }
        playerMoveLabels = new Label[]{playerMove0Label, playerMove1Label, playerMove2Label, playerMove3Label};
        updateBattleCardInteraction(false);
        updatePokeVial(null, false);
        try {
            battleLogManager.loadHistory();
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not load battle log history", exception);
        }
        renderBattleLogPanel();
        clearNotifications();
        mailboxViewController.setSessionListener(this::setAuthenticated);
        mailboxViewController.setTrainerListener(profileTrainerName::setText);
        mailboxViewController.setProfileImageListener(
                image -> profilePicture.setImage(image == null ? defaultProfilePicture : image));
        inventoryViewController.configure(this::executeInventoryCommand);
        boosterPacksViewController.configure(mailboxViewController::refresh);
        saveFileWatcher.start();
        // Battle detection must remain independent from the currently selected screen.
        // Keep battle activation and single/double classification responsive even when the emulator
        // is running above normal speed. The guarded refresh skips ticks while a read is in flight.
        liveRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), ignored -> refreshLiveData()));
        liveRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        liveRefreshTimeline.play();
        battleTextTimeline = new Timeline(new KeyFrame(Duration.millis(500), ignored -> pollBattleText()));
        battleTextTimeline.setCycleCount(Timeline.INDEFINITE);
        battleTextTimeline.play();
        refreshLiveData();
    }

    @FXML
    private void selectSection(javafx.event.ActionEvent event) {
        ToggleButton selectedButton = (ToggleButton) event.getSource();
        if (!selectedButton.isSelected()) {
            selectedButton.setSelected(true);
            return;
        }
        if (!authenticated) return;
        showSection(selectedButton);
    }

    @FXML
    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        double width = sidebarCollapsed ? 82 : 264;
        sidebar.setMinWidth(width);
        sidebar.setPrefWidth(width);
        sidebar.setMaxWidth(width);
        if (sidebarCollapsed) {
            if (!sidebar.getStyleClass().contains("sidebar-collapsed")) {
                sidebar.getStyleClass().add("sidebar-collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("sidebar-collapsed");
        }

        setShown(sidebarWordmark, !sidebarCollapsed);
        setShown(sidebarAppIcon, sidebarCollapsed);
        setShown(emulatorConnectionDetails, !sidebarCollapsed);
        setShown(emulatorRetryButton, !sidebarCollapsed);
        setShown(trainerDetails, !sidebarCollapsed);
        setShown(logoutButton, !sidebarCollapsed);
        navigation.setPadding(sidebarCollapsed
                ? new Insets(8)
                : new Insets(8, 0, 8, 12));
        sidebarFooter.setPadding(sidebarCollapsed
                ? new Insets(10)
                : new Insets(10, 14, 14, 16));
        sidebarCollapseButton.setText(sidebarCollapsed ? "›" : "‹");

        navigationGroup.getToggles().stream()
                .map(toggle -> (ToggleButton) toggle)
                .forEach(button -> {
                    button.setContentDisplay(sidebarCollapsed
                            ? ContentDisplay.GRAPHIC_ONLY
                            : ContentDisplay.LEFT);
                    button.setTooltip(sidebarCollapsed ? new Tooltip(button.getText()) : null);
                });
    }

    private static void setShown(javafx.scene.Node node, boolean shown) {
        node.setManaged(shown);
        node.setVisible(shown);
    }

    private void showSection(ToggleButton selectedButton) {
        String section = String.valueOf(selectedButton.getUserData());
        boolean showLive = "live".equals(section);
        boolean showBoxes = "boxes".equals(section);
        boolean showWildcards = "wildcards".equals(section);
        boolean showInventory = "inventory".equals(section);
        boolean showMailbox = "mailbox".equals(section);
        boolean showBoosterPacks = "booster-packs".equals(section);
        boolean showHome = !(showLive || showBoxes || showWildcards || showInventory
                || showMailbox || showBoosterPacks);
        homeView.setManaged(showHome);
        homeView.setVisible(showHome);
        boxesView.setManaged(showBoxes);
        boxesView.setVisible(showBoxes);
        wildcardsView.setManaged(showWildcards);
        wildcardsView.setVisible(showWildcards);
        inventoryView.setManaged(showInventory);
        inventoryView.setVisible(showInventory);
        mailboxView.setManaged(showMailbox);
        mailboxView.setVisible(showMailbox);
        boosterPacksView.setManaged(showBoosterPacks);
        boosterPacksView.setVisible(showBoosterPacks);
        combatDetailView.setManaged(false);
        combatDetailView.setVisible(false);
        doubleCombatDetailView.setManaged(false);
        doubleCombatDetailView.setVisible(false);
        sosCombatDetailView.setManaged(false);
        sosCombatDetailView.setVisible(false);
        battleRoyaleCombatDetailView.setManaged(false);
        battleRoyaleCombatDetailView.setVisible(false);
        allyCombatDetailView.setManaged(false);
        allyCombatDetailView.setVisible(false);
        liveView.setManaged(showLive);
        liveView.setVisible(showLive);
        if (showLive) {
            refreshLiveData();
        }
        if (showMailbox) {
            mailboxViewController.refresh();
        }
        if (showBoxes) {
            boxesViewController.refresh();
        }
        if (showInventory) {
            inventoryViewController.refresh();
        }
        if (showBoosterPacks) {
            boosterPacksViewController.refresh();
        }
    }

    private CompletableFuture<String> executeInventoryCommand(ServerClient.ClientCommand command,
                                                               InventoryController.CommandTarget target) {
        return CompletableFuture.supplyAsync(() -> {
            if (!"modify_pokemon_ev.v1".equals(command.capability())
                    && !"modify_nature.v1".equals(command.capability())) {
                throw new CompletionException(new IllegalArgumentException(
                        "Capacidad no soportada: " + command.capability()));
            }
            int slot = target.slot();
            try {
                int species = target.pokemon().species();
                Object rawNature = command.payload().get("nature");
                Object rawStat = command.payload().get("stat");
                Object rawAmount = command.payload().get("amount");
                if ("modify_nature.v1".equals(command.capability()) && !(rawNature instanceof Number))
                    throw new IllegalArgumentException("Payload de naturaleza inválido");
                if ("modify_pokemon_ev.v1".equals(command.capability())
                        && (!(rawStat instanceof String) || !(rawAmount instanceof Number)))
                    throw new IllegalArgumentException("Payload de EV inválido");

                SmPartyEditor.Result ramResult = null;
                String ramFailure = null;
                if (emulatorConnected || liveSessionReady) try {
                    SmPartyEditor ramEditor = new SmPartyEditor();
                    if ("modify_nature.v1".equals(command.capability())) {
                        Number nature = (Number) rawNature;
                        ramResult = withLiveClient(client -> ramEditor.modifyNature(
                                client, slot, species, nature.intValue()));
                    } else {
                        String stat = (String) rawStat;
                        Number amount = (Number) rawAmount;
                        ramResult = withLiveClient(client -> ramEditor.addEv(
                                client, slot, species, stat, amount.intValue()));
                    }
                } catch (Exception failure) {
                    ramFailure = failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage();
                }

                SmSaveEditor editor = new SmSaveEditor();
                SmSaveEditor.Result result;
                if ("modify_nature.v1".equals(command.capability())) {
                    Number nature = (Number) rawNature;
                    result = editor.modifyNature(GAME_CONFIG.save().file(), slot,
                            species, nature.intValue());
                } else {
                    String stat = (String) rawStat;
                    Number amount = (Number) rawAmount;
                    result = editor.addEv(GAME_CONFIG.save().file(), slot,
                            species, stat, amount.intValue());
                }
                String saveDetail = "save " + result.before() + " → " + result.after()
                        + " (backup: " + result.backup().getFileName() + ")";
                String ramDetail = ramResult != null
                        ? "RAM " + ramResult.before() + " → " + ramResult.after()
                        : ramFailure == null ? "RAM no disponible" : "RAM no aplicada: " + ramFailure;
                return result.field() + ": " + ramDetail + "; " + saveDetail;
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
    }

    @FXML
    private void openCombatDetails() {
        if (!battleActive) return;
        liveView.setManaged(false);
        liveView.setVisible(false);
        showCombatPanel(singleBattleActive, sosBattleActive, battleRoyaleActive, allyBattleActive);
        refreshLiveData();
    }

    private void showCombatPanel(boolean single, boolean sos, boolean royale, boolean ally) {
        combatDetailView.setManaged(single);
        combatDetailView.setVisible(single);
        sosCombatDetailView.setManaged(sos);
        sosCombatDetailView.setVisible(sos);
        battleRoyaleCombatDetailView.setManaged(royale);
        battleRoyaleCombatDetailView.setVisible(royale);
        allyCombatDetailView.setManaged(ally);
        allyCombatDetailView.setVisible(ally);
        boolean doubles = !single && !sos && !royale && !ally;
        doubleCombatDetailView.setManaged(doubles);
        doubleCombatDetailView.setVisible(doubles);
    }

    @FXML
    private void closeCombatDetails() {
        combatDetailView.setManaged(false);
        combatDetailView.setVisible(false);
        doubleCombatDetailView.setManaged(false);
        doubleCombatDetailView.setVisible(false);
        sosCombatDetailView.setManaged(false);
        sosCombatDetailView.setVisible(false);
        battleRoyaleCombatDetailView.setManaged(false);
        battleRoyaleCombatDetailView.setVisible(false);
        allyCombatDetailView.setManaged(false);
        allyCombatDetailView.setVisible(false);
        liveView.setManaged(true);
        liveView.setVisible(true);
    }

    @FXML
    private void refreshLiveData() {
        if (usingPokeVial.get()) return;
        long now = System.nanoTime();
        boolean probingForGame = !liveSessionReady;
        if (probingForGame) {
            if (gameWarmupUntilNanos != 0) {
                if (now < gameWarmupUntilNanos) return;
                liveSessionReady = true;
                gameWarmupUntilNanos = 0;
                probingForGame = false;
            } else {
                if (now < nextGameProbeNanos) return;
                nextGameProbeNanos = now + GAME_PROBE_INTERVAL_NANOS;
            }
        }
        if (!refreshingLiveData.compareAndSet(false, true)) {
            return;
        }
        // The vial may have reserved RAM access between the first check and this CAS.
        if (usingPokeVial.get()) {
            refreshingLiveData.set(false);
            return;
        }
        if (!probingForGame && !emulatorConnectionRow.getStyleClass().contains("connected")) {
            setConnectionState("Sincronizando…", null);
        }
        boolean gameProbe = probingForGame;
        Thread.startVirtualThread(() -> {
            try {
                if (gameProbe) {
                    // LimoMada3DS creates the RPC endpoint as the emulated title starts. A single-byte
                    // probe detects that transition without starting all party/battle readers while
                    // the ROM is still being initialized.
                    try (CitraUdpClient client = new CitraUdpClient(GAME_CONFIG.ram().host(), GAME_CONFIG.ram().port(),
                            java.time.Duration.ofMillis(300))) {
                        client.readMemory(GAME_CONFIG.ram().memoryMap().party().address(), 1);
                    }
                    Platform.runLater(() -> {
                        gameWarmupUntilNanos = System.nanoTime() + GAME_WARMUP_NANOS;
                        setConnectionState("Juego detectado…", null);
                    });
                    return;
                }
                LiveSnapshot snapshot = withLiveClient(client -> {
                    PartySnapshot[] party = readParty(client);
                    ActiveSnapshot active = readActivePokemon(client);
                    if (active.playerOne() == 0 && active.enemyOne() == 0
                            && active.playerTwo() == 0 && active.enemyTwo() == 0) {
                        latestBattleText = "";
                    }
                    BattleSnapshot battle = readBattle(client, active, latestBattleText);
                    return new LiveSnapshot(party, active, battle);
                });
                PartySnapshot[] party = snapshot.party();
                ActiveSnapshot active = snapshot.active();
                BattleSnapshot battle = snapshot.battle();
                Platform.runLater(() -> renderLiveData(party, active, battle));
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    liveSessionReady = false;
                    gameWarmupUntilNanos = 0;
                    nextGameProbeNanos = System.nanoTime() + GAME_PROBE_INTERVAL_NANOS;
                    setConnectionState("Esperando un juego", null);
                    markGameUnavailable();
                });
            } finally {
                refreshingLiveData.set(false);
            }
        });
    }

    private void markGameUnavailable() {
        updateBattleLogState(false, false, "", "");
        updateBattleCardInteraction(false);
        battleModeLabel.setText("Fuera de combate");
        battleSummaryMessage.setText("Abre un juego en LimoMada3DS");
        if (combatDetailView.isVisible()) {
            closeCombatDetails();
        }
    }

    private void pollBattleText() {
        if (!battleActive || !pollingBattleText.compareAndSet(false, true)) return;
        Thread.startVirtualThread(() -> {
            try {
                String message = withLiveClient(client -> new SmBattleTextReader(client).read().message());
                latestBattleText = message;
                Platform.runLater(() -> {
                    if (battleActive && battleLogManager.record(Instant.now(), message, singleBattleActive,
                            primaryTurnPokemon)) {
                        updateActiveBattleLogModal();
                    }
                });
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.DEBUG, "Battle text poll failed", exception);
            } finally {
                pollingBattleText.set(false);
            }
        });
    }

    private static PartySnapshot[] readParty(CitraUdpClient client) throws Exception {
        PartySnapshot[] result = new PartySnapshot[6];
        var party = GAME_CONFIG.ram().memoryMap().party();
        for (int slot = 0; slot < result.length; slot++) {
            long address = party.address() + (long) party.slotStride() * slot;
            byte[] encryptedPokemon = client.readMemory(address, party.pokemonDataSize());
            byte[] encryptedStats = client.readMemory(address + party.statsOffset(), party.statsDataSize());
            byte[] encrypted = new byte[encryptedPokemon.length + encryptedStats.length];
            System.arraycopy(encryptedPokemon, 0, encrypted, 0, encryptedPokemon.length);
            System.arraycopy(encryptedStats, 0, encrypted, encryptedPokemon.length, encryptedStats.length);
            byte[] decrypted = PokemonCrypto.decrypt(encrypted);
            int species = unsignedShort(decrypted, 0x08);
            if (species == 0) {
                result[slot] = PartySnapshot.empty();
                continue;
            }
            result[slot] = new PartySnapshot(
                    species,
                    decodeNickname(decrypted),
                    Byte.toUnsignedInt(decrypted[0xec]),
                    unsignedShort(decrypted, 0xf0),
                    unsignedShort(decrypted, 0xf2),
                    unsignedShort(decrypted, 0x0a),
                    Byte.toUnsignedInt(decrypted[0x14]),
                    Byte.toUnsignedInt(decrypted[0x1c]),
                    new int[]{unsignedShort(decrypted, 0xf2), unsignedShort(decrypted, 0xf4),
                            unsignedShort(decrypted, 0xf6), unsignedShort(decrypted, 0xfa),
                            unsignedShort(decrypted, 0xfc), unsignedShort(decrypted, 0xf8)},
                    new int[]{unsignedShort(decrypted, 0x5a), unsignedShort(decrypted, 0x5c),
                            unsignedShort(decrypted, 0x5e), unsignedShort(decrypted, 0x60)},
                    ByteBuffer.wrap(decrypted, 0xe8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(),
                    new int[]{Byte.toUnsignedInt(decrypted[0x62]), Byte.toUnsignedInt(decrypted[0x63]),
                            Byte.toUnsignedInt(decrypted[0x64]), Byte.toUnsignedInt(decrypted[0x65])},
                    partyMaxPp(decrypted));
        }
        return result;
    }

    private static ActiveSnapshot readActivePokemon(CitraUdpClient client) throws Exception {
        int[] species = new int[SmMemoryMap.ACTIVE_POKEMON_COUNT];
        for (int slot = 0; slot < species.length; slot++) {
            long address = SmMemoryMap.ACTIVE_POKEMON_ADDRESS
                    + (long) SmMemoryMap.ACTIVE_POKEMON_STRIDE * slot;
            species[slot] = unsignedShort(client.readMemory(address, 2), 0);
        }
        return new ActiveSnapshot(species[0], species[1], species[2], species[3]);
    }

    private static BattleSnapshot readBattle(CitraUdpClient client, ActiveSnapshot active,
                                             String battleText) throws Exception {
        if (active.playerOne() == 0 && active.enemyOne() == 0
                && active.playerTwo() == 0 && active.enemyTwo() == 0) {
            return BattleSnapshot.empty();
        }

        var combat = GAME_CONFIG.ram().memoryMap()
                .battle(net.paramada.pokemada.game.official.shared.memory.BattleEnvironment.WILD).combat();
        int finalSlot = 23;
        int regionSize = finalSlot * combat.pokemonStride() + combat.pokemonDataSize();
        byte[] region = client.readMemory(combat.address(), regionSize);
        BattlePokemonSnapshot[] playerTeam = parseBattleTeam(region, combat.pokemonStride(), 0);
        BattlePokemonSnapshot[] allyTeam = parseBattleTeam(region, combat.pokemonStride(), 6);
        BattlePokemonSnapshot[] enemyTeam = parseBattleTeam(region, combat.pokemonStride(), 12);
        BattlePokemonSnapshot[] fourthTeam = parseBattleTeam(region, combat.pokemonStride(), 18);
        BattlePokemonSnapshot player = findBattlePokemon(playerTeam, active.playerOne());
        BattlePokemonSnapshot enemy = findBattlePokemon(enemyTeam, active.enemyOne());
        return new BattleSnapshot(player, enemy, playerTeam, allyTeam, enemyTeam, fourthTeam, battleText);
    }

    private static BattlePokemonSnapshot[] parseBattleTeam(byte[] region, int stride, int firstSlot) {
        BattlePokemonSnapshot[] team = new BattlePokemonSnapshot[6];
        for (int index = 0; index < team.length; index++) {
            int offset = (firstSlot + index) * stride;
            team[index] = unsignedShort(region, offset + 0x04) == 0
                    ? BattlePokemonSnapshot.empty() : parseBattlePokemon(region, offset);
        }
        return team;
    }

    private static BattlePokemonSnapshot findBattlePokemon(BattlePokemonSnapshot[] team, int activeSpecies) {
        if (activeSpecies == 0) {
            return BattlePokemonSnapshot.empty();
        }
        for (BattlePokemonSnapshot pokemon : team) {
            if (pokemon.species() == activeSpecies) {
                return pokemon;
            }
        }
        return BattlePokemonSnapshot.empty();
    }

    private static boolean containsSpecies(BattlePokemonSnapshot[] team, int species) {
        return species != 0 && findBattlePokemon(team, species).species() != 0;
    }

    private static BattlePokemonSnapshot parseBattlePokemon(byte[] data, int offset) {
        int[] moves = new int[4];
        int[] pp = new int[4];
        for (int move = 0; move < moves.length; move++) {
            int moveOffset = offset + 0x1f4 + move * 14;
            moves[move] = unsignedShort(data, moveOffset);
            pp[move] = Byte.toUnsignedInt(data[moveOffset + 2]);
        }
        return new BattlePokemonSnapshot(
                unsignedShort(data, offset + 0x04),
                Byte.toUnsignedInt(data[offset + 0x10]),
                unsignedShort(data, offset + 0x08),
                unsignedShort(data, offset + 0x06),
                unsignedShort(data, offset + 0x0a),
                Byte.toUnsignedInt(data[offset + 0x22c]),
                Byte.toUnsignedInt(data[offset + 0x1dc]),
                Byte.toUnsignedInt(data[offset + 0x1dd]),
                battleStatus(data, offset),
                new int[]{
                        unsignedShort(data, offset + 0x1d2),
                        unsignedShort(data, offset + 0x1d4),
                        unsignedShort(data, offset + 0x1d6),
                        unsignedShort(data, offset + 0x1d8),
                        unsignedShort(data, offset + 0x1da)},
                new int[]{
                        Byte.toUnsignedInt(data[offset + 0x1e2]) - 6,
                        Byte.toUnsignedInt(data[offset + 0x1e3]) - 6,
                        Byte.toUnsignedInt(data[offset + 0x1e4]) - 6,
                        Byte.toUnsignedInt(data[offset + 0x1e5]) - 6,
                        Byte.toUnsignedInt(data[offset + 0x1e6]) - 6},
                moves,
                pp);
    }

    private static String battleStatus(byte[] data, int offset) {
        if (activeStatus(data[offset + 0x20])) return "PAR";
        if (activeStatus(data[offset + 0x28])) return "DOR";
        if (activeStatus(data[offset + 0x30])) return "CON";
        if (activeStatus(data[offset + 0x38])) return "QUE";
        if (activeStatus(data[offset + 0x40])) return "ENV";
        return "Sin estado";
    }

    private static boolean activeStatus(byte value) {
        int status = Byte.toUnsignedInt(value);
        return status != 0 && status != 248;
    }

    private void renderLiveData(PartySnapshot[] party, ActiveSnapshot active, BattleSnapshot battle) {
        setConnectionState("Conectado", "connected");
        latestParty = party.clone();
        logPartyUpdate(party);
        int occupied = 0;
        for (int slot = 0; slot < party.length; slot++) {
            PartySnapshot pokemon = party[slot];
            updatePartySlotInteraction(slot, pokemon.species() != 0);
            if (pokemon.species() == 0) {
                partySpriteSpecies[slot] = 0;
                partySprites[slot].setImage(null);
                partyNames[slot].setText("Vacío");
                partyDetails[slot].setText("—");
                partyHp[slot].setText("");
            } else {
                occupied++;
                partyNames[slot].setText(pokemon.nickname());
                partyDetails[slot].setText(speciesName(pokemon.species()) + "  ·  Nv. " + pokemon.level());
                partyHp[slot].setText(pokemon.currentHp() + " / " + pokemon.maxHp() + " PS");
                loadPartySprite(slot, pokemon.species());
            }
        }
        teamSummaryLabel.setText(occupied + " Pokémon");

        boolean hasActiveAddresses = active.playerOne() != 0 || active.enemyOne() != 0
                || active.playerTwo() != 0 || active.enemyTwo() != 0;
        boolean inBattle = hasActiveAddresses
                && battle.player().species() != 0
                && battle.enemy().species() != 0;
        boolean singleBattle = inBattle && active.playerTwo() == 0 && active.enemyTwo() == 0;
        boolean royaleBattle = inBattle && active.enemyTwo() != 0
                && containsSpecies(battle.fourthTeam(), active.enemyTwo());
        boolean allyBattle = inBattle && !royaleBattle && active.playerTwo() != 0
                && containsSpecies(battle.allyTeam(), active.playerTwo());
        boolean sosBattle = inBattle && !royaleBattle && active.playerTwo() == 0 && active.enemyTwo() != 0;
        sosBattleActive = sosBattle;
        battleRoyaleActive = royaleBattle;
        allyBattleActive = allyBattle;
        try {
            boolean recharged = pokeVial.observe(toVialPartyState(party), inBattle);
            updatePokeVial(recharged ? "Recargado en el Centro Pokémon" : null, inBattle);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not persist Poke Vial recharge", exception);
            updatePokeVial("No se pudo guardar el estado", inBattle);
        }
        primaryTurnPokemon = primaryTurnPokemon(party, battle.player());
        updateBattleLogState(inBattle, singleBattle, battle.battleText(), primaryTurnPokemon);
        updateBattleCardInteraction(inBattle);
        boolean detailsOpen = combatDetailView.isVisible() || doubleCombatDetailView.isVisible()
                || sosCombatDetailView.isVisible() || battleRoyaleCombatDetailView.isVisible();
        detailsOpen = detailsOpen || allyCombatDetailView.isVisible();
        if (!inBattle && detailsOpen) {
            closeCombatDetails();
        } else if (inBattle && detailsOpen) {
            // SOS starts (and can end) midway through a normal wild encounter. Keep the same
            // detail session open and swap its presentation as the active slots change.
            showCombatPanel(singleBattle, sosBattle, royaleBattle, allyBattle);
        }
        battleModeLabel.setText(inBattle
                ? (royaleBattle ? "Battle Royale" : allyBattle ? "Combate con aliado" : sosBattle ? "Combate SOS" : active.playerTwo() != 0 || active.enemyTwo() != 0
                        ? "Combate doble" : "Combate individual")
                : "Fuera de combate");
        battleSummaryMessage.setText(inBattle
                ? "Hay un combate activo"
                : "No hay un combate activo");
        renderBattleSide(0, battle.player(), playerActiveLabel, playerActiveMetaLabel,
                playerHpLabel, playerHpBar, playerBattleDetailsLabel, playerActiveSprite);
        renderBattleSide(1, battle.enemy(), enemyActiveLabel, enemyActiveMetaLabel,
                enemyHpLabel, enemyHpBar, enemyBattleDetailsLabel, enemyActiveSprite);
        renderMoves(battle.player());
        if (combatDetailView.isVisible()) {
            combatDetailViewController.render(party, battle);
        }
        if (doubleCombatDetailView.isVisible()) {
            doubleCombatDetailViewController.render(party, battle, active);
        }
        if (sosCombatDetailView.isVisible()) {
            sosCombatDetailViewController.render(party, battle, active);
        }
        if (battleRoyaleCombatDetailView.isVisible()) {
            battleRoyaleCombatDetailViewController.render(party, battle, active);
        }
        if (allyCombatDetailView.isVisible()) {
            allyCombatDetailViewController.render(party, battle, active);
        }
    }

    @FXML
    private void usePokeVial() {
        if (!emulatorConnected) {
            updatePokeVial("Conecta LimoMada3DS para usar Poke Vial", false);
            return;
        }
        if (battleActive) {
            updatePokeVial("No puede usarse durante un combate", true);
            return;
        }
        if (!pokeVial.available()) {
            updatePokeVial("Sin cargas; visita un Centro Pokémon", false);
            return;
        }
        if (!usingPokeVial.compareAndSet(false, true)) {
            updatePokeVial("El Poke Vial ya está en uso", false);
            return;
        }
        pokeVialButton.setDisable(true);
        pokeVialStatus.setText("Restaurando equipo…");
        Thread.startVirtualThread(() -> {
            String message;
            boolean healed = false;
            try {
                waitForLiveRefresh();
                SmPartyHealer.HealResult result = withLiveClient(client -> new SmPartyHealer().heal(client));
                    if (result.healedSlots() == 0) {
                        message = "Tu equipo ya está completamente restaurado";
                    } else {
                        pokeVial.consume();
                        healed = true;
                        message = "Equipo restaurado · " + result.healedSlots() +
                                (result.healedSlots() == 1 ? " Pokémon" : " Pokémon");
                    }
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Poke Vial failed", exception);
                message = "No se pudo restaurar · " + conciseError(exception);
            } finally {
                usingPokeVial.set(false);
            }
            String finalMessage = message;
            boolean finalHealed = healed;
            Platform.runLater(() -> {
                if (finalHealed) playPokeVialSound();
                updatePokeVial(finalMessage, battleActive);
                refreshLiveData();
            });
        });
    }

    private void playPokeVialSound() {
        try {
            if (pokeVialSound == null) {
                pokeVialSound = new AudioClip(MainController.class.getResource(
                        "/net/paramada/pokemada/assets/poke-vial.mp3").toExternalForm());
            }
            pokeVialSound.play();
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not play Poke Vial sound", exception);
        }
    }

    private void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        sidebar.setManaged(authenticated);
        sidebar.setVisible(authenticated);
        if (!authenticated) {
            stopNotifications();
            clearNotifications();
            homeView.setManaged(false);
            homeView.setVisible(false);
            liveView.setManaged(false);
            liveView.setVisible(false);
            boxesView.setManaged(false);
            boxesView.setVisible(false);
            wildcardsView.setManaged(false);
            wildcardsView.setVisible(false);
            boosterPacksView.setManaged(false);
            boosterPacksView.setVisible(false);
            combatDetailView.setManaged(false);
            combatDetailView.setVisible(false);
            doubleCombatDetailView.setManaged(false);
            doubleCombatDetailView.setVisible(false);
            sosCombatDetailView.setManaged(false);
            sosCombatDetailView.setVisible(false);
            battleRoyaleCombatDetailView.setManaged(false);
            battleRoyaleCombatDetailView.setVisible(false);
            allyCombatDetailView.setManaged(false);
            allyCombatDetailView.setVisible(false);
            mailboxView.setManaged(true);
            mailboxView.setVisible(true);
            return;
        }
        GameDataCatalogSync.synchronize(ServerSettings.load());
        startNotifications();
        homeNavigationButton.setSelected(true);
        showSection(homeNavigationButton);
    }

    public void refreshProfile() {
        if (authenticated) mailboxViewController.refreshProfile();
    }

    @FXML
    private void logout() {
        if (authenticated) mailboxViewController.logoutSession();
    }

    private void waitForLiveRefresh() throws InterruptedException, IOException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (refreshingLiveData.get()) {
            if (System.nanoTime() >= deadline) {
                throw new IOException("la lectura de RAM no terminó");
            }
            Thread.sleep(25);
        }
    }

    private static String conciseError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 72 ? message : message.substring(0, 69) + "…";
    }

    private void updatePokeVial(String message, boolean inBattle) {
        pokeVialButton.setText("POKE VIAL  ·  " + pokeVial.charges() + "/" + pokeVial.maxCharges());
        pokeVialButton.setDisable(!emulatorConnected || inBattle || !pokeVial.available() || usingPokeVial.get());
        if (message != null) pokeVialStatus.setText(message);
    }

    private static int[] partyMaxPp(byte[] decrypted) {
        int[] result = new int[4];
        for (int move = 0; move < result.length; move++) {
            int current = Byte.toUnsignedInt(decrypted[0x62 + move]);
            int maximum = SmPartyHealer.maxPp(unsignedShort(decrypted, 0x5a + move * 2),
                    Byte.toUnsignedInt(decrypted[0x66 + move]));
            result[move] = maximum < 0 ? current : maximum;
        }
        return result;
    }

    private static PokeVial.PartyState toVialPartyState(PartySnapshot[] party) {
        int[] species = new int[party.length];
        int[] currentHp = new int[party.length];
        int[] maxHp = new int[party.length];
        int[] status = new int[party.length];
        int[][] currentPp = new int[party.length][4];
        int[][] maxPp = new int[party.length][4];
        for (int slot = 0; slot < party.length; slot++) {
            PartySnapshot pokemon = party[slot];
            species[slot] = pokemon.species();
            currentHp[slot] = pokemon.currentHp();
            maxHp[slot] = pokemon.maxHp();
            status[slot] = pokemon.status();
            currentPp[slot] = pokemon.currentPp().clone();
            maxPp[slot] = pokemon.maxPp().clone();
        }
        return new PokeVial.PartyState(species, currentHp, maxHp, status, currentPp, maxPp);
    }

    private void updatePartySlotInteraction(int slot, boolean occupied) {
        HBox card = partyCards[slot];
        card.setMouseTransparent(!occupied);
        if (occupied) {
            if (!card.getStyleClass().contains("clickable-card")) {
                card.getStyleClass().add("clickable-card");
            }
            card.getStyleClass().remove("pokemon-slot-empty");
        } else {
            card.getStyleClass().remove("clickable-card");
            if (!card.getStyleClass().contains("pokemon-slot-empty")) {
                card.getStyleClass().add("pokemon-slot-empty");
            }
        }
    }

    private void updateBattleCardInteraction(boolean inBattle) {
        battleSummaryCard.setMouseTransparent(!inBattle);
        battleSummaryLink.setText(inBattle ? "ABRIR DETALLE  →" : "ESPERANDO COMBATE");
        if (inBattle) {
            if (!battleSummaryCard.getStyleClass().contains("clickable-card")) {
                battleSummaryCard.getStyleClass().add("clickable-card");
            }
            battleSummaryCard.getStyleClass().remove("battle-card-inactive");
        } else {
            battleSummaryCard.getStyleClass().remove("clickable-card");
            if (!battleSummaryCard.getStyleClass().contains("battle-card-inactive")) {
                battleSummaryCard.getStyleClass().add("battle-card-inactive");
            }
        }
    }

    private void updateBattleLogState(boolean inBattle, boolean singleBattle, String latestMessage,
                                      String primaryPokemonName) {
        Instant now = Instant.now();
        boolean wasActive = battleLogManager.isActive();
        battleActive = inBattle;
        singleBattleActive = singleBattle;
        if (inBattle) {
            if (!battleLogManager.isActive()) battleLogManager.begin(now);
            if (battleLogManager.record(now, latestMessage, singleBattle, primaryPokemonName)) updateActiveBattleLogModal();
        } else if (battleLogManager.isActive()) {
            try {
                battleLogManager.finish(now);
                battleLogController.closeActive();
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not persist battle log", exception);
            }
        }
        if (wasActive != battleLogManager.isActive()) renderBattleLogPanel();
    }

    private void renderBattleLogPanel() {
        combatLogEntries.getChildren().clear();
        combatLogModeChip.setText("HISTORIAL");
        List<BattleLogSession> recent = battleLogManager.recent();
        combatLogLabel.setText(recent.isEmpty() ? "No hay combates guardados" : "Últimos 3 combates");
        for (BattleLogSession session : recent) combatLogEntries.getChildren().add(historyRow(session));
    }

    private void updateActiveBattleLogModal() {
        battleLogManager.activeStartedAt().ifPresent(started ->
                battleLogController.updateActive(started, battleLogManager.activeEvents()));
    }

    private HBox historyRow(BattleLogSession session) {
        Label title = new Label(BATTLE_HISTORY_TIME.format(session.startedAt().atZone(ZoneId.systemDefault())));
        title.getStyleClass().add("battle-log-history-title");
        String firstMessage = session.events().stream()
                .map(BattleLogEvent::message)
                .filter(message -> !BattleLogManager.isTurnMarker(message))
                .findFirst()
                .orElse("Sin eventos");
        Label summary = new Label(session.events().size() + (session.events().size() == 1 ? " evento · " : " eventos · ")
                + firstMessage);
        summary.setWrapText(true);
        summary.getStyleClass().add("battle-log-history-summary");
        VBox text = new VBox(2, title, summary);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label arrow = new Label("›");
        arrow.getStyleClass().add("battle-log-history-arrow");
        HBox row = new HBox(8, text, arrow);
        row.getStyleClass().add("battle-log-history-row");
        row.setOnMouseClicked(ignored -> battleLogController.show(session));
        return row;
    }

    @FXML
    private void openCurrentBattleLog() {
        if (battleLogManager.isActive()) {
            battleLogManager.activeStartedAt().ifPresent(started ->
                    battleLogController.showActive(started, battleLogManager.activeEvents()));
        } else if (!battleLogManager.recent().isEmpty()) {
            battleLogController.show(battleLogManager.recent().getFirst());
        }
    }

    public void shutdown() {
        battleActive = false;
        singleBattleActive = false;
        sosBattleActive = false;
        battleRoyaleActive = false;
        allyBattleActive = false;
        if (liveRefreshTimeline != null) liveRefreshTimeline.stop();
        if (battleTextTimeline != null) battleTextTimeline.stop();
        stopNotifications();
        windowsNotifications.close();
        saveFileWatcher.close();
        closeLiveClient();
        if (battleLogManager.isActive()) {
            try {
                battleLogManager.finish(Instant.now());
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not persist open battle log during shutdown", exception);
            }
        }
    }

    @FXML
    private void openPokemonDetails(javafx.scene.input.MouseEvent event) {
        Object value = ((javafx.scene.Node) event.getSource()).getUserData();
        int slot;
        try {
            slot = Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return;
        }
        openPokemonDetails(slot);
    }

    private <T> T withLiveClient(ClientOperation<T> operation) throws Exception {
        synchronized (liveClientLock) {
            if (liveClient == null) liveClient = new CitraUdpClient(
                    GAME_CONFIG.ram().host(), GAME_CONFIG.ram().port());
            try {
                return operation.apply(liveClient);
            } catch (Exception exception) {
                liveClient.close();
                liveClient = null;
                throw exception;
            }
        }
    }

    private void closeLiveClient() {
        synchronized (liveClientLock) {
            if (liveClient != null) {
                liveClient.close();
                liveClient = null;
            }
        }
    }

    @FunctionalInterface
    private interface ClientOperation<T> {
        T apply(CitraUdpClient client) throws Exception;
    }

    private static String primaryTurnPokemon(PartySnapshot[] party, BattlePokemonSnapshot primary) {
        if (primary.species() == 0) return "";
        for (PartySnapshot pokemon : party) {
            if (pokemon.species() == primary.species() && !pokemon.nickname().isBlank()) return pokemon.nickname();
        }
        return PokemonSpeciesDex.nameOrFallback(primary.species());
    }

    private void openPokemonDetails(int slot) {
        if (slot < 0 || slot >= latestParty.length) return;
        PartySnapshot pokemon = latestParty[slot];
        if (pokemon.species() == 0) return;
        pokemonDetailController.show(pokemon.species(), pokemon.nickname(), pokemon.level(),
                pokemon.nature(), pokemon.ability(), pokemon.heldItem(),
                pokemon.realStats(), pokemon.moves());
    }

    private void startNotifications() {
        stopNotifications();
        clearNotifications();
        ServerSettings settings = ServerSettings.load();
        if (settings.token().isBlank() || settings.username().isBlank()) return;

        long generation = notificationGeneration;
        startRealtimeNotifications(settings);
        new ServerClient(settings.baseUrl()).notifications(settings.token()).whenComplete((notifications, failure) ->
                Platform.runLater(() -> {
                    if (notificationGeneration != generation || !authenticated) return;
                    if (failure != null) {
                        LOGGER.log(System.Logger.Level.WARNING, "Could not load notification history", failure);
                        return;
                    }
                    for (ServerClient.Notification notification : notifications) {
                        if (notification.id() > 0 && displayedNotificationIds.add(notification.id())
                                && !recentlyReceived(notification.message())) {
                            addNotificationRow(notification.message(), notification.createdAt(), false);
                        }
                    }
                    updateNotificationEmptyState();
                }));
    }

    @FXML
    private void reconnectNotifications() {
        if (!authenticated) return;
        LOGGER.log(System.Logger.Level.INFO, "Manual notification WebSocket reconnect requested");
        startRealtimeNotifications(ServerSettings.load());
    }

    private void startRealtimeNotifications(ServerSettings settings) {
        stopRealtimeNotifications();
        if (settings.username().isBlank()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Notification WebSocket was not started because the username is empty");
            return;
        }
        try {
            NotificationConnection connection = new NotificationConnection(settings.baseUrl(), settings.username(),
                    event -> Platform.runLater(() -> showRealtimeNotification(event)));
            notificationConnection = connection;
            connection.start();
        } catch (RuntimeException failure) {
            notificationConnection = null;
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not start notification WebSocket; the application will continue without real-time events",
                    failure);
        }
    }

    private void showRealtimeNotification(NotificationConnection.Event event) {
        if (!authenticated) {
            LOGGER.log(System.Logger.Level.INFO,
                    "Ignored real-time server notification while signed out: type={0}, message={1}",
                    event.type(), event.message());
            return;
        }
        LOGGER.log(System.Logger.Level.INFO,
                "Displaying real-time server notification: type={0}, message={1}",
                event.type(), event.message());
        long now = System.currentTimeMillis();
        recentRealtimeNotifications.put(event.message(), now);
        recentRealtimeNotifications.entrySet().removeIf(entry -> now - entry.getValue() > 30_000);
        addNotificationRow(event.message(), Instant.now(), true);
        if ("notification".equals(event.type())) {
            windowsNotifications.show("¡Notificación!", event.message());
        }
        playNotificationSound();
        updateNotificationEmptyState();
    }

    private boolean recentlyReceived(String message) {
        Long receivedAt = recentRealtimeNotifications.get(message);
        return receivedAt != null && System.currentTimeMillis() - receivedAt < 30_000;
    }

    private void addNotificationRow(String message, Instant createdAt, boolean newestFirst) {
        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(Double.MAX_VALUE);
        text.getStyleClass().add("notification-message");
        String timeText = createdAt == null ? "" : DateTimeFormatter.ofPattern("dd MMM · HH:mm", new Locale("es", "MX"))
                .withZone(ZoneId.systemDefault()).format(createdAt);
        Label time = new Label(timeText);
        time.getStyleClass().add("notification-time");
        VBox row = new VBox(4, text, time);
        row.getStyleClass().add("notification-entry");
        if (newestFirst) notificationEntries.getChildren().addFirst(row);
        else notificationEntries.getChildren().add(row);
        while (notificationEntries.getChildren().size() > 20) {
            notificationEntries.getChildren().removeLast();
        }
    }

    private void updateNotificationEmptyState() {
        boolean empty = notificationEntries.getChildren().isEmpty();
        notificationEmptyState.setManaged(empty);
        notificationEmptyState.setVisible(empty);
        notificationScroll.setManaged(!empty);
        notificationScroll.setVisible(!empty);
    }

    private void clearNotifications() {
        displayedNotificationIds.clear();
        recentRealtimeNotifications.clear();
        if (notificationEntries != null) notificationEntries.getChildren().clear();
        if (notificationEmptyState != null) updateNotificationEmptyState();
    }

    private void stopNotifications() {
        notificationGeneration++;
        stopRealtimeNotifications();
    }

    private void stopRealtimeNotifications() {
        NotificationConnection connection = notificationConnection;
        notificationConnection = null;
        if (connection != null) connection.close();
    }

    private void playNotificationSound() {
        try {
            if (notificationSound == null) {
                notificationSound = new AudioClip(MainController.class.getResource(
                        "/net/paramada/pokemada/assets/poke-vial.mp3").toExternalForm());
                notificationSound.setVolume(0.65);
            }
            notificationSound.play();
            LOGGER.log(System.Logger.Level.INFO, "Notification sound playback requested");
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not play notification sound", exception);
        }
    }

    private void openServerPokemonDetails(net.paramada.pokemada.server.ServerClient.Pokemon pokemon) {
        pokemonDetailController.show(pokemon.dexNumber(), pokemon.form(), pokemon.name(), pokemon.level(),
                pokemon.natureName(), pokemon.ability(), pokemon.heldItem(), pokemon.stats(), pokemon.moves());
    }

    private static Image bundledImage(String filename) {
        return new Image(MainController.class.getResource(
                "/net/paramada/pokemada/assets/" + filename).toExternalForm());
    }

    private void renderBattleSide(
            int side, BattlePokemonSnapshot pokemon, Label name, Label meta, Label hpText,
            ProgressBar hpBar, Label details, ImageView sprite
    ) {
        if (pokemon.species() == 0) {
            activeSpriteSpecies[side] = 0;
            sprite.setImage(null);
            name.setText("—");
            meta.setText("");
            hpText.setText("Esperando combate");
            hpBar.setProgress(0);
            details.setText("");
            return;
        }
        name.setText(speciesName(pokemon.species()));
        meta.setText("#%04d  ·  Nv. %d  ·  %s".formatted(
                pokemon.species(), pokemon.level(), pokemon.status()));
        hpText.setText(pokemon.currentHp() + " / " + pokemon.maxHp() + " PS");
        hpBar.setProgress(pokemon.maxHp() == 0 ? 0 : (double) pokemon.currentHp() / pokemon.maxHp());
        details.setText("Tipos %d/%d  ·  Habilidad #%d  ·  Objeto #%d%nATQ %d  DEF %d  A.ESP %d  D.ESP %d  VEL %d"
                .formatted(pokemon.typeOne(), pokemon.typeTwo(), pokemon.ability(), pokemon.heldItem(),
                        pokemon.stats()[0], pokemon.stats()[1], pokemon.stats()[2],
                        pokemon.stats()[3], pokemon.stats()[4]));
        loadActiveSprite(side, pokemon.species(), sprite);
    }

    private void renderMoves(BattlePokemonSnapshot pokemon) {
        for (int move = 0; move < playerMoveLabels.length; move++) {
            int moveId = pokemon.moves()[move];
            playerMoveLabels[move].setText(moveId == 0
                    ? "—"
                    : "Movimiento #" + moveId + "   ·   " + pokemon.pp()[move] + " PP");
        }
    }

    private void loadActiveSprite(int side, int species, ImageView view) {
        if (activeSpriteSpecies[side] == species) {
            return;
        }
        activeSpriteSpecies[side] = species;
        spriteCache.load(species).thenAccept(image -> Platform.runLater(() -> {
            if (activeSpriteSpecies[side] == species) {
                view.setImage(image.orElse(null));
            }
        }));
    }

    private void logPartyUpdate(PartySnapshot[] party) {
        if (sameParty(lastLoggedParty, party)) {
            return;
        }
        lastLoggedParty = party.clone();

        StringBuilder message = new StringBuilder("Equipo del jugador actualizado:");
        for (int slot = 0; slot < party.length; slot++) {
            PartySnapshot pokemon = party[slot];
            message.append(System.lineSeparator()).append("  Slot ").append(slot + 1).append(": ");
            if (pokemon.species() == 0) {
                message.append("vacío");
            } else {
                message.append(pokemon.nickname())
                        .append(" (#").append(pokemon.species()).append(", Nv. ")
                        .append(pokemon.level()).append(", PS ")
                        .append(pokemon.currentHp()).append('/').append(pokemon.maxHp()).append(')');
            }
        }
        LOGGER.log(System.Logger.Level.INFO, message.toString());
    }

    private static boolean sameParty(PartySnapshot[] first, PartySnapshot[] second) {
        if (first.length != second.length) return false;
        for (int index = 0; index < first.length; index++) {
            PartySnapshot a = first[index];
            PartySnapshot b = second[index];
            if (a.species() != b.species() || !a.nickname().equals(b.nickname())
                    || a.level() != b.level() || a.currentHp() != b.currentHp()
                    || a.maxHp() != b.maxHp() || a.heldItem() != b.heldItem()
                    || a.ability() != b.ability() || a.nature() != b.nature()
                    || !Arrays.equals(a.realStats(), b.realStats())
                    || !Arrays.equals(a.moves(), b.moves())) return false;
        }
        return true;
    }

    private void loadPartySprite(int slot, int species) {
        if (partySpriteSpecies[slot] == species) {
            return;
        }
        partySpriteSpecies[slot] = species;
        spriteCache.load(species).thenAccept(image -> Platform.runLater(() -> {
            // The slot may have changed while the download was in progress.
            if (partySpriteSpecies[slot] == species) {
                partySprites[slot].setImage(image.orElse(null));
            }
        }));
    }

    private void setConnectionState(String text, String stateClass) {
        emulatorConnected = "connected".equals(stateClass);
        emulatorConnectionStatus.setText(text);
        emulatorConnectionStatus.getStyleClass().removeAll("connected", "error");
        emulatorConnectionRow.getStyleClass().removeAll("connected", "error");
        if (stateClass != null) {
            emulatorConnectionStatus.getStyleClass().add(stateClass);
            emulatorConnectionRow.getStyleClass().add(stateClass);
        }
        emulatorConnectionIcon.setImage("connected".equals(stateClass)
                ? emulatorConnectedIcon : emulatorDisconnectedIcon);
        updatePokeVial(emulatorConnected ? null : "Conecta LimoMada3DS para usar Poke Vial", battleActive);
    }

    private static String speciesName(int species) {
        return PokemonSpeciesDex.nameOrFallback(species);
    }

    private static int unsignedShort(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static String decodeNickname(byte[] decrypted) {
        String value = new String(Arrays.copyOfRange(decrypted, 0x40, 0x58), StandardCharsets.UTF_16LE);
        int terminator = value.indexOf('\0');
        return terminator >= 0 ? value.substring(0, terminator) : value;
    }

    record PartySnapshot(int species, String nickname, int level, int currentHp, int maxHp,
                                 int heldItem, int ability, int nature, int[] realStats, int[] moves,
                                 int status, int[] currentPp, int[] maxPp) {
        private static PartySnapshot empty() {
            return new PartySnapshot(0, "", 0, 0, 0, 0, 0, 0, new int[6], new int[4],
                    0, new int[4], new int[4]);
        }
    }

    record ActiveSnapshot(int playerOne, int enemyOne, int playerTwo, int enemyTwo) {
    }

    private record LiveSnapshot(PartySnapshot[] party, ActiveSnapshot active, BattleSnapshot battle) {
    }

    record BattleSnapshot(BattlePokemonSnapshot player, BattlePokemonSnapshot enemy,
                                  BattlePokemonSnapshot[] playerTeam, BattlePokemonSnapshot[] allyTeam,
                                  BattlePokemonSnapshot[] enemyTeam, BattlePokemonSnapshot[] fourthTeam,
                                  String battleText) {
        private static BattleSnapshot empty() {
            BattlePokemonSnapshot[] playerTeam = new BattlePokemonSnapshot[6];
            BattlePokemonSnapshot[] enemyTeam = new BattlePokemonSnapshot[6];
            BattlePokemonSnapshot[] allyTeam = new BattlePokemonSnapshot[6];
            BattlePokemonSnapshot[] fourthTeam = new BattlePokemonSnapshot[6];
            Arrays.fill(playerTeam, BattlePokemonSnapshot.empty());
            Arrays.fill(allyTeam, BattlePokemonSnapshot.empty());
            Arrays.fill(enemyTeam, BattlePokemonSnapshot.empty());
            Arrays.fill(fourthTeam, BattlePokemonSnapshot.empty());
            return new BattleSnapshot(BattlePokemonSnapshot.empty(), BattlePokemonSnapshot.empty(),
                    playerTeam, allyTeam, enemyTeam, fourthTeam, "");
        }
    }

    record BattlePokemonSnapshot(
            int species,
            int level,
            int currentHp,
            int maxHp,
            int heldItem,
            int ability,
            int typeOne,
            int typeTwo,
            String status,
            int[] stats,
            int[] boosts,
            int[] moves,
            int[] pp
    ) {
        private static BattlePokemonSnapshot empty() {
            return new BattlePokemonSnapshot(0, 0, 0, 0, 0, 0, 0, 0, "",
                    new int[5], new int[5], new int[4], new int[4]);
        }
    }
}
