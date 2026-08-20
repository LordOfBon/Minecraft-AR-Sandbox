package org.example.ARSandbox_Spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SandboxTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        List<String> list = new ArrayList<>();
        if (strings.length >= 2 && strings[0].equals("game")) {
            if (strings.length == 2) {
                list = List.of("lava", "minecart", "build", "defense", "elytra", "hoglin", "stop");
            }
            else if (strings.length == 3) {
                if (strings[1].equals("lava")) {
                    list = List.of("nolava");
                }
                else if (strings[1].equals("hoglin")) {
                    list = List.of("mole");
                }
            }
        }
        else if (strings.length == 1) {
            list = List.of("restart", "stop", "bps", "game", "top", "lock", "unlock");
        }
        else {
            list = List.of();
        }
        return list;
    }
}
