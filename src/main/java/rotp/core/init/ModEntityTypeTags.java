package rotp.core.init;

import rotp.core.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class ModEntityTypeTags {
	public static final TagKey<EntityType<?>> VAMPIRE_CAN_DRAIN = create("vampire_can_drain");
	public static final TagKey<EntityType<?>> VAMPIRE_CANNOT_DRAIN = create("vampire_cannot_drain");
	// 1.16 ModTags: datapack overrides for JojoDefinitions.isAffectedByHamon
	public static final TagKey<EntityType<?>> HAMON_DAMAGE = create("hamon_damage");
	public static final TagKey<EntityType<?>> NO_HAMON_DAMAGE = create("no_hamon_damage");

	private static TagKey<EntityType<?>> create(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, JojoMod.resLoc(path));
	}
}
