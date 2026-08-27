package net.paramada.pokemada.server;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Typed client for the small subset of the private PokeMada API used by the desktop app. */
public final class ServerClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient http;
    private final URI baseUri;

    public ServerClient(String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build(),
                URI.create(ServerSettings.normalizeBaseUrl(baseUrl)));
    }

    ServerClient(HttpClient http, URI baseUri) {
        this.http = http;
        this.baseUri = baseUri;
    }

    public CompletableFuture<String> login(String username, String password) {
        String form = "username=" + encode(username) + "&password=" + encode(password);
        HttpRequest request = HttpRequest.newBuilder(endpoint("user/login/"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return send(request).thenApply(body -> text(object(Json.parse(body)).get("token"), "token"));
    }

    public CompletableFuture<Trainer> trainer(String token) {
        return get("api/trainers/get_trainer/", token).thenApply(ServerClient::trainerFromBody);
    }

    public CompletableFuture<List<Trainer>> trainers(String token) {
        return get("api/trainers/list_trainers/", token).thenApply(body -> array(Json.parse(body)).stream()
                .map(ServerClient::trainerFromJson).toList());
    }

    public CompletableFuture<List<BoxSummary>> boxes(String token, int trainerId) {
        return get("api/trainers/" + trainerId + "/list_boxes/", token).thenApply(body ->
                array(Json.parse(body)).stream().map(value -> {
                    Map<String, Object> box = object(value);
                    return new BoxSummary(integer(box.get("box_number")),
                            textOr(box.get("box_identifier"), "Caja " + (integer(box.get("box_number")) + 1)));
                }).toList());
    }

    public CompletableFuture<Box> box(String token, int trainerId, int boxNumber) {
        return get("api/trainers/" + trainerId + "/box/?box=" + boxNumber, token).thenApply(body -> {
            Map<String, Object> box = object(Json.parse(body));
            List<BoxSlot> slots = arrayOrEmpty(box.get("slots")).stream().map(value -> {
                Map<String, Object> slot = object(value);
                Object rawPokemon = slot.get("pokemon");
                return new BoxSlot(integer(slot.get("slot")), rawPokemon == null ? null : pokemon(object(rawPokemon)));
            }).toList();
            return new Box(integer(box.get("box_number")),
                    textOr(box.get("box_identifier"), "Caja " + (integer(box.get("box_number")) + 1)), slots);
        });
    }

    public CompletableFuture<List<Pokemon>> team(String token, int trainerId) {
        return get("api/trainers/" + trainerId + "/", token).thenApply(body -> {
            Map<String, Object> trainer = object(Json.parse(body));
            Object rawTeam = trainer.get("current_team");
            if (!(rawTeam instanceof Map<?, ?>)) return List.of();
            return arrayOrEmpty(object(rawTeam).get("team")).stream()
                    .map(value -> pokemon(object(value))).toList();
        });
    }

    public CompletableFuture<Profile> profile(String token) {
        return get("api/trainers/get_profile/", token).thenApply(body -> {
            Map<String, Object> json = object(Json.parse(body));
            String picture = nullableText(json.get("web_picture"));
            if (picture != null && !picture.isBlank()) picture = endpoint(picture).toString();
            return new Profile(textOr(json.get("name"), "Entrenador"), picture);
        });
    }

    public CompletableFuture<byte[]> image(String url, String token) {
        HttpRequest request = authenticated(url, token).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            throw new ServerException(response.statusCode(), "");
        });
    }

    public CompletableFuture<CatalogResponse> gameDataCatalog(String token, String etag) {
        HttpRequest.Builder builder = authenticated("api/game-data/catalog/", token).GET();
        if (etag != null && !etag.isBlank()) builder.header("If-None-Match", etag);
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    String responseEtag = response.headers().firstValue("ETag").orElse(etag == null ? "" : etag);
                    if (response.statusCode() == 304) return new CatalogResponse(false, responseEtag, "");
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return new CatalogResponse(true, responseEtag, response.body());
                    }
                    throw new ServerException(response.statusCode(), response.body());
                });
    }

    static Trainer trainerFromBody(String body) {
        if (body == null || body.isBlank() || "null".equalsIgnoreCase(body.trim())) {
            return new Trainer(0, "Entrenador");
        }
        return trainerFromJson(Json.parse(body));
    }

    private static Trainer trainerFromJson(Object value) {
        Map<String, Object> json = object(value);
        return new Trainer(integer(json.get("id")), textOr(json.get("name"), "Entrenador"));
    }

    private static Pokemon pokemon(Map<String, Object> json) {
        int[] stats = {integer(json.get("max_hp")), integer(json.get("attack")), integer(json.get("defense")),
                integer(json.get("special_attack")), integer(json.get("special_defense")), integer(json.get("speed"))};
        int[] moves = arrayOrEmpty(json.get("moves")).stream().limit(4).mapToInt(value ->
                value instanceof Map<?, ?> ? integer(object(value).get("index")) : integer(value)).toArray();
        return new Pokemon(integer(json.get("id")), integer(json.get("dex_number")), textOr(json.get("form"), "0"), textOr(json.get("mote"),
                textOr(json.get("species"), "Pokémon")), integer(json.get("level")),
                integer(json.get("cur_hp")), integer(json.get("max_hp")), textOr(json.get("nature_name"), "Desconocida"),
                integer(json.get("ability")), integer(json.get("held_item")), stats, moves);
    }

    public CompletableFuture<List<RewardBundle>> rewards(String token) {
        return get("api/trainers/get_rewards/", token).thenApply(body -> {
            List<RewardBundle> result = new ArrayList<>();
            for (Object element : array(Json.parse(body))) {
                Map<String, Object> bundle = object(element);
                List<RewardItem> items = new ArrayList<>();
                for (Object rawReward : arrayOrEmpty(bundle.get("rewards"))) {
                    Map<String, Object> reward = object(rawReward);
                    items.add(new RewardItem(integer(reward.get("reward_type")),
                            integer(reward.get("quantity")), nullableText(reward.get("pokemon_pid")),
                            nullableText(reward.get("item")), nullableText(reward.get("wildcard"))));
                }
                result.add(new RewardBundle(text(bundle.get("id"), "id"),
                        textOr(bundle.get("name"), "Paquete sin título"),
                        textOr(bundle.get("description"), ""), textOr(bundle.get("sender"), "Evento"),
                        integer(bundle.get("type")), List.copyOf(items)));
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<List<Notification>> notifications(String token) {
        return get("api/notifications/", token).thenApply(ServerClient::notificationsFromBody);
    }

    static List<Notification> notificationsFromBody(String body) {
        return array(Json.parse(body)).stream()
                .map(value -> {
                    Map<String, Object> json = object(value);
                    return new Notification(longInteger(json.get("id")),
                            textOr(json.get("message"), "Notificación"),
                            instantOrNull(json.get("created_at")));
                }).toList();
    }

    public CompletableFuture<Void> claim(String token, String rewardId) {
        HttpRequest request = authenticated("api/trainers/claim_reward/" + rewardId + "/", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        return send(request).thenApply(ignored -> null);
    }

    public CompletableFuture<List<BoosterPackSummary>> boosterPacks(String token) {
        return get("api/booster-packs/", token).thenApply(ServerClient::boosterPacksFromBody);
    }

    static List<BoosterPackSummary> boosterPacksFromBody(String body) {
        return array(Json.parse(body)).stream().map(value -> boosterPackSummary(object(value))).toList();
    }

    public CompletableFuture<BoosterPackDetail> boosterPack(String token, String code) {
        return get("api/booster-packs/" + encode(code) + "/", token)
                .thenApply(ServerClient::boosterPackDetailFromBody);
    }

    static BoosterPackDetail boosterPackDetailFromBody(String body) {
        Map<String, Object> json = object(Json.parse(body));
        BoosterPackSummary summary = boosterPackSummary(json);
        List<PackOddsSlot> slots = arrayOrEmpty(json.get("slots")).stream().map(value -> {
            Map<String, Object> slot = object(value);
            List<PackOddsEntry> entries = arrayOrEmpty(slot.get("entries")).stream().map(raw -> {
                Map<String, Object> entry = object(raw);
                return new PackOddsEntry(textOr(entry.get("name"), "Recompensa"),
                        textOr(entry.get("rarity"), "COMMON"), nullableText(entry.get("image_url")),
                        decimal(entry.get("probability")));
            }).toList();
            return new PackOddsSlot(integer(slot.get("position")), textOr(slot.get("label"), ""),
                    textOr(slot.get("pool"), ""), entries);
        }).toList();
        return new BoosterPackDetail(summary, slots);
    }

    public CompletableFuture<PackOpening> openBoosterPack(String token, String code, UUID idempotencyKey) {
        HttpRequest request = authenticated("api/booster-packs/" + encode(code) + "/open/", token)
                .header("Idempotency-Key", idempotencyKey.toString())
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        return send(request).thenApply(ServerClient::packOpeningFromBody);
    }

    public CompletableFuture<List<PackOpening>> packOpenings(String token) {
        return get("api/booster-packs/openings/", token).thenApply(body ->
                array(Json.parse(body)).stream().map(value -> packOpening(object(value))).toList());
    }

    static PackOpening packOpeningFromBody(String body) {
        return packOpening(object(Json.parse(body)));
    }

    private static BoosterPackSummary boosterPackSummary(Map<String, Object> json) {
        return new BoosterPackSummary(text(json.get("code"), "code"), textOr(json.get("name"), "Sobre"),
                textOr(json.get("description"), ""), nullableText(json.get("art_url")),
                integer(json.get("quantity")), integer(json.get("cards_per_pack")),
                textOr(json.get("guarantee_label"), ""), integer(json.get("configuration_version")));
    }

    private static PackOpening packOpening(Map<String, Object> json) {
        List<PackOpeningResult> results = arrayOrEmpty(json.get("results")).stream().map(value -> {
            Map<String, Object> result = object(value);
            return new PackOpeningResult(integer(result.get("position")),
                    textOr(result.get("name"), "Recompensa"), textOr(result.get("rarity"), "COMMON"),
                    nullableText(result.get("image_url")));
        }).toList();
        return new PackOpening(UUID.fromString(text(json.get("id"), "id")),
                text(json.get("pack_code"), "pack_code"), textOr(json.get("pack_name"), "Sobre"),
                textOr(json.get("state"), "COMPLETED"), integer(json.get("remaining_quantity")),
                nullableText(json.get("reward_bundle_id")), instantOrNull(json.get("created_at")),
                results, Boolean.TRUE.equals(json.get("replayed")));
    }

    public CompletableFuture<List<VirtualItemStack>> virtualInventory(String token) {
        return get("api/virtual-inventory/", token).thenApply(ServerClient::virtualInventoryFromBody);
    }

    static List<VirtualItemStack> virtualInventoryFromBody(String body) {
        return array(Json.parse(body)).stream().map(value -> {
            Map<String, Object> json = object(value);
            Map<String, Object> inputs = object(json.get("required_inputs"));
            return new VirtualItemStack(text(json.get("code"), "code"), textOr(json.get("name"), "Objeto"),
                    textOr(json.get("description"), ""), nullableText(json.get("sprite")),
                    textOr(json.get("pocket"), "event"),
                    integer(json.get("quantity")),
                    integer(json.get("reserved_quantity")), integer(json.get("available_quantity")),
                    Boolean.TRUE.equals(inputs.get("target_profile")),
                    Boolean.TRUE.equals(inputs.get("target_pokemon")),
                    !Boolean.FALSE.equals(json.get("directly_usable")),
                    arrayOrEmpty(json.get("client_capabilities")).stream().map(String::valueOf).toList());
        }).toList();
    }

    public CompletableFuture<ActionOperation> useVirtualItem(String token, String code, int partySlot,
                                                              int species, int quantity, UUID idempotencyKey) {
        String body = "{\"quantity\":" + quantity
                + (partySlot >= 0 ? ",\"target_party_slot\":" + partySlot
                + ",\"target_species\":" + species : "") + "}";
        HttpRequest request = authenticated("api/virtual-inventory/" + code + "/use/", token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(request).thenApply(ServerClient::operationFromBody);
    }

    public CompletableFuture<List<ClientCommand>> pendingVirtualItemCommands(String token) {
        return get("api/virtual-inventory/commands/pending/", token)
                .thenApply(ServerClient::commandsFromBody);
    }

    public CompletableFuture<ClientCommand> acknowledgeVirtualItemCommand(String token, UUID commandId,
                                                                            boolean succeeded, String detail) {
        String body = "{\"succeeded\":" + succeeded + ",\"result\":{\"detail\":\""
                + jsonEscape(detail == null ? "" : detail) + "\"}}";
        HttpRequest request = authenticated("api/virtual-inventory/commands/" + commandId + "/ack/", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(request).thenApply(value -> command(object(Json.parse(value))));
    }

    static ActionOperation operationFromBody(String body) {
        Map<String, Object> json = object(Json.parse(body));
        return new ActionOperation(UUID.fromString(text(json.get("id"), "id")),
                text(json.get("state"), "state"), commands(arrayOrEmpty(json.get("commands"))));
    }

    static List<ClientCommand> commandsFromBody(String body) {
        return commands(array(Json.parse(body)));
    }

    private static List<ClientCommand> commands(List<Object> values) {
        return values.stream().map(value -> command(object(value))).toList();
    }

    private static ClientCommand command(Map<String, Object> json) {
        return new ClientCommand(UUID.fromString(text(json.get("id"), "id")),
                UUID.fromString(text(json.get("operation_id"), "operation_id")),
                text(json.get("capability"), "capability"), object(json.get("payload")),
                text(json.get("state"), "state"));
    }

    public CompletableFuture<Void> uploadSave(String token, String path, String fileName, byte[] contents) {
        if (contents == null || contents.length == 0) throw new IllegalArgumentException("save is empty");
        String boundary = "PokeMada-" + java.util.UUID.randomUUID();
        byte[] opening = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName(fileName) + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] closing = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = authenticated(path, token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(opening),
                        HttpRequest.BodyPublishers.ofByteArray(contents),
                        HttpRequest.BodyPublishers.ofByteArray(closing)))
                .build();
        return send(request).thenApply(ignored -> null);
    }

    private CompletableFuture<String> get(String path, String token) {
        return send(authenticated(path, token).GET().build());
    }

    private HttpRequest.Builder authenticated(String path, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("missing server token");
        return HttpRequest.newBuilder(endpoint(path)).timeout(TIMEOUT)
                .header("Accept", "application/json").header("Authorization", "Token " + token.trim());
    }

    private CompletableFuture<String> send(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
                    throw new ServerException(response.statusCode(), response.body());
                });
    }

    private URI endpoint(String path) {
        return baseUri.resolve(path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String safeFileName(String value) {
        String name = value == null ? "main" : value.replace('\\', '_').replace('/', '_')
                .replace("\r", "_").replace("\n", "_").replace("\"", "_");
        return name.isBlank() ? "main" : name;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("expected JSON object");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        throw new IllegalArgumentException("expected JSON array");
    }

    private static List<Object> arrayOrEmpty(Object value) {
        return value == null ? List.of() : array(value);
    }

    private static String text(Object value, String field) {
        if (value instanceof String string && !string.isBlank()) return string;
        throw new IllegalArgumentException("missing JSON field " + field);
    }

    private static String textOr(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private static String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longInteger(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static Instant instantOrNull(Object value) {
        if (!(value instanceof String string) || string.isBlank()) return null;
        try {
            return Instant.parse(string);
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }

    public record Trainer(int id, String name) { }

    public record BoxSummary(int number, String name) { }

    public record Pokemon(int id, int dexNumber, String form, String name, int level, int currentHp, int maxHp,
                          String natureName, int ability, int heldItem, int[] stats, int[] moves) { }

    public record BoxSlot(int slot, Pokemon pokemon) { }

    public record Box(int number, String name, List<BoxSlot> slots) { }

    public record Profile(String name, String pictureUrl) { }

    public record CatalogResponse(boolean modified, String etag, String body) { }

    public record Notification(long id, String message, Instant createdAt) { }

    public record RewardBundle(String id, String name, String description, String sender, int type,
                               List<RewardItem> rewards) { }

    public record RewardItem(int type, int quantity, String pokemonPid, String itemId, String wildcardId) { }

    public record BoosterPackSummary(String code, String name, String description, String artUrl,
                                     int quantity, int cardsPerPack, String guaranteeLabel,
                                     int configurationVersion) { }

    public record BoosterPackDetail(BoosterPackSummary summary, List<PackOddsSlot> slots) { }

    public record PackOddsSlot(int position, String label, String pool, List<PackOddsEntry> entries) { }

    public record PackOddsEntry(String name, String rarity, String imageUrl, double probability) { }

    public record PackOpening(UUID id, String packCode, String packName, String state,
                              int remainingQuantity, String rewardBundleId, Instant createdAt,
                              List<PackOpeningResult> results, boolean replayed) { }

    public record PackOpeningResult(int position, String name, String rarity, String imageUrl) { }

    public record VirtualItemStack(String code, String name, String description, String spriteUrl,
                                   String pocket, int quantity,
                                   int reservedQuantity, int availableQuantity, boolean requiresTargetProfile,
                                   boolean requiresTargetPokemon, boolean directlyUsable,
                                   List<String> clientCapabilities) { }

    public record ActionOperation(UUID id, String state, List<ClientCommand> commands) { }

    public record ClientCommand(UUID id, UUID operationId, String capability, Map<String, Object> payload,
                                String state) { }

    public static final class ServerException extends RuntimeException {
        private final int statusCode;

        public ServerException(int statusCode, String responseBody) {
            super("request failed with HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
