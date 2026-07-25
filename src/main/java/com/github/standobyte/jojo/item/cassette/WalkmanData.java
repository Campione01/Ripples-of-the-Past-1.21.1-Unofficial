package com.github.standobyte.jojo.item.cassette;

import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public record WalkmanData(int id, boolean idInitialized, float volume, WalkmanPlaybackMode playbackMode, ItemStack cassette) {
	public static final WalkmanData EMPTY = new WalkmanData(0, false, 1.0F, WalkmanPlaybackMode.STOP_AT_THE_END, ItemStack.EMPTY);

	public static final Codec<WalkmanData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("id", 0).forGetter(WalkmanData::id),
			Codec.BOOL.optionalFieldOf("id_initialized", false).forGetter(WalkmanData::idInitialized),
			Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(WalkmanData::volume),
			WalkmanPlaybackMode.CODEC.optionalFieldOf("playback_mode", WalkmanPlaybackMode.STOP_AT_THE_END).forGetter(WalkmanData::playbackMode),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("cassette", ItemStack.EMPTY).forGetter(WalkmanData::cassette)
			).apply(instance, WalkmanData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, WalkmanData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WalkmanData::id,
			ByteBufCodecs.BOOL, WalkmanData::idInitialized,
			ByteBufCodecs.FLOAT, WalkmanData::volume,
			WalkmanPlaybackMode.STREAM_CODEC, WalkmanData::playbackMode,
			ItemStack.OPTIONAL_STREAM_CODEC, WalkmanData::cassette,
			WalkmanData::new);

	public WalkmanData {
		volume = Mth.clamp(volume, 0.0F, 1.0F);
		cassette = cassette.copy();
		if (!cassette.isEmpty()) {
			cassette.setCount(1);
		}
	}

	public WalkmanData withInitializedId(int newId) {
		return new WalkmanData(newId, true, volume, playbackMode, cassette);
	}

	public WalkmanData withVolume(float newVolume) {
		return new WalkmanData(id, idInitialized, Mth.clamp(newVolume, 0.0F, 1.0F), playbackMode, cassette);
	}

	public WalkmanData withPlaybackMode(WalkmanPlaybackMode newMode) {
		return new WalkmanData(id, idInitialized, volume, newMode, cassette);
	}

	public WalkmanData withCassette(ItemStack newCassette) {
		return new WalkmanData(id, idInitialized, volume, playbackMode, newCassette);
	}

	public WalkmanData update(UnaryOperator<WalkmanData> op) {
		return op.apply(this);
	}
}
