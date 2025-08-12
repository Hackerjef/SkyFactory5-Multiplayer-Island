package dev.nadie.skyfactoryisland;

import com.mojang.logging.LogUtils;

import dev.nadie.skyfactoryisland.data.Config;
import dev.nadie.skyfactoryisland.database.DatabaseManager;
import dev.nadie.skyfactoryisland.database.orm.PlayerIsland;
import dev.nadie.skyfactoryisland.utils.IslandUtils;
import dev.nadie.skyfactoryisland.utils.Utils;
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


    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("island")
                        .requires(source -> source.getEntity() instanceof ServerPlayer) // Ensure only players can execute
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            PlayerIsland island = null;
                            try {
                                island = DatabaseManager.getInstance().getPlayerIslandDao()
                                        .queryBuilder()
                                        .where()
                                        .eq("playerUUID", player.getUUID())
                                        .eq("isActive", true)
                                        .queryForFirst();
                            } catch (SQLException e) {
                                // TODO: add message and fail out better
                                LOGGER.error("Failed to query player island", e);
                                return 1;
                            }
                            if (island != null) {
                                // TODO: customize spawn location/make safe :D
                                // TODO: add message
                                player.teleportTo(player.serverLevel(), island.getCoords().getX() + 2, 73, island.getCoords().getZ() + 2, 0, 0);
                                return 1;
                            }

                            // TODO: add tofe support to get island of owner of team, if owner has island teleport, if not tell player that the team owner needs to create the island before continuing, if not on a team, create island for player

//                            PlayerIsland island = IslandUtils.createIsland(player);
                            return 1;
                        })
        );

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
