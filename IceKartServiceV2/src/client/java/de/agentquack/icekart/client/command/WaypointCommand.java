package de.agentquack.icekart.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.agentquack.icekart.client.IcekartClient;
import de.agentquack.icekart.client.waypoint.Waypoint;
import de.agentquack.icekart.client.waypoint.WaypointManager;
import de.agentquack.icekart.client.waypoint.WaypointRenderer;
import de.agentquack.icekart.client.waypoint.WaypointTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Commands for managing waypoints.
 *
 * Commands:
 * - /waypoint create <id> [type]     - Create a waypoint from selection
 * - /waypoint remove <id>            - Remove a waypoint
 * - /waypoint list                   - List all waypoints
 * - /waypoint info <id>              - Show waypoint info
 * - /waypoint clear                  - Clear all waypoints
 * - /waypoint save                   - Save waypoints to config
 * - /waypoint load                   - Load waypoints from config
 * - /waypoint view                   - Toggle waypoint visualization
 * - /waypoint selection clear        - Clear current selection
 * - /waypoint reset                  - Reset tracking
 */
public class WaypointCommand {

    // Suggestion provider for waypoint IDs
    private static final SuggestionProvider<FabricClientCommandSource> WAYPOINT_IDS = (context, builder) -> {
        for (String id : WaypointManager.getInstance().getWaypointIds()) {
            if (id.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    // Suggestion provider for waypoint types
    private static final SuggestionProvider<FabricClientCommandSource> WAYPOINT_TYPES = (context, builder) -> {
        for (Waypoint.Type type : Waypoint.Type.values()) {
            String name = type.name().toLowerCase();
            if (name.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("waypoint")
                // /waypoint create <id> [type]
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> createWaypoint(ctx, Waypoint.Type.CHECKPOINT))
                                .then(ClientCommandManager.argument("type", StringArgumentType.word())
                                        .suggests(WAYPOINT_TYPES)
                                        .executes(WaypointCommand::createWaypointWithType))))

                // /waypoint remove <id>
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests(WAYPOINT_IDS)
                                .executes(WaypointCommand::removeWaypoint)))

                // /waypoint list
                .then(ClientCommandManager.literal("list")
                        .executes(WaypointCommand::listWaypoints))

                // /waypoint info <id>
                .then(ClientCommandManager.literal("info")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests(WAYPOINT_IDS)
                                .executes(WaypointCommand::waypointInfo)))

                // /waypoint clear
                .then(ClientCommandManager.literal("clear")
                        .executes(WaypointCommand::clearWaypoints))

                // /waypoint save
                .then(ClientCommandManager.literal("save")
                        .executes(WaypointCommand::saveWaypoints))

                // /waypoint load
                .then(ClientCommandManager.literal("load")
                        .executes(WaypointCommand::loadWaypoints))

                // /waypoint view (toggle particle visualization)
                .then(ClientCommandManager.literal("view")
                        .executes(WaypointCommand::toggleView))

                // /waypoint selection clear
                .then(ClientCommandManager.literal("selection")
                        .then(ClientCommandManager.literal("clear")
                                .executes(WaypointCommand::clearSelection)))

                // /waypoint reset (reset tracking)
                .then(ClientCommandManager.literal("reset")
                        .executes(WaypointCommand::resetTracking))
        );

        IcekartClient.LOGGER.info("[IceKart] /waypoint command registered");
    }

    private static int createWaypoint(CommandContext<FabricClientCommandSource> ctx, Waypoint.Type type) {
        String id = StringArgumentType.getString(ctx, "id");
        WaypointManager manager = WaypointManager.getInstance();

        if (!manager.hasCompleteSelection()) {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7No selection! Use wooden axe to select an area."));
            ctx.getSource().sendFeedback(Text.literal("§7  Left-click: Set position 1"));
            ctx.getSource().sendFeedback(Text.literal("§7  Right-click: Set position 2"));
            return 0;
        }

        if (manager.getWaypoint(id).isPresent()) {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7Waypoint with ID '" + id + "' already exists!"));
            return 0;
        }

        Waypoint waypoint = manager.createWaypointFromSelection(id, type);
        if (waypoint != null) {
            ctx.getSource().sendFeedback(Text.literal(String.format(
                    "§a[IceKart] §fWaypoint created: §b%s §7(type: §e%s§7, order: §e#%d§7)",
                    id, type.name(), waypoint.getOrder() + 1)));
            manager.saveWaypoints();
            return 1;
        } else {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7Failed to create waypoint."));
            return 0;
        }
    }

    private static int createWaypointWithType(CommandContext<FabricClientCommandSource> ctx) {
        String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase();
        try {
            Waypoint.Type type = Waypoint.Type.valueOf(typeStr);
            return createWaypoint(ctx, type);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7Invalid waypoint type: " + typeStr));
            ctx.getSource().sendFeedback(Text.literal("§7Valid types: START, CHECKPOINT, FINISH"));
            return 0;
        }
    }

    private static int removeWaypoint(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        WaypointManager manager = WaypointManager.getInstance();

        if (manager.removeWaypoint(id)) {
            ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fWaypoint removed: §b" + id));
            manager.saveWaypoints();
            return 1;
        } else {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7Waypoint not found: " + id));
            return 0;
        }
    }

    private static int listWaypoints(CommandContext<FabricClientCommandSource> ctx) {
        WaypointManager manager = WaypointManager.getInstance();
        List<Waypoint> waypoints = manager.getOrderedWaypoints();

        if (waypoints.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§e[IceKart] §7No waypoints defined."));
            ctx.getSource().sendFeedback(Text.literal("§7Use wooden axe to select an area, then /waypoint create <id>"));
            return 0;
        }

        ctx.getSource().sendFeedback(Text.literal("§6[IceKart] §fWaypoints (" + waypoints.size() + "):"));

        for (Waypoint wp : waypoints) {
            String typeColor = switch (wp.getType()) {
                case START -> "§a";
                case CHECKPOINT -> "§b";
                case FINISH -> "§6";
            };

            MutableText line = Text.literal(String.format("  §7#%d §f%s %s%s",
                    wp.getOrder() + 1, wp.getId(), typeColor, wp.getType().name()));

            ctx.getSource().sendFeedback(line);
        }

        return waypoints.size();
    }

    private static int waypointInfo(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        WaypointManager manager = WaypointManager.getInstance();

        var waypointOpt = manager.getWaypoint(id);
        if (waypointOpt.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§c[IceKart] §7Waypoint not found: " + id));
            return 0;
        }

        Waypoint wp = waypointOpt.get();
        ctx.getSource().sendFeedback(Text.literal("§6[IceKart] §fWaypoint Info: §b" + wp.getId()));
        ctx.getSource().sendFeedback(Text.literal("  §7Type: §e" + wp.getType().name()));
        ctx.getSource().sendFeedback(Text.literal("  §7Order: §e#" + (wp.getOrder() + 1)));
        ctx.getSource().sendFeedback(Text.literal("  §7Pos1: §e" + wp.getPos1().toShortString()));
        ctx.getSource().sendFeedback(Text.literal("  §7Pos2: §e" + wp.getPos2().toShortString()));

        var box = wp.getBoundingBox();
        ctx.getSource().sendFeedback(Text.literal(String.format("  §7Size: §e%.0fx%.0fx%.0f",
                box.getLengthX(), box.getLengthY(), box.getLengthZ())));

        return 1;
    }

    private static int clearWaypoints(CommandContext<FabricClientCommandSource> ctx) {
        WaypointManager manager = WaypointManager.getInstance();
        int count = manager.getWaypointCount();
        manager.clearWaypoints();
        manager.saveWaypoints();
        ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fCleared " + count + " waypoints."));
        return count;
    }

    private static int saveWaypoints(CommandContext<FabricClientCommandSource> ctx) {
        WaypointManager manager = WaypointManager.getInstance();
        manager.saveWaypoints();
        ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fWaypoints saved."));
        return 1;
    }

    private static int loadWaypoints(CommandContext<FabricClientCommandSource> ctx) {
        WaypointManager manager = WaypointManager.getInstance();
        manager.loadWaypoints();
        ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fLoaded " + manager.getWaypointCount() + " waypoints."));
        return 1;
    }

    private static int toggleView(CommandContext<FabricClientCommandSource> ctx) {
        boolean enabled = WaypointRenderer.getInstance().toggleView();
        if (enabled) {
            ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fWaypoint view §aENABLED §7- showing particle outlines"));
        } else {
            ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fWaypoint view §cDISABLED"));
        }
        return 1;
    }

    private static int clearSelection(CommandContext<FabricClientCommandSource> ctx) {
        WaypointManager.getInstance().clearSelection();
        ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fSelection cleared."));
        return 1;
    }

    private static int resetTracking(CommandContext<FabricClientCommandSource> ctx) {
        WaypointTracker.getInstance().resetTracking();
        ctx.getSource().sendFeedback(Text.literal("§a[IceKart] §fWaypoint tracking reset."));
        return 1;
    }
}

