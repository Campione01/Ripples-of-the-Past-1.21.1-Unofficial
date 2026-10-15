package rotp.core.api.stand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

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
 * <p><b>Core helpers that honour the mark on their own.</b> They ask {@link #wallsHideEntities(Entity)}, which looks at
 * the skill in progress: the current action of the aiming entity, of the Stand it is, or of the Stand it has out.</p>
 * <ul>
 * <li>{@link rotp.core.subsystems.target.HitResultUtil#clip(net.minecraft.world.phys.Vec3,
 * net.minecraft.world.phys.Vec3, double, double, Level, java.util.function.Predicate, Entity, double)} and
 * {@link rotp.core.subsystems.target.HitResultUtil#clipEntityLook}, which use it;</li>
 * <li>{@link rotp.core.subsystems.target.StandPrecisionRay#clipTargetsBeforeWalls} and
 * {@link rotp.core.subsystems.target.StandPrecisionRay#crosshair(Entity, StandEntity, double, boolean)};</li>
 * <li>the client's Stand aim and target marker, which aim through these.</li>
 * </ul>
 * <p>An action that aims through these needs no further code. The World's time-stop punch, the time-stop blink and
 * Star Platinum's inhale ask for themselves.</p>
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
	 * @param skill an {@link Ability}, an {@link EntityActionType}, an {@link EntityActionInstance} or an
	 * {@link AbilityId}
	 */
	public static boolean isMarked(@Nullable Object skill) {
		if (MARKED.isEmpty()) {
			return false;
		}
		AbilityId id = abilityId(skill);
		return id != null && id.powerTypeId() != null && id.nameInMoveset() != null
				&& MARKED.contains(new Key(id.powerTypeId(), id.nameInMoveset()));
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
		if (aiming == null || MARKED.isEmpty() || !(aiming instanceof LivingEntity living)) {
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
