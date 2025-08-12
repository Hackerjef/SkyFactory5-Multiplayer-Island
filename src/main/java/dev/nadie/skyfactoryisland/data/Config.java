package dev.nadie.skyfactoryisland.data;

import dev.nadie.skyfactoryisland.SkyFactoryIsland;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = SkyFactoryIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();


    private static final ForgeConfigSpec.IntValue ISLAND_DISTANCE = BUILDER
            .comment("The distance between each generated island.")
            .defineInRange("generation_distance", 2000, 1000, 25000);

    private static final ForgeConfigSpec.IntValue ISLAND_HEIGHT = BUILDER
            .comment("The height of each island to be generated.")
            .defineInRange("generation_height", 64, 0, 128);

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STARTER_ITEMS = BUILDER
            .comment("A list of items you can get every hour through emergency_items command (once per hour).")
            .defineListAllowEmpty("starter_items", List.of("colouredstuff:sapling_none", "minecraft:dirt"), Config::validateItemName);

    private static final ForgeConfigSpec.BooleanValue ADD_CHECKBOOK = BUILDER
            .comment("Should the Sky Factory 5 Checkbook be given along the emergency items.")
            .define("give_checkbook", true);


    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int islandDistance;
    public static int islandHeight;
    public static Set<Item> starterItems;
    public static boolean giveCheckBook;

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        islandDistance = ISLAND_DISTANCE.get();
        islandHeight = ISLAND_HEIGHT.get();

        // convert the list of strings into a set of items
        starterItems = STARTER_ITEMS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        giveCheckBook = ADD_CHECKBOOK.get();
    }
}
