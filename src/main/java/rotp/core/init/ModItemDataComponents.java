package rotp.core.init;

import java.util.UUID;
import java.util.function.Supplier;

import rotp.core.core.JojoMod;
import rotp.core.item.cassette.CassetteData;
import rotp.core.item.cassette.WalkmanData;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.standarrow.StandArrowLore;
import rotp.core.mechanics.standarrow.StandArrowShardLore;
import rotp.core.mechanics.standdisc.StandWrittenOnDisc;
import rotp.core.subsystems.itemtracking.OriginalItemPosComponent;
import rotp.core.compat.v1_21_4.itemmodel.__ItemModelComponent;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItemDataComponents {
	public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, JojoMod.MOD_ID);
	
	public static final Supplier<DataComponentType<StandWrittenOnDisc>> DISC_STAND = DATA_COMPONENT_TYPES.registerComponentType("disc_stand", 
			builder -> builder
			.persistent(StandWrittenOnDisc.CODEC)
			.networkSynchronized(StandWrittenOnDisc.STREAM_CODEC)
			/*
			 * "cacheEncoding caches the encoding result of the Codec such that any subsequent encodes uses the cached value if the component value hasn't changed. 
			 * This should only be used if the component value is expected to rarely or never change."
			 */
			.cacheEncoding());

	public static final Supplier<DataComponentType<ClothesDataComponent>> CLOTHES_PIECE = DATA_COMPONENT_TYPES.registerComponentType("clothes", 
			builder -> builder
			.persistent(ClothesDataComponent.CODEC)
			.networkSynchronized(ClothesDataComponent.STREAM_CODEC)
			.cacheEncoding());

	public static final Supplier<DataComponentType<UUID>> TRACKER_ID = DATA_COMPONENT_TYPES.registerComponentType("tracker_id", 
			builder -> builder
			.persistent(UUIDUtil.CODEC)
			.networkSynchronized(UUIDUtil.STREAM_CODEC));

	public static final Supplier<DataComponentType<UUID>> GE_USER = DATA_COMPONENT_TYPES.registerComponentType("ge_user",
			builder -> builder
			.persistent(UUIDUtil.CODEC)
			.networkSynchronized(UUIDUtil.STREAM_CODEC));

	public static final Supplier<DataComponentType<OriginalItemPosComponent>> ORIGINAL_POS = DATA_COMPONENT_TYPES.registerComponentType("original_pos", 
			builder -> builder
			.persistent(OriginalItemPosComponent.CODEC)
			.networkSynchronized(OriginalItemPosComponent.STREAM_CODEC)
			.cacheEncoding());

	public static final Supplier<DataComponentType<Integer>> ARROW_SHARD_VARIANT = DATA_COMPONENT_TYPES.registerComponentType("arrow_shard_variant", 
			builder -> builder
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.cacheEncoding());

	public static final Supplier<DataComponentType<StandArrowLore>> ARROW_LORE = DATA_COMPONENT_TYPES.registerComponentType("arrow_lore",
			builder -> builder
			.persistent(StandArrowLore.CODEC)
			.networkSynchronized(StandArrowLore.STREAM_CODEC)
			.cacheEncoding());

	public static final Supplier<DataComponentType<StandArrowShardLore>> ARROW_SHARD_LORE = DATA_COMPONENT_TYPES.registerComponentType("arrow_shard_lore",
			builder -> builder
			.persistent(StandArrowShardLore.CODEC)
			.networkSynchronized(StandArrowShardLore.STREAM_CODEC)
			.cacheEncoding());

	public static final Supplier<DataComponentType<Integer>> BUBBLE_GLOVES_AMMO = DATA_COMPONENT_TYPES.registerComponentType("bubble_gloves_ammo",
			builder -> builder
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final Supplier<DataComponentType<Integer>> TOMMY_GUN_AMMO = DATA_COMPONENT_TYPES.registerComponentType("tommy_gun_ammo",
			builder -> builder
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final Supplier<DataComponentType<Integer>> TOMMY_GUN_GUNSHOT_TICKS = DATA_COMPONENT_TYPES.registerComponentType("tommy_gun_gunshot_ticks",
			builder -> builder
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final Supplier<DataComponentType<Integer>> HAMON_OILED_USES = DATA_COMPONENT_TYPES.registerComponentType("hamon_oiled_uses",
			builder -> builder
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final Supplier<DataComponentType<WalkmanData>> WALKMAN_DATA = DATA_COMPONENT_TYPES.registerComponentType("walkman",
			builder -> builder
			.persistent(WalkmanData.CODEC)
			.networkSynchronized(WalkmanData.STREAM_CODEC));

	public static final Supplier<DataComponentType<CassetteData>> CASSETTE_DATA = DATA_COMPONENT_TYPES.registerComponentType("cassette",
			builder -> builder
			.persistent(CassetteData.CODEC)
			.networkSynchronized(CassetteData.STREAM_CODEC));

	public static final Supplier<DataComponentType<ResourceLocation>> ITEM_MODEL = DATA_COMPONENT_TYPES.registerComponentType("item_model", 
			__ItemModelComponent.builder());
}
