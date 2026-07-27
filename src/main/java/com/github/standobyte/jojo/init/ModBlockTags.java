package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
	public static final TagKey<Block> CRAZY_D_CANNOT_RESTORE = TagKey.create(Registries.BLOCK,
			ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "crazy_d_cannot_restore"));

	private ModBlockTags() {}
}
