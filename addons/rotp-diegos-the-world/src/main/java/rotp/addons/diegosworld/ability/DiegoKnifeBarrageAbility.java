package rotp.addons.diegosworld.ability;

import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class DiegoKnifeBarrageAbility
        extends StandEntityAbility {
    public static final int KNIFE_COST = 16;
    public static final float STAMINA_COST = 100.0F;
    public static final int PERFORM_TICKS = 32;
    private static final ActionAnimIdentifier BARRAGE_ANIM =
            ActionAnimIdentifier.getOrCreate("barrage", false);

    public DiegoKnifeBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, KnifeBarrageAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, 5);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
        cooldown(800);
        resolveLevelToUnlock(2);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)
                || countKnives(player) < KNIFE_COST) {
            return ConditionCheck.createNegative(
                    "not_enough_knives");
        }
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return BARRAGE_ANIM;
    }

    private static int countKnives(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.KNIFE.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean consumeKnives(Player player, int amount) {
        if (countKnives(player) < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(ModItems.KNIFE.get())) {
                continue;
            }
            int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    public static final class KnifeBarrageAction
            extends EntityActionInstance {
        private boolean armed;

        public KnifeBarrageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(getPowerUser() instanceof Player player)) {
                return;
            }
            if (consumeKnives(player, KNIFE_COST)) {
                armed = true;
                level.playSound(
                        null,
                        stand.blockPosition(),
                        DiegosTheWorldAddon
                                .THE_WORLD_KNIFE_BARRAGE_VOICE.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
            }
            // 1.16 StandEntityTask paid staminaCost(100) after standPerform;
            // a short bar just drops to 0
            StandAbilityStamina.consume(
                    ability, StandPower.get(player), STAMINA_COST, false);
            if (!armed) {
                startRecovery();
            }
        }

        @Override
        public void actionTick() {
            if (!armed
                    || getPhase() != ActionPhase.PERFORM
                    || (int) getPhaseTick() % 2 == 0
                    || !(level() instanceof ServerLevel level)
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            LivingEntity aiming =
                    stand.isManuallyControlled() ? stand : user;
            ItemStack pickup = new ItemStack(ModItems.KNIFE.get());
            KnifeEntity knife =
                    new KnifeEntity(level, stand, pickup);
            knife.setPos(
                    knife.getX(),
                    knife.getY() - 0.4D,
                    knife.getZ());
            Vec3 target = aiming.getEyePosition()
                    .add(aiming.getLookAngle().scale(60.0D));
            Vec3 direction = target.subtract(knife.position())
                    .normalize();
            knife.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    2.5F,
                    4.5F);
            knife.setBaseDamage(3.75D);
            knife.pickup = AbstractArrow.Pickup.ALLOWED;
            level.addFreshEntity(knife);
            level.playSound(
                    null,
                    stand.blockPosition(),
                    DiegosTheWorldAddon
                            .THE_WORLD_KNIVES_THROW.get(),
                    SoundSource.PLAYERS,
                    0.5F,
                    1.0F);
        }
    }
}
