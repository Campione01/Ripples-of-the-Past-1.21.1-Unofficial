package com.github.standobyte.jojo.block;

import java.util.Optional;
import java.util.function.Consumer;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModFluids;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

public abstract class BoilingBloodFluid extends LavaFluid {

	@Override
	public Fluid getFlowing() {
		return ModFluids.FLOWING_BOILING_BLOOD.get();
	}

	@Override
	public Fluid getSource() {
		return ModFluids.BOILING_BLOOD.get();
	}

	@Override
	public Item getBucket() {
		return ModItems.BOILING_BLOOD_BUCKET.get();
	}

	@Override
	public FluidType getFluidType() {
		return ModFluids.BOILING_BLOOD_TYPE.get();
	}

	@Override
	public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
		BlockPos above = pos.above();
		if (level.getBlockState(above).isAir() && !level.getBlockState(above).isSolidRender(level, above)) {
			if (random.nextInt(100) == 0) {
				double x = (double) pos.getX() + random.nextDouble();
				double y = (double) pos.getY() + 1.0D;
				double z = (double) pos.getZ() + random.nextDouble();
				level.addParticle(ModParticles.BOILING_BLOOD_POP.get(), x, y, z, 0.0D, 0.0D, 0.0D);
				level.playLocalSound(x, y, z, ModSoundEvents.BOILING_BLOOD_POP.get(), SoundSource.BLOCKS,
						0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
			}
			if (random.nextInt(200) == 0) {
				level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(),
						ModSoundEvents.BOILING_BLOOD_AMBIENT.get(), SoundSource.BLOCKS,
						0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
			}
		}
	}

	@Override
	public ParticleOptions getDripParticle() {
		return ParticleTypes.DRIPPING_LAVA;
	}

	@Override
	public BlockState createLegacyBlock(FluidState state) {
		return ModBlocks.BOILING_BLOOD.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, Integer.valueOf(getLegacyLevel(state)));
	}

	@Override
	public boolean isSame(Fluid fluid) {
		return fluid == ModFluids.BOILING_BLOOD.get() || fluid == ModFluids.FLOWING_BOILING_BLOOD.get();
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(ModSoundEvents.BUCKET_FILL_BOILING_BLOOD.get());
	}

	public static class Flowing extends BoilingBloodFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}

	public static class Source extends BoilingBloodFluid {
		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}

	public static class BoilingBloodFluidType extends FluidType {
		private static final ResourceLocation STILL_TEXTURE = JojoMod.resLoc("block/boiling_blood_still");
		private static final ResourceLocation FLOWING_TEXTURE = JojoMod.resLoc("block/boiling_blood_flow");

		public BoilingBloodFluidType(Properties properties) {
			super(properties);
		}

		@Override
		public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
			consumer.accept(new IClientFluidTypeExtensions() {
				@Override
				public ResourceLocation getStillTexture() {
					return STILL_TEXTURE;
				}

				@Override
				public ResourceLocation getFlowingTexture() {
					return FLOWING_TEXTURE;
				}
			});
		}
	}
}
