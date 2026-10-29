package rotp.addons.shadowtheworld.ability;

import java.util.EnumSet;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import rotp.addons.shadowtheworld.ShadowTheWorldGameEvents;
import rotp.addons.shadowtheworld.state.ShadowEvolutionState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShadowEvolutionAbility
        extends StandEntityAbility {
    public static final int EVOLUTION_TICKS = 1160;
    public static final int FIRE_PROTECTION_TICKS = 1800;
    public static final int CHARGE_SOUND_INTERVAL = 10;

    private final Mode mode;

    public ShadowEvolutionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(abilityType, abilityId, EvolutionAction::new);
        this.mode = mode;
        if (mode == Mode.EVOLVE) {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE, 60);
        }
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        resolveLevelToUnlock(mode.resolveLevel);
        cooldown(mode.cooldownTicks);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)) {
            return ConditionCheck.NEGATIVE;
        }
        ShadowEvolutionState state =
                ShadowTheWorldAddon.state(player);
        switch (mode) {
            case EVOLVE -> {
                if (state.isEvolving()) {
                    return ConditionCheck.createNegative(
                            "evolution_in_progress");
                }
                long dayTime = player.level().getDayTime() % 24000L;
                if (dayTime < 13000L || dayTime > 23000L) {
                    return ConditionCheck.createNegative(
                            "evolution_night_required");
                }
                ItemStack offhand = player.getOffhandItem();
                if (!offhand.is(Items.NETHER_STAR)
                        && !offhand.is(
                                ShadowTheWorldAddon
                                        .DIO_NOTE_PAGE.get())) {
                    return ConditionCheck.createNegative(
                            "evolution_need_item");
                }
            }
            case STABLE, UNSTABLE -> {
                if (!state.stateConversionUnlocked()) {
                    return ConditionCheck.createNegative(
                            "state_conversion_locked");
                }
            }
            case QUICK_EVOLVE -> {
                if (!state.evolutionUnlocked()) {
                    return ConditionCheck.createNegative(
                            "quick_evolution_locked");
                }
            }
        }
        // 1.16 had no stamina gate here; short stamina drains to 0
        return ConditionCheck.POSITIVE;
    }

    public static boolean completeEvolution(
            ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power == null
                || !power.hasPower()
                || power.getPowerType()
                        != ShadowTheWorldAddon
                                .SHADOW_THE_WORLD.get()) {
            return false;
        }
        StandInstance current =
                power.getStandInstance().orElse(null);
        if (current == null) {
            return false;
        }
        StandInstance replacement =
                new StandInstance(ModStands.THE_WORLD.get());
        EnumSet<StandPart> parts = current.getAllParts();
        for (StandPart part : StandPart.values()) {
            if (!parts.contains(part)) {
                replacement.removePart(part);
            }
        }
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        current.getStandId(),
                        replacement);
        if (!result.applied()) {
            player.displayClientMessage(
                    Component.translatable(
                            "action.rotp_shadowtheworld"
                                    + ".evolution.failed"),
                    true);
            return false;
        }
        power.skipProgression();
        // 1.16 toggleSummon if not active: The World always comes out,
        // then the HUD switches to it
        if (!power.isSummoned()) {
            ModStands.THE_WORLD.get().summon(player, power);
        }
        ShadowTheWorldGameEvents.scheduleStandPanel(player);
        ShadowAbilitySupport.burst(
                player.serverLevel(), player);
        player.displayClientMessage(
                Component.translatable(
                        "action.rotp_shadowtheworld"
                                + ".quick_evolution.success"),
                true);
        return true;
    }

    // 1.16 tickHeldAction ran checkConditions on every held tick: the night, the
    // offhand item and a ritual under way end the 60-tick charge (EVOLVE; the
    // other modes are clicks)
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    public enum Mode {
        EVOLVE(4, 0.0F, 0),
        STABLE(3, 200.0F, 300),
        UNSTABLE(3, 200.0F, 300),
        QUICK_EVOLVE(4, 500.0F, 600);

        private final int resolveLevel;
        private final float staminaCost;
        private final int cooldownTicks;

        Mode(
                int resolveLevel,
                float staminaCost,
                int cooldownTicks) {
            this.resolveLevel = resolveLevel;
            this.staminaCost = staminaCost;
            this.cooldownTicks = cooldownTicks;
        }
    }

    private static final class EvolutionAction
            extends EntityActionInstance {
        private EvolutionAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
            }
        }

        // 1.16 holdTick: a sound every 10 held ticks, glass for the
        // Nether Star stage, bat wings for the note page stage
        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE
                    || !(level() instanceof ServerLevel level)
                    || !(getPowerUser() instanceof Player player)
                    || !(ability
                            instanceof ShadowEvolutionAbility
                                    evolution)
                    || evolution.mode != Mode.EVOLVE
                    || (int) getPhaseTick()
                            % CHARGE_SOUND_INTERVAL != 0
                    || ShadowTheWorldAddon.state(player)
                            .isEvolving()) {
                return;
            }
            boolean pageStage = player.getOffhandItem().is(
                    ShadowTheWorldAddon.DIO_NOTE_PAGE.get());
            if (pageStage) {
                level.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.BAT_LOOP,
                        player.getSoundSource(),
                        2.0F,
                        0.8F + level.random.nextFloat() * 0.4F);
            }
            else {
                level.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.GLASS_BREAK,
                        player.getSoundSource(),
                        1.0F,
                        0.5F + level.random.nextFloat() * 0.5F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                            instanceof ShadowEvolutionAbility
                                    evolution)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null) {
                forceStop();
                return;
            }
            // 1.16 StandEntityTask: consumeStamina unchecked
            if (evolution.mode.staminaCost > 0.0F) {
                power.consumeStamina(evolution.mode.staminaCost);
            }
            boolean performed = switch (evolution.mode) {
                case EVOLVE -> beginEvolution(level, player);
                case STABLE -> setStability(
                        level, player, true);
                case UNSTABLE -> setStability(
                        level, player, false);
                case QUICK_EVOLVE -> quickEvolve(level, player);
            };
            if (performed) {
                evolution.setCooldownOnUse(power);
            }
        }

        // 1.16 QuickEvolution.standPerform: the lightning ring and thunder
        // of the night evolution's end
        private static boolean quickEvolve(
                ServerLevel level, ServerPlayer player) {
            if (!completeEvolution(player)) {
                return false;
            }
            ShadowTheWorldGameEvents.spawnFinalLightningCircle(
                    level, player);
            return true;
        }

        private static boolean beginEvolution(
                ServerLevel level, ServerPlayer player) {
            ShadowEvolutionState state =
                    ShadowTheWorldAddon.state(player);
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(Items.NETHER_STAR)) {
                if (!player.getAbilities().instabuild) {
                    offhand.shrink(1);
                }
                ItemStack page = new ItemStack(
                        ShadowTheWorldAddon
                                .DIO_NOTE_PAGE.get());
                if (offhand.isEmpty()) {
                    player.setItemInHand(
                            net.minecraft.world
                                    .InteractionHand.OFF_HAND,
                            page);
                }
                else if (!player.getInventory().add(page)) {
                    player.drop(page, false);
                }
                // 1.16 field_188423_x: Glowing, not Absorption
                player.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING,
                        200,
                        0));
                state.setUsedNetherStar(true);
                state.unlockStateConversion(player);
                ShadowAbilitySupport.burst(level, player);
                return true;
            }
            if (!offhand.is(
                    ShadowTheWorldAddon.DIO_NOTE_PAGE.get())) {
                return false;
            }
            // 1.16 kept the note page; only the Nether Star is used up
            // 1.16 field_76421_d: Slowness III for the ritual, not Speed
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    EVOLUTION_TICKS,
                    2,
                    false,
                    false,
                    false));
            player.addEffect(new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE,
                    FIRE_PROTECTION_TICKS,
                    0,
                    false,
                    false,
                    false));
            state.setFireProtectionTicks(
                    FIRE_PROTECTION_TICKS);
            state.beginEvolution(player, EVOLUTION_TICKS);
            player.clearFire();
            // 1.16 world.playSound(null, ...): the user hears it too
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ShadowTheWorldAddon.SDDIO_EVOLUTION.get(),
                    player.getSoundSource(),
                    1.0F,
                    1.0F);
            ShadowAbilitySupport.burst(level, player);
            // 1.16 page stage: lightning ring and thunder at the start too
            ShadowTheWorldGameEvents
                    .spawnFinalLightningCircle(level, player);
            return true;
        }

        private static boolean setStability(
                ServerLevel level,
                ServerPlayer player,
                boolean stable) {
            ShadowEvolutionState state =
                    ShadowTheWorldAddon.state(player);
            if (!state.stateConversionUnlocked()) {
                return false;
            }
            state.setUsedNetherStar(stable);
            // 1.16 StateConversion*: PLAYERS, heard by the user too
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ShadowTheWorldAddon
                            .SHADOW_WORLD_SUMMON.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    stable ? 2.0F : 0.5F);
            ShadowAbilitySupport.burst(level, player);
            player.displayClientMessage(
                    Component.translatable(
                            "action.rotp_shadowtheworld."
                                    + (stable
                                            ? "state_conversion_stable"
                                            : "state_conversion_unstable")
                                    + ".success"),
                    true);
            return true;
        }
    }
}
