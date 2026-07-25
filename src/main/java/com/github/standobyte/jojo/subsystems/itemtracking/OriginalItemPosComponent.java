package com.github.standobyte.jojo.subsystems.itemtracking;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record OriginalItemPosComponent(BlockPos blockPos, Optional<ResourceKey<Level>> dimension) {
	private static final Codec<OriginalItemPosComponent> MAP_CODEC = RecordCodecBuilder.create(builder -> builder.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(OriginalItemPosComponent::blockPos),
			ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(OriginalItemPosComponent::dimension))
			.apply(builder, OriginalItemPosComponent::new));

	public static final Codec<OriginalItemPosComponent> CODEC = Codec.withAlternative(
			MAP_CODEC, BlockPos.CODEC, OriginalItemPosComponent::new);
	
	public static final StreamCodec<ByteBuf, OriginalItemPosComponent> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, OriginalItemPosComponent::blockPos,
			ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs::optional), OriginalItemPosComponent::dimension,
			OriginalItemPosComponent::new);

	public OriginalItemPosComponent(BlockPos blockPos) {
		this(blockPos, Optional.empty());
	}

	public OriginalItemPosComponent(BlockPos blockPos, ResourceKey<Level> dimension) {
		this(blockPos, Optional.of(dimension));
	}

	public boolean matchesDimension(Level level) {
		return level != null && (dimension.isEmpty() || dimension.get().equals(level.dimension()));
	}

}
