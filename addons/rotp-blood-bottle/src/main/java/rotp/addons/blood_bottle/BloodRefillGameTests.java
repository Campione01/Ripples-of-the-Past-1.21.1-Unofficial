package rotp.addons.blood_bottle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.blood_bottle.ability.BloodRefillAbility;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;

@GameTestHolder(BloodBottleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BloodRefillGameTests {
    private static final short KEY = 23;

    private BloodRefillGameTests() {}

    @GameTest(template = "empty")
    public static void refillUsesSelectedEmptyHand(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().is(BloodBottleAddon.BLOOD_BOTTLE.get())
                            && fixture.player.getInventory().getItem(0).isEmpty(),
                    "Refill went to an earlier empty slot instead of the selected empty hand");
            fixture.assertPayment(false);
        });
    }

    @GameTest(template = "empty")
    public static void occupiedHandIsNotOverwritten(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().is(Items.STICK)
                            && fixture.player.getInventory().getItem(0).is(BloodBottleAddon.BLOOD_BOTTLE.get()),
                    "Occupied hand was overwritten or filled bottle was lost");
            fixture.assertPayment(false);
        });
    }

    @GameTest(template = "empty")
    public static void existingBottleStackStillMerges(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.player.getInventory().setItem(0, new ItemStack(BloodBottleAddon.BLOOD_BOTTLE.get(), 2));
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty()
                            && fixture.player.getInventory().getItem(0).getCount() == 3,
                    "Existing bottle stack did not merge or an extra stack was created");
            fixture.assertPayment(false);
        });
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsExactlyOneBottle(GameTestHelper helper) {
        run(helper, fixture -> {
            for (int slot = 0; slot < 36; slot++) {
                fixture.player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            fixture.perform();
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    fixture.player.getBoundingBox().inflate(2),
                    entity -> entity.getItem().is(BloodBottleAddon.BLOOD_BOTTLE.get()));
            try {
                helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                        "Full inventory lost or duplicated the output bottle");
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.STONE), "Full hand was overwritten");
                fixture.assertPayment(false);
            } finally {
                drops.forEach(ItemEntity::discard);
            }
        });
    }

    @GameTest(template = "empty")
    public static void creativeUsesEmptyHandWithoutDebitOrDuplication(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.player.getAbilities().instabuild = true;
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().is(BloodBottleAddon.BLOOD_BOTTLE.get()),
                    "Creative refill missed the selected empty hand");
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().getCount() == 1
                            && fixture.player.getInventory().getItem(0).isEmpty(),
                    "Creative refill duplicated a held bottle");
            fixture.assertPayment(true);
        });
    }

    @GameTest(template = "empty")
    public static void lowBloodDoesNotConsumeContainer(GameTestHelper helper) {
        run(helper, fixture -> {
            VampirismState.get(fixture.player).blood().setCurrent(0);
            helper.assertTrue(!fixture.ability.checkSpecificConditions(PowerClass.PLAYER_POWER.get(fixture.player)).isPositive(),
                    "Insufficient blood passed refill admission");
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty()
                            && fixture.player.getOffhandItem().getCount() == 2
                            && VampirismState.get(fixture.player).blood().current() == 0,
                    "Rejected refill consumed a container or granted a bottle");
        });
    }

    @GameTest(template = "empty")
    public static void missingOffhandContainerDoesNotPay(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(!fixture.ability.checkSpecificConditions(PowerClass.PLAYER_POWER.get(fixture.player)).isPositive(),
                    "Missing container passed refill admission");
            fixture.perform();
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty()
                            && VampirismState.get(fixture.player).blood().current() == fixture.initialBlood,
                    "Missing-container refill paid or created a bottle");
        });
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran BloodRefill.checkSpecificConditions on every tick of the
    // holdToFire(5, false) hold and sent the failed check's message as it ended the hold.
    @GameTest(template = "empty")
    public static void emptiedOffHandEndsTheHeldRefillWithNoVolume(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.press();
            fixture.tick(2);
            helper.assertTrue(fixture.running(hold) && hold.getPhase() == ActionPhase.WINDUP,
                    "fixture: the refill must still be held: " + fixture.state(hold));
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            fixture.tick(1);
            helper.assertTrue(!fixture.running(hold),
                    "1.16 ended the held refill once the off hand held no glass bottle; it still runs: "
                            + fixture.state(hold));
            helper.assertTrue(fixture.hasMessage("jojo.message.action_condition.no_volume"),
                    "1.16 named no_volume when the held refill ended: " + fixture.player.messages);
            fixture.tick(8);
            fixture.assertNothingFilled("after the emptied off hand ended the hold", 0, fixture.initialBlood);
        });
    }

    @GameTest(template = "empty")
    public static void bloodShortOfTheRefillEndsTheHeldRefillWithNoEnergy(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.press();
            fixture.tick(2);
            helper.assertTrue(fixture.running(hold) && hold.getPhase() == ActionPhase.WINDUP,
                    "fixture: the refill must still be held: " + fixture.state(hold));
            float shortBlood = fixture.cost / 2.0F;
            VampirismState.get(fixture.player).blood().setCurrent(shortBlood);
            fixture.tick(1);
            helper.assertTrue(!fixture.running(hold),
                    "1.16 ended the held refill once the blood fell short of its cost; it still runs: "
                            + fixture.state(hold));
            helper.assertTrue(fixture.hasMessage("jojo.message.action_condition.no_energy_vampirism"),
                    "1.16 named no_energy_vampirism when the held refill ended: " + fixture.player.messages);
            fixture.tick(8);
            fixture.assertNothingFilled("after the blood ran short", 2, shortBlood);
        });
    }

    // 1.16 tickHeldAction gave the hold's last tick to stopHeldAction(true), which ran checkRequirements once more,
    // silently: a stunned user filled nothing and heard nothing. The port's hold fires and ends inside that tick.
    @GameTest(template = "empty")
    public static void refillChecksOnceMoreAsTheHoldFires(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.press();
            for (int tick = 0; tick < 20 && hold.getPhase() == ActionPhase.WINDUP
                    && hold.getPhaseTick() < hold.getCurPhaseLength() - 1; tick++) {
                fixture.tick(1);
            }
            helper.assertTrue(fixture.running(hold) && hold.getPhase() == ActionPhase.WINDUP
                            && hold.getPhaseTick() >= hold.getCurPhaseLength() - 1,
                    "fixture: the hold must stand at the start of its last tick: " + fixture.state(hold));
            fixture.assertNothingFilled("before the hold's last tick", 2, fixture.initialBlood);
            fixture.player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
            fixture.tick(3);
            fixture.assertNothingFilled("when its user was stunned as the hold fired", 2, fixture.initialBlood);
            helper.assertTrue(fixture.player.messages.isEmpty(),
                    "1.16 fired nothing, silently, when the last check failed: " + fixture.player.messages);
        });
    }

    // The refill's own held checks (no_volume, no_energy_vampirism) end an ordinary held tick with their message; in the
    // tick the hold fires in they are 1.16 stopHeldAction(true)'s, which fires nothing and says nothing.
    @GameTest(template = "empty")
    public static void emptiedOffHandAsTheHoldFiresEndsItSilently(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.pressAndStandAtTheFiringTick();
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            fixture.tick(3);
            helper.assertTrue(!fixture.running(hold), "the refill still runs after its failed last check: " + fixture.state(hold));
            fixture.assertNothingFilled("when the off hand was emptied as the hold fired", 0, fixture.initialBlood);
            helper.assertTrue(fixture.player.messages.isEmpty(),
                    "1.16 fired nothing, silently, when the last check failed: " + fixture.player.messages);
        });
    }

    @GameTest(template = "empty")
    public static void bloodShortAsTheHoldFiresEndsItSilently(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.pressAndStandAtTheFiringTick();
            float shortBlood = fixture.cost / 2.0F;
            VampirismState.get(fixture.player).blood().setCurrent(shortBlood);
            fixture.tick(3);
            helper.assertTrue(!fixture.running(hold), "the refill still runs after its failed last check: " + fixture.state(hold));
            fixture.assertNothingFilled("when the blood ran short as the hold fired", 2, shortBlood);
            helper.assertTrue(fixture.player.messages.isEmpty(),
                    "1.16 fired nothing, silently, when the last check failed: " + fixture.player.messages);
        });
    }

    @GameTest(template = "empty")
    public static void aFullHoldFillsOneBottle(GameTestHelper helper) {
        run(helper, fixture -> {
            EntityActionInstance hold = fixture.press();
            for (int tick = 0; tick < 20 && fixture.running(hold); tick++) {
                fixture.tick(1);
            }
            helper.assertTrue(fixture.player.getMainHandItem().is(BloodBottleAddon.BLOOD_BOTTLE.get()),
                    "the full hold filled no bottle: " + fixture.state(hold));
            fixture.assertPayment(false);
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> test) {
        try (Fixture fixture = new Fixture(helper)) {
            test.accept(fixture);
            helper.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final RecordingPlayer player;
        private final BloodRefillAbility ability;
        private final float initialBlood;
        private final float cost;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new RecordingPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BottleProbe"));
            player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            player.setNoGravity(true);
            helper.getLevel().addNewPlayer(player);
            var power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            var data = PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, player);
            initialBlood = data.getMaxBlood(player);
            VampirismState.get(player).blood().setCurrent(initialBlood);
            data.setBloodLevel(initialBlood);
            cost = BloodBottleEconomy.refillCost(initialBlood, BloodBottleConfig.FILL_BOTTLE_MULTIPLIER.get());
            player.getInventory().clearContent();
            player.getInventory().selected = 7;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
            var registered = power.getAbility("vampirism_blood_refill");
            helper.assertTrue(registered instanceof BloodRefillAbility, "Missing registered refill extension");
            ability = (BloodRefillAbility) registered;
            helper.assertTrue(ability.checkSpecificConditions(power).isPositive(), "Valid refill fixture rejected");
        }

        private void perform() {
            var action = ability.initActionOnAbilityUse(helper.getLevel(), player, player, null);
            action.setPhaseStart(ActionPhase.PERFORM);
            var component = LivingComponentAction.getComponent(player);
            component.setAction(action, player, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "Registered refill action was not installed");
            action._tickAction();
            component.setAction(null, player, SyncType.NO_SYNC);
        }

        // The server half of the SHIFT hold on the Blood Gift slot: the admission, then the key press.
        private EntityActionInstance press() {
            helper.assertTrue(AbilityInput.withConditionCheck(ability, player),
                    "the refill press was refused: " + player.messages);
            BufferingState buffering = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD, 0.0F, buffering,
                    ability.getAbilityId());
            EntityActionInstance action = LivingComponentAction.getComponent(player).getAction();
            helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == ability
                            && action.getPhase() == ActionPhase.WINDUP && AbilityInput.isHeldByKey(player, action),
                    "the press did not start a held refill: " + action);
            return action;
        }

        private EntityActionInstance pressAndStandAtTheFiringTick() {
            EntityActionInstance hold = press();
            for (int tick = 0; tick < 20 && hold.getPhase() == ActionPhase.WINDUP && !hold.isChargeFiringTick(); tick++) {
                tick(1);
            }
            helper.assertTrue(running(hold) && hold.getPhase() == ActionPhase.WINDUP && hold.isChargeFiringTick(),
                    "fixture: the hold must stand at the start of its firing tick: " + state(hold));
            assertNothingFilled("before the hold's last tick", 2, initialBlood);
            return hold;
        }

        // The user's action is ticked by hand within one game tick, so regeneration and effects cannot step in.
        private void tick(int count) {
            LivingComponentAction component = LivingComponentAction.getComponent(player);
            for (int tick = 0; tick < count; tick++) {
                component.tick();
            }
        }

        private boolean running(EntityActionInstance action) {
            return LivingComponentAction.getComponent(player).getAction() == action && !action.isOver();
        }

        private boolean hasMessage(String key) {
            for (Component message : player.messages) {
                if (message.getContents() instanceof TranslatableContents translatable
                        && translatable.getKey().equals(key)) {
                    return true;
                }
            }
            return false;
        }

        private void assertNothingFilled(String when, int glassBottles, float blood) {
            helper.assertTrue(player.getMainHandItem().isEmpty() && player.getInventory().getItem(0).isEmpty()
                            && player.getOffhandItem().getCount() == glassBottles,
                    "1.16 filled no bottle " + when + ": main hand " + player.getMainHandItem()
                            + ", off hand " + player.getOffhandItem());
            float current = VampirismState.get(player).blood().current();
            helper.assertTrue(Math.abs(current - blood) < 0.001F,
                    "1.16 took no blood " + when + ": " + current + ", expected " + blood);
        }

        private String state(EntityActionInstance action) {
            return "[phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick() + " over=" + action.isOver()
                    + " current=" + running(action) + " heldByKey=" + AbilityInput.isHeldByKey(player, action)
                    + " offHand=" + player.getOffhandItem()
                    + " blood=" + VampirismState.get(player).blood().current()
                    + " stunned=" + ModStatusEffects.isStunned(player) + "]";
        }

        private void assertPayment(boolean creative) {
            helper.assertTrue(player.getOffhandItem().getCount() == (creative ? 2 : 1),
                    "Incorrect glass bottle debit");
            helper.assertTrue(Math.abs(VampirismState.get(player).blood().current()
                            - (initialBlood - (creative ? 0 : cost))) < 0.001F,
                    "Incorrect blood debit");
        }

        @Override
        public void close() {
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            LivingComponentAction.getComponent(player).setAction(null, player, SyncType.NO_SYNC);
            player.removeAllEffects();
            player.getInventory().clearContent();
            player.discard();
        }
    }

    // Keeps the action-bar messages a FakePlayer would drop.
    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();

        private RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }
    }
}
