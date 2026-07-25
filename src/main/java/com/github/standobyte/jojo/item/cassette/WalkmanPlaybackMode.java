package com.github.standobyte.jojo.item.cassette;

import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum WalkmanPlaybackMode implements StringRepresentable {
	STOP_AT_THE_END("stop_at_the_end"),
	LOOP("loop");

	public static final Codec<WalkmanPlaybackMode> CODEC = StringRepresentable.fromEnum(WalkmanPlaybackMode::values);
	public static final StreamCodec<FriendlyByteBuf, WalkmanPlaybackMode> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public WalkmanPlaybackMode decode(FriendlyByteBuf buf) {
			return buf.readEnum(WalkmanPlaybackMode.class);
		}

		@Override
		public void encode(FriendlyByteBuf buf, WalkmanPlaybackMode mode) {
			buf.writeEnum(mode);
		}
	};

	private final String name;

	WalkmanPlaybackMode(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public WalkmanPlaybackMode toggle() {
		return this == LOOP ? STOP_AT_THE_END : LOOP;
	}
}
