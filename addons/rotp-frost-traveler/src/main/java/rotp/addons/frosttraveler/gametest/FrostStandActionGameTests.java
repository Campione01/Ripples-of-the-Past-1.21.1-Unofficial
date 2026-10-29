package rotp.addons.frosttraveler.gametest;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility.Operation;
import rotp.addons.frosttraveler.state.CreamStarterService;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 DeformAction (cs_deform), DeformTargetAction (cs_def) and PutItemAction (cs_item) were StandEntityActions:
 * holdToFire(15 / 10 / 15, false), FULL auto-summon, the IDLE pose, a task holding the block (BLOCK) or entity
 * (ENTITY) they were pressed at, Deform and PutItem performing on that target, PutItem's staminaCost(100) paid at
 * the perform with no stamina check, and its onTaskStopped releasing the main-hand Cream Starter.
 */
@GameTestHolder("frost_injection")
@PrefixGameTestTemplate(false)
public final class FrostStandActionGameTests {
    private static final short KEY = 72;
    private static final double EPS = 1.0E-6D;
    // 6 x 1 x 4 of air: the framework forces and waits for only the chunks of the structure's bounds, and every
    // fixture of these scenes stands within them (Scene.inArea)
    private static final String AREA = "fixture_area";

    private FrostStandActionGameTests() {}

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void creamStandActionsChargeOnTheStandAndHoldTheirPressTargets(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility deform = scene.ability(FrostTravelerAddon.CS_DEFORM_NAME, Operation.DEFORM);
            CreamStandActionAbility impair = scene.ability(FrostTravelerAddon.CS_DEF_NAME, Operation.DEFENSE);
            CreamStandActionAbility inject = scene.ability(FrostTravelerAddon.CS_ITEM_NAME, Operation.ITEM_INJECTION);

            // cs_deform pressed at iron bars east of the user's eyes
            BlockPos bars = scene.block(2, 13, 1);
            helper.getLevel().setBlockAndUpdate(bars, Blocks.IRON_BARS.defaultBlockState());
            scene.turn(-90.0F, 0.0F);
            EntityActionInstance deformAction = scene.press(deform, 15);
            ActionTarget deformTarget = deformAction.getTaskTarget(helper.getLevel());
            helper.assertTrue(deformTarget.getType() == ActionTarget.TargetType.BLOCK
                            && bars.equals(deformTarget.getBlockPos()),
                    "1.16 Deform (TargetRequirement.BLOCK) held the block it was pressed at; the task holds "
                            + deformTarget);
            scene.release();
            helper.getLevel().setBlockAndUpdate(bars, Blocks.AIR.defaultBlockState());

            // cs_def and cs_item pressed at a cow south of the user, 40 degrees down onto its legs
            scene.turn(0.0F, 40.0F);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIRE_CHARGE));
            EntityActionInstance injectAction = scene.press(inject, 15);
            helper.assertTrue(injectAction.getTaskTarget(helper.getLevel()).getEntity() == scene.cow,
                    "1.16 PutItem (TargetRequirement.ENTITY) held the cow it was pressed at; the task holds "
                            + injectAction.getTaskTarget(helper.getLevel()));
            scene.release();
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            EntityActionInstance impairAction = scene.press(impair, 10);
            helper.assertTrue(impairAction.getTaskTarget(helper.getLevel()).getEntity() == scene.cow,
                    "1.16 DeformTarget (TargetRequirement.ENTITY) held the cow it was pressed at; the task holds "
                            + impairAction.getTaskTarget(helper.getLevel()));
            scene.stand.updatePosition(scene.player);
            helper.assertTrue(scene.stand.position().distanceTo(scene.player.position()) < EPS,
                    "1.16 gave these actions no offset: the Cream Starter stays at its user's feet, got "
                            + scene.stand.position().subtract(scene.player.position()));
            scene.runToEnd(impairAction);
            helper.assertTrue(scene.cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "The charged cs_def Stand action did not slow the cow at its legs");
            helper.succeed();
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void deformGoesThroughTheBlockItsTaskHolds(GameTestHelper helper) {
        AtomicInteger sprays = new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtPosition> spray = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == FrostTravelerAddon.CS_SHOT.get()) {
                sprays.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, spray);
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility deform = scene.ability(FrostTravelerAddon.CS_DEFORM_NAME, Operation.DEFORM);
            BlockPos east = scene.block(2, 13, 1);
            BlockPos north = scene.block(1, 13, 0);
            helper.getLevel().setBlockAndUpdate(east, Blocks.IRON_BARS.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(north, Blocks.IRON_BARS.defaultBlockState());

            // a release before the 15-tick charge goes off drops it
            scene.turn(-90.0F, 0.0F);
            Vec3 start = scene.player.position();
            EntityActionInstance dropped = scene.press(deform, 15);
            for (int tick = 0; tick < 5; tick++) {
                scene.stand.getStandActionComponent().tick();
            }
            scene.release();
            scene.runToEnd(dropped);
            helper.assertTrue(scene.player.position().distanceTo(start) < EPS,
                    "1.16 holdToFire(15, false): a deform let go of after 5 ticks never went off, but the user moved to "
                            + scene.player.position());
            helper.assertTrue(sprays.getAndSet(0) == 5,
                    "1.16 DeformAction.onHoldTick sprayed once on each of the 5 held ticks; sprays " + sprays.get());

            // pressed at the bars east, then the user faces the bars north while it charges
            EntityActionInstance action = scene.press(deform, 15);
            scene.turn(180.0F, 0.0F);
            scene.runToEnd(action);
            helper.assertTrue(sprays.get() == 14,
                    "1.16 DeformAction.onHoldTick sprayed on held ticks 1 to 14 and went off on the 15th; sprays "
                            + sprays.get());
            Vec3 expected = scene.inArea(Vec3.atBottomCenterOf(east.north().below()));
            helper.assertTrue(scene.player.position().distanceTo(expected) < EPS,
                    "1.16 DeformAction.standPerform went through the block its task held (the bars east, toward the"
                            + " way the user faces now) to " + expected + ", not through the bars the user looks at"
                            + " now; got " + scene.player.position());
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(spray);
        }
    }

    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void injectionGoesIntoItsTaskEntityAndPaysItsStaminaAtThePerform(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility inject = scene.ability(FrostTravelerAddon.CS_ITEM_NAME, Operation.ITEM_INJECTION);
            scene.turn(0.0F, 40.0F);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIRE_CHARGE, 2));

            // letting go before the charge fires still puts the main-hand Cream Starter on cooldown
            EntityActionInstance dropped = scene.press(inject, 15);
            scene.stand.getStandActionComponent().tick();
            scene.release();
            scene.runToEnd(dropped);
            helper.assertTrue(scene.player.getCooldowns().isOnCooldown(FrostTravelerAddon.CREAM_STARTER_ITEM.get())
                            && scene.cow.getRemainingFireTicks() <= 0
                            && scene.player.getOffhandItem().getCount() == 2,
                    "1.16 PutItemAction.onTaskStopped released the main-hand Cream Starter (its 20-tick cooldown)"
                            + " when the dropped hold stopped, and nothing was injected");
            scene.player.getCooldowns().removeCooldown(FrostTravelerAddon.CREAM_STARTER_ITEM.get());

            // 30 stamina is no bar to it; pressed at the cow, which steps aside while a second cow takes its place
            scene.power.setStamina(30.0F);
            helper.assertTrue(inject.checkConditions(scene.power).isPositive(),
                    "1.16 PutItemAction had no stamina condition, but cs_item refused 30 stamina");
            EntityActionInstance action = scene.press(inject, 15);
            Cow second = scene.addCow(scene.cow.position());
            scene.cow.setPos(scene.inArea(scene.cow.position().add(1.5D, 0.0D, 0.0D)));
            scene.runToEnd(action);
            helper.assertTrue(scene.cow.getRemainingFireTicks() == 100 && second.getRemainingFireTicks() <= 0
                            && scene.player.getOffhandItem().getCount() == 1,
                    "1.16 PutItemAction.standPerform set the entity its task held on fire for 5 s, not the one the"
                            + " user looks at now: held cow " + scene.cow.getRemainingFireTicks() + ", second cow "
                            + second.getRemainingFireTicks());
            helper.assertTrue(scene.power.getStamina() == 0.0F,
                    "1.16 paid staminaCost(100) after the perform, a short bar dropping to 0; stamina is "
                            + scene.power.getStamina());
            helper.assertTrue(scene.player.getCooldowns().isOnCooldown(FrostTravelerAddon.CREAM_STARTER_ITEM.get()),
                    "1.16 PutItemAction.onTaskStopped put the main-hand Cream Starter on cooldown after the perform");
            second.discard();
            helper.succeed();
        }
    }

    /**
     * 1.16 cs_item is the one Cream action whose press works with the Stand away (it needs no sprayer): the press,
     * driven through the server's admission (AbilityInput.withConditionCheck, then the key press), summons the full
     * Stand, which keeps the IDLE pose.
     */
    @GameTest(template = AREA, templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void aPressWithTheStandAwaySummonsTheFullStand(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            CreamStandActionAbility inject = scene.ability(FrostTravelerAddon.CS_ITEM_NAME, Operation.ITEM_INJECTION);
            FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(scene.player, scene.power);
            helper.assertTrue(!scene.power.isSummoned(), "setup: the Cream Starter is still summoned");
            scene.turn(0.0F, 40.0F);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIRE_CHARGE));
            helper.assertTrue(scene.pressAsServer(inject),
                    "1.16 PutItemAction needed no sprayer: cs_item pressed with the Stand away is admitted");
            StandEntity stand = scene.power.getSummonedStandEntity();
            helper.assertTrue(scene.power.isSummoned() && stand != null && !stand.isArmsOnlyMode()
                            && stand.getCurStandAction() != null
                            && stand.getCurStandAction().ability == inject,
                    "1.16 StandEntityAction.onClick summoned the full Stand (AutoSummonMode.FULL) for cs_item: "
                            + stand);
            ActionAnimIdentifier anim = stand.getCurStandAction().getEntityAnim();
            helper.assertTrue(anim.isIdle() && "idle".equals(anim.name()),
                    "1.16 PutItem kept StandPose.IDLE: cs_item must show the idle animation, not " + anim);
            scene.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.succeed();
        }
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final Player player;
        private final StandPower power;
        private final StandEntity stand;
        private final Cow cow;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            // not a FakePlayer: its connection drops the teleport cs_deform performs
            player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "FrostStandAction")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            player.setNoGravity(true);
            player.setPos(inArea(helper.absoluteVec(new Vec3(1.5D, 12.0D, 1.5D))));
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Cream Starter user");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(FrostTravelerAddon.CREAM_STARTER.get());
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            player.setItemInHand(InteractionHand.MAIN_HAND, CreamStarterService.createOwnedSprayer(player));
            helper.assertTrue(FrostTravelerAddon.CREAM_STARTER.get().summon(player, power)
                            && FrostTravelerAddon.controller(player) != null,
                    "Could not summon the Cream Starter");
            stand = FrostTravelerAddon.controller(player);
            stand.summonLockTicks = 0;
            cow = addCow(player.position().add(0.0D, 0.0D, 2.0D));
        }

        private Cow addCow(Vec3 pos) {
            Cow added = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(added != null, "Could not create a cow");
            added.setNoAi(true);
            added.setNoGravity(true);
            inArea(pos);
            added.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(added), "Could not add a cow");
            return added;
        }

        private BlockPos block(int x, int y, int z) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            inArea(Vec3.atCenterOf(pos));
            return pos;
        }

        // beyond the structure's chunks a fixture may stand where entities are not visible to world queries yet
        private Vec3 inArea(Vec3 pos) {
            AABB area = helper.getBounds();
            helper.assertTrue(pos.x >= area.minX && pos.x < area.maxX && pos.z >= area.minZ && pos.z < area.maxZ,
                    "Fixture: " + pos + " lies outside the test structure " + area
                            + ", whose chunks alone the framework loads before the test");
            return pos;
        }

        private CreamStandActionAbility ability(String name, Operation operation) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability instanceof CreamStandActionAbility action && action.operation() == operation,
                    "1.16 " + name + " was a StandEntityAction; the moveset has " + ability);
            return (CreamStandActionAbility) ability;
        }

        private void turn(float yaw, float pitch) {
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;
            player.setXRot(pitch);
            player.xRotO = pitch;
        }

        private EntityActionInstance press(CreamStandActionAbility ability, int chargeTicks) {
            helper.assertTrue(ability.checkConditions(power).isPositive(),
                    "setup: " + ability.name() + " cannot be used here");
            HeldInputEntry held = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD, 0,
                    BufferingState.clickOnly(), ability.getAbilityId());
            EntityActionInstance action = stand.getCurStandAction();
            helper.assertTrue(held != null && action != null && held.action == action && action.ability == ability
                            && action.getPhase() == ActionPhase.BUTTON_CHARGE
                            && action.getCurPhaseLength() == chargeTicks,
                    "1.16 " + ability.name() + " was holdToFire(" + chargeTicks + ", false) on the Stand's task; got "
                            + action + " " + (action != null ? action.getPhase() + " " + action.getCurPhaseLength() : ""));
            return action;
        }

        // as ClAbilityInputPacket: the key press only after AbilityInput.withConditionCheck lets it through
        private boolean pressAsServer(CreamStandActionAbility ability) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null, ability.name() + " is not in the moveset");
            if (!AbilityInput.withConditionCheck(selected, player, InputMethod.HOLD)) {
                return false;
            }
            AbilityInput.keyPress(KEY, selected.ability, player, null, InputMethod.HOLD, 0,
                    BufferingState.clickCanBuffer(), ability.getAbilityId());
            return true;
        }

        // the release reaches the action now; the Stand clears a stopped action on its next tick
        private void release() {
            AbilityInput.keyRelease(KEY, player);
            if (stand.getCurStandAction() != null && stand.getCurStandAction().isOver()) {
                stand.getStandActionComponent().tick();
            }
        }

        private void runToEnd(EntityActionInstance action) {
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == action; tick++) {
                stand.getStandActionComponent().tick();
            }
            helper.assertTrue(stand.getCurStandAction() != action && action.isOver(),
                    "The " + action.ability + " Stand action did not end");
            release();
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, player);
            if (power.isSummoned()) {
                FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(player, power);
            }
            player.discard();
            cow.discard();
        }
    }
}
