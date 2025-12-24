 package de.agentquack.icekart.client.waypoint;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Callback for when a player passes through a waypoint.
 */
public interface WaypointTriggeredCallback {

    Event<WaypointTriggeredCallback> EVENT = EventFactory.createArrayBacked(
            WaypointTriggeredCallback.class,
            (listeners) -> (waypoint, player) -> {
                for (WaypointTriggeredCallback listener : listeners) {
                    listener.onWaypointTriggered(waypoint, player);
                }
            }
    );

    /**
     * Called when a player triggers a waypoint.
     *
     * @param waypoint The waypoint that was triggered
     * @param player   The player who triggered the waypoint
     */
    void onWaypointTriggered(Waypoint waypoint, PlayerEntity player);
}

