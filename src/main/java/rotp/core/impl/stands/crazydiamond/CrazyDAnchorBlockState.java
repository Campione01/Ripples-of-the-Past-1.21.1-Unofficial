package rotp.core.impl.stands.crazydiamond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block state an anchor was made from (1.16 CDCheckpoint "BlockState"),
 * plus the drop count the state is worth, so a split stack can't restore the whole block.
 */
public record CrazyDAnchorBlockState(BlockState state, int count) {
	public static final Codec<CrazyDAnchorBlockState> CODEC = RecordCodecBuilder.create(builder -> builder.group(
			BlockState.CODEC.fieldOf("state").forGetter(CrazyDAnchorBlockState::state),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(CrazyDAnchorBlockState::count))
			.apply(builder, CrazyDAnchorBlockState::new));

	public static final StreamCodec<ByteBuf, CrazyDAnchorBlockState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), CrazyDAnchorBlockState::state,
			ByteBufCodecs.VAR_INT, CrazyDAnchorBlockState::count,
			CrazyDAnchorBlockState::new);

	public CrazyDAnchorBlockState {
		count = Math.max(count, 1);
	}
}
