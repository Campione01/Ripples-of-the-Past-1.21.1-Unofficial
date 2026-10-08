package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.init.ModEntityTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GETransformationEntity is a plain Entity: nothing damages it, so a burning one finishes. Only /kill
 * (Entity#kill) and falling out of the world remove it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceTransformationDamageGameTests {
	private static final int DURATION = 200;

	private GoldExperienceTransformationDamageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = DURATION + 60)
	public static void burningTransformationFinishesAndGivesItsLifeform(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pig lifeform = EntityType.PIG.create(level);
		helper.assertTrue(lifeform != null, "Could not create the lifeform");
		lifeform.setNoAi(true);
		GETransformationEntity transformation = addTransformation(helper, lifeform, DURATION);
		transformation.igniteForSeconds(DURATION / 20.0F + 2.0F);
		helper.assertTrue(transformation.isOnFire(), "Transformation fixture is not burning");
		helper.succeedWhen(() -> {
			helper.assertTrue(transformation.isRemoved() && level.getEntity(lifeform.getUUID()) == lifeform,
					"The burning transformation did not give its lifeform, removed=" + transformation.isRemoved()
					+ " age=" + transformation.tickCount + " duration=" + DURATION);
			helper.assertTrue(transformation.tickCount >= DURATION,
					"The transformation was removed before its duration, age=" + transformation.tickCount);
			lifeform.discard();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void transformationIgnoresEveryKindOfDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pig lifeform = EntityType.PIG.create(level);
		helper.assertTrue(lifeform != null, "Could not create the lifeform");
		GETransformationEntity transformation = addTransformation(helper, lifeform, 1000);
		try {
			Vec3 motion = transformation.getDeltaMovement();
			DamageSource[] sources = {
					level.damageSources().generic(),
					level.damageSources().inFire(),
					level.damageSources().onFire(),
					level.damageSources().lava(),
					level.damageSources().explosion(null),
					level.damageSources().fellOutOfWorld(),
					level.damageSources().genericKill()
			};
			for (DamageSource source : sources) {
				helper.assertFalse(transformation.hurt(source, 100.0F),
						"The transformation took damage from " + source.getMsgId());
				helper.assertTrue(transformation.isAlive(), "The transformation died of " + source.getMsgId());
			}
			helper.assertTrue(transformation.getDeltaMovement().equals(motion), "Damage knocked the transformation back");
			transformation.kill();
			helper.assertTrue(transformation.isRemoved(), "/kill did not remove the transformation");
		}
		finally {
			transformation.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void transformationBelowTheWorldIsRemoved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pig lifeform = EntityType.PIG.create(level);
		helper.assertTrue(lifeform != null, "Could not create the lifeform");
		GETransformationEntity transformation = addTransformation(helper, lifeform, 1000);
		Vec3 pos = transformation.position();
		transformation.setPos(pos.x, level.getMinBuildHeight() - 80, pos.z);
		helper.succeedWhen(() -> helper.assertTrue(transformation.isRemoved(),
				"The transformation below the world was not removed"));
	}

	private static GETransformationEntity addTransformation(GameTestHelper helper, Pig lifeform, int duration) {
		ServerLevel level = helper.getLevel();
		GETransformationEntity transformation = ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get().create(level);
		helper.assertTrue(transformation != null, "Could not create the transformation");
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		transformation.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		transformation.withSourceItem(new ItemStack(Items.APPLE)).withTransformationTarget(lifeform).withDuration(duration);
		helper.assertTrue(level.addFreshEntity(transformation), "Could not add the transformation");
		return transformation;
	}
}
