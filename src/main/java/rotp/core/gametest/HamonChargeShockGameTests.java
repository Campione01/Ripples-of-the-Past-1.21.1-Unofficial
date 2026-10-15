package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonCharge.tick: Hamon Shock (30 ticks, with the infuser's HAMON_SHOCK skill) is part of the full-strength
 * hit only, with the sparks and the knockback. A charged entity then keeps its charge at a tenth of the damage, and
 * those reduced hits shock no one.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonChargeShockGameTests {
	private static final int WATCHED_TICKS = 75;

	private HamonChargeShockGameTests() {}

	@GameTest(template = "empty", timeoutTicks = WATCHED_TICKS + 40)
	public static void anInfusedMobShocksItsNeighbourOnlyWithItsFirstFullHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "HamonChargeShockUser"));
		Vec3 carrierPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		Vec3 victimPos = carrierPos.add(1.0D, 0.0D, 0.0D);
		user.setPos(carrierPos.x, carrierPos.y, carrierPos.z + 8.0D);
		Pig carrier = EntityType.PIG.create(level);
		Pig victim = EntityType.PIG.create(level);
		helper.assertTrue(carrier != null && victim != null, "Could not create the pigs");
		carrier.setNoAi(true);
		carrier.setNoGravity(true);
		carrier.moveTo(carrierPos.x, carrierPos.y, carrierPos.z, 0.0F, 0.0F);
		// Hamon Shock, a stun, refuses a mob without AI (StunEffect.isApplicable), so the neighbour keeps its AI
		victim.setNoGravity(true);
		victim.moveTo(victimPos.x, victimPos.y, victimPos.z, 0.0F, 0.0F);

		List<Long> shocks = new ArrayList<>();
		int[] hits = new int[1];
		Consumer<MobEffectEvent.Added> shockAdded = event -> {
			if (event.getEntity() == victim && event.getEffectInstance().is(ModStatusEffects.HAMON_SHOCK)) {
				shocks.add(level.getGameTime());
			}
		};
		Consumer<LivingDamageEvent.Post> hurt = event -> {
			if (event.getEntity() == victim) {
				hits[0]++;
			}
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(shockAdded);
			NeoForge.EVENT_BUS.unregister(hurt);
			EntityHamonChargeState.get(carrier).clear();
			carrier.discard();
			victim.discard();
			user.discard();
		};
		try {
			helper.assertTrue(level.addFreshEntity(user), "Could not add the infuser");
			PowerClass.PLAYER_POWER.attachGet(user).setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HAMON_SHOCK.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.HAMON_SHOCK.get()), "Could not grant Hamon Shock");
			helper.assertTrue(level.addFreshEntity(carrier) && level.addFreshEntity(victim), "Could not add the pigs");
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, MobEffectEvent.Added.class, shockAdded);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.Post.class, hurt);
			EntityHamonChargeState.get(carrier).setHamonCharge(2.0F, 400, user, 0.0F);
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}
		// the full-strength hit knocks the neighbour away from the charge; it is put back beside it every tick
		helper.onEachTick(() -> {
			if (!victim.isRemoved()) {
				victim.setPos(victimPos.x, victimPos.y, victimPos.z);
				victim.setDeltaMovement(Vec3.ZERO);
			}
		});
		helper.runAfterDelay(WATCHED_TICKS, () -> {
			try {
				JojoMod.LOGGER.info("HAMON-CHARGE-SHOCK hits={} shocks at {} health={}", hits[0], shocks, victim.getHealth());
				helper.assertTrue(hits[0] > 40 && EntityHamonChargeState.get(carrier).hasHamonCharge(),
						"premise: the infused pig did not keep hitting its neighbour for " + WATCHED_TICKS + " ticks: hits="
								+ hits[0] + " charged=" + EntityHamonChargeState.get(carrier).hasHamonCharge());
				helper.assertTrue(shocks.size() == 1,
						"1.16 HamonCharge.tick shocks only with the first full-strength hit, the reduced hits that follow"
								+ " shock no one: the neighbour was shocked " + shocks.size() + " times, at game ticks " + shocks
								+ " (" + hits[0] + " hits)");
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}
}
