package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A Survival user with a summoned Stand, held still above its test cell, that presses one of the Stand's abilities
 * through the real condition check and key press. 1.16 checked no stamina before a Stand action
 * (PowerBaseImpl.checkRequirements) and its Stand task paid the costs with the result ignored, a short bar dropping
 * to 0 (StandEntityTask, StandPower.consumeStamina).
 */
final class EmptyStaminaBarScene {
	static final short KEY = 124;

	final GameTestHelper helper;
	final ServerLevel level;
	Player user;
	StandPower power;
	StandType type;
	StandEntity stand;
	private final List<Entity> spawned = new ArrayList<>();
	private final List<Consumer<EntityTickEvent.Post>> standTicks = new ArrayList<>();
	private Consumer<EntityJoinLevelEvent> joinListener;
	private boolean closed;

	EmptyStaminaBarScene(GameTestHelper helper) {
		this.helper = helper;
		this.level = helper.getLevel();
	}

	/**
	 * Grants and summons the Stand at a Resolve level with the given skills unlocked; the user looks along xRot from
	 * 12 blocks up.
	 */
	void start(String standId, float xRot, int resolveLevel, String... skills) {
		user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 12, 2.5));
		user.setNoGravity(true);
		user.moveTo(pos.x, pos.y, pos.z, 0, xRot);
		helper.assertTrue(level.addFreshEntity(user), "Could not add the " + standId + " user");
		type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
		power.setResolveLevel(resolveLevel);
		for (String skill : skills) {
			power.getCurTypeData()._setSkillUnlocked(skill, true, false);
		}
		helper.assertTrue(type.summon(user, power), "Could not summon " + standId);
		stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "The summoned " + standId + " is missing");
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.setXRot(xRot);
		// full stamina first: the Stand's strength multipliers read it
		power.setStamina(power.getMaxStamina());
		StandEntity.updateStandStatAttributes(stand, user);
		stand.updateStrengthMultipliers();
	}

	/** Keeps every new entity the test cares about, to count it and to discard it at the end. */
	List<Entity> trackSpawns(Predicate<Entity> filter) {
		List<Entity> found = new ArrayList<>();
		joinListener = event -> {
			if (!closed && event.getLevel() == level && filter.test(event.getEntity())) {
				found.add(event.getEntity());
				spawned.add(event.getEntity());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);
		return found;
	}

	/** Runs after each tick of this scene's Stand, where its action ticks. */
	void afterStandTick(Runnable onTick) {
		Consumer<EntityTickEvent.Post> listener = event -> {
			if (!closed && event.getEntity() == stand) {
				onTick.run();
			}
		};
		standTicks.add(listener);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, listener);
	}

	/** Sets the bar and presses the ability as a player would; true if the condition check let it through. */
	boolean pressOnBar(String abilityName, InputMethod method, float stamina) {
		power.setStamina(stamina);
		forgetCachedMoves();
		Ability ability = power.getAbility(abilityName);
		helper.assertTrue(ability != null, "Missing ability " + abilityName);
		AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
		helper.assertTrue(selected != null, abilityName + " is not in the moveset");
		if (!AbilityInput.withConditionCheck(selected, user, method)) {
			return false;
		}
		BufferingState press = BufferingState.clickOnly();
		AbilityInput.keyPress(KEY, selected.ability, user, null, method, 0, press, selected.ability.getAbilityId());
		helper.assertTrue(press.isActionSuccess, "The admitted " + abilityName + " press did not start the action");
		return true;
	}

	// the moves and their condition checks are cached for the tick
	private void forgetCachedMoves() {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
	}

	void close() {
		if (closed) return;
		closed = true;
		if (joinListener != null) {
			NeoForge.EVENT_BUS.unregister(joinListener);
		}
		standTicks.forEach(NeoForge.EVENT_BUS::unregister);
		spawned.forEach(Entity::discard);
		if (user != null) {
			AbilityInput.keyRelease(KEY, user);
			if (power != null && type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
