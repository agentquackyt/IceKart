package de.agentquack.icekart.client.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.agentquack.icekart.client.IcekartClient;
import de.agentquack.icekart.client.command.RacerManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client for communicating with the IceKart WebSocket API.
 */
public class WebSocketClient implements WebSocket.Listener {

    private static final String DEFAULT_WS_URL = "ws://localhost:3000/ws";
    private static final Gson GSON = new Gson();

    private static WebSocketClient instance;

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final StringBuilder messageBuffer = new StringBuilder();

    // Race status tracking: "idle", "racing", "stopped"
    private volatile String raceStatus = "idle";

    private WebSocketClient() {
    }

    public static WebSocketClient getInstance() {
        if (instance == null) {
            instance = new WebSocketClient();
        }
        return instance;
    }

    public CompletableFuture<Void> connect() {
        return connect(DEFAULT_WS_URL);
    }

    public CompletableFuture<Void> connect(String url) {
        if (connected.get()) {
            IcekartClient.LOGGER.info("[IceKart] Already connected");
            return CompletableFuture.completedFuture(null);
        }

        IcekartClient.LOGGER.info("[IceKart] Attempting to connect to {}", url);
        HttpClient client = HttpClient.newHttpClient();
        return client.newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    connected.set(true);
                    IcekartClient.LOGGER.info("[IceKart] WebSocket connected to {}", url);
                })
                .exceptionally(ex -> {
                    IcekartClient.LOGGER.error("[IceKart] WebSocket connection failed: {}", ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    public void disconnect() {
        if (webSocket != null && connected.get()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
            connected.set(false);
            IcekartClient.LOGGER.info("[IceKart] WebSocket disconnected");
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Get the current race status
     * @return "idle", "racing", or "stopped"
     */
    public String getRaceStatus() {
        return raceStatus;
    }

    /**
     * Check if a race is currently running
     */
    public boolean isRacing() {
        return "racing".equals(raceStatus);
    }

    // --- Send Messages ---

    /**
     * Send a race action (start, stop, reset)
     */
    public void sendAction(String action) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "action");
        msg.addProperty("payload", action);
        sendMessage(msg);
    }

    /**
     * Trigger a lap completion for a racer
     */
    public void sendLap(String racerId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "lap");
        msg.addProperty("racerId", racerId);
        sendMessage(msg);
    }

    /**
     * Trigger a checkpoint for a racer
     */
    public void sendCheckpoint(String racerId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "checkpoint");
        msg.addProperty("racerId", racerId);
        sendMessage(msg);
    }

    /**
     * Toggle disqualification for a racer
     */
    public void sendDisqualify(String racerId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "disqualify");
        msg.addProperty("racerId", racerId);
        sendMessage(msg);
    }

    /**
     * Register a new racer with the server
     */
    public void sendRegister(String name) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "register");
        msg.addProperty("name", name);
        sendMessage(msg);
    }

    /**
     * Remove a racer from the game by name
     */
    public void sendRemove(String name) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "remove");
        msg.addProperty("name", name);
        sendMessage(msg);
    }

    private void sendMessage(JsonObject msg) {
        if (!connected.get()) {
            IcekartClient.LOGGER.warn("[IceKart] Cannot send message - not connected (connected=false)");
            return;
        }
        if (webSocket == null) {
            IcekartClient.LOGGER.warn("[IceKart] Cannot send message - webSocket is null");
            return;
        }

        String json = GSON.toJson(msg);
        IcekartClient.LOGGER.info("[IceKart] Sending WebSocket message: {}", json);
        try {
            webSocket.sendText(json, true);
            IcekartClient.LOGGER.info("[IceKart] WebSocket message sent successfully");
        } catch (Exception e) {
            IcekartClient.LOGGER.error("[IceKart] Failed to send WebSocket message: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    // --- WebSocket.Listener Implementation ---

    @Override
    public void onOpen(WebSocket webSocket) {
        IcekartClient.LOGGER.info("[IceKart] WebSocket connection opened");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);
            handleMessage(message);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected.set(false);
        IcekartClient.LOGGER.info("[IceKart] WebSocket closed: {} - {}", statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected.set(false);
        IcekartClient.LOGGER.error("[IceKart] WebSocket error: {}", error.getMessage());
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String type = json.has("type") ? json.get("type").getAsString() : "unknown";

            switch (type) {
                case "init":
                    handleInitOrUpdate(json, "init");
                    break;
                case "update":
                    handleInitOrUpdate(json, "update");
                    break;
                case "status":
                    String newStatus = json.has("status") ? json.get("status").getAsString() : "unknown";
                    raceStatus = newStatus;
                    IcekartClient.LOGGER.info("[IceKart] Race status changed: {}", newStatus);
                    break;
                default:
                    IcekartClient.LOGGER.debug("[IceKart] Received unknown message type: {}", type);
            }
        } catch (Exception e) {
            IcekartClient.LOGGER.error("[IceKart] Error parsing message: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle init and update messages - parse racers and sync with RacerManager
     */
    private void handleInitOrUpdate(JsonObject json, String eventType) {
        String status = json.has("status") ? json.get("status").getAsString() : "unknown";

        // Update race status from init event
        if ("init".equals(eventType) && json.has("status")) {
            raceStatus = status;
        }

        IcekartClient.LOGGER.info("[IceKart] Received {}: status={}", eventType, status);

        if (json.has("racers") && json.get("racers").isJsonArray()) {
            JsonArray racersArray = json.getAsJsonArray("racers");
            RacerManager racerManager = RacerManager.getInstance();

            for (JsonElement element : racersArray) {
                if (element.isJsonObject()) {
                    JsonObject racer = element.getAsJsonObject();
                    String id = racer.has("id") ? racer.get("id").getAsString() : null;
                    String name = racer.has("name") ? racer.get("name").getAsString() : null;

                    if (id != null && name != null) {
                        racerManager.updateRacerFromServer(id, name);
                        IcekartClient.LOGGER.debug("[IceKart] Synced racer: {} (ID: {})", name, id);
                    }
                }
            }
            IcekartClient.LOGGER.info("[IceKart] Synced {} racers from server", racersArray.size());
        }
    }
}

