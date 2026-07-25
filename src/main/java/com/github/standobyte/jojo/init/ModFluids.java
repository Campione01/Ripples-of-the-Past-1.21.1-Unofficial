package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.block.BoilingBloodFluid;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModFluids {
	public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, JojoMod.MOD_ID);
	public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, JojoMod.MOD_ID);

	public static final DeferredHolder<FluidType, FluidType> BOILING_BLOOD_TYPE = FLUID_TYPES.register("boiling_blood",
			() -> new BoilingBloodFluid.BoilingBloodFluidType(FluidType.Properties.create()
					.descriptionId("block.jojo_ripples.boiling_blood")
					.lightLevel(15)
					.density(3000)
					.viscosity(6000)
					.temperature(1300)
					.sound(SoundActions.BUCKET_FILL, ModSoundEvents.BUCKET_FILL_BOILING_BLOOD.get())
					.sound(SoundActions.BUCKET_EMPTY, ModSoundEvents.BUCKET_EMPTY_BOILING_BLOOD.get())));

	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_BOILING_BLOOD = FLUIDS.register("flowing_boiling_blood",
			BoilingBloodFluid.Flowing::new);

	public static final DeferredHolder<Fluid, FlowingFluid> BOILING_BLOOD = FLUIDS.register("boiling_blood",
			BoilingBloodFluid.Source::new);
}
