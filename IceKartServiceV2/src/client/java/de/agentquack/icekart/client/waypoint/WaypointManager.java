package de.agentquack.icekart.client.waypoint;

import com.google.gson.*;
import de.agentquack.icekart.client.IcekartClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages waypoints for race tracks.
 * Supports world-specific and global waypoint configurations.
 */
public class WaypointManager {

    private static WaypointManager instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Waypoints by ID
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();
    // Waypoints sorted by order
    private final List<Waypoint> orderedWaypoints = new ArrayList<>();

    // Current world name for world-specific configs
    private String currentWorldName = null;

    // Selection state for waypoint creation tool
    private BlockPos selectionPos1 = null;
    private BlockPos selectionPos2 = null;

    private WaypointManager() {
    }

    public static WaypointManager getInstance() {
        if (instance == null) {
            instance = new WaypointManager();
        }
        return instance;
    }

    // --- Selection Tool Methods ---

    public void setPos1(BlockPos pos) {
        this.selectionPos1 = pos.toImmutable();
        IcekartClient.LOGGER.info("[IceKart] Waypoint Pos1 set to: {}", pos.toShortString());
    }

    public void setPos2(BlockPos pos) {
        this.selectionPos2 = pos.toImmutable();
        IcekartClient.LOGGER.info("[IceKart] Waypoint Pos2 set to: {}", pos.toShortString());
    }

    public BlockPos getSelectionPos1() {
        return selectionPos1;
    }

    public BlockPos getSelectionPos2() {
        return selectionPos2;
    }

    public boolean hasCompleteSelection() {
        return selectionPos1 != null && selectionPos2 != null;
    }

    public void clearSelection() {
        selectionPos1 = null;
        selectionPos2 = null;
    }

    // --- Waypoint Management ---

    /**
     * Create a waypoint from the current selection.
     */
    public Waypoint createWaypointFromSelection(String id, Waypoint.Type type) {
        if (!hasCompleteSelection()) {
            return null;
        }

        int order = orderedWaypoints.size();
        Waypoint waypoint = new Waypoint(id, type, selectionPos1, selectionPos2, order);
        addWaypoint(waypoint);
        clearSelection();
        return waypoint;
    }

    /**
     * Add a waypoint.
     */
    public void addWaypoint(Waypoint waypoint) {
        waypoints.put(waypoint.getId(), waypoint);
        orderedWaypoints.add(waypoint);
        orderedWaypoints.sort(Comparator.comparingInt(Waypoint::getOrder));
        IcekartClient.LOGGER.info("[IceKart] Waypoint added: {}", waypoint);
    }

    /**
     * Remove a waypoint by ID.
     */
    public boolean removeWaypoint(String id) {
        Waypoint removed = waypoints.remove(id);
        if (removed != null) {
            orderedWaypoints.remove(removed);
            IcekartClient.LOGGER.info("[IceKart] Waypoint removed: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Get a waypoint by ID.
     */
    public Optional<Waypoint> getWaypoint(String id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    /**
     * Get all waypoints in order.
     */
    public List<Waypoint> getOrderedWaypoints() {
        return Collections.unmodifiableList(orderedWaypoints);
    }

    /**
     * Get all waypoint IDs.
     */
    public Set<String> getWaypointIds() {
        return new HashSet<>(waypoints.keySet());
    }

    /**
     * Get total number of waypoints.
     */
    public int getWaypointCount() {
        return waypoints.size();
    }

    /**
     * Clear all waypoints.
     */
    public void clearWaypoints() {
        waypoints.clear();
        orderedWaypoints.clear();
        IcekartClient.LOGGER.info("[IceKart] All waypoints cleared");
    }

    // --- World Management ---

    /**
     * Called when the world changes.
     */
    public void onWorldChanged(String worldName) {
        if (worldName == null) {
            currentWorldName = null;
            clearWaypoints();
            return;
        }

        if (!worldName.equals(currentWorldName)) {
            currentWorldName = worldName;
            loadWaypoints();
        }
    }

    public String getCurrentWorldName() {
        return currentWorldName;
    }

    // --- Persistence ---

    private Path getConfigDir() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config")
                .resolve("icekart");
    }

    private Path getWorldConfigPath() {
        if (currentWorldName == null) {
            return getConfigDir().resolve("waypoints_global.json");
        }
        return getConfigDir().resolve("waypoints_" + sanitizeFileName(currentWorldName) + ".json");
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Save waypoints to config file.
     */
    public void saveWaypoints() {
        try {
            Path configDir = getConfigDir();
            Files.createDirectories(configDir);

            JsonObject root = new JsonObject();
            root.addProperty("worldName", currentWorldName);
            root.addProperty("version", 1);

            JsonArray waypointsArray = new JsonArray();
            for (Waypoint waypoint : orderedWaypoints) {
                JsonObject wpJson = new JsonObject();
                wpJson.addProperty("id", waypoint.getId());
                wpJson.addProperty("type", waypoint.getType().name());
                wpJson.addProperty("order", waypoint.getOrder());

                JsonObject pos1Json = new JsonObject();
                pos1Json.addProperty("x", waypoint.getPos1().getX());
                pos1Json.addProperty("y", waypoint.getPos1().getY());
                pos1Json.addProperty("z", waypoint.getPos1().getZ());
                wpJson.add("pos1", pos1Json);

                JsonObject pos2Json = new JsonObject();
                pos2Json.addProperty("x", waypoint.getPos2().getX());
                pos2Json.addProperty("y", waypoint.getPos2().getY());
                pos2Json.addProperty("z", waypoint.getPos2().getZ());
                wpJson.add("pos2", pos2Json);

                waypointsArray.add(wpJson);
            }
            root.add("waypoints", waypointsArray);

            Path configPath = getWorldConfigPath();
            Files.writeString(configPath, GSON.toJson(root));
            IcekartClient.LOGGER.info("[IceKart] Saved {} waypoints to {}", waypoints.size(), configPath);

        } catch (IOException e) {
            IcekartClient.LOGGER.error("[IceKart] Failed to save waypoints: {}", e.getMessage());
        }
    }

    /**
     * Load waypoints from config file.
     */
    public void loadWaypoints() {
        clearWaypoints();

        Path configPath = getWorldConfigPath();
        if (!Files.exists(configPath)) {
            IcekartClient.LOGGER.info("[IceKart] No waypoint config found at {}", configPath);
            return;
        }

        try {
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonArray waypointsArray = root.getAsJsonArray("waypoints");
            if (waypointsArray == null) {
                return;
            }

            for (JsonElement element : waypointsArray) {
                JsonObject wpJson = element.getAsJsonObject();

                String id = wpJson.get("id").getAsString();
                Waypoint.Type type = Waypoint.Type.valueOf(wpJson.get("type").getAsString());
                int order = wpJson.get("order").getAsInt();

                JsonObject pos1Json = wpJson.getAsJsonObject("pos1");
                BlockPos pos1 = new BlockPos(
                        pos1Json.get("x").getAsInt(),
                        pos1Json.get("y").getAsInt(),
                        pos1Json.get("z").getAsInt()
                );

                JsonObject pos2Json = wpJson.getAsJsonObject("pos2");
                BlockPos pos2 = new BlockPos(
                        pos2Json.get("x").getAsInt(),
                        pos2Json.get("y").getAsInt(),
                        pos2Json.get("z").getAsInt()
                );

                Waypoint waypoint = new Waypoint(id, type, pos1, pos2, order);
                waypoints.put(id, waypoint);
                orderedWaypoints.add(waypoint);
            }

            orderedWaypoints.sort(Comparator.comparingInt(Waypoint::getOrder));
            IcekartClient.LOGGER.info("[IceKart] Loaded {} waypoints from {}", waypoints.size(), configPath);

        } catch (Exception e) {
            IcekartClient.LOGGER.error("[IceKart] Failed to load waypoints: {}", e.getMessage());
        }
    }
}

