package dev.nadie.skyfactoryisland.utils;

import dev.nadie.skyfactoryisland.data.Config;
import dev.nadie.skyfactoryisland.data.Coords;
import dev.nadie.skyfactoryisland.database.DatabaseManager;
import dev.nadie.skyfactoryisland.database.orm.PlayerIsland;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class IslandUtils {
    private static final String ISLAND_TEMPLATE_ID = "skyblock_spawn_structure/starting_tree";


    public static PlayerIsland createIsland(ServerPlayer player) {
        Coords cord = generateUniqueCoords();
        PlayerIsland island = new PlayerIsland(player.getUUID(), cord.getX(), cord.getY(), cord.getZ(), true);
        try {
            DatabaseManager.getInstance().getPlayerIslandDao().create(island);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        createIslandStructureAndTeleport(player, island);

        return island;
    }

    public static Optional<PlayerIsland> getIslandByPlayerUUID(UUID playerUUID) {
        if (playerUUID == null) {
            return Optional.empty();
        }
        try {
            return DatabaseManager.getInstance().getPlayerIslandDao()
                    .queryBuilder()
                    .where()
                    .eq("playerUUID", playerUUID)
                    .and()
                    .eq("isActive", true)
                    .query()
                    .stream()
                    .findFirst();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    private static void createIslandStructureAndTeleport(ServerPlayer player, PlayerIsland island) {
        ResourceLocation templateId = ResourceLocation.fromNamespaceAndPath("minecraft", ISLAND_TEMPLATE_ID);


        Optional<StructureTemplate> template = player.serverLevel().getStructureManager().get(templateId);
        if (template.isEmpty()) {
            System.err.println("Island template not found: " + ISLAND_TEMPLATE_ID);
            return;
        }

        System.out.println("Placing island: " + island.toString());
        player.teleportTo(player.serverLevel(), island.getCoords().getX() + 2, 73, island.getCoords().getZ() + 2, 0, 0);
        BlockPos pos = new BlockPos(island.getCoords().getX(), island.getCoords().getY(), island.getCoords().getZ());
        template.get().placeInWorld(
                player.serverLevel(),
                pos,
                pos,
                new StructurePlaceSettings(),
                player.serverLevel().random,
                2
        );
    }

    private static Coords generateUniqueCoords() {
        int y = Config.islandHeight;
        int increment = Config.islandDistance;

        PlayerIsland island = null;
        try {
            var islands = DatabaseManager.getInstance().getPlayerIslandDao()
                    .queryBuilder()
                    .orderBy("id", false) // Assuming "id" is the primary key
                    .limit(1L).query();
            if (!islands.isEmpty()) {
                island = islands.get(0);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        int x;
        int z;
        int step;
        if (island == null) {
            x = 0;
            z = 1000;
            step = 0;
        } else {
            x = island.getCoords().getX();
            z = island.getCoords().getZ();
            step = island.getId();
        }
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

        return new Coords(x * increment, y, z * increment);
    }
}
