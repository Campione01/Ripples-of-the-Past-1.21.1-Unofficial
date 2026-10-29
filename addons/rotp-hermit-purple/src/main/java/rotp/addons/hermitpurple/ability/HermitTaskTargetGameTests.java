package rotp.addons.hermitpurple.ability;

import java.util.Objects;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.entity.HermitBarrierVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

/**
 * 1.16 Hermit Purple tasks kept the target their action accepted (StandEntityTask.keepTarget), which a next action
 * pressed at nothing took over (StandEntity.setTask): HPAttachBarrier a block (getTargetRequirement BLOCK),
 * HPGrapple only in its shift variation hp_vine_entity an entity (standKeepsTarget), HPVineAttack any target.
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitTaskTargetGameTests {
    private static final short BARRIER_KEY = 94;

    private HermitTaskTargetGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_task_target")
    public static void hermitTasksKeepTheTargetsTheDonorKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPTaskTarget"));
        StandPower power = null;
        Cow cow = null;
        BlockPos stone = null;
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            level.addNewPlayer(player);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            HermitPurpleStandEntity stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "setup: no Hermit Purple controller");

            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "setup: no cow");
            cow.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 3.5D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "setup: the cow was not added");
            stone = origin.offset(3, 0, 0);
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            ActionTarget cowTarget = new ActionTarget(cow);
            ActionTarget stoneTarget = new ActionTarget(stone, Direction.UP);

            // HPAttachBarrier: a block
            EntityActionInstance barrier = start(helper, power, stand, player, HermitPurpleAddon.HP_BARRIER_NAME,
                    stoneTarget);
            helper.assertTrue(holds(helper, barrier, TargetType.BLOCK) && barrier.getTaskTarget(level)
                            .getBlockPos().equals(stone),
                    "1.16 HPAttachBarrier (BLOCK) kept the block it was pressed at, the task holds "
                            + barrier.getTaskTarget(level));
            helper.assertTrue(!holds(helper, start(helper, power, stand, player, HermitPurpleAddon.HP_BARRIER_NAME,
                            cowTarget), TargetType.ENTITY),
                    "1.16 HPAttachBarrier (BLOCK) kept an entity");
            helper.assertTrue(!holds(helper, start(helper, power, stand, player, HermitPurpleAddon.HP_UNBARRIER_NAME,
                            stoneTarget), TargetType.BLOCK),
                    "1.16 HPUnattachBarrier kept a target");

            // HPGrapple: only the shift variation, an entity
            EntityActionInstance entityVine = start(helper, power, stand, player,
                    HermitPurpleAddon.HP_VINE_ENTITY_NAME, cowTarget);
            helper.assertTrue(holds(helper, entityVine, TargetType.ENTITY)
                            && entityVine.getTaskTarget(level).getEntity() == cow,
                    "1.16 HPGrapple.standKeepsTarget kept the entity hp_vine_entity was pressed at, the task holds "
                            + entityVine.getTaskTarget(level));
            helper.assertTrue(!holds(helper, start(helper, power, stand, player,
                            HermitPurpleAddon.HP_VINE_ENTITY_NAME, stoneTarget), TargetType.BLOCK),
                    "1.16 HPGrapple.standKeepsTarget kept a block");
            helper.assertTrue(!holds(helper, start(helper, power, stand, player, HermitPurpleAddon.HP_VINE_NAME,
                            cowTarget), TargetType.ENTITY),
                    "1.16 hp_vine, not a shift variation, kept an entity");
            helper.assertTrue(!holds(helper, start(helper, power, stand, player, HermitPurpleAddon.HP_GRAB_NAME,
                            cowTarget), TargetType.ENTITY),
                    "1.16 HPGrabCommand kept an entity");

            // HPVineAttack: any target, and a next one pressed at nothing takes it over
            for (String name : new String[] {HermitPurpleAddon.HP_VINE_ATTACK_NAME,
                    HermitPurpleAddon.HP_VINE_SHIFT_NAME}) {
                EntityActionInstance atCow = start(helper, power, stand, player, name, cowTarget);
                helper.assertTrue(atCow.getTaskTarget(level).getEntity() == cow,
                        "1.16 HPVineAttack.standKeepsTarget kept the entity " + name + " was pressed at, the task holds "
                                + atCow.getTaskTarget(level));
                EntityActionInstance atStone = start(helper, power, stand, player, name, stoneTarget);
                helper.assertTrue(holds(helper, atStone, TargetType.BLOCK),
                        "1.16 HPVineAttack.standKeepsTarget kept the block " + name + " was pressed at, the task holds "
                                + atStone.getTaskTarget(level));
                start(helper, power, stand, player, name, cowTarget);
                EntityActionInstance atNothing = start(helper, power, stand, player, name, ActionTarget.EMPTY);
                helper.assertTrue(atNothing.getTaskTarget(level).getEntity() == cow,
                        "1.16 StandEntity.setTask gave " + name + " pressed at nothing the cow of the one it replaced, "
                                + "it holds " + atNothing.getTaskTarget(level));
            }
            LogUtils.getLogger().info("R770_HP_TASK_TARGET_PASS task_target");
        }
        finally {
            if (stone != null) {
                level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
            }
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }

    /*
     * 1.16 StandEntityTask.rotateStand: a task holding no target left the Stand to defaultRotation, its user's look. hp_grab
     * and hp_vine kept none, nor hp_vine_entity pressed at a block; hp_vine_entity turned the Stand to the entity it kept.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_task_turn")
    public static void hermitGrapplesTurnTheStandOnlyToATargetTheirTaskKeeps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPTaskTurn"));
        StandPower power = null;
        Cow cow = null;
        BlockPos stone = null;
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            player.setXRot(0.0F);
            level.addNewPlayer(player);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            HermitPurpleStandEntity stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null && stand.isFollowingUser(), "setup: no following Hermit Purple controller");

            // east of the user, who looks south
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "setup: no cow");
            cow.moveTo(origin.getX() + 3.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "setup: the cow was not added");
            stone = origin.offset(-3, 0, 0);
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            ActionTarget cowTarget = new ActionTarget(cow);
            ActionTarget stoneTarget = new ActionTarget(stone, Direction.UP);

            Object[][] alongTheLook = { { HermitPurpleAddon.HP_GRAB_NAME, cowTarget },
                    { HermitPurpleAddon.HP_VINE_NAME, cowTarget },
                    { HermitPurpleAddon.HP_VINE_ENTITY_NAME, stoneTarget } };
            for (Object[] row : alongTheLook) {
                start(helper, power, stand, player, (String) row[0], (ActionTarget) row[1]);
                stand.updatePosition(player);
                helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - player.getYRot())) < 0.01F,
                        "1.16 left the Stand of " + row[0] + ", whose task kept no target, to its user's look (yaw "
                                + player.getYRot() + "); it faces yaw " + stand.getYRot());
            }
            start(helper, power, stand, player, HermitPurpleAddon.HP_VINE_ENTITY_NAME, cowTarget);
            stand.updatePosition(player);
            Vec3 toCow = cow.getEyePosition().subtract(stand.getEyePosition()).normalize();
            helper.assertTrue(stand.getLookAngle().dot(toCow) > 0.999D,
                    "1.16 turned the Stand of hp_vine_entity to the cow its task kept; it looks along "
                            + stand.getLookAngle() + " instead of " + toCow);
            LogUtils.getLogger().info("R770_HP_TASK_TARGET_PASS task_turn");
        }
        finally {
            if (stone != null) {
                level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
            }
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget emptied the target an action was pressed at unless its requirement or
     * standKeepsTarget took it and checkStandTarget allowed it; an emptied one never turned the Stand.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_task_pressed")
    public static void hermitPressedTargetsFollowTheDonorsTargetChecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPPressed"));
        StandPower power = null;
        Cow cow = null;
        BlockPos stone = null;
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setNoGravity(true);
            level.addNewPlayer(player);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            HermitPurpleStandEntity stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "setup: no Hermit Purple controller");
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "setup: no cow");
            cow.moveTo(origin.getX() + 3.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "setup: the cow was not added");
            helper.assertTrue(stand.canAttack(cow), "setup: Hermit Purple may not attack the cow");
            stone = origin.offset(-3, 0, 0);
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            ActionTarget cowTarget = new ActionTarget(cow);
            ActionTarget stoneTarget = new ActionTarget(stone, Direction.UP);

            // name, the cow taken, the block taken
            Object[][] rows = { { HermitPurpleAddon.HP_BARRIER_NAME, false, true },
                    { HermitPurpleAddon.HP_UNBARRIER_NAME, false, false },
                    { HermitPurpleAddon.HP_VINE_NAME, false, false },
                    { HermitPurpleAddon.HP_GRAB_NAME, false, false },
                    { HermitPurpleAddon.HP_VINE_ENTITY_NAME, true, false },
                    { HermitPurpleAddon.HP_VINE_ATTACK_NAME, true, true },
                    { HermitPurpleAddon.HP_VINE_SHIFT_NAME, true, true } };
            for (Object[] row : rows) {
                String name = (String) row[0];
                helper.assertTrue(power.getAbility(name) instanceof StandEntityAbility,
                        "setup: " + name + " is no Stand entity ability");
                StandEntityAbility ability = (StandEntityAbility) power.getAbility(name);
                boolean atCow = ability.takesPressedTarget(stand, cowTarget);
                boolean atStone = ability.takesPressedTarget(stand, stoneTarget);
                helper.assertTrue(atCow == (Boolean) row[1] && atStone == (Boolean) row[2],
                        "1.16's target checks for " + name + ": cow " + row[1] + ", block " + row[2]
                                + "; the port took cow " + atCow + ", block " + atStone);
            }
            EntityActionInstance barrier = start(helper, power, stand, player, HermitPurpleAddon.HP_BARRIER_NAME,
                    cowTarget);
            helper.assertTrue(barrier.standRotationTarget == null,
                    "1.16 hp_barrier pressed at a cow left no target to turn the Stand to; it holds "
                            + barrier.standRotationTarget);
            LogUtils.getLogger().info("R770_HP_TASK_TARGET_PASS pressed");
        }
        finally {
            if (stone != null) {
                level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
            }
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        }
        helper.succeed();
    }

    /*
     * 1.16 HPAttachBarrier (TargetRequirement BLOCK): PowerBaseImpl.checkTarget refused the press at anything but a
     * block, silently, and Action.checkRangeAndTarget one at air (silently) or beyond 10 blocks ("target_too_far"), so
     * nothing started; a press at a block in reach stretched the string from it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_barrier_press")
    public static void hpBarrierPressIsRefusedUnlessAtABlockAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPBarrierPress"));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos near = origin.offset(-3, 0, 0);
        BlockPos far = origin.offset(0, 0, 12);
        BlockPos air = origin.offset(2, 0, 0);
        Cow[] cow = new Cow[1];
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(BARRIER_KEY, player);
            level.setBlock(near, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
            if (cow[0] != null) {
                cow[0].discard();
            }
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        };
        HermitPurpleStandEntity summoned;
        try {
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setNoGravity(true);
            level.addNewPlayer(player);
            PlayerPower hamonPower = PowerClass.PLAYER_POWER.attachGet(player);
            hamonPower.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.ROPE_TRAP.get());
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.ROPE_TRAP.get()), "setup: no Rope Trap");
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            summoned = HermitPurpleAddon.controller(player);
            helper.assertTrue(summoned != null, "setup: no Hermit Purple controller");
            cow[0] = EntityType.COW.create(level);
            helper.assertTrue(cow[0] != null, "setup: no cow");
            cow[0].moveTo(origin.getX() + 3.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            cow[0].setNoAi(true);
            cow[0].setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow[0]), "setup: the cow was not added");
            level.setBlock(near, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(far, Blocks.STONE.defaultBlockState(), 3);
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity stand = summoned;
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> {
                    try {
                        Ability barrier = power.getAbility(HermitPurpleAddon.HP_BARRIER_NAME);
                        helper.assertTrue(barrier != null, "setup: no hp_barrier");
                        Object[][] refused = { { "nothing", ActionTarget.EMPTY, null },
                                { "a cow", new ActionTarget(cow[0]), null },
                                { "air", new ActionTarget(air, Direction.UP), null },
                                { "a stone 12 blocks away", new ActionTarget(far, Direction.UP),
                                        "jojo.message.action_condition.target_too_far" } };
                        for (Object[] row : refused) {
                            aimBoth(player, stand, (ActionTarget) row[1]);
                            ConditionCheck check = barrier.checkConditions(power);
                            String warning = warningKey(check);
                            helper.assertTrue(!check.isPositive() && Objects.equals(warning, row[2]),
                                    "1.16 refused hp_barrier pressed at " + row[0] + " (message " + row[2]
                                            + "); the port answers " + check.isPositive() + " " + warning);
                        }
                        aimBoth(player, stand, new ActionTarget(near, Direction.UP));
                        ConditionCheck accepted = barrier.checkConditions(power);
                        helper.assertTrue(accepted.isPositive(),
                                "hp_barrier pressed at a stone 3 blocks away was refused: " + warningKey(accepted));
                        BufferingState pressed = BufferingState.clickOnly();
                        AbilityInput.keyPress(BARRIER_KEY, barrier, player, null, InputMethod.CLICK, 0.0F, pressed,
                                barrier.getAbilityId());
                        AbilityInput.keyRelease(BARRIER_KEY, player);
                        EntityActionInstance action = stand.getCurStandAction();
                        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == barrier,
                                "hp_barrier pressed at a stone did not start");
                        AABB around = new AABB(near).inflate(16.0D);
                        for (int i = 0; i < 3 && strings(level, around, near) == 0 && !action.isOver(); i++) {
                            action._tickAction();
                        }
                        helper.assertTrue(strings(level, around, near) == 1,
                                "hp_barrier pressed at a stone started no string from it: "
                                        + strings(level, around, near));
                        LogUtils.getLogger().info("R770_HP_TASK_TARGET_PASS barrier_press");
                    }
                    finally {
                        close.run();
                    }
                })
                .thenSucceed();
    }

    /*
     * 1.16 StandEntityAction.overrideVanillaMouseTarget let a pressed block without a collision shape give way to the
     * mob behind it only for an action that takes an entity: HPVineAttack (no requirement) took the cow behind a torch,
     * HPAttachBarrier (TargetRequirement BLOCK) kept the torch and stretched its string from it. The Stand's eyes are
     * level with its user's, just south of the user's box, looking south as the user does, as in the core's scenes.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_barrier_torch")
    public static void hpBarrierPressedAtATorchKeepsTheTorchAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPBarrierTorch"));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
        Cow[] cow = new Cow[1];
        BlockPos[] torch = new BlockPos[1];
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(BARRIER_KEY, player);
            if (torch[0] != null) {
                level.setBlock(torch[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (cow[0] != null) {
                cow[0].discard();
            }
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.discard();
        };
        HermitPurpleStandEntity summoned;
        try {
            player.setNoGravity(true);
            player.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            player.setYHeadRot(0.0F);
            level.addNewPlayer(player);
            PlayerPower hamonPower = PowerClass.PLAYER_POWER.attachGet(player);
            hamonPower.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.ROPE_TRAP.get());
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.ROPE_TRAP.get()), "setup: no Rope Trap");
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            summoned = HermitPurpleAddon.controller(player);
            helper.assertTrue(summoned != null, "setup: no Hermit Purple controller");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity stand = summoned;
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> {
                    try {
                        stand.setCanFollowUser(false);
                        stand.setNoGravity(true);
                        stand.moveTo(player.getX(), player.getEyeY() - stand.getEyeHeight(), player.getZ() + 0.35D,
                                0.0F, 0.0F);
                        stand.setYHeadRot(0.0F);
                        torch[0] = BlockPos.containing(player.getEyePosition()).south();
                        level.setBlock(torch[0], Blocks.WALL_TORCH.defaultBlockState()
                                        .setValue(WallTorchBlock.FACING, Direction.NORTH),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        cow[0] = EntityType.COW.create(level);
                        helper.assertTrue(cow[0] != null, "setup: no cow");
                        cow[0].setNoAi(true);
                        cow[0].setNoGravity(true);
                        cow[0].moveTo(player.getX(), player.getEyeY() - cow[0].getBbHeight() / 2.0D,
                                player.getZ() + 2.0D, 0.0F, 0.0F);
                        helper.assertTrue(level.addFreshEntity(cow[0]), "setup: the cow was not added");
                        ActionTarget atTorch = new ActionTarget(torch[0], Direction.NORTH);

                        StandEntityAbility vineAttack =
                                (StandEntityAbility) power.getAbility(HermitPurpleAddon.HP_VINE_ATTACK_NAME);
                        helper.assertTrue(vineAttack.overridePressedTarget(player, atTorch).getEntity() == cow[0],
                                "setup: the Stand's collision ray does not pass the torch to the cow, the vine attack"
                                        + " takes " + vineAttack.overridePressedTarget(player, atTorch));
                        StandEntityAbility barrier =
                                (StandEntityAbility) power.getAbility(HermitPurpleAddon.HP_BARRIER_NAME);
                        ActionTarget kept = barrier.overridePressedTarget(player, atTorch);
                        helper.assertTrue(kept.getType() == TargetType.BLOCK && torch[0].equals(kept.getBlockPos()),
                                "1.16 HPAttachBarrier (TargetRequirement BLOCK) kept the torch it was pressed at; the"
                                        + " port's press let it give way to " + kept);

                        aimBoth(player, stand, atTorch);
                        ConditionCheck accepted = barrier.checkConditions(power);
                        helper.assertTrue(accepted.isPositive(),
                                "hp_barrier pressed at a torch was refused: " + warningKey(accepted));
                        BufferingState pressed = BufferingState.clickOnly();
                        AbilityInput.keyPress(BARRIER_KEY, barrier, player, null, InputMethod.CLICK, 0.0F, pressed,
                                barrier.getAbilityId());
                        AbilityInput.keyRelease(BARRIER_KEY, player);
                        EntityActionInstance action = stand.getCurStandAction();
                        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == barrier,
                                "hp_barrier pressed at a torch did not start");
                        ActionTarget held = action.getTaskTarget(level);
                        helper.assertTrue(held.getType() == TargetType.BLOCK && torch[0].equals(held.getBlockPos()),
                                "1.16's hp_barrier pressed at a torch with a cow behind it held the torch; the port's"
                                        + " task holds " + held);
                        AABB around = new AABB(torch[0]).inflate(16.0D);
                        for (int i = 0; i < 3 && strings(level, around, torch[0]) == 0 && !action.isOver(); i++) {
                            action._tickAction();
                        }
                        helper.assertTrue(strings(level, around, torch[0]) == 1,
                                "1.16's hp_barrier pressed at a torch stretched its string from the torch; the port's"
                                        + " strings from it: " + strings(level, around, torch[0]));
                        LogUtils.getLogger().info("R770_HP_TASK_TARGET_PASS barrier_torch");
                    }
                    finally {
                        close.run();
                    }
                })
                .thenSucceed();
    }

    private static int strings(ServerLevel level, AABB around, BlockPos from) {
        return level.getEntitiesOfClass(HermitBarrierVineEntity.class, around,
                barrier -> from.equals(barrier.getOriginBlockPos())).size();
    }

    private static String warningKey(ConditionCheck check) {
        if (check.getWarning() == null) {
            return null;
        }
        return check.getWarning().getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : check.getWarning().getString();
    }

    private static void aimBoth(FakePlayer player, HermitPurpleStandEntity stand, ActionTarget target) {
        LivingComponentAction.getComponent(player).entityAim.setTarget(target);
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    private static boolean holds(GameTestHelper helper, EntityActionInstance action, TargetType type) {
        return action.getTaskTarget(helper.getLevel()).getType() == type;
    }

    private static EntityActionInstance start(GameTestHelper helper, StandPower power, HermitPurpleStandEntity stand,
            FakePlayer player, String name, ActionTarget pressedAt) {
        LivingComponentAction.getComponent(player);
        LivingComponentAction.getComponent(stand);
        LivingComponentAction.getAim(player).setTarget(pressedAt);
        LivingComponentAction.getAim(stand).setTarget(pressedAt);
        EntityActionType type = (EntityActionType) power.getAbility(name);
        helper.assertTrue(type != null, "Hermit Purple has no action " + name);
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), player, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, player, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action, "setup: " + name + " did not become the Stand's action");
        return action;
    }
}
