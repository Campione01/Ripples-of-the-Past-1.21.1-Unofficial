package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility.TheWorldHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The World's heavy punch and kick, as 1.16 TheWorldHeavyPunch / TheWorldKick (shout DIO_DIE) and
 * TheWorldHeavyPunchInstance.afterAttack: a press queued while the Stand is busy shouts only when the attack really
 * starts, and a heavy punch that hurts any entity frozen in stopped time, not only a living one, makes its impact
 * sound when time resumes.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldHeavyShoutAndImpactGameTests {
	private static final short PUNCH = 151;
	private static final short HEAVY = 152;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc(TheWorldHeavyShoutAndImpactGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));

	private TheWorldHeavyShoutAndImpactGameTests() {}

	private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void queuedHeavyOrKickShoutsWhenItStarts(GameTestHelper helper) {
		ShoutScene heavy = new ShoutScene(helper, false, new Vec3(0, 0, 0));
		ShoutScene kick = new ShoutScene(helper, true, new Vec3(4, 0, 0));
		try {
			heavy.start();
			kick.start();
		}
		catch (RuntimeException | Error e) {
			heavy.close();
			kick.close();
			throw e;
		}
		helper.onEachTick(() -> {
			try {
				heavy.tick();
				kick.tick();
				if (heavy.done && kick.done) {
					heavy.close();
					kick.close();
					helper.succeed();
				}
			}
			catch (RuntimeException | Error e) {
				heavy.close();
				kick.close();
				throw e;
			}
		});
	}

	@GameTest(template = "empty", timeoutTicks = 120, batch = GameTestBatches.TIME_STOP)
	public static void frozenBoatHurtByHeavyPunchSoundsOnTimeResume(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TimeStopState stop = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		List<Entity> owned = new ArrayList<>();
		List<Vec3> impactSounds = new ArrayList<>();
		Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
			if (event.getLevel() == level && is(event.getSound(), ModSoundEvents.THE_WORLD_PUNCH_HEAVY_TS_IMPACT)) {
				impactSounds.add(event.getPosition());
			}
		};
		Vec3 origin = origin(helper, Vec3.ZERO);
		// A weak The World: the boat survives the punch (its damage stays under 40).
		Actor caster = actor(helper, owned, origin, 3);
		Boat boat = EntityType.BOAT.create(level);
		helper.assertTrue(boat != null, "Could not create the boat");
		owned.add(boat);
		boat.setNoGravity(true);
		boat.moveTo(origin.x, origin.y, origin.z + 1.5, 0, 0);
		helper.assertTrue(level.addFreshEntity(boat), "Could not add the boat");
		int stopId = caster.user.getId();
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(listener);
			stop.removeInstance(stopId);
			close(caster, owned);
		};
		EntityActionInstance[] heavy = new EntityActionInstance[1];
		try {
			unlock(helper, caster, "time_stop");
			caster.power.setResolveLevel(Math.max(2, caster.power.getAbility("time_stop").getRequiredResolveLevel(caster.power)));
			caster.user.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200, 0));
			helper.assertTrue(stop.tryPutInstance(new TimeStopState.Instance(stopId, 200, 200, new ChunkPos(boat.blockPosition()), 1,
					caster.user.getId(), "r769_the_world_heavy_ts_impact")), "The time stop was not admitted");
			helper.assertTrue(stop.shouldFreeze(boat) && !stop.shouldFreeze(caster.stand),
					"The boat must be frozen and The World free to move");
			aim(caster, new ActionTarget(boat), boat.position());
			var press = press(caster, "heavy_punch", HEAVY, BufferingState.clickOnly());
			helper.assertTrue(press instanceof TheWorldHeavyPunch, "The World's heavy punch did not start: " + press);
			heavy[0] = press;
		}
		catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
		helper.onEachTick(() -> {
			try {
				EntityActionInstance action = heavy[0];
				if (action == null || action.punchedTarget == null) {
					return;
				}
				heavy[0] = null;
				helper.assertTrue(action.punchedTarget.getMainEntity() == boat, "The heavy punch did not hit the boat");
				helper.assertTrue(!boat.isRemoved() && boat.getDamage() > 0,
						"The frozen boat was not hurt, or did not survive: damage=" + boat.getDamage() + " removed=" + boat.isRemoved());
				helper.assertTrue(impactSounds.isEmpty(), "The stopped-time impact sound played before time resumed");
				stop.removeInstance(stopId);
				helper.assertTrue(impactSounds.size() == 1 && impactSounds.get(0).distanceTo(boat.position()) < 1,
						"A heavy punch that hurt a frozen boat made " + impactSounds.size()
						+ " stopped-time impact sounds at the boat when time resumed; 1.16 made one");
				cleanup.run();
				helper.succeed();
			}
			catch (RuntimeException | Error e) {
				cleanup.run();
				throw e;
			}
		});
	}

	private static final class ShoutScene {
		final GameTestHelper helper;
		final boolean kick;
		final Vec3 offset;
		final List<Entity> owned = new ArrayList<>();
		final Consumer<PlayLevelSoundEvent.AtPosition> listener;
		Actor caster;
		int shouts;
		boolean done;
		boolean closed;
		boolean listening;

		ShoutScene(GameTestHelper helper, boolean kick, Vec3 offset) {
			this.helper = helper;
			this.kick = kick;
			this.offset = offset;
			this.listener = event -> {
				if (caster != null && event.getLevel() == helper.getLevel() && is(event.getSound(), ModSoundEvents.DIO_DIE)
						&& event.getPosition().distanceTo(caster.user.position()) < 0.5) {
					shouts++;
				}
			};
		}

		void start() {
			Vec3 origin = origin(helper, offset);
			caster = actor(helper, owned, origin, 15);
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create the target");
			owned.add(cow);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			cow.setHealth(200);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.moveTo(origin.x, origin.y, origin.z + 1.7, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the target");
			aim(caster, new ActionTarget(cow), cow.getBoundingBox().getCenter());
			if (kick) {
				unlock(helper, caster, "kick");
				caster.stand.setFinisherMeter(0.75F);
			}
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
			listening = true;

			EntityActionInstance punch = press(caster, "punch", PUNCH, BufferingState.clickOnly());
			helper.assertTrue(punch instanceof StandEntityPunch, "The World's light punch did not start");
			BufferingState buffer = BufferingState.clickCanBuffer();
			press(caster, "heavy_punch", HEAVY, buffer);
			helper.assertTrue(buffer.shouldBuffer && !buffer.isActionSuccess && caster.stand.getCurStandAction() == punch,
					"The " + what() + " press during the light punch's windup was not queued");
			helper.assertTrue(shouts == 0, "The queued " + what() + " press shouted DIO_DIE " + shouts
					+ " time(s) before the attack started; 1.16 shouted only when it started");
		}

		void tick() {
			if (done) {
				return;
			}
			EntityActionInstance current = caster.stand.getCurStandAction();
			if (kick ? current instanceof TheWorldKick : current instanceof TheWorldHeavyPunch && !(current instanceof TheWorldKick)) {
				helper.assertTrue(shouts == 1, "The " + what() + " started with " + shouts + " DIO_DIE shouts, expected 1");
				done = true;
			}
			else if (current != null && !(current instanceof StandEntityPunch)) {
				throw new AssertionError("The queued " + what() + " started as " + current.getClass().getSimpleName());
			}
		}

		String what() {
			return kick ? "kick" : "heavy punch";
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (listening) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (caster != null) {
				TheWorldHeavyShoutAndImpactGameTests.close(caster, owned);
			}
			else {
				owned.forEach(Entity::discard);
			}
		}
	}

	private static Vec3 origin(GameTestHelper helper, Vec3 offset) {
		ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
		return new Vec3(chunk.getMiddleBlockX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 8,
				chunk.getMiddleBlockZ() + 0.5).add(offset);
	}

	private static Actor actor(GameTestHelper helper, List<Entity> owned, Vec3 position, double strength) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.getAbilities().invulnerable = false;
		user.setNoGravity(true);
		user.noPhysics = true;
		user.moveTo(position.x, position.y, position.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add The World's user");
		owned.add(user);
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant The World");
		power.setResolveLevel(2);
		helper.assertTrue(type.summon(user, power), "Could not summon The World");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null && stand.getUser() == user, "The World's Stand is missing");
		owned.add(stand);
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.setCanFollowUser(false);
		stand.setNoGravity(true);
		stand.moveTo(position.x, position.y, position.z, 0, 0);
		stand.setYHeadRot(0);
		user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(strength);
		user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
		user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
		user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
		user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
		StandEntity.updateStandStatAttributes(stand, user);
		power.setStamina(power.getMaxStamina());
		stand.updateStrengthMultipliers();
		user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
		return new Actor(user, power, type, stand);
	}

	private static void aim(Actor actor, ActionTarget target, Vec3 point) {
		LivingComponentAction.getComponent(actor.user).entityAim.setTarget(target);
		LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(target);
		actor.user.lookAt(EntityAnchorArgument.Anchor.EYES, point);
		actor.user.setYHeadRot(actor.user.getYRot());
		actor.stand.lookAt(EntityAnchorArgument.Anchor.EYES, point);
		actor.stand.setYHeadRot(actor.stand.getYRot());
	}

	private static void unlock(GameTestHelper helper, Actor actor, String skill) {
		var result = UNLOCKS.forceUnlock(actor.power, skill);
		helper.assertTrue(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
				"Could not unlock " + skill);
	}

	private static EntityActionInstance press(Actor actor, String name, short key, BufferingState buffer) {
		Ability base = actor.power.getAbility(name);
		if (base == null) {
			throw new AssertionError("Missing registered " + name);
		}
		var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
		if (selected == null || !AbilityInput.withConditionCheck(selected, actor.user, InputMethod.CLICK)) {
			throw new AssertionError("The " + name + " input was refused");
		}
		var input = AbilityInput.keyPress(key, selected.ability, actor.user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
		return input != null && input.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
	}

	private static boolean is(Holder<SoundEvent> sound, Holder<SoundEvent> expected) {
		return sound != null && sound.value() == expected.value();
	}

	private static void close(Actor actor, List<Entity> owned) {
		AbilityInput.keyRelease(PUNCH, actor.user);
		AbilityInput.keyRelease(HEAVY, actor.user);
		LivingComponentAction.getComponent(actor.stand).setAction(null, SyncType.NO_SYNC);
		actor.user.removeAllEffects();
		if (actor.power.isSummoned()) {
			actor.type.forceUnsummon(actor.user, actor.power);
		}
		owned.forEach(Entity::discard);
	}
}
