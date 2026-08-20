package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Random;

public class HoglinGame extends Game {

    private final long timeLimit;
    private final long hogs;
    private long startTime;
    private BossBar bar;
    private final ArrayList<Entity> mobs;
    private final Player viewer;
    private final boolean whackamole;
    private long clock;
    private long interval;
    private int delay;
    private int deleted = 0;

    private final int x1;
    private final int x2;
    private final int z1;
    private final int z2;

    private ArrayList<Boolean> deadHogs;

    static final int BUFFER = 10;

    protected HoglinGame(Player player, World world, Player viewer, FileConfiguration config, boolean whackamole, CuboidRegion region) {
        super(player, world, config);
        this.viewer = viewer;
        this.whackamole = whackamole;


        this.timeLimit = config.getLong("hoglin_game_time");
        this.hogs = config.getLong("hoglin_game_hogs");
        this.delay = -config.getInt("hoglin_mole_delay");
        mobs = new ArrayList<>();

        x2 = region.getMaximumX() - BUFFER;
        z2 = region.getMaximumZ() - BUFFER;
        x1 = region.getMinimumX() + BUFFER;
        z1 = region.getMinimumZ() + BUFFER;
    }

    @Override
    public void start() {
        startTime = System.currentTimeMillis();
        bar = Bukkit.createBossBar("Time", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);
        if (viewer != null) {
            bar.addPlayer(viewer);
        }

        if (whackamole) {
            clock = System.currentTimeMillis();
            spawnMobs(1);
            interval = timeLimit / (hogs + 10);
        }
        else {
            spawnMobs((int) hogs);
        }

        deadHogs = new ArrayList<>();
        for (int i = 0; i < hogs; ++i) {
            deadHogs.add(false);
        }
    }

    @Override
    public void step() {
        long time = System.currentTimeMillis();

        if (time - startTime >= timeLimit) {
            end(false);
        }
        else {
            bar.setProgress((double) (time - startTime) / (double) timeLimit);

            for (int i = 0; i < mobs.size(); ++i) {
                Entity h = mobs.get(i);
                if (h.isDead() && !deadHogs.get(i)) {
                    deadHogs.set(i, true);
                    player.playSound(player.getLocation(), Sound.ENTITY_PIG_DEATH, 0.4f, 1.1f);
                }
            }

            if (mobs.size() == hogs && mobs.stream().allMatch(Entity::isDead)) {
                end(true);
            }

            if (mobs.size() < hogs && time - clock >= interval) {
                spawnMobs(1);
                delay += 1;
                if (delay >= 0 && !mobs.get(delay).isDead()) {
                    ((Hoglin)mobs.get(delay)).damage(10.0);
                    deleted += 1;
                }
                clock = time;
            }
        }
    }

    @Override
    public void end(boolean win) {
        done = true;
        bar.removeAll();
        for (Entity v : mobs) {
            v.setGlowing(false);
        }
        long dead = 0;
        for (Entity mob : mobs) {
            if (mob.isDead()) {
                dead += 1;
            }
            else {
                mob.remove();
            }
        }
        player.sendMessage("You killed %d out of %d hoglins".formatted(dead - deleted, mobs.size()));
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        return false;
    }

    private void spawnMobs(int num) {
        org.bukkit.World world = Bukkit.getWorld("world");
        assert world != null;
        Random r = new Random();
        for (int i = 0; i < num; ++i) {
            int x = r.nextInt(x2 - x1) + x1;
            int z = r.nextInt(z2 - z1) + z1;
            Location pos = new Location(
                    world,
                    x,
                    world.getHighestBlockAt(x, z).getY() + 2,
                    z
            );
            Hoglin hog = (Hoglin) world.spawnEntity(pos, EntityType.HOGLIN);
            mobs.add(hog);
            hog.setGlowing(true);
            hog.setImmuneToZombification(true);
            hog.setHealth(0.5);
            hog.setMaximumAir((int) timeLimit + 1);
            hog.setRemainingAir((int) timeLimit + 1);
            hog.setCustomName("Hog");
            hog.setRemoveWhenFarAway(false);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 0.6f, 1.1f);
    }
}
