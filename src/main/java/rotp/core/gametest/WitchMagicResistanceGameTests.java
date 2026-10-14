package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;

/**
 * A Witch takes 15% of the magic damage dealt to her and none of her own (1.16 and vanilla 1.21
 * Witch.getDamageAfterMagicAbsorb). NeoForge 21.1.197's actuallyHurt reads the damage container and drops that
 * method's return value, so without the core's Witch mixin she takes it all; add-on effects that deal plain magic
 * damage (Diego's bleeding) rely on the cut.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WitchMagicResistanceGameTests {
	private static final float EPS = 1.0E-4F;

	private WitchMagicResistanceGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void witchTakesFifteenPercentOfPlainMagicDamage(GameTestHelper helper) {
		Witch witch = witch(helper, 1.5D);
		Witch control = witch(helper, 3.5D);
		try {
			helper.assertTrue(witch.hurt(helper.getLevel().damageSources().magic(), 1.0F),
					"setup: the magic hit on the Witch was refused");
			helper.assertTrue(near(witch.getHealth(), 199.85F),
					"1.16: a Witch takes 15% of a plain magic hit (1 -> 0.15); health 200 -> " + witch.getHealth());
			helper.assertTrue(control.hurt(helper.getLevel().damageSources().generic(), 1.0F) && near(control.getHealth(), 199.0F),
					"a Witch takes a hit that is not magic in full; health 200 -> " + control.getHealth());
			helper.succeed();
		}
		finally {
			witch.discard();
			control.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void witchMagicCutStacksOnResistance(GameTestHelper helper) {
		Witch witch = witch(helper, 1.5D);
		try {
			witch.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
			helper.assertTrue(witch.hurt(helper.getLevel().damageSources().magic(), 10.0F),
					"setup: the magic hit on the Witch was refused");
			helper.assertTrue(near(witch.getHealth(), 200.0F - 10.0F * 0.8F * 0.15F),
					"1.16: Resistance I cuts a magic hit to 80% and the Witch takes 15% of that (10 -> 1.2); health 200 -> "
							+ witch.getHealth());
			helper.succeed();
		}
		finally {
			witch.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void witchTakesNoMagicDamageOfHerOwn(GameTestHelper helper) {
		Witch witch = witch(helper, 1.5D);
		try {
			witch.hurt(helper.getLevel().damageSources().indirectMagic(witch, witch), 4.0F);
			helper.assertTrue(near(witch.getHealth(), 200.0F),
					"1.16: a Witch takes no damage from her own magic; health 200 -> " + witch.getHealth());
			helper.succeed();
		}
		finally {
			witch.discard();
		}
	}

	private static Witch witch(GameTestHelper helper, double x) {
		Witch witch = helper.spawn(EntityType.WITCH, new Vec3(x, 2.0D, 1.5D));
		witch.setNoAi(true);
		witch.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
		witch.setHealth(200.0F);
		return witch;
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}
}
