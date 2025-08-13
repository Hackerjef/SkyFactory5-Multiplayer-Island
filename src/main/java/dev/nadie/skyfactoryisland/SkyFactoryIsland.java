package dev.nadie.skyfactoryisland;

import com.buuz135.together_forever.api.ITogetherTeam;
import com.buuz135.together_forever.api.TogetherForeverAPI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

import dev.nadie.skyfactoryisland.data.Config;
import dev.nadie.skyfactoryisland.database.DatabaseManager;
import dev.nadie.skyfactoryisland.database.orm.PlayerEmergency;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(SkyFactoryIsland.MODID)
public class SkyFactoryIsland
{
    public static final String MODID = "skyfactoryisland";
    private static final Logger LOGGER = LogUtils.getLogger();

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
        LiteralArgumentBuilder<CommandSourceStack> islandcmd = LiteralArgumentBuilder.literal("island");

        islandcmd
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ITogetherTeam team = TogetherForeverAPI.getInstance().getPlayerTeam(player.getUUID());
                    PlayerIsland island;
                    if (team != null) {
                        // If the player is in a team, try to get the island of the team owner
                        island = IslandUtils.getIslandByPlayerUUID(team.getOwner()).orElse(null);
                    } else {
                        // If not in a team, get the player's own island
                        island = IslandUtils.getIslandByPlayerUUID(player.getUUID()).orElse(null);
                    }
                    
                    if (island == null) {
                        String teamMessage = null;
                        if (team != null) {
                            teamMessage = (team.getOwner().equals(player.getUUID())
                                    ? "You do not have an island. You are the team owner and can create an island for your team using /island create."
                                    : "You do not have an island. You are in a team. Only the team owner can create an island for the team using /island create. Ask the team owner to create one.");
                        }
                        String message = team != null
                                ? teamMessage
                                : "You do not have an island. Use /island create to create one";

                        context.getSource().sendFailure(Component.literal(message));
                        return 1;
                    }

                    // Teleport player to island
                    player.teleportTo(player.serverLevel(), island.getCoords().getX() + 2, 73, island.getCoords().getZ() + 2, 0, 0);
                    context.getSource().sendSuccess(() -> Component.literal("Teleported to Island"), false);
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
                                context.getSource().sendSuccess(() -> Component.literal("Island deleted successfully."), false);
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

                            boolean isTeamOwner = team != null && team.getOwner().equals(player.getUUID());

                            if (island != null) {
                                String teamMessage = null;
                                if (team != null) {
                                    teamMessage = isTeamOwner
                                            ? "You already have an island. Use /island delete to remove it | you are the team owner so remember if you delete it, the team island will be deleted too."
                                            : "You are part of a team that already has an island. Only the team owner can create/delete an island for the team";
                                }
                                String message = team != null
                                        ? teamMessage
                                        : "You already have an island. Use /island delete to remove it";
                                context.getSource().sendFailure(Component.literal(message));
                                return 1;
                            }

                            if (team != null && !isTeamOwner) {
                                context.getSource().sendFailure(Component.literal("You are not the team owner. Only the team owner can create an island for the team."));
                                return 1;
                            }

                            PlayerIsland finalIsland  = IslandUtils.createIsland(player);
                            context.getSource().sendSuccess(() -> Component.literal("Island created at: " + finalIsland.getCoords().getX() + ", " + finalIsland.getCoords().getY() + ", " + finalIsland.getCoords().getZ()), false);
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

                            long lastUsed;
                            try {
                                 Optional<PlayerEmergency> data = DatabaseManager.getInstance().getPlayerEmergencyDao()
                                        .queryBuilder()
                                        .where()
                                        .eq("playerUUID", playerUUID)
                                        .query()
                                        .stream()
                                        .findFirst();

                                 if (data.isEmpty()) {
                                     lastUsed = 0L;
                                 } else {
                                     lastUsed = data.get().getTime();
                                 }

                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }

                            if (currentTime - lastUsed < TimeUnit.HOURS.toMillis(5)) {
                                context.getSource().sendFailure(Component.literal("You can only use this command once every five hours."));
                                return 0;
                            }

                            try {
                                DatabaseManager.getInstance().getPlayerEmergencyDao().createOrUpdate(new PlayerEmergency(playerUUID, currentTime));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }

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
