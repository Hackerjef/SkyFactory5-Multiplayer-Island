package dev.nadie.skyfactoryisland;

import com.buuz135.together_forever.api.ITogetherTeam;
import com.buuz135.together_forever.api.TogetherForeverAPI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

import dev.nadie.skyfactoryisland.data.Config;
import dev.nadie.skyfactoryisland.database.DatabaseManager;
import dev.nadie.skyfactoryisland.database.orm.PlayerIsland;
import dev.nadie.skyfactoryisland.utils.IslandUtils;
import dev.nadie.skyfactoryisland.utils.Utils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(SkyFactoryIsland.MODID)
public class SkyFactoryIsland
{
    public static final String MODID = "skyfactoryisland";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> lastUsageTime = new ConcurrentHashMap<>();

    public SkyFactoryIsland(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        DatabaseManager.getInstance().initialize(event.getServer().getWorldPath(LevelResource.ROOT).toFile());
        LOGGER.info("SkyFactory Island Loaded");
    }

    @SubscribeEvent
    public void onServerShutdown(ServerStoppingEvent event)
    {
        DatabaseManager.getInstance().close();
        LOGGER.info("SkyFactory Island Unloaded");
    }



    // TODO: Enable logging/messaging for commands n such
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> islandcmd = LiteralArgumentBuilder.literal("island");

        islandcmd
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ITogetherTeam team = TogetherForeverAPI.getInstance().getPlayerTeam(player.getUUID());
                    PlayerIsland island = null;

                    if (team != null) {
                        // If the player is in a team, try to get the island of the team owner
                        island = IslandUtils.getIslandByPlayerUUID(team.getOwner()).orElse(null);
                    } else {
                        // If not in a team, get the player's own island
                        island = IslandUtils.getIslandByPlayerUUID(player.getUUID()).orElse(null);
                    }

                    if (island == null) {
                        return 1;
                    }

                    // Teleport player to island
                    player.teleportTo(player.serverLevel(), island.getCoords().getX() + 2, 73, island.getCoords().getZ() + 2, 0, 0);
                    return 1;
                });


        islandcmd.then(
                Commands.literal("delete")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PlayerIsland island = IslandUtils.getIslandByPlayerUUID(player.getUUID()).orElse(null);

                            if (island == null) {
                                context.getSource().sendFailure(Component.literal("You do not have an island to delete."));
                                return 0;
                            }

                            try {
                                island.delete();
                                DatabaseManager.getInstance().getPlayerIslandDao().update(island);
                                return 1;
                            } catch (SQLException e) {
                                LOGGER.error("Error deleting island for player {}: {}", player.getName().getString(), e.getMessage());
                                return 0;
                            }
                        })
        );

        islandcmd.then(
                Commands.literal("create")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ITogetherTeam team = TogetherForeverAPI.getInstance().getPlayerTeam(player.getUUID());
                            PlayerIsland island = IslandUtils.getIslandByPlayerUUID(player.getUUID()).orElse(null);

                            if (island != null) {
                                boolean isTeamOwner = Objects.requireNonNull(team).getOwner() == player.getUUID();
                                String message = isTeamOwner
                                        ? "You already have an island. Use /island delete to remove it | you are the team owner so remember if you delete it, the team island will be deleted too"
                                        : "You already have an island. Use /island delete to remove it";
                                context.getSource().sendFailure(Component.literal(message));
                                return 1;
                            }

                            if (team != null && team.getOwner() != player.getUUID()) {
                                context.getSource().sendFailure(Component.literal("Only the team owner can create an island."));
                                return 1;
                            }

                            island = IslandUtils.createIsland(player);
                            return 1;
                        })
        );

        event.getDispatcher().register(islandcmd);

        event.getDispatcher().register(
                Commands.literal("emergency")
                        .requires(source -> source.getEntity() instanceof ServerPlayer) // Ensure only players can execute
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            UUID playerUUID = player.getUUID();
                            long currentTime = System.currentTimeMillis();
                            long lastUsed = lastUsageTime.getOrDefault(playerUUID, 0L);

                            if (currentTime - lastUsed < TimeUnit.HOURS.toMillis(1)) {
                                context.getSource().sendFailure(Component.literal("You can only use this command once every hour."));
                                return 0;
                            }

                            lastUsageTime.put(playerUUID, currentTime);

                            String playerName = player.getName().getString();

                            // Get the starter items from the config and for each item give it to the player
                            Config.starterItems.forEach(item -> {
                                String registryName = BuiltInRegistries.ITEM.getKey(item).toString();
                                Utils.executeCommand(Objects.requireNonNull(player.getServer()),  "/give " + playerName + " " + registryName);
                            });

                            if (Config.giveCheckBook) {
                                Utils.executeCommand(Objects.requireNonNull(player.getServer()), "/give " + playerName + " checklist:task_book");
                            }

                            return 1;
                        })
        );
    }
}
