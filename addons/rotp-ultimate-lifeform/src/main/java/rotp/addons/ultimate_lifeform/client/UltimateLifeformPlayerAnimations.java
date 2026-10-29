package rotp.addons.ultimate_lifeform.client;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.core.api.client.animation.AddonPlayerAnimations;
import rotp.core.api.client.animation.AddonPlayerAnimations.PlayerAnimationState;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformPlayerClips;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UltimateLifeformPlayerAnimations {
    private static final ResourceLocation ANIMATION_SET =
            UltimateLifeformAddon.id("ulf_wall_climb");
    private static final Map<Player, Playback> PLAYBACK =
            new WeakHashMap<>();
    private static final ResourceLocation CORE_PILLAR_MAN_SET =
            ResourceLocation.fromNamespaceAndPath(
                    UltimateLifeformPlayerClips.CORE_NAMESPACE,
                    UltimateLifeformPlayerClips.PILLAR_MAN);
    private static final Map<Player, StoneFormPose> STONE_FORM =
            new WeakHashMap<>();
    private static final double MOVEMENT_EPSILON = 1.0E-4D;

    public static void register() {
        AddonPlayerAnimations.register(
                UltimateLifeformAddon.id("wall_climb"),
                100,
                UltimateLifeformPlayerAnimations::resolve);
    }

    @Nullable
    static PlayerAnimationState resolve(
            Player player, float partialTick) {
        UltimateLifeformData data = PlayerPower.getPowerData(
                        player,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        if (data == null
                || !data.isWallClimbing()) {
            PLAYBACK.remove(player);
            return stoneFormPose(player, data, partialTick);
        }
        STONE_FORM.remove(player);

        Vec3 motion = player.getDeltaMovement();
        Vec3 localMotion = motion.yRot(
                player.getYRot() * Mth.DEG_TO_RAD);
        Direction direction = direction(
                motion.y, localMotion.x);
        float speed = Mth.clamp(
                (float) (Math.max(
                                Math.abs(motion.y),
                                Math.abs(localMotion.x))
                        / 0.06D),
                0.25F,
                2.5F);
        float worldTime = player.tickCount + partialTick;
        Playback playback = PLAYBACK.computeIfAbsent(
                player, ignored -> new Playback());
        UltimateLifeformClientAcceptance.recordPlayerVisual(
                player, ClientReceipt.WALL_CLIMB_ANIMATION);
        return new PlayerAnimationState(
                ANIMATION_SET,
                direction.animation,
                playback.animationTime(
                        direction, worldTime, speed));
    }

    // 1.16 held a random core stone form pose while stone form was on
    @Nullable
    private static PlayerAnimationState stoneFormPose(
            Player player,
            @Nullable UltimateLifeformData data,
            float partialTick) {
        if (data == null
                || !data.isStoneForm()
                || player.isPassenger()) {
            STONE_FORM.remove(player);
            return null;
        }
        float worldTime = player.tickCount + partialTick;
        StoneFormPose pose = STONE_FORM.computeIfAbsent(
                player, ignored -> new StoneFormPose(
                        player.getRandom().nextInt(
                                UltimateLifeformPlayerClips
                                        .STONE_FORM_POSES.length),
                        worldTime));
        return new PlayerAnimationState(
                CORE_PILLAR_MAN_SET,
                UltimateLifeformPlayerClips.STONE_FORM_POSES[pose.variant()],
                Math.max(worldTime - pose.startTime(), 0.0F));
    }

    private record StoneFormPose(int variant, float startTime) {}

    private static Direction direction(
            double vertical, double left) {
        if (Math.abs(vertical) >= Math.abs(left)
                && Math.abs(vertical) > MOVEMENT_EPSILON) {
            return vertical < 0.0D
                    ? Direction.DOWN
                    : Direction.UP;
        }
        if (Math.abs(left) > MOVEMENT_EPSILON) {
            return left < 0.0D
                    ? Direction.RIGHT
                    : Direction.LEFT;
        }
        return Direction.UP;
    }

    private enum Direction {
        UP("wall_climb_up"),
        DOWN("wall_climb_down"),
        LEFT("wall_climb_left"),
        RIGHT("wall_climb_right");

        private final String animation;

        Direction(String animation) {
            this.animation = animation;
        }
    }

    private static final class Playback {
        private Direction direction;
        private float previousWorldTime = Float.NaN;
        private float animationTime;

        private float animationTime(
                Direction current,
                float worldTime,
                float speed) {
            if (direction != current
                    || Float.isNaN(previousWorldTime)
                    || worldTime < previousWorldTime) {
                direction = current;
                animationTime = 0.0F;
            }
            else {
                animationTime +=
                        (worldTime - previousWorldTime)
                                * speed;
            }
            previousWorldTime = worldTime;
            return Math.max(animationTime, 0.0F);
        }
    }

    private UltimateLifeformPlayerAnimations() {}
}
