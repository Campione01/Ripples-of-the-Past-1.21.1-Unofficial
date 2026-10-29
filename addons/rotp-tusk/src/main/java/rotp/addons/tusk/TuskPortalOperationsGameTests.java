package rotp.addons.tusk;

import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import rotp.addons.tusk.entity.WormholeEntity;
import rotp.addons.tusk.entity.WormholePortalEntity;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.state.TuskPortalOperations;
import rotp.addons.tusk.state.TuskPortalOperations.MemberRole;
import rotp.addons.tusk.state.TuskPortalOperations.MoveReceipt;
import rotp.addons.tusk.state.TuskPortalOperations.OperationKind;
import rotp.addons.tusk.state.TuskPortalOperations.OperationRecord;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Template and enabled namespace are rotp_t (runTuskGameTest).
@GameTestHolder(TuskAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TuskPortalOperationsGameTests {
    private static final UUID OWNER_UUID = UUID.fromString(
            "842b7876-77ca-43f6-a979-e71e164ea969");
    private static final UUID ARM_OWNER_UUID = UUID.fromString(
            "5d0c2f3e-1b7a-4c52-9e0d-6a3f8b41c7d2");
    private static final UUID STEP_OWNER_UUID = UUID.fromString(
            "0f6b9d1c-4e27-4a8b-b3c5-2d9e7a61f804");
    private static final double EPSILON_SQR = 1.0E-8D;

    private TuskPortalOperationsGameTests() {}

    @GameTest(
            template = "empty",
            timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void portalMoveReceiptAndPersistedStateMatchDonor(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> portalMoveReceiptAndPersistedStateMatchDonorBody(h));
    }

    private static void portalMoveReceiptAndPersistedStateMatchDonorBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = FakePlayerFactory.get(
                level,
                new GameProfile(OWNER_UUID, "TuskPortalMove"));
        StandType standType = TuskAddon.TUSK_ACT_3.get();
        StandPower power = null;
        WormholeEntity source = null;
        TuskPortalOperations operations =
                TuskPortalOperations.get(level.getServer());
        BlockPos targetBlock = helper.absolutePos(
                new BlockPos(3, 13, 6));
        BlockState originalTarget = level.getBlockState(targetBlock);

        try {
            BlockPos ownerBlock = helper.absolutePos(
                    new BlockPos(6, 12, 2));
            owner.moveTo(
                    ownerBlock.getX() + 0.5D,
                    ownerBlock.getY(),
                    ownerBlock.getZ() + 0.5D,
                    0.0F,
                    0.0F);
            helper.assertTrue(
                    level.addFreshEntity(owner),
                    "Could not add the Tusk portal owner.");

            power = PowerClass.STAND.attachGet(owner);
            StandPowerTransitions.Result inserted =
                    StandPowerTransitions.insert(
                            power, new StandInstance(standType));
            helper.assertTrue(
                    inserted.status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Tusk Act 3: "
                            + inserted.status());
            power.skipProgression();
            helper.assertTrue(
                    standType.summon(owner, power),
                    "Could not summon Tusk Act 3.");
            TuskAddon.nailState(owner).applySnapshot(
                    TuskNailState.MAX_NAILS, 0, false);

            operations.closeOwned(owner.getUUID());
            operations.resetMoveDiagnostics(owner.getUUID());
            level.setBlockAndUpdate(
                    targetBlock, Blocks.OBSIDIAN.defaultBlockState());
            owner.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(targetBlock));

            Vec3 sourcePosition = Vec3.atCenterOf(
                    helper.absolutePos(new BlockPos(6, 13, 6)));
            source = new WormholeEntity(owner, level, 200, null);
            source.setPos(sourcePosition);
            helper.assertTrue(
                    level.addFreshEntity(source),
                    "Could not add the donor wormhole source.");
            helper.assertTrue(
                    operations.createPortalPair(owner),
                    "Production portal-pair creation failed.");

            OperationRecord beforeRecord =
                    operations.getOwned(owner.getUUID());
            helper.assertTrue(
                    beforeRecord != null
                            && beforeRecord.kind()
                                    == OperationKind.PORTAL_PAIR,
                    "Production portal-pair record is missing.");
            WormholePortalEntity entry = portal(
                    helper, level, beforeRecord.entryUuid(), "ENTRY");
            WormholePortalEntity exit = portal(
                    helper, level, beforeRecord.exitUuid(), "EXIT");
            Vec3 initialEntry = entry.position();
            Vec3 initialExit = exit.position();

            operations.resetMoveDiagnostics(owner.getUUID());
            helper.assertTrue(
                    operations.moveOwned(
                            owner, OperationKind.PORTAL_PAIR),
                    "Production moveOwned rejected the owned portal pair.");

            List<MoveReceipt> receipts =
                    operations.moveReceipts(owner.getUUID());
            helper.assertTrue(
                    receipts.size() == 1,
                    "Expected one move receipt, got "
                            + receipts.size() + '.');
            MoveReceipt receipt = receipts.getFirst();
            OperationRecord afterRecord =
                    operations.getOwned(owner.getUUID());

            helper.assertTrue(
                    receipt.sequence() == 1
                            && receipt.ownerUuid().equals(owner.getUUID())
                            && receipt.operationUuid().equals(
                                    beforeRecord.operationUuid())
                            && receipt.memberUuid().equals(
                                    beforeRecord.exitUuid())
                            && receipt.kind()
                                    == OperationKind.PORTAL_PAIR
                            && receipt.role() == MemberRole.EXIT,
                    "Move receipt identity did not bind the owned EXIT.");
            helper.assertTrue(
                    !receipt.inAir(),
                    "Solid ray target was classified as air.");
            helper.assertTrue(
                    !receipt.verticalCollisionBefore(),
                    "Isolated EXIT began the move vertically colliding.");
            assertVec(
                    helper,
                    receipt.before(),
                    initialExit,
                    "Receipt before-position drifted");

            Vec3 expectedDifference = receipt.target()
                    .subtract(receipt.before());
            Vec3 expectedMotion = expectedDifference.multiply(
                    TuskPortalOperations.MOVE_XZ_FACTOR,
                    TuskPortalOperations.MOVE_Y_FACTOR,
                    TuskPortalOperations.MOVE_XZ_FACTOR).add(
                            0.0D,
                            TuskPortalOperations.GROUND_COMPENSATION,
                            0.0D);
            Vec3 expectedAfter = receipt.before()
                    .add(expectedMotion)
                    .add(
                            0.0D,
                            -TuskPortalOperations.GROUND_COMPENSATION,
                            0.0D);

            assertVec(
                    helper,
                    receipt.difference(),
                    expectedDifference,
                    "Donor movement difference drifted");
            assertVec(
                    helper,
                    receipt.motion(),
                    expectedMotion,
                    "Donor 0.25/1.0/0.25 motion or +0.7 medium lift drifted");
            assertVec(
                    helper,
                    receipt.after(),
                    expectedAfter,
                    "Receipt after-position drifted");
            assertVec(
                    helper,
                    receipt.entryBefore(),
                    initialEntry,
                    "ENTRY moved before EXIT displacement");
            assertVec(
                    helper,
                    receipt.entryAfter(),
                    initialEntry,
                    "ENTRY moved with the EXIT");
            helper.assertTrue(
                    receipt.after().distanceToSqr(receipt.before())
                            > EPSILON_SQR,
                    "Production moveOwned emitted a zero displacement.");
            Vec3 fullRay = owner.getViewVector(1.0F)
                    .scale(TuskPortalOperations.MOVE_RANGE);
            assertVec(
                    helper,
                    receipt.rayEnd(),
                    receipt.rayStart().add(fullRay),
                    "Receipt rayEnd is not the full 12-block ray endpoint");
            Vec3 hitOffset = receipt.target()
                    .subtract(receipt.rayStart());
            double rayLengthSqr = fullRay.lengthSqr();
            helper.assertTrue(
                    hitOffset.cross(fullRay).lengthSqr()
                                    <= EPSILON_SQR
                            && hitOffset.dot(fullRay) >= 0.0D
                            && hitOffset.dot(fullRay)
                                    <= rayLengthSqr + EPSILON_SQR,
                    "Production collision target is not on the bounded "
                            + "12-block ray segment.");
            helper.assertTrue(
                    afterRecord != null,
                    "Portal operation disappeared during displacement.");
            assertVec(
                    helper,
                    entry.position(),
                    initialEntry,
                    "ENTRY entity position changed");
            assertVec(
                    helper,
                    exit.position(),
                    receipt.after(),
                    "EXIT entity and receipt diverged");
            assertVec(
                    helper,
                    afterRecord.entryPosition(),
                    initialEntry,
                    "Persisted ENTRY position changed");
            assertVec(
                    helper,
                    afterRecord.exitPosition(),
                    receipt.after(),
                    "Persisted EXIT and receipt diverged");

            helper.succeed();
        }
        finally {
            operations.closeOwned(owner.getUUID());
            if (source != null && !source.isRemoved()) {
                source.discard();
            }
            level.setBlockAndUpdate(targetBlock, originalTarget);
            if (power != null && power.isSummoned()) {
                standType.forceUnsummon(owner, power);
            }
            owner.discard();
        }
    }

    // 1.16 AbstractWormholeEntity.tick: the extra 0.7 step-up move is made after a vertical collision, not at a wall.
    @GameTest(
            template = "empty",
            timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void portalStepUpFollowsVerticalCollisionOnly(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> portalStepUpFollowsVerticalCollisionOnlyBody(h));
    }

    private static void portalStepUpFollowsVerticalCollisionOnlyBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = FakePlayerFactory.get(
                level,
                new GameProfile(STEP_OWNER_UUID, "TuskPortalStep"));
        StandType standType = TuskAddon.TUSK_ACT_3.get();
        StandPower power = null;
        WormholeEntity source = null;
        TuskPortalOperations operations =
                TuskPortalOperations.get(level.getServer());
        BlockPos sourceBlock = helper.absolutePos(new BlockPos(6, 13, 6));
        BlockPos floorBlock = sourceBlock.below();
        BlockPos wallBlock = sourceBlock.offset(2, 1, 0);
        BlockState originalFloor = level.getBlockState(floorBlock);
        BlockState originalWall = level.getBlockState(wallBlock);

        try {
            BlockPos ownerBlock = helper.absolutePos(
                    new BlockPos(6, 12, 2));
            owner.moveTo(
                    ownerBlock.getX() + 0.5D,
                    ownerBlock.getY(),
                    ownerBlock.getZ() + 0.5D,
                    0.0F,
                    0.0F);
            helper.assertTrue(
                    level.addFreshEntity(owner),
                    "Could not add the Tusk portal owner.");

            power = PowerClass.STAND.attachGet(owner);
            StandPowerTransitions.Result inserted =
                    StandPowerTransitions.insert(
                            power, new StandInstance(standType));
            helper.assertTrue(
                    inserted.status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Tusk Act 3: "
                            + inserted.status());
            power.skipProgression();
            helper.assertTrue(
                    standType.summon(owner, power),
                    "Could not summon Tusk Act 3.");
            TuskAddon.nailState(owner).applySnapshot(
                    TuskNailState.MAX_NAILS, 0, false);
            operations.closeOwned(owner.getUUID());

            // Open air up and to the side: the 12-block ray misses, so the target is its end and gets no ground lift.
            owner.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(
                            helper.absolutePos(new BlockPos(3, 16, 6))));
            level.setBlockAndUpdate(
                    floorBlock, Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(
                    wallBlock, Blocks.OBSIDIAN.defaultBlockState());

            source = new WormholeEntity(owner, level, 200, null);
            source.setPos(Vec3.atCenterOf(sourceBlock));
            helper.assertTrue(
                    level.addFreshEntity(source),
                    "Could not add the donor wormhole source.");
            helper.assertTrue(
                    operations.createPortalPair(owner),
                    "Production portal-pair creation failed.");
            OperationRecord record = operations.getOwned(owner.getUUID());
            helper.assertTrue(
                    record != null
                            && record.kind() == OperationKind.PORTAL_PAIR,
                    "Production portal-pair record is missing.");
            WormholePortalEntity exit = portal(
                    helper, level, record.exitUuid(), "EXIT");

            // Resting on the floor: the last move was stopped from below.
            exit.move(MoverType.SELF, new Vec3(0.0D, -0.7D, 0.0D));
            helper.assertTrue(
                    exit.verticalCollision
                            && !exit.horizontalCollision
                            && Math.abs(exit.getY()
                                    - (floorBlock.getY() + 1.0D)) < 1.0E-9D,
                    "The EXIT did not come to rest on the floor: y="
                            + exit.getY() + ", vertical="
                            + exit.verticalCollision + ", horizontal="
                            + exit.horizontalCollision);
            MoveReceipt onGround = moveOnce(helper, operations, owner);
            helper.assertTrue(
                    onGround.inAir(),
                    "The open-air ray end was classified as ground.");
            Vec3 stepped = onGround.before()
                    .add(onGround.motion().scale(2.0D));
            assertVec(
                    helper,
                    onGround.after(),
                    stepped,
                    "An EXIT resting on the ground must first make the "
                            + "donor's (motion + 0.7) step-up move and then "
                            + "(motion - 0.7); without the step it ends at "
                            + onGround.before().add(onGround.motion())
                                    .add(0.0D, -0.7D, 0.0D));
            assertVec(
                    helper,
                    operations.getOwned(owner.getUUID()).exitPosition(),
                    exit.position(),
                    "Persisted EXIT and entity diverged after the step");

            // In the air against a wall: the last move was stopped only sideways.
            exit.setPos(Vec3.atCenterOf(sourceBlock.above()));
            exit.move(MoverType.SELF, new Vec3(1.5D, 0.0D, 0.0D));
            helper.assertTrue(
                    exit.horizontalCollision
                            && !exit.verticalCollision
                            && Math.abs(exit.getX()
                                    - (wallBlock.getX() - 0.5D)) < 1.0E-9D,
                    "The EXIT did not stop against the wall: x="
                            + exit.getX() + ", vertical="
                            + exit.verticalCollision + ", horizontal="
                            + exit.horizontalCollision);
            MoveReceipt atWall = moveOnce(helper, operations, owner);
            helper.assertTrue(
                    atWall.inAir(),
                    "The open-air ray end was classified as ground.");
            assertVec(
                    helper,
                    atWall.after(),
                    atWall.before().add(atWall.motion())
                            .add(0.0D, -0.7D, 0.0D),
                    "An EXIT that only touched a wall must make the single "
                            + "(motion - 0.7) move; with the step-up move it "
                            + "ends at " + atWall.before()
                                    .add(atWall.motion().scale(2.0D)));

            helper.succeed();
        }
        finally {
            operations.closeOwned(owner.getUUID());
            if (source != null && !source.isRemoved()) {
                source.discard();
            }
            level.setBlockAndUpdate(floorBlock, originalFloor);
            level.setBlockAndUpdate(wallBlock, originalWall);
            if (power != null && power.isSummoned()) {
                standType.forceUnsummon(owner, power);
            }
            owner.discard();
        }
    }

    private static MoveReceipt moveOnce(
            GameTestHelper helper,
            TuskPortalOperations operations,
            ServerPlayer owner) {
        operations.resetMoveDiagnostics(owner.getUUID());
        helper.assertTrue(
                operations.moveOwned(owner, OperationKind.PORTAL_PAIR),
                "Production moveOwned rejected the owned portal pair.");
        List<MoveReceipt> receipts =
                operations.moveReceipts(owner.getUUID());
        helper.assertTrue(
                receipts.size() == 1,
                "Expected one move receipt, got " + receipts.size() + '.');
        return receipts.getFirst();
    }

    // 1.16: the hand whose arm is in the wormhole is emptied (item dropped, 40-tick pickup delay).
    @GameTest(
            template = "empty",
            timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void remoteArmEmptiesTheArmHand(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> remoteArmEmptiesTheArmHandBody(h));
    }

    private static void remoteArmEmptiesTheArmHandBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = FakePlayerFactory.get(
                level,
                new GameProfile(ARM_OWNER_UUID, "TuskArmHand"));
        StandType standType = TuskAddon.TUSK_ACT_3.get();
        StandPower power = null;
        WormholeEntity source = null;
        TuskPortalOperations operations =
                TuskPortalOperations.get(level.getServer());
        AABB dropArea = null;

        try {
            BlockPos ownerBlock = helper.absolutePos(
                    new BlockPos(2, 12, 2));
            owner.moveTo(
                    ownerBlock.getX() + 0.5D,
                    ownerBlock.getY(),
                    ownerBlock.getZ() + 0.5D,
                    0.0F,
                    0.0F);
            helper.assertTrue(
                    level.addFreshEntity(owner),
                    "Could not add the Tusk arm owner.");
            dropArea = owner.getBoundingBox().inflate(2.0D);

            power = PowerClass.STAND.attachGet(owner);
            StandPowerTransitions.Result inserted =
                    StandPowerTransitions.insert(
                            power, new StandInstance(standType));
            helper.assertTrue(
                    inserted.status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Tusk Act 3: "
                            + inserted.status());
            power.skipProgression();
            helper.assertTrue(
                    standType.summon(owner, power),
                    "Could not summon Tusk Act 3.");
            TuskAddon.nailState(owner).applySnapshot(
                    TuskNailState.MAX_NAILS, 0, false);
            operations.closeOwned(owner.getUUID());

            owner.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(Items.DIAMOND_SWORD));
            owner.setItemInHand(
                    InteractionHand.OFF_HAND,
                    new ItemStack(Items.SHIELD));
            helper.assertTrue(
                    !TuskGameEvents.emptyArmHand(owner)
                            && owner.getMainHandItem().is(
                                    Items.DIAMOND_SWORD),
                    "A hand was emptied without an active arm.");

            source = new WormholeEntity(owner, level, 200, null);
            source.setPos(Vec3.atCenterOf(
                    helper.absolutePos(new BlockPos(2, 13, 6))));
            helper.assertTrue(
                    level.addFreshEntity(source),
                    "Could not add the donor wormhole source.");
            helper.assertTrue(
                    operations.createRemoteArm(owner)
                            && operations.hasActiveArm(owner),
                    "Production remote-arm creation failed.");

            // 10 nails: the main-hand arm is out.
            helper.assertTrue(
                    TuskGameEvents.emptyArmHand(owner),
                    "Active arm did not empty the main hand.");
            helper.assertTrue(
                    owner.getMainHandItem().isEmpty()
                            && owner.getOffhandItem().is(Items.SHIELD),
                    "Only the main hand should be emptied at 10 nails.");
            List<ItemEntity> swords = level.getEntitiesOfClass(
                    ItemEntity.class,
                    dropArea,
                    item -> item.getItem().is(Items.DIAMOND_SWORD));
            helper.assertTrue(
                    swords.size() == 1
                            && swords.getFirst().hasPickUpDelay()
                            && Math.abs(swords.getFirst().getY()
                                    - owner.getEyeY()) < 1.0E-6D,
                    "Dropped sword missing, not at eye height or "
                            + "pickable at once: " + swords.size());
            helper.assertTrue(
                    !TuskGameEvents.emptyArmHand(owner),
                    "An empty arm hand dropped again.");

            // 5 nails: the off-hand arm is out.
            TuskAddon.nailState(owner).applySnapshot(5, 0, false);
            helper.assertTrue(
                    TuskGameEvents.emptyArmHand(owner)
                            && owner.getOffhandItem().isEmpty(),
                    "Active arm did not empty the off hand at 5 nails.");
            helper.assertTrue(
                    level.getEntitiesOfClass(
                            ItemEntity.class,
                            dropArea,
                            item -> item.getItem().is(Items.SHIELD))
                            .size() == 1,
                    "Dropped shield missing.");

            operations.closeOwned(owner.getUUID());
            owner.setItemInHand(
                    InteractionHand.OFF_HAND,
                    new ItemStack(Items.SHIELD));
            helper.assertTrue(
                    !TuskGameEvents.emptyArmHand(owner)
                            && owner.getOffhandItem().is(Items.SHIELD),
                    "A hand was emptied after the arm closed.");

            helper.succeed();
        }
        finally {
            operations.closeOwned(owner.getUUID());
            if (source != null && !source.isRemoved()) {
                source.discard();
            }
            if (dropArea != null) {
                level.getEntitiesOfClass(ItemEntity.class, dropArea)
                        .forEach(ItemEntity::discard);
            }
            owner.getInventory().clearContent();
            if (power != null && power.isSummoned()) {
                standType.forceUnsummon(owner, power);
            }
            owner.discard();
        }
    }

    private static WormholePortalEntity portal(
            GameTestHelper helper,
            ServerLevel level,
            UUID uuid,
            String role) {
        var entity = level.getEntity(uuid);
        helper.assertTrue(
                entity instanceof WormholePortalEntity,
                role + " portal entity is missing.");
        return (WormholePortalEntity) entity;
    }

    private static void assertVec(
            GameTestHelper helper,
            Vec3 actual,
            Vec3 expected,
            String message) {
        helper.assertTrue(
                actual != null
                        && expected != null
                        && actual.distanceToSqr(expected) <= EPSILON_SQR,
                message + ": expected=" + expected + ", actual=" + actual);
    }
}
