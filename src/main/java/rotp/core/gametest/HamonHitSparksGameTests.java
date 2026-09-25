package rotp.core.gametest;

import io.netty.buffer.Unpooled;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModParticles;
import rotp.core.network.s2c.TrHamonParticlesPacket;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 DamageUtil.dealHamonDamage: every landed Hamon hit sends HamonUtil.createHamonSparkParticlesEmitter
 * to the target's trackers, intensity = dealt damage / (MAX_HAMON_STRENGTH_MULTIPLIER * 5) capped at 4,
 * in the attack's particle (coloured overdrives) or the default spark.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonHitSparksGameTests {
	private static final float EPS = 1.0E-4F;

	private HamonHitSparksGameTests() {}

	private static float expectedIntensity(float dealtDamage) {
		return Math.min(dealtDamage / (HamonData.MAX_HAMON_STRENGTH_MULTIPLIER * 5.0F), 4.0F);
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonHitSendsSparkEmitter(GameTestHelper helper) {
		LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		float base = 6.0F;
		float dealt = HamonAbilityHelpers.hamonDamageAmount(zombie, base) * HamonAbilityHelpers.configHamonDamageMultiplier();
		HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(HamonAbilityHelpers.hamonHurt(zombie, base, null, null), "Hamon hit on a zombie did not land");
		TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(sent != null, "A landed Hamon hit sent no spark emitter");
		helper.assertTrue(sent.entityId() == zombie.getId() && sent.particle() == null
				&& Math.abs(sent.soundVolume() - 1.0F) < EPS
				&& Math.abs(sent.intensity() - expectedIntensity(dealt)) < EPS,
				"Default spark emitter wrong: " + sent + ", expected intensity " + expectedIntensity(dealt));

		// no-source-multiplier path (1.16 noSrcEntityHamonMultiplier) sparks too
		LivingEntity zombie2 = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
		helper.assertTrue(HamonAbilityHelpers.hamonHurtWithAmount(zombie2, 3.0F,
				HamonAbilityHelpers.hamonDamageSource(helper.getLevel(), null, null),
				HamonAbilityHelpers.HamonAttackProperties.NO_SOURCE_ENTITY_HAMON_MULTIPLIER), "Plain Hamon hit did not land");
		sent = HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(sent != null && sent.entityId() == zombie2.getId()
				&& Math.abs(sent.intensity() - expectedIntensity(3.0F * HamonAbilityHelpers.configHamonDamageMultiplier())) < EPS,
				"No-source-multiplier Hamon hit emitter wrong: " + sent);

		// a hit that does not land sparks nothing
		zombie.setInvulnerable(true);
		helper.assertFalse(HamonAbilityHelpers.hamonHurt(zombie, base, null, null), "Hamon hit landed on an invulnerable zombie");
		helper.assertTrue(HamonAbilityHelpers.takeLastSparkEmitter() == null, "A Hamon hit that did not land still sparked");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void colouredHamonHitSparksInItsColour(GameTestHelper helper) {
		LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		LivingEntity user = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 3);
		HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(HamonAbilityHelpers.hamonHurtWithParticles(zombie, user, 6.0F, ModParticles.HAMON_SPARK_RED.get(), 12),
				"Coloured Hamon hit did not land");
		TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
		helper.assertTrue(sent != null && sent.entityId() == zombie.getId() && sent.particle() == ModParticles.HAMON_SPARK_RED.get(),
				"Coloured Hamon hit must spark in its own particle: " + sent);

		// cap at 4, nothing at 0, default spark not written out
		TrHamonParticlesPacket big = TrHamonParticlesPacket.emitter(zombie, 100.0F, 1.0F, null);
		helper.assertTrue(big != null && Math.abs(big.intensity() - 4.0F) < EPS, "Emitter intensity not capped at 4: " + big);
		helper.assertTrue(TrHamonParticlesPacket.emitter(zombie, 0.0F, 1.0F, null) == null, "Zero intensity still made an emitter");
		TrHamonParticlesPacket plain = TrHamonParticlesPacket.emitter(zombie, 1.0F, 1.0F, ModParticles.HAMON_SPARK.get());
		helper.assertTrue(plain != null && plain.particle() == null, "Default spark particle not elided: " + plain);

		TrHamonParticlesPacket.Handler handler = new TrHamonParticlesPacket.Handler(JojoMod.resLoc("trhamonparticles"));
		for (TrHamonParticlesPacket packet : new TrHamonParticlesPacket[] { sent, plain }) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				handler.encode(packet, buf);
				TrHamonParticlesPacket decoded = handler.decode(buf);
				helper.assertTrue(packet.equals(decoded) && buf.readableBytes() == 0,
						"Hamon spark packet round trip changed it: " + packet + " -> " + decoded);
			}
			finally {
				buf.release();
			}
		}
		helper.succeed();
	}
}
