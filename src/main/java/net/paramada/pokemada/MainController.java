package net.paramada.pokemada;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.paramada.pokemada.battlelog.BattleLogEvent;
import net.paramada.pokemada.battlelog.BattleLogManager;
import net.paramada.pokemada.battlelog.BattleLogSession;
import net.paramada.pokemada.battlelog.BattleLogStore;
import net.paramada.pokemada.features.vial.PokeVial;
import net.paramada.pokemada.game.assets.PokemonSpriteCache;
import net.paramada.pokemada.game.assets.PokemonBaseStats;
import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.assets.MoveEffectiveness;
import net.paramada.pokemada.game.assets.PokemonAbilityDex;
import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.game.assets.PokemonItemSpriteCache;
import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import net.paramada.pokemada.game.official.sm.SmBattleTextReader;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import net.paramada.pokemada.game.official.sm.SmPartyHealer;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainController {
    private static final System.Logger LOGGER = System.getLogger(MainController.class.getName());
    private static final String[] STAT_TIER_CLASSES = {
            "stat-tier-very-low", "stat-tier-low", "stat-tier-medium", "stat-tier-high",
            "stat-tier-very-high", "stat-tier-exceptional", "stat-tier-extreme"
    };

    @FXML
    private ScrollPane homeView;
    @FXML
    private ScrollPane liveView;
    @FXML
    private ScrollPane combatDetailView;
    @FXML
    private ScrollPane boxesView;
    @FXML private ScrollPane showdownView;
    @FXML private ScrollPane wildcardsView;
    @FXML private ScrollPane mailboxView;
    @FXML private ScrollPane roulettesView;
    @FXML private HBox emulatorConnectionRow;
    @FXML private Label emulatorConnectionStatus;
    @FXML private ImageView emulatorConnectionIcon;
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
    private ImageView detailPlayerSprite;
    @FXML
    private ImageView detailEnemySprite;
    @FXML private ImageView detailPlayerTypeOne;
    @FXML private ImageView detailPlayerTypeTwo;
    @FXML private ImageView detailEnemyTypeOne;
    @FXML private ImageView detailEnemyTypeTwo;
    @FXML private ImageView detailPlayerTeam0;
    @FXML private ImageView detailPlayerTeam1;
    @FXML private ImageView detailPlayerTeam2;
    @FXML private ImageView detailPlayerTeam3;
    @FXML private ImageView detailPlayerTeam4;
    @FXML private ImageView detailPlayerTeam5;
    @FXML private StackPane detailPlayerTeamCard0;
    @FXML private StackPane detailPlayerTeamCard1;
    @FXML private StackPane detailPlayerTeamCard2;
    @FXML private StackPane detailPlayerTeamCard3;
    @FXML private StackPane detailPlayerTeamCard4;
    @FXML private StackPane detailPlayerTeamCard5;
    @FXML private ImageView detailPlayerItem0;
    @FXML private ImageView detailPlayerItem1;
    @FXML private ImageView detailPlayerItem2;
    @FXML private ImageView detailPlayerItem3;
    @FXML private ImageView detailPlayerItem4;
    @FXML private ImageView detailPlayerItem5;
    @FXML private ImageView detailEnemyTeam0;
    @FXML private ImageView detailEnemyTeam1;
    @FXML private ImageView detailEnemyTeam2;
    @FXML private ImageView detailEnemyTeam3;
    @FXML private ImageView detailEnemyTeam4;
    @FXML private ImageView detailEnemyTeam5;
    @FXML
    private Label detailPlayerName;
    @FXML
    private Label detailEnemyName;
    @FXML
    private Label detailPlayerMeta;
    @FXML
    private Label detailEnemyMeta;
    @FXML private Label detailPlayerStatus;
    @FXML private Label detailEnemyStatus;
    @FXML
    private ProgressBar detailEnemyHpBar;
    @FXML
    private Label detailEnemyHp;
    @FXML private ProgressBar detailPlayerStat0;
    @FXML private ProgressBar detailPlayerStat1;
    @FXML private ProgressBar detailPlayerStat2;
    @FXML private ProgressBar detailPlayerStat3;
    @FXML private ProgressBar detailPlayerStat4;
    @FXML private ProgressBar detailEnemyStat0;
    @FXML private ProgressBar detailEnemyStat1;
    @FXML private ProgressBar detailEnemyStat2;
    @FXML private ProgressBar detailEnemyStat3;
    @FXML private ProgressBar detailEnemyStat4;
    @FXML private Label detailPlayerStat0Label;
    @FXML private Label detailPlayerStat1Label;
    @FXML private Label detailPlayerStat2Label;
    @FXML private Label detailPlayerStat3Label;
    @FXML private Label detailPlayerStat4Label;
    @FXML private Label detailEnemyStat0Label;
    @FXML private Label detailEnemyStat1Label;
    @FXML private Label detailEnemyStat2Label;
    @FXML private Label detailEnemyStat3Label;
    @FXML private Label detailEnemyStat4Label;
    @FXML private Label detailPlayerBoost0;
    @FXML private Label detailPlayerBoost1;
    @FXML private Label detailPlayerBoost2;
    @FXML private Label detailPlayerBoost3;
    @FXML private Label detailPlayerBoost4;
    @FXML private Label detailEnemyBoost0;
    @FXML private Label detailEnemyBoost1;
    @FXML private Label detailEnemyBoost2;
    @FXML private Label detailEnemyBoost3;
    @FXML private Label detailEnemyBoost4;
    @FXML
    private Label detailPlayerInfo;
    @FXML
    private Label detailEnemyInfo;
    @FXML private Label detailPlayerAbility;
    @FXML private Label detailPlayerItem;
    @FXML private Label detailEnemyAbility;
    @FXML private Label detailEnemyItem;
    @FXML
    private HBox detailMove0;
    @FXML
    private HBox detailMove1;
    @FXML
    private HBox detailMove2;
    @FXML
    private HBox detailMove3;
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
    private final BattleLogManager battleLogManager = new BattleLogManager(new BattleLogStore());
    private final PokeVial pokeVial = new PokeVial();
    private final PokemonSpriteCache spriteCache = new PokemonSpriteCache();
    private final PokemonItemSpriteCache itemSpriteCache = new PokemonItemSpriteCache();
    private final Image missingNoSprite = bundledImage("missingno.png");
    private final Image pokeballSprite = bundledImage("enemy-team-pokeball.png");
    private final Image emulatorConnectedIcon = bundledImage("lime_logo.png");
    private final Image emulatorDisconnectedIcon = bundledImage("lime_logo_off.png");
    private Timeline liveRefreshTimeline;
    private Timeline battleTextTimeline;
    private volatile boolean battleActive;
    private volatile boolean singleBattleActive;
    private Label[] partyNames;
    private Label[] partyDetails;
    private Label[] partyHp;
    private HBox[] partyCards;
    private ImageView[] partySprites;
    private final int[] partySpriteSpecies = new int[6];
    private final int[] activeSpriteSpecies = new int[2];
    private Label[] playerMoveLabels;
    private HBox[] detailMoveCards;
    private Tooltip[] detailMoveTooltips;
    private final Map<String, Image> moveAssetCache = new HashMap<>();
    private ImageView[] detailPlayerTypes;
    private ImageView[] detailEnemyTypes;
    private ImageView[] detailPlayerTeamSprites;
    private StackPane[] detailPlayerTeamCards;
    private ImageView[] detailPlayerItemSprites;
    private Tooltip[] detailPlayerItemTooltips;
    private final int[] detailPlayerTeamItems = new int[6];
    private ImageView[] detailEnemyTeamSprites;
    private ProgressBar[] detailPlayerStats;
    private ProgressBar[] detailEnemyStats;
    private Label[] detailPlayerStatLabels;
    private Label[] detailEnemyStatLabels;
    private Label[] detailPlayerBoosts;
    private Label[] detailEnemyBoosts;
    private Tooltip[] detailPlayerStatTooltips;
    private Tooltip[] detailEnemyStatTooltips;
    private Tooltip detailPlayerAbilityTooltip;
    private Tooltip detailPlayerItemTooltip;
    private Tooltip detailEnemyAbilityTooltip;
    private Tooltip detailEnemyItemTooltip;
    private final int[] detailPlayerTeamSpecies = new int[6];
    private final int[] detailEnemyTeamSpecies = new int[6];
    private PartySnapshot[] lastLoggedParty = new PartySnapshot[0];
    private PartySnapshot[] latestParty = new PartySnapshot[0];
    private static final DateTimeFormatter BATTLE_HISTORY_TIME = DateTimeFormatter
            .ofPattern("dd MMM · HH:mm", new Locale("es", "MX"));

    @FXML
    private void initialize() {
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
        detailMoveCards = new HBox[]{detailMove0, detailMove1, detailMove2, detailMove3};
        detailMoveTooltips = installMoveTooltips(detailMoveCards);
        detailPlayerTeamSprites = new ImageView[]{detailPlayerTeam0, detailPlayerTeam1, detailPlayerTeam2,
                detailPlayerTeam3, detailPlayerTeam4, detailPlayerTeam5};
        detailPlayerTeamCards = new StackPane[]{detailPlayerTeamCard0, detailPlayerTeamCard1,
                detailPlayerTeamCard2, detailPlayerTeamCard3, detailPlayerTeamCard4, detailPlayerTeamCard5};
        for (int slot = 0; slot < detailPlayerTeamCards.length; slot++) {
            updateDetailTeamSlotInteraction(slot, false);
        }
        detailPlayerItemSprites = new ImageView[]{detailPlayerItem0, detailPlayerItem1, detailPlayerItem2,
                detailPlayerItem3, detailPlayerItem4, detailPlayerItem5};
        detailPlayerItemTooltips = installItemTooltips(detailPlayerItemSprites);
        detailPlayerTypes = new ImageView[]{detailPlayerTypeOne, detailPlayerTypeTwo};
        detailEnemyTypes = new ImageView[]{detailEnemyTypeOne, detailEnemyTypeTwo};
        detailEnemyTeamSprites = new ImageView[]{detailEnemyTeam0, detailEnemyTeam1, detailEnemyTeam2,
                detailEnemyTeam3, detailEnemyTeam4, detailEnemyTeam5};
        detailPlayerStats = new ProgressBar[]{detailPlayerStat0, detailPlayerStat1, detailPlayerStat2,
                detailPlayerStat3, detailPlayerStat4};
        detailEnemyStats = new ProgressBar[]{detailEnemyStat0, detailEnemyStat1, detailEnemyStat2,
                detailEnemyStat3, detailEnemyStat4};
        detailPlayerStatLabels = new Label[]{detailPlayerStat0Label, detailPlayerStat1Label,
                detailPlayerStat2Label, detailPlayerStat3Label, detailPlayerStat4Label};
        detailEnemyStatLabels = new Label[]{detailEnemyStat0Label, detailEnemyStat1Label,
                detailEnemyStat2Label, detailEnemyStat3Label, detailEnemyStat4Label};
        detailPlayerBoosts = new Label[]{detailPlayerBoost0, detailPlayerBoost1, detailPlayerBoost2,
                detailPlayerBoost3, detailPlayerBoost4};
        detailEnemyBoosts = new Label[]{detailEnemyBoost0, detailEnemyBoost1, detailEnemyBoost2,
                detailEnemyBoost3, detailEnemyBoost4};
        detailPlayerStatTooltips = installStatTooltips(detailPlayerStats);
        detailEnemyStatTooltips = installStatTooltips(detailEnemyStats);
        detailPlayerAbilityTooltip = installDescriptionTooltip(detailPlayerAbility);
        detailPlayerItemTooltip = installDescriptionTooltip(detailPlayerItem);
        detailEnemyAbilityTooltip = installDescriptionTooltip(detailEnemyAbility);
        detailEnemyItemTooltip = installDescriptionTooltip(detailEnemyItem);
        updateBattleCardInteraction(false);
        updatePokeVial(null, false);
        try {
            battleLogManager.loadHistory();
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not load battle log history", exception);
        }
        renderBattleLogPanel();
        // Battle detection must remain independent from the currently selected screen.
        // Keep battle activation and single/double classification responsive even when the emulator
        // is running above normal speed. The guarded refresh skips ticks while a read is in flight.
        liveRefreshTimeline = new Timeline(new KeyFrame(Duration.millis(500), ignored -> refreshLiveData()));
        liveRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        liveRefreshTimeline.play();
        battleTextTimeline = new Timeline(new KeyFrame(Duration.millis(350), ignored -> pollBattleText()));
        battleTextTimeline.setCycleCount(Timeline.INDEFINITE);
        battleTextTimeline.play();
        refreshLiveData();
    }

    @FXML
    private void selectSection(javafx.event.ActionEvent event) {
        ToggleButton selectedButton = (ToggleButton) event.getSource();
        boolean showLive = "COMBATES".equals(selectedButton.getText());
        boolean showBoxes = "CAJAS".equals(selectedButton.getText());
        boolean showShowdown = "SHOWDOWN".equals(selectedButton.getText());
        boolean showWildcards = "COMODINES".equals(selectedButton.getText());
        boolean showMailbox = "BUZÓN".equals(selectedButton.getText());
        boolean showRoulettes = "RULETAS".equals(selectedButton.getText());
        boolean showHome = !(showLive || showBoxes || showShowdown || showWildcards || showMailbox || showRoulettes);
        homeView.setManaged(showHome);
        homeView.setVisible(showHome);
        boxesView.setManaged(showBoxes);
        boxesView.setVisible(showBoxes);
        showdownView.setManaged(showShowdown);
        showdownView.setVisible(showShowdown);
        wildcardsView.setManaged(showWildcards);
        wildcardsView.setVisible(showWildcards);
        mailboxView.setManaged(showMailbox);
        mailboxView.setVisible(showMailbox);
        roulettesView.setManaged(showRoulettes);
        roulettesView.setVisible(showRoulettes);
        combatDetailView.setManaged(false);
        combatDetailView.setVisible(false);
        liveView.setManaged(showLive);
        liveView.setVisible(showLive);
        if (showLive) {
            refreshLiveData();
        }
    }

    @FXML
    private void openCombatDetails() {
        if (!battleActive) return;
        liveView.setManaged(false);
        liveView.setVisible(false);
        combatDetailView.setManaged(true);
        combatDetailView.setVisible(true);
        refreshLiveData();
    }

    @FXML
    private void closeCombatDetails() {
        combatDetailView.setManaged(false);
        combatDetailView.setVisible(false);
        liveView.setManaged(true);
        liveView.setVisible(true);
    }

    @FXML
    private void refreshLiveData() {
        if (usingPokeVial.get()) return;
        if (!refreshingLiveData.compareAndSet(false, true)) {
            return;
        }
        // The vial may have reserved RAM access between the first check and this CAS.
        if (usingPokeVial.get()) {
            refreshingLiveData.set(false);
            return;
        }
        if (!emulatorConnectionRow.getStyleClass().contains("connected")) {
            setConnectionState("Sincronizando…", null);
        }
        Thread.startVirtualThread(() -> {
            try (CitraUdpClient client = new CitraUdpClient()) {
                PartySnapshot[] party = readParty(client);
                ActiveSnapshot active = readActivePokemon(client);
                BattleSnapshot battle = readBattle(client, active);
                Platform.runLater(() -> renderLiveData(party, active, battle));
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    setConnectionState("No responde", "error");
                    teamSummaryLabel.setText("No se pudo actualizar");
                    combatLogLabel.setText(exception.getMessage());
                });
            } finally {
                refreshingLiveData.set(false);
            }
        });
    }

    private void pollBattleText() {
        if (!battleActive || !pollingBattleText.compareAndSet(false, true)) return;
        Thread.startVirtualThread(() -> {
            try (CitraUdpClient client = new CitraUdpClient()) {
                String message = new SmBattleTextReader(client).read().message();
                Platform.runLater(() -> {
                    if (battleActive && battleLogManager.record(Instant.now(), message, singleBattleActive)) {
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
        var party = SmMemoryMap.INSTANCE.party();
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

    private static BattleSnapshot readBattle(CitraUdpClient client, ActiveSnapshot active) throws Exception {
        if (active.playerOne() == 0 && active.enemyOne() == 0
                && active.playerTwo() == 0 && active.enemyTwo() == 0) {
            return BattleSnapshot.empty();
        }

        var combat = SmMemoryMap.INSTANCE
                .battle(net.paramada.pokemada.game.official.shared.memory.BattleEnvironment.WILD).combat();
        int finalSlot = 17;
        int regionSize = finalSlot * combat.pokemonStride() + combat.pokemonDataSize();
        byte[] region = client.readMemory(combat.address(), regionSize);
        BattlePokemonSnapshot[] playerTeam = parseBattleTeam(region, combat.pokemonStride(), 0);
        BattlePokemonSnapshot[] enemyTeam = parseBattleTeam(region, combat.pokemonStride(), 12);
        BattlePokemonSnapshot player = findBattlePokemon(playerTeam, active.playerOne());
        BattlePokemonSnapshot enemy = findBattlePokemon(enemyTeam, active.enemyOne());
        String battleText = new SmBattleTextReader(client).read().message();
        return new BattleSnapshot(player, enemy, playerTeam, enemyTeam, battleText);
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
        teamSummaryLabel.setText(occupied + (occupied == 1 ? " Pokémon" : " Pokémon"));

        boolean hasActiveAddresses = active.playerOne() != 0 || active.enemyOne() != 0
                || active.playerTwo() != 0 || active.enemyTwo() != 0;
        boolean inBattle = hasActiveAddresses
                && battle.player().species() != 0
                && battle.enemy().species() != 0;
        boolean singleBattle = inBattle && active.playerTwo() == 0 && active.enemyTwo() == 0;
        try {
            boolean recharged = pokeVial.observe(toVialPartyState(party), inBattle);
            updatePokeVial(recharged ? "Recargado en el Centro Pokémon" : null, inBattle);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not persist Poke Vial recharge", exception);
            updatePokeVial("No se pudo guardar el estado", inBattle);
        }
        updateBattleLogState(inBattle, singleBattle, battle.battleText());
        updateBattleCardInteraction(inBattle);
        if (!inBattle && combatDetailView.isVisible()) {
            closeCombatDetails();
        }
        battleModeLabel.setText(inBattle
                ? (active.playerTwo() != 0 || active.enemyTwo() != 0 ? "Combate doble" : "Combate individual")
                : "Fuera de combate");
        battleSummaryMessage.setText(inBattle
                ? "Hay un combate activo"
                : "No hay un combate activo");
        renderBattleSide(0, battle.player(), playerActiveLabel, playerActiveMetaLabel,
                playerHpLabel, playerHpBar, playerBattleDetailsLabel, playerActiveSprite);
        renderBattleSide(1, battle.enemy(), enemyActiveLabel, enemyActiveMetaLabel,
                enemyHpLabel, enemyHpBar, enemyBattleDetailsLabel, enemyActiveSprite);
        renderMoves(battle.player());
        renderCombatDetails(party, battle);
    }

    @FXML
    private void usePokeVial() {
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
            try {
                waitForLiveRefresh();
                try (CitraUdpClient client = new CitraUdpClient()) {
                    SmPartyHealer.HealResult result = new SmPartyHealer().heal(client);
                    if (result.healedSlots() == 0) {
                        message = "Tu equipo ya está completamente restaurado";
                    } else {
                        pokeVial.consume();
                        message = "Equipo restaurado · " + result.healedSlots() +
                                (result.healedSlots() == 1 ? " Pokémon" : " Pokémon");
                    }
                }
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Poke Vial failed", exception);
                message = "No se pudo restaurar · " + conciseError(exception);
            } finally {
                usingPokeVial.set(false);
            }
            String finalMessage = message;
            Platform.runLater(() -> {
                updatePokeVial(finalMessage, battleActive);
                refreshLiveData();
            });
        });
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
        pokeVialButton.setDisable(inBattle || !pokeVial.available() || usingPokeVial.get());
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

    private void updateBattleLogState(boolean inBattle, boolean singleBattle, String latestMessage) {
        Instant now = Instant.now();
        battleActive = inBattle;
        singleBattleActive = singleBattle;
        if (inBattle) {
            if (!battleLogManager.isActive()) battleLogManager.begin(now);
            if (battleLogManager.record(now, latestMessage, singleBattle)) updateActiveBattleLogModal();
        } else if (battleLogManager.isActive()) {
            try {
                battleLogManager.finish(now);
                battleLogController.closeActive();
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not persist battle log", exception);
            }
        }
        renderBattleLogPanel();
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
        if (liveRefreshTimeline != null) liveRefreshTimeline.stop();
        if (battleTextTimeline != null) battleTextTimeline.stop();
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
        if (slot < 0 || slot >= latestParty.length) return;
        PartySnapshot pokemon = latestParty[slot];
        if (pokemon.species() == 0) return;
        pokemonDetailController.show(pokemon.species(), pokemon.nickname(), pokemon.level(),
                pokemon.nature(), pokemon.ability(), pokemon.heldItem(),
                pokemon.realStats(), pokemon.moves());
    }

    private void renderCombatDetails(PartySnapshot[] party, BattleSnapshot battle) {
        renderDetailedSide(battle.player(), detailPlayerName, detailPlayerMeta, detailPlayerStatus, detailPlayerInfo,
                detailPlayerSprite, detailPlayerStats, detailPlayerStatLabels, detailPlayerBoosts,
                detailPlayerStatTooltips, detailPlayerTypes, detailPlayerAbility, detailPlayerItem,
                detailPlayerAbilityTooltip, detailPlayerItemTooltip);
        renderDetailedSide(battle.enemy(), detailEnemyName, detailEnemyMeta, detailEnemyStatus, detailEnemyInfo,
                detailEnemySprite, detailEnemyStats, detailEnemyStatLabels, detailEnemyBoosts,
                detailEnemyStatTooltips, detailEnemyTypes, detailEnemyAbility, detailEnemyItem,
                detailEnemyAbilityTooltip, detailEnemyItemTooltip);
        renderEnemyInformationVisibility(party, battle.player());
        for (int move = 0; move < detailMoveCards.length; move++) {
            int moveId = battle.player().moves()[move];
            renderMove(move, moveId, battle.player(), battle.enemy());
        }
        for (int slot = 0; slot < 6; slot++) {
            updateDetailTeamSlotInteraction(slot, party[slot].species() != 0);
            loadDetailTeamSprite(detailPlayerTeamSprites[slot], detailPlayerTeamSpecies, slot, party[slot].species());
            renderPartyItem(slot, party[slot]);
            int enemySpecies = battle.enemyTeam()[slot].species();
            detailEnemyTeamSpecies[slot] = enemySpecies;
            ImageView enemySlot = detailEnemyTeamSprites[slot];
            enemySlot.setFitWidth(enemySpecies == 0 ? 76 : 56);
            enemySlot.setFitHeight(enemySpecies == 0 ? 68 : 50);
            enemySlot.setImage(enemySpecies == 0 ? missingNoSprite : pokeballSprite);
        }
    }

    private void updateDetailTeamSlotInteraction(int slot, boolean occupied) {
        StackPane card = detailPlayerTeamCards[slot];
        card.setMouseTransparent(!occupied);
        if (occupied) {
            if (!card.getStyleClass().contains("clickable-card")) {
                card.getStyleClass().add("clickable-card");
            }
            card.getStyleClass().remove("team-pokemon-preview-empty");
        } else {
            card.getStyleClass().remove("clickable-card");
            if (!card.getStyleClass().contains("team-pokemon-preview-empty")) {
                card.getStyleClass().add("team-pokemon-preview-empty");
            }
        }
    }

    private void renderMove(
            int slot, int moveId, BattlePokemonSnapshot attacker, BattlePokemonSnapshot enemy
    ) {
        HBox card = detailMoveCards[slot];
        Tooltip tooltip = detailMoveTooltips[slot];
        card.getStyleClass().remove("stab");
        card.getChildren().clear();
        if (moveId == 0) {
            card.getChildren().add(new Label("—"));
            tooltip.setText("");
            return;
        }
        PokemonMoveDex.find(moveId).ifPresentOrElse(move -> {
            if (hasStab(move.type(), attacker)) card.getStyleClass().add("stab");
            ImageView typeIcon = moveIcon(typeAssetName(move.type()), 27);
            Label name = new Label(move.name());
            name.getStyleClass().add("move-card-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            ImageView categoryIcon = moveIcon(categoryAssetName(move.category()), 25);
            card.getChildren().addAll(typeIcon, name, spacer, categoryIcon);
            if (enemy.species() != 0) MoveEffectiveness.against(move, enemy.typeOne(), enemy.typeTwo()).ifPresent(multiplier -> {
                Label badge = new Label("x" + formatMultiplier(multiplier));
                badge.getStyleClass().addAll("move-multiplier", multiplier > 1 ? "effective"
                        : multiplier < 1 ? "resisted" : "neutral");
                card.getChildren().add(badge);
            });
            String description = move.description().isBlank()
                    ? "Sin descripción disponible en Gen VII." : move.description();
            tooltip.setText("%s%n%nTipo: %s · Categoría: %s%nPotencia: %s · Precisión: %s"
                    .formatted(description, move.type(), move.category(),
                            move.power() < 0 ? "—" : Integer.toString(move.power()),
                            move.accuracy() < 0 ? "—" : move.accuracy() + "%"));
        }, () -> {
            card.getChildren().add(new Label("Movimiento #" + moveId));
            tooltip.setText("Movimiento no incluido en el catálogo de Gen VII.");
        });
    }

    private ImageView moveIcon(String filename, double size) {
        Image image = moveAssetCache.computeIfAbsent(filename,
                ignored -> bundledImage("moves/" + filename));
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static String categoryAssetName(String category) {
        return switch (category.toLowerCase()) {
            case "físico" -> "physical_move.png";
            case "especial" -> "special_move.png";
            default -> "status_move.png";
        };
    }

    private static String typeAssetName(String type) {
        return switch (type) {
            case "Lucha" -> "Fighting.png"; case "Volador" -> "Flying.png";
            case "Veneno" -> "Poison.png"; case "Tierra" -> "Ground.png";
            case "Roca" -> "Rock.png"; case "Bicho" -> "Bug.png";
            case "Fantasma" -> "Ghost.png"; case "Acero" -> "Steel.png";
            case "Fuego" -> "Fire.png"; case "Agua" -> "Water.png";
            case "Planta" -> "Grass.png"; case "Eléctrico" -> "Electric.png";
            case "Psíquico" -> "Psychic.png"; case "Hielo" -> "Ice.png";
            case "Dragón" -> "Dragon.png"; case "Siniestro" -> "Dark.png";
            case "Hada" -> "Fairy.png"; default -> "Normal.png";
        };
    }

    private static String formatMultiplier(double multiplier) {
        return multiplier == Math.rint(multiplier)
                ? Integer.toString((int) multiplier) : Double.toString(multiplier);
    }

    private static boolean hasStab(String moveType, BattlePokemonSnapshot attacker) {
        if (attacker.species() == 0) return false;
        String moveAsset = typeAssetName(moveType);
        return moveAsset.equals(typeAssetName(attacker.typeOne()))
                || moveAsset.equals(typeAssetName(attacker.typeTwo()));
    }

    private static Tooltip[] installMoveTooltips(HBox[] cards) {
        Tooltip[] tooltips = new Tooltip[cards.length];
        for (int index = 0; index < cards.length; index++) {
            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(Duration.millis(450));
            tooltip.setHideDelay(Duration.millis(150));
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(380);
            Tooltip.install(cards[index], tooltip);
            tooltips[index] = tooltip;
        }
        return tooltips;
    }

    private void loadDetailTeamSprite(ImageView view, int[] renderedSpecies, int slot, int species) {
        if (renderedSpecies[slot] == species && view.getImage() != null) return;
        renderedSpecies[slot] = species;
        if (species == 0) {
            view.setImage(missingNoSprite);
            return;
        }
        spriteCache.load(species).thenAccept(image -> Platform.runLater(() -> {
            if (renderedSpecies[slot] == species) view.setImage(image.orElse(null));
        }));
    }

    private void renderPartyItem(int slot, PartySnapshot pokemon) {
        ImageView badge = detailPlayerItemSprites[slot];
        Tooltip tooltip = detailPlayerItemTooltips[slot];
        int itemId = pokemon.species() == 0 ? 0 : pokemon.heldItem();
        if (itemId == 0) {
            detailPlayerTeamItems[slot] = 0;
            badge.setImage(null);
            badge.setManaged(false);
            badge.setVisible(false);
            tooltip.setText("");
            return;
        }
        PokemonItemDex.find(itemId).ifPresentOrElse(item -> {
            tooltip.setText(descriptionText(item.name(), item.description()));
            badge.setManaged(true);
            badge.setVisible(true);
            if (detailPlayerTeamItems[slot] == itemId && badge.getImage() != null) return;
            detailPlayerTeamItems[slot] = itemId;
            badge.setImage(null);
            itemSpriteCache.load(item.identifier()).thenAccept(image -> Platform.runLater(() -> {
                if (detailPlayerTeamItems[slot] != itemId) return;
                badge.setImage(image.orElse(null));
                badge.setManaged(image.isPresent());
                badge.setVisible(image.isPresent());
            }));
        }, () -> {
            detailPlayerTeamItems[slot] = itemId;
            badge.setImage(null);
            badge.setManaged(false);
            badge.setVisible(false);
            tooltip.setText("Objeto #" + itemId + "\n\nSin descripción disponible en Gen VII.");
        });
    }

    private void renderTypes(ImageView[] icons, int first, int second) {
        renderTypeIcon(icons[0], first, first >= 0 && first <= 17);
        renderTypeIcon(icons[1], second, second >= 0 && second <= 17 && second != first);
    }

    private void renderTypeIcon(ImageView icon, int type, boolean shown) {
        icon.setManaged(shown);
        icon.setVisible(shown);
        icon.setImage(shown ? moveAssetCache.computeIfAbsent(typeAssetName(type),
                ignored -> bundledImage("moves/" + typeAssetName(type))) : null);
        installTypeTooltip(icon, shown ? typeName(type) : "");
    }

    private static void installTypeTooltip(ImageView icon, String name) {
        Object stored = icon.getProperties().get("type-tooltip");
        Tooltip tooltip;
        if (stored instanceof Tooltip existing) {
            tooltip = existing;
        } else {
            tooltip = new Tooltip();
            tooltip.getStyleClass().add("type-tooltip");
            tooltip.setShowDelay(Duration.millis(450));
            Tooltip.install(icon, tooltip);
            icon.getProperties().put("type-tooltip", tooltip);
        }
        tooltip.setText(name);
    }

    private static String typeName(int type) {
        return switch (type) {
            case 0 -> "Normal"; case 1 -> "Lucha"; case 2 -> "Volador";
            case 3 -> "Veneno"; case 4 -> "Tierra"; case 5 -> "Roca";
            case 6 -> "Bicho"; case 7 -> "Fantasma"; case 8 -> "Acero";
            case 9 -> "Fuego"; case 10 -> "Agua"; case 11 -> "Planta";
            case 12 -> "Eléctrico"; case 13 -> "Psíquico"; case 14 -> "Hielo";
            case 15 -> "Dragón"; case 16 -> "Siniestro"; case 17 -> "Hada";
            default -> "";
        };
    }

    private static String typeAssetName(int type) {
        return switch (type) {
            case 1 -> "Fighting.png"; case 2 -> "Flying.png"; case 3 -> "Poison.png";
            case 4 -> "Ground.png"; case 5 -> "Rock.png"; case 6 -> "Bug.png";
            case 7 -> "Ghost.png"; case 8 -> "Steel.png"; case 9 -> "Fire.png";
            case 10 -> "Water.png"; case 11 -> "Grass.png"; case 12 -> "Electric.png";
            case 13 -> "Psychic.png"; case 14 -> "Ice.png"; case 15 -> "Dragon.png";
            case 16 -> "Dark.png"; case 17 -> "Fairy.png"; default -> "Normal.png";
        };
    }

    private static void renderAbilityAndItem(
            BattlePokemonSnapshot pokemon, Label abilityLabel, Label itemLabel,
            Tooltip abilityTooltip, Tooltip itemTooltip
    ) {
        if (pokemon.species() == 0) {
            abilityLabel.setText("Habilidad: —");
            itemLabel.setText("Objeto: —");
            abilityTooltip.setText("");
            itemTooltip.setText("");
            return;
        }
        PokemonAbilityDex.find(pokemon.ability()).ifPresentOrElse(ability -> {
            abilityLabel.setText("Habilidad: " + ability.name());
            abilityTooltip.setText(descriptionText(ability.name(), ability.description()));
        }, () -> {
            abilityLabel.setText("Habilidad: #" + pokemon.ability());
            abilityTooltip.setText("Sin descripción disponible en Gen VII.");
        });
        if (pokemon.heldItem() == 0) {
            itemLabel.setText("Objeto: Ninguno");
            itemTooltip.setText("Este Pokémon no lleva ningún objeto.");
        } else PokemonItemDex.find(pokemon.heldItem()).ifPresentOrElse(item -> {
            itemLabel.setText("Objeto: " + item.name());
            itemTooltip.setText(descriptionText(item.name(), item.description()));
        }, () -> {
            itemLabel.setText("Objeto: #" + pokemon.heldItem());
            itemTooltip.setText("Sin descripción disponible en Gen VII.");
        });
    }

    private void renderEnemyInformationVisibility(PartySnapshot[] party, BattlePokemonSnapshot player) {
        int originalAbility = originalAbilityOf(party, player);
        boolean abilityKnown = originalAbility == 36; // Rastro
        boolean itemKnown = originalAbility == 119; // Cacheo
        setKnownInformation(detailEnemyAbility, detailEnemyAbilityTooltip, abilityKnown);
        setKnownInformation(detailEnemyItem, detailEnemyItemTooltip, itemKnown);
    }

    private static int originalAbilityOf(PartySnapshot[] party, BattlePokemonSnapshot player) {
        if (player.species() == 0) return 0;
        for (PartySnapshot pokemon : party) {
            if (pokemon.species() == player.species()) return pokemon.ability();
        }
        return player.ability();
    }

    private static void setKnownInformation(Label label, Tooltip tooltip, boolean known) {
        label.setManaged(known);
        label.setVisible(known);
        if (!known) tooltip.setText("");
    }

    private static String descriptionText(String name, String description) {
        return name + "\n\n" + (description.isBlank()
                ? "Sin descripción disponible en Gen VII." : description);
    }

    private static Tooltip installDescriptionTooltip(Label label) {
        Tooltip tooltip = descriptionTooltip();
        Tooltip.install(label, tooltip);
        return tooltip;
    }

    private static Tooltip[] installItemTooltips(ImageView[] badges) {
        Tooltip[] tooltips = new Tooltip[badges.length];
        for (int index = 0; index < badges.length; index++) {
            tooltips[index] = descriptionTooltip();
            Tooltip.install(badges[index], tooltips[index]);
        }
        return tooltips;
    }

    private static Tooltip descriptionTooltip() {
        Tooltip tooltip = new Tooltip();
        tooltip.getStyleClass().add("description-tooltip");
        tooltip.setShowDelay(Duration.millis(450));
        tooltip.setHideDelay(Duration.millis(150));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(380);
        return tooltip;
    }

    private void renderDetailedSide(
            BattlePokemonSnapshot pokemon, Label name, Label meta, Label statusBadge, Label info, ImageView sprite,
            ProgressBar[] statBars, Label[] statLabels, Label[] boostBadges, Tooltip[] statTooltips,
            ImageView[] typeIcons, Label abilityLabel, Label itemLabel,
            Tooltip abilityTooltip, Tooltip itemTooltip
    ) {
        if (pokemon.species() == 0) {
            name.setText("—");
            meta.setText("Sin Pokémon activo");
            renderStatusBadge(statusBadge, "Sin estado");
            info.setText("Esperando un combate individual");
            renderTypes(typeIcons, -1, -1);
            renderAbilityAndItem(pokemon, abilityLabel, itemLabel, abilityTooltip, itemTooltip);
            for (int index = 0; index < statBars.length; index++) {
                statBars[index].setProgress(0);
                applyStatTier(statBars[index], 0);
                statLabels[index].setText(statName(index));
                statTooltips[index].setText("—");
                renderBoostBadge(boostBadges[index], 0);
            }
            sprite.setImage(null);
            return;
        }
        name.setText(speciesName(pokemon.species()));
        meta.setText("#%04d  ·  Nv. %d".formatted(pokemon.species(), pokemon.level()));
        renderStatusBadge(statusBadge, pokemon.status());
        int[] baseStats = PokemonBaseStats.forSpecies(pokemon.species());
        for (int index = 0; index < statBars.length; index++) {
            int value = baseStats[index];
            statBars[index].setProgress(Math.min(value, 256) / 256.0);
            applyStatTier(statBars[index], value);
            statLabels[index].setText(statName(index));
            statTooltips[index].setText(Integer.toString(value));
            renderBoostBadge(boostBadges[index], pokemon.boosts()[index]);
        }
        info.setText("");
        renderTypes(typeIcons, pokemon.typeOne(), pokemon.typeTwo());
        renderAbilityAndItem(pokemon, abilityLabel, itemLabel, abilityTooltip, itemTooltip);
        spriteCache.load(pokemon.species()).thenAccept(image ->
                Platform.runLater(() -> sprite.setImage(image.orElse(null))));
    }

    private static Tooltip[] installStatTooltips(ProgressBar[] bars) {
        Tooltip[] tooltips = new Tooltip[bars.length];
        for (int index = 0; index < bars.length; index++) {
            tooltips[index] = new Tooltip(statName(index) + ": —");
            tooltips[index].setShowDelay(Duration.seconds(1.2));
            tooltips[index].setHideDelay(Duration.millis(150));
            Tooltip.install(bars[index], tooltips[index]);
        }
        return tooltips;
    }

    private static void applyStatTier(ProgressBar bar, int value) {
        bar.getStyleClass().removeAll(STAT_TIER_CLASSES);
        int tier = value < 50 ? 0
                : value < 80 ? 1
                : value < 110 ? 2
                : value < 140 ? 3
                : value < 180 ? 4
                : value < 220 ? 5
                : 6;
        bar.getStyleClass().add(STAT_TIER_CLASSES[tier]);
    }

    private static Image bundledImage(String filename) {
        return new Image(MainController.class.getResource(
                "/net/paramada/pokemada/assets/" + filename).toExternalForm());
    }

    private static void renderBoostBadge(Label badge, int rawStage) {
        int stage = Math.max(-6, Math.min(6, rawStage));
        boolean boosted = stage != 0;
        badge.setManaged(boosted);
        badge.setVisible(boosted);
        badge.setText(stage > 0 ? "+" + stage : Integer.toString(stage));
        badge.getStyleClass().removeAll("positive", "negative");
        if (boosted) badge.getStyleClass().add(stage > 0 ? "positive" : "negative");
    }

    private static void renderStatusBadge(Label badge, String status) {
        badge.getStyleClass().removeAll("paralyzed", "asleep", "frozen", "burned", "poisoned");
        String style = switch (status) {
            case "PAR" -> "paralyzed";
            case "DOR" -> "asleep";
            case "CON" -> "frozen";
            case "QUE" -> "burned";
            case "ENV" -> "poisoned";
            default -> null;
        };
        badge.setManaged(style != null);
        badge.setVisible(style != null);
        if (style == null) {
            badge.setText("");
            return;
        }
        badge.getStyleClass().add(style);
        badge.setText(switch (status) {
            case "PAR" -> "PARALIZADO";
            case "DOR" -> "DORMIDO";
            case "CON" -> "CONGELADO";
            case "QUE" -> "QUEMADO";
            default -> "ENVENENADO";
        });
    }

    private static String statName(int index) {
        return switch (index) {
            case 0 -> "Ataque";
            case 1 -> "Defensa";
            case 2 -> "At. Esp.";
            case 3 -> "Def. Esp.";
            default -> "Velocidad";
        };
    }

    private static String statsText(BattlePokemonSnapshot pokemon) {
        int[] stats = pokemon.stats();
        return "ATQ %d   DEF %d   A.ESP %d   D.ESP %d   VEL %d"
                .formatted(stats[0], stats[1], stats[2], stats[3], stats[4]);
    }

    private static String boostsText(BattlePokemonSnapshot pokemon) {
        int[] boosts = pokemon.boosts();
        return "Boosts: ATQ %s   DEF %s   A.ESP %s   D.ESP %s   VEL %s"
                .formatted(stage(boosts[0]), stage(boosts[1]), stage(boosts[2]),
                        stage(boosts[3]), stage(boosts[4]));
    }

    private static String stage(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
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
        emulatorConnectionStatus.setText(text);
        emulatorConnectionStatus.getStyleClass().removeAll("connected", "error");
        emulatorConnectionRow.getStyleClass().removeAll("connected", "error");
        if (stateClass != null) {
            emulatorConnectionStatus.getStyleClass().add(stateClass);
            emulatorConnectionRow.getStyleClass().add(stateClass);
        }
        emulatorConnectionIcon.setImage("connected".equals(stateClass)
                ? emulatorConnectedIcon : emulatorDisconnectedIcon);
    }

    private static String activeName(int first, int second) {
        if (first == 0 && second == 0) {
            return "—";
        }
        if (second == 0) {
            return speciesName(first);
        }
        return speciesName(first) + " + " + speciesName(second);
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

    private record PartySnapshot(int species, String nickname, int level, int currentHp, int maxHp,
                                 int heldItem, int ability, int nature, int[] realStats, int[] moves,
                                 int status, int[] currentPp, int[] maxPp) {
        private static PartySnapshot empty() {
            return new PartySnapshot(0, "", 0, 0, 0, 0, 0, 0, new int[6], new int[4],
                    0, new int[4], new int[4]);
        }
    }

    private record ActiveSnapshot(int playerOne, int enemyOne, int playerTwo, int enemyTwo) {
    }

    private record BattleSnapshot(BattlePokemonSnapshot player, BattlePokemonSnapshot enemy,
                                  BattlePokemonSnapshot[] playerTeam, BattlePokemonSnapshot[] enemyTeam,
                                  String battleText) {
        private static BattleSnapshot empty() {
            BattlePokemonSnapshot[] playerTeam = new BattlePokemonSnapshot[6];
            BattlePokemonSnapshot[] enemyTeam = new BattlePokemonSnapshot[6];
            Arrays.fill(playerTeam, BattlePokemonSnapshot.empty());
            Arrays.fill(enemyTeam, BattlePokemonSnapshot.empty());
            return new BattleSnapshot(BattlePokemonSnapshot.empty(), BattlePokemonSnapshot.empty(),
                    playerTeam, enemyTeam, "");
        }
    }

    private record BattlePokemonSnapshot(
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
