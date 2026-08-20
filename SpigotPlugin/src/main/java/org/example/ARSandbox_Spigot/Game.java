package org.example.ARSandbox_Spigot;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public abstract class Game {
    public boolean done = false;
    protected final Player player;
    protected final World world;
    protected final FileConfiguration config;

    protected Game(Player player, World world, FileConfiguration config) {
        this.player = player;
        this.world = world;
        this.config = config;
    }

    abstract public void start();
    abstract public void step();
    abstract public void end(boolean win);
    abstract public boolean blockOverride(BlockState block, EditSession edit, int x, int y, int z);

    public void onBlockReset() {}
}
