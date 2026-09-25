package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityMeleeBarrage.BarrageEntityPunch: knockback factor attack damage * 0.0075 (0 on a Stand);
 * with the user in Resolve no knockback, and a hit target loses gravity for 3 of its ticks
 * (LivingUtilCap.setNoGravityFor(3), downward motion clamped) and a mob drops its path.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResolveBarragePinGameTests {
	private static final float EPSILON = 1.0E-5F;

	private ResolveBarragePinGameTests() {}

	// the pinned target must tick in normal time: no neighbouring test's time stop
	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.NO_TIME_STOP)
	public static void starPlatinumResolveBarragePinsTarget(GameTestHelper helper) {
		verify(helper, "star_platinum", "barrage");
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.NO_TIME_STOP)
	public static void silverChariotResolveBarragePinsTarget(GameTestHelper helper) {
		verify(helper, "silver_chariot", "melee_barrage");
	}

	private static void verify(GameTestHelper helper, String standId, String abilityName) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player attacker = fakePlayer(level, "ResolvePinA_" + standId);
		Player other = fakePlayer(level, "ResolvePinB_" + standId);
		StandType attackerType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		StandType otherType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Fixture holder = new Fixture();
		boolean handedOff = false;
		try {
			helper.assertTrue(attackerType != null && otherType != null, "Missing Stand type " + standId);
			holder.attackerPower = addStandUser(helper, attacker, attackerType, origin);
			holder.otherPower = addStandUser(helper, other, otherType, origin.add(4, 0, 0));
			StandEntity stand = holder.attackerPower.getSummonedStandEntity();
			StandEntity otherStand = holder.otherPower.getSummonedStandEntity();
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);

			Ability ability = holder.attackerPower.getAbility(abilityName);
			helper.assertTrue(ability instanceof StandEntityBarrageAbility,
					standId + " " + abilityName + " is not a Stand barrage: " + ability);
			holder.attackerPower.setStamina(holder.attackerPower.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(level, attacker, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, attacker, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityBarrage, "Barrage action type: " + action);
			StandEntityBarrage barrage = (StandEntityBarrage) action;
			barrage.hitsThisTick = 1;

			// knockback factor without Resolve
			Pig free = spawnPig(helper, new BlockPos(1, 2, 3));
			holder.free = free;
			float expected = Mth.clamp((float) stand.getAttackDamage() * 0.0075F, 0, 1);
			float onMob = StandEntityBarrageAbility.getBarrageKnockbackMultiplier(stand, free);
			helper.assertTrue(Math.abs(onMob - expected) < EPSILON,
					"1.16: the barrage knockback factor is attack damage * 0.0075 = " + expected + ", but it is " + onMob);
			float onStand = StandEntityBarrageAbility.getBarrageKnockbackMultiplier(stand, otherStand);
			helper.assertTrue(onStand == 0, "1.16: a barrage gives a Stand no knockback, but the factor is " + onStand);

			// no Resolve: knocked back, not pinned
			free.setDeltaMovement(0, -0.5, 0);
			hit(barrage, free, level, stand);
			helper.assertTrue(horizontal(free) > 1.0E-4, "Fixture: a barrage hit without Resolve gave no knockback");
			helper.assertFalse(isPinned(free), "Without Resolve the barrage removed the target's gravity");

			// Resolve: no knockback, pinned in the air, path dropped
			attacker.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
			helper.assertTrue(StandEntityBarrageAbility.isResolveBarrage(stand), "Fixture: the user has no Resolve");
			// The pin is released on the target's own ticks. Only the test cell's chunk is force-loaded: the old
			// (1, 2, 4) sat in the next chunk whenever the cell was at chunk-local x 15 or z 12-15, that chunk did
			// not tick the pig and its gravity never came back. On top of the cell's barrier lid it always ticks.
			Pig pinned = spawnPig(helper, new BlockPos(0, 3, 0));
			holder.pinned = pinned;
			helper.assertTrue(level.isPositionEntityTicking(pinned.blockPosition()),
					"Fixture: the pinned pig at " + pinned.blockPosition() + " is not entity-ticking");
			BlockPos pigPos = pinned.blockPosition();
			Path path = new Path(List.of(new Node(pigPos.getX(), pigPos.getY(), pigPos.getZ()),
					new Node(pigPos.getX() + 2, pigPos.getY(), pigPos.getZ())), pigPos.offset(2, 0, 0), true);
			helper.assertTrue(pinned.getNavigation().moveTo(path, 1.0) && !pinned.getNavigation().isDone(),
					"Fixture: the pig has no path to follow");
			pinned.setDeltaMovement(0, -0.5, 0);
			hit(barrage, pinned, level, stand);
			helper.assertTrue(horizontal(pinned) < 1.0E-6,
					"1.16: a Resolve barrage gives no knockback, but the target moves " + pinned.getDeltaMovement());
			helper.assertTrue(pinned.getDeltaMovement().y >= 0,
					"1.16: a Resolve barrage stops the target's fall, but its motion is " + pinned.getDeltaMovement());
			helper.assertTrue(isPinned(pinned) && gravity(pinned) == 0,
					"1.16: a Resolve barrage removes the target's gravity, but it is " + gravity(pinned));
			helper.assertTrue(pinned.getNavigation().isDone(), "1.16: a Resolve barrage stops the mob's path");

			// still pinned after one of its ticks, released within 3
			helper.runAfterDelay(1, () -> guarded(holder, attacker, other, attackerType, otherType, () ->
					helper.assertTrue(isPinned(pinned), "1.16: the target stays without gravity for 3 ticks, not 1")));
			helper.runAfterDelay(8, () -> {
				try {
					helper.assertFalse(isPinned(pinned), "1.16: the target's gravity returns 3 ticks after the last hit");
					double base = pinned.getAttribute(Attributes.GRAVITY).getBaseValue();
					helper.assertTrue(Math.abs(gravity(pinned) - base) < EPSILON,
							"Gravity after the pin: " + gravity(pinned) + ", expected " + base);
					helper.succeed();
				}
				finally {
					cleanup(holder, attacker, other, attackerType, otherType);
				}
			});
			handedOff = true;
		}
		finally {
			if (!handedOff) {
				cleanup(holder, attacker, other, attackerType, otherType);
			}
		}
	}

	private static final class Fixture {
		StandPower attackerPower;
		StandPower otherPower;
		Pig free;
		Pig pinned;
		boolean cleaned;
	}

	private static void guarded(Fixture holder, Player attacker, Player other,
			StandType attackerType, StandType otherType, Runnable check) {
		try {
			check.run();
		}
		catch (RuntimeException | Error e) {
			cleanup(holder, attacker, other, attackerType, otherType);
			throw e;
		}
	}

	private static void cleanup(Fixture holder, Player attacker, Player other, StandType attackerType, StandType otherType) {
		if (holder.cleaned) {
			return;
		}
		holder.cleaned = true;
		if (holder.attackerPower != null && holder.attackerPower.isSummoned() && attackerType != null) {
			attackerType.forceUnsummon(attacker, holder.attackerPower);
		}
		if (holder.otherPower != null && holder.otherPower.isSummoned() && otherType != null) {
			otherType.forceUnsummon(other, holder.otherPower);
		}
		attacker.removeEffect(ModStatusEffects.RESOLVE);
		// the 1x1x1 template does not cover the pigs
		if (holder.free != null) {
			holder.free.discard();
		}
		if (holder.pinned != null) {
			holder.pinned.discard();
		}
		attacker.discard();
		other.discard();
	}

	private static Pig spawnPig(GameTestHelper helper, BlockPos pos) {
		Pig pig = helper.spawn(EntityType.PIG, pos);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		pig.setHealth(200);
		return pig;
	}

	private static boolean isPinned(Pig pig) {
		AttributeInstance gravity = pig.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(StandEntityBarrageAbility.RESOLVE_BARRAGE_NO_GRAVITY_ID);
	}

	private static double gravity(Pig pig) {
		return pig.getAttributeValue(Attributes.GRAVITY);
	}

	private static double horizontal(Entity entity) {
		return entity.getDeltaMovement().horizontalDistance();
	}

	private static Player fakePlayer(ServerLevel level, String name) {
		return FakePlayerFactory.get(level, new GameProfile(
				UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
	}

	private static StandPower addStandUser(GameTestHelper helper, Player user, StandType standType, Vec3 pos) {
		user.moveTo(pos.x, pos.y, pos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add " + user.getScoreboardName());
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant a Stand to " + user.getScoreboardName());
		helper.assertTrue(standType.summon(user, power), "Could not summon the Stand of " + user.getScoreboardName());
		helper.assertTrue(power.getSummonedStandEntity() != null, "Missing Stand entity of " + user.getScoreboardName());
		return power;
	}

	private static void hit(StandEntityBarrage barrage, Entity target, Level level, StandEntity stand) {
		try {
			Method method = StandEntityBarrage.class.getDeclaredMethod(
					"hitEntity", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			// virtual call: Silver Chariot runs its own override
			method.invoke(barrage, new ActionTarget(target), level, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the barrage hitEntity", e);
		}
	}
}
