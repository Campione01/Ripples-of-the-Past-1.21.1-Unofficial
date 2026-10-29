package rotp.addons.ger.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerBeamEntity;
import rotp.addons.ger.entity.GerStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GerLifeShotAbility extends StandEntityAbility {
    public static final double LEGACY_RANGE = 150.0D;
    public static final int LEGACY_MAX_HITS = 8;
    public static final int LEGACY_COOLDOWN_TICKS = 100;
    public static final float LEGACY_STAMINA_COST = 100.0F;
    public static final int VISUAL_LIFETIME_TICKS = 16;
    public static final float MULTI_SPREAD_DEGREES = 5.0F;

    private final int shotCount;

    public GerLifeShotAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            int shotCount) {
        super(abilityType, abilityId, LifeShotAction::new);
        this.shotCount = Math.max(1, shotCount);
        if (this.shotCount > 1) {
            setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 20);
        } else {
            setDefaultPhaseLength(ActionPhase.WINDUP, 10);
        }
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
        partsRequired(StandPart.ARMS);
        cooldown(LEGACY_COOLDOWN_TICKS);
    }

    int shotCount() {
        return shotCount;
    }

    // 1.16 ger_life_shot_multi, holdToFire(20, true), set the cooldown when
    // the held shot fired, so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return shotCount <= 1;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        StandPower power = user != null ? StandPower.get(user) : null;
        if (user == null || power == null) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck shotInputs = checkShotInputs(user);
        if (!shotInputs.isPositive()) {
            return shotInputs;
        }
        if (!GoldExperienceCreateLifeformAbility
                .canCreateMoreLifeforms(power)) {
            return ConditionCheck.createNegative(
                    "ge_too_many_mobs");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction and stopHeldAction(true) re-ran checkConditions:
    // a charge that loses its offhand material stops with the message.
    // Cooldown and stamina stay press/fire checks, as in 1.16.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkShotInputs(user);
    }

    // What the shot needs when pressed, while charging and on release.
    private ConditionCheck checkShotInputs(LivingEntity user) {
        if (GoldExperienceRequiemAddon.activeStand(user) == null) {
            return ConditionCheck.NEGATIVE;
        }
        ItemStack offhand = user.getOffhandItem();
        if (offhand.isEmpty() || offhand.getCount() < shotCount) {
            return ConditionCheck.createNegative(
                    "life_shot_material");
        }
        if (!GoldExperienceCreateLifeformAbility
                .hasSelectedLifeform(user)) {
            return ConditionCheck.createNegative(
                    "choose_lifeform");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class LifeShotAction
            extends EntityActionInstance {
        public LifeShotAction(EntityActionType ability) {
            super(ability);
        }

        private boolean isHeldMultiShot() {
            return ability instanceof GerLifeShotAbility config
                    && config.shotCount() > 1;
        }

        // 1.16 holdToFire(20, true): a full charge waits until the key
        // is let go and fires then; letting go earlier cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return isHeldMultiShot()
                    && getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        @Override
        public void onButtonStopHold() {
            if (isHeldMultiShot()
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                // 1.16 stopHeldAction(true) checked the requirements once
                // more before the released charge fired.
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof GerLifeShotAbility config
                        && config.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.WINDUP);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof GerStandEntity stand)
                    || GoldExperienceRequiemAddon.activeStand(owner)
                            != stand
                    || !(ability instanceof GerLifeShotAbility config)) {
                return;
            }
            StandPower power = StandPower.get(owner);
            ItemStack material = owner.getOffhandItem();
            int shots = config.shotCount();
            if (material.isEmpty()
                    || material.getCount() < shots
                    || !GoldExperienceCreateLifeformAbility
                            .hasSelectedLifeform(owner)) {
                return;
            }
            if (shots > 1) {
                config.setCooldownOnUse(power);
            }
            // 1.16 LifeShot.standPerform: the only stamina check; a short
            // bar empties and the shot is not fired, without a message.
            if (!StandAbilityStamina.consume(
                    ability,
                    power,
                    LEGACY_STAMINA_COST,
                    false)) {
                return;
            }
            ItemStack sourceItem = material.copy();
            sourceItem.setCount(1);
            if (!owner.getAbilities().instabuild) {
                material.shrink(shots);
            }

            StandUtil.playStandEntitySound(
                    stand,
                    GoldExperienceRequiemAddon.LIFE_SHOT_SOUND,
                    1.0F,
                    1.0F);
            for (int index = 0; index < shots; index++) {
                Vec3 direction = owner.getLookAngle().normalize();
                if (shots > 1) {
                    direction = applyRandomRotation(
                            direction,
                            serverLevel.getRandom());
                }
                fire(serverLevel,
                        owner,
                        stand,
                        direction,
                        sourceItem.copy());
            }
        }

        private void fire(
                ServerLevel level,
                ServerPlayer owner,
                GerStandEntity stand,
                Vec3 direction,
                ItemStack sourceItem) {
            Vec3 start = stand.getEyePosition();
            Vec3 aimStart = owner.getEyePosition();
            Vec3 rawEnd = aimStart.add(
                    direction.scale(LEGACY_RANGE));
            Vec3 end = traceToBlockAllowingTranslucent(
                    level,
                    owner,
                    aimStart,
                    rawEnd);

            List<BeamHit> hits = new ArrayList<>();
            AABB search = new AABB(start, end).inflate(1.0D);
            for (Entity entity : level.getEntities(
                    stand,
                    search,
                    candidate -> candidate
                                    instanceof LivingEntity living
                            && living != owner
                            && living.isAlive()
                            && living.isPickable())) {
                LivingEntity living = (LivingEntity) entity;
                Vec3 delta = end.subtract(start);
                double lengthSqr = delta.lengthSqr();
                double projection = projectionT(
                        start,
                        delta,
                        lengthSqr,
                        living.getBoundingBox().getCenter());
                if (projection < 0.0D || projection > 1.0D) {
                    continue;
                }
                Vec3 closest = start.add(delta.scale(projection));
                if (distanceToAabbSqr(
                        living.getBoundingBox(),
                        closest) <= 0.5625D) {
                    hits.add(new BeamHit(living, projection));
                }
            }
            hits.sort(Comparator.comparingDouble(BeamHit::distanceSqr));
            if (hits.size() > LEGACY_MAX_HITS) {
                end = getEntityBeamEnd(
                        hits.get(LEGACY_MAX_HITS - 1).target(),
                        start,
                        end);
            }

            LivingEntity primary = null;
            int hitCount = 0;
            for (BeamHit hit : hits) {
                if (hitCount++ >= LEGACY_MAX_HITS) {
                    break;
                }
                if (primary == null) {
                    primary = hit.target();
                }
                // 1.16 LifeShot.onHit: a plain hurt with
                // DamageSource.indirectMagic(stand, user), so armour, the
                // Stand damage multiplier and Stand-only rules never applied.
                hit.target().hurt(
                        level.damageSources().indirectMagic(stand, owner),
                        (float) stand.getAttackDamage());
            }

            StandPower power = StandPower.get(owner);
            Entity lifeform =
                    GoldExperienceCreateLifeformAbility
                                    .canCreateMoreLifeforms(power)
                            ? GoldExperienceCreateLifeformAbility
                                    .createSelectedLifeformEntity(
                                            level,
                                            owner)
                            : null;
            if (lifeform != null) {
                Vec3 spawn = end.subtract(direction.scale(0.5D));
                lifeform.moveTo(
                        spawn.x,
                        spawn.y,
                        spawn.z,
                        owner.getYRot(),
                        0.0F);
                if (lifeform instanceof Mob mob
                        && primary != null
                        && primary.isAlive()) {
                    mob.setTarget(primary);
                }
                if (level.addFreshEntity(lifeform)) {
                    GECreatedLifeformEffect effect =
                            ModStandAbilities
                                    .EFFECT_GE_CREATED_LIFEFORM
                                    .get()
                                    .create(level);
                    effect.setSourceItem(sourceItem);
                    if (primary != null && primary.isAlive()) {
                        effect.setFollowTarget(
                                primary.getUUID(),
                                GETransformationEntity
                                        .FollowTargetMode
                                        .AGGRO_TRACK);
                    }
                    effect.withTarget(lifeform);
                    power.userStandEffects.addEffect(effect);
                }
            }
            level.addFreshEntity(new GerBeamEntity(
                    owner,
                    start,
                    end,
                    VISUAL_LIFETIME_TICKS));
        }

        private static Vec3 traceToBlockAllowingTranslucent(
                ServerLevel level,
                Entity shooter,
                Vec3 start,
                Vec3 maxEnd) {
            Vec3 delta = maxEnd.subtract(start);
            if (delta.lengthSqr() < 1.0E-6D) {
                return maxEnd;
            }
            Vec3 direction = delta.normalize();
            Vec3 currentStart = start;
            for (int index = 0; index < 64; index++) {
                BlockHitResult hit = level.clip(new ClipContext(
                        currentStart,
                        maxEnd,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        shooter));
                if (hit.getType() == HitResult.Type.MISS) {
                    return maxEnd;
                }
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()
                        && state.isSolidRender(level, pos)) {
                    return hit.getLocation();
                }
                currentStart = hit.getLocation().add(
                        direction.scale(0.01D));
            }
            return maxEnd;
        }

        private static double projectionT(
                Vec3 start,
                Vec3 delta,
                double lengthSqr,
                Vec3 point) {
            return lengthSqr < 1.0E-6D
                    ? 0.0D
                    : point.subtract(start).dot(delta) / lengthSqr;
        }

        private static Vec3 getEntityBeamEnd(
                LivingEntity entity,
                Vec3 start,
                Vec3 end) {
            Vec3 delta = end.subtract(start);
            double lengthSqr = delta.lengthSqr();
            if (lengthSqr < 1.0E-6D) {
                return entity.getBoundingBox().getCenter();
            }
            double projection = projectionT(
                    start,
                    delta,
                    lengthSqr,
                    entity.getBoundingBox().getCenter());
            return start.add(delta.scale(
                    Math.max(0.0D, Math.min(1.0D, projection))));
        }

        private static double distanceToAabbSqr(
                AABB box,
                Vec3 point) {
            double dx = point.x < box.minX
                    ? box.minX - point.x
                    : point.x > box.maxX
                            ? point.x - box.maxX
                            : 0.0D;
            double dy = point.y < box.minY
                    ? box.minY - point.y
                    : point.y > box.maxY
                            ? point.y - box.maxY
                            : 0.0D;
            double dz = point.z < box.minZ
                    ? box.minZ - point.z
                    : point.z > box.maxZ
                            ? point.z - box.maxZ
                            : 0.0D;
            return dx * dx + dy * dy + dz * dz;
        }

        private static Vec3 applyRandomRotation(
                Vec3 vector,
                RandomSource random) {
            double x = randomAngle(random);
            double y = randomAngle(random);
            double z = randomAngle(random);
            Vec3 rotated = rotateAroundX(vector, x);
            rotated = rotateAroundY(rotated, y);
            rotated = rotateAroundZ(rotated, z);
            return rotated.lengthSqr() < 1.0E-6D
                    ? vector
                    : rotated.normalize();
        }

        private static double randomAngle(RandomSource random) {
            return Math.toRadians(
                    -MULTI_SPREAD_DEGREES
                            + random.nextDouble()
                                    * MULTI_SPREAD_DEGREES
                                    * 2.0D);
        }

        private static Vec3 rotateAroundX(
                Vec3 vector,
                double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Vec3(
                    vector.x,
                    vector.y * cos - vector.z * sin,
                    vector.y * sin + vector.z * cos);
        }

        private static Vec3 rotateAroundY(
                Vec3 vector,
                double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Vec3(
                    vector.x * cos - vector.z * sin,
                    vector.y,
                    vector.x * sin + vector.z * cos);
        }

        private static Vec3 rotateAroundZ(
                Vec3 vector,
                double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Vec3(
                    vector.x * cos - vector.y * sin,
                    vector.x * sin + vector.y * cos,
                    vector.z);
        }
    }

    private record BeamHit(
            LivingEntity target, double distanceSqr) {}
}
