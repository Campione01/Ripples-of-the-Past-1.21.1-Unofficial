package rotp.addons.shadowtheworld.ability;

import java.util.Comparator;

import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import rotp.addons.shadowtheworld.state.ShadowKnifeReleaseTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShadowKnivesAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier KNIFE_THROW_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "knife_throw", false);
    private static final ActionAnimIdentifier CIRCLE_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "knifethhh", false);

    private final Pattern pattern;

    public ShadowKnivesAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Pattern pattern) {
        super(abilityType, abilityId, KnifeAction::new);
        this.pattern = pattern;
        // 1.16 multiple knives was the SHIFT variation of single knife
        if (pattern == Pattern.MULTIPLE) {
            setPlaysVoiceLineOnSneak();
        }
        // 1.16 standWindupDuration: 10 for circle knives, 5 for the throws
        setDefaultPhaseLength(
                ActionPhase.WINDUP, pattern == Pattern.CIRCLE ? 10 : 5);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(pattern.cooldownTicks, pattern.additionalCooldownTicks);
        resolveLevelToUnlock(pattern.resolveLevel);
        // 1.16 InitStands partsRequired: the circle needs the body and arms,
        // the throws the arms
        if (pattern == Pattern.CIRCLE) {
            partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
        } else {
            partsRequired(StandPart.ARMS);
        }
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return pattern == Pattern.CIRCLE
                ? CIRCLE_ANIM
                : KNIFE_THROW_ANIM;
    }

    // Cooldowns are 1.16's cooldown(10, 25, 0), (90, 180, 0) and (200, 600, 0):
    // Resolve drops the second part.
    public enum Pattern {
        SINGLE(3, 5.0F, 0.2F, 3, 90.0F, 10, 25, 0),
        MULTIPLE(9, 3.0F, 0.6F, 4, 180.0F, 90, 180, 2),
        CIRCLE(60, 1.5F, 0.5F, 0, 600.0F, 200, 600, 4);

        private final int count;
        private final float velocity;
        private final float inaccuracy;
        private final int timeStopFlightTicks;
        private final float stamina;
        private final int cooldownTicks;
        private final int additionalCooldownTicks;
        private final int resolveLevel;

        Pattern(
                int count,
                float velocity,
                float inaccuracy,
                int timeStopFlightTicks,
                float stamina,
                int cooldownTicks,
                int additionalCooldownTicks,
                int resolveLevel) {
            this.count = count;
            this.velocity = velocity;
            this.inaccuracy = inaccuracy;
            this.timeStopFlightTicks = timeStopFlightTicks;
            this.stamina = stamina;
            this.cooldownTicks = cooldownTicks;
            this.additionalCooldownTicks = additionalCooldownTicks;
            this.resolveLevel = resolveLevel;
        }
    }

    private static final class KnifeAction
            extends EntityActionInstance {
        private boolean standVoicePlayed;

        private KnifeAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFront(): half a block ahead at the default
        // height, full mode only, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            playStandVoiceOnce();
        }

        // an auto-summoned Stand joins the level after the action starts
        @Override
        public void actionTick() {
            super.actionTick();
            playStandVoiceOnce();
        }

        // 1.16 standSound(WINDUP, ...): the Stand voices the throw once as
        // the windup starts; only players who can hear Stands hear it
        private void playStandVoiceOnce() {
            if (standVoicePlayed
                    || level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)
                    || !stand.isAddedToLevel()
                    || !(ability
                            instanceof ShadowKnivesAbility knives)) {
                return;
            }
            standVoicePlayed = true;
            StandUtil.playStandEntitySound(
                    stand,
                    knives.pattern == Pattern.SINGLE
                            ? ShadowTheWorldAddon
                                    .SDDIO_KNIFE_THROW_VOICE
                                    .get()
                            : ShadowTheWorldAddon
                                    .SDDIO_KNIVES_THROW_VOICE
                                    .get(),
                    1.0F,
                    1.0F);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(ability
                            instanceof ShadowKnivesAbility knives)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                forceStop();
                return;
            }
            if (knives.pattern == Pattern.CIRCLE) {
                spawnCircle(level, user, knives.pattern);
            } else {
                spawnVolley(level, user, knives.pattern);
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    (Ability) knives,
                    power,
                    knives.pattern.stamina,
                    false);
            // 1.16: the user shouts the stand_user line (sneak rule);
            // the Stand voice played at windup start
            sayShoutOf(
                    ability,
                    user,
                    knives.pattern == Pattern.SINGLE
                            ? ShadowTheWorldAddon
                                    .SHADOW_WORLD_KNIFE_THROW
                            : ShadowTheWorldAddon
                                    .SHADOW_WORLD_KNIVES_THROW);
            knives.setCooldownOnUse(power);
            ShadowAbilitySupport.unsummonIfUnstable(user);
        }

        private static void spawnVolley(
                ServerLevel level,
                LivingEntity user,
                Pattern pattern) {
            for (int index = 0;
                    index < pattern.count;
                    index++) {
                KnifeEntity knife = new KnifeEntity(
                        level,
                        user,
                        new ItemStack(ModItems.KNIFE.get()));
                knife.pickup = AbstractArrow.Pickup.DISALLOWED;
                knife.setTimeStopFlightTicks(
                        pattern.timeStopFlightTicks);
                float yawOffset = pattern == Pattern.SINGLE
                        ? (index - 1) * 1.5F
                        : (index % 3 - 1) * 2.0F;
                float pitchOffset = pattern == Pattern.SINGLE
                        ? 0.0F
                        : (index / 3 - 1) * 3.0F;
                knife.shootFromRotation(
                        user,
                        user.getXRot() + pitchOffset,
                        user.getYRot() + yawOffset,
                        0.0F,
                        pattern.velocity,
                        pattern.inaccuracy
                                + (pattern == Pattern.MULTIPLE
                                        ? (index % 3) * 0.7F
                                        : 0.0F));
                level.addFreshEntity(knife);
            }
        }

        private static void spawnCircle(
                ServerLevel level,
                LivingEntity user,
                Pattern pattern) {
            LivingEntity target = findTarget(user);
            Vec3 center = target != null
                    ? ShadowAbilitySupport.targetCenter(target)
                    : user.position().add(
                            user.getLookAngle().scale(10.0D));
            for (int index = 0;
                    index < pattern.count;
                    index++) {
                double theta =
                        Math.PI * 2.0D * user.getRandom()
                                .nextDouble();
                double phi = Math.acos(
                        2.0D * user.getRandom()
                                .nextDouble() - 1.0D);
                Vec3 offset = new Vec3(
                        6.0D * Math.sin(phi)
                                * Math.cos(theta),
                        6.0D * Math.sin(phi)
                                * Math.sin(theta),
                        6.0D * Math.cos(phi));
                KnifeEntity knife = new KnifeEntity(
                        level,
                        user,
                        new ItemStack(ModItems.KNIFE.get()));
                knife.pickup = AbstractArrow.Pickup.DISALLOWED;
                knife.setPos(center.add(offset));
                knife.setNoGravity(true);
                knife.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(knife);
                ShadowKnifeReleaseTracker.schedule(
                        knife,
                        center,
                        5 + user.getRandom().nextInt(5),
                        200);
            }
        }

        private static LivingEntity findTarget(
                LivingEntity user) {
            Vec3 look = user.getLookAngle().normalize();
            Vec3 eye = user.getEyePosition();
            double threshold =
                    Math.cos(Math.toRadians(12.5D));
            return user.level().getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(eye, eye).inflate(15.0D),
                            target -> target != user
                                    && target.isAlive()
                                    && !target.isAlliedTo(user))
                    .stream()
                    .filter(target -> look.dot(
                                    ShadowAbilitySupport
                                            .targetCenter(target)
                                            .subtract(eye)
                                            .normalize())
                            >= threshold)
                    .min(Comparator.comparingDouble(
                            target -> target.distanceToSqr(user)))
                    .orElse(null);
        }
    }
}
