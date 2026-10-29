package rotp.addons.scarymonsters.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.client.ClawSlashRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 PunchLeftAction extended StandEntityLightAttack: the core light punch
 * (timing from the Stand's speed, damage from its strength, block hits, the
 * finisher meter), silent on impact, with its own staminaCost.
 */
public final class ScaryMonstersPunchAbility extends StandEntityPunchAbility {
    // 1.16 staminaCost(7.5f)
    static final float STAMINA = 7.5F;

    public ScaryMonstersPunchAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, ScaryPunch::new);
        // 1.16 punchSound(() -> null)
        punchImpactSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !ScaryMonstersAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        // 1.16 had no stamina gate: short stamina still jabs and drops to 0.
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("punch", false);
    }

    public static final class ScaryPunch extends StandEntityPunch {
        public ScaryPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            StandPower power = user != null && !level().isClientSide()
                    ? StandPower.get(user) : null;
            float before = power != null ? power.getStamina() : 0.0F;
            super.actionPerformStart();
            // The core light punch takes a flat 10; 1.16 took this 7.5.
            if (power != null && !power.isStaminaInfinite()) {
                power.setStamina(before);
                power.consumeStamina(STAMINA);
            }
            // 1.16 PunchLeftAction client side: an off-hand swing and a
            // claw slash 2 blocks ahead of the user's eyes
            if (level().isClientSide() && user != null) {
                if (user instanceof Player player) {
                    player.swing(InteractionHand.OFF_HAND);
                }
                ClawSlashRenderer.spawnSlashAtEntity(user);
            }
        }
    }
}
