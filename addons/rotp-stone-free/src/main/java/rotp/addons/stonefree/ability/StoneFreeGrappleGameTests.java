package rotp.addons.stonefree.ability;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.ability.StoneFreeBarrierGameTests.Fixture;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity.Kind;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

/** Every string here flies straight up, clear of the neighbouring test plots. */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeGrappleGameTests {
    private static final TicketType<ChunkPos> SITE_TICKET =
            TicketType.create("rotp_sf_grapple_site", Comparator.comparingLong(ChunkPos::toLong));

    private StoneFreeGrappleGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrapplePullsAtDonorSpeedAndLetsGoWithinTwoBlocks(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            PolarBear target = target(helper, fixture, place.add(0.0D, 10.0D, 0.0D));
            await(helper, fixture, Kind.STAND_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        target.setDeltaMovement(Vec3.ZERO);
                        string.tick();
                        Vec3 unitPull = stand.position().subtract(target.position()).normalize();
                        helper.assertTrue(string.isAlive()
                                        && target.getDeltaMovement().distanceTo(unitPull) < 1.0E-6D,
                                "The Stand's grapple did not pull its catch at 1.0 block a tick: motion="
                                        + target.getDeltaMovement() + " (speed "
                                        + target.getDeltaMovement().length() + "), expected " + unitPull);
                        target.setPos(stand.position().add(0.0D, 2.5D, 0.0D));
                        string.tick();
                        helper.assertTrue(string.isAlive()
                                        && Math.abs(target.getDeltaMovement().length() - 1.0D) < 1.0E-6D,
                                "The Stand's grapple stopped pulling a catch still 2.5 blocks away");
                        target.setPos(stand.position().add(0.0D, 1.9D, 0.0D));
                        string.tick();
                        helper.assertTrue(!string.isAlive(),
                                "The Stand's grapple kept its catch after pulling it within two blocks");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 PowerBaseImpl.checkTarget judged the pressed target (core K5-26 C asks takesPressedTarget): the bind
    // (StoneFreeStringBind.standKeepsTarget true, 017 :89-91) kept a mob the Stand may attack and a block, the barrier
    // (TargetRequirement BLOCK, StoneFreeUserBarrier :76-78) only a block; the extended punch and
    // stone_free_grapple_entity kept none (standKeepsTarget false; StoneFreeGrapple :58-60 only as a shift variation),
    // nor did the user's strings, StandActions with no Stand task.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void pressedTargetsFollowTheDonorsTargetChecks(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, true);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            PolarBear bear = target(helper, fixture, stand.position().add(0.0D, 0.0D, 3.0D));
            helper.assertTrue(stand.canAttack(bear), "Setup: Stone Free may not attack the bear");
            ActionTarget atBear = new ActionTarget(bear);
            ActionTarget atBlock = new ActionTarget(fixture.first, Direction.UP);
            String[][] rows = {
                    {StoneFreeAddon.EXTENDED_PUNCH_NAME, "false", "false"},
                    {StoneFreeAddon.GRAPPLE_ENTITY_NAME, "false", "false"},
                    {StoneFreeAddon.USER_STRING_ATTACK_NAME, "false", "false"},
                    {StoneFreeAddon.USER_STRING_PICK_NAME, "false", "false"},
                    {StoneFreeAddon.STRING_BIND_NAME, "true", "true"},
                    {StoneFreeAddon.USER_BARRIER_NAME, "false", "true"}};
            for (String[] row : rows) {
                Ability ability = fixture.power.getAbility(row[0]);
                helper.assertTrue(ability instanceof StandEntityAbility,
                        "Setup: Stone Free has no Stand ability " + row[0]);
                StandEntityAbility standAbility = (StandEntityAbility) ability;
                boolean entity = standAbility.takesPressedTarget(stand, atBear);
                boolean block = standAbility.takesPressedTarget(stand, atBlock);
                helper.assertTrue(entity == Boolean.parseBoolean(row[1]) && block == Boolean.parseBoolean(row[2]),
                        "1.16's target checks for " + row[0] + ": mob " + row[1] + ", block " + row[2]
                                + "; the port took mob " + entity + ", block " + block);
            }
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // 1.16 DamagingEntity.onHitEntity after SFGrapplingStringEntity.hurtTarget returned true.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrappleMarksItsCatchAsHurtByTheUserWithoutHurtingIt(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            PolarBear target = target(helper, fixture, place.add(0.0D, 10.0D, 0.0D));
            await(helper, fixture, Kind.STAND_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        helper.assertTrue(target.getLastHurtByMob() == fixture.owner,
                                "The Stand's grapple did not mark its catch as last hurt by the Stand's user:"
                                        + " lastHurtByMob=" + target.getLastHurtByMob());
                        helper.assertTrue(target.getHealth() == target.getMaxHealth() && target.hurtTime == 0,
                                "The Stand's grapple hurt what it caught: health " + target.getHealth() + " of "
                                        + target.getMaxHealth() + ", hurtTime " + target.hurtTime);
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 OwnerBoundProjectileEntity.dragTarget pulled the catch's root vehicle. A boat is not living, so the
    // string passes it and catches its rider.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrapplePullsTheVehicleItsCatchRides(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            Boat boat = EntityType.BOAT.create(helper.getLevel());
            helper.assertTrue(boat != null, "Missing vehicle fixture");
            boat.setNoGravity(true);
            boat.setPos(place.add(0.0D, 10.0D, 0.0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(boat), "Could not spawn the vehicle");
            fixture.extras.add(boat);
            PolarBear rider = target(helper, fixture, boat.position());
            helper.assertTrue(rider.startRiding(boat, true) && rider.getRootVehicle() == boat,
                    "Could not seat the rider in its vehicle");
            await(helper, fixture, Kind.STAND_GRAPPLE, 12, string -> string.getEntityAttachedTo() == rider,
                    string -> {
                        helper.assertTrue(rider.getRootVehicle() == boat, "The rider left its vehicle");
                        boat.setDeltaMovement(Vec3.ZERO);
                        rider.setDeltaMovement(Vec3.ZERO);
                        string.tick();
                        Vec3 unitPull = stand.position().subtract(rider.position()).normalize();
                        helper.assertTrue(string.isAlive()
                                        && boat.getDeltaMovement().distanceTo(unitPull) < 1.0E-6D,
                                "The Stand's grapple did not pull the vehicle its catch rides: vehicle motion="
                                        + boat.getDeltaMovement() + ", rider motion=" + rider.getDeltaMovement()
                                        + ", expected " + unitPull);
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 OwnerBoundProjectileEntity.doDragEntity: an effect that resets motion every tick would undo a pull,
    // so such a catch is moved outright.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrappleMovesAnImmobilisedCatchInsteadOfSettingItsMotion(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            PolarBear target = target(helper, fixture, place.add(0.0D, 10.0D, 0.0D));
            await(helper, fixture, Kind.STAND_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        helper.assertTrue(target.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, 200))
                                        && target.hasEffect(ModStatusEffects.IMMOBILIZE),
                                "Could not immobilise the catch");
                        target.setDeltaMovement(Vec3.ZERO);
                        Vec3 before = target.position();
                        Vec3 unitPull = stand.position().subtract(before).normalize();
                        string.tick();
                        helper.assertTrue(string.isAlive()
                                        && target.position().distanceTo(before.add(unitPull)) < 1.0E-6D
                                        && target.getDeltaMovement().lengthSqr() == 0.0D,
                                "An immobilised catch was not moved one block towards the Stand: it moved "
                                        + target.position().subtract(before) + " with motion "
                                        + target.getDeltaMovement() + ", expected a move of " + unitPull
                                        + " and no motion");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userEntityGrapplePullsUserToTheStringsEndOnItsCatch(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = target(helper, fixture, fixture.owner.position().add(0.0D, 10.0D, 0.0D));
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME);
            await(helper, fixture, Kind.USER_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        Vec3 user = fixture.owner.position();
                        // The catch's feet are level-diagonal from the user; its middle sits 0.7 higher. West of
                        // the user it stays on the plot's own forced chunk (the fixture's column is at most 13
                        // blocks into it); to the east it reached the next chunk, which the last plot of a row
                        // has no neighbour to force, and the string, its end off the entity-ticking area, was
                        // dropped before it pulled.
                        target.setPos(user.add(-2.0D, 2.0D, 0.0D));
                        assertCatchOnTickingChunk(helper, target);
                        fixture.owner.setDeltaMovement(Vec3.ZERO);
                        string.tick();
                        Vec3 stringEnd = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
                        Vec3 pull = stringEnd.subtract(user).normalize().scale(1.5D);
                        helper.assertTrue(string.isAlive()
                                        && fixture.owner.getDeltaMovement().distanceTo(pull) < 1.0E-6D,
                                "The user was not pulled at 1.5 towards the string's end at its catch's middle:"
                                        + " motion=" + fixture.owner.getDeltaMovement() + ", expected " + pull
                                        + ", towards the feet it would be "
                                        + target.position().subtract(user).normalize().scale(1.5D));
                        helper.assertTrue(target.getLastHurtByMob() == null,
                                "The user's own grapple marked its catch as hurt by the user; in 1.16 only a"
                                        + " string a Stand threw did");
                        // Feet 2.07 apart, the string's end 1.95 from the user.
                        target.setPos(user.add(-1.95D, -0.7D, 0.0D));
                        assertCatchOnTickingChunk(helper, target);
                        string.tick();
                        helper.assertTrue(!string.isAlive(),
                                "The user's grapple held on with its end on the catch within two blocks of the user");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // The port already had this half of the donor rule; it stays pinned beside the half above.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userEntityGrappleLetsGoOnceFeetAreWithinTwoBlocks(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = target(helper, fixture, fixture.owner.position().add(0.0D, 10.0D, 0.0D));
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME);
            await(helper, fixture, Kind.USER_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        // Feet 1.5 apart; the string's end on the catch's middle is 2.2 from the user.
                        target.setPos(fixture.owner.position().add(0.0D, 1.5D, 0.0D));
                        string.tick();
                        helper.assertTrue(!string.isAlive(),
                                "The user's grapple held on with its catch standing within two blocks of the user");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 140)
    public static void looseGrappleFliesFourBlocksATickForFortyTicks(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME);
            watchFlight(helper, fixture, new Flight(), 60);
        });
    }

    private static void watchFlight(GameTestHelper helper, Fixture fixture, Flight flight, int ticksLeft) {
        StoneFreeStringEntity string = find(helper, fixture, Kind.USER_GRAPPLE);
        // A string that has not ticked yet still sits at its launch point.
        if (string != null && string.tickCount > 0) {
            flight.seen = true;
            flight.lastAge = string.tickCount;
            flight.lastReach = string.position().distanceTo(string.getOriginPoint(1.0F));
            helper.assertTrue(Math.abs(flight.lastReach - 4.0D * string.tickCount) < 0.01D,
                    "A loose grapple " + string.tickCount + " ticks old reached " + flight.lastReach
                            + " blocks instead of 4 a tick");
        }
        else if (string == null && flight.seen) {
            helper.assertTrue(flight.lastAge == 40 && Math.abs(flight.lastReach - 160.0D) < 0.01D,
                    "A loose grapple was last seen " + flight.lastAge + " ticks old, " + flight.lastReach
                            + " blocks out; the donor lets it fly 40 ticks, 160 blocks");
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The loose grapple never appeared or never ended: seen=" + flight.seen
                + ", last age " + flight.lastAge);
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchFlight(helper, fixture, flight, ticksLeft - 1));
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userGrappleAnchorsOnABlockBeyondTwentyFourBlocks(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        BlockPos anchor = anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            await(helper, fixture, Kind.USER_GRAPPLE, 16, StoneFreeStringEntity::isAttachedToBlock, string -> {
                fixture.owner.setDeltaMovement(Vec3.ZERO);
                string.tick();
                Vec3 pull = Vec3.atCenterOf(anchor).subtract(fixture.owner.position()).normalize().scale(2.0D);
                helper.assertTrue(string.isAlive() && fixture.owner.getDeltaMovement().distanceTo(pull) < 1.0E-6D,
                        "The user was not pulled at 2.0 towards a block anchor 34 blocks up: motion="
                                + fixture.owner.getDeltaMovement() + ", expected " + pull);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // 1.16 InitStands:169-170: the user's grapples have no hold type. One press throws a string that stays out
    // by itself and takes 1 stamina a tick (SFUGrapplingStringEntity.tick), whatever is left of it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userGrappleStaysOutAfterOnePressAndTakesOneStaminaATick(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                StoneFreeStringEntity string = find(helper, fixture, Kind.USER_GRAPPLE);
                helper.assertTrue(string != null,
                        "The user's grapple string was gone 4 ticks after its key was pressed and released");
                fixture.power.setStamina(fixture.power.getMaxStamina() * 0.5F);
                float before = fixture.power.getStamina();
                StoneFreeBarrierGameTests.later(helper, fixture, 10, () -> {
                    helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) == string,
                            "The user's grapple string did not stay out 14 ticks after its one press");
                    float taken = before - fixture.power.getStamina();
                    helper.assertTrue(Math.abs(taken - 10.0F) < 0.01F,
                            "A grapple string out for 10 ticks took " + taken + " stamina instead of 1 a tick: "
                                    + before + " -> " + fixture.power.getStamina());
                    fixture.power.setStamina(0.5F);
                    StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                        helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) == string
                                        && fixture.power.getStamina() == 0.0F,
                                "A grapple string its user could no longer pay for did not stay out on empty"
                                        + " stamina as in 1.16: string "
                                        + find(helper, fixture, Kind.USER_GRAPPLE) + ", stamina "
                                        + fixture.power.getStamina());
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
                });
            });
        });
    }

    // 1.16 StoneFreeUserGrapple.replaceAction and StringAction.checkSpecificConditions: with a string out the
    // grapple's slot is Recover String, no second string can be thrown, and holding Recover String ends the first.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void grappleSlotRecoversTheStringThatIsOutInsteadOfThrowingASecond(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Ability grapple = fixture.power.getAbility(StoneFreeAddon.USER_GRAPPLE_NAME);
            Ability recover = fixture.power.getAbility(StoneFreeAddon.USER_RECOVER_STRING_NAME);
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) != null,
                        "The user's grapple string was gone 4 ticks after its key was pressed and released");
                AvailableAbilities moves = fixture.power.updateAvailableMoves();
                Ability slot = moves.getContextVariation(StoneFreeAddon.USER_GRAPPLE_NAME);
                Ability shiftSlot = moves.getContextVariation(StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME);
                helper.assertTrue(recover != null && slot == recover && shiftSlot == recover,
                        "With a grapple string out its slot did not become Recover String: slot="
                                + name(slot) + ", Shift slot=" + name(shiftSlot));
                helper.assertTrue(!grapple.checkConditions(fixture.power).isPositive(),
                        "A second grapple could be thrown while the first string was still out");
                helper.assertTrue(recover.checkConditions(fixture.power).isPositive(),
                        "Recover String refused a user with a string out");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, recover, fixture.owner, null,
                        InputMethod.HOLD, 0.0F, buffering, grapple.abilityId);
                helper.assertTrue(buffering.isActionSuccess, "Public held Recover String did not start");
                StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                    helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) == null,
                            "Holding Recover String did not take the grapple string back");
                    AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                    StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                        Ability slotAfter = fixture.power.updateAvailableMoves()
                                .getContextVariation(StoneFreeAddon.USER_GRAPPLE_NAME);
                        helper.assertTrue(slotAfter == grapple && grapple.checkConditions(fixture.power).isPositive(),
                                "The grapple's slot did not come back once the string was recovered: slot="
                                        + name(slotAfter));
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
                });
            });
        });
    }

    // 1.16 SFUGrapplingStringEntity:71 dropped the string on a tick that saw Recover String held, and the 1.16
    // client ended a hold no sooner than the client tick after its press (InputHandler:336 and 741-760): the
    // briefest press took the string back. Here the release may reach the server before the action's first tick.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void briefRecoverStringPressTakesTheStringBack(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Ability grapple = fixture.power.getAbility(StoneFreeAddon.USER_GRAPPLE_NAME);
            Ability recover = fixture.power.getAbility(StoneFreeAddon.USER_RECOVER_STRING_NAME);
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) != null
                                && recover != null && recover.checkConditions(fixture.power).isPositive(),
                        "Setup: no grapple string out for Recover String to take back");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, recover, fixture.owner, null,
                        InputMethod.HOLD, 0.0F, buffering, grapple.abilityId);
                helper.assertTrue(buffering.isActionSuccess, "Public Recover String press did not start");
                AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                    helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) == null,
                            "A Recover String press released before its first tick left the grapple string out");
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    // The port draws a user string from the Stand's arms, which are also its owner. The press's action is over at
    // once, so the arms have to stay for the string and leave with it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsThatThrewTheUsersGrappleStayWithTheStringAndLeaveWithIt(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode(),
                    "The press did not bring out the Stand's arms for the string");
            StoneFreeBarrierGameTests.later(helper, fixture, 20, () -> {
                StoneFreeStringEntity string = find(helper, fixture, Kind.USER_GRAPPLE);
                helper.assertTrue(string != null, "The user's grapple string was gone 20 ticks after its press");
                helper.assertTrue(arms.isAlive() && StoneFreeServices.controller(fixture.owner) == arms
                                && string.getOwner() == arms,
                        "The arms that threw the string left it while it was out: arms alive=" + arms.isAlive()
                                + ", current controller=" + StoneFreeServices.controller(fixture.owner)
                                + ", string owner=" + string.getOwner());
                StoneFreeServices.discardUserGrapples(fixture.owner);
                StoneFreeBarrierGameTests.later(helper, fixture, 15, () -> {
                    helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                            "The Stand's arms stayed out 15 ticks after the user's grapple string was gone");
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    // The core's retractAndUnsummon waits while any ability key is down, and nothing asks the arms again.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsLeaveWithTheUsersGrappleWhileAnAbilityKeyIsStillHeld(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            // The key that threw the string stays down.
            press(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME, InputMethod.CLICK);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode(),
                    "The press did not bring out the Stand's arms for the string");
            StoneFreeBarrierGameTests.later(helper, fixture, 20, () -> {
                helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) != null
                                && StoneFreeServices.controller(fixture.owner) == arms,
                        "The string or the arms that threw it were gone 20 ticks after the press: string "
                                + find(helper, fixture, Kind.USER_GRAPPLE) + ", controller "
                                + StoneFreeServices.controller(fixture.owner));
                helper.assertTrue(holdsAbilityKey(fixture), "The pressed key no longer counted as held");
                StoneFreeServices.discardUserGrapples(fixture.owner);
                StoneFreeBarrierGameTests.later(helper, fixture, 15, () -> {
                    helper.assertTrue(holdsAbilityKey(fixture), "The pressed key no longer counted as held");
                    helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                            "The Stand's arms stayed out 15 ticks after the user's grapple string was gone,"
                                    + " because an ability key was still held: arms action "
                                    + arms.getCurStandAction() + ", arms alive " + arms.isAlive());
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    /*
     * The port draws a user string from the Stand's arms (D-rotp-stone-free-02); the string is its user's Stand-power
     * action (1.16 StandAction), not the arms' own. 1.16's user attack string (SFUStringEntity, its user's own) went
     * out and came back over its 16 ticks, so the arms that threw it, also its owner here, stay until it is back,
     * as they stay for the user's grapple, and leave with it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsBroughtOutForAUserStringStayUntilItIsBackAndLeaveWithIt(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            EntityActionInstance thrown = LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && thrown != null && thrown.getPerformer() == arms
                            && arms.getCurStandAction() == null,
                    "The press did not bring out the Stand's arms to throw the string as its user's Stand-power"
                            + " action: arms " + arms + ", the user's Stand-power action " + thrown
                            + (thrown != null ? " performed by " + thrown.getPerformer() : ""));
            fixture.stand = arms;
            await(helper, fixture, Kind.USER_STRING, 6, string -> true, string -> {
                Flight flight = new Flight();
                when(helper, fixture, 30, () -> {
                    StoneFreeStringEntity out = find(helper, fixture, Kind.USER_STRING);
                    if (out != null) {
                        flight.lastAge = out.tickCount;
                        flight.farthest = Math.max(flight.farthest,
                                out.position().distanceTo(out.getOriginPoint(1.0F)));
                        helper.assertTrue(arms.isAlive() && !leaving(arms)
                                        && StoneFreeServices.controller(fixture.owner) == arms,
                                "The arms that threw the user's attack string left it at age " + out.tickCount
                                        + ": arms alive " + arms.isAlive() + ", action " + arms.getCurStandAction());
                    }
                    return out == null;
                }, "The user's attack string outlived 30 ticks", () -> {
                    helper.assertTrue(flight.lastAge >= 14 && flight.lastAge <= 16
                                    && Math.abs(flight.farthest - 8.0D) <= 0.3D,
                            "1.16's user attack string went 8 blocks out and was back by age 16; thrown from the"
                                    + " arms it went " + flight.farthest + " and was last seen at age "
                                    + flight.lastAge);
                    StoneFreeBarrierGameTests.later(helper, fixture, 15, () -> {
                        helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                                "The Stand's arms stayed out 15 ticks after the user string they threw was back:"
                                        + " arms action " + arms.getCurStandAction() + ", alive " + arms.isAlive());
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
                });
            });
        });
    }

    /*
     * 1.16's sweep, whip and pick spike (SFUStringSweepEntity, SFUStringWhipEntity, SFUStringSpikeEntity, thrown by
     * StoneFreeUserStringSweep / Whip / Pick with world.addFreshEntity) were their user's own and lived their whole
     * flight: back at age 10, 10 and 16. Drawn here from arms brought out for them (D-rotp-stone-free-02), which are also
     * their owner, so the arms stay until the string is back, even under Resolve (arms leave in 3 ticks), and go with it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsBroughtOutForASweepUnderResolveStayUntilItIsBackAsIn116(GameTestHelper helper) {
        runArmsStayForString(helper, StoneFreeAddon.USER_STRING_SWEEP_NAME, Kind.USER_SWEEP, 9);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsBroughtOutForAWhipUnderResolveStayUntilItIsBackAsIn116(GameTestHelper helper) {
        runArmsStayForString(helper, StoneFreeAddon.USER_STRING_WHIP_NAME, Kind.USER_WHIP, 9);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void armsBroughtOutForAPickUnderResolveStayUntilItsSpikeIsBackAsIn116(GameTestHelper helper) {
        runArmsStayForString(helper, StoneFreeAddon.USER_STRING_PICK_NAME, Kind.USER_SPIKE, 15);
    }

    private static void runArmsStayForString(GameTestHelper helper, String abilityName, Kind kind, int lastAge) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400)),
                    "Could not give the user Resolve");
            boolean pick = kind == Kind.USER_SPIKE;
            press(helper, fixture, abilityName, pick ? InputMethod.HOLD : InputMethod.CLICK);
            if (!pick) {
                AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
            }
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && arms.getUnsummonDuration() == 3,
                    "Setup: the press did not bring out the Stand's arms, leaving in 3 ticks under Resolve");
            fixture.stand = arms;
            await(helper, fixture, kind, 10, string -> true, string -> {
                // The pick fired; its key goes up, as a player's would.
                AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                Flight flight = new Flight();
                flight.lastReach = -1.0D;
                when(helper, fixture, 30, () -> {
                    StoneFreeStringEntity out = find(helper, fixture, kind);
                    if (out != null) {
                        flight.lastAge = out.tickCount;
                        if (flight.lastReach < 0.0D && (!arms.isAlive() || leaving(arms))) {
                            flight.lastReach = out.tickCount;
                        }
                    }
                    return out == null;
                }, "The user's " + kind + " outlived 30 ticks", () -> {
                    helper.assertTrue(flight.lastAge == lastAge && flight.lastReach < 0.0D,
                            "1.16's " + kind + " was its user's own and lived until it was back, last seen at age "
                                    + lastAge + "; thrown from arms under Resolve it was last seen at age "
                                    + flight.lastAge + (flight.lastReach < 0.0D ? ""
                                            : ", the arms leaving from its age " + (int) flight.lastReach));
                    StoneFreeBarrierGameTests.later(helper, fixture, 15, () -> {
                        helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                                "The Stand's arms stayed out 15 ticks after the " + kind + " they threw was back:"
                                        + " arms action " + arms.getCurStandAction() + ", alive " + arms.isAlive());
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
                });
            });
        });
    }

    // Arms whose user grapple string goes while they still perform another user string (a Close Wounds charge) stay
    // for that string and leave once it is over.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void armsStayForTheUserStringTheyStillPerformWhenTheGrappleGoes(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode(),
                    "The press did not bring out the Stand's arms for the string");
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) != null
                                && StoneFreeServices.controller(fixture.owner) == arms,
                        "Setup: the grapple string or its arms were gone 4 ticks after the press");
                helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                        "Could not make the user bleed");
                press(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, InputMethod.HOLD);
                EntityActionInstance charge =
                        LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
                helper.assertTrue(charge != null
                                && charge.ability == fixture.power.getAbility(StoneFreeAddon.USER_CLOSE_WOUNDS_NAME)
                                && charge.getPerformer() == arms && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Setup: the arms are not charging Close Wounds as the user's Stand-power action: " + charge);
                StoneFreeServices.discardUserGrapples(fixture.owner);
                helper.assertTrue(find(helper, fixture, Kind.USER_GRAPPLE) == null
                                && StoneFreeServices.controller(fixture.owner) == arms
                                && arms.getCurStandAction() == null,
                        "The arms left with the grapple string while they still charged Close Wounds: arms action "
                                + arms.getCurStandAction() + ", controller "
                                + StoneFreeServices.controller(fixture.owner));
                StoneFreeBarrierGameTests.later(helper, fixture, 30, () -> {
                    helper.assertTrue(!fixture.owner.hasEffect(ModStatusEffects.BLEEDING),
                            "Close Wounds did not fire after the grapple string went");
                    helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                            "The Stand's arms stayed out after the Close Wounds they performed was over: arms action "
                                    + arms.getCurStandAction() + ", alive " + arms.isAlive());
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    /*
     * 1.16 stone_free_user_close_wounds (StoneFreeUserCloseWounds :68-143, a plain StandAction with no
     * autoSummonStand) closed its user's wounds with no Stand. The port draws it from the arms (D-rotp-stone-free-02):
     * arms that threw a user string and are leaving stay for a Close Wounds held before they are gone, and leave after.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void closeWoundsHeldAsTheArmsLeaveClosesTheWoundsAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400, 2)),
                    "Could not make the user bleed");
            click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && arms.getUnsummonDuration() < 10,
                    "Setup: the press did not bring out the Stand's arms for the string, leaving within the charge");
            // They leave once the string is back, by its 16th tick.
            when(helper, fixture, 25, () -> leaving(arms),
                    "Setup: the arms that threw the user string did not start to leave", () -> {
                        holdCloseWoundsFrom(helper, fixture, arms);
                        assertWoundsClosedWithTheArmsOut(helper, fixture, arms, fixture.power.getStamina(), 14);
                    });
        });
    }

    /*
     * The pick (1.16 holdToFire(5)) throws its spike from the arms here: arms leaving after a Mobius toggle, in 3 ticks
     * under Resolve, stay for a pick held before they are gone, which throws its spike and sets its cooldown as it fires.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void pickHeldAsTheArmsLeaveUnderResolveThrowsItsSpike(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400)),
                    "Could not give the user Resolve");
            click(helper, fixture, StoneFreeAddon.USER_MOBIUS_STRIP_NAME);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && arms.getUnsummonDuration() == 3,
                    "Setup: the Mobius press did not bring out the Stand's arms, leaving in 3 ticks under Resolve");
            when(helper, fixture, 10, () -> leaving(arms),
                    "Setup: the arms that toggled Mobius did not start to leave", () -> {
                        Ability pick = fixture.power.getAbility(StoneFreeAddon.USER_STRING_PICK_NAME);
                        press(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME, InputMethod.HOLD);
                        EntityActionInstance charge =
                                LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
                        helper.assertTrue(charge != null && charge.ability == pick && charge.getPerformer() == arms
                                        && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                                        && charge.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) == 5.0F,
                                "Setup: the leaving arms are not charging the 5-tick pick: " + charge);
                        when(helper, fixture, 10, () -> charge.isOver(),
                                "The pick held as the arms left never ended", () -> {
                                    helper.assertTrue(find(helper, fixture, Kind.USER_SPIKE) != null
                                                    && fixture.power.isAbilityOnCooldown(pick.name())
                                                    && arms.isAlive()
                                                    && StoneFreeServices.controller(fixture.owner) == arms,
                                            "The pick held as the arms left under Resolve did not go off from them:"
                                                    + " spike " + find(helper, fixture, Kind.USER_SPIKE)
                                                    + ", cooldown " + fixture.power.isAbilityOnCooldown(pick.name())
                                                    + ", arms alive " + arms.isAlive() + ", controller "
                                                    + StoneFreeServices.controller(fixture.owner));
                                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                                });
                    });
        });
    }

    // 1.16 Close Wounds needed no Stand: arms gone before its charge fires (removed as an unsummon's end removes them)
    // leave it closing the wounds on its user alone, paid as usual.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void closeWoundsWhoseArmsAreGoneClosesTheWoundsOnTheUserAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400, 2)),
                    "Could not make the user bleed");
            press(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, InputMethod.HOLD);
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            EntityActionInstance charge = LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && charge != null && charge.getPerformer() == arms
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the press did not bring out the arms to charge Close Wounds: " + charge);
            StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                fixture.power.getPowerType().forceUnsummon(fixture.owner, fixture.power);
                helper.assertTrue(fixture.power.getSummonedStandEntity() == null && !charge.isOver()
                                && LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND) == charge,
                        "Setup: the arms are not gone with Close Wounds still charging: " + charge);
                float stamina = fixture.power.getStamina();
                watchWoundsClose(helper, fixture, stamina, 12, () -> StoneFreeBarrierGameTests.succeed(helper, fixture));
            });
        });
    }

    /*
     * The end of an arms-only punch starts the arms' unsummon (StandEntity.onActionSet); a Close Wounds they still
     * charge from the user's Stand-power slot keeps them out, so it fires from them, and they leave after it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void armsKeepTheCloseWoundsTheyChargeWhenTheirPunchEnds(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false);
        aimStraightUp(helper, fixture);
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400, 2))
                            && fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400)),
                    "Could not make the user bleed under Resolve");
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(fixture.owner, fixture.power,
                            stand -> stand.setArmsOnlyMode(), true),
                    "Could not bring out the Stand's arms");
            StoneFreeStandEntity arms = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(arms != null && arms.isArmsOnlyMode() && arms.getUnsummonDuration() == 3,
                    "Setup: no arms-only Stone Free, leaving in 3 ticks under Resolve");
            StoneFreeBarrierGameTests.later(helper, fixture, arms.summonLockTicks + 1, () -> {
                Ability punch = fixture.power.getAbility("punch");
                BufferingState punched = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(PUNCH_KEY, punch, fixture.owner, null, InputMethod.CLICK, 0.0F, punched,
                        punch.abilityId);
                AbilityInput.keyRelease(PUNCH_KEY, fixture.owner);
                EntityActionInstance armsPunch = LivingComponentAction.getCurEntityAction(arms);
                helper.assertTrue(punched.isActionSuccess && armsPunch != null && armsPunch.ability == punch
                                && arms.isArmsOnlyMode(),
                        "Setup: the arms did not start a punch of their own: " + armsPunch);
                when(helper, fixture, 30, () -> armsPunch.getPhase() == ActionPhase.RECOVERY
                                && armsPunch.getPhaseTicksLeft() <= 3.0F,
                        "Setup: the arms' punch did not reach the last ticks of its recovery", () -> {
                            helper.assertTrue(!armsPunch.isOver(), "Setup: the arms' punch was over already");
                            holdCloseWoundsFrom(helper, fixture, arms);
                            assertWoundsClosedWithTheArmsOut(helper, fixture, arms, fixture.power.getStamina(), 14);
                        });
            });
        });
    }

    private static final short PUNCH_KEY = 3;

    private static boolean leaving(StoneFreeStandEntity arms) {
        EntityActionInstance action = arms.getCurStandAction();
        return action != null && !action.isOver() && action.ability == ModSpecialActions.STAND_UNSUMMON.get();
    }

    private static void holdCloseWoundsFrom(GameTestHelper helper, Fixture fixture, StoneFreeStandEntity arms) {
        press(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, InputMethod.HOLD);
        EntityActionInstance charge = LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
        helper.assertTrue(charge != null
                        && charge.ability == fixture.power.getAbility(StoneFreeAddon.USER_CLOSE_WOUNDS_NAME)
                        && charge.getPerformer() == arms && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                "Setup: the arms are not charging Close Wounds as the user's Stand-power action: " + charge);
    }

    // the wounds close, paid in that tick, with the arms still out; the arms leave once Close Wounds is over
    private static void assertWoundsClosedWithTheArmsOut(GameTestHelper helper, Fixture fixture,
            StoneFreeStandEntity arms, float stamina, int ticksLeft) {
        watchWoundsClose(helper, fixture, stamina, ticksLeft, () -> {
            helper.assertTrue(arms.isAlive() && StoneFreeServices.controller(fixture.owner) == arms,
                    "The arms that charged Close Wounds were gone when it fired: arms alive " + arms.isAlive()
                            + ", controller " + StoneFreeServices.controller(fixture.owner));
            when(helper, fixture, 15, () -> fixture.power.getSummonedStandEntity() == null,
                    "The arms stayed out after the Close Wounds they performed was over: arms action "
                            + arms.getCurStandAction(),
                    () -> StoneFreeBarrierGameTests.succeed(helper, fixture));
        });
    }

    // 20 per wound level (1.16 getStaminaCost), 60 here, paid in the tick the wounds close
    private static void watchWoundsClose(GameTestHelper helper, Fixture fixture, float previous, int ticksLeft,
            Runnable then) {
        float now = fixture.power.getStamina();
        if (!fixture.owner.hasEffect(ModStatusEffects.BLEEDING)) {
            helper.assertTrue(previous - now >= 40.0F,
                    "Close Wounds closed the wounds without paying its 60: stamina " + previous + " -> " + now);
            then.run();
            return;
        }
        helper.assertTrue(ticksLeft > 0, "Close Wounds never closed the wounds: the user's Stand-power action "
                + LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND) + ", Stand "
                + fixture.power.getSummonedStandEntity());
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchWoundsClose(helper, fixture, now, ticksLeft - 1, then));
    }

    private static void when(GameTestHelper helper, Fixture fixture, int ticksLeft, BooleanSupplier condition,
            String failure, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        helper.assertTrue(ticksLeft > 0, failure);
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> when(helper, fixture, ticksLeft - 1, condition, failure, then));
    }

    private static boolean holdsAbilityKey(Fixture fixture) {
        return !fixture.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.isEmpty();
    }

    // 1.21 ticks an entity only inside the simulation distance, and a string that does not tick neither runs
    // out nor checks anything. This user is a mob, which the donor's bound for players does not cover.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review",
            timeoutTicks = StoneFreeBarrierGameTests.CHUNK_WAIT_TEST_TICKS + 200)
    public static void userGrappleThatFliesOutOfTheEntityTickingAreaEndsThere(GameTestHelper helper) {
        atRemoteSite(helper, 40, (column, release) -> {
            Cow owner = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(owner != null, "Missing Stone Free owner fixture");
            owner.setNoAi(true);
            Fixture fixture = remoteFixture(helper, owner, column, release);
            StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                BlockPos outside = fixture.owner.blockPosition().east(12);
                helper.assertTrue(helper.getLevel().isLoaded(outside)
                                && !helper.getLevel().isPositionEntityTicking(outside),
                        "The site has no loaded chunk without entity ticking east of the user: loaded "
                                + helper.getLevel().isLoaded(outside));
                click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
                watchLevelFlight(helper, fixture, new Flight(), 12, flight -> {
                    helper.assertTrue(flight.seen, "The press threw no grapple string");
                    StoneFreeStringEntity left = find(helper, fixture, Kind.USER_GRAPPLE);
                    helper.assertTrue(left == null,
                            "A user's grapple that flew out of the entity-ticking area was left there, where"
                                    + " it can never end: " + describe(helper, fixture, left));
                    assertNothingLeftOfTheGrapple(helper, fixture);
                });
            });
        });
    }

    // 1.16 SFUGrapplingStringEntity.tick:62-64 ended a player's string level with the edge of the view
    // distance, which was then also where entities stopped ticking. With a smaller simulation distance that
    // edge is nearer.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review",
            timeoutTicks = StoneFreeBarrierGameTests.CHUNK_WAIT_TEST_TICKS + 200)
    public static void playersGrappleEndsAtTheSimulationDistanceWhenThatIsBelowTheViewDistance(
            GameTestHelper helper) {
        atRemoteSite(helper, 80, (column, release) -> {
            PlayerList players = helper.getLevel().getServer().getPlayerList();
            int view = players.getViewDistance();
            int simulation = players.getSimulationDistance();
            Runnable restore = () -> {
                players.setViewDistance(view);
                players.setSimulationDistance(simulation);
                release.run();
            };
            players.setViewDistance(6);
            players.setSimulationDistance(2);
            Fixture fixture;
            try {
                fixture = remoteFixture(helper, new FakePlayer(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "SFGrappleOwner")), column, restore);
            } catch (Throwable error) {
                restore.run();
                throw error;
            }
            awaitPlayerArea(helper, fixture, StoneFreeBarrierGameTests.CHUNK_WAIT_TICKS, System.nanoTime(), () -> {
                click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
                watchLevelFlight(helper, fixture, new Flight(), 30, flight -> {
                    helper.assertTrue(flight.seen, "The press threw no grapple string");
                    StoneFreeStringEntity left = find(helper, fixture, Kind.USER_GRAPPLE);
                    helper.assertTrue(left == null,
                            "A player's missed grapple was still out 30 ticks after its press with view"
                                    + " distance 6 and simulation distance 2: "
                                    + describe(helper, fixture, left));
                    // min(6, 2) * 16 - 4
                    helper.assertTrue(flight.farthest > 20.0D && flight.farthest <= 28.0D,
                            "A player's grapple was last seen flying " + flight.farthest + " blocks from its"
                                    + " player (age " + flight.lastAge + "); with simulation distance 2 it ends"
                                    + " once it is more than 28 blocks out");
                    assertNothingLeftOfTheGrapple(helper, fixture);
                });
            });
        });
    }

    private static void awaitPlayerArea(GameTestHelper helper, Fixture fixture, int ticksLeft, long startNanos,
            Runnable ready) {
        ServerLevel level = helper.getLevel();
        BlockPos inside = fixture.owner.blockPosition().east(30);
        BlockPos outside = fixture.owner.blockPosition().east(44);
        if (level.isPositionEntityTicking(inside) && level.isLoaded(outside)
                && !level.isPositionEntityTicking(outside)) {
            ready.run();
            return;
        }
        helper.assertTrue(!StoneFreeBarrierGameTests.chunkWaitSpent(ticksLeft, startNanos),
                "The player's chunks did not settle within "
                + StoneFreeBarrierGameTests.chunkWaitBudget(startNanos) + ": 30 blocks east ticks entities "
                + level.isPositionEntityTicking(inside) + ", 44 blocks east loaded " + level.isLoaded(outside)
                + " and ticks entities " + level.isPositionEntityTicking(outside));
        StoneFreeBarrierGameTests.yieldToChunkWorkers();
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> awaitPlayerArea(helper, fixture, ticksLeft - 1, startNanos, ready));
    }

    private static void watchLevelFlight(GameTestHelper helper, Fixture fixture, Flight flight, int ticksLeft,
            Consumer<Flight> done) {
        StoneFreeStringEntity string = find(helper, fixture, Kind.USER_GRAPPLE);
        if (string != null) {
            flight.seen = true;
            flight.lastAge = string.tickCount;
            flight.farthest = Math.max(flight.farthest, levelDistance(fixture, string));
        }
        if (ticksLeft == 0) {
            done.accept(flight);
            return;
        }
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchLevelFlight(helper, fixture, flight, ticksLeft - 1, done));
    }

    private static double levelDistance(Fixture fixture, StoneFreeStringEntity string) {
        return Math.hypot(string.getX() - fixture.owner.getX(), string.getZ() - fixture.owner.getZ());
    }

    private static String describe(GameTestHelper helper, Fixture fixture, StoneFreeStringEntity string) {
        StoneFreeState state = StoneFreeState.get(fixture.owner);
        return string == null ? "no string" : "age " + string.tickCount + ", " + levelDistance(fixture, string)
                + " blocks from its user at " + string.position() + ", its position ticks entities "
                + helper.getLevel().isPositionEntityTicking(string.blockPosition()) + ", saved grapple "
                + state.userGrapple().isPresent() + ", active user strings " + state.activeUserStrings()
                + ", arms out " + (fixture.power.getSummonedStandEntity() != null);
    }

    // What a string that never ends would keep: the saved id that turns the slot into Recover String and
    // refuses every user string, the count that stops stamina regeneration, and the arms.
    private static void assertNothingLeftOfTheGrapple(GameTestHelper helper, Fixture fixture) {
        StoneFreeState state = StoneFreeState.get(fixture.owner);
        helper.assertTrue(state.userGrapple().isEmpty(), "The ended grapple left its saved id behind");
        // Past the 20 tick recount of active strings and the arms' unsummon.
        StoneFreeBarrierGameTests.later(helper, fixture, 25, () -> {
            Ability grapple = fixture.power.getAbility(StoneFreeAddon.USER_GRAPPLE_NAME);
            Ability slot = fixture.power.updateAvailableMoves()
                    .getContextVariation(StoneFreeAddon.USER_GRAPPLE_NAME);
            helper.assertTrue(slot == grapple && grapple.checkConditions(fixture.power).isPositive(),
                    "The grapple could not be thrown again after its string ended: slot=" + name(slot));
            helper.assertTrue(state.activeUserStrings() == 0,
                    "The ended grapple still counted as an active user string, which stops stamina"
                            + " regeneration: " + state.activeUserStrings());
            helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                    "The Stand's arms stayed out after the grapple ended");
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    /**
     * A chunk of its own far from the test grid: entities tick in that chunk only and the chunks around it
     * are loaded, so a level string thrown east from its middle leaves the ticking area 8 blocks on.
     */
    private static void atRemoteSite(GameTestHelper helper, int chunksAway, BiConsumer<BlockPos, Runnable> body) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos site = new ChunkPos((origin.getX() >> 4) - chunksAway, (origin.getZ() >> 4) - chunksAway);
        level.getChunkSource().addRegionTicket(SITE_TICKET, site, 2, site);
        Runnable release = () -> level.getChunkSource().removeRegionTicket(SITE_TICKET, site, 2, site);
        awaitSite(helper, new BlockPos(site.getMinBlockX() + 7, origin.getY(), site.getMinBlockZ() + 8),
                release, StoneFreeBarrierGameTests.CHUNK_WAIT_TICKS, System.nanoTime(), body);
    }

    private static void awaitSite(GameTestHelper helper, BlockPos column, Runnable release, int ticksLeft,
            long startNanos, BiConsumer<BlockPos, Runnable> body) {
        ServerLevel level = helper.getLevel();
        try {
            if (level.isPositionEntityTicking(column) && level.isLoaded(column.east(16))) {
                body.accept(column, release);
                return;
            }
            helper.assertTrue(!StoneFreeBarrierGameTests.chunkWaitSpent(ticksLeft, startNanos),
                    "The remote site at " + column + " did not tick entities within "
                    + StoneFreeBarrierGameTests.chunkWaitBudget(startNanos) + ": its column ticks entities "
                    + level.isPositionEntityTicking(column) + ", the chunk 16 blocks east is loaded "
                    + level.isLoaded(column.east(16)));
        } catch (Throwable error) {
            release.run();
            throw error;
        }
        StoneFreeBarrierGameTests.yieldToChunkWorkers();
        helper.runAfterDelay(1, () -> awaitSite(helper, column, release, ticksLeft - 1, startNanos, body));
    }

    private static Fixture remoteFixture(GameTestHelper helper, LivingEntity owner, BlockPos column,
            Runnable afterwards) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, false, owner, column);
        fixture.afterwards.add(afterwards);
        helper.getLevel().setBlockAndUpdate(fixture.first, fixture.originalFirst);
        helper.getLevel().setBlockAndUpdate(fixture.second, fixture.originalSecond);
        owner.setYRot(-90.0F);
        owner.yRotO = -90.0F;
        owner.setYBodyRot(-90.0F);
        owner.yBodyRotO = -90.0F;
        owner.setYHeadRot(-90.0F);
        owner.yHeadRotO = -90.0F;
        owner.setXRot(0.0F);
        owner.xRotO = 0.0F;
        return fixture;
    }

    // 1.16 SFUGrapplingStringEntity.moveToBlockAttached set the user's motion outright and never cleared it:
    // the string lets go within two blocks and the user flies on.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userGrappleLeavesItsPullSpeedOnTheUserWhenItLetsGo(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        BlockPos anchor = anchorAbove(helper, fixture, 10.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            await(helper, fixture, Kind.USER_GRAPPLE, 16, StoneFreeStringEntity::isAttachedToBlock, string -> {
                Vec3 pull = pullOnce(helper, fixture, string);
                fixture.owner.setPos(Vec3.atCenterOf(anchor).subtract(0.0D, 1.9D, 0.0D));
                string.tick();
                helper.assertTrue(!string.isAlive(), "The grapple did not let go within two blocks of its anchor");
                helper.assertTrue(fixture.owner.getDeltaMovement().distanceTo(pull) < 1.0E-6D,
                        "The grapple stopped its user dead on arrival; 1.16 left the pull speed: motion="
                                + fixture.owner.getDeltaMovement() + ", the pull was " + pull);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // 1.16 never put the user in the string's 'dragged' set, whose motion is cleared when the string goes.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void takingTheGrappleBackLeavesTheUsersMotionAlone(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        anchorAbove(helper, fixture, 10.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            await(helper, fixture, Kind.USER_GRAPPLE, 16, StoneFreeStringEntity::isAttachedToBlock, string -> {
                Vec3 pull = pullOnce(helper, fixture, string);
                // What Recover String does with the string.
                StoneFreeServices.discardUserGrapples(fixture.owner);
                helper.assertTrue(!string.isAlive(), "The grapple string was not taken back");
                helper.assertTrue(fixture.owner.getDeltaMovement().distanceTo(pull) < 1.0E-6D,
                        "Taking the grapple string back stopped its user; 1.16 left the motion alone: motion="
                                + fixture.owner.getDeltaMovement() + ", the pull was " + pull);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    private static Vec3 pullOnce(GameTestHelper helper, Fixture fixture, StoneFreeStringEntity string) {
        fixture.owner.setDeltaMovement(Vec3.ZERO);
        string.tick();
        Vec3 pull = fixture.owner.getDeltaMovement();
        helper.assertTrue(string.isAlive() && Math.abs(pull.length() - 2.0D) < 1.0E-6D,
                "The anchored grapple did not pull its user at 2.0: motion=" + pull);
        return pull;
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBlockGrappleDoesNotStrikeWhatItPassesThrough(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear bystander = target(helper, fixture, fixture.owner.position().add(0.0D, 6.0D, 0.0D));
            click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            watchPassage(helper, fixture, bystander, new StringBuilder(), 4);
        });
    }

    private static void watchPassage(GameTestHelper helper, Fixture fixture, PolarBear bystander,
            StringBuilder trace, int ticksLeft) {
        StoneFreeStringEntity string = find(helper, fixture, Kind.USER_GRAPPLE);
        trace.append(string == null ? " gone" : " age" + string.tickCount + "@y" + (float) string.getY())
                .append("/hurt").append(bystander.hurtTime);
        if (ticksLeft == 0) {
            helper.assertTrue(bystander.hurtTime == 0 && bystander.getLastHurtByMob() == null,
                    "A grapple thrown for a block struck what it passed through: lastHurtByMob="
                            + bystander.getLastHurtByMob() + ", trace" + trace);
            helper.assertTrue(string != null && string.getEntityAttachedTo() == null
                            && string.getY() > bystander.getBoundingBox().maxY,
                    "The block grapple did not fly on past the bystander (top y "
                            + bystander.getBoundingBox().maxY + "): trace" + trace);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchPassage(helper, fixture, bystander, trace, ticksLeft - 1));
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrappleDrawsBackFromABlockInsteadOfPassingThrough(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            BlockPos roof = BlockPos.containing(place.add(0.0D, 12.0D, 0.0D));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    fixture.setBlock(helper, roof.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                }
            }
            watchRetraction(helper, fixture, roof, new Flight(), 14);
        });
    }

    private static void watchRetraction(GameTestHelper helper, Fixture fixture, BlockPos roof, Flight flight,
            int ticksLeft) {
        StoneFreeStringEntity string = find(helper, fixture, Kind.STAND_GRAPPLE);
        if (string != null) {
            flight.seen = true;
            flight.lastAge = string.tickCount;
            flight.highest = Math.max(flight.highest, string.getY());
        }
        if (ticksLeft == 0) {
            helper.assertTrue(flight.seen && flight.highest > roof.getY() - 6.0D,
                    "The Stand's grapple never flew up to the roof: seen=" + flight.seen + ", highest y "
                            + flight.highest + ", roof underside " + roof.getY() + ", Stand pitch "
                            + fixture.stand.getXRot());
            helper.assertTrue(flight.highest <= roof.getY(),
                    "The Stand's grapple passed through the block it hit: highest y " + flight.highest
                            + ", roof underside " + roof.getY());
            helper.assertTrue(string == null,
                    "The Stand's grapple did not draw back and end after hitting a block: age " + flight.lastAge);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchRetraction(helper, fixture, roof, flight, ticksLeft - 1));
    }

    // 1.16 stone_free_grapple_entity: no stamina condition, and StandEntityTask paid its staminaCostTick(1) with
    // the result ignored, so the held grapple starts and keeps its catch on an empty bar.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standGrappleHoldsOnAnEmptyBar(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            emptyBar(helper, fixture);
            Vec3 place = holdGrappleFromItsPlace(helper, fixture);
            PolarBear target = target(helper, fixture, place.add(0.0D, 10.0D, 0.0D));
            Ability grapple = fixture.power.getAbility(StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            await(helper, fixture, Kind.STAND_GRAPPLE, 12, string -> string.getEntityAttachedTo() == target,
                    string -> {
                        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                        helper.assertTrue(action != null && action.ability == grapple
                                        && action.getPhase() == ActionPhase.PERFORM,
                                "The held grapple stopped on an empty bar: " + action);
                        helper.assertTrue(fixture.power.getStamina() == 0.0F,
                                "The paid tick cost must keep the bar at 0: " + fixture.power.getStamina());
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 StandAction.onPerform paid stone_free_user_string_attack's 20 with the result ignored: the string flies
    // on an empty bar.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 60)
    public static void userStringAttackFiresOnAnEmptyBar(GameTestHelper helper) {
        Fixture fixture = upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            emptyBar(helper, fixture);
            click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            await(helper, fixture, Kind.USER_STRING, 5, string -> true, string -> {
                helper.assertTrue(fixture.power.getStamina() < 20.0F,
                        "The 20 was not paid down to an empty bar: " + fixture.power.getStamina());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    private static void emptyBar(GameTestHelper helper, Fixture fixture) {
        fixture.power.setStamina(0.0F);
        helper.assertTrue(!fixture.power.isStaminaInfinite() && fixture.power.getStamina() == 0.0F,
                "Setup: the Stone Free user's stamina is not finite and empty");
    }

    static Fixture upwardFixture(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, true);
        aimStraightUp(helper, fixture);
        return fixture;
    }

    private static void aimStraightUp(GameTestHelper helper, Fixture fixture) {
        // The barrier fixture's anchor blocks stand beside the user; nothing may block the way up.
        helper.getLevel().setBlockAndUpdate(fixture.first, fixture.originalFirst);
        helper.getLevel().setBlockAndUpdate(fixture.second, fixture.originalSecond);
        // The framework walls every test plot in barrier blocks up to the fixture's home height; a string
        // thrown for a block from inside one anchors on it at once.
        fixture.owner.setPos(fixture.home.add(0.0D, 3.0D, 0.0D));
        fixture.owner.setXRot(-90.0F);
        fixture.owner.xRotO = -90.0F;
    }

    static BlockPos anchorAbove(GameTestHelper helper, Fixture fixture, double height) {
        BlockPos anchor = BlockPos.containing(fixture.owner.position().add(0.0D, height, 0.0D));
        fixture.setBlock(helper, anchor, Blocks.STONE.defaultBlockState());
        return anchor;
    }

    // The 1x1x1 template forces only the plot's own chunk, and StoneFreeStringEntity drops a string whose end
    // leaves the entity-ticking area, which would pass for the rule a test checks.
    private static void assertCatchOnTickingChunk(GameTestHelper helper, LivingEntity target) {
        BlockPos middle = BlockPos.containing(target.getX(), target.getY(0.5D), target.getZ());
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(middle),
                "Fixture: the catch's middle " + middle + " is off the entity-ticking area");
    }

    static PolarBear target(GameTestHelper helper, Fixture fixture, Vec3 feet) {
        PolarBear target = EntityType.POLAR_BEAR.create(helper.getLevel());
        helper.assertTrue(target != null, "Missing grapple target fixture");
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(feet);
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not spawn the grapple target");
        fixture.extras.add(target);
        return target;
    }

    // 1.16 stone_free_grapple_entity kept no pressed target: the Stand threw along its user's look (straight up here)
    // from its grapple place, standOffsetFromUser(-0.5, 0.25), where the press has just put it.
    private static Vec3 holdGrappleFromItsPlace(GameTestHelper helper, Fixture fixture) {
        hold(helper, fixture, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
        fixture.stand.updatePosition(fixture.owner);
        return fixture.stand.position();
    }

    private static void hold(GameTestHelper helper, Fixture fixture, String abilityName) {
        press(helper, fixture, abilityName, InputMethod.HOLD);
    }

    // The donor's one press: the key is down and up again before the action first ticks.
    static void click(GameTestHelper helper, Fixture fixture, String abilityName) {
        press(helper, fixture, abilityName, InputMethod.CLICK);
        AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
    }

    private static void press(GameTestHelper helper, Fixture fixture, String abilityName, InputMethod input) {
        StoneFreeBarrierGameTests.assertTicksEntities(helper, fixture);
        var ability = fixture.power.getAbility(abilityName);
        helper.assertTrue(ability instanceof StoneFreeStringAbility
                        && ability.checkConditions(fixture.power).isPositive(),
                "Registered " + abilityName + " refused the fixture");
        BufferingState buffering = BufferingState.clickOnly();
        AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, ability, fixture.owner, null,
                input, 0.0F, buffering, ability.abilityId);
        helper.assertTrue(buffering.isActionSuccess, "Public " + input + " of " + abilityName + " did not start");
    }

    private static String name(Ability ability) {
        return ability == null ? "none" : ability.name();
    }

    static StoneFreeStringEntity find(GameTestHelper helper, Fixture fixture, Kind kind) {
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof StoneFreeStringEntity string && string.isAlive() && string.kind() == kind
                    && StoneFreeServices.userOf(string.getOwner()) == fixture.owner) {
                return string;
            }
        }
        return null;
    }

    static void await(GameTestHelper helper, Fixture fixture, Kind kind, int ticksLeft,
            Predicate<StoneFreeStringEntity> ready, Consumer<StoneFreeStringEntity> assertions) {
        StoneFreeStringEntity string = find(helper, fixture, kind);
        if (string != null && ready.test(string)) {
            assertions.accept(string);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The " + kind + " string did not get there: "
                + (string == null ? "no live string" : "age " + string.tickCount + ", "
                        + string.position().distanceTo(string.getOriginPoint(1.0F)) + " blocks out at "
                        + string.position() + ", attached entity " + string.getEntityAttachedTo()
                        + ", attached block " + string.isAttachedToBlock())
                + ", user pitch " + fixture.owner.getXRot() + ", Stand pitch " + fixture.stand.getXRot());
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> await(helper, fixture, kind, ticksLeft - 1, ready, assertions));
    }

    private static final class Flight {
        boolean seen;
        int lastAge;
        double lastReach;
        double highest = Double.NEGATIVE_INFINITY;
        double farthest;
    }
}
