package de.agentquack.icekart.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.agentquack.icekart.client.IcekartClient;
import de.agentquack.icekart.client.websocket.WebSocketClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/**
 * Registers the /racer command with subcommands for managing racers and race state.
 *
 * Commands:
 * - /racer player add <name>         - Add a player to the race
 * - /racer player remove <name>      - Remove a player from the race
 * - /racer player disqualify <name>  - Disqualify a player
 * - /racer race start                - Start the race
 * - /racer race stop                 - Stop the race
 * - /racer race reset                - Reset the race
 * - /racer connect                   - Connect to WebSocket server
 * - /racer disconnect                - Disconnect from WebSocket server
 */
public class RacerCommand {

    // Suggestion provider for online players
    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_PLAYERS = (context, builder) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().getName();
                if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    };

    // Suggestion provider for registered racers
    private static final SuggestionProvider<FabricClientCommandSource> REGISTERED_RACERS = (context, builder) -> {
        for (String name : RacerManager.getInstance().getRacerNames()) {
            if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("racer")
                // /racer connect
                .then(ClientCommandManager.literal("connect")
                        .executes(RacerCommand::connect))
                // /racer disconnect
                .then(ClientCommandManager.literal("disconnect")
                        .executes(RacerCommand::disconnect))
                // /racer player add <name>
                .then(ClientCommandManager.literal("player")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(RacerCommand::addPlayer)))
                        // /racer player remove <name>
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(REGISTERED_RACERS)
                                        .executes(RacerCommand::removePlayer)))
                        // /racer player disqualify <name>
                        .then(ClientCommandManager.literal("disqualify")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(REGISTERED_RACERS)
                                        .executes(RacerCommand::disqualifyPlayer))))
                // /racer race start
                .then(ClientCommandManager.literal("race")
                        .then(ClientCommandManager.literal("start")
                                .executes(RacerCommand::startRace))
                        // /racer race stop
                        .then(ClientCommandManager.literal("stop")
                                .executes(RacerCommand::stopRace))
                        // /racer race reset
                        .then(ClientCommandManager.literal("reset")
                                .executes(RacerCommand::resetRace))));
    }

    private static int connect(CommandContext<FabricClientCommandSource> context) {
        WebSocketClient client = WebSocketClient.getInstance();

        if (client.isConnected()) {
            context.getSource().sendFeedback(Text.literal("§e[IceKart] Already connected to WebSocket server"));
            return 0;
        }

        context.getSource().sendFeedback(Text.literal("§7[IceKart] Connecting to WebSocket server..."));
        client.connect().thenRun(() -> {
            if (client.isConnected()) {
                context.getSource().sendFeedback(Text.literal("§a[IceKart] Connected to WebSocket server!"));
            }
        });
        return 1;
    }

    private static int disconnect(CommandContext<FabricClientCommandSource> context) {
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(Text.literal("§e[IceKart] Not connected to WebSocket server"));
            return 0;
        }

        client.disconnect();
        context.getSource().sendFeedback(Text.literal("§a[IceKart] Disconnected from WebSocket server"));
        return 1;
    }

    private static int addPlayer(CommandContext<FabricClientCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "name");
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        // Add player to local tracking
        RacerManager.getInstance().addRacer(playerName);

        // Register racer on the server
        client.sendRegister(playerName);

        context.getSource().sendFeedback(
                Text.literal("§a[IceKart] Registered racer: §f" + playerName));

        IcekartClient.LOGGER.info("[IceKart] Registered racer: {}", playerName);
        return 1;
    }

    private static int removePlayer(CommandContext<FabricClientCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "name");
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        // Remove player from local tracking
        boolean removed = RacerManager.getInstance().removeRacer(playerName);

        if (removed) {
            // Remove racer from server (use playerName as racerId)
            client.sendRemove(playerName);

            context.getSource().sendFeedback(
                    Text.literal("§a[IceKart] Removed racer: §f" + playerName));
            IcekartClient.LOGGER.info("[IceKart] Removed racer: {}", playerName);
        } else {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Racer not found locally: §f" + playerName));
        }
        return 1;
    }

    private static int disqualifyPlayer(CommandContext<FabricClientCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "name");
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        // Get the racer ID from the RacerManager
        java.util.Optional<String> racerIdOpt = RacerManager.getInstance().getRacerId(playerName);
        if (racerIdOpt.isEmpty()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Racer not found or ID not synced: §f" + playerName));
            return 0;
        }

        String racerId = racerIdOpt.get();
        client.sendDisqualify(racerId);

        context.getSource().sendFeedback(
                Text.literal("§6[IceKart] Toggled disqualification for: §f" + playerName));
        IcekartClient.LOGGER.info("[IceKart] Toggled disqualification for racer: {} (ID: {})", playerName, racerId);
        return 1;
    }

    private static int startRace(CommandContext<FabricClientCommandSource> context) {
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        client.sendAction("start");
        context.getSource().sendFeedback(Text.literal("§a[IceKart] Race started!"));
        IcekartClient.LOGGER.info("[IceKart] Race started");
        return 1;
    }

    private static int stopRace(CommandContext<FabricClientCommandSource> context) {
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        client.sendAction("stop");
        context.getSource().sendFeedback(Text.literal("§e[IceKart] Race stopped!"));
        IcekartClient.LOGGER.info("[IceKart] Race stopped");
        return 1;
    }

    private static int resetRace(CommandContext<FabricClientCommandSource> context) {
        WebSocketClient client = WebSocketClient.getInstance();

        if (!client.isConnected()) {
            context.getSource().sendFeedback(
                    Text.literal("§c[IceKart] Not connected! Use /racer connect first"));
            return 0;
        }

        client.sendAction("reset");
        context.getSource().sendFeedback(Text.literal("§a[IceKart] Race reset!"));
        IcekartClient.LOGGER.info("[IceKart] Race reset");
        return 1;
    }
}

