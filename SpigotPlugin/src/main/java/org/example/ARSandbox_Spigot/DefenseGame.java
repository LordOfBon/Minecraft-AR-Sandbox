package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class DefenseGame extends Game {

    private final long timeLimit;
    private long startTime;
    private BossBar bar;
    private ArrayList<Entity> villagers;
    private final org.bukkit.Location villagerSpawn;

    protected DefenseGame(Player player, World world, FileConfiguration config) {
        super(player, world, config);
        this.villagerSpawn = player.getLocation();
        this.timeLimit = config.getLong("defense_game_time");
    }

    @Override
    public void start() {
        startTime = System.currentTimeMillis();
        bar = Bukkit.createBossBar("Time", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);

        spawnVillagers();
    }

    @Override
    public void step() {
        long time = System.currentTimeMillis() - startTime;

        if (time >= timeLimit) {
            end(true);
        }
        else {
            bar.setProgress((double) time / (double) timeLimit);

            if (villagers.stream().allMatch(Entity::isDead)) {
                end(false);
            }
        }
    }

    @Override
    public void end(boolean win) {
        // Here win means shovel win
        done = true;
        bar.removeAll();
        for (Entity v : villagers) {
            v.setGlowing(false);
        }
        if (win) {
            player.sendMessage("Shovel player wins!");
        } else {
            player.sendMessage("Card player wins!");
        }
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        return false;
    }

    private void spawnVillagers() {
        villagers = new ArrayList<>();
        org.bukkit.World world = Bukkit.getWorld("world");
        assert world != null;
        for (int i = 0; i < 10; ++i) {
            villagers.add(world.spawnEntity(villagerSpawn, EntityType.VILLAGER));
            villagers.get(i).setGlowing(true);
        }
    }
}
