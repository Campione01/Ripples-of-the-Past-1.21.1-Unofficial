package rotp.core.api.stand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
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
 * Stand skills that go through walls, for the server setting "standSkillsThroughWalls" of the common config.
 *
 * <p>In the "manga" mode (the default) a marked skill picks and hits an entity behind blocks within its range, as the
 * 1.16 entity rays did. In the "walls" mode, and for every skill that is not marked, blocks stop the pick and the
 * hit.</p>
 *
 * <p>A core or add-on skill is marked once, during mod construction or common setup, by the id of its Stand type and
 * the name the Stand's moveset gives the skill (the {@link AbilityId} of the ability):</p>
 * <pre>{@code
 * StandSkillWalls.markThroughWalls(ResourceLocation.fromNamespaceAndPath("rotp_wr", "weather_report"), "lightning");
 * }</pre>
 *
 * <p>The core asks {@link #wallsHideEntities(Entity)} wherever it stops an entity pick or a hit at a block:
 * {@link rotp.core.subsystems.target.HitResultUtil#clip}, the client's Stand aim that uses it,
 * {@link rotp.core.subsystems.target.StandPrecisionRay} and the line of sight checks of Stand hits. The skill asked
 * about is the current action of the aiming entity, of the Stand it is or of the Stand it has out, so a marked action
 * that aims through these needs no further code. A skill that is not an action of its user or Stand, or that runs its
 * own ray, asks {@link #wallsHideEntities(Level, Object)} with itself and passes the answer on (for example to
 * {@link rotp.core.subsystems.target.HitResultUtil#clip(net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3,
 * double, double, Level, java.util.function.Predicate, Entity, double, boolean)}).</p>
 */
public final class StandSkillWalls {
	private static final Set<Key> MARKED = ConcurrentHashMap.newKeySet();

	private StandSkillWalls() {}

	/**
	 * Marks a skill as one that goes through walls in the "manga" mode.
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

	/** Whether blocks stop this skill's entity pick and hit, by the server setting (the synced copy on a client). */
	public static boolean wallsHideEntities(Level level, @Nullable Object skill) {
		return !isMarked(skill) || !JojoModConfig.getCommonConfigInstance(level.isClientSide()).skillsGoThroughWalls();
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
