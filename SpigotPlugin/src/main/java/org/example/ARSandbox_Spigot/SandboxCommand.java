package org.example.ARSandbox_Spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SandboxCommand implements CommandExecutor {
    private final ARSandbox_Spigot plugin;

    public SandboxCommand(ARSandbox_Spigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length >= 1) {
            switch (strings[0]) {
                case "restart" -> {
                    plugin.restart();
                    return true;
                }
                case "stop" -> {
                    plugin.stop();
                    return true;
                }
                case "bps" -> {
                    plugin.togglePrintBPS();
                    return true;
                }
                case "top" -> {
                    plugin.moveToTop = !plugin.moveToTop;
                    return true;
                }
                case "game" -> {
                    if (commandSender instanceof Player && strings.length >= 2) {
                        plugin.refreshConfig();
                        switch (strings[1]) {
                            case "lava" -> {
                                boolean useLava = !(strings.length == 3 && strings[2].equals("nolava"));
                                plugin.startGame(new LavaGame((Player) commandSender, plugin.socketHandler.world, plugin.config, plugin.socketHandler.region, plugin.lockedPlayer, useLava));
                                commandSender.sendMessage("Starting lava game");
                            }
                            case "build" -> {
                                plugin.startGame(new BuildGame((Player) commandSender, plugin.socketHandler.world, plugin));
                                commandSender.sendMessage("Starting build game");
                            }
                            case "defense" -> {
                                plugin.startGame(new DefenseGame((Player) commandSender, plugin.socketHandler.world, plugin.config));
                                commandSender.sendMessage("Starting defense game");
                            }
                            case "elytra" -> {
                                plugin.startGame(new ElytraGame((Player) commandSender, plugin.socketHandler.world, plugin.config, plugin.lockedPlayer, plugin.socketHandler.region));
                                commandSender.sendMessage("Starting elytra game");
                            }
                            case "hoglin" -> {
                                boolean whackamole = strings.length == 3 && strings[2].equals("mole");
                                plugin.startGame(new HoglinGame((Player) commandSender, plugin.socketHandler.world, plugin.lockedPlayer, plugin.config, whackamole, plugin.socketHandler.region));
                                commandSender.sendMessage("Starting hoglin game");
                            }
                            case "minecart" -> {
                                plugin.startGame(new MinecartGame((Player) commandSender, plugin.socketHandler.world, plugin, plugin.socketHandler.region));
                                commandSender.sendMessage("Starting minecart game");
                            }
                            case "stop" -> {
                                if (plugin.game != null) {
                                    plugin.game.end(false);
                                } else {
                                    commandSender.sendMessage("No game running");
                                }
                            }
                        }
                        return true;
                    }
                    return false;
                }
                case "lock"-> {
                    if (commandSender instanceof Player) {
                        plugin.lockedLocation = ((Player) commandSender).getLocation();
                        plugin.lockedPlayer = (Player) commandSender;
                        commandSender.sendMessage("Locking in place");
                        return true;
                    }
                    return false;
                }
                case "unlock" -> {
                    plugin.lockedPlayer = null;
                    plugin.lockedLocation = null;
                    commandSender.sendMessage("Unlocking");
                    return true;
                }
            }
        }
        return false;
    }
}
