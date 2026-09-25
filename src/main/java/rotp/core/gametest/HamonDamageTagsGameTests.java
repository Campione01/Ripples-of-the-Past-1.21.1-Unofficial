package rotp.core.gametest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModEntityTypeTags;
import rotp.core.mechanics.JojoDefinitions;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 JojoModUtil.isAffectedByHamon: the jojo_ripples:hamon_damage / no_hamon_damage entity type
 * tags win over the undead check, no_hamon_damage first. Test tags are bound and restored inside
 * one call, so other tests never see them.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonDamageTagsGameTests {
	private static final float EPS = 1.0E-4F;

	private HamonDamageTagsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonDamageTagsOverrideUndeadCheck(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ModEntityTypeTags.HAMON_DAMAGE).isPresent(),
				"Data tag jojo_ripples:hamon_damage is not loaded");
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ModEntityTypeTags.NO_HAMON_DAMAGE).isPresent(),
				"Data tag jojo_ripples:no_hamon_damage is not loaded");
		LivingEntity pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
		LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
		LivingEntity skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, 1, 2, 3);
		// control: the shipped empty tags keep the undead rule
		helper.assertTrue(!JojoDefinitions.isAffectedByHamon(pig) && JojoDefinitions.isAffectedByHamon(zombie)
				&& JojoDefinitions.isAffectedByHamon(skeleton), "Empty Hamon tags must keep the undead rule");

		Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> original = new IdentityHashMap<>();
		BuiltInRegistries.ENTITY_TYPE.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
		Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> tagged = new IdentityHashMap<>(original);
		// skeleton sits in both tags: no_hamon_damage must win
		tagged.put(ModEntityTypeTags.HAMON_DAMAGE, withAdded(original.get(ModEntityTypeTags.HAMON_DAMAGE),
				EntityType.PIG, EntityType.SKELETON));
		tagged.put(ModEntityTypeTags.NO_HAMON_DAMAGE, withAdded(original.get(ModEntityTypeTags.NO_HAMON_DAMAGE),
				EntityType.ZOMBIE, EntityType.SKELETON));
		try {
			BuiltInRegistries.ENTITY_TYPE.bindTags(tagged);
			helper.assertTrue(JojoDefinitions.isAffectedByHamon(pig),
					"A pig in jojo_ripples:hamon_damage must take Hamon as undead");
			helper.assertFalse(JojoDefinitions.isAffectedByHamon(zombie),
					"A zombie in jojo_ripples:no_hamon_damage must not take Hamon as undead");
			helper.assertFalse(JojoDefinitions.isAffectedByHamon(skeleton),
					"no_hamon_damage must be checked before hamon_damage");
			float pigMultiplier = HamonAbilityHelpers.hamonDamageMultiplier(pig);
			float zombieMultiplier = HamonAbilityHelpers.hamonDamageMultiplier(zombie);
			helper.assertTrue(Math.abs(pigMultiplier - 1.0F) < EPS && Math.abs(zombieMultiplier - 0.2F) < EPS,
					"Hamon damage factor must follow the tags: pig " + pigMultiplier + ", zombie " + zombieMultiplier);
		}
		finally {
			BuiltInRegistries.ENTITY_TYPE.bindTags(original);
		}
		helper.assertTrue(JojoDefinitions.isAffectedByHamon(zombie) && !JojoDefinitions.isAffectedByHamon(pig),
				"Tags were not restored after the test");
		helper.succeed();
	}

	private static List<Holder<EntityType<?>>> withAdded(List<Holder<EntityType<?>>> base, EntityType<?>... types) {
		List<Holder<EntityType<?>>> list = base == null ? new ArrayList<>() : new ArrayList<>(base);
		for (EntityType<?> type : types) {
			list.add(type.builtInRegistryHolder());
		}
		return list;
	}
}
