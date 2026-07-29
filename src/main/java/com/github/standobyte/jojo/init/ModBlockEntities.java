package com.github.standobyte.jojo.init;

import java.util.function.Supplier;

import com.github.standobyte.jojo.api.stonemask.StoneMaskExtensions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.block.SlumberingPillarmanBlockEntity;
import com.github.standobyte.jojo.block.StoneMaskBlockEntity;
import com.github.standobyte.jojo.mechanics.clothes.sewing.SewingMachineBlockEntity;
import com.github.standobyte.jojo.mrpresident.CocoJumboShellBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JojoMod.MOD_ID);


	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SewingMachineBlockEntity>> SEWING_MACHINE = BLOCK_ENTITY_TYPES.register("sewing_machine", key -> {
		return BlockEntityType.Builder.of(SewingMachineBlockEntity::new, ModBlocks.SEWING_MACHINE.get()).build(null);
	});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CocoJumboShellBlockEntity>> COCO_JUMBO_SHELL = BLOCK_ENTITY_TYPES.register("coco_jumbo_shell", key -> {
		return BlockEntityType.Builder.of(CocoJumboShellBlockEntity::new, ModBlocks.COCO_JUMBO_SHELL.get()).build(null);
	});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneMaskBlockEntity>> STONE_MASK = BLOCK_ENTITY_TYPES.register("stone_mask", key -> {
		return BlockEntityType.Builder.of(
				StoneMaskBlockEntity::new,
				StoneMaskExtensions.resolveBlocks(
						ModBlocks.STONE_MASK.get(),
						ModBlocks.AJA_STONE_MASK.get()))
				.build(null);
	});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SlumberingPillarmanBlockEntity>> SLUMBERING_PILLARMAN = BLOCK_ENTITY_TYPES.register("slumbering_pillarman", key -> {
		return BlockEntityType.Builder.of(SlumberingPillarmanBlockEntity::new, ModBlocks.SLUMBERING_PILLARMAN.get()).build(null);
	});

	public static final Supplier<BlockEntityType<?>> _PLACEHOLDER_STONE_MASK = STONE_MASK::get;
}
