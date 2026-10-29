package rotp.addons.whitealbum;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class WhiteAlbumTags {
    public static final TagKey<EntityType<?>> IRREFLECTABLE =
            TagKey.create(
                    Registries.ENTITY_TYPE,
                    WhiteAlbumAddon.id("irreflectable"));
    public static final TagKey<Block> ICE_SPEED =
            TagKey.create(
                    Registries.BLOCK,
                    WhiteAlbumAddon.id("ice_speed"));
    public static final TagKey<Block> ICE_LAYER_SPEED =
            TagKey.create(
                    Registries.BLOCK,
                    WhiteAlbumAddon.id("ice_layer_speed"));
    public static final TagKey<Block> ICE_BREAK =
            TagKey.create(
                    Registries.BLOCK,
                    WhiteAlbumAddon.id("ice_break"));

    private WhiteAlbumTags() {}
}
