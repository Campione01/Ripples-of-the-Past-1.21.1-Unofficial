package rotp.addons.hermitpurple.ability;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Hermit Purple had no stamina gate of its own except HPThorns (any stamina above 0, checked on every held
 * tick, refused silently): StandEntityTask paid every cost after the perform and every ticking cost on every perform
 * tick, ignoring both results, and StandPower.consumeStamina drained a short bar to 0.
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitStaminaGameTests {
    private static final short KEY = 91;
    private static final float SHORT_BAR = 10.0F;
    private static final float DOXX_BAR = 50.0F;

    private HermitStaminaGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void vineAttackFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
        begin(helper, Case.VINE_ATTACK);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void grappleStartsAndHoldsOnAnEmptyBar(GameTestHelper helper) {
        begin(helper, Case.GRAPPLE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void grabThrowsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        begin(helper, Case.GRAB);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void cringeRunsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        begin(helper, Case.CRINGE);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void thornsNeedAnyStaminaAndEndSilentlyOnAnEmptyBar(GameTestHelper helper) {
        begin(helper, Case.THORNS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void divinationIsNotRefusedOnAnEmptyBar(GameTestHelper helper) {
        begin(helper, Case.DOXX);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_stamina")
    public static void divinationWithNoTargetStillPaysItsCost(GameTestHelper helper) {
        begin(helper, Case.DOXX_NO_TARGET);
    }

    private enum Case {
        VINE_ATTACK("vine_attack", 12, HermitPurpleAddon.HP_VINE_ATTACK_NAME),
        GRAPPLE("grapple", 22, HermitPurpleAddon.HP_VINE_NAME),
        GRAB("grab", 32, HermitPurpleAddon.HP_GRAB_NAME),
        CRINGE("cringe", 42, HermitPurpleAddon.CRINGE_NAME),
        THORNS("thorns", 52, HermitPurpleAddon.HP_BLOCK_NAME),
        DOXX("doxx", 62, HermitPurpleAddon.HP_DOXX_NAME),
        DOXX_NO_TARGET("doxx_no_target", 72, HermitPurpleAddon.HP_DOXX_NAME);

        final String label;
        final int height;
        final String ability;

        Case(String label, int height, String ability) {
            this.label = label;
            this.height = height;
            this.ability = ability;
        }
    }

    private static void begin(GameTestHelper helper, Case kind) {
        Fixture fixture = new Fixture(helper, kind);
        helper.testInfo.addListener(fixture);
        fixture.guarded(fixture::prepare);
    }

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Case kind;
        FakePlayer player;
        StandPower power;
        HermitPurpleStandEntity stand;
        Ability ability;
        EntityActionInstance started;
        final Consumer<EntityJoinLevelEvent> joinListener = this::joined;
        int lashesJoined;
        int heldVinesJoined;
        float staminaAtFirstVine = -1.0F;
        int waited;
        boolean closed;

        Fixture(GameTestHelper helper, Case kind) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.kind = kind;
        }

        void prepare() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int chunkX = origin.getX() & ~15;
            int chunkZ = origin.getZ() & ~15;
            int y = origin.getY() + kind.height;
            AABB corridor = new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 8, chunkZ + 15);
            helper.assertTrue(level.getBlockStates(corridor).allMatch(state ->
                            state.isAir() && state.getFluidState().isEmpty()),
                    "Stamina fixture needs an existing empty, dry corridor");
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPStamina" + kind.ordinal()));
            player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            player.setXRot(0.0F);
            level.addNewPlayer(player);
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition()),
                    "Stamina fixture is outside an entity-ticking chunk");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "Normal summon produced no Hermit controller");
            helper.assertTrue(!power.isStaminaInfinite(), "Fixture: the stamina bar is infinite");
            ability = power.getAbility(kind.ability);
            helper.assertTrue(ability != null, "Hermit Purple has no " + kind.ability);
            later(stand.summonLockTicks + 2, this::press);
        }

        void press() {
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            NeoForge.EVENT_BUS.addListener(joinListener);
            switch (kind) {
                case VINE_ATTACK, CRINGE -> {
                    power.setStamina(SHORT_BAR);
                    admitted();
                    fire(InputMethod.CLICK);
                    later(1, this::watchShortBar);
                }
                case GRAB -> {
                    power.setStamina(SHORT_BAR);
                    admitted();
                    fire(InputMethod.HOLD);
                    later(1, this::watchShortBar);
                }
                case GRAPPLE -> {
                    power.setStamina(0.0F);
                    admitted();
                    fire(InputMethod.HOLD);
                    later(6, this::checkGrappleHeld);
                }
                case THORNS -> thorns();
                case DOXX -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.MAP));
                    power.setStamina(0.0F);
                    admitted();
                    pass();
                }
                case DOXX_NO_TARGET -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.MAP));
                    stand.setTargetSelection(HermitPurpleSpec.TARGET_MODE_PLAYER, "HPStaminaNobody");
                    power.setStamina(DOXX_BAR);
                    admitted();
                    fire(InputMethod.CLICK);
                    later(1, this::watchDivination);
                }
            }
        }

        void admitted() {
            ConditionCheck check = ability.checkConditions(power);
            helper.assertTrue(check.isPositive(), "1.16 had no stamina check: " + kind.ability
                    + " must be admitted at " + power.getStamina() + " stamina, got "
                    + (check.getWarning() != null ? check.getWarning().getString() : "a silent refusal"));
        }

        void fire(InputMethod method) {
            BufferingState buffering = BufferingState.clickOnly();
            ability.onKeyPress(level, player, null, method, 0.0F, buffering);
            helper.assertTrue(buffering.isActionSuccess, kind.ability + " did not start on the admitted press");
            started = stand.getCurStandAction();
            LogUtils.getLogger().info("R769_HP_STAMINA_INPUT case={} stamina={} action={}",
                    kind.label, power.getStamina(), started);
        }

        void joined(EntityJoinLevelEvent event) {
            if (closed || event.getLevel() != level) return;
            Entity entity = event.getEntity();
            boolean lash = entity instanceof HermitVineProjectileEntity vine && vine.getOwner() == stand;
            boolean held = entity instanceof HermitGrappleVineEntity vine && vine.getOwner() == stand;
            if (!lash && !held) return;
            if (staminaAtFirstVine < 0.0F) staminaAtFirstVine = power.getStamina();
            if (lash) lashesJoined++;
            if (held) heldVinesJoined++;
        }

        // The cost is paid when the perform starts; regeneration is off while the Stand acts.
        void watchShortBar() {
            boolean performed = switch (kind) {
                case VINE_ATTACK -> lashesJoined >= 2;
                case GRAB -> heldVinesJoined >= 1;
                case CRINGE -> started != null && (started.isOver() || started.getPhase() == ActionPhase.RECOVERY);
                default -> false;
            };
            if (!performed) {
                EntityActionInstance action = stand.getCurStandAction();
                helper.assertTrue(++waited < 30, kind.ability + " never performed on a " + SHORT_BAR
                        + " bar; lashes=" + lashesJoined + " heldVines=" + heldVinesJoined + " action="
                        + (action == null ? "none" : action.ability.getAbilityId() + "/" + action.getPhase()));
                later(1, this::watchShortBar);
                return;
            }
            helper.assertTrue(kind == Case.CRINGE || staminaAtFirstVine == SHORT_BAR,
                    "the vine must be out before the cost is paid: stamina at its spawn " + staminaAtFirstVine);
            helper.assertTrue(power.getStamina() == 0.0F, "the " + kind.ability + " cost drains a "
                    + SHORT_BAR + " bar to 0, the bar holds " + power.getStamina());
            pass();
        }

        // A player name nobody has: the perform finds no target, makes no map and still pays the 30.
        void watchDivination() {
            if (started != null && !started.isOver() && started.getPhase() != ActionPhase.RECOVERY) {
                helper.assertTrue(power.getStamina() == DOXX_BAR, "the divination paid before its perform: "
                        + power.getStamina() + " in " + started.getPhase());
                helper.assertTrue(++waited < 40, "the divination never reached its perform, phase "
                        + started.getPhase());
                later(1, this::watchDivination);
                return;
            }
            helper.assertTrue(started != null, "the divination did not start");
            helper.assertTrue(player.getMainHandItem().isEmpty() && player.getOffhandItem().is(Items.MAP),
                    "a divination with no target made a map: main=" + player.getMainHandItem()
                            + " off=" + player.getOffhandItem());
            helper.assertTrue(power.getStamina() == DOXX_BAR - HermitPurpleSpec.DOXX_STAMINA,
                    "1.16 HPDoxx paid its " + HermitPurpleSpec.DOXX_STAMINA + " after every perform, also with no"
                            + " target: a " + DOXX_BAR + " bar holds " + power.getStamina());
            pass();
        }

        void checkGrappleHeld() {
            EntityActionInstance action = stand.getCurStandAction();
            helper.assertTrue(action != null && action == started && action.getPhase() == ActionPhase.PERFORM,
                    "1.16 hp_vine ignored its ticking cost: the hold must go on on an empty bar, the action is "
                            + (action == null ? "gone" : action.getPhase()));
            helper.assertTrue(heldVinesJoined == 1, "the grapple threw " + heldVinesJoined + " vines on an empty bar");
            helper.assertTrue(power.getStamina() == 0.0F, "the held grapple bar is " + power.getStamina());
            pass();
        }

        void thorns() {
            power.setStamina(0.5F);
            admitted();
            power.setStamina(0.0F);
            ConditionCheck empty = ability.checkConditions(power);
            if (empty.isPositive() || empty.getWarning() != null) {
                helper.fail("1.16 HPThorns refused an empty bar without a message, got "
                        + (empty.isPositive() ? "an admission" : empty.getWarning().getString()));
            }
            power.setStamina(2.5F);
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD, 0.0F, buffering, ability.getAbilityId());
            started = stand.getCurStandAction();
            helper.assertTrue(buffering.isActionSuccess && started != null,
                    "the held thorns block did not start on a 2.5 bar");
            later(1, this::watchThorns);
        }

        // 1 a tick: 2.5 -> 1.5 -> 0.5 -> 0, then the held check ends the block while the key is still down.
        void watchThorns() {
            EntityActionInstance action = stand.getCurStandAction();
            boolean holding = action == started && action.getPhase() == ActionPhase.PERFORM;
            if (holding) {
                // Owner rule of 2026-10-15: every Stand's own guard halves its user's walk speed.
                helper.assertTrue(waited > 0 || action.userWalkSpeed == 0.5F,
                        "the held thorns block must halve the user's walk speed, it is " + action.userWalkSpeed);
                helper.assertTrue(++waited < 10, "the held thorns block outlived an empty bar, stamina "
                        + power.getStamina());
                later(1, this::watchThorns);
                return;
            }
            helper.assertTrue(waited >= 2, "the held thorns block ended after " + waited
                    + " ticks, before its 2.5 bar was spent");
            helper.assertTrue(power.getStamina() == 0.0F, "the thorns block ended with " + power.getStamina()
                    + " stamina left");
            pass();
        }

        void pass() {
            LogUtils.getLogger().info("R769_HP_STAMINA_PASS {}", kind.label);
            close();
            helper.succeed();
        }

        void later(int ticks, Runnable task) {
            helper.runAfterDelay(ticks, () -> guarded(task));
        }

        void guarded(Runnable task) {
            if (closed || helper.testInfo.isDone()) return;
            try {
                task.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(joinListener);
            try {
                if (player != null) AbilityInput.keyRelease(KEY, player);
                if (power != null && power.hasPower() && power.isSummoned()) {
                    power.getPowerType().forceUnsummon(player, power);
                }
            }
            finally {
                if (player != null) {
                    for (Entity vine : level.getEntities((Entity) null, player.getBoundingBox().inflate(96.0D),
                            entity -> entity instanceof HermitVineProjectileEntity
                                    || entity instanceof HermitGrappleVineEntity)) {
                        vine.discard();
                    }
                    if (!player.isRemoved()) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
