package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class BuildGame extends Game {

    private final ARSandbox_Spigot plugin;
    private final List<String> prompts;
    private final List<String> startingItems;

    protected BuildGame(Player player, World world, ARSandbox_Spigot plugin) {
        super(player, world, plugin.config);
        this.plugin = plugin;
        prompts = plugin.config.getStringList("build_game_prompts");
        startingItems = plugin.config.getStringList("build_game_items");

    }

    @Override
    public void start() {
        Random rand = new Random();
        String prompt = prompts.get(rand.nextInt(prompts.size()));
        player.sendMessage("Prompt: " + prompt);
        player.setGameMode(GameMode.CREATIVE);
        for (String id : startingItems) {
            ItemStack stack = Bukkit.getItemFactory().createItemStack(id);
            player.getInventory().addItem(stack);
        }
    }

    @Override
    public void step() {

    }

    @Override
    public void end(boolean win) {
        // Here win means a normal finish
        done = true;
        if (win) {
            player.sendMessage("Time is up!");
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.sendMessage("Game ended prematurely.");
        }
        Bukkit.getLogger().info("Stopping socket due to end of build game");
        plugin.stop();
    }

    @Override
    public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z) {
        return false;
    }
}
