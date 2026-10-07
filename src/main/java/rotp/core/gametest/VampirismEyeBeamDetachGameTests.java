package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;

/**
 * 1.16 SpaceRipperStingyEyesEntity: while bound, the tip is the owner's eye origin plus the look vector times the
 * grown length, so it follows a moving owner. Once detached (tickCount > 20) the beam keeps the origin it had, flies
 * along origin-to-tip at its movement speed and carries that origin with it; the owner no longer moves it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismEyeBeamDetachGameTests {
    private static final double EPS = 1.0E-4D;
    private static final double DEG_TO_RAD = Math.PI / 180.0D;

    private VampirismEyeBeamDetachGameTests() {}

    @GameTest(template = "empty", batch = "vampire_eye_beam_detach")
    public static void boundBeamsFollowTheMovingOwnerThenFlyOffAsOneStraightSegment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(template);
        Vec3 start = new Vec3(chunk.getMinBlockX() + 2.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 8.5D);
        AABB space = new AABB(chunk.getMinBlockX(), start.y - 1, chunk.getMinBlockZ(),
                chunk.getMinBlockX() + 16, start.y + 34, chunk.getMinBlockZ() + 16);
        helper.assertTrue(space.maxY < level.getMaxBuildHeight() && level.getEntities((Entity) null, space).isEmpty()
                        && BlockPos.betweenClosedStream(space.deflate(0.5D)).allMatch(level::isEmptyBlock),
                "EYE-DETACH premise: scene is not empty air");
        float speed = 0.5F + level.getDifficulty().getId() * 0.25F;

        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EyeBeamOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        // Looking straight up keeps the 21 block beam inside the test chunk.
        owner.moveTo(start.x, start.y, start.z, 0.0F, -90.0F);
        level.addNewPlayer(owner);
        SpaceRipperStingyEyesEntity right = new SpaceRipperStingyEyesEntity(level, owner, true);
        SpaceRipperStingyEyesEntity left = new SpaceRipperStingyEyesEntity(level, owner, false);
        List<SpaceRipperStingyEyesEntity> beams = List.of(right, left);
        try {
            helper.assertTrue(level.addFreshEntity(right) && level.addFreshEntity(left)
                            && beams.stream().allMatch(beam -> beam.isBoundToOwner() && beam.getLength() == 0.0F),
                    "EYE-DETACH premise: beams were not added bound and unextended");

            // Bound: the owner strafes 0.2 blocks before each beam tick, as a player moves before the level ticks.
            for (int tick = 1; tick <= 21; tick++) {
                owner.moveTo(start.x + 0.2D * tick, start.y, start.z, 0.0F, -90.0F);
                tick(beams);
                if (tick > 20) {
                    break;
                }
                for (SpaceRipperStingyEyesEntity beam : beams) {
                    Vec3 expected = eyeOrigin(owner, beam == right).add(owner.getLookAngle().scale(tick * speed));
                    helper.assertTrue(beam.isAlive() && beam.isBoundToOwner()
                                    && Math.abs(beam.getLength() - tick * speed) < EPS
                                    && beam.position().distanceTo(expected) < EPS,
                            "Bound eye beam left its moving owner at tick " + tick + ": bound=" + beam.isBoundToOwner()
                                    + " length=" + beam.getLength() + " tip=" + beam.position() + " expected=" + expected);
                }
            }

            Vec3[] tip = new Vec3[2];
            Vec3[] origin = new Vec3[2];
            Vec3[] motion = new Vec3[2];
            for (int i = 0; i < 2; i++) {
                SpaceRipperStingyEyesEntity beam = beams.get(i);
                tip[i] = beam.position();
                origin[i] = beam.getOriginPoint(1.0F);
                motion[i] = beam.getDeltaMovement();
                Vec3 expectedOrigin = eyeOrigin(owner, beam == right);
                float length = 21 * speed;
                helper.assertTrue(beam.isAlive() && !beam.isBoundToOwner() && Math.abs(beam.getLength() - length) < EPS
                                && origin[i].distanceTo(expectedOrigin) < EPS
                                && tip[i].distanceTo(expectedOrigin.add(0.0D, length, 0.0D)) < EPS
                                && motion[i].distanceTo(new Vec3(0.0D, speed, 0.0D)) < EPS
                                && beam.ticksLifespan() == Mth.floor(length / speed * 20.0F) + 20,
                        "Eye beam did not detach on its 21st tick along origin-to-tip: bound=" + beam.isBoundToOwner()
                                + " length=" + beam.getLength() + " origin=" + origin[i] + " expectedOrigin=" + expectedOrigin
                                + " tip=" + tip[i] + " motion=" + motion[i] + " lifespan=" + beam.ticksLifespan());
            }

            // Detached: the owner walks on and turns; neither end of the beam follows.
            for (int tick = 1; tick <= 6; tick++) {
                owner.moveTo(start.x + 4.2D + 0.5D * tick, start.y, start.z + 0.5D * tick, 90.0F, 0.0F);
                tick(beams);
                for (int i = 0; i < 2; i++) {
                    SpaceRipperStingyEyesEntity beam = beams.get(i);
                    Vec3 expectedTip = tip[i].add(motion[i].scale(tick));
                    Vec3 expectedOrigin = origin[i].add(motion[i].scale(tick));
                    helper.assertTrue(beam.isAlive() && !beam.isBoundToOwner()
                                    && beam.position().distanceTo(expectedTip) < EPS
                                    && beam.getOriginPoint(1.0F).distanceTo(expectedOrigin) < EPS
                                    && beam.getDeltaMovement().distanceTo(motion[i]) < EPS,
                            "Detached eye beam did not keep flying as one straight segment at tick " + tick + ": tip="
                                    + beam.position() + " expectedTip=" + expectedTip + " origin=" + beam.getOriginPoint(1.0F)
                                    + " expectedOrigin=" + expectedOrigin + " motion=" + beam.getDeltaMovement());
                }
            }
            JojoMod.LOGGER.info("EYE-DETACH speed={} detachTips={}/{} detachOrigins={}/{} motion={}",
                    speed, tip[0], tip[1], origin[0], origin[1], motion[0]);
        }
        finally {
            beams.forEach(Entity::discard);
            owner.discard();
        }
        helper.succeed();
    }

    // ServerLevel.tickNonPassenger: the age is advanced, then the entity ticks.
    private static void tick(List<SpaceRipperStingyEyesEntity> beams) {
        for (SpaceRipperStingyEyesEntity beam : beams) {
            beam.tickCount++;
            beam.tick();
        }
    }

    // 1.16 DamagingEntity.getPos with the eye beam's offsets: 0.09375 to a side, 0.2 down, 0.2 up along the pitch.
    private static Vec3 eyeOrigin(FakePlayer owner, boolean rightEye) {
        Vec3 offset = new Vec3(rightEye ? -0.09375D : 0.09375D, -0.2D, 0.0D)
                .add(new Vec3(0.0D, 0.2D, 0.0D).xRot((float) (-owner.getXRot() * DEG_TO_RAD)))
                .yRot((float) (-owner.getYRot() * DEG_TO_RAD));
        return owner.getEyePosition().add(offset);
    }
}
