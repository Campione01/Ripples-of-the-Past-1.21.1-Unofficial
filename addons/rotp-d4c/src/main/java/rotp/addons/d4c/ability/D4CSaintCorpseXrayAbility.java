package rotp.addons.d4c.ability;

import rotp.addons.d4c.D4CSaintCorpseXrayService;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Wheel entry for the Saint Corpse X-ray, which 1.16 bound to a private U key. Usable while the eyes or the
 * complete corpse is carried or worn; each use flips the X-ray on or off.
 */
public final class D4CSaintCorpseXrayAbility extends Ability {
    private static final int TOGGLE_COOLDOWN_TICKS = 10;

    public D4CSaintCorpseXrayAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
        cooldown(TOGGLE_COOLDOWN_TICKS);
    }

    // The donor key was no Action: it neither ended a held action nor was refused by one.
    @Override
    public boolean endsOtherHoldOnPress() {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (!(user instanceof Player player) || !D4CSaintCorpseXrayService.canUse(player)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "message.rotp_d4c.xray_need_eyes"));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            D4CSaintCorpseXrayService.toggle(player);
        }
    }
}
