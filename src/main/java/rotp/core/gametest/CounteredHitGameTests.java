package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 C-Moon's gravity counter cancelled the hit at LivingHurtEvent, so LivingDamageEvent never fired for it: the
 * user's Resolve bookkeeping (ResolveCounter.onGettingAttacked) and the held action stop
 * (PowerBaseImpl.onUserGettingAttacked) did not see it. The port's counter zeroes the hit at LivingDamageEvent.Pre
 * (HIGH) and marks its source countered on its own target; a hit zeroed there without the mark still reaches both, as
 * the controls show. Vanilla hurts every target of a sweep or an explosion with one source; 1.16 cancelled the hit for
 * the countering target only, so another target of the same source still records it and still drops its hold.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CounteredHitGameTests {
	private static final short KEY = 123;
	private static final float HIT = 10.0F;

	private CounteredHitGameTests() {}

	@GameTest(template = "empty")
	public static void counteredHitRecordsNoResolveBookkeeping(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Pig attacker = EntityType.PIG.create(helper.getLevel());
		Counter counter = new Counter(user);
		try {
			place(helper, user, new Vec3(1.5, 2, 1.5), 0.0F);
			helper.assertTrue(attacker != null, "fixture: no attacker");
			attacker.setNoAi(true);
			place(helper, attacker, new Vec3(1.5, 2, 3.5), 180.0F);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && power.usesResolve(), "fixture: Magician's Red with Resolve not granted");
			ResolveCounter resolve = power.resolveCounter;
			helper.assertTrue(!resolve.hpOnGettingAttacked.isPresent() && resolve.getResolveValue() == 0,
					"fixture: the new Stand already has Resolve bookkeeping");

			counter.hit(attacker, true);
			helper.assertTrue(!resolve.hpOnGettingAttacked.isPresent(),
					"1.16 a countered hit never reached the Resolve bookkeeping: hpOnGettingAttacked was recorded at "
							+ resolve.hpOnGettingAttacked.orElseGet(() -> -1) + " resolve=" + resolve.getResolveValue());

			counter.hit(attacker, false);
			helper.assertTrue(resolve.hpOnGettingAttacked.isPresent(),
					"fixture: a hit zeroed without the countered mark did not reach the Resolve bookkeeping");
			helper.assertTrue(user.getHealth() == user.getMaxHealth(), "fixture: a zeroed hit hurt the user: " + user.getHealth());
		}
		finally {
			counter.close();
			if (attacker != null) attacker.discard();
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anotherTargetOfACounteredSourceRecordsResolve(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Cow countering = EntityType.COW.create(helper.getLevel());
		Pig attacker = EntityType.PIG.create(helper.getLevel());
		Counter counter = new Counter(countering);
		try {
			place(helper, user, new Vec3(1.5, 2, 1.5), 0.0F);
			helper.assertTrue(countering != null && attacker != null, "fixture: no countering cow or attacker");
			countering.setNoAi(true);
			place(helper, countering, new Vec3(3.5, 2, 1.5), 90.0F);
			attacker.setNoAi(true);
			place(helper, attacker, new Vec3(1.5, 2, 3.5), 180.0F);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && power.usesResolve(), "fixture: Magician's Red with Resolve not granted");
			ResolveCounter resolve = power.resolveCounter;

			// one source for both, as a sword sweep or a blast
			DamageSource sweep = user.damageSources().mobAttack(attacker);
			counter.hit(sweep, true);
			helper.assertTrue(DamageSourceModified.isCountered(sweep, countering), "fixture: the counter did not mark its hit");
			user.invulnerableTime = 0;
			user.hurt(sweep, HIT);
			helper.assertTrue(resolve.hpOnGettingAttacked.isPresent(),
					"1.16 cancelled the hit for the countering target only, but the user hurt by the same source after it"
							+ " recorded no Resolve bookkeeping");
		}
		finally {
			counter.close();
			if (countering != null) countering.discard();
			if (attacker != null) attacker.discard();
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anotherTargetOfACounteredSourceDropsItsHold(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Cow prey = EntityType.COW.create(helper.getLevel());
		Cow countering = EntityType.COW.create(helper.getLevel());
		Pig attacker = EntityType.PIG.create(helper.getLevel());
		Counter counter = new Counter(countering);
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		boolean held = false;
		try {
			place(helper, user, new Vec3(1.5, 2, 1.5), 0.0F);
			helper.assertTrue(prey != null && countering != null && attacker != null, "fixture: no prey, countering cow or attacker");
			prey.setNoAi(true);
			place(helper, prey, new Vec3(1.5, 2, 3.0), 180.0F);
			countering.setNoAi(true);
			place(helper, countering, new Vec3(0.5, 2, 3.5), 180.0F);
			attacker.setNoAi(true);
			place(helper, attacker, new Vec3(3.5, 2, 1.5), 90.0F);

			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			Ability drain = power.getAbility("vampirism_blood_drain");
			component.entityAim.setTarget(new ActionTarget(prey));
			helper.assertTrue(drain instanceof VampirismBloodDrainAbility && drain.checkConditions(power).isPositive(),
					"fixture: Blood Drain is not usable on the cow");
			HeldInputEntry entry = AbilityInput.keyPress(KEY, drain, user, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), drain.getAbilityId());
			held = true;
			EntityActionInstance action = component.getAction();
			helper.assertTrue(entry != null && action != null && entry.action == action
					&& action.getPhase() == ActionPhase.PERFORM, "fixture: the Blood Drain hold did not start");

			DamageSource sweep = user.damageSources().mobAttack(attacker);
			counter.hit(sweep, true);
			helper.assertTrue(DamageSourceModified.isCountered(sweep, countering), "fixture: the counter did not mark its hit");
			helper.assertTrue(component.getAction() == action && !action.isOver(),
					"fixture: the countered hit on the cow stopped the user's Blood Drain");
			user.invulnerableTime = 0;
			user.hurt(sweep, HIT);
			helper.assertTrue(action.isOver() || component.getAction() != action,
					"1.16 cancelled the hit for the countering target only, but the user hurt by the same source after it"
							+ " kept its held Blood Drain");
		}
		finally {
			counter.close();
			if (held) AbilityInput.keyRelease(KEY, user);
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, user, SyncType.NO_SYNC);
			if (prey != null) prey.discard();
			if (countering != null) countering.discard();
			if (attacker != null) attacker.discard();
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void counteredHitDoesNotStopAHeldAction(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Cow prey = EntityType.COW.create(helper.getLevel());
		Pig attacker = EntityType.PIG.create(helper.getLevel());
		Counter counter = new Counter(user);
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		boolean held = false;
		try {
			place(helper, user, new Vec3(1.5, 2, 1.5), 0.0F);
			helper.assertTrue(prey != null && attacker != null, "fixture: no prey or attacker");
			prey.setNoAi(true);
			place(helper, prey, new Vec3(1.5, 2, 3.0), 180.0F);
			attacker.setNoAi(true);
			place(helper, attacker, new Vec3(3.5, 2, 1.5), 90.0F);
			// the armour cut is what makes a hit zeroed at Pre still count at LivingDamageEvent.Post
			user.getAttribute(Attributes.ARMOR).setBaseValue(10.0D);

			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			Ability drain = power.getAbility("vampirism_blood_drain");
			component.entityAim.setTarget(new ActionTarget(prey));
			helper.assertTrue(drain instanceof VampirismBloodDrainAbility && drain.checkConditions(power).isPositive(),
					"fixture: Blood Drain is not usable on the cow");
			HeldInputEntry entry = AbilityInput.keyPress(KEY, drain, user, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), drain.getAbilityId());
			held = true;
			EntityActionInstance action = component.getAction();
			helper.assertTrue(entry != null && action != null && entry.action == action
					&& action.getPhase() == ActionPhase.PERFORM, "fixture: the Blood Drain hold did not start");

			counter.hit(attacker, true);
			helper.assertTrue(component.getAction() == action && !action.isOver() && AbilityInput.isHeldByKey(user, action),
					"1.16 a countered hit never stopped a held action: over=" + action.isOver()
							+ " current=" + (component.getAction() == action));

			counter.hit(attacker, false);
			helper.assertTrue(action.isOver() || component.getAction() != action,
					"fixture: a hit zeroed without the countered mark did not stop the held Blood Drain");
		}
		finally {
			counter.close();
			if (held) AbilityInput.keyRelease(KEY, user);
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, user, SyncType.NO_SYNC);
			if (prey != null) prey.discard();
			if (attacker != null) attacker.discard();
			user.discard();
		}
		helper.succeed();
	}

	private static void place(GameTestHelper helper, LivingEntity entity, Vec3 relative, float yaw) {
		Vec3 pos = helper.absoluteVec(relative);
		entity.setNoGravity(true);
		entity.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "fixture: could not add " + entity);
	}

	/** A counter on the target at C-Moon's priority: it zeroes each hit, and marks it countered when asked. */
	private static final class Counter implements AutoCloseable {
		private final LivingEntity target;
		private final Consumer<LivingDamageEvent.Pre> listener;
		private boolean markCountered;

		Counter(LivingEntity target) {
			this.target = target;
			this.listener = event -> {
				if (event.getEntity() == target) {
					event.setNewDamage(0.0F);
					if (markCountered) {
						((DamageSourceModified) event.getSource()).jojo_ripples$setCounteredTarget(target);
					}
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingDamageEvent.Pre.class, listener);
		}

		void hit(LivingEntity attacker, boolean countered) {
			hit(target.damageSources().mobAttack(attacker), countered);
		}

		void hit(DamageSource source, boolean countered) {
			markCountered = countered;
			target.invulnerableTime = 0;
			target.hurt(source, HIT);
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
	}
}
