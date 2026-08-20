package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class LavaGame extends Game {
    private final BlockMask air_and_water = new BlockMask();

    private int lava;
    private final int lava_max;

    private final int x1;
    private final int x2;
    private final int z1;
    private final int z2;
    private final int bottom;
    private final boolean useLava;
    private long clock;
    private ArrayList<CuboidRegion> objectives;
    private final String pillarBlock;
    private final ArrayList<BlockVector3> points;
    private final int numObjectives;
    private final Player sandboxPlayer;
    private final long lavaDelay;
    private long clock2;



    public LavaGame(Player player, World world, FileConfiguration config, CuboidRegion region, Player sandboxPlayer, boolean useLava) {
        super(player, world, config);
        this.sandboxPlayer = sandboxPlayer;
        air_and_water.add(BlockState.get("minecraft:water"));
        air_and_water.add(BlockState.get("minecraft:air"));
        lava_max = region.getMaximumY() - 10;
        this.useLava = useLava;
        x2 = region.getMaximumX();
        z2 = region.getMaximumZ();
        bottom = region.getMinimumY();
        lava = bottom;
        x1 = region.getMinimumX();
        z1 = region.getMinimumZ();
        pillarBlock = config.getString("lava_pillar_block");
        numObjectives = config.getInt("lava_objectives");
        lavaDelay = config.getLong("lava_delay");

        int gridX = config.getInt("lava_goalGrid_width");
        int gridZ = config.getInt("lava_goalGrid_length");
        points = new ArrayList<>();

        int gridCellX = region.getWidth() / (gridX + 2);
        int gridCellZ = region.getLength() / (gridZ + 2);

        for (int i = 1; i <= gridX; ++i) {
            for (int j = 1 ; j <= gridZ; ++j) {
                points.add(BlockVector3.at(i * gridCellX, 0, j * gridCellZ));
            }
        }
    }

    @Override
    public void start() {
        clock = System.currentTimeMillis();
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        ItemStack pic = Bukkit.getItemFactory().createItemStack("minecraft:netherite_pickaxe[can_break={blocks:['minecraft:beacon']}]");
        ItemStack lamp = Bukkit.getItemFactory().createItemStack("minecraft:lantern");
        player.getInventory().addItem(pic);
        player.getInventory().setItemInOffHand(lamp);

        // Set up objectives
        objectives = new ArrayList<>();
        Collections.shuffle(points);
        Random r = new Random();
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            for (int i = 0; i < numObjectives; ++i) {
                BlockVector3 pos = points.get(i);
                pos = BlockVector3.at(pos.x(), lava_max - r.nextInt((lava_max - lava) / 2), pos.z());
                CuboidRegion region = new CuboidRegion(pos.add(BlockVector3.at(1, 0, 1)), pos.add(-1, 0, -1));
                objectives.add(region);

                Region pillar = new CuboidRegion(pos, BlockVector3.at(pos.x(), bottom, pos.z()));
                edit.setBlocks(pillar, BlockTypes.get(pillarBlock));
                edit.setBlocks((Region) region, BlockTypes.BEACON);
            }
        }

        clock2 = System.currentTimeMillis();
    }

    @Override
    public void step() {
        // Check beacons
        int done = 0;
        for (CuboidRegion r : objectives) {
            int air = (int) r.stream().filter(b -> world.getBlock(b).isAir()).count();
            if (air > 0 && air < 9) { // Destroyed at least one block
                try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
                    edit.setBlocks((Region) r, BlockTypes.AIR);
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                    if (sandboxPlayer != null) {
                        sandboxPlayer.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                    }
                }
                air = 9;
            }
            if (air == 9) {
                done += 1;
            }
        }

        if (done == objectives.size()) {
            long time = System.currentTimeMillis() - clock2;
            player.sendMessage("Completed lava game in %.0f seconds".formatted((float) time / 1000));
            Bukkit.getLogger().info("Completed lava game in %.0f seconds".formatted((float) time / 1000));
            end(true);
            return;
        }

        if (useLava) {
            // Lava rise
            long time = System.currentTimeMillis();
            if (time >= clock + lavaDelay) {
                clock = time;
                lava++;

                // Lava fill
                try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
                    CuboidRegion r = new CuboidRegion(BlockVector3.at(x1, lava, z1), BlockVector3.at(x2, 0, z2));
                    edit.replaceBlocks(r,  air_and_water, BlockTypes.LAVA);
                }
            }

            // Loss check
            if (lava >= lava_max - 1 || player.isDead()) {
                end(false);
                return;
            }

            // Make player invulnerable unless below lava level
            if (player.getLocation().getY() < lava) {
                if (player.isInvulnerable()) {
                    player.setInvulnerable(false);
                }
            }
            else {
                if (!player.isInvulnerable()) {
                    player.setInvulnerable(true);
                }
            }
        }
    }

    @Override
    public void end(boolean win) {
        done = true;
        if (win) {
            player.sendMessage("You Win!");
        }
        else {
            player.sendMessage("You lose!");
        }
        player.setInvulnerable(false);
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        for (CuboidRegion r : objectives) {
            // Override if at or below beacon, but not if above
            if (r.contains(x, y, z) && y <= r.getCenter().y()) {
                return true;
            }
            Vector3 center = r.getCenter();
            if (center.x() == x && center.z() == z && center.y() > y) { // Pillar
                return true;
            }
        }
        if (useLava && y <= lava && (block.isAir() || block.getBlockType() == BlockTypes.WATER)) {
            edit.setBlock(x,y,z, BlockTypes.LAVA);
            return true;
        }
        return false;
    }
}
