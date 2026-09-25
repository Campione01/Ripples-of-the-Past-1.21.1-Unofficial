package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonMetalSilverOverdriveAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveBarrageAbility;
import rotp.core.init.ModParticles;
import rotp.core.network.s2c.TrHamonParticlesPacket;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonMetalSilverOverdrive(Weapon).dealDamage and HamonSunlightYellowOverdriveBarrage hits used
 * DamageUtil.dealHamonDamage with attack.hamonParticle(silver / yellow): one emitter in the overdrive's colour,
 * not a default (orange) emitter plus a coloured burst.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonOverdriveSparkColourGameTests {
	private HamonOverdriveSparkColourGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void metalSilverHitSparksSilver(GameTestHelper helper) {
		LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		LivingEntity user = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 3);
		HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(HamonMetalSilverOverdriveAbility.dealMetalSilverDamage(zombie, user, 4.0F),
				"Metal Silver Overdrive hit did not land");
		TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(sent != null && sent.entityId() == zombie.getId()
				&& sent.particle() == ModParticles.HAMON_SPARK_SILVER.get(),
				"Metal Silver Overdrive hit must send one silver spark emitter: " + sent);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void syoBarrageHitSparksYellow(GameTestHelper helper) {
		LivingEntity user = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 3);
		// barrage tick hit (0.1) and finisher (15) share this path
		for (float damage : new float[] { 0.1F, 15.0F }) {
			LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
			zombie.invulnerableTime = 10;
			HamonAbilityHelpers.takeLastSparkEmitter();
			helper.assertTrue(HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance
					.hamonHurtThroughInvul(zombie, user, damage), "SYO Barrage hit did not land, damage " + damage);
			helper.assertTrue(zombie.invulnerableTime == 10, "SYO Barrage hit did not keep invulnerability ticks");
			TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
			helper.assertTrue(sent != null && sent.entityId() == zombie.getId()
					&& sent.particle() == ModParticles.HAMON_SPARK_YELLOW.get(),
					"SYO Barrage hit must send one yellow spark emitter, damage " + damage + ": " + sent);
		}
		helper.succeed();
	}
}
