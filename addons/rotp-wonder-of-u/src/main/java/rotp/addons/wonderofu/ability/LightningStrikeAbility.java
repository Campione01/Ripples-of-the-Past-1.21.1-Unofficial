package rotp.addons.wonderofu.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public final class LightningStrikeAbility extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 30;
    public static final float STAMINA_COST = 50.0F;
    public static final int COOLDOWN_TICKS = 0;

    public LightningStrikeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, LightningStrikeAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
    }

    // 1.16 LightningStrike.standKeepsTarget: an entity
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    public static final class LightningStrikeAction
            extends EntityActionInstance {
        public LightningStrikeAction(EntityActionType ability) {
            super(ability);
        }

        // the Stand's aim as the press came (the user's when the Stand's met nothing); a block without a collision
        // shape gives way to what the Stand's ray meets (1.16 StandEntityAction.overrideVanillaMouseTarget)
        @Override
        protected ActionTarget pressedTaskTarget() {
            var aim = LivingComponentAction.getAim(getPerformer());
            ActionTarget pressed = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
            LivingEntity user = getPowerUser();
            return user != null && ability instanceof StandEntityAbility strike
                    ? strike.overridePressedTarget(user, pressed)
                    : pressed;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer()
                            instanceof WonderOfUStandEntity stand)
                    || !stand.isAlive()
                    || stand.isRemoved()) {
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (power == null
                    || power.getSummonedStandEntity() != stand) {
                return;
            }
            strike(serverLevel, stand, user, ability,
                    getTaskTarget(serverLevel));
            // 1.16 StandEntityTask: the cost is paid after the perform; a
            // short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        private static void strike(
                ServerLevel serverLevel,
                WonderOfUStandEntity stand,
                LivingEntity user,
                EntityActionType skill,
                ActionTarget kept) {
            // 1.16 aimWithStandOrUser(100, task.getTarget())
            Vec3 target = CalamityRangedTargeting
                    .aimWithStandOrUser(stand, user, skill, kept)
                    .location();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(
                    serverLevel);
            if (bolt == null) {
                return;
            }
            bolt.moveTo(target.x, target.y, target.z);
            if (user instanceof ServerPlayer serverPlayer) {
                bolt.setCause(serverPlayer);
            }
            if (serverLevel.addFreshEntity(bolt)) {
                StandUtil.playStandEntitySound(
                        stand,
                        WonderOfUAddon.CALAMITY4.get(),
                        1.0F,
                        1.0F);
            }
        }
    }
}
