package rotp.addons.wonderofu.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;

public final class CalamityPassiveAbility extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final float STAMINA_COST = 125.0F;
    public static final int COOLDOWN_TICKS = 0;

    public CalamityPassiveAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CalamityPassiveAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
    }

    public int iconColor(Power<?> context, int color) {
        return CalamityModeIcon.color(
                context, color, WonderOfUStandEntity::isPassiveEnabled);
    }

    @Override
    public void renderAbilityIcon(
            Power<?> context,
            GuiGraphics guiGraphics,
            TextureAtlasSprite sprite,
            float x,
            float y,
            int color) {
        super.renderAbilityIcon(
                context, guiGraphics, sprite, x, y,
                iconColor(context, color));
    }

    public static final class CalamityPassiveAction
            extends EntityActionInstance {
        public CalamityPassiveAction(EntityActionType ability) {
            super(ability);
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
            if (level().isClientSide()
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

            boolean enabled = !stand.isPassiveEnabled();
            if (enabled) {
                stand.setActiveEnabled(false);
            }
            stand.setPassiveEnabled(enabled);
            if (enabled) {
                StandUtil.playStandEntitySound(
                        stand,
                        WonderOfUAddon.CALAMITY2.get(),
                        1.0F,
                        1.0F);
            }
            // 1.16 StandEntityTask: the cost is paid after the toggle; a
            // short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }
    }
}
