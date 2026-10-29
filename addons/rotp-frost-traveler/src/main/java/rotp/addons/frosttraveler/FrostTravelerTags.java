package rotp.addons.frosttraveler;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class FrostTravelerTags {
    public static final TagKey<Item> EXCELLENT_MEAT =
            item("eggcelent_meat_source");
    public static final TagKey<Item> GOOD_MEAT =
            item("good_meat_source");
    public static final TagKey<Item> MID_MEAT =
            item("mid_meat_source");
    public static final TagKey<Item> BAD_MEAT =
            item("bad_meat_source");
    public static final TagKey<Block> HORIZONTAL =
            block("horizontal");
    public static final TagKey<Block> VERTICAL =
            block("vertical");
    public static final TagKey<EntityType<?>> NO_MEATABLE =
            TagKey.create(
                    Registries.ENTITY_TYPE,
                    FrostTravelerAddon.id("no_meatable"));

    private FrostTravelerTags() {}

    private static TagKey<Item> item(String path) {
        return TagKey.create(
                Registries.ITEM, FrostTravelerAddon.id(path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(
                Registries.BLOCK, FrostTravelerAddon.id(path));
    }
}
