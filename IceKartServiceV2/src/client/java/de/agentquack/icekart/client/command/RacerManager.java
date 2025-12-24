package de.agentquack.icekart.client.command;

import de.agentquack.icekart.client.IcekartClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the list of racers participating in the race.
 * Tracks the mapping between player names and server-assigned racer IDs.
 */
public class RacerManager {

    private static RacerManager instance;

    // Map of player name (lowercase) -> racer ID from server
    private final Map<String, String> racerNameToId = new HashMap<>();
    // Map of racer ID -> player name (for reverse lookup)
    private final Map<String, String> racerIdToName = new HashMap<>();

    private RacerManager() {
    }

    public static RacerManager getInstance() {
        if (instance == null) {
            instance = new RacerManager();
        }
        return instance;
    }

    /**
     * Add a racer locally (before server confirms with ID)
     */
    public void addRacer(String playerName) {
        // We add with null ID initially, the ID will be set when we receive init/update from server
        if (!racerNameToId.containsKey(playerName)) {
            racerNameToId.put(playerName, null);
            IcekartClient.LOGGER.debug("[IceKart] Racer added locally (awaiting server ID): {}", playerName);
        }
    }

    /**
     * Update racer information from server data.
     * Called when we receive init or update events.
     */
    public void updateRacerFromServer(String id, String name) {
        racerNameToId.put(name, id);
        racerIdToName.put(id, name);
        IcekartClient.LOGGER.info("[IceKart] Racer synced from server: {} -> ID: {}", name, id);
    }

    /**
     * Remove a racer by name
     */
    public boolean removeRacer(String playerName) {
        String id = racerNameToId.remove(playerName);
        if (id != null) {
            racerIdToName.remove(id);
            IcekartClient.LOGGER.debug("[IceKart] Racer removed: {} (ID: {})", playerName, id);
            return true;
        }
        IcekartClient.LOGGER.debug("[IceKart] Racer not found for removal: {}", playerName);
        return false;
    }

    /**
     * Check if a player is registered as a racer
     */
    public boolean isRacer(String playerName) {
        return racerNameToId.containsKey(playerName.toLowerCase());
    }

    /**
     * Get the server-assigned racer ID for a player name
     */
    public Optional<String> getRacerId(String playerName) {
        String id = racerNameToId.get(playerName);
        return Optional.ofNullable(id);
    }

    /**
     * Get the player name for a racer ID
     */
    public Optional<String> getRacerName(String racerId) {
        String name = racerIdToName.get(racerId);
        return Optional.ofNullable(name);
    }

    /**
     * Clear all racers
     */
    public void clearRacers() {
        racerNameToId.clear();
        racerIdToName.clear();
        IcekartClient.LOGGER.debug("[IceKart] All racers cleared");
    }

    /**
     * Get all registered racer names
     */
    public java.util.Set<String> getRacerNames() {
        return new java.util.HashSet<>(racerNameToId.keySet());
    }
}

