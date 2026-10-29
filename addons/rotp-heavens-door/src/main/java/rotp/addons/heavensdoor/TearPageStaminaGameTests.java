package rotp.addons.heavensdoor;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.ability.TearOutAPageAbility.TearPageAction;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 TearOutAPage had no stamina condition: the hold-to-fire tear starts on any bar, and StandEntityTask paid
 * its staminaCost(100) after the perform, ignoring the result, so a short bar drops to 0 without a message.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TearPageStaminaGameTests {
    private static final short KEY = 123;
    private static final float SHORT_BAR = 10.0F;

    private TearPageStaminaGameTests() {}

    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageRunsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageRunsOnAShortBarAndDrainsItInLoadedArea(helper));
    }

    private static void tearPageRunsOnAShortBarAndDrainsItInLoadedArea(GameTestHelper helper) {
        RecordingPlayer owner = new RecordingPlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "HdTearStamina"));
        owner.getAbilities().instabuild = false;
        owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D)));
        helper.getLevel().addNewPlayer(owner);
        Cow cow = EntityType.COW.create(helper.getLevel());
        StandPower power = PowerClass.STAND.attachGet(owner);
        HeavensDoorStandEntity[] standRef = new HeavensDoorStandEntity[1];
        // the bar regenerates every tick, so the lowest value seen right after each Stand tick is the paid one
        float[] lowest = {Float.MAX_VALUE};
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (standRef[0] != null && event.getEntity() == standRef[0]) {
                lowest[0] = Math.min(lowest[0], power.getStamina());
            }
        };
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease(KEY, owner);
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            if (cow != null) {
                cow.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        };
        try {
            helper.assertTrue(cow != null, "Cow fixture failed");
            cow.setNoAi(true);
            cow.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 2.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            standRef[0] = stand;
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.assertTrue(BookStateService.markFromHit(stand, cow), "Book marking failed");
            HeavensDoorGameTestAims.lookAt(owner, cow);
            helper.assertTrue(BookStateService.authorizedTarget(owner, cow) == cow, "Fixture book is not authorized");

            power.tick(); // moves are cached per tick
            power.setStamina(SHORT_BAR);
            Ability tear = power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(tear);
            helper.assertTrue(selected != null, "Tear Out a Page is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "1.16 had no stamina check: Tear Out a Page must be admitted at " + SHORT_BAR + " stamina, got "
                            + selected.conditionCheck.getWarning());
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            helper.assertTrue(press.isActionSuccess && stand.getCurStandAction() instanceof TearPageAction,
                    "the admitted press did not start Tear Out a Page");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        helper.runAfterDelay(BookStateRules.TEAR_HOLD_TICKS + 10, () -> {
            try {
                helper.assertTrue(HeavensDoorAddon.bookState(cow).pageCount() == 1,
                        "the charged tear did not take a page on a short bar: pages "
                                + HeavensDoorAddon.bookState(cow).pageCount());
                helper.assertTrue(lowest[0] == 0.0F,
                        "the 100 stamina cost must drain a short bar to 0: lowest " + lowest[0]);
                helper.assertTrue(owner.staminaMessages().isEmpty(),
                        "1.16 sent no stamina message: " + owner.staminaMessages());
            }
            finally {
                close.run();
            }
            helper.succeed();
        });
    }

    // Keeps the action-bar messages a FakePlayer would drop.
    private static final class RecordingPlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();

        RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        List<String> staminaMessages() {
            List<String> found = new ArrayList<>();
            for (Component message : messages) {
                String text = message.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey()
                        : message.getString();
                if (text.contains("stamina")) {
                    found.add(text);
                }
            }
            return found;
        }
    }
}
