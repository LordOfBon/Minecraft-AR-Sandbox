package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinecartGame extends Game {

    private ArrayList<ArrayList<BlockVector3>> paths;
    private MinecartPathGrid grid;
    private final int top;
    private final int[] surface;
    private Minecart cart;
    private int goalsPassed;
    private final long cartTimeLimit;
    private long cartTimer;
    private long levelTimer;
    private final float initialCartSpeed;
    private final BlockType pillarBlock;
    private int level;
    private final CuboidRegion region;
    private final ARSandbox_Spigot plugin;

    private static final Property<String> keyShape = BlockTypes.RAIL.getProperty("shape");
    private static final Property<String> keyPoweredShape = BlockTypes.POWERED_RAIL.getProperty("shape");


    public MinecartGame(Player player, World world, ARSandbox_Spigot plugin, CuboidRegion region) {
        super(player, world, plugin.config);
        this.plugin = plugin;
        this.region = region;

        top = region.getMaximumY();
        surface = new int[region.getWidth() * region.getLength()];

        cartTimeLimit = config.getLong("cart_respawn_time");
        initialCartSpeed = config.getInt("initial_cart_speed");
        String pillarID = config.getString("cart_pillar_block");
        if (pillarID == null) {
            Bukkit.getLogger().warning("Could not load cart pillar type");
            pillarBlock = BlockTypes.CHERRY_WOOD;
        }
        else {
            pillarBlock = BlockTypes.get(pillarID);
        }

        try {
            List<List<Vector>> ps = (List<List<Vector>>) config.get("cart_path");
            assert ps != null;
            paths = new ArrayList<>();
            for (List<Vector> p : ps) {
                ArrayList<BlockVector3> temp = new ArrayList<>();
                BlockVector3 dims = region.getDimensions();

                for (Vector v : p) {
                    temp.add(BlockVector3.at((float)dims.x() * v.getX(), (float)dims.y() * v.getY(), (float)dims.z() * v.getZ()));
                }
                paths.add(temp);
            }
        }
        catch (Error e) {
            Bukkit.getLogger().severe("Unable to load path. Error: %s".formatted(e.getMessage()));
            end(false);
            return;
        }
    }

    @Override
    public void start() {
        level = 0;
        startLevel();
    }

    private void startLevel() {
        ArrayList<BlockVector3> path = paths.get(level);

        grid = new MinecartPathGrid(region.getWidth(), region.getLength(), path);

        levelTimer = System.currentTimeMillis();

    }

    @Override
    public void step() {
        ArrayList<BlockVector3> path = paths.get(level);
        reshapeTracks();

        if(cart == null) {
            makePillarNodes();
            goalsPassed = 0;
            org.bukkit.World world = Bukkit.getWorld("world");
            assert world != null;
            BlockVector3 startWE = path.getFirst();
            Location start = new Location(world, startWE.x() + 0.5, startWE.y() + 1, startWE.z() + 0.5);
            cart = (Minecart) world.spawnEntity(start, EntityType.MINECART);
            BlockVector2 dir = grid.get(startWE.x(), startWE.z()).next;
            cart.setVelocity(new Vector(dir.x() * initialCartSpeed, 0, dir.z() * initialCartSpeed));
            cartTimer = System.currentTimeMillis();
            cart.setDerailedVelocityMod(new Vector(1.0, 1.0, 1.0));
            cart.setFlyingVelocityMod(new Vector(1.0, 1.0, 1.0));
        }

        // Check cart progress
        BlockVector3 next = path.get(goalsPassed);
        Location c = cart.getLocation();
        if (next.x() == c.getBlockX() && next.y() + 1 == c.getBlockY()  && next.z() == c.getBlockZ()) {
            try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
                edit.setBlock(next.x(), next.y(), next.z(), BlockTypes.EMERALD_BLOCK);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0f, 1.0f);
            goalsPassed += 1;
            cartTimer = System.currentTimeMillis(); // Reset respawn timer
            if (goalsPassed == path.size()) {
                endLevel();
            }
        }

        // Respawn if timelimit runs out
        if (System.currentTimeMillis() - cartTimer >= cartTimeLimit) {
            cart.remove();
            cart = null;
        }

    }

    private void endLevel() {
        long time = System.currentTimeMillis() - levelTimer;
        level += 1;
        player.sendMessage("Completed level %d in %.0f seconds".formatted(level, (float)time / 1000.0f));
        Bukkit.getLogger().info("Completed level %d in %.0f seconds".formatted(level, (float)time / 1000.0f));
        if (level >= paths.size()) {
            end(true);
        }
        else {
            plugin.socketHandler.requestBlockReset();
            if (cart != null) {
                cart.remove();
                cart = null;
            }
            startLevel();
        }
    }

    @Override
    public void end(boolean win) {
        done = true;
        if (win) {
            player.sendMessage("You Win!");
        } else {
            player.sendMessage("Minecart Game Shutdown");
        }
        if (cart != null) {
            cart.remove();
        }
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        MinecartPathGrid.PathCell cell = grid.get(x, z);
        return cell.node;
    }

    private int getSurfaceHeight(int x, int z) {
        int i = z * grid.width + x;
        if (surface[i] == -1) {
            surface[i] = world.getHighestTerrainBlock(x, z, 0, top) + 1;
        }
        return surface[i];
    }

    private void makePillarNodes() {
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            for (BlockVector3 p : paths.get(level)) {
                edit.setBlock(p.x(), p.y(), p.z(), BlockTypes.REDSTONE_BLOCK);
                edit.setBlocks((Region) new CuboidRegion(BlockVector3.at(p.x(), p.y() + 2, p.z()), BlockVector3.at(p.x(), top, p.z())), BlockTypes.AIR);
                edit.setBlocks((Region) new CuboidRegion(BlockVector3.at(p.x(), p.y() - 1, p.z()), BlockVector3.at(p.x(), 0, p.z())), pillarBlock);
            }
        }
    }

    private BlockState getRailToPlace(MinecartPathGrid.PathCell gridCell, boolean shouldAscend, boolean shouldDescend) {
        String shape;
        if (shouldAscend || shouldDescend) {
            BlockVector2 dir = gridCell.next;
            if (dir.z() == 1) {
                shape = shouldAscend ? "ascending_south" : "ascending_north";
            } else if (dir.z() == -1) {
                shape = shouldAscend ? "ascending_north" : "ascending_south";
            } else if (dir.x() == 1) {
                shape = shouldAscend ? "ascending_east" : "ascending_west";
            } else {
                shape = shouldAscend ? "ascending_west" : "ascending_east";
            }
        }
        else {
            shape = gridCell.getRailType();
        }

        if (gridCell.powered()) {
            return BlockState.get(String.format("minecraft:powered_rail[powered=true,shape=%s]", shape));
        }
        else {
            return BlockState.get(String.format("minecraft:rail[shape=%s]", shape));
        }
    }

    private void reshapeTracks() {
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            Arrays.fill(surface, -1);
            for (int x = 0; x < grid.width; ++x) {
                for (int z = 0; z < grid.height; ++z) {
                    MinecartPathGrid.PathCell gridCell = grid.get(x, z);
                    if (gridCell.rail) {
                        int y = getSurfaceHeight(x, z);
                        if (gridCell.node && gridCell.node_height != y) {
                            y = gridCell.node_height + 1;
                        }
                        BlockVector3 block = BlockVector3.at(x,y,z);

                        BlockState blockState = world.getBlock(block);
                        boolean notRail = blockState.getBlockType() != BlockTypes.RAIL;

                        boolean shouldAscend = false;
                        boolean shouldDescend = false;
                        boolean ascensionChange = false;

                        // Only the powered rails should ascend or descend, the unpowered rails are turns
                        if(gridCell.powered()) {
                            BlockVector3 nextBlock = block.add(gridCell.next.x(), 0, gridCell.next.z());
                            int nextSurfaceHeight = getSurfaceHeight(nextBlock.x(), nextBlock.z());
                            shouldAscend = nextSurfaceHeight == nextBlock.y() + 1;

                            BlockVector3 prevBlock = block.add(-gridCell.next.x(), 0, -gridCell.next.z());
                            int prevSurfaceHeight = getSurfaceHeight(prevBlock.x(), prevBlock.z());
                            shouldDescend = prevSurfaceHeight == prevBlock.y() + 1;

                            String shape = blockState.getState(blockState.getBlockType() == BlockTypes.POWERED_RAIL ? keyPoweredShape : keyShape);
                            ascensionChange = (shouldAscend || shouldDescend) ^ !shape.startsWith("ascend");
                        }

                        if (notRail || ascensionChange) {
                            edit.setBlocks((Region) new CuboidRegion(block, BlockVector3.at(x, top, z)), BlockTypes.AIR);
                            BlockState rail = getRailToPlace(gridCell, shouldAscend, shouldDescend);
                            edit.setBlock(block.x(), block.y(), block.z(), rail);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBlockReset() {
        makePillarNodes();
    }
}
