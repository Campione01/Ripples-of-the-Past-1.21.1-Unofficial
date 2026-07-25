package com.github.standobyte.jojo.item.cassette;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

public record CassetteData(List<CassetteTrackSource> tracks, int generation, Optional<DyeColor> dye, boolean dyeCraftHint,
		CassetteSide side, int sideTrack) {
	public static final int MAX_GENERATION = 4;
	public static final CassetteData BROKEN = new CassetteData(List.of(), 0, Optional.empty(), false, CassetteSide.SIDE_A, 0);

	public static final Codec<CassetteData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CassetteTrackSource.CODEC.listOf().optionalFieldOf("tracks", List.of()).forGetter(CassetteData::tracks),
			Codec.INT.optionalFieldOf("generation", 0).forGetter(CassetteData::generation),
			DyeColor.CODEC.optionalFieldOf("dye").forGetter(CassetteData::dye),
			Codec.BOOL.optionalFieldOf("dye_craft_hint", false).forGetter(CassetteData::dyeCraftHint),
			CassetteSide.CODEC.optionalFieldOf("side", CassetteSide.SIDE_A).forGetter(CassetteData::side),
			Codec.INT.optionalFieldOf("side_track", 0).forGetter(CassetteData::sideTrack)
			).apply(instance, CassetteData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CassetteData> STREAM_CODEC = StreamCodec.composite(
			CassetteTrackSource.STREAM_CODEC.apply(ByteBufCodecs.list()), CassetteData::tracks,
			ByteBufCodecs.VAR_INT, CassetteData::generation,
			DyeColor.STREAM_CODEC.apply(ByteBufCodecs::optional), CassetteData::dye,
			ByteBufCodecs.BOOL, CassetteData::dyeCraftHint,
			CassetteSide.STREAM_CODEC, CassetteData::side,
			ByteBufCodecs.VAR_INT, CassetteData::sideTrack,
			CassetteData::new);

	public boolean isBroken() {
		return tracks.isEmpty();
	}

	public CassetteData copyForNextGeneration() {
		return new CassetteData(tracks, Math.min(generation + 1, MAX_GENERATION), dye, dyeCraftHint, side, sideTrack);
	}

	public CassetteData withSide(CassetteSide newSide) {
		return new CassetteData(tracks, generation, dye, dyeCraftHint, newSide, sideTrack);
	}

	public CassetteData withSideTrack(int newSideTrack) {
		return new CassetteData(tracks, generation, dye, dyeCraftHint, side, Math.max(0, newSideTrack));
	}

	public static CassetteData recorded(List<CassetteTrackSource> tracks, Optional<DyeColor> dye) {
		if (tracks.isEmpty()) {
			return BROKEN;
		}
		return new CassetteData(List.copyOf(tracks), 0, dye, false, CassetteSide.SIDE_A, 0);
	}

	public CassetteData update(UnaryOperator<CassetteData> op) {
		return op.apply(this);
	}
}
