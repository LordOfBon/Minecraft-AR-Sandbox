package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

public final class ARSandbox_Spigot extends JavaPlugin {

    public SocketHandler socketHandler;
    private BukkitTask socketRunner;
    private BukkitTask printBPS;
    private BukkitTask runGame;
    private World world;
    public boolean moveToTop = false;
    public Game game;
    public FileConfiguration config = this.getConfig();
    public Location lockedLocation;
    public Player lockedPlayer;
    private int lockTimer = 20;

    @Override
    public void onEnable() {
        world = Bukkit.getWorld("world");
        com.sk89q.worldedit.world.World adapted_world = BukkitAdapter.adapt(world);
        socketHandler = new SocketHandler(this, adapted_world);

        restart();

        this.getCommand("sandbox").setExecutor(new SandboxCommand(this));
        this.getCommand("sandbox").setTabCompleter(new SandboxTabCompleter());

        config.addDefault("defense_game_time", 1000000L);
        config.addDefault("elytra_game_time", 100000L);

        config.addDefault("lava_pillar_block", "minecraft:glass");
        config.addDefault("lava_objectives", 3);
        config.addDefault("lava_delay", 8000L);
        config.addDefault("lava_goalGrid_width", 5);
        config.addDefault("lava_goalGrid_length", 3);

        config.addDefault("hoglin_game_time", 100000L);
        config.addDefault("hoglin_game_hogs", 50L);
        config.addDefault("hoglin_mole_delay", 5);

        config.addDefault("cart_respawn_time", 10000L);
        config.addDefault("cart_game_time", 100000L);
        config.addDefault("initial_cart_speed", 1.0);
        List<Vector> path1 = List.of(
                new Vector(0.25, 0.5, 0.5),
                new Vector(0.5, 0.7, 0.5),
                new Vector(0.75, 0.5, 0.5)
        );
        List<Vector> path2 = List.of(
                new Vector(0.8, 0.5, 0.4),
                new Vector(0.8, 0.6, 0.7),
                new Vector(0.4, 0.4, 0.7),
                new Vector(0.4, 0.6, 0.4)
        );
        List<Vector> path3 = List.of(
                new Vector(0.2, 0.5, 0.5),
                new Vector(0.35, 0.6, 0.5),
                new Vector(0.35, 0.7, 0.75),
                new Vector(0.5, 0.7, 0.75),
                new Vector(0.5, 0.5, 0.25),
                new Vector(0.6, 0.6, 0.25),
                new Vector(0.75, 0.7, 0.5)
        );
        config.addDefault("cart_path", List.of(path1, path2, path3));
        config.addDefault("cart_pillar_block", "minecraft:cherry_wood");

        config.addDefault("build_game_items", List.of("minecraft:oak_planks", "minecraft:water_bucket", "minecraft:stone", "minecraft:cobblestone"));
        config.addDefault("build_game_prompts", List.of("Resort House", "Wizard Tower", "Atlantis", "Golf Course", "Volcano", "Waterfall"));
        config.options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        stopGame();
        socketHandler.stop();
    }

    public void restart() {
        // Start task if stopped / not started
        if (socketRunner == null || socketRunner.isCancelled()) {
            socketRunner = new BukkitRunnable() {
                @Override
                public void run() {
                    socketHandler.tick();
                    if (lockedLocation != null && lockedPlayer != null) {
                        lockTimer -= 1;
                        if (lockTimer == 0) {
                            lockedPlayer.teleport(lockedLocation);
                            lockTimer = 10;
                        }
                    }
                }
            }
            .runTaskTimer(this, 0L, 0L);
        }
        socketHandler.reset();
    }

    public void stop() {
        socketRunner.cancel();
    }

    public void togglePrintBPS() {
        if (printBPS == null || printBPS.isCancelled()) {
            printBPS = new BukkitRunnable() {
                @Override
                public void run() {
                    socketHandler.printBPS();
                }
            }
            .runTaskTimer(this, 0L, 20L);
        }
        else {
            printBPS.cancel();
        }
    }

    public void startGame(Game newGame) {
        for (Player player : world.getPlayers()) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
        }
        newGame.player.setGlowing(true);
        if (runGame != null && !runGame.isCancelled()) {
            if (game != null) {
                game.end(false);
            }
            stopGame();
            Bukkit.getLogger().info("Stopping current game and starting new one");
        }
        runGame = new BukkitRunnable() {
            @Override
            public void run() {
                if (game.done) {
                    stopGame();
                    Bukkit.getLogger().info("Game has ended");
                }
                else {
                    game.step();
                    if (moveToTop) {
                        bringPlayersToTop();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 0L);
        game = newGame;
        game.start();
    }

    private void bringPlayersToTop() {
        for (Player player : world.getPlayers()) {
            Location plLoc = player.getLocation();
            double y = world.getHighestBlockAt(plLoc).getY() + 1;
            if (plLoc.getY() < y) {
                Location loc = new Location(world, plLoc.getX(), y, plLoc.getZ());
                player.teleport(loc);
            }
        }
    }

    public void refreshConfig() {
        reloadConfig();
        this.config = getConfig();
    }

    private void stopGame() {
        runGame.cancel();
        if (game != null) {
            game.player.setInvulnerable(false);
            game.player.getInventory().clear();
            game.player.setGlowing(false);
        }
        socketHandler.requestBlockReset();
        game = null;
    }
}
