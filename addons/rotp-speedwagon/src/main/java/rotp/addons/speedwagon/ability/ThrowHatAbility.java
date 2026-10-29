package rotp.addons.speedwagon.ability;

import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ThrowHatAbility
        extends SpeedwagonActionAbility {
    public ThrowHatAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                250.0F,
                0.0F,
                0,
                ThrowHatAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        return user != null && findHat(user) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("hat");
    }

    private static EquipmentSlot findHat(LivingEntity user) {
        if (user.getItemBySlot(EquipmentSlot.HEAD)
                .is(ModItems.BLADE_HAT.get())) {
            return EquipmentSlot.HEAD;
        }
        if (user.getMainHandItem()
                .is(ModItems.BLADE_HAT.get())) {
            return EquipmentSlot.MAINHAND;
        }
        if (user.getOffhandItem()
                .is(ModItems.BLADE_HAT.get())) {
            return EquipmentSlot.OFFHAND;
        }
        return null;
    }

    public static final class ThrowHatAction
            extends EntityActionInstance {
        public ThrowHatAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof ThrowHatAbility throwAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            EquipmentSlot slot =
                    user != null ? findHat(user) : null;
            if (user == null
                    || slot == null
                    || !throwAbility.consumeInitialEnergy(user)) {
                forceStop();
                syncPhaseChanges();
                return;
            }

            ItemStack stack = user.getItemBySlot(slot);
            BladeHatEntity hat =
                    new BladeHatEntity(level(), user, stack);
            hat.shootFromRotation(
                    user,
                    user.getXRot(),
                    user.getYRot(),
                    0.0F,
                    0.75F,
                    0.5F);
            level().addFreshEntity(hat);
            level().playSound(
                    null,
                    user,
                    ModSoundEvents.BLADE_HAT_THROW.get(),
                    SoundSource.AMBIENT,
                    1.0F,
                    1.0F
                            + (level().random.nextFloat() - 0.5F)
                                    * 0.15F);
            stack.shrink(1);
        }
    }
}
