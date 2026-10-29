package rotp.addons.neo_stand.ability;

import java.util.EnumSet;

import rotp.core.api.stand.StandPowerTransitions;
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
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.NeoStandGameEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class DevolutionAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 2000.0F;

    public DevolutionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, DevolutionAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 40);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 30);
        cooldown(1800, 3600);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.MAIN_BODY);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)) {
            return ConditionCheck.NEGATIVE;
        }
        StandPower power = StandPower.get(player);
        StandInstance current = power != null
                ? power.getStandInstance().orElse(null)
                : null;
        if (current == null
                || !NeoStandAddon.THE_WORLD.equals(
                        current.getStandId())
                || StandInstance.fromExistingStandId(
                        NeoStandAddon.SHADOW_THE_WORLD) == null) {
            return ConditionCheck.createNegative(
                    "devolution_target_unavailable");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class DevolutionAction
            extends EntityActionInstance {
        public DevolutionAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPowerUser()
                            instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null) {
                forceStop();
                return;
            }

            StandInstance current =
                    power.getStandInstance().orElse(null);
            StandInstance replacement =
                    StandInstance.fromExistingStandId(
                            NeoStandAddon.SHADOW_THE_WORLD);
            if (current == null
                    || replacement == null
                    || !NeoStandAddon.THE_WORLD.equals(
                            current.getStandId())) {
                forceStop();
                return;
            }

            EnumSet<StandPart> presentParts =
                    current.getAllParts();
            for (StandPart part : StandPart.values()) {
                if (!presentParts.contains(part)) {
                    replacement.removePart(part);
                }
            }

            boolean wasSummoned = power.isSummoned();
            StandPowerTransitions.Result result =
                    StandPowerTransitions.replace(
                            power,
                            NeoStandAddon.THE_WORLD,
                            replacement);
            if (!result.applied()) {
                player.displayClientMessage(
                        Component.translatable(
                                "action.neojojostand"
                                        + ".the_world_devolution"
                                        + ".failed"),
                        true);
                forceStop();
                return;
            }

            power.skipProgression();
            if (wasSummoned) {
                NeoStandGameEvents.scheduleResummon(player);
            }
            emitTransition(serverLevel, player);
            player.displayClientMessage(
                    Component.translatable(
                            "action.neojojostand"
                                    + ".the_world_devolution"
                                    + ".success"),
                    true);
            // 1.16 StandEntityTask: the cost is paid after the swap; a short
            // bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        private static void emitTransition(
                ServerLevel level, ServerPlayer player) {
            Vec3 center = player.position().add(
                    0.0D, player.getBbHeight() * 0.5D, 0.0D);
            RandomSource random = level.getRandom();
            ParticleOptions[] particles = {
                    ParticleTypes.END_ROD,
                    ParticleTypes.PORTAL,
                    ParticleTypes.ENCHANT,
                    ParticleTypes.ELECTRIC_SPARK
            };
            for (int index = 0; index < 80; index++) {
                double azimuth =
                        random.nextDouble() * Math.PI * 2.0D;
                double polar = Math.acos(
                        1.0D - 2.0D * random.nextDouble());
                double x = 2.5D
                        * Math.sin(polar)
                        * Math.cos(azimuth);
                double y = 2.5D * Math.cos(polar);
                double z = 2.5D
                        * Math.sin(polar)
                        * Math.sin(azimuth);
                level.sendParticles(
                        particles[index % particles.length],
                        center.x + x,
                        center.y + y,
                        center.z + z,
                        0,
                        -x * 0.2D,
                        -y * 0.2D,
                        -z * 0.2D,
                        1.0D);
            }
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    center.x,
                    center.y,
                    center.z,
                    10,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.1D);
            for (int index = 0; index < 7; index++) {
                double angle = index * Math.PI * 2.0D / 6.0D;
                double radius = index == 6 ? 0.0D : 2.0D;
                LightningBolt lightning =
                        EntityType.LIGHTNING_BOLT.create(level);
                if (lightning != null) {
                    lightning.setVisualOnly(true);
                    lightning.moveTo(
                            player.getX()
                                    + Math.cos(angle) * radius,
                            player.getY(),
                            player.getZ()
                                    + Math.sin(angle) * radius);
                    level.addFreshEntity(lightning);
                }
            }
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER,
                    3.0F,
                    0.6F + random.nextFloat() * 0.2F);
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER,
                    2.0F,
                    0.4F + random.nextFloat() * 0.2F);
        }
    }
}
