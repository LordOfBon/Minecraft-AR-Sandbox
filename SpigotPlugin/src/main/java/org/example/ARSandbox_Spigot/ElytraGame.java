package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ElytraGame extends Game {
    private final long timeLimit;
    private long startTime;
    private BossBar bar;
    private final CuboidRegion region;
    private final Player viewer;
    private final int ceiling;
    private final BlockMask air;

    protected ElytraGame(Player player, World world, FileConfiguration config, Player viewer, CuboidRegion region) {
        super(player, world, config);
        this.viewer = viewer;
        this.timeLimit = config.getLong("elytra_game_time");
        this.region = region.clone();
        this.region.expand(BlockVector3.at(-1, -1, -1));
        ceiling = region.getMaximumY() - (int)(((float)(region.getMaximumY() - region.getMinimumY())) / 5.0f);
        air = new BlockMask();
        air.add(BlockTypes.AIR);
    }

    @Override
    public void start() {
        bar = Bukkit.createBossBar("Time", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);
        if (viewer != null) {
            bar.addPlayer(viewer);
        }
        PlayerInventory inv = player.getInventory();
        inv.setChestplate(Bukkit.getItemFactory().createItemStack("elytra"));
        ItemStack fireworks = Bukkit.getItemFactory().createItemStack("firework_rocket");
        fireworks.setAmount(64);
        inv.addItem(fireworks);
        startTime = System.currentTimeMillis();

        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            edit.makeCuboidFaces(region, BlockTypes.GLASS);
            int maxX = region.getMaximumX();
            int minX = region.getMinimumX();
            int maxZ = region.getMaximumZ();
            int minZ = region.getMinimumZ();
            int maxY = region.getMaximumY();
            CuboidRegion top = new CuboidRegion(BlockVector3.at(maxX, maxY, maxZ), BlockVector3.at(minX, maxY, minZ));
            edit.makeCuboidFaces(top, BlockTypes.AIR);
            CuboidRegion ceilingPlane = new CuboidRegion(BlockVector3.at(maxX - 1, ceiling, maxZ - 1), BlockVector3.at(minX + 1, ceiling, minZ + 1));
            edit.replaceBlocks(ceilingPlane, air, BlockTypes.BARRIER);
        }
    }

    @Override
    public void step() {
        if (player.isDead()) {
            end(false);
        }

        long time = System.currentTimeMillis() - startTime;
        if (time >= timeLimit) {
            end(true);
        }
        else {
            bar.setProgress((double) time / (double) timeLimit);
        }
    }

    @Override
    public void end(boolean win) {
        done = true;
        bar.removeAll();
        if (win) {
            player.sendMessage("You win!");
        } else {
            player.sendMessage("You lost!");
        }
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            edit.makeCuboidFaces(region, BlockTypes.AIR);
        }
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        if (y == ceiling) {
            if (block.isAir()) {
                edit.setBlock(x, y, z, BlockTypes.BARRIER);
                return true;
            }
        }
        return false;
    }
}
