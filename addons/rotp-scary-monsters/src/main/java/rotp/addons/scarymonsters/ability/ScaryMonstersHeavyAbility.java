package rotp.addons.scarymonsters.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.client.ClawSlashRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 HeavyPunchAction, FinisherPunchAction, TailSweepAction and
 * SlashAction all extended StandEntityHeavyAttack: the core heavy punch
 * (strength damage, knockback and impact, block explosion, a stopped
 * barrage) comes first, then each one's own area hit.
 */
public final class ScaryMonstersHeavyAbility extends StandEntityHeavyPunchAbility {
    public enum Kind {
        // 1.16 staminaCost, cooldown and resolveLevelToUnlock
        HEAVY(37.5F, 0, 0),
        FINISHER(37.5F, 0, 0),
        TAIL_SWEEP(150.0F, 60, 2),
        SLASH(225.0F, 60, 1);

        final float stamina;
        final int cooldown;
        final int resolve;

        Kind(float stamina, int cooldown, int resolve) {
            this.stamina = stamina;
            this.cooldown = cooldown;
            this.resolve = resolve;
        }
    }

    private final Kind kind;
    // 1.16 TailSweepAction rotSpeed
    static final float TAIL_SPIN_DEGREES = 3.0F;

    public ScaryMonstersHeavyAbility(
            AbilityType<?> type, AbilityId id, Kind kind) {
        super(type, id);
        this.kind = kind;
        createActionObj = ScaryHeavyPunch::new;
        standAutoSummonMode(AutoSummonMode.FULL);
        if (kind.cooldown > 0) cooldown(kind.cooldown);
        if (kind.resolve > 0) resolveLevelToUnlock(kind.resolve);
        // 1.16 punchSound(() -> null) and its stand voices: the heavy's at
        // the windup, the finisher's at the hit, none for the other two.
        Holder<SoundEvent> silent =
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
        heavyPunchImpactSound(silent);
        heavyPunchCrySound(kind == Kind.HEAVY
                ? ScaryMonstersAddon.HEAVY_VOICE : silent);
        if (kind == Kind.FINISHER) {
            heavyPunchPerformSound(ScaryMonstersAddon.FINISHER_VOICE);
            // 1.16 setFinisherVariation: with the finisher meter at half or
            // more, the heavy punch comes out as the finisher (the core
            // Stands' finishers work the same way; it has no wheel slot).
            initIsFinisher("example_stand_heavy_punch");
        }
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !ScaryMonstersAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        // 1.16 had no stamina gate: short stamina still fires and drops to 0.
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("heavy_punch", false);
    }

    public static final class ScaryHeavyPunch extends StandEntityHeavyPunch {
        public ScaryHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            StandPower power = !level().isClientSide()
                    ? StandPower.get(getPowerUser()) : null;
            float before = power != null ? power.getStamina() : 0.0F;
            super.actionPerformStart();
            if (level().isClientSide()) {
                if (ability instanceof ScaryMonstersHeavyAbility heavy
                        && performer instanceof StandEntity stand
                        && getPowerUser() != null) {
                    clientPerform(heavy.kind, getPowerUser(), stand);
                }
                return;
            }
            if (power == null
                    || !(ability instanceof ScaryMonstersHeavyAbility heavy)
                    || !(performer instanceof StandEntity stand)
                    || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            // The core heavy punch takes a flat 50; 1.16 took this
            // action's own staminaCost at the hit instead.
            if (!power.isStaminaInfinite()) {
                power.setStamina(before);
                power.consumeStamina(heavy.kind.stamina);
            }
            switch (heavy.kind) {
                case FINISHER -> ScaryMonstersService.slash(player, stand, true);
                case TAIL_SWEEP -> ScaryMonstersService.tailSweep(player, stand);
                case SLASH -> ScaryMonstersService.slash(player, stand, false);
                case HEAVY -> { }
            }
        }

        // 1.16 client halves of HeavyPunchAction, SlashAction,
        // FinisherPunchAction and TailSweepAction.standPerform; their
        // standTickPerform ran once, the heavy perform being 1 tick there.
        private void clientPerform(Kind kind, LivingEntity user, StandEntity stand) {
            switch (kind) {
                case HEAVY -> {
                    if (user instanceof Player player) {
                        player.swing(InteractionHand.MAIN_HAND);
                        ClawSlashRenderer.spawnSlashAtEntity(player);
                    }
                }
                case SLASH -> {
                    if (user instanceof Player player) {
                        player.swing(InteractionHand.MAIN_HAND);
                        // 1.16 SlashAction: a second slash at the user,
                        // heard by the local player only
                        player.playSound(ScaryMonstersAddon.SLASH_SOUND.get(), 1.0F, 1.0F);
                        ClawSlashRenderer.spawnSlashAtEntity(player);
                    }
                }
                case FINISHER -> ClawSlashRenderer.spawnSlashWhirlwind(user);
                case TAIL_SWEEP -> tailSpin(user, stand);
            }
        }

        // 1.16 TailSweepAction.standTickPerform on its one perform tick:
        // user and Stand turn 3 degrees, a ring of 8 crits round the Stand
        private void tailSpin(LivingEntity user, StandEntity stand) {
            if (user instanceof Player player) {
                player.setYRot(player.getYRot() + TAIL_SPIN_DEGREES);
                player.yRotO = player.getYRot() - TAIL_SPIN_DEGREES;
                player.yBodyRot += TAIL_SPIN_DEGREES;
                player.yBodyRotO = player.yBodyRot - TAIL_SPIN_DEGREES;
            }
            stand.setYRot(stand.getYRot() + TAIL_SPIN_DEGREES);
            stand.yRotO = stand.getYRot() - TAIL_SPIN_DEGREES;
            for (int i = 0; i < 8; ++i) {
                double angle = Math.toRadians(i * 45);
                double radius = 1.5D + Math.random() * 0.5D;
                level().addParticle(ParticleTypes.CRIT,
                        stand.getX() + Math.cos(angle) * radius,
                        stand.getY() + 0.5D + Math.random() * 0.5D,
                        stand.getZ() + Math.sin(angle) * radius,
                        (Math.random() - 0.5D) * 0.2D, Math.random() * 0.1D,
                        (Math.random() - 0.5D) * 0.2D);
            }
        }
    }
}
