package com.github.standobyte.jojo.item.cassette;

import java.util.Locale;
import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.mechanics.standdisc.StandDiscItem;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

public record CassetteTrackSource(Type type, ResourceLocation id, int colorId) {
	private static final ResourceLocation EMPTY_ID = JojoMod.resLoc("empty");

	public static final Codec<CassetteTrackSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Type.CODEC.fieldOf("type").forGetter(CassetteTrackSource::type),
			ResourceLocation.CODEC.optionalFieldOf("id", EMPTY_ID).forGetter(CassetteTrackSource::id),
			Codec.INT.optionalFieldOf("color", -1).forGetter(CassetteTrackSource::colorId)
			).apply(instance, CassetteTrackSource::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CassetteTrackSource> STREAM_CODEC = StreamCodec.composite(
			Type.STREAM_CODEC, CassetteTrackSource::type,
			ResourceLocation.STREAM_CODEC, CassetteTrackSource::id,
			ByteBufCodecs.VAR_INT, CassetteTrackSource::colorId,
			CassetteTrackSource::new);

	public static Optional<CassetteTrackSource> fromItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}

		if (stack.getItem() instanceof DyeItem dyeItem) {
			DyeColor dye = dyeItem.getDyeColor();
			return Optional.of(new CassetteTrackSource(Type.DYE_COLOR, EMPTY_ID, dye.getId()));
		}

		if (stack.is(ModItems.STAND_DISC.get())) {
			StandInstance stand = StandDiscItem.getStandInstance(stack);
			if (stand != null && stand.getStandId() != null) {
				return Optional.of(new CassetteTrackSource(Type.STAND_DISC, stand.getStandId(), -1));
			}
		}

		if (stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)) {
			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			return Optional.of(new CassetteTrackSource(Type.MUSIC_DISC, itemId, -1));
		}

		return Optional.empty();
	}

	public boolean sourceItemIsSpent() {
		return type == Type.DYE_COLOR;
	}

	public Optional<DyeColor> dyeColor() {
		if (type != Type.DYE_COLOR || colorId < 0 || colorId >= DyeColor.values().length) {
			return Optional.empty();
		}
		return Optional.of(DyeColor.byId(colorId));
	}

	public Component displayName(HolderLookup.Provider registries) {
		return switch (type) {
			case MUSIC_DISC -> musicDiscName(registries);
			case STAND_DISC -> Component.translatable(id.toLanguageKey("stand", "name"));
			case DYE_COLOR -> dyeColor()
					.map(dye -> Component.translatable("jojo_ripples.cassette.track.dye", Component.translatable(DyeItem.byColor(dye).getDescriptionId())))
					.orElse(Component.translatable("jojo_ripples.cassette.track.unknown"));
		};
	}

	private Component musicDiscName(HolderLookup.Provider registries) {
		Item item = BuiltInRegistries.ITEM.get(id);
		ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
		if (!stack.isEmpty()) {
			Optional<Component> jukeboxDesc = JukeboxSong.fromStack(registries, stack).map(holder -> holder.value().description());
			if (jukeboxDesc.isPresent()) {
				return jukeboxDesc.get();
			}
			return Component.translatable(item.getDescriptionId(stack) + ".desc");
		}
		return Component.translatable("jojo_ripples.cassette.track.unknown");
	}

	public enum Type implements net.minecraft.util.StringRepresentable {
		MUSIC_DISC("music_disc"),
		STAND_DISC("stand_disc"),
		DYE_COLOR("dye_color");

		public static final Codec<Type> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Type::values);
		public static final StreamCodec<RegistryFriendlyByteBuf, Type> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public Type decode(RegistryFriendlyByteBuf buf) {
				return buf.readEnum(Type.class);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, Type type) {
				buf.writeEnum(type);
			}
		};

		private final String name;

		Type(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name.toLowerCase(Locale.ROOT);
		}
	}
}
