package de.agentquack.icekart.client.waypoint;

import de.agentquack.icekart.client.IcekartClient;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Handles the wooden axe selection tool for creating waypoints.
 * Left-click with wooden axe: Set position 1
 * Right-click with wooden axe: Set position 2
 */
public class WaypointSelectionTool {

    private static boolean registered = false;

    /**
     * Register the selection tool event handlers.
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // Left-click to set position 1
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND) {
                if (player.getMainHandStack().getItem() == Items.WOODEN_AXE) {
                    WaypointManager.getInstance().setPos1(pos);
                    player.sendMessage(Text.literal("§a[IceKart] §fPosition 1 set: §e" + pos.toShortString()), false);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // Right-click to set position 2
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND) {
                if (player.getMainHandStack().getItem() == Items.WOODEN_AXE) {
                    WaypointManager.getInstance().setPos2(hitResult.getBlockPos());
                    player.sendMessage(Text.literal("§a[IceKart] §fPosition 2 set: §e" + hitResult.getBlockPos().toShortString()), false);

                    // Show selection info if both positions are set
                    WaypointManager manager = WaypointManager.getInstance();
                    if (manager.hasCompleteSelection()) {
                        var pos1 = manager.getSelectionPos1();
                        var pos2 = manager.getSelectionPos2();
                        int sizeX = Math.abs(pos2.getX() - pos1.getX()) + 1;
                        int sizeY = Math.abs(pos2.getY() - pos1.getY()) + 1;
                        int sizeZ = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
                        player.sendMessage(Text.literal(String.format(
                                "§7Selection: §e%dx%dx%d §7blocks. Use §b/waypoint create <id> [type]§7 to create.",
                                sizeX, sizeY, sizeZ)), false);
                    }
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        IcekartClient.LOGGER.info("[IceKart] Waypoint selection tool registered (wooden axe)");
    }
}

