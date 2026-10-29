package rotp.addons.stickyfingers.ability;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.network.ZipperClimbPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 Climb Zipper: one press snaps the user onto the zipper
 * wall; W/S then climb it until jump, leaving the zipper, or
 * backing off on the ground.
 */
public final class StickyFingersClimbZipperAbility
        extends StandEntityAbility {
    // Climbing players and the wall side they hold, per side.
    private static final Map<Entity, Direction> CLIMBING =
            Collections.synchronizedMap(new WeakHashMap<>());

    public StickyFingersClimbZipperAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ClimbZipperAction::new);
        partsRequired(StandPart.ARMS);
        cooldown(StickyFingersPhase1Spec.CLIMB_ZIPPER_COOLDOWN_TICKS);
        // A click, not a held move (1.16 had no hold or time cap).
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 1);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null || isHamonWallClimbing(user)
                || climbTarget(user) == null) {
            return ConditionCheck.createNegative(
                    "sticky_fingers_zipper_wall");
        }
        return ConditionCheck.POSITIVE;
    }

    private static boolean isHamonWallClimbing(LivingEntity user) {
        return PlayerPower.getPowerData(user, ModPlayerPowers.HAMON)
                .map(HamonData::isWallClimbing)
                .orElse(false);
    }

    // Aimed side face of a zipper within 1.16 reach, or null.
    private static BlockHitResult climbTarget(LivingEntity user) {
        BlockHitResult hit = zipperRaycast(user);
        if (hit.getType() != HitResult.Type.BLOCK
                || hit.getDirection().getAxis() == Direction.Axis.Y
                || !isZipper(user.level(), hit.getBlockPos())) {
            return null;
        }
        Vec3 toWall = towardWall(hit.getDirection());
        return collideTowardWall(user, toWall)
                .distanceToSqr(toWall) > 1.0E-7D ? hit : null;
    }

    private static BlockHitResult zipperRaycast(LivingEntity user) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getViewVector(1.0F).scale(
                StickyFingersPhase1Spec.ZIPPER_RANGE));
        return user.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                user));
    }

    private static Vec3 towardWall(Direction face) {
        return Vec3.atLowerCornerOf(face.getOpposite().getNormal())
                .scale(ZipperClimbRules.WALL_REACH);
    }

    private static Vec3 collideTowardWall(LivingEntity user, Vec3 toWall) {
        return Entity.collideBoundingBox(
                user, toWall, user.getBoundingBox(),
                user.level(), List.of());
    }

    private static boolean isZipper(Level level, BlockPos pos) {
        return level.getBlockState(pos)
                .is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
    }

    /** Wall side the entity is climbing, or null. */
    public static Direction climbWall(Entity entity) {
        return CLIMBING.get(entity);
    }

    public static boolean isOnZipperColumn(Entity entity, Direction wall) {
        Level level = entity.level();
        return ZipperClimbRules.isZipperColumn(
                entity.getBoundingBox(),
                wall.getStepX(),
                wall.getStepZ(),
                (x, y, z) -> isZipper(level, new BlockPos(x, y, z)));
    }

    private static void startClimbing(ServerPlayer player, Direction wall) {
        CLIMBING.put(player, wall);
        PacketDistributor.sendToPlayer(player,
                new ZipperClimbPayload(wall.get3DDataValue()));
    }

    /** Lets go on this side and tells the other side. */
    public static void stopClimbing(Player player) {
        if (CLIMBING.remove(player) == null) {
            return;
        }
        if (player.level().isClientSide()) {
            PacketDistributor.sendToServer(
                    new ZipperClimbPayload(ZipperClimbPayload.STOP));
        }
        else if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new ZipperClimbPayload(ZipperClimbPayload.STOP));
        }
    }

    /** Server state on the client, or a client let-go on the server. */
    public static void handleSync(Player player, int wallId) {
        if (player == null) {
            return;
        }
        if (player.level().isClientSide()) {
            Direction wall = wallId >= 0 && wallId < 6
                    ? Direction.from3DDataValue(wallId)
                    : null;
            if (wall != null && wall.getAxis().isHorizontal()) {
                CLIMBING.put(player, wall);
            }
            else {
                CLIMBING.remove(player);
            }
        }
        else if (wallId == ZipperClimbPayload.STOP) {
            CLIMBING.remove(player);
        }
    }

    @EventBusSubscriber(modid = StickyFingersAddon.MOD_ID)
    public static final class ServerTicks {
        private ServerTicks() {}

        // Server side: no fall damage, drop off when the zipper ends.
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide()) {
                return;
            }
            Direction wall = climbWall(player);
            if (wall == null) {
                return;
            }
            player.fallDistance = 0.0F;
            if (!player.isAlive() || player.isSpectator()
                    || !isOnZipperColumn(player, wall)) {
                stopClimbing(player);
            }
        }
    }

    public static final class ClimbZipperAction
            extends EntityActionInstance {
        public ClimbZipperAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || !StickyFingersAddon.isStickyFingersActive(user)) {
                return;
            }
            BlockHitResult hit = climbTarget(user);
            if (hit == null) {
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    StickyFingersPhase1Spec.CLIMB_ZIPPER_STAMINA,
                    false);
            // 1.16: touch the wall, then back off 0.15 body widths.
            Direction face = hit.getDirection();
            Vec3 moveTo = user.position()
                    .add(collideTowardWall(user, towardWall(face)))
                    .add(Vec3.atLowerCornerOf(face.getNormal()).scale(
                            ZipperClimbRules.wallGap(user.getBbWidth())));
            user.teleportTo(moveTo.x, moveTo.y, moveTo.z);
            user.fallDistance = 0.0F;
            if (user instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(
                        "jojo.message.wall_climb.hint_jump",
                        Component.keybind("key.jump")), true);
                startClimbing(player, face.getOpposite());
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }
    }
}
