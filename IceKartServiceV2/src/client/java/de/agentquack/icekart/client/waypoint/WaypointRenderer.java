package de.agentquack.icekart.client.waypoint;

import de.agentquack.icekart.client.IcekartClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Renders waypoint boundaries using particles for development/debugging.
 * Toggle with /waypoint view
 */
public class WaypointRenderer {

    private static WaypointRenderer instance;

    private boolean viewEnabled = false;
    private int tickCounter = 0;
    private static final int PARTICLE_INTERVAL = 5; // Render every N ticks (reduce spam)
    private static final double PARTICLE_SPACING = 1.0; // Space between particles in blocks

    private WaypointRenderer() {
    }

    public static WaypointRenderer getInstance() {
        if (instance == null) {
            instance = new WaypointRenderer();
        }
        return instance;
    }

    /**
     * Register the tick event for rendering particles.
     */
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!viewEnabled || client.world == null || client.player == null) {
                return;
            }

            tickCounter++;
            if (tickCounter >= PARTICLE_INTERVAL) {
                tickCounter = 0;
                renderWaypoints();
            }
        });

        IcekartClient.LOGGER.info("[IceKart] Waypoint renderer registered");
    }

    /**
     * Toggle the view mode.
     */
    public boolean toggleView() {
        viewEnabled = !viewEnabled;
        return viewEnabled;
    }

    /**
     * Check if view mode is enabled.
     */
    public boolean isViewEnabled() {
        return viewEnabled;
    }

    /**
     * Set view mode.
     */
    public void setViewEnabled(boolean enabled) {
        this.viewEnabled = enabled;
    }

    /**
     * Render all waypoints with particles.
     */
    private void renderWaypoints() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        List<Waypoint> waypoints = WaypointManager.getInstance().getOrderedWaypoints();
        Vec3d playerPos = client.player.getPos();

        for (Waypoint waypoint : waypoints) {
            Box box = waypoint.getBoundingBox();

            // Only render if player is within reasonable distance (performance)
            if (playerPos.distanceTo(box.getCenter()) > 100) {
                continue;
            }

            renderBoxOutline(box, waypoint.getType());
        }

        // Also render current selection if exists
        WaypointManager manager = WaypointManager.getInstance();
        if (manager.getSelectionPos1() != null) {
            renderSelectionPoint(Vec3d.ofCenter(manager.getSelectionPos1()), true);
        }
        if (manager.getSelectionPos2() != null) {
            renderSelectionPoint(Vec3d.ofCenter(manager.getSelectionPos2()), false);
        }
        if (manager.hasCompleteSelection()) {
            Box selectionBox = new Box(
                    manager.getSelectionPos1().getX(),
                    manager.getSelectionPos1().getY(),
                    manager.getSelectionPos1().getZ(),
                    manager.getSelectionPos2().getX() + 1,
                    manager.getSelectionPos2().getY() + 1,
                    manager.getSelectionPos2().getZ() + 1
            );
            renderBoxOutlineSelection(selectionBox);
        }
    }

    /**
     * Spawn a particle at the given position.
     */
    private void spawnParticle(net.minecraft.particle.ParticleEffect particle, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.particleManager != null) {
            client.particleManager.addParticle(particle, x, y, z, 0, 0, 0);
        }
    }

    /**
     * Render a box outline with particles based on waypoint type.
     */
    private void renderBoxOutline(Box box, Waypoint.Type type) {
        // Draw edges of the box
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom edges
        drawLine(minX, minY, minZ, maxX, minY, minZ, type);
        drawLine(minX, minY, minZ, minX, minY, maxZ, type);
        drawLine(maxX, minY, maxZ, maxX, minY, minZ, type);
        drawLine(maxX, minY, maxZ, minX, minY, maxZ, type);

        // Top edges
        drawLine(minX, maxY, minZ, maxX, maxY, minZ, type);
        drawLine(minX, maxY, minZ, minX, maxY, maxZ, type);
        drawLine(maxX, maxY, maxZ, maxX, maxY, minZ, type);
        drawLine(maxX, maxY, maxZ, minX, maxY, maxZ, type);

        // Vertical edges
        drawLine(minX, minY, minZ, minX, maxY, minZ, type);
        drawLine(maxX, minY, minZ, maxX, maxY, minZ, type);
        drawLine(minX, minY, maxZ, minX, maxY, maxZ, type);
        drawLine(maxX, minY, maxZ, maxX, maxY, maxZ, type);
    }

    /**
     * Draw a line of particles between two points.
     */
    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Waypoint.Type type) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int particles = (int) Math.ceil(distance / PARTICLE_SPACING);

        for (int i = 0; i <= particles; i++) {
            double t = particles == 0 ? 0 : (double) i / particles;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;

            // Use different colored particles based on waypoint type
            // HAPPY_VILLAGER = green, FLAME = orange, SOUL_FIRE_FLAME = blue
            var particleType = switch (type) {
                case START -> ParticleTypes.HAPPY_VILLAGER;      // Green
                case CHECKPOINT -> ParticleTypes.HAPPY_VILLAGER; // Green
                case FINISH -> ParticleTypes.FLAME;              // Orange/Yellow
            };

            spawnParticle(particleType, x, y, z);
        }
    }

    /**
     * Render selection box outline with different particles.
     */
    private void renderBoxOutlineSelection(Box box) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Draw with END_ROD particles (white/yellow)
        drawLineSelection(minX, minY, minZ, maxX, minY, minZ);
        drawLineSelection(minX, minY, minZ, minX, minY, maxZ);
        drawLineSelection(maxX, minY, maxZ, maxX, minY, minZ);
        drawLineSelection(maxX, minY, maxZ, minX, minY, maxZ);
        drawLineSelection(minX, maxY, minZ, maxX, maxY, minZ);
        drawLineSelection(minX, maxY, minZ, minX, maxY, maxZ);
        drawLineSelection(maxX, maxY, maxZ, maxX, maxY, minZ);
        drawLineSelection(maxX, maxY, maxZ, minX, maxY, maxZ);
        drawLineSelection(minX, minY, minZ, minX, maxY, minZ);
        drawLineSelection(maxX, minY, minZ, maxX, maxY, minZ);
        drawLineSelection(minX, minY, maxZ, minX, maxY, maxZ);
        drawLineSelection(maxX, minY, maxZ, maxX, maxY, maxZ);
    }

    private void drawLineSelection(double x1, double y1, double z1, double x2, double y2, double z2) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int particles = (int) Math.ceil(distance / PARTICLE_SPACING);

        for (int i = 0; i <= particles; i++) {
            double t = particles == 0 ? 0 : (double) i / particles;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;

            spawnParticle(ParticleTypes.END_ROD, x, y, z);
        }
    }

    /**
     * Render a selection point marker.
     */
    private void renderSelectionPoint(Vec3d pos, boolean isPos1) {
        // Create a small burst of particles at the selection point
        var particleType = isPos1 ? ParticleTypes.COMPOSTER : ParticleTypes.DRIPPING_HONEY;

        for (int i = 0; i < 3; i++) {
            double offsetX = (Math.random() - 0.5) * 0.3;
            double offsetY = (Math.random() - 0.5) * 0.3;
            double offsetZ = (Math.random() - 0.5) * 0.3;
            spawnParticle(particleType, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ);
        }
    }
}

