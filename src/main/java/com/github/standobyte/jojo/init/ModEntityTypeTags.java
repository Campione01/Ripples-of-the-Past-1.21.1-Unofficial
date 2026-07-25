package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class ModEntityTypeTags {
	public static final TagKey<EntityType<?>> VAMPIRE_CAN_DRAIN = create("vampire_can_drain");
	public static final TagKey<EntityType<?>> VAMPIRE_CANNOT_DRAIN = create("vampire_cannot_drain");

	private static TagKey<EntityType<?>> create(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, JojoMod.resLoc(path));
	}
}
