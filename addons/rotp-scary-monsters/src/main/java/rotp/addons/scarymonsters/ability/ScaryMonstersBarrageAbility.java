package rotp.addons.scarymonsters.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.client.ClawSlashRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 BarrageAction extended StandEntityMeleeBarrage: the core barrage (hits
 * a tick from the Stand's speed, damage from its strength and precision, block
 * hits, the finisher meter, the hit and heavy-attack stops), with no hit
 * sound, its own swing sound and voice, and its own staminaCostTick.
 */
public final class ScaryMonstersBarrageAbility extends StandEntityBarrageAbility {
    // 1.16 staminaCostTick(3.0f)
    static final float STAMINA_TICK = 3.0F;
    // what the core barrage takes every tick of it
    private static final float CORE_STAMINA_TICK = 4.0F;

    public ScaryMonstersBarrageAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, ScaryBarrage::new);
        // 1.16 barrageHitSound(() -> null), standSound(BARRAGE_VOICE_RANDOM)
        barrageHitSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
        barrageCrySound(ScaryMonstersAddon.BARRAGE_VOICE);
        // the one-argument standSound also cried with the arms only
        barrageCryInArmsOnly();
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !ScaryMonstersAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        // 1.16 had no stamina gate: short stamina still barrages and drops to 0.
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("barrage", false);
    }

    public static final class ScaryBarrage extends StandEntityBarrage {
        public ScaryBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            boolean performing = getPhase() == ActionPhase.PERFORM;
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            float before = power != null ? power.getStamina() : 0.0F;
            super.actionTick();
            // The core barrage took min(4, stamina) this tick (a directional
            // tick it could not pay ends in recovery); 1.16 took min(3,
            // stamina), so the difference goes back and short stamina ends at 0.
            if (performing && getPhase() == ActionPhase.PERFORM && power != null) {
                float refund = Math.min(CORE_STAMINA_TICK, before)
                        - Math.min(STAMINA_TICK, before);
                if (refund > 0.0F) {
                    power.consumeStamina(-refund, true);
                }
            }
            // 1.16 BarrageAction.standTickPerform client side: hands swing
            // in turn every 2 ticks, a claw slash every tick
            if (performing && level().isClientSide()
                    && getPowerUser() instanceof Player player) {
                if (player.tickCount % 2 == 0) {
                    player.swinging = false;
                    player.swing(player.tickCount / 2 % 2 == 0
                            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                }
                ClawSlashRenderer.spawnSlashAtEntity(player);
            }
        }

        // 1.16 barrageSwingSound(InitSounds.BARRAGE_SWING)
        @Override
        protected SoundEvent getBarrageSwingSound() {
            return ScaryMonstersAddon.BARRAGE_SWING.get();
        }
    }
}
