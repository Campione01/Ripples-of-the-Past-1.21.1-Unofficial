package rotp.addons.weatherreport.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
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

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WeatherReportCloudShieldAbility
        extends StandEntityAbility {
    public static final int COOLDOWN_TICKS = 20;
    private static final ActionAnimIdentifier CLOUD_SHIELD_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    WeatherReportAddon.CLOUD_SHIELD_NAME, false);
    private Holder<SoundEvent> userShoutSound;

    public WeatherReportCloudShieldAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CloudShieldAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        // 1.16 WeatherReportCloudShield kept the builder's FULL: the whole
        // Stand comes out, so its offset shows
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        cooldown(COOLDOWN_TICKS);
    }

    public WeatherReportCloudShieldAbility userShoutSound(
            Holder<SoundEvent> sound) {
        userShoutSound = sound;
        return this;
    }

    // 1.16 held shield: the cooldown starts when the hold ends.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide()
                && standUser != null
                && userShoutSound != null) {
            sayShout(standUser, userShoutSound);
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return CLOUD_SHIELD_ANIM;
    }

    public static final class CloudShieldAction
            extends EntityActionInstance {
        private boolean cooldownSet;

        public CloudShieldAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 WeatherReportCloudShield.java:50 standOffsetFromUser(0.0,
            // -0.4), turned by the view yaw
            setStandOffset(
                    0.0D,
                    -0.4D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                CloudShieldProjectileControl.redirectHeldShield(
                        serverLevel, user, stand);
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);

            if (level().isClientSide()) {
                if (newPhase == ActionPhase.PERFORM
                        && performer instanceof StandEntity stand
                        && (stand.isVisibleForAll()
                                || ClientGlobals.canHearStands)) {
                    EntityStoppableSoundInstance sound =
                            new EntityStoppableSoundInstance(
                                    ClientsideSoundsHelper.withStandSkin(
                                            WeatherReportAddon
                                                    .WEATHER_REPORT_CLOUD_SHIELD_SOUND
                                                    .get(),
                                            stand),
                                    stand.getSoundSource(),
                                    1.0F,
                                    1.0F,
                                    true,
                                    stand,
                                    id,
                                    () -> isOver()
                                            || getPhase()
                                                    != ActionPhase.PERFORM);
                    ClientsideSoundsHelper.playNonVanillaClassSound(
                            sound);
                }
                return;
            }

            if (newPhase == ActionPhase.RECOVERY) {
                setReleaseCooldown();
            }
            if (newPhase == ActionPhase.RECOVERY
                    && performer instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WeatherReportAddon.WEATHER_REPORT_UNSUMMON,
                        1.0F,
                        1.0F);
            }
        }

        // A replaced, unsummoned or force-stopped hold ends here.
        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            setReleaseCooldown();
        }

        private void setReleaseCooldown() {
            if (cooldownSet || level().isClientSide()
                    || !(ability instanceof WeatherReportCloudShieldAbility
                            shield)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            if (standPower != null) {
                shield.setCooldownOnUse(standPower);
                cooldownSet = true;
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 WeatherReportCloudShield (StandEntityAction, no isChainable) refused its own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        // 1.16 WeatherReportCloudShield (builder.holdType()) was a holdType
        // Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }
}
