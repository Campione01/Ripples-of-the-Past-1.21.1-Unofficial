package rotp.addons.speedking;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.speedking.entity.SpeedKingEntity;
import rotp.addons.speedking.entity.SpeedKingPickaxeEntity;
import rotp.addons.speedking.state.SpeedKingState;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 099 SpeedKingHeatTrap.standKeepsTarget BLOCK with standMovesByItself: the self-moving Stand turns to the block the
 * press kept (StandEntityTask.rotateStand), and an entity pressed is not kept. 099 ExampleStandThrowPickaxe.standPerform
 * shot the pickaxe with StandEntity.shootProjectile, whose addProjectile scaled its damage and speed by the Stand's
 * attack damage and speed over 8. 099 InitStands gave partsRequired(ARMS) to the pickaxe, the heat trap, the barrage and
 * the punches but not to heat cook, whose hold PowerBaseImpl.tickHeldAction rechecked on every tick.
 */
@GameTestHolder(SpeedKingAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpeedKingGameTests {
    private static final short KEY = 125;

    private SpeedKingGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatTrapTurnsTheStandToThePressedBlock(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkTrapBlock")) {
            BlockPos block = helper.absolutePos(new BlockPos(6, 2, 5));
            helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            scene.aim(new ActionTarget(block, Direction.WEST), Vec3.atCenterOf(block));
            scene.stand.setYRot(90.0F);
            scene.stand.setXRot(0.0F);
            EntityActionInstance trap = scene.start("speed_king_heat_trap");
            helper.assertTrue(trap.getTaskTarget(scene.owner.level()).getType() == ActionTarget.TargetType.BLOCK,
                    "1.16 standKeepsTarget BLOCK: the task holds the pressed block, it holds "
                            + trap.getTaskTarget(scene.owner.level()));
            scene.stand.updatePosition(scene.owner);
            Vec3 toBlock = Vec3.atCenterOf(block).subtract(scene.stand.getEyePosition()).normalize();
            Vec3 look = scene.stand.getLookAngle();
            helper.assertTrue(look.dot(toBlock) > 0.999D,
                    "1.16 StandEntityTask.rotateStand turned the self-moving Stand to the block its task held; it looks along "
                            + look + " instead of " + toBlock);
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatTrapPressedAtAnEntityKeepsNothingAndDoesNotTurn(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkTrapEntity")) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            scene.extra = cow;
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(new Vec3(6.5D, 2.0D, 5.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            scene.aim(new ActionTarget(cow), cow.getEyePosition());
            scene.stand.setYRot(90.0F);
            scene.stand.setXRot(0.0F);
            EntityActionInstance trap = scene.start("speed_king_heat_trap");
            helper.assertTrue(trap.getTaskTarget(scene.owner.level()).getType() == ActionTarget.TargetType.EMPTY
                            && trap.standRotationTarget == null,
                    "1.16 standKeepsTarget kept only a block; the task holds " + trap.getTaskTarget(scene.owner.level())
                            + ", the Stand turns to " + trap.standRotationTarget);
            scene.stand.updatePosition(scene.owner);
            helper.assertTrue(Math.abs(scene.stand.getYRot() - 90.0F) < 1.0E-4F,
                    "A Stand whose task holds no target keeps its yaw while the heat trap charges, it turned to "
                            + scene.stand.getYRot());
            // 1.16 StandEntity.setTaskTarget went through the same standKeepsTarget
            scene.stand.setTaskTarget(new ActionTarget(cow));
            helper.assertTrue(trap.getTaskTarget(scene.owner.level()).getType() == ActionTarget.TargetType.EMPTY,
                    "1.16 standKeepsTarget refused an entity given to the task later too; the task holds "
                            + trap.getTaskTarget(scene.owner.level()));
            helper.succeed();
        }
    }

    // 099 SpeedKingHeatTrap.standPerform (:66-91) cast JojoModUtil.rayTrace(eyes, look, getMaxRange, ..., 0, 0), which
    // meets blocks by their outline (1.16 JojoModUtil :120-127): a trap aimed at a torch was set on the torch, not on the
    // block behind it.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatTrapIsSetOnTheTorchItsRayMeetsAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkTrapTorch")) {
            scene.owner.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
            scene.owner.setYHeadRot(0.0F);
            Vec3 eyes = scene.owner.getEyePosition();
            // a wall torch hanging on nothing on the crosshair, its outline 1.19 blocks before the eyes
            BlockPos torch = BlockPos.containing(eyes).south();
            BlockPos stone = torch.south(2);
            helper.getLevel().setBlock(torch,
                    Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            try {
                EntityActionInstance trap = scene.start("speed_king_heat_trap");
                trap.actionPerformStart();
                SpeedKingState state = SpeedKingAddon.state(scene.owner);
                helper.assertTrue(state.hasHeatTrap() && state.heatTrapPos().equals(torch),
                        "1.16's ray met the torch by its outline and set the heat trap there; the port set it at "
                                + (state.hasHeatTrap() ? state.heatTrapPos() : "nowhere") + " (the torch " + torch
                                + ", the stone behind it " + stone + ")");
            } finally {
                helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        }
    }

    // 099 SpeedKingHeatTrap.standPerform's JojoModUtil.rayTrace (1.16 JojoModUtil :150-182) took the pickable entities on
    // its 8 blocks first, through blocks, the Stand and its user left out; an ENTITY result set no trap (:76).
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatTrapAimedAtOrPastAMobIsNotSetAsIn116(GameTestHelper helper) {
        List<String> wrong = new java.util.ArrayList<>();
        try (Scene scene = new Scene(helper, "SkTrapMob")) {
            scene.owner.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
            scene.owner.setYHeadRot(0.0F);
            Vec3 eyes = scene.owner.getEyePosition();
            BlockPos stone = BlockPos.containing(eyes).south(5);
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            scene.extra = cow;
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(eyes.add(0.0D, -0.7D, 2.5D));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            try {
                Object[][] rows = {
                        { "a cow on the crosshair 2.5 blocks before the stone", 0.0D, 2.5D, false },
                        { "a cow behind the stone, 6.5 blocks away", 0.0D, 6.5D, false },
                        { "a cow behind the stone, 9.5 blocks away", 0.0D, 9.5D, true },
                        { "a cow 3 blocks beside the ray", 3.0D, 2.5D, true } };
                for (Object[] row : rows) {
                    cow.setPos(eyes.add((Double) row[1], -0.7D, (Double) row[2]));
                    SpeedKingState state = SpeedKingAddon.state(scene.owner);
                    state.clearHeatTrap();
                    EntityActionInstance trap = scene.start("speed_king_heat_trap");
                    trap.actionPerformStart();
                    boolean set = state.hasHeatTrap() && state.heatTrapPos().equals(stone);
                    if (set != (Boolean) row[3]) {
                        wrong.add(row[0] + ": 1.16 " + ((Boolean) row[3] ? "set the trap on the stone " + stone
                                : "met the cow first and set no trap") + "; the port set it "
                                + (state.hasHeatTrap() ? "at " + state.heatTrapPos() : "nowhere"));
                    }
                    LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                }
            } finally {
                helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            }
        }
        helper.assertTrue(wrong.isEmpty(), "Heat trap with a mob on its ray: " + wrong);
        helper.succeed();
    }

    // 099 SpeedKingHeatTrap.standPerform :79 standEntity.moveTo(ray.getLocation()): Speed King went to the point the ray
    // met on the block's face, not into the block.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatTrapMovesSpeedKingToTheRaysHitPointAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkTrapPlace")) {
            scene.owner.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
            scene.owner.setYHeadRot(0.0F);
            Vec3 eyes = scene.owner.getEyePosition();
            BlockPos stone = BlockPos.containing(eyes).south(5);
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            try {
                EntityActionInstance trap = scene.start("speed_king_heat_trap");
                trap.actionPerformStart();
                SpeedKingState state = SpeedKingAddon.state(scene.owner);
                Vec3 face = new Vec3(eyes.x, eyes.y, stone.getZ());
                helper.assertTrue(state.hasHeatTrap() && state.heatTrapPos().equals(stone),
                        "Setup: the trap is not on the stone " + stone + ": "
                                + (state.hasHeatTrap() ? state.heatTrapPos() : "nowhere"));
                helper.assertTrue(scene.stand.position().distanceTo(face) < 1.0E-4D,
                        "1.16 moved Speed King to the point its ray met on the stone's face " + face
                                + "; the port put it at " + scene.stand.position() + " (the stone's centre "
                                + Vec3.atCenterOf(stone) + ")");
            } finally {
                helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void thrownPickaxeIsScaledByTheStandsAttackStats(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkPickaxe")) {
            scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16.0D);
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(12.0D);
            double damage = scene.stand.getAttackDamage();
            double speed = scene.stand.getAttackSpeed();
            helper.assertTrue(Math.abs(damage / 8 - 1.0D) > 0.1D && Math.abs(speed / 8 - 1.0D) > 0.1D,
                    "Setup: the Stand's stats must differ from 8, damage " + damage + ", speed " + speed);
            EntityActionInstance throwing = scene.start("example_stand_throw_pickaxe");
            throwing.setPhaseStart(ActionPhase.PERFORM);
            throwing._tickAction();
            List<SpeedKingPickaxeEntity> shot = helper.getLevel().getEntitiesOfClass(SpeedKingPickaxeEntity.class,
                    scene.stand.getBoundingBox().inflate(6.0D));
            try {
                helper.assertTrue(shot.size() == 1, "The throw shot " + shot.size() + " pickaxes, not one");
                SpeedKingPickaxeEntity pickaxe = shot.get(0);
                helper.assertTrue(Math.abs(pickaxe.getDamageFactor() - damage / 8) < 1.0E-4D,
                        "1.16 addProjectile scaled the pickaxe's damage by the Stand's attack damage / 8 = " + damage / 8
                                + ", it has " + pickaxe.getDamageFactor());
                helper.assertTrue(Math.abs(pickaxe.getSpeedFactor() - speed / 8) < 1.0E-4D,
                        "1.16 addProjectile scaled the pickaxe's speed by the Stand's attack speed / 8 = " + speed / 8
                                + ", it has " + pickaxe.getSpeedFactor());
            }
            finally {
                shot.forEach(SpeedKingPickaxeEntity::discard);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void heatCookHoldNeedsNoStandArmsAsInTheDonor(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkCookArms")) {
            EntityActionInstance cook = scene.hold("speed_king_heat_cook");
            scene.power.getStandInstance().orElseThrow().removePart(StandPart.ARMS);
            cook._tickAction();
            cook._tickAction();
            helper.assertTrue(!cook.isOver() && cook.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "1.16 gave heat cook no partsRequired: its hold went on without the arms; phase " + cook.getPhase()
                            + ", over " + cook.isOver());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void pickaxeHoldEndsWhenTheStandLosesItsArms(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkPickaxeArms")) {
            EntityActionInstance throwing = scene.hold("example_stand_throw_pickaxe");
            helper.assertTrue(!throwing.isOver(), "Setup: the pickaxe hold is over before the arms are lost");
            scene.power.getStandInstance().orElseThrow().removePart(StandPart.ARMS);
            throwing._tickAction();
            helper.assertTrue(throwing.isOver(),
                    "1.16 partsRequired(ARMS) ended the pickaxe hold once the arms were gone; phase " + throwing.getPhase());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(SpeedKingPickaxeEntity.class,
                            scene.stand.getBoundingBox().inflate(6.0D)).isEmpty(),
                    "The ended hold still threw the pickaxe");
            helper.succeed();
        }
    }

    // 1.16 StandEntityLightAttack:53, StandEntityMeleeBarrage:65 and StandEntityHeavyAttack:125 refused the press of a
    // Stand that cannot attack in melee (an attack speed of 0 here); the finisher punch is a StandEntityHeavyAttack too.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void meleeMovesRefuseTheirPressWhenTheStandCannotAttackInMelee(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkNoMelee")) {
            double speed = scene.stand.getAttribute(Attributes.ATTACK_SPEED).getBaseValue();
            String[] moves = {"example_stand_punch", "example_stand_barrage", "example_stand_heavy_punch",
                    "example_stand_finisher_punch"};
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(0.0D);
            helper.assertTrue(!scene.stand.canAttackMelee(), "Setup: the Stand can still attack in melee");
            for (String move : moves) {
                helper.assertTrue(!scene.press(move),
                        "1.16 refused the press of " + move + " for a Stand that cannot attack in melee; it started "
                                + scene.stand.getCurStandAction());
                helper.assertTrue(scene.stand.getCurStandAction() == null,
                        "The refused press of " + move + " left an action: " + scene.stand.getCurStandAction());
            }
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(speed);
            helper.assertTrue(scene.stand.canAttackMelee(), "Setup: the Stand cannot attack in melee again");
            for (String move : moves) {
                helper.assertTrue(scene.press(move), "The press of " + move + " was refused for a Stand that can attack");
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            }
            helper.succeed();
        }
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran StandEntityMeleeBarrage.checkStandConditions on every held tick.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void barrageHoldEndsWhenTheStandCanNoLongerAttackInMelee(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkBarrageMelee")) {
            EntityActionInstance barrage = scene.hold("example_stand_barrage");
            barrage._tickAction();
            helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver(),
                    "Setup: the barrage is not performing, phase " + barrage.getPhase());
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(0.0D);
            barrage._tickAction();
            helper.assertTrue(barrage.isOver() || barrage.getPhase() == ActionPhase.RECOVERY,
                    "1.16 ended the barrage hold once the Stand could not attack in melee; phase " + barrage.getPhase());
            helper.succeed();
        }
    }

    // 1.16 StandEntityMeleeBarrage was a holdType Stand action (heldAllowsOtherAction); Speed King's charges were not.
    // Its recovery still keeps a punch pressed during the barrage waiting (isCancelable needed no recovery).
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void barrageIsAHoldTypeStandActionAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkBarrageHoldType")) {
            EntityActionInstance barrage = scene.hold("example_stand_barrage");
            barrage._tickAction();
            helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver(),
                    "Setup: the barrage is not performing, phase " + barrage.getPhase());
            helper.assertTrue(barrage.isHoldTypeStandAction(),
                    "1.16 example_stand_barrage was a StandEntityMeleeBarrage, a holdType Stand action");
            helper.assertTrue(!barrage.canBeCancelledInto((EntityActionType) scene.power.getAbility("example_stand_punch")),
                    "1.16 kept a press during a barrage with recovery ticks queued; the punch would replace it");
            AbilityInput.keyRelease(KEY, scene.owner);
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            for (String charge : List.of("speed_king_heat_cook", "example_stand_throw_pickaxe", "speed_king_heat_trap")) {
                EntityActionInstance held = scene.start(charge);
                helper.assertTrue(!held.isHoldTypeStandAction(),
                        "1.16 " + charge + " was a hold-to-fire action, not a holdType one");
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            }
            helper.succeed();
        }
    }

    // 1.16 ActionsOverlayGui heldReadyToFire: a slot turned green once a hold was held past its fire length, which only
    // a holdToFire(n, true) action could be: the pickaxe throw and the heat trap (20); heat cook fired at its 40th tick.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void releasedThrowsShowReadyToFireAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkReadyToFire")) {
            Object[][] rows = {
                    { "example_stand_throw_pickaxe", 19, false },
                    { "example_stand_throw_pickaxe", 20, true },
                    { "speed_king_heat_trap", 20, true },
                    { "speed_king_heat_cook", 39, false } };
            for (Object[] row : rows) {
                String name = (String) row[0];
                int tick = (Integer) row[1];
                boolean ready = (Boolean) row[2];
                EntityActionInstance held = scene.start(name);
                held.setPhase(ActionPhase.BUTTON_CHARGE, tick);
                EntityActionAbility ability = (EntityActionAbility) scene.power.getAbility(name);
                helper.assertTrue(held.getPhase() == ActionPhase.BUTTON_CHARGE && ability.isHeldReadyToFire(scene.owner) == ready,
                        "1.16 heldReadyToFire for " + name + " held " + tick + " ticks was " + ready + ", the port says "
                                + ability.isHeldReadyToFire(scene.owner) + " (phase " + held.getPhase() + ")");
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            }
            helper.succeed();
        }
    }

    // 1.16 099 InitStands :61-64: the punch, the barrage, the heavy punch and the finisher were the core
    // StandEntityLightAttack, StandEntityMeleeBarrage and StandEntityHeavyAttack, whose phase lengths came from
    // StandStatFormulas: light windup, perform getLightAttackRecovery and recovery x4 (x2 arms-only); heavy windup
    // getHeavyAttackWindup, perform 1, recovery getHeavyAttackRecovery; barrage recovery getBarrageRecovery (0 arms-only).
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void meleePhasesFollowTheDonorFormulasAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkMeleePhases")) {
            double speed = scene.stand.getAttackSpeed();
            helper.assertTrue(speed > 0.0D, "Setup: Speed King has no attack speed");
            scene.stand.setFinisherMeter(0.0F);
            EntityActionInstance punch = scene.start("example_stand_punch");
            double windupValue = (24 - speed) / 4 / 2;
            float punchWindup = punch.phasesLength.getFloat(ActionPhase.WINDUP);
            float perform = lightRecovery116(speed, 0.0F);
            helper.assertTrue((punchWindup == (float) Math.floor(windupValue) || punchWindup == (float) Math.ceil(windupValue))
                            && punch.phasesLength.getFloat(ActionPhase.PERFORM) == perform
                            && punch.phasesLength.getFloat(ActionPhase.RECOVERY) == perform * 4,
                    "1.16 example_stand_punch (a first punch) ran windup " + windupValue + " rounded, perform " + perform
                            + ", recovery " + perform * 4 + "; the port " + phases(punch));
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            for (float meter : new float[] {0.0F, 1.0F}) {
                for (String name : List.of("example_stand_heavy_punch", "example_stand_finisher_punch")) {
                    // a heavy attack spends the meter it reads, so each press is given it again
                    scene.stand.setFinisherMeter(meter);
                    EntityActionInstance heavy = scene.start(name);
                    helper.assertTrue(heavy.phasesLength.getFloat(ActionPhase.WINDUP) == heavyWindup116(speed, meter)
                                    && heavy.phasesLength.getFloat(ActionPhase.PERFORM) == 1.0F
                                    && heavy.phasesLength.getFloat(ActionPhase.RECOVERY) == heavyRecovery116(speed, meter),
                            "1.16 " + name + " at a finisher meter of " + meter + " ran windup "
                                    + heavyWindup116(speed, meter) + ", perform 1, recovery " + heavyRecovery116(speed, meter)
                                    + "; the port " + phases(heavy));
                    LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                }
            }
            scene.stand.setFinisherMeter(0.0F);
            EntityActionInstance barrage = scene.start("example_stand_barrage");
            float barrageRecovery = (float) Math.floor((40.0D - speed * 1.25D) * 0.25D);
            helper.assertTrue(barrage.phasesLength.getFloat(ActionPhase.RECOVERY) == barrageRecovery,
                    "1.16 example_stand_barrage recovered " + barrageRecovery + " ticks, the port " + phases(barrage));
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            scene.stand.setArmsOnlyMode();
            EntityActionInstance armsPunch = scene.start("example_stand_punch");
            helper.assertTrue(armsPunch.phasesLength.getFloat(ActionPhase.RECOVERY)
                            == armsPunch.phasesLength.getFloat(ActionPhase.PERFORM) * 2,
                    "1.16 an arms-only punch recovered twice its perform, the port " + phases(armsPunch));
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            EntityActionInstance armsBarrage = scene.start("example_stand_barrage");
            helper.assertTrue(armsBarrage.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
                    "1.16 an arms-only barrage had no recovery, the port " + phases(armsBarrage));
            helper.succeed();
        }
    }

    // 1.16 StandEntityLightAttack.isCancelable :127-129 (any action, the punch too: isChainable, in its recovery),
    // StandEntityHeavyAttack.isFreeRecovery :228-230 (a finisher's recovery, not the heavy punch's) and
    // StandEntityMeleeBarrage.isCancelable :179-190 / isFreeRecovery / getHoldDurationMax: a heavy attack follows a
    // barrage up in its recovery, Resolve frees that recovery, and the hold ends after 20 + 5 x durability ticks.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void meleeRecoveriesAndBarrageLengthFollowTheDonorAsIn116(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "SkMeleeCancel")) {
            EntityActionType punch = (EntityActionType) scene.power.getAbility("example_stand_punch");
            EntityActionType heavy = (EntityActionType) scene.power.getAbility("example_stand_heavy_punch");
            EntityActionType finisher = (EntityActionType) scene.power.getAbility("example_stand_finisher_punch");
            EntityActionType barrageType = (EntityActionType) scene.power.getAbility("example_stand_barrage");

            EntityActionInstance punchAction = scene.start("example_stand_punch");
            helper.assertTrue(!punchAction.canBeCancelledInto(heavy),
                    "1.16 queued a press during a punch's windup");
            punchAction.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(punchAction.canBeCancelledInto(heavy) && punchAction.canBeCancelledInto(punch)
                            && !punchAction.refusesOwnAbilityPressedAgain(),
                    "1.16 let any action, another punch too, replace a punch in its recovery");
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            EntityActionInstance finisherAction = scene.start("example_stand_finisher_punch");
            finisherAction.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(finisherAction.canBeCancelledInto(punch) && finisherAction.refusesOwnAbilityPressedAgain(),
                    "1.16 freed a finisher's recovery for any other action and refused its own press");
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            EntityActionInstance heavyAction = scene.start("example_stand_heavy_punch");
            heavyAction.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(!heavyAction.canBeCancelledInto(punch),
                    "1.16 queued a press during a heavy punch's recovery");
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            EntityActionInstance barrage = scene.start("example_stand_barrage");
            barrage.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(barrage.canBeCancelledInto(heavy) && barrage.canBeCancelledInto(finisher)
                            && !barrage.canBeCancelledInto(punch) && barrage.refusesOwnAbilityPressedAgain(),
                    "1.16 let a heavy attack (canFollowUpBarrage) follow a barrage up in its recovery, nothing else");
            scene.owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 100));
            helper.assertTrue(barrage.canBeCancelledInto(punch),
                    "1.16 freed a barrage's recovery while its user had Resolve");
            scene.owner.removeEffect(ModStatusEffects.RESOLVE);
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            int max = 20 + (int) (scene.stand.getDurability() * 5.0D);
            EntityActionInstance held = scene.hold("example_stand_barrage");
            held._tickAction();
            helper.assertTrue(held.getPhase() == ActionPhase.PERFORM,
                    "Setup: the barrage is not performing, phase " + held.getPhase());
            held.setPhase(ActionPhase.PERFORM, max - 1);
            held._tickAction();
            helper.assertTrue(held.getPhase() == ActionPhase.PERFORM,
                    "1.16 kept a barrage held " + (max - 1) + " ticks going, phase " + held.getPhase());
            held._tickAction();
            helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY,
                    "1.16 ended a barrage held for its " + max + " ticks (20 + 5 x durability), phase " + held.getPhase());
            helper.assertTrue(barrageType != null, "Speed King has no barrage");
            helper.succeed();
        }
    }

    // 1.16 099 InitStands :61-64 registered the plain core StandEntityLightAttack, StandEntityMeleeBarrage and
    // StandEntityHeavyAttack: the heavy punch's key gave its finisher variation from half a finisher meter
    // (StandEntityHeavyAttack.getFinisherVariationIfPresent), a punch hurt by the Stand's strength
    // (StandStatFormulas.getLightAttackDamage) and filled the finisher meter, and the punch and barrage summoned only the
    // Stand's arms (MAIN_ARM, ARMS), the heavy punch all of it. The 1.16 barrage went on at 0 stamina; that a player's
    // barrage stops on a tick it cannot pay its 4 stamina is the owner's rule for the core barrage (D-core-90).
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void meleeMovesAreTheCoreAttacksAsIn116(GameTestHelper helper) {
        List<String> wrong = new java.util.ArrayList<>();
        try (Scene scene = new Scene(helper, "SkCoreMelee")) {
            Ability heavy = scene.power.getAbility("example_stand_heavy_punch");
            for (float meter : new float[] {0.0F, 0.5F}) {
                scene.stand.setFinisherMeter(meter);
                scene.power.tick();
                AbilityConditionCheck key = scene.power.updateAvailableMoves().getContextVariationContainer(heavy);
                String expected = meter >= 0.5F ? "example_stand_finisher_punch" : "example_stand_heavy_punch";
                if (key == null || !expected.equals(key.ability.name())) {
                    wrong.add("the heavy punch key at a finisher meter of " + meter + ": 1.16 gave " + expected
                            + ", the port " + (key != null ? key.ability.name() : null));
                }
            }
            scene.stand.setFinisherMeter(0.0F);

            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            scene.extra = cow;
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 3.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            scene.aim(new ActionTarget(cow), cow.getEyePosition());
            float health = cow.getHealth();
            float damage = StandStatFormulas.getLightAttackDamage(scene.stand.getAttackDamage());
            EntityActionInstance punch = scene.start("example_stand_punch");
            punch.setPhaseStart(ActionPhase.PERFORM);
            punch._tickAction();
            if (Math.abs(health - cow.getHealth() - damage) > 1.0E-3F || !(scene.stand.getFinisherMeter() > 0.0F)) {
                wrong.add("a punch at the cow: 1.16 hurt it by getLightAttackDamage(" + scene.stand.getAttackDamage()
                        + ") = " + damage + " and filled the finisher meter; the port took " + (health - cow.getHealth())
                        + ", meter " + scene.stand.getFinisherMeter());
            }
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);

            EntityActionInstance barrage = scene.hold("example_stand_barrage");
            barrage._tickAction();
            helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM, "Setup: the barrage is not performing");
            scene.power.setStamina(0.0F);
            barrage._tickAction();
            if (barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver()) {
                wrong.add("a barrage that could not pay its tick: the owner's core barrage rule D-core-90 (not"
                        + " 1.16, which went on) stops it, the port barrages on");
            }
            AbilityInput.keyRelease(KEY, scene.owner);
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            scene.power.setStamina(scene.power.getMaxStamina());

            Object[][] summons = {
                    { "example_stand_punch", true },
                    { "example_stand_barrage", true },
                    { "example_stand_heavy_punch", false } };
            for (Object[] row : summons) {
                String name = (String) row[0];
                boolean armsOnly = (Boolean) row[1];
                if (scene.power.isSummoned()) {
                    SpeedKingAddon.SPEED_KING.get().forceUnsummon(scene.owner, scene.power);
                }
                scene.power.tick();
                scene.press(name);
                if (!(scene.power.getSummonedStandEntity() instanceof SpeedKingEntity summoned)
                        || summoned.isArmsOnlyMode() != armsOnly) {
                    wrong.add(name + " pressed with Speed King away: 1.16 summoned " + (armsOnly ? "its arms only"
                            : "the whole Stand") + ", the port " + scene.power.getSummonedStandEntity());
                }
                AbilityInput.keyRelease(KEY, scene.owner);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Speed King melee (core attacks): " + wrong);
        helper.succeed();
    }

    // 1.16 StandEntityAction.onClick summoned the Stand for a press with it away by the action's auto-summon mode: 099
    // InitStands :65 example_stand_block = new StandEntityBlock(), whose constructor (StandEntityBlock :17) set ARMS, so
    // the guard brought out only the arms; the pickaxe throw and the heat moves (:66-72) kept the builder's FULL.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void everyMoveSummonsWhatItsDonorBuilderDidAsIn116(GameTestHelper helper) {
        List<String> wrong = new java.util.ArrayList<>();
        try (Scene scene = new Scene(helper, "SkSummonModes")) {
            Object[][] rows = {
                    { "example_stand_block", true },
                    { "example_stand_throw_pickaxe", false },
                    { "speed_king_heat_injection", false },
                    { "speed_king_heat_vision", false },
                    { "speed_king_heat_cook", false },
                    { "speed_king_heat_grab", false },
                    { "speed_king_heat_trap_trigger", false },
                    { "speed_king_heat_trap", false } };
            for (Object[] row : rows) {
                String name = (String) row[0];
                boolean armsOnly = (Boolean) row[1];
                if (scene.power.isSummoned()) {
                    SpeedKingAddon.SPEED_KING.get().forceUnsummon(scene.owner, scene.power);
                }
                scene.power.tick();
                scene.press(name);
                if (!(scene.power.getSummonedStandEntity() instanceof SpeedKingEntity summoned)
                        || summoned.isArmsOnlyMode() != armsOnly) {
                    wrong.add(name + ": 1.16 summoned " + (armsOnly ? "its arms only" : "the whole Stand")
                            + ", the port " + (scene.power.getSummonedStandEntity() instanceof SpeedKingEntity summoned
                                    ? summoned.isArmsOnlyMode() ? "its arms only" : "the whole Stand"
                                    : "nothing"));
                }
                AbilityInput.keyRelease(KEY, scene.owner);
                if (scene.power.getSummonedStandEntity() != null) {
                    LivingComponentAction.getComponent(scene.power.getSummonedStandEntity())
                            .setAction(null, SyncType.NO_SYNC);
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "pressed with Speed King away: " + wrong);
        helper.succeed();
    }

    // 1.16 StandStatFormulas.getLightAttackRecovery
    private static float lightRecovery116(double speed, float finisherMeter) {
        double val = (24 - speed) / 2;
        if (val <= 1) {
            return 1;
        }
        if (val > 4) {
            val = Math.max(val * (1.0F - finisherMeter * 0.4F), 4);
        }
        return (float) Math.ceil(val);
    }

    // 1.16 StandStatFormulas.getHeavyAttackWindup
    private static float heavyWindup116(double speed, float finisherMeter) {
        float f = 40 - (float) speed * 1.25F;
        return Mth.ceil(Mth.lerp(finisherMeter, f * 2 / 3, f / 3));
    }

    // 1.16 StandStatFormulas.getHeavyAttackRecovery
    private static float heavyRecovery116(double speed, float finisher) {
        float max = (40 - (float) speed * 1.25F) * 0.75F;
        return Mth.floor(Mth.lerp(finisher, max, max / 2));
    }

    private static String phases(EntityActionInstance action) {
        return "windup " + action.phasesLength.getFloat(ActionPhase.WINDUP) + ", perform "
                + action.phasesLength.getFloat(ActionPhase.PERFORM) + ", recovery "
                + action.phasesLength.getFloat(ActionPhase.RECOVERY);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final FakePlayer owner;
        final StandPower power;
        final SpeedKingEntity stand;
        net.minecraft.world.entity.Entity extra;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
            helper.getLevel().addNewPlayer(owner);
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(SpeedKingAddon.SPEED_KING.get());
            power.setResolveLevel(4);
            helper.assertTrue(SpeedKingAddon.SPEED_KING.get().summon(owner, power), "summon failed");
            helper.assertTrue(power.getSummonedStandEntity() instanceof SpeedKingEntity,
                    "Setup: the summoned Stand is not Speed King's");
            stand = (SpeedKingEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        }

        void aim(ActionTarget target, Vec3 at) {
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, at);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(target);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
        }

        EntityActionInstance start(String name) {
            Ability type = power.getAbility(name);
            helper.assertTrue(type instanceof EntityActionType, "Speed King has no " + name);
            EntityActionInstance action = ((EntityActionType) type).initActionOnAbilityUse(
                    owner.level(), owner, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action, "Setup: " + name + " did not start");
            return action;
        }

        boolean press(String name) {
            BufferingState press = BufferingState.clickOnly();
            power.getAbility(name).onKeyPress(owner.level(), owner, null, InputMethod.CLICK, 0, press);
            return press.isActionSuccess;
        }

        EntityActionInstance hold(String name) {
            power.tick(); // moves are cached per tick
            AbilityConditionCheck selected = power.updateAvailableMoves()
                    .getContextVariationContainer(power.getAbility(name));
            helper.assertTrue(selected != null, name + " is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "The press of " + name + " was refused: " + selected.conditionCheck.getWarning());
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            helper.assertTrue(press.isActionSuccess && stand.getCurStandAction() != null,
                    "The press of " + name + " did not start the action");
            return stand.getCurStandAction();
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, owner);
            if (power.isSummoned()) {
                SpeedKingAddon.SPEED_KING.get().forceUnsummon(owner, power);
            }
            if (extra != null) {
                extra.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }
}
