package rotp.addons.stone_legacy.ability;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.core.init.ModBlocks;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.block.SlumberingPillarmanBlock;

import rotp.addons.stone_legacy.StoneLegacyAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.core.particles.ParticleTypes;

public final class PillarmanPetrifyAbility
        extends EntityActionAbility {
    public static final int CHARGE_TICKS = 40;
    private static final Map<UUID, Long> PENDING_CLEAR =
            new ConcurrentHashMap<>();

    public PillarmanPetrifyAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                PetrifyInstance::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setIgnoresPerformerStun();
    }

    @Override
    public String getResourceNamespace() {
        return StoneLegacyAddon.MOD_ID;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (!(context instanceof PlayerPower playerPower)
                || playerPower.getPowerType()
                        != ModPlayerPowers.PILLAR_MAN.get()
                || !hasStoneFormation(user)) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction reran PillarmanPetrify.checkSpecificConditions
    // (the stone formation behind the user) every held tick; turning away
    // from it ended the charge silently.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    @Override
    public void appendWarnings(
            List<Component> warnings,
            Power<?> context,
            Player clientPlayerUser) {
        warnings.add(Component.translatable(
                "stone_legacy.pillarman_petrify.warning"));
    }

    public static void tickPendingClear(ServerPlayer player) {
        Long clearAt = PENDING_CLEAR.get(player.getUUID());
        if (clearAt == null
                || player.level().getGameTime() < clearAt) {
            return;
        }
        PENDING_CLEAR.remove(player.getUUID());
        PlayerPower power =
                PlayerPower.getOptional(player).orElse(null);
        if (power != null
                && power.getPowerType()
                        == ModPlayerPowers.PILLAR_MAN.get()) {
            power.setPowerType(null);
        }
        StoneLegacyAddon.state(player)
                .setHamonOrigin(player, false);
    }

    public static boolean hasStoneFormation(
            LivingEntity living) {
        if (living == null) {
            return false;
        }
        Level level = living.level();
        Direction facing = living.getDirection();
        BlockPos bottomLeft = living.blockPosition()
                .relative(facing.getOpposite())
                .relative(facing.getCounterClockWise());
        int stone = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 2; x++) {
                BlockPos pos = bottomLeft.above(y)
                        .relative(facing.getClockWise(), x);
                if (isStone(level.getBlockState(pos))) {
                    stone++;
                }
            }
        }
        return stone == 6;
    }

    private static void replaceStoneFormation(
            ServerLevel level, LivingEntity living) {
        if (!hasStoneFormation(living)) {
            return;
        }
        Direction facing = living.getDirection();
        Direction statueFacing = facing.getOpposite();
        BlockPos bottomLeft = living.blockPosition()
                .relative(statueFacing)
                .relative(facing.getCounterClockWise());
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 2; x++) {
                BlockPos pos = bottomLeft.above(y)
                        .relative(facing.getClockWise(), x);
                if (!isStone(level.getBlockState(pos))) {
                    continue;
                }
                int part = (2 - y) * 2 + (1 - x);
                BlockState state = ModBlocks
                        .SLUMBERING_PILLARMAN.get()
                        .defaultBlockState()
                        .setValue(
                                HorizontalDirectionalBlock.FACING,
                                statueFacing)
                        .setValue(
                                SlumberingPillarmanBlock.PART,
                                part);
                level.setBlock(pos, state, 3);
            }
        }
    }

    private static boolean isStone(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER);
    }

    public static final class PetrifyInstance
            extends EntityActionInstance {
        public PetrifyInstance(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 0.0F;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                            instanceof PillarmanPetrifyAbility petrify)) {
                return;
            }
            // 1.16 stopHeldAction(true) checked once more as the charge
            // ended: no formation, no petrification.
            Power<?> context = petrify.getUserPower(player);
            if (context == null
                    || !petrify.checkHeldActionConditions(this, context)
                            .isPositive()) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            replaceStoneFormation(level, player);
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    player.getX(),
                    player.getY()
                            + player.getBbHeight() / 2.0D,
                    player.getZ(),
                    40,
                    0.4D,
                    0.8D,
                    0.4D,
                    0.02D);
            DimensionTransition transition =
                    player.findRespawnPositionAndUseSpawnBlock(
                            false,
                            DimensionTransition.DO_NOTHING);
            player.changeDimension(transition);
            PENDING_CLEAR.put(
                    player.getUUID(),
                    player.level().getGameTime() + 20L);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
            }
        }
    }
}
