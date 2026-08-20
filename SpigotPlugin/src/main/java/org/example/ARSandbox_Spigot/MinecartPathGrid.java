package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;

import java.util.ArrayList;

public class MinecartPathGrid {
    public static final class PathCell {
        public final boolean start;
        public final boolean rail;
        public final boolean node;
        private final byte rail_type;
        public final BlockVector2 next;
        public final int node_height;

        private static final String[] rail_types = {
                "east_west",
                "north_east",
                "north_south",
                "north_west",
                "south_east",
                "south_west"
        };

        public String getRailType() {
            return rail_types[rail_type];
        }

        private PathCell(boolean start, boolean rail, boolean node, byte rail_type, BlockVector2 next, int node_height) {
            this.rail_type = rail_type;
            this.rail = rail;
            this.node = node;
            this.start = start;
            this.next = next;
            this.node_height = node_height;
        }

        public static final PathCell NONE = new PathCell(false, false, false, (byte) 0, BlockVector2.ZERO, 0);

        public static PathCell rail(BlockVector2 direction) {
            // Normal rails must be straight
            byte rail_type = 0;
            if(direction.z() != 0) {
                rail_type = 2;
            }
            return new PathCell(false, true, false, rail_type, direction, 0);
        }

        public static PathCell node(boolean start, BlockVector2 to, BlockVector2 from, int height) {
            byte rail_type = 0;
            BlockVector2 dir = to.add(from);
            if (dir.x() == 0 && to.z() != 0) {
                rail_type = 2;
            }
            else {
                if (dir.x() == 1) { // East
                    if (dir.z() == 1) { // South
                        rail_type = 4;
                    }
                    else if (dir.z() == -1) { // North
                        rail_type = 1;
                    }
                }
                else if (dir.x() == -1) { // West
                    if (dir.z() == 1) { // South
                        rail_type = 5;
                    }
                    else if (dir.z() == -1) { // North
                        rail_type = 3;
                    }
                }
            }

            return new PathCell(start, true, true, rail_type, to, height);
        }

        public boolean powered() {
            return rail_type == 0 || rail_type == 2;
        }
    }

    private final ArrayList<PathCell> grid;
    public final int width;
    public final int height;

    public MinecartPathGrid(int width, int height, ArrayList<BlockVector3> path) {
        grid = new ArrayList<>(width * height);
        for(int i = 0; i < width * height; ++i) {
            grid.add(PathCell.NONE);
        }
        this.width = width;
        this.height = height;
        boolean start = true;
        BlockVector2 from = BlockVector2.ZERO;
        for (int i = 0; i < path.size() - 1; ++i) {
            // Fill in tiles between goals
            BlockVector3 pos1 = path.get(i);
            BlockVector3 pos2 = path.get(i + 1);

            BlockVector2 dif = BlockVector2.at(pos2.x() - pos1.x(), pos2.z() - pos1.z());
            if (Math.pow(dif.x() + dif.z(), 2) != dif.lengthSq()) { // When they differ in more than one direction
                Bukkit.getLogger().warning("Path node in Minecart game differs in more than one dimension from next node");
                return;
            }
            BlockVector2 step = dif.normalize();
            int limiter = 0;
            BlockVector2 goal = pos2.toBlockVector2();
            for (BlockVector2 p = pos1.toBlockVector2(); !p.equals(goal); p = p.add(step)) {
                limiter += 1;
                if (limiter > 10000000) {
                    Bukkit.getLogger().warning(String.format("Path from node to next is too long in Minecart game. At p = %s", p));
                    return;
                }
                if (contains(p.x(), p.z())) {
                    if (p.x() == pos1.x() && p.z() == pos1.z()) {
                        set(p.x(), p.z(), PathCell.node(start, step, from, pos1.y()));
                        start = false;
                    }
                    else {
                        set(p.x(), p.z(), PathCell.rail(step));
                    }
                }
            }
            from = step.multiply(-1);
            if (contains(pos2.x(), pos2.z())) {
                set(pos2.x(), pos2.z(), PathCell.node(false, step, from, pos2.y()));
            }
        }
    }

    public void set(int x, int y, PathCell cell) {
        grid.set(y * width + x, cell);
    }

    public PathCell get(int x, int y) {
        return grid.get(y * width + x);
    }

    public boolean contains(int x, int y) {
        return x > 0 && x < width && y > 0 && y < height;
    }
}
