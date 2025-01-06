package net.micaxs.skyfactoryisland;

import com.mojang.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.UUID;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(SkyFactoryIsland.MODID)
public class SkyFactoryIsland
{
    public static final String MODID = "skyfactoryisland";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Long> lastUsageTime = new ConcurrentHashMap<>();



    public SkyFactoryIsland()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("SkyFactory Island Loaded");
    }


    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
        }
    }


    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {

        // The /island command to generate an island as a new player.
        event.getDispatcher().register(
                Commands.literal("island")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            UUID playerUUID = player.getUUID();

                            File worldFolder = context.getSource().getServer().getWorldPath(LevelResource.ROOT).toFile();
                            File dataDir = new File(worldFolder, "sky_factory_island");
                            if (!dataDir.exists()) {
                                dataDir.mkdirs();
                            }
                            File dataFile = new File(dataDir, "spawn_locations.json");
                            Data data = loadData(dataFile);

                            Coords playerCoords = data.islandCoords.get(playerUUID.toString());
                            boolean hasIsland = playerCoords != null;

                            if (!hasIsland) {
                                // Generate a new Island
                                // get new coords for player's new island.
                                playerCoords = generateUniqueCoords(data, worldFolder);

                                // save the new island coords to the player's data.
                                savePlayerIslandCoords(playerUUID, playerCoords.x, playerCoords.y, playerCoords.z, worldFolder);
                                saveLastKnownCoords(playerCoords.x, playerCoords.y, playerCoords.z, worldFolder);

                                // Teleport the player to their new island.
                                executeCommand(context.getSource(), "/execute in minecraft:overworld run tp " + playerUUID + " " + (playerCoords.x + 2) + " 73 " + (playerCoords.z + 2));

                                // Generate the structure sicne new island.
                                executeCommand(context.getSource(), "/place template minecraft:skyblock_spawn_structure/starting_tree " + playerCoords.x + " 64 " + playerCoords.z);
                                executeCommand(context.getSource(), "/fill " + (playerCoords.x + 2) + " 63 " + (playerCoords.z + 2) + " " + (playerCoords.x + 2) + " 63 " + (playerCoords.z + 2) + " minecraft:bedrock");

                                final String command = "Generating a new island for " + player.getName().getString() + "!";
                                context.getSource().sendSuccess(() -> Component.literal(command), false);

                                return 1;
                            } else {

                                // Teleport the player to their existing island.
                                executeCommand(context.getSource(), "/execute in minecraft:overworld run tp " + playerUUID + " " + (playerCoords.x + 2) + " 73 " + (playerCoords.z + 2));

                                final String command = "Teleporting you to your own island!";
                                context.getSource().sendSuccess(() -> Component.literal(command), false);

                                return 1;
                            }

                        })
        );

        event.getDispatcher().register(
                Commands.literal("emergency")
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
                                executeCommand(context.getSource(), "/give " + playerName + " " + registryName);
                            });

                            if (Config.giveCheckBook) {
                                executeCommand(context.getSource(), "/give " + playerName + " checklist:task_book");
                            }

                            return 1;
                        })
        );
    }



    private void executeCommand(CommandSourceStack source, String command) {
        source.getServer().getCommands().performPrefixedCommand(
                source.getServer().createCommandSourceStack(),
                command
        );
    }



    private Coords generateUniqueCoords(Data data, File worldFolder) {
        int y = Config.islandHeight;
        int increment = Config.islandDistance;
        //int increment = 2000;
        int x = data.lastKnownCoords.x;
        int z = data.lastKnownCoords.z;
        int step = data.step;
        step++;


        int layer = (int) Math.ceil((Math.sqrt(step + 1) - 1) / 2);
        int layerStart = 4 * layer * (layer - 1) + 1;
        int positionInLayer = step - layerStart;

        int sideLength = 2 * layer;
        int side = positionInLayer / sideLength;
        int offset = positionInLayer % sideLength - layer;

        switch (side) {
            case 0:
                x = layer;
                z = offset;
                break;
            case 1:
                x = -offset;
                z = layer;
                break;
            case 2:
                x = -layer;
                z = -offset;
                break;
            case 3:
                x = offset;
                z = -layer;
                break;
        }

        if (x == 0 && z == 0) {
            data.step++;
            return generateUniqueCoords(data, worldFolder);
        }

        data.step++;
        return new Coords(x * increment, y, z * increment);
    }



    private void savePlayerIslandCoords(UUID playerUUID, int x, int y, int z, File worldFolder) {
        File file = new File(worldFolder, "sky_factory_island/spawn_locations.json");
        Data data = loadData(file);
        data.islandCoords.put(playerUUID.toString(), new Coords(x, y, z));

        data.step++;

        saveData(file, data);
    }

    private void saveLastKnownCoords(int x, int y, int z, File worldFolder) {
        File file = new File(worldFolder, "sky_factory_island/spawn_locations.json");
        Data data = loadData(file);
        data.lastKnownCoords = new Coords(x, y, z);
        saveData(file, data);
    }

    private Data loadData(File file) {
        Data data = new Data();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                data = GSON.fromJson(reader, Data.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load data from file", e);
            }
        }

        // Ensure step is initialized properly if the file doesn't exist or is corrupted
        if (data.lastKnownCoords == null) {
            data.lastKnownCoords = new Coords(0, 64, 1000);
        }

        return data;
    }

    private void saveData(File file, Data data) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save data to file", e);
        }
    }

    private static class Data {
        private Coords lastKnownCoords;
        private final Map<String, Coords> islandCoords = new HashMap<>();
        private int step = 0;
    }

    private static class Coords {
        private final int x;
        private final int y;
        private final int z;

        public Coords(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }


}
