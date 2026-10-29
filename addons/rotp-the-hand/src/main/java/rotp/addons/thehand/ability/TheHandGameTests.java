package rotp.addons.thehand.ability;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.thehand.TheHandAddon;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheHandAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheHandGameTests {
    private static final short ERASE_KEY = 81;
    private static final short BARRAGE_KEY = 82;
    private static final short ERASE_ITEM_KEY = 83;
    private static final short PUNCH_KEY = 84;
    private static final double TELEPORT_DISTANCE_SQR = 16.0D;
    private static final float SHORT_BAR = 5.0F;

    private TheHandGameTests() {}

    @GameTest(
            template = "empty",
            batch = "the_hand_erase",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissTeleportsBeforeActionCompletion(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        Vec3 start = context.owner().position();
        helper.assertTrue(
                action instanceof TheHandEraseAbility.EraseAction
                        && action.getPhase()
                                == ActionPhase.BUTTON_CHARGE,
                "Erase did not enter its production button-charge phase.");

        boolean[] released = {false};
        helper.succeedWhen(() -> {
            if (!released[0]) {
                helper.assertTrue(
                        LivingComponentAction.getCurEntityAction(
                                context.stand()) == action
                                && action.getPhase()
                                        == ActionPhase.BUTTON_CHARGE,
                        "Erase left button charge while waiting for "
                                + "its production charge threshold.");
                float releaseThreshold = action.phasesLength.getFloat(
                        ActionPhase.BUTTON_CHARGE) / 2.0F;
                helper.assertTrue(
                        action.getPhaseTick() >= releaseThreshold,
                        "Erase had not reached its production release "
                                + "threshold. phaseTick="
                                + action.getPhaseTick()
                                + ", threshold="
                                + releaseThreshold
                                + ", standTickCount="
                                + context.stand().tickCount
                                + ", standRemoved="
                                + context.stand().isRemoved()
                                + ", standChunkEntityTicking="
                                + helper.getLevel().isPositionEntityTicking(
                                        context.stand().blockPosition()));
                helper.assertTrue(
                        context.owner().position().distanceToSqr(start)
                                < 1.0D,
                        "Erase teleported before its perform phase.");
                AbilityInput.keyRelease(ERASE_KEY, context.owner());
                released[0] = true;
            }
            helper.assertTrue(
                    ((TheHandEraseAbility.EraseAction) action)
                            .missTeleportOccurredDuringPerform(),
                    "Erase deferred the miss teleport until action clear.");
            helper.assertTrue(
                    context.owner().position().distanceToSqr(start)
                            > TELEPORT_DISTANCE_SQR,
                    "Erase miss did not teleport at the "
                            + "PERFORM-to-RECOVERY boundary.");
            cleanup(context);
        });
        });
    }

    // 1.16 stopHeldAction(true): a charge let go past its threshold fired
    // only if its requirements still passed. A Stand stunned in the same
    // tick drops the released Erase instead of starting its windup.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_recheck",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseReleaseRechecksBeforeFiring(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        Vec3 start = context.owner().position();
        boolean[] released = {false};
        boolean[] fired = {false};
        helper.succeedWhen(() -> {
            if (!released[0]) {
                float releaseThreshold = action.phasesLength.getFloat(
                        ActionPhase.BUTTON_CHARGE) / 2.0F;
                helper.assertTrue(
                        action.getPhase() == ActionPhase.BUTTON_CHARGE
                                && action.getPhaseTick() >= releaseThreshold,
                        "Erase had not reached its release threshold.");
                context.stand().addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN, 40));
                AbilityInput.keyRelease(ERASE_KEY, context.owner());
                released[0] = true;
                fired[0] = !action.isOver()
                        || action.getPhase() == ActionPhase.WINDUP;
            }
            // Kept failing on every retry, so a fired Erase cannot pass later.
            helper.assertFalse(
                    fired[0],
                    "A released Erase fired although its Stand "
                            + "was stunned.");
            helper.assertTrue(
                    context.owner().position().distanceToSqr(start)
                            < 1.0D,
                    "A dropped Erase still teleported its user.");
            cleanup(context);
        });
        });
    }

    // 1.16 StandEntityHeavyAttack.checkStandConditions (canAttackMelee) ran
    // on the press and, through tickHeldAction, on every held tick: a Stand
    // left without reach drops the Erase charge unfired and unpaid, and
    // cannot start another.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_melee_recheck",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseChargeStopsWhenTheStandCannotAttackInMelee(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        standActions.tick();
        helper.assertTrue(
                !action.isOver()
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                "Erase did not keep charging while its Stand could attack.");
        float stamina = context.power().getStamina();
        Vec3 start = context.owner().position();
        AttributeInstance reach = context.stand().getAttribute(
                Attributes.ENTITY_INTERACTION_RANGE);
        double reachBefore = reach.getBaseValue();
        reach.setBaseValue(0.0D);
        try {
            helper.assertTrue(
                    !context.stand().canAttackMelee(),
                    "Setup: The Hand still attacks in melee without reach.");
            standActions.tick();
            helper.assertTrue(
                    action.isOver()
                            && LivingComponentAction.getCurEntityAction(
                                    context.stand()) == null,
                    "1.16 tickHeldAction dropped the Erase charge once its "
                            + "Stand could not attack in melee: phase "
                            + action.getPhase());
            for (int tick = 0; tick < 30; tick++) {
                standActions.tick();
            }
            helper.assertTrue(
                    context.owner().position().distanceToSqr(start) < 1.0D
                            && context.power().getStamina() == stamina,
                    "A dropped Erase still swiped, teleported or paid: "
                            + "stamina " + stamina + " -> "
                            + context.power().getStamina());
            AbilityInput.keyRelease(ERASE_KEY, context.owner());
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(
                    ERASE_KEY,
                    ability,
                    context.owner(),
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    pressed,
                    ability.abilityId);
            helper.assertTrue(
                    !pressed.isActionSuccess
                            && LivingComponentAction.getCurEntityAction(
                                    context.stand()) == null,
                    "1.16 refused an Erase pressed while its Stand could "
                            + "not attack in melee.");
        }
        finally {
            reach.setBaseValue(reachBefore);
            AbilityInput.keyRelease(ERASE_KEY, context.owner());
        }
        cleanup(context);
        helper.succeed();
        });
    }

    @GameTest(
            template = "empty",
            batch = "the_hand_erasure_barrage",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasureBarrageMissTeleportsOnHoldRelease(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASURE_BARRAGE_NAME);
        helper.assertTrue(
                ability instanceof TheHandErasureBarrageAbility,
                "The production moveset did not resolve the erasure "
                        + "barrage ability.");
        onceStandTicks(helper, context, () -> {
        pressHold(
                helper,
                context,
                ability,
                BARRAGE_KEY,
                TheHandErasureBarrageAbility
                        .ErasureBarrageAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        Vec3 start = context.owner().position();
        helper.assertTrue(
                action instanceof TheHandErasureBarrageAbility
                                .ErasureBarrageAction
                        && action.getPhase() == ActionPhase.PERFORM,
                "Erasure barrage did not enter its production "
                        + "perform phase.");

        boolean[] released = {false};
        helper.succeedWhen(() -> {
            if (!released[0]) {
                helper.assertTrue(
                        LivingComponentAction.getCurEntityAction(
                                context.stand()) == action
                                && action.getPhase()
                                        == ActionPhase.PERFORM,
                        "Erasure barrage was not performing while "
                                + "waiting for its first server tick.");
                helper.assertTrue(
                        ((TheHandErasureBarrageAbility
                                        .ErasureBarrageAction) action)
                                .hasPerformedServerTick(),
                        "Erasure barrage had not executed a production "
                                + "perform tick before release. phaseTick="
                                + action.getPhaseTick()
                                + ", standTickCount="
                                + context.stand().tickCount
                                + ", gameTime="
                                + context.stand().level().getGameTime());
                helper.assertTrue(
                        context.owner().position().distanceToSqr(start)
                                < 1.0D,
                        "Erasure barrage teleported before HOLD release.");

                AbilityInput.keyRelease(BARRAGE_KEY, context.owner());
                released[0] = true;
            }

            helper.assertTrue(
                    LivingComponentAction.getCurEntityAction(
                            context.stand()) == action
                            && action.getPhase()
                                    == ActionPhase.RECOVERY,
                    "Erasure barrage cleared instead of entering recovery.");
            helper.assertTrue(
                    ((TheHandErasureBarrageAbility
                                    .ErasureBarrageAction) action)
                            .missTeleportOccurredDuringPerform(),
                    "Erasure barrage deferred the miss teleport "
                            + "until action clear.");
            helper.assertTrue(
                    context.owner().position().distanceToSqr(start)
                            > TELEPORT_DISTANCE_SQR,
                    "Erasure barrage miss did not teleport while "
                            + "leaving PERFORM.");
            cleanup(context);
        });
        });
    }

    // 1.16 Erase had no stamina condition: it starts on a short bar, swipes,
    // and StandEntityTask's 150 cost drains the bar to 0.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_short_bar",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseFiresOnAShortBarAndDrainsIt(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        float[] staminaAtErase = {-1.0F};
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() == context.stand()
                    && staminaAtErase[0] < 0.0F
                    && LivingComponentAction.getCurEntityAction(
                                    context.stand())
                            instanceof TheHandEraseAbility.EraseAction erase
                    && erase.eraseTimingSnapshot()
                            .eraseCallbackGameTick() >= 0L) {
                staminaAtErase[0] = context.power().getStamina();
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        // A failed or timed-out run must not leave the listener on the bus.
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                NeoForge.EVENT_BUS.unregister(standTick);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                NeoForge.EVENT_BUS.unregister(standTick);
            }

            @Override
            public void testAddedForRerun(
                    GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
                NeoForge.EVENT_BUS.unregister(standTick);
            }
        });
        onceStandTicks(helper, context, () -> {
        context.power().setStamina(SHORT_BAR);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        Vec3 start = context.owner().position();
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    staminaAtErase[0] >= 0.0F,
                    "The fully charged Erase has not swiped yet.");
            helper.assertTrue(
                    ((TheHandEraseAbility.EraseAction) action)
                            .missTeleportOccurredDuringPerform()
                            && context.owner().position()
                                    .distanceToSqr(start)
                                    > TELEPORT_DISTANCE_SQR,
                    "Erase on a short bar did not swipe and teleport.");
            helper.assertTrue(
                    staminaAtErase[0] == 0.0F,
                    "Erase's 150 cost must drain a short bar to 0: "
                            + staminaAtErase[0]);
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease(ERASE_KEY, context.owner());
            cleanup(context);
        });
        });
    }

    // 1.16 TheHandEraseItem paid staminaCostTick after each erase and had
    // no stamina condition, so it starts and keeps erasing on an empty bar.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_item_empty_bar",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseItemErasesOnAnEmptyBar(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_ITEM_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseItemAbility,
                "The production moveset did not resolve the erase-item "
                        + "ability.");
        onceStandTicks(helper, context, () -> {
        context.owner().setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
        context.power().setStamina(0.0F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_ITEM_KEY,
                TheHandEraseItemAbility.EraseItemAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        helper.succeedWhen(() -> {
            int left = context.owner()
                    .getItemInHand(InteractionHand.OFF_HAND).getCount();
            helper.assertTrue(
                    left <= 64 - 2 * TheHandEraseItemAbility
                            .UNLOCKED_ERASE_COUNT,
                    "Erase-item has not erased two ticks' worth yet: "
                            + left + " left");
            helper.assertTrue(
                    LivingComponentAction.getCurEntityAction(
                            context.stand()) == action
                            && action.getPhase() == ActionPhase.PERFORM,
                    "Erase-item stopped on an empty bar.");
            AbilityInput.keyRelease(ERASE_ITEM_KEY, context.owner());
            cleanup(context);
        });
        });
    }

    // 1.16 TheHandEraseItem was a holdType StandEntityAction with no
    // recovery (InitStands :133): StandEntityAction.isCancelable let a punch
    // pressed during the hold replace it at once, PowerBaseImpl
    // stopHeldAction(false) ended the hold, and letting go of the key ended
    // it at once. The builder set no cooldown.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_item_gives_way",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseItemGivesWayToAPunchAtOnceAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability eraseItem = context.power().getAbility(
                TheHandAddon.ERASE_ITEM_NAME);
        Ability punch = context.power().getAbility("punch");
        helper.assertTrue(
                eraseItem instanceof TheHandEraseItemAbility
                        && punch != null,
                "The production moveset did not resolve the erase-item "
                        + "ability and the punch.");
        onceStandTicks(helper, context, () -> {
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        context.owner().setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
        pressHold(
                helper,
                context,
                eraseItem,
                ERASE_ITEM_KEY,
                TheHandEraseItemAbility.EraseItemAction.class);
        EntityActionInstance hold = standActions.getAction();
        helper.assertTrue(
                hold.getPhase() == ActionPhase.PERFORM,
                "Setup: erase-item is not held in its perform: "
                        + hold.getPhase());

        BufferingState pressed = pressClick(context, punch, PUNCH_KEY);
        EntityActionInstance now = standActions.getAction();
        AbilityInput.keyRelease(PUNCH_KEY, context.owner());
        helper.assertTrue(
                pressed.isActionSuccess && !pressed.shouldBuffer
                        && now != null && now != hold
                        && now.ability == punch && hold.isOver(),
                "1.16 let a punch pressed during the erase-item hold "
                        + "replace it at once (isCancelable, no recovery): "
                        + "started " + pressed.isActionSuccess
                        + ", buffered " + pressed.shouldBuffer
                        + ", the Stand's action " + now
                        + ", erase-item over " + hold.isOver()
                        + " in " + hold.getPhase());
        helper.assertTrue(
                context.power().getAbilityCooldown(
                        TheHandAddon.ERASE_ITEM_NAME) == 0,
                "1.16 the_hand_erase_item had no cooldown, yet the "
                        + "replaced hold set "
                        + context.power().getAbilityCooldown(
                                TheHandAddon.ERASE_ITEM_NAME));
        AbilityInput.keyRelease(ERASE_ITEM_KEY, context.owner());
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);

        // letting go ends the hold at once: no recovery to wait through
        resetMoves(context.power());
        pressHold(
                helper,
                context,
                eraseItem,
                ERASE_ITEM_KEY,
                TheHandEraseItemAbility.EraseItemAction.class);
        EntityActionInstance released = standActions.getAction();
        AbilityInput.keyRelease(ERASE_ITEM_KEY, context.owner());
        helper.assertTrue(
                released.isOver(),
                "1.16 ended a let-go erase-item hold at once (no "
                        + "recovery ticks): phase " + released.getPhase());
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 ActionsOverlayGui heldReadyToFire filled the Erase slot green once
    // it was held for getHoldDurationToFire, half its getHoldDurationMax
    // (TheHandErase :290-292), until it was let go or fired by itself.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_ready_to_fire",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseIsReadyToFireFromHalfItsChargeAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        TheHandEraseAbility erase = (TheHandEraseAbility) ability;
        onceStandTicks(helper, context, () -> {
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        float charge = action.phasesLength.getFloat(
                ActionPhase.BUTTON_CHARGE);
        float half = Math.max(charge, 1.0F) / 2.0F;
        helper.assertTrue(
                charge >= 4.0F,
                "Setup: the Erase charge is too short to test: " + charge);
        int ticks = 0;
        while (action.getPhase() == ActionPhase.BUTTON_CHARGE
                && action.getPhaseTick() < half && ticks++ < 100) {
            helper.assertFalse(
                    erase.isHeldReadyToFire(context.owner()),
                    "1.16 showed no green before half the Erase charge: "
                            + "tick " + action.getPhaseTick() + " of "
                            + charge);
            standActions.tick();
        }
        helper.assertTrue(
                action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && erase.isHeldReadyToFire(context.owner()),
                "1.16 filled the Erase slot green once it was held for "
                        + "half its charge (getHoldDurationToFire): phase "
                        + action.getPhase() + ", tick "
                        + action.getPhaseTick() + " of " + charge);
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        helper.assertFalse(
                erase.isHeldReadyToFire(context.owner()),
                "The released Erase still shows the green: phase "
                        + action.getPhase());
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 TheHandErase.getHoldDurationToFire (:290-292) was
    // getHoldDurationMax / 2 in int arithmetic: an odd charge (7 ticks at
    // Resolve 4 with the finisher meter at 0.75) showed green and fired from
    // its third held tick, and stoppedHolding gave the swipe the ticks held
    // over the maximum as its preparation, 3 / 7.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_odd_charge",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseFiresFromTheIntegerHalfOfAnOddChargeAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        TheHandEraseAbility erase = (TheHandEraseAbility) ability;
        onceStandTicks(helper, context, () -> {
        context.stand().setFinisherMeter(0.75F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        float charge = action.phasesLength.getFloat(
                ActionPhase.BUTTON_CHARGE);
        helper.assertTrue(
                charge == 7.0F,
                "Setup: the Erase charge at the meter 0.75 is not 7 ticks: "
                        + charge);
        int ticks = 0;
        while (action.getPhase() == ActionPhase.BUTTON_CHARGE
                && action.getPhaseTick() < 3.0F && ticks++ < 100) {
            helper.assertFalse(
                    erase.isHeldReadyToFire(context.owner()),
                    "1.16 showed no green before the third tick of a "
                            + "7-tick Erase charge: tick "
                            + action.getPhaseTick());
            standActions.tick();
        }
        helper.assertTrue(
                action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.getPhaseTick() == 3.0F
                        && erase.isHeldReadyToFire(context.owner()),
                "1.16 filled the Erase slot green from getHoldDurationToFire"
                        + " = 7 / 2 = 3 ticks: phase " + action.getPhase()
                        + ", tick " + action.getPhaseTick() + ", green "
                        + erase.isHeldReadyToFire(context.owner()));
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        helper.assertTrue(
                action.getPhase() == ActionPhase.WINDUP,
                "1.16 fired an Erase let go after 3 of its 7 charge ticks; "
                        + "the port left it " + action.getPhase());
        FriendlyByteBuf synced = new FriendlyByteBuf(Unpooled.buffer());
        action.toBuf(synced);
        synced.readBoolean();
        float preparation = synced.readFloat();
        synced.release();
        helper.assertTrue(
                Math.abs(preparation - 3.0F / 7.0F) < 1.0E-4F,
                "1.16 stoppedHolding gave the swipe the ticks held over the "
                        + "maximum, 3 / 7, as its preparation; the port "
                        + preparation);
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 TheHandErasureBarrage had no stamina condition: the press is
    // admitted on a bar below one tick's 12 cost.
    @GameTest(
            template = "empty",
            batch = "the_hand_erasure_barrage_short_bar",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasureBarrageStartsOnAShortBar(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASURE_BARRAGE_NAME);
        helper.assertTrue(
                ability instanceof TheHandErasureBarrageAbility,
                "The production moveset did not resolve the erasure "
                        + "barrage ability.");
        onceStandTicks(helper, context, () -> {
        context.power().setStamina(SHORT_BAR);
        pressHold(
                helper,
                context,
                ability,
                BARRAGE_KEY,
                TheHandErasureBarrageAbility
                        .ErasureBarrageAction.class);

        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    ((TheHandErasureBarrageAbility
                                    .ErasureBarrageAction) action)
                            .hasPerformedServerTick(),
                    "Erasure barrage on a short bar did not perform.");
            AbilityInput.keyRelease(BARRAGE_KEY, context.owner());
            cleanup(context);
        });
        });
    }

    // 1.16 TheHandModel drew the erase effect on the right forearm while
    // TheHandEntity.isErasing: an Erase from the tick its hold reached
    // getHoldDurationToFire (TheHandErase.onHoldTick :130-135) to its task's
    // end (onTaskStopped :220), Erase Item for its whole task (TheHandEraseItem
    // :69, :88), the Erasure Barrage until its first recovery tick
    // (TheHandErasureBarrage :84, :168-169); never during a punch.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_effect",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseEffectShowsOnlyWhileTheStandErasesAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability erase = context.power().getAbility(TheHandAddon.ERASE_NAME);
        Ability barrage = context.power().getAbility(
                TheHandAddon.ERASURE_BARRAGE_NAME);
        Ability eraseItem = context.power().getAbility(
                TheHandAddon.ERASE_ITEM_NAME);
        Ability punch = context.power().getAbility("punch");
        helper.assertTrue(
                erase instanceof TheHandEraseAbility
                        && barrage instanceof TheHandErasureBarrageAbility
                        && eraseItem instanceof TheHandEraseItemAbility
                        && punch != null,
                "The production moveset did not resolve the Erase moves "
                        + "and the punch.");
        onceStandTicks(helper, context, () -> {
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        helper.assertFalse(
                TheHandCombat.isErasing(standActions.getAction()),
                "1.16 drew no erase effect on an idle The Hand.");

        pressHold(helper, context, erase, ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action = standActions.getAction();
        int toFire = TheHandEraseAbility.holdTicksToFire(action);
        helper.assertTrue(
                toFire >= 2,
                "Setup: the Erase charge is too short to test: " + toFire);
        int ticks = 0;
        while (action.getPhase() == ActionPhase.BUTTON_CHARGE
                && action.getPhaseTick() < toFire && ticks++ < 100) {
            helper.assertFalse(
                    TheHandCombat.isErasing(action),
                    "1.16 drew the erase effect only once the Erase hold "
                            + "reached getHoldDurationToFire: tick "
                            + action.getPhaseTick() + " of " + toFire);
            standActions.tick();
        }
        helper.assertTrue(
                action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && TheHandCombat.isErasing(action),
                "1.16 set the erase flag on the tick the Erase hold reached "
                        + "getHoldDurationToFire: phase " + action.getPhase()
                        + ", tick " + action.getPhaseTick());
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        ticks = 0;
        while (!action.isOver() && ticks++ < 100) {
            helper.assertTrue(
                    TheHandCombat.isErasing(action),
                    "1.16 kept the erase flag until the Erase task stopped: "
                            + "phase " + action.getPhase() + ", tick "
                            + action.getPhaseTick());
            standActions.tick();
        }
        helper.assertTrue(
                action.isOver() && !TheHandCombat.isErasing(action)
                        && !TheHandCombat.isErasing(
                                standActions.getAction()),
                "1.16 cleared the erase flag when the Erase task stopped: "
                        + "over " + action.isOver() + ", now "
                        + standActions.getAction());
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);

        BufferingState pressed = pressClick(context, punch, PUNCH_KEY);
        EntityActionInstance punchAction = standActions.getAction();
        AbilityInput.keyRelease(PUNCH_KEY, context.owner());
        helper.assertTrue(
                pressed.isActionSuccess && punchAction != null
                        && punchAction.ability == punch
                        && !TheHandCombat.isErasing(punchAction),
                "1.16 drew no erase effect during a punch: started "
                        + pressed.isActionSuccess + ", action "
                        + punchAction);
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);

        resetMoves(context.power());
        pressHold(helper, context, barrage, BARRAGE_KEY,
                TheHandErasureBarrageAbility.ErasureBarrageAction.class);
        EntityActionInstance barrageAction = standActions.getAction();
        helper.assertTrue(
                barrageAction.getPhase() != ActionPhase.RECOVERY
                        && TheHandCombat.isErasing(barrageAction),
                "1.16 set the erase flag as the Erasure Barrage began: phase "
                        + barrageAction.getPhase());
        AbilityInput.keyRelease(BARRAGE_KEY, context.owner());
        helper.assertTrue(
                barrageAction.getPhase() == ActionPhase.RECOVERY
                        && !TheHandCombat.isErasing(barrageAction),
                "1.16 cleared the erase flag at the Erasure Barrage's first "
                        + "recovery tick: phase " + barrageAction.getPhase());
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);

        resetMoves(context.power());
        context.owner().setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
        pressHold(helper, context, eraseItem, ERASE_ITEM_KEY,
                TheHandEraseItemAbility.EraseItemAction.class);
        EntityActionInstance eraseItemAction = standActions.getAction();
        helper.assertTrue(
                TheHandCombat.isErasing(eraseItemAction),
                "1.16 set the erase flag for the whole Erase Item hold: phase "
                        + eraseItemAction.getPhase());
        AbilityInput.keyRelease(ERASE_ITEM_KEY, context.owner());
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 TheHandErase.getHoldDurationMax (:294-300) was 0 below Resolve 4
    // with an empty finisher bar, and an action with no hold maximum was no
    // held action: PowerBaseImpl.onClickAction performed it at once, with the
    // preparation the Stand had kept (TheHandEntity.preparation, 0.5 at first).
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_no_charge",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseBelowResolve4WithAnEmptyBarSwipesAtOnceAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        context.power().setResolveLevel(3);
        context.stand().setFinisherMeter(0.0F);
        helper.assertTrue(
                TheHandEraseAbility.holdDurationMax(
                        context.owner(), context.stand()) == 0,
                "Setup: the Erase maximum at Resolve 3 with an empty bar "
                        + "is not 0.");
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        helper.assertTrue(
                action.getPhase() != ActionPhase.BUTTON_CHARGE
                        && action.phasesLength.getFloat(
                                ActionPhase.BUTTON_CHARGE) == 0.0F,
                "1.16 held no Erase whose maximum was 0 and swiped at once; "
                        + "the port charges: phase " + action.getPhase()
                        + ", charge " + action.phasesLength.getFloat(
                                ActionPhase.BUTTON_CHARGE));
        TheHandEraseAbility.EraseAction erase =
                (TheHandEraseAbility.EraseAction) action;
        int ticks = 0;
        while (erase.eraseTimingSnapshot().eraseCallbackGameTick() < 0L
                && !action.isOver() && ticks++ < 200) {
            helper.assertTrue(
                    action.getPhase() != ActionPhase.BUTTON_CHARGE,
                    "An Erase with no maximum waited in its charge.");
            standActions.tick();
        }
        helper.assertTrue(
                erase.eraseTimingSnapshot().eraseCallbackGameTick() >= 0L,
                "The Erase with no maximum never swiped: phase "
                        + action.getPhase());
        helper.assertTrue(
                preparationOf(action) == 0.5F,
                "1.16 swiped with the Stand's kept preparation, 0.5 at "
                        + "first; the port " + preparationOf(action));
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 TheHandEntity.preparation stayed on the Stand: a swipe with no
    // charge used what the last charge held over its maximum had left there.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_kept_preparation",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseWithNoChargeUsesThePreparationTheStandKept(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        context.stand().setFinisherMeter(0.75F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        EntityActionInstance first = standActions.getAction();
        for (int tick = 0; tick < 4; tick++) {
            standActions.tick();
        }
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        helper.assertTrue(
                first.getPhase() == ActionPhase.WINDUP
                        && Math.abs(preparationOf(first) - 4.0F / 7.0F)
                                < 1.0E-4F,
                "Setup: the charge let go after 4 of 7 ticks did not fire "
                        + "with 4 / 7: phase " + first.getPhase()
                        + ", preparation " + preparationOf(first));
        standActions.setAction(null, context.owner(), SyncType.NO_SYNC);

        context.power().setResolveLevel(3);
        context.stand().setFinisherMeter(0.0F);
        resetMoves(context.power());
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance second = standActions.getAction();
        helper.assertTrue(
                second != first
                        && second.getPhase() != ActionPhase.BUTTON_CHARGE
                        && Math.abs(preparationOf(second) - 4.0F / 7.0F)
                                < 1.0E-4F,
                "1.16 swiped with the preparation the last charge left on "
                        + "the Stand, 4 / 7; the port " + preparationOf(second));
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 PowerBaseImpl.tickHeldAction asked getHoldDurationMax on every
    // held tick: a finisher meter that rose during the charge shortened it,
    // and the hold fired at the new maximum.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_live_maximum",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseChargeFollowsTheFinisherMeterEveryHeldTickAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        TheHandEraseAbility erase = (TheHandEraseAbility) ability;
        onceStandTicks(helper, context, () -> {
        context.stand().setFinisherMeter(0.0F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        helper.assertTrue(
                action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE)
                        == 10.0F,
                "Setup: the Erase charge at an empty bar is not 10 ticks: "
                        + action.phasesLength.getFloat(
                                ActionPhase.BUTTON_CHARGE));
        standActions.tick();
        standActions.tick();
        context.stand().setFinisherMeter(0.75F);
        standActions.tick();
        helper.assertTrue(
                action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.phasesLength.getFloat(
                                ActionPhase.BUTTON_CHARGE) == 7.0F
                        && erase.isHeldReadyToFire(context.owner()),
                "1.16 read the Erase maximum on every held tick: at 3 held "
                        + "ticks of a charge that became 7, half of it "
                        + "was reached; the port: phase " + action.getPhase()
                        + ", charge " + action.phasesLength.getFloat(
                                ActionPhase.BUTTON_CHARGE)
                        + ", green " + erase.isHeldReadyToFire(
                                context.owner()));
        for (int tick = 4; tick <= 6; tick++) {
            standActions.tick();
            helper.assertTrue(
                    action.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "The Erase fired before its live maximum: tick " + tick);
        }
        standActions.tick();
        helper.assertTrue(
                action.getPhase() == ActionPhase.WINDUP
                        && preparationOf(action) == 1.0F,
                "1.16 fired the hold in the 7th held tick, the live "
                        + "maximum, with the ticks held over it, 1; the "
                        + "port: phase " + action.getPhase()
                        + ", preparation " + preparationOf(action));
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 InputHandler.stopHeldAction fired a release at
    // getHoldDurationToFire, and stoppedHolding divided the ticks by
    // getHoldDurationMax, both read at the release.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_live_release",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseReleaseReadsTheLiveMaximumAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        context.stand().setFinisherMeter(0.0F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        for (int tick = 0; tick < 4; tick++) {
            standActions.tick();
        }
        helper.assertTrue(
                action.getPhase() == ActionPhase.BUTTON_CHARGE,
                "Setup: the Erase left its charge early.");
        context.stand().setFinisherMeter(0.75F);
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        helper.assertTrue(
                action.getPhase() == ActionPhase.WINDUP
                        && Math.abs(preparationOf(action) - 4.0F / 7.0F)
                                < 1.0E-4F,
                "1.16 read the maximum, 7, at the release: 4 held ticks "
                        + "reached half of it and fired with 4 / 7; the "
                        + "port: phase " + action.getPhase()
                        + ", preparation " + preparationOf(action));
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 StandEntity.punch -> aimWithThisOrUser turned the Stand toward
    // what a strike aimed at just before the hit (MCUtil.rotateTowards): the
    // Erase swipe is a heavy attack and did the same at its hit tick.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_turn",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseTurnsTheStandAtTheHitTickAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        Cow cow = sideCow(helper, context);
        context.stand().setYRot(0.0F);
        context.stand().setXRot(0.0F);
        context.stand().setYHeadRot(0.0F);
        aimAt(context, cow);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        Vec3 toCow = cow.getBoundingBox().getCenter()
                .subtract(context.stand().getEyePosition());
        float want = (float) Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(-toCow.x, toCow.z)));
        float before = context.stand().getYRot();
        chargeAndSwipe(helper, context, action);
        float after = context.stand().getYRot();
        helper.assertTrue(
                Math.abs(Mth.wrapDegrees(want - after))
                        < Math.abs(Mth.wrapDegrees(want - before)) - 0.5F
                        && Math.abs(Mth.wrapDegrees(
                                want - context.stand().getYHeadRot()))
                                < Math.abs(Mth.wrapDegrees(want - before))
                                        - 0.5F,
                "1.16 turned the Stand toward the struck target at the hit "
                        + "tick; the cow lies at yaw " + want + ", the Stand "
                        + "went " + before + " -> " + after + " (head "
                        + context.stand().getYHeadRot() + ")");
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cow.discard();
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 RHCP's counterattack dropped the aim of the Stand it hit with
    // StandEntity.setTaskTarget(new ActionTarget(null)); the target an Erase
    // kept is the task's, before and after the swipe.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_drop_target",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void rhcpDroppingTheTargetClearsTheTargetTheEraseKeptAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        Cow cow = sideCow(helper, context);
        aimAt(context, cow);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        helper.assertTrue(
                action.getTaskTarget(helper.getLevel()).getMainEntity()
                        == cow,
                "Setup: the Erase pressed at the cow keeps no task target: "
                        + action.getTaskTarget(helper.getLevel()));
        context.stand().setTaskTarget(null);
        helper.assertTrue(
                action.getTaskTarget(helper.getLevel())
                        .isEmpty(helper.getLevel()),
                "1.16 setTaskTarget(null) dropped the target a charging "
                        + "Erase kept; the port keeps "
                        + action.getTaskTarget(helper.getLevel()));
        aimAt(context, cow);
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        LivingComponentAction.getComponent(context.stand())
                .setAction(null, context.owner(), SyncType.NO_SYNC);
        resetMoves(context.power());
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance second =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        chargeAndSwipe(helper, context, second);
        helper.assertTrue(
                second.getTaskTarget(helper.getLevel()).getMainEntity()
                        == cow,
                "Setup: the Erase swipe kept no task target: "
                        + second.getTaskTarget(helper.getLevel()));
        context.stand().setTaskTarget(null);
        helper.assertTrue(
                second.getTaskTarget(helper.getLevel())
                        .isEmpty(helper.getLevel()),
                "1.16 setTaskTarget(null) dropped the target the Erase swipe "
                        + "kept; the port keeps "
                        + second.getTaskTarget(helper.getLevel()));
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cow.discard();
        cleanup(context);
        helper.succeed();
        });
    }

    // 1.16 TheHandErase multiplied the damage (:161, :168) and the armour wear (HeavyPunchInstance.afterAttack :383-407)
    // by TheHandEntity.preparation, which stoppedHolding / the hold's own firing set to the ticks held over the live
    // maximum, never capped: a maximum that shrank mid-charge gave more than 1.
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_prep_over_one",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasePreparationAboveOneIsNotCappedAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareTheHand(helper);
        if (context == null) {
            return;
        }

        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        helper.assertTrue(
                ability instanceof TheHandEraseAbility,
                "The production moveset did not resolve the erase ability.");
        onceStandTicks(helper, context, () -> {
        context.stand().setFinisherMeter(0.0F);
        pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        for (int tick = 0; tick < 9; tick++) {
            standActions.tick();
        }
        context.stand().setFinisherMeter(0.75F);
        standActions.tick();
        float preparation = preparationOf(action);
        helper.assertTrue(
                action.getPhase() == ActionPhase.WINDUP
                        && Math.abs(preparation - 10.0F / 7.0F) < 1.0E-4F,
                "Setup: 10 held ticks of a charge that shrank to 7 did not "
                        + "fire with 10 / 7: phase " + action.getPhase()
                        + ", preparation " + preparation);

        Cow cow = sideCow(helper, context);
        float whole = TheHandCombat.eraseDamage(
                cow, context.stand(), 1.0F);
        float over = TheHandCombat.eraseDamage(
                cow, context.stand(), preparation);
        helper.assertTrue(
                whole > 0.0F
                        && Math.abs(over - whole * preparation)
                                < 1.0E-3F * over
                        && TheHandCombat.eraseDamage(
                                cow, context.stand(), -0.5F) == 0.0F,
                "1.16 multiplied the Erase damage by the preparation as it "
                        + "was, 10 / 7 here, and never below 0: " + whole
                        + " at 1, " + over + " at " + preparation);

        var husk = EntityType.HUSK.create(helper.getLevel());
        Vec3 at = context.owner().position().add(-2.5D, 0.0D, 0.0D);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        husk.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        GameTestChunkTickets.forTest(helper).cover(at);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        husk.setItemSlot(EquipmentSlot.CHEST, chest);
        helper.getLevel().addFreshEntity(husk);
        float fraction = 0.15F * preparation;
        int want = (int) (chest.getMaxDamage() * fraction);
        boolean hit = TheHandCombat.attack(
                context.stand(), husk, 1.0F, preparation, false);
        helper.assertTrue(
                hit && chest.getDamageValue() == want
                        && want > (int) (chest.getMaxDamage() * 0.15F),
                "1.16 wore the chestplate by 0.15 of its durability times "
                        + "the preparation, " + want + " here (a cap at 1 "
                        + "would be " + (int) (chest.getMaxDamage() * 0.15F)
                        + "); hit " + hit + ", worn " + chest.getDamageValue());
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        cow.discard();
        husk.discard();
        cleanup(context);
        helper.succeed();
        });
    }

    private static Cow sideCow(GameTestHelper helper, TestContext context) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 at = context.owner().position().add(2.5D, 0.0D, 0.0D);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPersistenceRequired();
        cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        GameTestChunkTickets.forTest(helper).cover(at);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void aimAt(TestContext context, Cow cow) {
        ActionTarget target = new ActionTarget(cow);
        LivingComponentAction.getComponent(context.owner())
                .entityAim.setTarget(target);
        LivingComponentAction.getComponent(context.stand())
                .entityAim.setTarget(target);
    }

    // Holds the Erase to its release threshold, lets go and ticks it to the
    // tick of its swipe.
    private static void chargeAndSwipe(
            GameTestHelper helper,
            TestContext context,
            EntityActionInstance action) {
        LivingComponentAction standActions =
                LivingComponentAction.getComponent(context.stand());
        int ticks = 0;
        while (action.getPhase() == ActionPhase.BUTTON_CHARGE
                && action.getPhaseTick()
                        < TheHandEraseAbility.holdTicksToFire(action)
                && ticks++ < 100) {
            standActions.tick();
        }
        AbilityInput.keyRelease(ERASE_KEY, context.owner());
        TheHandEraseAbility.EraseAction erase =
                (TheHandEraseAbility.EraseAction) action;
        ticks = 0;
        while (erase.eraseTimingSnapshot().eraseCallbackGameTick() < 0L
                && !action.isOver() && ticks++ < 200) {
            standActions.tick();
        }
        helper.assertTrue(
                erase.eraseTimingSnapshot().eraseCallbackGameTick() >= 0L,
                "The Erase never swiped: phase " + action.getPhase());
    }

    private static float preparationOf(EntityActionInstance action) {
        FriendlyByteBuf synced = new FriendlyByteBuf(Unpooled.buffer());
        action.toBuf(synced);
        synced.readBoolean();
        float preparation = synced.readFloat();
        synced.release();
        return preparation;
    }

    /**
     * Runs the timed part of a test only once The Hand's chunk actually ticks entities. A ticket only asks for the
     * chunk: it becomes entity-ticking after its neighbour ring is generated, and under the seal's load that outlasted
     * the 40 and then the 200 ticks the first versions of this wait allowed (round 66: standChunkEntityTicking=false,
     * origin z = 15 of its chunk). The server ticks as fast as it can, so the wait is budgeted in real time
     * (GameTestChunkTickets) and keeps the Erase timing assertions about Erase.
     */
    static void onceStandTicks(
            GameTestHelper helper, TestContext context, Runnable timedPart) {
        GameTestChunkTickets.forTest(helper).whenTicking(
                () -> cleanup(context),
                () -> {
                    helper.assertTrue(
                            helper.getLevel().isPositionEntityTicking(
                                    context.stand().blockPosition()),
                            "The Hand's chunk never started ticking entities.");
                    timedPart.run();
                });
    }

    static TestContext prepareTheHand(GameTestHelper helper) {
        ServerPlayer owner = makeServerPlayer(helper);
        owner.setNoGravity(true);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.setShiftKeyDown(false);
        owner.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                owner.getEyePosition().add(0.0D, 24.0D, 0.0D));

        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return null;
        }
        power.setStand(TheHandAddon.THE_HAND.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                TheHandAddon.THE_HAND.get().summon(owner, power),
                "The Hand could not be summoned for its input test.");

        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "The Hand was not added to the server level.");
        if (stand == null) {
            return null;
        }
        GameTestChunkTickets.forTest(helper).cover(stand.position());
        stand.summonLockTicks = 0;
        stand.setManuallyControlled(false);
        stand.setNoGravity(true);
        stand.setDeltaMovement(Vec3.ZERO);
        stand.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                stand.getEyePosition().add(0.0D, 24.0D, 0.0D));
        return new TestContext(owner, power, stand);
    }

    static void pressHold(
            GameTestHelper helper,
            TestContext context,
            Ability ability,
            short key,
            Class<? extends EntityActionInstance> expectedAction) {
        AbilityConditionCheck input =
                context.power()
                        .updateAvailableMoves()
                        .getContextVariationContainer(ability);
        helper.assertTrue(
                input != null
                        && input.ability == ability
                        && AbilityInput.withConditionCheck(
                                input,
                                context.owner(),
                                InputMethod.HOLD),
                "The production HOLD preflight rejected "
                        + ability.name() + ".");

        var held = AbilityInput.keyPress(
                key,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        helper.assertTrue(
                held != null
                        && expectedAction.isInstance(action)
                        && action.ability == ability,
                "The production HOLD input did not create "
                        + ability.name() + ".");
    }

    // as ClAbilityInputPacket handles a click: the key's resolved ability,
    // its condition check, then the press
    static BufferingState pressClick(
            TestContext context, Ability ability, short key) {
        BufferingState state = BufferingState.clickCanBuffer();
        resetMoves(context.power());
        AbilityConditionCheck input = context.power()
                .updateAvailableMoves()
                .getContextVariationContainer(ability);
        if (input != null && AbilityInput.withConditionCheck(
                input, context.owner(), InputMethod.CLICK)) {
            AbilityInput.keyPress(
                    key,
                    input.ability,
                    context.owner(),
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.CLICK,
                    0.0F,
                    state,
                    ability.abilityId);
        }
        return state;
    }

    // the power resolves its keys once a tick, and the test does not tick it
    static void resetMoves(Power<?> power) {
        try {
            Field cached = Power.class.getDeclaredField(
                    "cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Power.cachedMovesThisTick is not reachable", e);
        }
    }

    static void cleanup(TestContext context) {
        context.stand().discard();
        context.owner().discard();
    }

    private static ServerPlayer makeServerPlayer(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "the-hand-gametest");
        ServerPlayer player = new ServerPlayer(
                server,
                level,
                profile,
                ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        new SilentServerPacketListener(
                server, connection, player, cookie);
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 12, 1));
        player.moveTo(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D);
        GameTestChunkTickets.forTest(helper).cover(spawn);
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentServerPacketListener
            extends ServerGamePacketListenerImpl {
        private SilentServerPacketListener(
                MinecraftServer server,
                Connection connection,
                ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(
                Packet<?> packet,
                PacketSendListener sendListener) {
        }
    }

    record TestContext(
            ServerPlayer owner,
            StandPower power,
            StandEntity stand) {}
}
