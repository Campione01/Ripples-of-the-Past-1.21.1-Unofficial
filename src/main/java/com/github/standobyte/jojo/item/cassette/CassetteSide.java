package com.github.standobyte.jojo.item.cassette;

import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum CassetteSide implements StringRepresentable {
	SIDE_A("side_a"),
	SIDE_B("side_b");

	public static final Codec<CassetteSide> CODEC = StringRepresentable.fromEnum(CassetteSide::values);
	public static final StreamCodec<FriendlyByteBuf, CassetteSide> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public CassetteSide decode(FriendlyByteBuf buf) {
			return buf.readEnum(CassetteSide.class);
		}

		@Override
		public void encode(FriendlyByteBuf buf, CassetteSide side) {
			buf.writeEnum(side);
		}
	};

	private final String name;

	CassetteSide(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public CassetteSide opposite() {
		return this == SIDE_A ? SIDE_B : SIDE_A;
	}
}
