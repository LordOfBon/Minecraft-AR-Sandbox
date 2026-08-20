package org.example.ARSandbox_Spigot;

import com.fastasyncworldedit.core.FaweAPI;
import com.fastasyncworldedit.core.extent.processor.lighting.RelightMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.Msg;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

enum MessageTypes {
    Palette,
    Update,
    Reset,
    Spawn
}

public class SocketHandler {

    private static final int port = 4960;

    private final ARSandbox_Spigot plugin;

    public final World world;

    private ZContext context;
    private ZMQ.Socket socket;
    private boolean requestReset;
    private boolean waiting = false;
    private double blocksPerSec = 0.0;
    private long lastTime;
    public CuboidRegion region;

    private ArrayList<BlockState> blockStates;
    private Biome biome;

    public SocketHandler(ARSandbox_Spigot plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        reset();
    }

    public void tick() {
        long time = System.currentTimeMillis();
        try {
            if (!waiting) {
                if (requestReset) {
                    socket.send("Restart Pls");
                    requestReset = false;
                } else {
                    socket.send("Update Pls");
                }
                waiting = true;
            }
        }
        catch (ZMQException e) {
            Bukkit.getLogger().severe(e.toString());
        }
        receiving:
        while (true) {
            try {
                Msg m = socket.recvMsg(ZMQ.DONTWAIT);
                if (m == null) {
                    break;
                }
                MessageTypes type = MessageTypes.values()[m.buf().order(ByteOrder.LITTLE_ENDIAN).getInt()];
                switch (type) {
                    case Palette -> {
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        if (m == null) {
                            waiting = false;
                            break receiving;
                        }
                        this.biome = Registry.BIOME.get(NamespacedKey.minecraft(new String(m.data(), ZMQ.CHARSET)));
                        blockStates = new ArrayList<>();
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        int length = m.buf().order(ByteOrder.LITTLE_ENDIAN).getInt();
                        for (int i = 0; i < length; ++i) {
                            m = socket.recvMsg(ZMQ.DONTWAIT);
                            if (m == null) {
                                waiting = false;
                                break receiving;
                            }
                            String name = new String(m.data(), ZMQ.CHARSET);
                            blockStates.add(BlockState.get(name));
                        }
                        setBiome();
                    }
                    case Update -> {
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        if (m == null) {
                            waiting = false;
                            break receiving;
                        }
                        IntBuffer buffer = m.buf().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                        double blocks = (double)(buffer.limit() / 4);
                        blocksPerSec = blocksPerSec * 0.75 + 0.25 * (blocks / (time - lastTime) * 1000.0);
                        updateFromBuffer(buffer);

                    }
                    case Reset -> {
                        Bukkit.getLogger().info("Reset");
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        if (m == null) {
                            waiting = false;
                            break receiving;
                        }
                        IntBuffer buff = m.buf().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                        BlockVector3 corner1 = BlockVector3.at(buff.get(), buff.get(), buff.get());
                        BlockVector3 corner2 = BlockVector3.at(buff.get(), buff.get(), buff.get());
                        Bukkit.getLogger().info("Block Region: " + corner1 + ", " + corner2);
                        region = new CuboidRegion(world, corner1, corner2);
                        resetBlocks();
                    }
                    case Spawn -> {
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        if (m == null) {
                            waiting = false;
                            break receiving;
                        }
                        FloatBuffer buff = m.buf().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                        float x = buff.get();
                        float z = buff.get();
                        float y = (float) world.getHighestTerrainBlock((int) x, (int) z, -60, 100) + 6.0F;
                        m = socket.recvMsg(ZMQ.DONTWAIT);
                        if (m == null) {
                            waiting = false;
                            break receiving;
                        }
                        String entityName = new String(m.data(), ZMQ.CHARSET);
                        org.bukkit.World bukkitWorld = Bukkit.getWorld("world");
                        assert bukkitWorld != null;
                        EntityType entity = Registry.ENTITY_TYPE.match(entityName);
                        if (entity == null) {
                            Bukkit.getLogger().warning("Could not identify entity " + entityName);
                        } else {
                            bukkitWorld.spawnEntity(new org.bukkit.Location(bukkitWorld, x,y,z), entity);
                        }
                    }
                }
                if(m==null || !m.hasMore()){
                    waiting = false;
                    break;
                }
            }
            catch(ZMQException ignore){} // Keep looking for response if running
        }

        lastTime = time;
    }

    private void resetBlocks() {
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            edit.setBlocks(
                    (Region) region,
                    BlockState.get("minecraft:air")
            );
        }
        FaweAPI.fixLighting(world, region, null, RelightMode.NONE);
        FaweAPI.fixLighting(world, region, null, RelightMode.OPTIMAL);
        //world.fixLighting(region.getChunks());
        if (plugin.game != null) {
            plugin.game.onBlockReset();
        }
    }

    public void requestBlockReset() {
        requestReset = true;
    }

    public void stop() {
        socket.disconnect(String.format("tcp://127.0.0.1:%d", port));
        context.destroy();
    }

    public void reset() {
        if (context != null) {
            context.destroy();
            socket.close();
        }
        context = new ZContext();
        socket = context.createSocket(SocketType.REQ);
        String address = String.format("tcp://127.0.0.1:%d", port);
        socket.connect(address);
        requestReset = true;
        waiting = false;
        lastTime = System.currentTimeMillis();
    }

    public void printBPS() {
        Bukkit.getLogger().info("Blocks per second " + blocksPerSec);
    }

    public void updateFromBuffer(IntBuffer buffer){
        try (EditSession edit = WorldEdit.getInstance().newEditSession(world)) {
            while (buffer.hasRemaining()){
                int x = buffer.get();
                int y = buffer.get();
                int z = buffer.get();
                int b = buffer.get();

                BlockState block = blockStates.get(b);

                // Check to see if the game running overrides the block and skip normal block placement if so
                if (plugin.game != null && plugin.game.blockOverride(block, edit, x, y, z)) {
                    continue;
                }
                edit.setBlock(x,y,z, blockStates.get(b));
            }
        }
    }

    private void setBiome() {
        if (biome == null) { return; }
        Bukkit.getLogger().info("Biome: " + biome);
        org.bukkit.World world = Bukkit.getWorld("world");
        assert world != null;
        for (BlockVector3 loc : region) {
            world.setBiome(loc.x(), loc.y(), loc.z(), biome);
        }

    }
}
