package rotp.addons.diverdown.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import org.slf4j.Logger;

@GameTestHolder("rotp_dd_review_fixes")
@PrefixGameTestTemplate(false)
public final class DiverDownReviewGameTests {
    private static final short PROTECTION_KEY = 1;
    private static final float EPSILON = 1.0E-4F;
    private static final float SHORT_BAR = 10.0F;
    private static final float OFF_WALL_BAR = 30.0F;
    private static final int OFF_WALL_TICKS = 5;
    private static final Logger LOGGER = LogUtils.getLogger();

    private DiverDownReviewGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void protectionRejectsRepeatedDamageFeedback(GameTestHelper helper) {
        protectionCase(helper, 2, fixture -> {}, fixture -> {
            var source = helper.getLevel().damageSources().mobAttack(fixture.owner);
            helper.assertTrue(fixture.target.hurt(source, 4.0F), "Initial protected hit was rejected");
            health(helper, fixture, 98.0F, 98.0F);
            helper.assertTrue(!fixture.target.hurt(source, 4.0F), "Equal hit bypassed the target hurt window");
            health(helper, fixture, 98.0F, 98.0F);
            helper.assertTrue(fixture.target.hurt(source, 6.0F), "Larger hurt-window difference was rejected");
            health(helper, fixture, 97.0F, 97.0F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void protectionRejectsShieldBlockedFeedback(GameTestHelper helper) {
        protectionCase(helper, 6, fixture -> {
            fixture.target.setYRot(180.0F);
            fixture.target.setYHeadRot(180.0F);
            fixture.target.setXRot(0.0F);
            fixture.target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
            fixture.target.startUsingItem(InteractionHand.MAIN_HAND);
        }, fixture -> {
            helper.assertTrue(fixture.target.isBlocking(), "The target shield never became active");
            helper.assertTrue(!fixture.target.hurt(
                            helper.getLevel().damageSources().mobAttack(fixture.owner), 8.0F),
                    "Shield fixture did not block the incoming hit");
            health(helper, fixture, 100.0F, 100.0F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void protectionSharesAfterArmor(GameTestHelper helper) {
        protectionCase(helper, 2, fixture -> {
            fixture.target.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
            fixture.target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
        }, fixture -> {
            helper.assertTrue(fixture.target.hurt(
                            helper.getLevel().damageSources().mobAttack(fixture.owner), 8.0F),
                    "Armored protected hit was rejected");
            health(helper, fixture, 98.88F, 98.88F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void protectionPreservesFullAbsorption(GameTestHelper helper) {
        protectionCase(helper, 2, fixture -> fixture.target.setAbsorptionAmount(8.0F), fixture -> {
            helper.assertTrue(fixture.target.hurt(
                            helper.getLevel().damageSources().mobAttack(fixture.owner), 6.0F),
                    "Fully absorbed hit was not accepted");
            health(helper, fixture, 100.0F, 100.0F);
            helper.assertTrue(Math.abs(fixture.target.getAbsorptionAmount() - 2.0F) < EPSILON,
                    "Protection changed the fully absorbed hit's absorption consumption");
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void protectionSharesOnlyPostAbsorptionHealthDamage(GameTestHelper helper) {
        protectionCase(helper, 2, fixture -> fixture.target.setAbsorptionAmount(3.0F), fixture -> {
            helper.assertTrue(fixture.target.hurt(
                            helper.getLevel().damageSources().mobAttack(fixture.owner), 8.0F),
                    "Partially absorbed hit was rejected");
            health(helper, fixture, 97.5F, 97.5F);
            helper.assertTrue(fixture.target.getAbsorptionAmount() == 0.0F,
                    "Protection saved absorption instead of splitting only the remaining health damage");
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void disassemblyRetainsBonusBones(GameTestHelper helper) {
        DisassemblyLootGameTests.disassemblyRollsVictimLootFiveTimesWithLuckAndBones(helper);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void disassemblyExcludesBonusSaddles(GameTestHelper helper) {
        DisassemblyLootGameTests.disassemblyExcludesVanillaRavagerSaddles(helper);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void springLegsAdmitsBelowCompletionCost(GameTestHelper helper) {
        springCase(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void springLegsCommitsAfterWindupDepletion(GameTestHelper helper) {
        springCase(helper, false);
    }

    // 1.16 DiverDownSpringLegs was a StandEntityHeavyAttack: StandEntity.attackTarget played the heavy impact sound
    // after the hit, and only when it landed.
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void springLegsRefusedHitMakesNoImpactSound(GameTestHelper helper) {
        springSoundCase(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void springLegsLandedHitMakesOneImpactSound(GameTestHelper helper) {
        springSoundCase(helper, true);
    }

    // 1.16 Diver Down had no stamina condition: StandEntityTask (and StandAction.onPerform for the steal) paid each
    // cost, the ticking ones every perform tick, with the result ignored, and a short bar only dropped to 0.
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void deformDashDashesOnAShortBar(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.DEFORM_DASH);
    }

    // a heavy attack: the trap is set after the Stand's windup
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void kineticTrapIsSetOnAShortBar(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.KINETIC_TRAP);
    }

    // a heavy attack pressed through the heavy key: the disassembly performs after the Stand's windup
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void disassemblyRunsOnAShortBar(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.DISASSEMBLE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void stealFiresOnAShortBar(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.STEAL);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void wallGlideHoldsOnAnEmptyBar(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.WALL_GLIDE);
    }

    // 1.16 off a wall only stopped the movement (NEGATIVE_CONTINUE_HOLD); the held perform went on and
    // StandEntityTask still took staminaCostTick(3) every tick.
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 30)
    public static void wallGlideHeldOffAWallPaysEachTick(GameTestHelper helper) {
        staminaCase(helper, StaminaCase.WALL_GLIDE_OFF_WALL);
    }

    private enum StaminaCase {
        DEFORM_DASH(DiverDownAddon.DEFORM_DASH_NAME, SHORT_BAR),
        KINETIC_TRAP(DiverDownAddon.KINETIC_TRAP_NAME, SHORT_BAR),
        DISASSEMBLE(DiverDownAddon.DISASSEMBLE_MOB_NAME, SHORT_BAR),
        STEAL(DiverDownAddon.STEAL_ITEM_NAME, SHORT_BAR),
        WALL_GLIDE(DiverDownAddon.WALL_GLIDE_NAME, 0.0F),
        WALL_GLIDE_OFF_WALL(DiverDownAddon.WALL_GLIDE_NAME, 0.0F);

        final String ability;
        final float stamina;

        StaminaCase(String ability, float stamina) {
            this.ability = ability;
            this.stamina = stamina;
        }
    }

    private static void staminaCase(GameTestHelper helper, StaminaCase kind) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> staminaCase(helper, kind, fixture(helper, placement)));
    }

    private static void staminaCase(GameTestHelper helper, StaminaCase kind, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                helper.assertTrue(!fixture.power.isStaminaInfinite(), "Stamina fixture has infinite stamina");
                fixture.owner.setYRot(0.0F);
                fixture.owner.setYHeadRot(0.0F);
                fixture.owner.setXRot(0.0F);
                prepareStaminaCase(helper, kind, fixture);
                fixture.power.setStamina(kind.stamina);
                // 1.16 reached the disassembly only through the heavy key (the deform dash's replaceAction)
                var ability = kind == StaminaCase.DISASSEMBLE
                        ? fixture.power.updateAvailableMoves().getContextVariation(DiverDownAddon.HEAVY_PUNCH)
                        : fixture.power.getAbility(kind.ability);
                helper.assertTrue(kind != StaminaCase.DISASSEMBLE || ability instanceof MobDisassembleAbility,
                        "The heavy key does not hold the disassembly: " + (ability != null ? ability.name() : null));
                helper.assertTrue(ability != null, "Missing registered " + kind.ability);
                var check = ability.checkConditions(fixture.power);
                helper.assertTrue(check.isPositive(), "1.16 had no stamina check: " + kind.ability
                        + " must be admitted on a " + kind.stamina + " bar, got "
                        + (check.getWarning() != null ? check.getWarning().getString() : "a silent refusal"));
                BufferingState buffering = BufferingState.clickOnly();
                ability.onKeyPress(helper.getLevel(), fixture.owner, null,
                        kind == StaminaCase.DEFORM_DASH || kind == StaminaCase.KINETIC_TRAP
                                || kind == StaminaCase.DISASSEMBLE
                                ? InputMethod.CLICK : InputMethod.HOLD,
                        0.0F, buffering);
                // 1.16 Steal Item was a StandAction, performed by the user: it runs in the user's Stand-power slot
                EntityActionInstance action = kind == StaminaCase.STEAL
                        ? LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND)
                        : LivingComponentAction.getCurEntityAction(fixture.stand);
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == ability,
                        kind.ability + " did not start on a " + kind.stamina + " bar");
                watchStaminaAction(helper, kind, fixture, action, 0);
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    private static void prepareStaminaCase(GameTestHelper helper, StaminaCase kind, Fixture fixture) {
        var level = helper.getLevel();
        BlockPos ownerBlock = BlockPos.containing(fixture.owner.position());
        switch (kind) {
            // a wall across the owner's look at eye height, before the target
            case KINETIC_TRAP -> {
                for (int dx = -1; dx <= 0; dx++) {
                    placeBlock(helper, fixture, ownerBlock.offset(dx, 1, 2));
                }
                fixture.trapsBefore = KineticTrapEntity.countOwned(level, fixture.owner.getUUID());
            }
            case DISASSEMBLE -> {
                fixture.stand.setFinisherMeter(1.0F);
                fixture.target.setHealth(1.0F);
                fixture.lootArea = fixture.target.getBoundingBox().inflate(2.0D);
                helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target)
                                && MobDisassembleAbility.qualifies(fixture.stand),
                        "The disassembly victim did not qualify");
            }
            case STEAL -> {
                Villager villager = EntityType.VILLAGER.create(level);
                helper.assertTrue(villager != null, "Missing steal target");
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.setPos(fixture.owner.position().add(0.0D, 0.0D, 2.0D));
                level.addFreshEntity(villager);
                fixture.extra = villager;
            }
            // the owner's front face 0.005 short of a wall it glides on
            case WALL_GLIDE, WALL_GLIDE_OFF_WALL -> {
                BlockPos wall = ownerBlock.offset(0, 0, 1);
                for (int dx = -1; dx <= 0; dx++) {
                    placeBlock(helper, fixture, wall.offset(dx, 0, 0));
                    placeBlock(helper, fixture, wall.offset(dx, 1, 0));
                }
                fixture.owner.setPos(fixture.owner.getX(), fixture.owner.getY(),
                        wall.getZ() - fixture.owner.getBbWidth() / 2.0D - 0.005D);
            }
            default -> {
            }
        }
    }

    private static void placeBlock(GameTestHelper helper, Fixture fixture, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        fixture.placed.add(pos);
    }

    private static void watchStaminaAction(GameTestHelper helper, StaminaCase kind, Fixture fixture,
            EntityActionInstance action, int waited) {
        helper.runAfterDelay(1, () -> {
            try {
                boolean ready = switch (kind) {
                    // the dash pays as its perform begins and the perform lasts at least two ticks
                    case DEFORM_DASH -> true;
                    // the trap is set and paid for in the perform tick after the windup
                    case KINETIC_TRAP -> KineticTrapEntity.countOwned(helper.getLevel(), fixture.owner.getUUID())
                            > fixture.trapsBefore;
                    // the heavy windup is over and Diver Down has left the target it disassembled
                    case DISASSEMBLE -> !fixture.stand.isEmbedded();
                    // the 15-tick charge fires, the steal pays and recovers
                    case STEAL -> action.getPhase() == ActionPhase.RECOVERY || action.isOver();
                    case WALL_GLIDE -> waited >= 3;
                    case WALL_GLIDE_OFF_WALL -> {
                        if (waited == 0) {
                            for (BlockPos pos : fixture.placed) {
                                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                            }
                            // straight up into open air: a step back towards the origin could take the Stand into
                            // a chunk without the fixture's ticket; the flag is only refreshed by a move
                            fixture.owner.setPos(fixture.owner.position().add(0.0D, 3.0D, 0.0D));
                            fixture.owner.horizontalCollision = false;
                            fixture.power.setStamina(OFF_WALL_BAR);
                            fixture.firstPhaseTick = action.getPhaseTick();
                            fixture.lastPhaseTick = fixture.firstPhaseTick;
                            fixture.lastStamina = OFF_WALL_BAR;
                        }
                        else {
                            offWallTick(helper, fixture, action, waited);
                        }
                        yield waited >= OFF_WALL_TICKS;
                    }
                    default -> true;
                };
                if (!ready) {
                    helper.assertTrue(waited < 40, kind.ability + " never fired, phase " + action.getPhase());
                    watchStaminaAction(helper, kind, fixture, action, waited + 1);
                    return;
                }
                switch (kind) {
                    case DEFORM_DASH -> helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                            "The dash on a short bar must perform, not recover: " + action.getPhase());
                    case STEAL -> helper.assertTrue(fixture.power.isAbilityOnCooldown(kind.ability),
                            "The fired steal set no cooldown");
                    case DISASSEMBLE -> helper.assertTrue(fixture.target.getHealth() < 1.0F,
                            "The disassembly did not hit on a short bar; target health " + fixture.target.getHealth());
                    case KINETIC_TRAP -> helper.assertTrue(
                            KineticTrapEntity.countOwned(helper.getLevel(), fixture.owner.getUUID())
                                    == fixture.trapsBefore + 1,
                            "The kinetic trap was not set once on a short bar");
                    case WALL_GLIDE -> helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && !action.isOver(),
                            "1.16 ignored the 3 a tick glide cost: the hold must go on on an empty bar, phase "
                                    + action.getPhase());
                    case WALL_GLIDE_OFF_WALL -> {
                        helper.assertTrue(!fixture.owner.horizontalCollision && !helper.getLevel().getBlockCollisions(
                                        fixture.owner, fixture.owner.getBoundingBox().inflate(0.01D, 0.0D, 0.01D))
                                        .iterator().hasNext(),
                                "Fixture: the owner still touches a wall: horizontalCollision="
                                        + fixture.owner.horizontalCollision + " blocks=" + StreamSupport.stream(
                                                helper.getLevel().getBlockCollisions(fixture.owner, fixture.owner
                                                        .getBoundingBox().inflate(0.01D, 0.0D, 0.01D)).spliterator(), false)
                                                .map(shape -> shape.bounds().toString()).toList());
                        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && !action.isOver(),
                                "1.16 kept the glide held off a wall, phase " + action.getPhase());
                        int performTicks = Math.round(action.getPhaseTick() - fixture.firstPhaseTick);
                        helper.assertTrue(performTicks == OFF_WALL_TICKS,
                                "Fixture: the glide must perform once per server tick off the wall, "
                                        + performTicks + " PERFORM ticks in " + OFF_WALL_TICKS);
                        // The ticking cost is applied on the user's next power tick, so the last tick's 3 may
                        // still be pending; every earlier tick's 3 must already be off the bar.
                        float paid = OFF_WALL_BAR - fixture.power.getStamina();
                        float regen = fixture.power.getStaminaTickGain() * OFF_WALL_TICKS;
                        float cost = WallGlideAbility.STAMINA_COST_PER_TICK;
                        boolean allApplied = Math.abs(paid - (cost * performTicks - regen)) < EPSILON;
                        boolean lastPending = Math.abs(paid - (cost * (performTicks - 1) - regen)) < EPSILON;
                        helper.assertTrue(allApplied || lastPending,
                                "1.16 took the glide's 3 on each of the " + performTicks
                                        + " PERFORM ticks held off a wall: " + OFF_WALL_BAR + " -> "
                                        + fixture.power.getStamina() + " (paid " + paid + ", regen " + regen + ")");
                        LOGGER.info("R769_DD_STAMINA_PASS wall_glide_off_wall perform_ticks={} paid={} drops={}",
                                performTicks, paid, fixture.offWallDrops);
                        helper.succeed();
                        cleanup(helper, fixture);
                        return;
                    }
                    default -> {
                    }
                }
                assertDrained(helper, kind, fixture);
                helper.succeed();
                cleanup(helper, fixture);
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    // One server tick off the wall: exactly one PERFORM tick, and from the second on the bar falls by exactly the
    // previous tick's 3 (the ticking cost reaches the bar on the user's power tick, one tick late at most).
    private static void offWallTick(GameTestHelper helper, Fixture fixture, EntityActionInstance action, int waited) {
        assertTicks(helper, fixture);
        float phaseTick = action.getPhaseTick();
        float stamina = fixture.power.getStamina();
        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && !action.isOver()
                        && Math.abs(phaseTick - fixture.lastPhaseTick - 1.0F) < EPSILON,
                "Fixture: the glide must stay in PERFORM and tick once a server tick off the wall, phase "
                        + action.getPhase() + " phase tick " + fixture.lastPhaseTick + " -> " + phaseTick);
        float drop = fixture.lastStamina - stamina;
        fixture.offWallDrops.add(drop);
        if (waited >= 2) {
            float expected = WallGlideAbility.STAMINA_COST_PER_TICK - fixture.power.getStaminaTickGain();
            helper.assertTrue(Math.abs(drop - expected) < EPSILON,
                    "1.16 took the glide's 3 on every PERFORM tick held off a wall: off-wall tick " + waited
                            + " took " + drop + " (" + fixture.lastStamina + " -> " + stamina + "), expected "
                            + expected + ", drops so far " + fixture.offWallDrops);
        }
        fixture.lastPhaseTick = phaseTick;
        fixture.lastStamina = stamina;
    }

    private static void assertDrained(GameTestHelper helper, StaminaCase kind, Fixture fixture) {
        // The steal is the user's action (a 1.16 StandAction), so the idle Stand's regen (1.16
        // StandPower.tickStamina) may already have refilled one tick of it when its action is seen over.
        float allowed = kind == StaminaCase.STEAL ? fixture.power.getStaminaTickGain() + EPSILON : 0.0F;
        helper.assertTrue(fixture.power.getStamina() <= allowed, "The " + kind.ability + " cost drains a "
                + kind.stamina + " bar to 0, the bar holds " + fixture.power.getStamina());
        LOGGER.info("R769_DD_STAMINA_PASS {}", kind.ability);
    }

    private static void protectionCase(GameTestHelper helper, int settleTicks,
            Consumer<Fixture> prepare, Consumer<Fixture> assertions) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> protectionCase(helper, settleTicks, prepare, assertions, fixture(helper, placement)));
    }

    private static void protectionCase(GameTestHelper helper, int settleTicks,
            Consumer<Fixture> prepare, Consumer<Fixture> assertions, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target), "Could not embed the protecting Stand");
                var ability = fixture.power.getAbility(DiverDownAddon.INSIDE_PROTECTION_NAME);
                helper.assertTrue(ability instanceof InsideProtectionAbility
                                && ability.checkConditions(fixture.power).isPositive(),
                        "Registered protection refused the fixture");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(PROTECTION_KEY, ability, fixture.owner, null,
                        InputMethod.HOLD, 0.0F, buffering, ability.abilityId);
                helper.assertTrue(buffering.isActionSuccess, "Public held protection entry failed");
                prepare.accept(fixture);
                helper.runAfterDelay(settleTicks, () -> {
                    try {
                        var action = LivingComponentAction.getCurEntityAction(fixture.stand);
                        helper.assertTrue(action instanceof InsideProtectionAbility.ProtectionAction
                                        && action.getPhase() == ActionPhase.PERFORM
                                        && AbilityInput.isHeldByKey(fixture.owner, action)
                                        && fixture.stand.getEmbeddedTarget() == fixture.target,
                                "Protection did not remain held in its real perform phase");
                        assertions.accept(fixture);
                        helper.succeed();
                    } finally {
                        cleanup(helper, fixture);
                    }
                });
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    private static void springCase(GameTestHelper helper, boolean startLow) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> springCase(helper, startLow, fixture(helper, placement)));
    }

    private static void springCase(GameTestHelper helper, boolean startLow, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                helper.assertTrue(!fixture.power.isStaminaInfinite(), "Spring fixture has infinite stamina");
                helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target), "Could not embed Spring caster");
                if (startLow) {
                    fixture.power.setStamina(0.0F);
                }
                var ability = fixture.power.getAbility(DiverDownAddon.SPRING_LEGS_NAME);
                helper.assertTrue(ability instanceof SpringLegsAbility
                                && ability.checkConditions(fixture.power).isPositive(),
                        "Production Spring conditions added an admission minimum");
                fixture.damageObserver = event -> {
                    if (event.getEntity() == fixture.target
                            && event.getSource().getDirectEntity() == fixture.stand) {
                        fixture.hitStamina = fixture.power.getStamina();
                        fixture.hitTick = helper.getLevel().getGameTime();
                        fixture.effectAtHit = fixture.target.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        LivingDamageEvent.Post.class, fixture.damageObserver);
                BufferingState buffering = BufferingState.clickOnly();
                ability.onKeyPress(helper.getLevel(), fixture.owner, null,
                        InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(fixture.stand);
                helper.assertTrue(buffering.isActionSuccess
                                && action instanceof SpringLegsAbility.SpringLegsAction
                                && action.getPhase() == ActionPhase.WINDUP,
                        "Public Spring cast did not enter the real windup");
                int windup = (int) Math.ceil(action.getPhaseTicksLeft());
                helper.assertTrue(windup > 2 && windup < 45, "Unexpected natural Spring windup");
                helper.runAfterDelay(windup - 2, () -> {
                    try {
                        helper.assertTrue(action.getPhase() == ActionPhase.WINDUP
                                        && !fixture.target.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT),
                                "Spring performed before the low-stamina windup control");
                        float regen = fixture.power.getStaminaTickGain();
                        helper.assertTrue(regen >= 0.0F && 25.0F + regen * 6.0F < SpringLegsAbility.STAMINA_COST,
                                "Fixture regeneration would hide the below-cost completion");
                        fixture.power.setStamina(25.0F);
                        helper.runAfterDelay(5, () -> {
                            try {
                                helper.assertTrue(fixture.effectAtHit && fixture.hitTick >= 0
                                                && fixture.hitStamina > regen + EPSILON
                                                && fixture.hitStamina < SpringLegsAbility.STAMINA_COST,
                                        "Spring did not apply its effect and real hit before low-stamina payment");
                                helper.assertTrue(fixture.target.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT)
                                                && !fixture.stand.isEmbedded() && fixture.target.getHealth() < 100.0F,
                                        "Below-cost Spring lost its effect, hit, or embedding cleanup");
                                long ticksAfterHit = helper.getLevel().getGameTime() - fixture.hitTick;
                                helper.assertTrue(fixture.power.getStamina() <= (ticksAfterHit + 1) * regen + EPSILON,
                                        "Spring did not clamp its completion debit before natural regeneration");
                                helper.succeed();
                            } finally {
                                cleanup(helper, fixture);
                            }
                        });
                    } catch (Throwable error) {
                        cleanup(helper, fixture);
                        throw error;
                    }
                });
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    private static void springSoundCase(GameTestHelper helper, boolean lands) {
        GameTestPlacement.whenTicking(helper, 3,
                placement -> springSoundCase(helper, lands, fixture(helper, placement)));
    }

    private static void springSoundCase(GameTestHelper helper, boolean lands, Fixture fixture) {
        helper.runAfterDelay(fixture.stand.summonLockTicks + 1, () -> {
            try {
                assertTicks(helper, fixture);
                helper.assertTrue(fixture.stand.setEmbeddedTarget(fixture.target), "Could not embed Spring caster");
                var ability = fixture.power.getAbility(DiverDownAddon.SPRING_LEGS_NAME);
                helper.assertTrue(ability instanceof SpringLegsAbility
                                && ability.checkConditions(fixture.power).isPositive(),
                        "Registered Spring Legs refused the embedded fixture");
                BufferingState buffering = BufferingState.clickOnly();
                ability.onKeyPress(helper.getLevel(), fixture.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(fixture.stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof SpringLegsAbility.SpringLegsAction,
                        "Public Spring cast did not start");
                int[] sounds = {0};
                fixture.soundObserver = event -> {
                    if (event.getLevel() == helper.getLevel() && event.getSound() != null
                            && event.getSound().value() == DiverDownAddon.DIVER_DOWN_PUNCH_HEAVY.get()) {
                        sounds[0]++;
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        PlayLevelSoundEvent.AtPosition.class, fixture.soundObserver);
                if (!lands) {
                    // refused by the target's own damage handling after Diver Down's authority checks passed
                    fixture.damageBlocker = event -> {
                        if (event.getEntity() == fixture.target) {
                            event.setCanceled(true);
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                            LivingIncomingDamageEvent.class, fixture.damageBlocker);
                }
                action.actionPerformStart();
                helper.assertTrue(fixture.target.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT)
                                && !fixture.stand.isEmbedded(),
                        "Fixture: Spring Legs did not perform on the embedded target");
                if (lands) {
                    helper.assertTrue(fixture.target.getHealth() < 100.0F && sounds[0] == 1,
                            "A landed Spring Legs hit played " + sounds[0] + " impact sounds, target health "
                                    + fixture.target.getHealth());
                }
                else {
                    helper.assertTrue(fixture.target.getHealth() == 100.0F,
                            "Fixture: the refused hit hurt the target");
                    helper.assertTrue(sounds[0] == 0,
                            "1.16 played no Spring Legs impact sound for a hit that did not land; the port played "
                                    + sounds[0]);
                }
                helper.succeed();
            } finally {
                cleanup(helper, fixture);
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper, GameTestPlacement placement) {
        Cow owner = EntityType.COW.create(helper.getLevel());
        Cow target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(owner != null && target != null, "Missing Diver Down review fixtures");
        owner.setNoAi(true);
        owner.setNoGravity(true);
        target.setNoAi(true);
        target.setNoGravity(true);
        owner.setPos(placement.owner);
        target.setPos(placement.target);
        owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(128.0D);
        owner.setHealth(100.0F);
        target.setHealth(100.0F);
        helper.getLevel().addFreshEntity(owner);
        helper.getLevel().addFreshEntity(target);
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power), "Could not summon review caster");
            var stand = (DiverDownStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Summon did not yield the actual Diver Down controller");
            return new Fixture(owner, target, stand, power);
        } catch (Throwable error) {
            cleanup(helper, new Fixture(owner, target, null, power));
            throw error;
        }
    }

    private static void assertTicks(GameTestHelper helper, Fixture fixture) {
        GameTestPlacement.assertTicks(helper, "owner", fixture.owner);
        GameTestPlacement.assertTicks(helper, "target", fixture.target);
        GameTestPlacement.assertTicks(helper, "Stand", fixture.stand);
    }

    private static void health(GameTestHelper helper, Fixture fixture, float owner, float target) {
        helper.assertTrue(Math.abs(fixture.owner.getHealth() - owner) < EPSILON
                        && Math.abs(fixture.target.getHealth() - target) < EPSILON,
                "Unexpected protected health: owner=" + fixture.owner.getHealth()
                        + ", target=" + fixture.target.getHealth());
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        if (fixture.damageObserver != null) {
            NeoForge.EVENT_BUS.unregister(fixture.damageObserver);
            fixture.damageObserver = null;
        }
        if (fixture.damageBlocker != null) {
            NeoForge.EVENT_BUS.unregister(fixture.damageBlocker);
            fixture.damageBlocker = null;
        }
        if (fixture.soundObserver != null) {
            NeoForge.EVENT_BUS.unregister(fixture.soundObserver);
            fixture.soundObserver = null;
        }
        AbilityInput.keyRelease(PROTECTION_KEY, fixture.owner);
        for (BlockPos pos : fixture.placed) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        fixture.placed.clear();
        if (fixture.extra != null) {
            fixture.extra.discard();
        }
        if (fixture.lootArea != null) {
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.lootArea)) {
                item.discard();
            }
        }
        fixture.target.stopUsingItem();
        DiverDownAuthority.clearOwnedBy(helper.getLevel().getServer(), fixture.owner.getUUID());
        if (fixture.power.hasPower()) {
            fixture.power.getPowerType().forceUnsummon(fixture.owner, fixture.power);
        }
        fixture.target.discard();
        fixture.owner.discard();
    }

    private static final class Fixture {
        final Cow owner;
        final Cow target;
        final DiverDownStandEntity stand;
        final StandPower power;
        Consumer<LivingDamageEvent.Post> damageObserver;
        Consumer<PlayLevelSoundEvent.AtPosition> soundObserver;
        Consumer<LivingIncomingDamageEvent> damageBlocker;
        final List<BlockPos> placed = new ArrayList<>();
        Entity extra;
        AABB lootArea;
        float hitStamina = Float.NaN;
        long hitTick = -1;
        boolean effectAtHit;
        float firstPhaseTick;
        float lastPhaseTick;
        float lastStamina;
        final List<Float> offWallDrops = new ArrayList<>();
        int trapsBefore;

        Fixture(Cow owner, Cow target, DiverDownStandEntity stand, StandPower power) {
            this.owner = owner;
            this.target = target;
            this.stand = stand;
            this.power = power;
        }
    }
}
