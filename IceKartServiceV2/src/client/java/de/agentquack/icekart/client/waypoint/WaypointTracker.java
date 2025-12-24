package de.agentquack.icekart.client.waypoint;

import de.agentquack.icekart.client.IcekartClient;
import de.agentquack.icekart.client.command.RacerManager;
import de.agentquack.icekart.client.websocket.WebSocketClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players passing through waypoints.
 * Uses position-based detection instead of block-based, avoiding distance limitations.
 */
public class WaypointTracker {

    private static WaypointTracker instance;

    // Track which waypoint each player was last inside (to detect entry)
    // Key: playerName, Value: waypointId (or null if not inside any)
    private final Map<String, String> playerCurrentWaypoint = new ConcurrentHashMap<>();

    // Track the last waypoint each player passed through (for sequence validation)
    // Key: playerName, Value: last passed waypoint order
    private final Map<String, Integer> playerLastWaypointOrder = new ConcurrentHashMap<>();

    // Cooldown to prevent rapid duplicate triggers
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 500;

    private WaypointTracker() {
    }

    public static WaypointTracker getInstance() {
        if (instance == null) {
            instance = new WaypointTracker();
        }
        return instance;
    }

    /**
     * Called every tick to check player positions against waypoints.
     * This method should be called from the client tick event.
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        WaypointManager waypointManager = WaypointManager.getInstance();
        List<Waypoint> waypoints = waypointManager.getOrderedWaypoints();
        if (waypoints.isEmpty()) {
            return;
        }

        RacerManager racerManager = RacerManager.getInstance();
        ClientWorld world = client.world;

        // Check all players in vehicles (or on foot) for waypoint triggers
        Set<String> checkedPlayers = new HashSet<>();

        // Check entities in the world
        for (Entity entity : world.getEntities()) {
            checkEntityForWaypoint(entity, waypoints, racerManager, checkedPlayers);
        }

        // Clean up tracking for players no longer in world
        playerCurrentWaypoint.keySet().removeIf(name -> !checkedPlayers.contains(name));
    }

    private void checkEntityForWaypoint(Entity entity, List<Waypoint> waypoints,
                                         RacerManager racerManager, Set<String> checkedPlayers) {
        // Get players from entity (direct or as passenger)
        List<PlayerEntity> players = getPlayersFromEntity(entity);

        for (PlayerEntity player : players) {
            String playerName = player.getName().getString();

            // Skip if already checked this tick
            if (checkedPlayers.contains(playerName)) {
                continue;
            }
            checkedPlayers.add(playerName);

            // Skip if not a registered racer
            if (!racerManager.isRacer(playerName)) {
                continue;
            }

            // IMPORTANT: Only trigger checkpoints when player is in a vehicle (boat, minecart, etc.)
            // This prevents false triggers when walking on foot
            if (!player.hasVehicle() || player.getVehicle() == null) {
                // Player is not in a vehicle, clear their waypoint tracking and skip
                playerCurrentWaypoint.remove(playerName);
                continue;
            }

            // Use vehicle position for checkpoint detection
            Vec3d checkPos = player.getVehicle().getPos();

            // Find which waypoint the player is currently inside
            Waypoint currentWaypoint = null;
            for (Waypoint wp : waypoints) {
                if (wp.contains(checkPos)) {
                    currentWaypoint = wp;
                    break;
                }
            }

            String previousWaypointId = playerCurrentWaypoint.get(playerName);
            String currentWaypointId = currentWaypoint != null ? currentWaypoint.getId() : null;

            // Detect entry into a new waypoint
            if (currentWaypointId != null && !currentWaypointId.equals(previousWaypointId)) {
                // Player just entered this waypoint
                onPlayerEnteredWaypoint(player, currentWaypoint);
            }

            // Update tracking
            if (currentWaypointId != null) {
                playerCurrentWaypoint.put(playerName, currentWaypointId);
            } else {
                playerCurrentWaypoint.remove(playerName);
            }
        }
    }

    private List<PlayerEntity> getPlayersFromEntity(Entity entity) {
        List<PlayerEntity> players = new ArrayList<>();

        // Direct player
        if (entity instanceof PlayerEntity player) {
            players.add(player);
        }

        // Check passengers (for boats, minecarts, etc.)
        if (entity instanceof BoatEntity || entity instanceof AbstractMinecartEntity || entity.hasPassengers()) {
            for (Entity passenger : entity.getPassengerList()) {
                if (passenger instanceof PlayerEntity player) {
                    players.add(player);
                }
            }
        }

        return players;
    }

    private void onPlayerEnteredWaypoint(PlayerEntity player, Waypoint waypoint) {
        String playerName = player.getName().getString();

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastTrigger = playerCooldowns.get(playerName);
        if (lastTrigger != null && (now - lastTrigger) < COOLDOWN_MS) {
            return;
        }
        playerCooldowns.put(playerName, now);

        // Validate waypoint order (optional - can be enabled for stricter checking)
        Integer lastOrder = playerLastWaypointOrder.get(playerName);
        int currentOrder = waypoint.getOrder();

        // Update last waypoint order
        playerLastWaypointOrder.put(playerName, currentOrder);

        // Get racer ID for WebSocket
        RacerManager racerManager = RacerManager.getInstance();
        Optional<String> racerIdOpt = racerManager.getRacerId(playerName);

        if (racerIdOpt.isEmpty()) {
            IcekartClient.LOGGER.warn("[IceKart] Waypoint triggered but no racer ID for: {}", playerName);
            showErrorMessage("§c[IceKart] §7Waypoint missed - racer ID not synced");
            return;
        }

        WebSocketClient wsClient = WebSocketClient.getInstance();

        // Check WebSocket connection
        if (!wsClient.isConnected()) {
            IcekartClient.LOGGER.warn("[IceKart] Waypoint triggered but WebSocket not connected: {} at {}",
                    playerName, waypoint.getId());
            showErrorMessage("§c[IceKart] §7Waypoint missed - not connected to server");
            return;
        }

        // Check if race is running
        if (!wsClient.isRacing()) {
            IcekartClient.LOGGER.debug("[IceKart] Waypoint triggered but race not running: {} at {}",
                    playerName, waypoint.getId());
            return;
        }

        String racerId = racerIdOpt.get();

        // Send checkpoint to server
        wsClient.sendCheckpoint(racerId);

        IcekartClient.LOGGER.info("[IceKart] Waypoint {} triggered by {} (ID: {}, type: {}, order: {})",
                waypoint.getId(), playerName, racerId, waypoint.getType(), waypoint.getOrder());

        // Show success message only when view is enabled
        if (WaypointRenderer.getInstance().isViewEnabled()) {
            String typeStr = switch (waypoint.getType()) {
                case START -> "§aSTART";
                case CHECKPOINT -> "§bCHECKPOINT";
                case FINISH -> "§6FINISH";
            };
            showMessage(String.format("§a[IceKart] §f%s §7| §b%s §7(#%d)",
                    typeStr, playerName, waypoint.getOrder() + 1));
        }
    }

    /**
     * Show a message in chat (only used for debug/view mode).
     */
    private void showMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(message), false);
            }
        });
    }

    /**
     * Show an error message in chat (always shown).
     */
    private void showErrorMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(message), false);
            }
        });
    }

    /**
     * Reset tracking for all players (e.g., on race reset).
     */
    public void resetTracking() {
        playerCurrentWaypoint.clear();
        playerLastWaypointOrder.clear();
        playerCooldowns.clear();
        IcekartClient.LOGGER.info("[IceKart] Waypoint tracking reset");
    }

    /**
     * Reset tracking for a specific player.
     */
    public void resetPlayerTracking(String playerName) {
        playerCurrentWaypoint.remove(playerName);
        playerLastWaypointOrder.remove(playerName);
        playerCooldowns.remove(playerName);
    }

    /**
     * Get the last waypoint order a player passed through.
     */
    public Optional<Integer> getPlayerLastWaypointOrder(String playerName) {
        return Optional.ofNullable(playerLastWaypointOrder.get(playerName));
    }
}

