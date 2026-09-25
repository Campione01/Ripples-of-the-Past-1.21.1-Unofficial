package rotp.core.gametest;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModParticles;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.network.s2c.TrHamonParticlesPacket;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KnockbackCollisionImpact: an S.Y.O. / Scarlet Overdrive punch's impact dealt its Hamon damage with
 * attack.hamonParticle(hamonParticles), so the hit's spark emitter is in the punch's colour (default spark without one).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnockbackImpactHamonSparkGameTests {

	private KnockbackImpactHamonSparkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void knockbackImpactHamonHitSparksInThePunchColour(GameTestHelper helper) {
		ParticleOptions yellow = ModParticles.HAMON_SPARK_YELLOW.get();
		ParticleOptions red = ModParticles.HAMON_SPARK_RED.get();
		checkImpactSpark(helper, 1, yellow, 0, yellow, "S.Y.O.");
		checkImpactSpark(helper, 2, red, 60, red, "Scarlet Overdrive");
		checkImpactSpark(helper, 3, null, 0, null, "uncoloured");
		helper.succeed();
	}

	private static void checkImpactSpark(GameTestHelper helper, int z, @Nullable ParticleOptions punchParticle,
			int fireTicks, @Nullable ParticleOptions expected, String what) {
		LivingEntity flying = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, z);
		LivingEntity hit = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, z);
		try {
			Vec3 motion = new Vec3(0.4, 0, 0);
			HurtRefused impact = new HurtRefused(flying);
			impact.onPunchSetKnockbackImpact(motion, null).hamonDamage(10, fireTicks, punchParticle);
			HamonAbilityHelpers.takeLastSparkEmitter();
			float health = hit.getHealth();
			impact.collideWith(hit, motion);
			DamageSource source = hit.getLastDamageSource();
			helper.assertTrue(hit.getHealth() < health && source != null && source.is(ModDamageTypes.HAMON),
					what + " impact dealt no Hamon damage: " + source);
			TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
			helper.assertTrue(sent != null && sent.entityId() == hit.getId(),
					what + " impact Hamon hit sent no spark emitter on its target: " + sent);
			helper.assertTrue(sent.particle() == expected,
					what + " impact Hamon spark is " + sent.particle() + ", 1.16 sparked in " + expected);
		}
		finally {
			flying.discard();
			hit.discard();
		}
	}

	// only the Hamon part may hurt, so the emitter read is the Hamon hit's
	private static final class HurtRefused extends KnockbackCollisionImpact {
		HurtRefused(Entity entity) {
			super(entity);
		}

		@Override
		protected boolean hurtTarget(Entity target, DamageSource dmgSource, float amount) {
			return false;
		}

		boolean collideWith(LivingEntity target, Vec3 motion) {
			return onCollideWith(target, target, motion);
		}
	}
}
