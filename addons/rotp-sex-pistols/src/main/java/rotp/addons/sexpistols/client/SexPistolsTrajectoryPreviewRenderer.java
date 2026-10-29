package rotp.addons.sexpistols.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTransferOrder;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class SexPistolsTrajectoryPreviewRenderer {
    public static final int MAX_SIMULATION_STEPS = 120;
    public static final double MAX_RENDER_SEGMENT_LENGTH = 0.35D;
    public static final double LIVE_PROJECTILE_RANGE = 128.0D;
    public static final double RICOCHET_MIN_SPEED_SQR = 0.04D;
    public static final double RICOCHET_SURFACE_OFFSET = 0.18D;
    public static final double RICOCHET_TRAVEL_BIAS = 0.06D;

    private static final float HELD_LINE_WIDTH = 2.4F;
    private static final float LIVE_LINE_WIDTH = 1.8F;
    private static final double MIN_MOTION_SQR = 1.0E-4D;
    private static final double MEMBER_HIT_INFLATION = 0.12D;

    private SexPistolsTrajectoryPreviewRenderer() {}

    @SubscribeEvent
    public static void renderTrajectory(
            RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null
                || level == null
                || !shouldRender(player)) {
            return;
        }

        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(player);
        SexPistolsClientConfigSnapshot.Snapshot previewConfig =
                SexPistolsClientConfigSnapshot.snapshot();
        List<SexPistolsEntity> members = members(
                level, player, coordinator);
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(true);
        List<TrajectoryPath> paths = new ArrayList<>();
        TrajectorySpec held = heldSpec(
                minecraft,
                player,
                partialTick,
                previewConfig.maxRicochetCount());
        if (held != null) {
            addPath(
                    paths,
                    simulate(
                            level,
                            player,
                            coordinator,
                            members,
                            previewConfig,
                            held,
                            HELD_LINE_WIDTH,
                            1.0F));
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RevolverBulletEntity bullet
                    && bullet.isAlive()
                    && bullet.distanceToSqr(player)
                            <= LIVE_PROJECTILE_RANGE
                                    * LIVE_PROJECTILE_RANGE) {
                Vec3 motion = bullet.getDeltaMovement();
                if (motion.lengthSqr() < MIN_MOTION_SQR) {
                    continue;
                }
                addPath(
                        paths,
                        simulate(
                                level,
                                player,
                                coordinator,
                                members,
                                previewConfig,
                                new TrajectorySpec(
                                        bullet.getPosition(partialTick),
                                        motion,
                                        bullet.ballisticGravity(),
                                        bullet.ballisticDrag(),
                                        previewConfig
                                                .maxRicochetCount(),
                                        bullet.ricochetCount()),
                                LIVE_LINE_WIDTH,
                                0.8F));
            }
        }
        if (paths.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        int segmentCount = 0;
        int ricochets = 0;
        int redirects = 0;
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (TrajectoryPath path : paths) {
            segmentCount += Math.max(0, path.points.size() - 1);
            ricochets += path.ricochets;
            redirects += path.memberRedirects;
            renderPath(poseStack, buffers, path);
        }
        poseStack.popPose();
        SexPistolsPresentationDiagnostics.recordTrajectory(
                paths.size(), segmentCount, ricochets, redirects);
    }

    private static boolean shouldRender(LocalPlayer player) {
        StandPower standPower = StandPower.get(player);
        return standPower != null
                && standPower.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get()
                && player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                && SexPistolsAttachments.coordinator(player)
                        .trajectoryVision();
    }

    private static TrajectorySpec heldSpec(
            Minecraft minecraft,
            LocalPlayer player,
            float partialTick,
            int maxRicochetCount) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RevolverItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof RevolverItem)) {
            return null;
        }
        Vec3 direction = player.getViewVector(partialTick).normalize();
        Vec3 start = player.getEyePosition(partialTick)
                .add(direction.scale(0.25D));
        if (minecraft.options.getCameraType().isFirstPerson()) {
            Vec3 right = Vec3.directionFromRotation(
                    0.0F, player.getYRot() + 90.0F).normalize();
            Vec3 up = right.cross(direction);
            if (up.lengthSqr() < 1.0E-6D) {
                up = new Vec3(0.0D, 1.0D, 0.0D);
            }
            else {
                up = up.normalize();
            }
            start = player.getEyePosition(partialTick)
                    .add(direction.scale(0.45D))
                    .add(right.scale(0.42D))
                    .subtract(up.scale(0.28D));
        }
        return new TrajectorySpec(
                start,
                direction.scale(RevolverBulletEntity.SPEED),
                RevolverBulletEntity.EFFECTIVE_GRAVITY,
                RevolverBulletEntity.DRAG,
                maxRicochetCount,
                0);
    }

    private static List<SexPistolsEntity> members(
            ClientLevel level,
            LocalPlayer owner,
            SexPistolsCoordinator coordinator) {
        List<SexPistolsEntity> members = new ArrayList<>();
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            Entity entity = level.getEntity(
                    coordinator.childEntityId(number));
            if (entity instanceof SexPistolsEntity member
                    && member.isOwnedBy(owner)) {
                members.add(member);
            }
        }
        return members;
    }

    private static TrajectoryPath simulate(
            ClientLevel level,
            LocalPlayer owner,
            SexPistolsCoordinator coordinator,
            List<SexPistolsEntity> members,
            SexPistolsClientConfigSnapshot.Snapshot previewConfig,
            TrajectorySpec spec,
            float lineWidth,
            float alphaScale) {
        List<Vec3> points = new ArrayList<>();
        Vec3 position = spec.start;
        Vec3 motion = spec.motion;
        RedirectState redirects = new RedirectState();
        int ricochets = spec.initialRicochets;
        int memberRedirects = 0;
        points.add(position);
        for (int step = 0;
                step < MAX_SIMULATION_STEPS
                        && motion.lengthSqr() >= MIN_MOTION_SQR;
                step++) {
            Vec3 next = position.add(motion);
            MemberHit memberHit = findMemberHit(
                    members,
                    redirects.memberMask,
                    position,
                    next);
            if (memberHit != null) {
                appendSegment(points, position, memberHit.location);
                redirects.memberMask |= 1 << memberHit.slot;
                LivingEntity target = findTarget(
                        level,
                        owner,
                        coordinator,
                        memberHit.location,
                        previewConfig.targetSearchRange());
                SexPistolsEntity relay = findRelay(
                        level,
                        owner,
                        coordinator,
                        members,
                        memberHit.member,
                        target,
                        memberHit.location,
                        redirects,
                        previewConfig.transferAssistRange());
                Vec3 aimPoint = relay != null
                        ? relay.getBoundingBox().getCenter()
                        : target != null
                                ? target.getBoundingBox().getCenter()
                                : memberHit.location.add(
                                        motion.normalize());
                Vec3 direction = aimPoint.subtract(
                        memberHit.location);
                if (direction.lengthSqr() < 1.0E-7D) {
                    direction = owner.getLookAngle();
                }
                direction = direction.normalize();
                motion = direction.scale(
                        Math.max(0.25D, motion.length()));
                position = memberHit.location.add(
                        direction.scale(0.35D));
                appendSegment(points, memberHit.location, position);
                redirects.lastMember = memberHit.member.getUUID();
                memberRedirects++;
                continue;
            }

            BlockHitResult blockHit = level.clip(
                    new ClipContext(
                            position,
                            next,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            owner));
            if (blockHit.getType() != HitResult.Type.MISS) {
                appendSegment(
                        points, position, blockHit.getLocation());
                RicochetResult ricochet = ricochets < spec.maxRicochets
                        ? tryRicochet(
                                level,
                                owner,
                                motion,
                                blockHit,
                                previewConfig.bulletDestroysBlocks())
                        : null;
                if (ricochet == null) {
                    break;
                }
                ricochets++;
                appendSegment(
                        points,
                        blockHit.getLocation(),
                        ricochet.position);
                position = ricochet.position;
                motion = ricochet.motion;
                continue;
            }
            appendSegment(points, position, next);
            position = next;
            motion = motion.scale(spec.drag)
                    .subtract(0.0D, spec.gravity, 0.0D);
        }
        return new TrajectoryPath(
                points,
                lineWidth,
                alphaScale,
                Math.max(0, ricochets - spec.initialRicochets),
                memberRedirects);
    }

    private static MemberHit findMemberHit(
            List<SexPistolsEntity> members,
            int redirectedMask,
            Vec3 start,
            Vec3 end) {
        MemberHit closest = null;
        for (SexPistolsEntity member : members) {
            int slot = slotForNumber(member.getMemberNumber());
            if (slot < 0
                    || (redirectedMask & 1 << slot) != 0
                    || member.getKickTicks() != 0
                    || !member.canRedirectBullet()) {
                continue;
            }
            Optional<Vec3> clipped = member.getBoundingBox()
                    .inflate(MEMBER_HIT_INFLATION)
                    .clip(start, end);
            if (clipped.isEmpty()) {
                continue;
            }
            double distance = start.distanceToSqr(clipped.get());
            if (closest == null
                    || distance < closest.distanceSqr) {
                closest = new MemberHit(
                        member, clipped.get(), slot, distance);
            }
        }
        return closest;
    }

    private static LivingEntity findTarget(
            ClientLevel level,
            LocalPlayer owner,
            SexPistolsCoordinator coordinator,
            Vec3 origin,
            double range) {
        AABB search = AABB.ofSize(
                origin, range * 2.0D, range * 2.0D, range * 2.0D);
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        candidate -> coordinator.targetMode()
                                        .matches(owner, candidate)
                                && hasLineOfSight(
                                        level,
                                        owner,
                                        origin,
                                        candidate.getBoundingBox()
                                                .getCenter()))
                .stream()
                .min(Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(origin)))
                .orElse(null);
    }

    private static SexPistolsEntity findRelay(
            ClientLevel level,
            LocalPlayer owner,
            SexPistolsCoordinator coordinator,
            List<SexPistolsEntity> members,
            SexPistolsEntity source,
            LivingEntity target,
            Vec3 origin,
            RedirectState redirects,
            double transferRange) {
        SexPistolsTransferOrder order = coordinator.transferOrder();
        if (order == SexPistolsTransferOrder.NONE) {
            return null;
        }
        double rangeSqr = transferRange * transferRange;
        List<SexPistolsEntity> candidates = members.stream()
                .filter(member -> member != source)
                .filter(member -> member.getKickTicks() == 0)
                .filter(SexPistolsEntity::canRedirectBullet)
                .filter(member -> redirects.lastMember == null
                        || !member.getUUID().equals(
                                redirects.lastMember))
                .filter(member -> {
                    int slot = slotForNumber(member.getMemberNumber());
                    return slot >= 0
                            && (redirects.memberMask & 1 << slot) == 0;
                })
                .filter(member -> member.distanceToSqr(origin)
                        <= rangeSqr)
                .filter(member -> hasLineOfSight(
                        level,
                        owner,
                        origin,
                        member.getBoundingBox().getCenter()))
                .toList();
        if (order == SexPistolsTransferOrder.NUMBER) {
            return candidates.stream()
                    .filter(member -> member.getMemberNumber()
                            > source.getMemberNumber())
                    .min(Comparator
                            .comparingInt(
                                    SexPistolsEntity::getMemberNumber)
                            .thenComparingDouble(
                                    member -> member.distanceToSqr(
                                            origin)))
                    .orElse(null);
        }
        if (target == null) {
            return null;
        }
        Vec3 targetPosition = target.getBoundingBox().getCenter();
        double sourceDistance = source.distanceToSqr(targetPosition);
        return candidates.stream()
                .filter(member -> member.distanceToSqr(targetPosition)
                        < sourceDistance)
                .min(Comparator.comparingDouble(
                        member -> member.distanceToSqr(targetPosition)))
                .orElse(null);
    }

    private static boolean hasLineOfSight(
            ClientLevel level,
            Entity context,
            Vec3 start,
            Vec3 end) {
        return level.clip(new ClipContext(
                        start,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        context))
                .getType() == HitResult.Type.MISS;
    }

    private static RicochetResult tryRicochet(
            ClientLevel level,
            Entity context,
            Vec3 motion,
            BlockHitResult hit,
            boolean bulletDestroysBlocks) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        ImpactTier tier = classifyImpact(
                level, context, pos, state, hardness);
        if (!bulletDestroysBlocks
                && (tier == ImpactTier.FRAGILE_BREAK
                        || tier == ImpactTier.SOFT_BREAK)) {
            tier = ImpactTier.HARD_SURFACE;
        }
        if (tier == ImpactTier.FORCED_RICOCHET) {
            return ricochetFrom(hit, motion, 0.88D);
        }
        if (tier == ImpactTier.HARD_SURFACE
                && shouldRicochetOnHardSurface(
                        level, context, pos, state, motion,
                        hit.getDirection())) {
            return ricochetFrom(hit, motion, 0.72D);
        }
        return null;
    }

    private static RicochetResult ricochetFrom(
            BlockHitResult hit,
            Vec3 motion,
            double speedScale) {
        Vec3 normal = directionVector(hit.getDirection());
        Vec3 reflected = motion.subtract(
                normal.scale(2.0D * motion.dot(normal)));
        if (reflected.lengthSqr() < 1.0E-7D) {
            return null;
        }
        Vec3 bounced = reflected.normalize().scale(
                Math.max(
                        Math.sqrt(RICOCHET_MIN_SPEED_SQR),
                        motion.length() * speedScale));
        if (bounced.lengthSqr() < RICOCHET_MIN_SPEED_SQR) {
            return null;
        }
        Vec3 position = hit.getLocation()
                .add(normal.scale(RICOCHET_SURFACE_OFFSET))
                .add(bounced.normalize().scale(
                        RICOCHET_TRAVEL_BIAS));
        return new RicochetResult(position, bounced);
    }

    private static ImpactTier classifyImpact(
            ClientLevel level,
            Entity context,
            BlockPos pos,
            BlockState state,
            float hardness) {
        if (hardness < 0.0F || isAlwaysRicochetBlock(state)) {
            return ImpactTier.FORCED_RICOCHET;
        }
        SoundType sound = state.getSoundType(level, pos, context);
        boolean hard = sound == SoundType.METAL
                || sound == SoundType.STONE;
        if (sound == SoundType.GLASS
                || state.is(BlockTags.LEAVES)
                || !hard && hardness <= 0.35F) {
            return ImpactTier.FRAGILE_BREAK;
        }
        if (hard) {
            return ImpactTier.HARD_SURFACE;
        }
        if (hardness <= 1.2F
                || hardness <= 2.4F
                        && state.getFluidState().isEmpty()) {
            return ImpactTier.SOFT_BREAK;
        }
        return ImpactTier.HARD_SURFACE;
    }

    private static boolean shouldRicochetOnHardSurface(
            ClientLevel level,
            Entity context,
            BlockPos pos,
            BlockState state,
            Vec3 motion,
            Direction face) {
        if (motion.lengthSqr() < 1.0E-6D) {
            return false;
        }
        double incidence = Math.abs(
                motion.normalize().dot(directionVector(face)));
        SoundType sound = state.getSoundType(level, pos, context);
        double threshold = sound == SoundType.METAL
                ? 0.42D
                : 0.36D;
        return incidence < threshold;
    }

    private static boolean isAlwaysRicochetBlock(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL);
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
    }

    private static int slotForNumber(int number) {
        for (int slot = 0;
                slot < SexPistolsIds.SEX_PISTOLS_NUMBERS.length;
                slot++) {
            if (SexPistolsIds.SEX_PISTOLS_NUMBERS[slot] == number) {
                return slot;
            }
        }
        return -1;
    }

    private static void appendSegment(
            List<Vec3> points, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double length = segment.length();
        if (length < 1.0E-6D) {
            return;
        }
        int subdivisions = Math.max(
                1,
                (int) Math.ceil(
                        length / MAX_RENDER_SEGMENT_LENGTH));
        for (int index = 1; index <= subdivisions; index++) {
            points.add(start.add(segment.scale(
                    (double) index / subdivisions)));
        }
    }

    private static void addPath(
            List<TrajectoryPath> paths, TrajectoryPath path) {
        if (path.points.size() > 1) {
            paths.add(path);
        }
    }

    private static void renderPath(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            TrajectoryPath path) {
        RenderType renderType = RenderType.debugLineStrip(
                path.lineWidth);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        int last = path.points.size() - 1;
        for (int index = 0; index <= last; index++) {
            Vec3 point = path.points.get(index);
            Vec3 normal = index < last
                    ? path.points.get(index + 1).subtract(point)
                    : point.subtract(path.points.get(index - 1));
            if (normal.lengthSqr() < 1.0E-7D) {
                normal = new Vec3(0.0D, 1.0D, 0.0D);
            }
            else {
                normal = normal.normalize();
            }
            float progress = last > 0
                    ? (float) index / last
                    : 0.0F;
            int alpha = Math.max(
                    12,
                    (int) (107.0F
                            * (1.0F - progress * 0.7F)
                            * path.alphaScale));
            vertices.addVertex(
                            pose,
                            (float) point.x,
                            (float) point.y,
                            (float) point.z)
                    .setColor(232, 145, 250, alpha)
                    .setNormal(
                            pose,
                            (float) normal.x,
                            (float) normal.y,
                            (float) normal.z);
        }
        buffers.endBatch(renderType);
    }

    private record TrajectorySpec(
            Vec3 start,
            Vec3 motion,
            double gravity,
            double drag,
            int maxRicochets,
            int initialRicochets) {}

    private record TrajectoryPath(
            List<Vec3> points,
            float lineWidth,
            float alphaScale,
            int ricochets,
            int memberRedirects) {}

    private record MemberHit(
            SexPistolsEntity member,
            Vec3 location,
            int slot,
            double distanceSqr) {}

    private record RicochetResult(Vec3 position, Vec3 motion) {}

    private static final class RedirectState {
        private int memberMask;
        private UUID lastMember;
    }

    private enum ImpactTier {
        FRAGILE_BREAK,
        SOFT_BREAK,
        HARD_SURFACE,
        FORCED_RICOCHET
    }
}
