package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.util.functions.ItemUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GoldExperienceHealingItemAbility extends NoPoseStandEntityAbility {
    private static final int STAMINA_COST = 40;

    public GoldExperienceHealingItemAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, HealingItemAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 10);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user != null) {
            ConditionCheck materialCheck = GoldExperienceHealAbility.checkHealingMaterial(user);
            if (!materialCheck.isPositive()) {
                return materialCheck;
            }
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
    }

    public static class HealingItemAction extends EntityActionInstance {
        public HealingItemAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            StandPower standPower = StandPower.get(user);
            if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
                StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
                return;
            }
            if (!GoldExperienceHealAbility.spendHealingMaterial(user)) {
                return;
            }
            if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
                return;
            }

            ItemStack tissueItem = new ItemStack(ModItems.GE_BODY_TISSUE.get());
            GEBodyTissueItem.onCreated(standPower, tissueItem);
            ItemUtil.giveItemTo(user, tissueItem, false);
            StandUtil.broadcastSound((ServerLevel) level, user.position(),
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSoundEvents.GOLD_EXPERIENCE_LIFE_START.get()),
                    true, standPower, SoundSource.AMBIENT,
                    1.0F, 0.95F + user.getRandom().nextFloat() * 0.1F);
        }
    }
}
