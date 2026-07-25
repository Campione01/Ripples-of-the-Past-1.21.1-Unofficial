package com.github.standobyte.jojo.init;

import java.util.EnumMap;
import java.util.Map;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.block.MagiciansRedFireBlock;
import com.github.standobyte.jojo.block.SlumberingPillarmanBlock;
import com.github.standobyte.jojo.block.StoneMaskBlock;
import com.github.standobyte.jojo.block.WoodenCoffinBlock;
import com.github.standobyte.jojo.mechanics.clothes.sewing.SewingMachineBlock;
import com.github.standobyte.jojo.mechanics.standarrow.MeteoriteCoreBlock;
import com.github.standobyte.jojo.mrpresident.CocoJumboShellBlock;
import com.github.standobyte.jojo.mrpresident.MrPresidentGemBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(JojoMod.MOD_ID);

	public static final DeferredBlock<MagiciansRedFireBlock> MAGICIANS_RED_FIRE = BLOCKS.register("magicians_red_fire",
			() -> new MagiciansRedFireBlock(Block.Properties.ofFullCopy(Blocks.FIRE)
					.lightLevel(state -> 15).sound(SoundType.WOOL)));

	public static final DeferredBlock<LiquidBlock> BOILING_BLOOD = BLOCKS.register("boiling_blood",
			() -> new LiquidBlock(ModFluids.BOILING_BLOOD.get(), Block.Properties.ofFullCopy(Blocks.LAVA)
					.lightLevel(state -> 15).noLootTable()));

	public static final DeferredBlock<Block> SEWING_MACHINE = BLOCKS.register("sewing_machine", 
			() -> new SewingMachineBlock(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)
					.requiresCorrectToolForDrops()));

	public static final Map<DyeColor, DeferredBlock<WoodenCoffinBlock>> WOODEN_COFFIN_OAK = registerWoodenCoffins();

	public static final DeferredBlock<Block> METEORIC_IRON = BLOCKS.register("meteoric_iron", 
			() -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)
					.requiresCorrectToolForDrops()));

	public static final DeferredBlock<MeteoriteCoreBlock> METEORITE_CORE = BLOCKS.register("meteorite_core", 
			() -> new MeteoriteCoreBlock(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)
					.strength(10.0F, 3.0F).requiresCorrectToolForDrops()));
	
	public static final DeferredBlock<StoneMaskBlock> STONE_MASK = BLOCKS.register("stone_mask",
			() -> new StoneMaskBlock(Block.Properties.ofFullCopy(Blocks.STONE)
					.strength(1.5F).noOcclusion().noCollission()));

	public static final DeferredBlock<StoneMaskBlock> AJA_STONE_MASK = BLOCKS.register("aja_stone_mask",
			() -> new StoneMaskBlock(Block.Properties.ofFullCopy(Blocks.STONE)
					.strength(1.5F).noOcclusion().noCollission()));

	public static final DeferredBlock<SlumberingPillarmanBlock> SLUMBERING_PILLARMAN = BLOCKS.register("slumbering_pillarman",
			() -> new SlumberingPillarmanBlock(Block.Properties.ofFullCopy(Blocks.BEDROCK)
					.isValidSpawn((state, level, pos, type) -> false)));

	public static final DeferredBlock<Block> COCO_JUMBO_SHELL = BLOCKS.register("coco_jumbo_shell",
			() -> new CocoJumboShellBlock(Block.Properties.ofFullCopy(Blocks.TURTLE_EGG)
					.strength(1.0F).noOcclusion()));

	public static final DeferredBlock<MrPresidentGemBlock> MR_PRESIDENT_EXIT = BLOCKS.register("mr_president_gem",
			() -> new MrPresidentGemBlock(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)
					.strength(-1.0F, 3600000.0F).lightLevel(state -> 15).noLootTable()));

	public static final TagKey<Block> CRAZY_D_CAN_MAKE_BULLET = TagKey.create(Registries.BLOCK,
			ResourceLocation.fromNamespaceAndPath("jojo_ripples", "crazy_d_can_make_bullet"));

	private static Map<DyeColor, DeferredBlock<WoodenCoffinBlock>> registerWoodenCoffins() {
		EnumMap<DyeColor, DeferredBlock<WoodenCoffinBlock>> blocks = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			blocks.put(color, BLOCKS.register("wooden_coffin_oak_" + color.getName(),
					() -> new WoodenCoffinBlock(color, Block.Properties.ofFullCopy(Blocks.OAK_PLANKS)
							.noOcclusion()
							.pushReaction(PushReaction.DESTROY))));
		}
		return blocks;
	}
}
