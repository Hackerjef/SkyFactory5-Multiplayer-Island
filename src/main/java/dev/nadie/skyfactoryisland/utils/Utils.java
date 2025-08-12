package dev.nadie.skyfactoryisland.utils;

import net.minecraft.server.MinecraftServer;

public class Utils {
    public static void executeCommand(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                command
        );
    }
}
