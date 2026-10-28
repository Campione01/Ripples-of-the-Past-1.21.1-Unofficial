package rotp.core.init;

import rotp.core.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
	public static final TagKey<Block> CRAZY_D_CANNOT_RESTORE = TagKey.create(Registries.BLOCK,
			ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "crazy_d_cannot_restore"));
	/** The blocks of 1.16's Material.STONE, which the Angelo Rock is made of (1.16 CDTurnIntoAngeloRockEffect.canUseBlock). */
	public static final TagKey<Block> ANGELO_ROCK_STONE = TagKey.create(Registries.BLOCK,
			ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "angelo_rock_stone"));

	private ModBlockTags() {}
}
