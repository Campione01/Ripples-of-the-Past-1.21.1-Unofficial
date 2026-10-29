package rotp.addons.d4c.ability;

import rotp.addons.d4c.D4CCorpseFinderService;
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
 * Wheel entry for the Corpse Finder ("Guardian Spirit"), which 1.16 bound to a private Ctrl+R key. Like the donor
 * key it only reaches the server while a Saint Corpse part or the complete corpse is carried; the finder service
 * keeps the donor's own six-second cooldown and status messages.
 */
public final class D4CCorpseFinderAbility extends Ability {
    public D4CCorpseFinderAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
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
        if (!(user instanceof Player player) || !D4CCorpseFinderService.hasAnyPart(player)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "message.rotp_d4c.corpse_finder.need_part_first"));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            D4CCorpseFinderService.requestGuidance(player);
        }
    }
}
