package rotp.addons.wonderofu.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
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

public final class CalamityActiveAbility extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 30;
    public static final float STAMINA_COST = 200.0F;
    public static final float STAMINA_COST_TICK = 10.0F;
    // Donor GameplayEventHandler.onTick drained 2.0 with no phase filter,
    // so 1.16 ran it for both PlayerTickEvent phases: 4 per server tick.
    // The port drains once per tick (PlayerTickEvent.Post).
    public static final float STAMINA_DRAIN_PER_TICK = 4.0F;
    public static final int COOLDOWN_TICKS = 0;

    public CalamityActiveAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CalamityActiveAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
    }

    public int iconColor(Power<?> context, int color) {
        return CalamityModeIcon.color(
                context, color, WonderOfUStandEntity::isActiveEnabled);
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

    public static boolean drainStamina(StandPower power) {
        Ability activeAbility = power != null
                ? power.getAbility("calamity_active")
                : null;
        if (!StandAbilityStamina.consume(
                activeAbility,
                power,
                STAMINA_DRAIN_PER_TICK,
                true)) {
            return false;
        }
        return power.isStaminaInfinite()
                || power.getStamina() > STAMINA_DRAIN_PER_TICK;
    }

    public static final class CalamityActiveAction
            extends EntityActionInstance {
        public CalamityActiveAction(EntityActionType ability) {
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

            boolean enabled = !stand.isActiveEnabled();
            if (enabled) {
                stand.setPassiveEnabled(false);
            }
            stand.setActiveEnabled(enabled);
            if (enabled) {
                StandUtil.playStandEntitySound(
                        stand,
                        WonderOfUAddon.CALAMITY3.get(),
                        1.0F,
                        1.0F);
            }
            // 1.16 StandEntityTask: the cost is paid after the toggle; a
            // short bar just drops to 0 and the drain then ends the mode.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
            // 1.16 staminaCostTick(10): paid on the one perform tick, result ignored.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST_TICK, true);
        }
    }
}
