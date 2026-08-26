package net.paramada.pokemada.server;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Keeps the legacy data WebSocket connected and exposes user-facing notification events. */
public final class NotificationConnection implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(NotificationConnection.class.getName());
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pokemada-notifications");
        thread.setDaemon(true);
        return thread;
    });
    private final URI socketUri;
    private final String origin;
    private final Consumer<Event> eventConsumer;
    private final AtomicBoolean connecting = new AtomicBoolean();
    private volatile boolean closed;
    private volatile WebSocket socket;

    public NotificationConnection(String baseUrl, String username, Consumer<Event> eventConsumer) {
        URI baseUri = URI.create(ServerSettings.normalizeBaseUrl(baseUrl));
        this.socketUri = socketUri(baseUri, username);
        this.origin = origin(baseUri);
        this.eventConsumer = eventConsumer;
    }

    public void start() {
        connect();
    }

    private void connect() {
        if (closed || socket != null || !connecting.compareAndSet(false, true)) return;
        try {
            http.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT).header("Origin", origin)
                    .buildAsync(socketUri, new Listener())
                    .whenComplete((connected, failure) -> {
                        connecting.set(false);
                        if (failure != null) {
                            if (handshakeStatus(failure) == 403) {
                                LOGGER.log(System.Logger.Level.INFO,
                                        "Real-time notifications are unavailable (HTTP 403); use Reconectar to retry");
                                return;
                            }
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Notification WebSocket connection failed; retrying in the background", failure);
                            reconnectLater();
                        } else if (closed) {
                            connected.abort();
                        } else {
                            socket = connected;
                        }
                    });
        } catch (RuntimeException failure) {
            connecting.set(false);
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not create notification WebSocket; retrying in the background", failure);
            reconnectLater();
        }
    }

    private static int handshakeStatus(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof WebSocketHandshakeException handshake) {
                return handshake.getResponse().statusCode();
            }
            cause = cause.getCause();
        }
        return 0;
    }

    private void reconnectLater() {
        if (closed) return;
        try {
            scheduler.schedule(this::connect, 3, TimeUnit.SECONDS);
        } catch (RejectedExecutionException rejected) {
            if (!closed) LOGGER.log(System.Logger.Level.WARNING,
                    "Notification WebSocket reconnect could not be scheduled", rejected);
        }
    }

    @Override
    public void close() {
        closed = true;
        WebSocket current = socket;
        socket = null;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "logout");
        scheduler.shutdownNow();
    }

    static URI socketUri(String baseUrl, String username) {
        return socketUri(URI.create(ServerSettings.normalizeBaseUrl(baseUrl)), username);
    }

    private static URI socketUri(URI base, String username) {
        String scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        String room = URLEncoder.encode(username == null ? "" : username.trim(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return URI.create(scheme + "://" + base.getAuthority() + base.getPath() + "ws/data/" + room);
    }

    static String origin(String baseUrl) {
        return origin(URI.create(ServerSettings.normalizeBaseUrl(baseUrl)));
    }

    private static String origin(URI base) {
        return base.getScheme().toLowerCase() + "://" + base.getAuthority();
    }

    static Event decode(String payload) {
        IncomingMessage incoming = decodeIncoming(payload);
        if (incoming == null || !incoming.type().contains("notification")) return null;
        if (incoming.data() == null || String.valueOf(incoming.data()).isBlank()) return null;
        return new Event(incoming.type(), String.valueOf(incoming.data()));
    }

    static IncomingMessage decodeIncoming(String payload) {
        Object parsed = Json.parse(payload);
        if (!(parsed instanceof Map<?, ?> outer)) return null;
        Object message = outer.get("message");
        Object inner = message instanceof String string ? Json.parse(string) : message;
        if (!(inner instanceof Map<?, ?> data)) return null;
        Object rawType = data.get("type");
        String type = rawType == null ? "" : String.valueOf(rawType);
        Object content = data.get("data");
        if (content == null) content = data.get("message");
        return new IncomingMessage(type.isBlank() ? "<sin tipo>" : type, content);
    }

    public record Event(String type, String message) { }

    record IncomingMessage(String type, Object data) { }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder fragments = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            LOGGER.log(System.Logger.Level.INFO, "Notification WebSocket connected to {0}", socketUri);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                try {
                    String payload = fragments.toString();
                    IncomingMessage incoming = decodeIncoming(payload);
                    if (incoming == null) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Received unrecognized server WebSocket message: {0}", payload);
                    } else {
                        LOGGER.log(System.Logger.Level.INFO,
                                "Received server WebSocket message: type={0}, data={1}",
                                incoming.type(), incoming.data());
                    }
                    Event event = decode(payload);
                    if (event != null) eventConsumer.accept(event);
                } catch (RuntimeException malformed) {
                    LOGGER.log(System.Logger.Level.WARNING, "Ignored malformed notification event", malformed);
                } finally {
                    fragments.setLength(0);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            reconnectLater();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket = null;
            LOGGER.log(System.Logger.Level.WARNING, "Notification WebSocket disconnected", error);
            reconnectLater();
        }
    }
}
