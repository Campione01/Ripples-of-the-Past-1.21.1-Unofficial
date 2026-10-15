package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonCharge;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
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
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 DamageUtil.dealHamonDamage sent any amount to hurt(), zero too (an action at an efficiency of 0, an empty leaves
 * glider): a mob takes such a hit (hurt animation, knockback, true) and a player refuses it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonZeroAmountGameTests {
	private HamonZeroAmountGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aHamonHitOfZeroLandsOnAMobAndNotOnAPlayer(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonZeroUser"));
		Player victim = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonZeroVictim"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
		user.setPos(origin.x, origin.y, origin.z);
		victim.setPos(origin.x + 2.0D, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(user) && helper.getLevel().addFreshEntity(victim),
				"setup: could not add the players");
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5D, 2.0D, 3.5D));
		pig.setNoGravity(true);
		try {
			float pigHealth = pig.getHealth();
			boolean pigHit = HamonAbilityHelpers.hamonHurt(pig, user, 0.0F);
			helper.assertTrue(pigHit && pig.hurtTime > 0 && pig.getHealth() == pigHealth,
					"1.16: a Hamon hit of 0 still goes to hurt(), which a mob takes: hit=" + pigHit + " hurtTime="
							+ pig.hurtTime + " health " + pigHealth + " -> " + pig.getHealth());

			boolean playerHit = HamonAbilityHelpers.hamonHurt(victim, user, 0.0F);
			helper.assertTrue(!playerHit && victim.hurtTime == 0,
					"1.16: a player refuses a hurt() of 0: hit=" + playerHit + " hurtTime=" + victim.hurtTime);
			helper.succeed();
		}
		finally {
			pig.discard();
			victim.discard();
			user.discard();
		}
	}

	// 1.16 HamonCharge.tick sent a charge worth 0 on to dealHamonDamage like any other: the mob next to an infused
	// mob takes the hit, the knockback and the shock, and the charge drops to its reduced hits.
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aChargeWorthNothingStillHitsAMobNextToIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "HamonZeroChargeUser"));
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
		List<Float> knockbacks = new ArrayList<>();
		int[] hurtTime = new int[1];
		Consumer<LivingKnockBackEvent> knockback = event -> {
			if (event.getEntity() == victim) {
				knockbacks.add(event.getOriginalStrength());
				hurtTime[0] = Math.max(hurtTime[0], victim.hurtTime);
			}
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(knockback);
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
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
			EntityHamonChargeState.get(carrier).setHamonCharge(0.0F, 200, user, 0.0F);
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}
		helper.runAfterDelay(10, () -> {
			try {
				HamonCharge charge = EntityHamonChargeState.get(carrier).getHamonCharge();
				boolean fullHitSpent = charge != null && !charge.toNBT().getBoolean("LargeDmg");
				helper.assertTrue(victim.hurtTime > 0 || hurtTime[0] > 0,
						"1.16: a charge worth 0 still goes to hurt(), which the pig next to the infused one takes: hurtTime="
								+ victim.hurtTime);
				helper.assertTrue(knockbacks.contains(0.75F) && victim.hasEffect(ModStatusEffects.HAMON_SHOCK) && fullHitSpent,
						"1.16: the full-strength hit of a charge worth 0 still knocks back (0.75) and shocks the neighbour and"
								+ " is spent: knockbacks=" + knockbacks + " shocked=" + victim.hasEffect(ModStatusEffects.HAMON_SHOCK)
								+ " fullHitSpent=" + fullHitSpent + " charged=" + (charge != null));
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}
}
