package rotp.addons.shadowtheworld.effect;

import rotp.core.mechanics.ImmobilizeEffect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KnockdownEffect extends ImmobilizeEffect {
    public static final int COLOR = 0x5140A8;

    public KnockdownEffect() {
        super(COLOR);
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (!entity.level().isClientSide()) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    80,
                    1,
                    false,
                    false,
                    false));
        }
        if (entity instanceof Player player) {
            player.getAbilities().flying = false;
            // player movement is client-side: pull down on the owning client
            if (player.isLocalPlayer()) {
                pullDownToGround(player);
            }
            // updatePlayerPose would reset SLEEPING; players are drawn
            // lying by client.KnockdownRenderEvents (1.16 render hook)
            return true;
        }
        entity.setPose(Pose.SLEEPING);
        if (resetsDeltaMovement()) {
            // 1.16: keep a knocked-down mob sinking to the ground
            double yMotion = entity.getDeltaMovement().y;
            if (yMotion > -0.1D) {
                yMotion = Math.min(yMotion - 0.02D, -0.02D);
            }
            entity.setDeltaMovement(0.0D, yMotion, 0.0D);
        }
        return true;
    }

    // 1.16: an airborne player falls fast and snaps onto ground within
    // 1.5 blocks; a grounded one is held there
    private static void pullDownToGround(Player player) {
        if (player.isInWater() || player.isInLava()) {
            return;
        }
        double groundY = findGroundLevel(player);
        if (!player.onGround()) {
            double yMotion = Math.min(
                    player.getDeltaMovement().y - 0.15D, -0.8D);
            player.setDeltaMovement(0.0D, yMotion, 0.0D);
            if (!Double.isNaN(groundY)
                    && player.getY() - groundY < 1.5D
                    && canStandAt(player, groundY)) {
                player.setPos(player.getX(), groundY, player.getZ());
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
        else {
            player.setDeltaMovement(Vec3.ZERO);
            if (!Double.isNaN(groundY)
                    && Math.abs(player.getY() - groundY) > 0.1D
                    && canStandAt(player, groundY)) {
                player.setPos(player.getX(), groundY, player.getZ());
            }
        }
    }

    // never snap the hitbox into a block
    private static boolean canStandAt(Player player, double groundY) {
        return player.level().noCollision(player,
                player.getBoundingBox().move(
                        0.0D, groundY - player.getY(), 0.0D));
    }

    // top of the first solid block at or up to 10 blocks below the feet
    private static double findGroundLevel(LivingEntity entity) {
        Level level = entity.level();
        BlockPos.MutableBlockPos pos =
                entity.blockPosition().mutable();
        int startY = Mth.floor(entity.getY());
        int minY = Math.max(level.getMinBuildHeight(), startY - 10);
        for (int y = startY; y >= minY; y--) {
            pos.setY(y);
            VoxelShape shape = level.getBlockState(pos)
                    .getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    @Override
    public void onAdded(
            LivingEntity entity,
            MobEffectInstance instance,
            Entity source) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }

    @Override
    public void onRemoved(
            LivingEntity entity,
            MobEffectInstance instance) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        entity.setPose(Pose.STANDING);
        entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return super.isApplicable(entity)
                && (!(entity instanceof Mob mob)
                        || !mob.isNoAi());
    }
}
