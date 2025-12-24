package de.agentquack.icekart.client;

import de.agentquack.icekart.client.command.RacerCommand;
import de.agentquack.icekart.client.command.WaypointCommand;
import de.agentquack.icekart.client.waypoint.WaypointManager;
import de.agentquack.icekart.client.waypoint.WaypointRenderer;
import de.agentquack.icekart.client.waypoint.WaypointSelectionTool;
import de.agentquack.icekart.client.waypoint.WaypointTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IcekartClient implements ClientModInitializer {

    public static final String MOD_ID = "icekart";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[IceKart] Client initialisiert (Waypoint-basiertes Tracking aktiv)");
        registerRacerCommand();
        registerWaypointCommand();
        registerWaypointSystem();
    }

    private void registerWaypointCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            WaypointCommand.register(dispatcher);
        });
    }

    private void registerWaypointSystem() {
        // Register the wooden axe selection tool
        WaypointSelectionTool.register();

        // Register the waypoint particle renderer
        WaypointRenderer.getInstance().register();

        // Track world changes for waypoint loading
        String[] lastWorldName = {null};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            String currentWorld = null;
            if (client.world != null && client.isIntegratedServerRunning()) {
                // Single player world
                var server = client.getServer();
                if (server != null) {
                    currentWorld = server.getSaveProperties().getLevelName();
                }
            } else if (client.world != null && client.getCurrentServerEntry() != null) {
                // Multiplayer server
                currentWorld = client.getCurrentServerEntry().address;
            }

            if (currentWorld != null && !currentWorld.equals(lastWorldName[0])) {
                lastWorldName[0] = currentWorld;
                WaypointManager.getInstance().onWorldChanged(currentWorld);
            } else if (currentWorld == null && lastWorldName[0] != null) {
                lastWorldName[0] = null;
                WaypointManager.getInstance().onWorldChanged(null);
            }
        });

        // Register waypoint tick tracker
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.player != null) {
                WaypointTracker.getInstance().tick();
            }
        });

        LOGGER.info("[IceKart] Waypoint system initialized");
    }

    private void registerRacerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RacerCommand.register(dispatcher);
            LOGGER.info("[IceKart] /racer command registered");
        });
    }
}
