package de.agentquack.icekart.client.waypoint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Represents a waypoint (checkpoint) in the race track.
 * A waypoint is defined by a bounding box that triggers when a player enters it.
 */
public class Waypoint {

    public enum Type {
        START,      // Start/Finish line (first checkpoint)
        CHECKPOINT, // Regular checkpoint
        FINISH      // Finish line (if different from start)
    }

    private final String id;
    private final Type type;
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final int order; // Order in the race sequence (0 = start)

    public Waypoint(String id, Type type, BlockPos pos1, BlockPos pos2, int order) {
        this.id = id;
        this.type = type;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public int getOrder() {
        return order;
    }

    /**
     * Get the bounding box for collision detection.
     * Expands by 0.5 to match block boundaries properly.
     */
    public Box getBoundingBox() {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Check if a position is inside this waypoint's bounding box.
     */
    public boolean contains(Vec3d position) {
        return getBoundingBox().contains(position);
    }

    /**
     * Check if a position is inside this waypoint's bounding box.
     */
    public boolean contains(BlockPos position) {
        return contains(Vec3d.ofCenter(position));
    }

    /**
     * Get the center of the waypoint.
     */
    public Vec3d getCenter() {
        return getBoundingBox().getCenter();
    }

    @Override
    public String toString() {
        return String.format("Waypoint{id='%s', type=%s, order=%d, pos1=%s, pos2=%s}",
                id, type, order, pos1.toShortString(), pos2.toShortString());
    }
}

