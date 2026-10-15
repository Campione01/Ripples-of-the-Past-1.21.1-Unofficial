package rotp.core.api.stand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Which Stand skills go through walls. There is one rule, the original story's (owner, 2026-10-15): an effect that
 * appears at its target, or a Stand power that crosses matter, passes walls; the bodily attacks of ordinary Stands are
 * stopped by them. A marked skill picks and hits an entity behind blocks within its range, as the 1.16 entity rays
 * did; blocks stop the pick and the hit of every skill that is not marked. There is no server setting for it.
 *
 * <p><b>Marking a skill.</b> Mark it once, on both sides, during mod construction or common setup, by the id of its
 * Stand type and the name the Stand's moveset gives the skill (the {@link AbilityId} of the ability). A variant the
 * moveset lists under its own name (a barrage, a combo, a shift variant) is marked by that name:</p>
 * <pre>{@code
 * StandSkillWalls.markThroughWalls(ResourceLocation.fromNamespaceAndPath("rotp_wr", "weather_report"), "lightning");
 * }</pre>
 *
 * <p><b>The attacks of a Stand whose power crosses matter.</b> {@link #markAttacksThroughWalls} marks every punch,
 * heavy punch (charged too) and barrage of a Stand type at once, whatever their names, add-on subclasses of the core
 * attack abilities included. For them the user's aim decides, as the original story has it: an entity the aim meets
 * behind a wall is hit through the wall (with the aim assist within the Stand's precision boxes, without it on the
 * crosshair line), and a wall the aim meets with no entity behind it in reach is struck and broken as by any Stand,
 * within the Stand's strength and the block breaking game rule. A target the attack took from a synced aim or from the
 * target kept at the key press is picked again through walls when it is a block (see
 * {@link StandEntityPunchAbility#throughWallsTarget}).</p>
 *
 * <p><b>Core helpers that honour the mark on their own.</b> They ask {@link #wallsHideEntities(Entity)}, which looks at
 * the skill in progress: the current action of the aiming entity, of the Stand it is, or of the Stand it has out.</p>
 * <ul>
 * <li>{@link rotp.core.subsystems.target.HitResultUtil#clip(net.minecraft.world.phys.Vec3,
 * net.minecraft.world.phys.Vec3, double, double, Level, java.util.function.Predicate, Entity, double)} and
 * {@link rotp.core.subsystems.target.HitResultUtil#clipEntityLook}, which use it;</li>
 * <li>{@link rotp.core.subsystems.target.StandPrecisionRay#clipTargetsBeforeWalls} and
 * {@link rotp.core.subsystems.target.StandPrecisionRay#crosshair(Entity, StandEntity, double, boolean)};</li>
 * <li>the client's Stand aim and target marker while an action runs;</li>
 * <li>the targets of {@link StandEntityPunchAbility}, the heavy punches and the barrages, and of every action that takes
 * its target through {@link StandEntityPunchAbility#getFreshPunchTarget(StandEntity, rotp.core.subsystems.target.ActionTarget)}.</li>
 * </ul>
 * <p>An action that aims through these needs no further code. The World's time-stop punch, the time-stop blink and
 * Star Platinum's inhale ask for themselves; the aim point of Hierophant Green's barrier net always passes walls (its
 * emeralds do not). The synced aims a client sends (its crosshair target and the Stand's aim)
 * are picked while no action runs, so they stop at walls; an action that takes its target from them re-picks a block
 * through {@link StandEntityPunchAbility#throughWallsTarget}, as the core attacks do.</p>
 *
 * <p><b>When an add-on passes the answer itself.</b> A skill that is not the current action of its user or Stand (an
 * ability that runs no action, a delayed or summoned effect), or that runs its own entity search, ray or line of
 * sight check, asks {@link #wallsHideEntities(Level, Object)} with itself and passes the answer on:
 * {@link rotp.core.subsystems.target.HitResultUtil#clip(net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3,
 * double, double, Level, java.util.function.Predicate, Entity, double, boolean)},
 * {@link rotp.core.subsystems.target.StandPrecisionRay#clipTargets},
 * {@link rotp.core.subsystems.target.StandPrecisionRay#trace(Entity, StandEntity, double, double, boolean, boolean)}
 * and {@link rotp.core.subsystems.target.StandPrecisionRay#crosshair(Entity, StandEntity, double, boolean, boolean)}
 * take it, and a line of sight check is skipped when the answer is false.</p>
 *
 * <p>A mark changes neither projectiles, which still collide with blocks, nor
 * {@link rotp.core.subsystems.target.ActionTargetRange}, which keeps 1.16's shorter range to an entity out of
 * sight.</p>
 */
public final class StandSkillWalls {
	private static final Set<Key> MARKED = ConcurrentHashMap.newKeySet();
	private static final Set<ResourceLocation> ATTACKS_MARKED = ConcurrentHashMap.newKeySet();

	private StandSkillWalls() {}

	/**
	 * Marks a skill as one that goes through walls.
	 * @param standTypeId the id of the Stand type whose moveset holds the skill
	 * @param nameInMoveset the name the moveset gives the skill
	 */
	public static void markThroughWalls(ResourceLocation standTypeId, String nameInMoveset) {
		MARKED.add(new Key(Objects.requireNonNull(standTypeId, "standTypeId"),
				Objects.requireNonNull(nameInMoveset, "nameInMoveset")));
	}

	/** Takes a mark back. Returns whether the skill was marked. */
	public static boolean unmarkThroughWalls(ResourceLocation standTypeId, String nameInMoveset) {
		return MARKED.remove(new Key(standTypeId, nameInMoveset));
	}

	/**
	 * Marks every punch, heavy punch and barrage of a Stand type as going through walls: the user's aim decides whether
	 * they hit an entity behind a wall or strike the wall.
	 * @param standTypeId the id of the Stand type
	 */
	public static void markAttacksThroughWalls(ResourceLocation standTypeId) {
		ATTACKS_MARKED.add(Objects.requireNonNull(standTypeId, "standTypeId"));
	}

	/** Takes a Stand type's attack mark back. Returns whether its attacks were marked. */
	public static boolean unmarkAttacksThroughWalls(ResourceLocation standTypeId) {
		return ATTACKS_MARKED.remove(standTypeId);
	}

	/** Whether a skill is one of the attacks {@link #markAttacksThroughWalls} marks: a punch, heavy punch or barrage. */
	public static boolean isStandAttack(@Nullable Object skill) {
		if (skill instanceof EntityActionInstance action) {
			skill = action.ability;
		}
		return skill instanceof StandEntityPunchAbility || skill instanceof StandEntityHeavyPunchAbility
				|| skill instanceof StandEntityHeavyPunchChargedAbility || skill instanceof StandEntityBarrageAbility;
	}

	/**
	 * @param skill an {@link Ability}, an {@link EntityActionType}, an {@link EntityActionInstance} or an
	 * {@link AbilityId}; a bare id is not known as an attack, so only its own mark counts
	 */
	public static boolean isMarked(@Nullable Object skill) {
		if (noMarks()) {
			return false;
		}
		AbilityId id = abilityId(skill);
		if (id == null || id.powerTypeId() == null) {
			return false;
		}
		return id.nameInMoveset() != null && MARKED.contains(new Key(id.powerTypeId(), id.nameInMoveset()))
				|| ATTACKS_MARKED.contains(id.powerTypeId()) && isStandAttack(skill);
	}

	private static boolean noMarks() {
		return MARKED.isEmpty() && ATTACKS_MARKED.isEmpty();
	}

	/**
	 * Whether blocks stop this skill's entity pick and hit: true unless the skill is marked.
	 * @param level the level the skill runs in
	 */
	public static boolean wallsHideEntities(Level level, @Nullable Object skill) {
		return !isMarked(skill);
	}

	/**
	 * Whether blocks stop the entity pick or hit of the skill in progress: the current action of the aiming entity,
	 * or, for a Stand user, of its summoned Stand.
	 */
	public static boolean wallsHideEntities(@Nullable Entity aiming) {
		if (aiming == null || noMarks() || !(aiming instanceof LivingEntity living)) {
			return true;
		}
		Level level = living.level();
		if (!wallsHideEntities(level, currentAction(living))) {
			return false;
		}
		if (!(living instanceof StandEntity)) {
			StandPower power = StandPower.get(living);
			StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
			return stand == null || wallsHideEntities(level, currentAction(stand));
		}
		return true;
	}

	@Nullable
	private static EntityActionInstance currentAction(LivingEntity performer) {
		return LivingComponentAction.getCurEntityAction(performer);
	}

	@Nullable
	private static AbilityId abilityId(@Nullable Object skill) {
		if (skill instanceof EntityActionInstance action) {
			skill = action.ability;
		}
		if (skill instanceof AbilityId id) {
			return id;
		}
		if (skill instanceof EntityActionType type) {
			return type.getAbilityId();
		}
		if (skill instanceof Ability ability) {
			return ability.getAbilityId();
		}
		return null;
	}

	private record Key(ResourceLocation standTypeId, String nameInMoveset) {}
}
