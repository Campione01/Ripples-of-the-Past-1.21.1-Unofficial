package rotp.addons.cream;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import rotp.addons.cream.ability.CreamChompAbility;
import rotp.addons.cream.entity.CreamEntity;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(CreamAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreamMeleeGameTests {
    // Cream's own gameTestServer run ships rotp_cream:empty.
    private static final String TEMPLATE_NAMESPACE = CreamAddon.MOD_ID;

    private CreamMeleeGameTests() {}

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 120)
    public static void finisherPunchDamagesResolvedTarget(
            GameTestHelper helper) {
        runClickRoute(
                helper,
                CreamAddon.CREAM_FINISHER_PUNCH_NAME,
                "heavy_punch",
                true,
                (short) 190);
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 120)
    public static void doubleClawFinisherDamagesResolvedTarget(
            GameTestHelper helper) {
        runClickRoute(
                helper,
                CreamAddon.CREAM_DOUBLE_CLAW_FINISHER_NAME,
                CreamAddon.CREAM_HEAVY_CLAW_NAME,
                true,
                (short) 191);
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 120)
    public static void heavyClawDamagesResolvedTarget(
            GameTestHelper helper) {
        runClickRoute(
                helper,
                CreamAddon.CREAM_HEAVY_CLAW_NAME,
                CreamAddon.CREAM_HEAVY_CLAW_NAME,
                false,
                (short) 192);
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 120)
    public static void heavyPunchDamagesResolvedTarget(
            GameTestHelper helper) {
        runClickRoute(
                helper,
                "heavy_punch",
                "heavy_punch",
                false,
                (short) 193);
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 120)
    public static void chopDamagesResolvedTarget(
            GameTestHelper helper) {
        runClickRoute(
                helper,
                CreamAddon.CREAM_CHOP_NAME,
                CreamAddon.CREAM_CHOP_NAME,
                false,
                (short) 194);
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 140)
    public static void chompDamagesResolvedTarget(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "cream-chomp");
        placeTarget(context, true);
        Ability ability = requireAbility(
                helper, context, CreamAddon.CREAM_CHOMP_NAME);
        EntityActionInstance action = press(
                helper,
                context,
                ability,
                ability,
                InputMethod.HOLD,
                (short) 195);
        RouteProbe probe = new RouteProbe(
                CreamAddon.CREAM_CHOMP_NAME,
                context.target().getUUID(),
                context.target().getHealth());

        helper.succeedWhen(() -> {
            probe.observe(action, context.target());
            if (!probe.released
                    && action.getPhase() != null
                    && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                AbilityInput.keyRelease((short) 195, context.owner());
                probe.released = true;
            }
            Object resolved = privateField(action, "target");
            if (resolved instanceof LivingEntity living) {
                probe.resolvedTargetUuid = living.getUUID();
                probe.resolvedTargetType = living.getType()
                        .builtInRegistryHolder().key().location().toString();
            }
            probe.attackAttempted = Boolean.TRUE.equals(
                    privateField(action, "chomped"));
            finishOrFail(helper, context, action, probe, 125);
        });
    }

    // 1.16 CreamChomp hurt its target directly (hurt(GENERIC, 20)), so the
    // server's Stand damage multiplier never scaled the bite. Its own batch
    // keeps the changed multiplier away from the other melee tests.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 140,
            batch = "cream_chomp_fixed_damage")
    public static void chompIgnoresTheStandDamageMultiplier(
            GameTestHelper helper) {
        ModConfigSpec.ConfigValue<Double> multiplier =
                JojoModConfig.COMMON_SPEC.getValues().get(
                        List.of("Stand settings", "standPowerMultiplier"));
        helper.assertTrue(multiplier != null,
                "fixture: the Stand damage multiplier config value is missing");
        double previous = multiplier.get();
        Runnable close = () -> multiplier.set(previous);
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                close.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                close.run();
            }

            @Override
            public void testAddedForRerun(
                    GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
                close.run();
            }
        });
        multiplier.set(2.0D);
        helper.assertTrue(JojoModConfig.getCommonConfigInstance(false)
                        .standDamageMultiplier.get() == 2.0D,
                "fixture: the live Stand damage multiplier did not change");

        TestContext context = prepareCream(helper, "chomp-fixed");
        placeTarget(context, true);
        Ability ability = requireAbility(
                helper, context, CreamAddon.CREAM_CHOMP_NAME);
        EntityActionInstance action = press(
                helper,
                context,
                ability,
                ability,
                InputMethod.HOLD,
                (short) 198);
        float initialHealth = context.target().getHealth();
        float expected = CreamSpec.CHOMP_DAMAGE
                * (float) context.stand().getStandEfficiency();
        boolean[] released = {false};
        boolean[] finished = {false};
        float[] dealt = {0.0F};
        int[] ticks = {0};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                if (!released[0]
                        && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 198, context.owner());
                    released[0] = true;
                }
                synchronizeAim(context);
                dealt[0] = initialHealth - context.target().getHealth();
                if (dealt[0] <= 0.0F && ++ticks[0] < 125) {
                    helper.assertTrue(false, "Chomp has not landed yet");
                }
                finished[0] = true;
                cleanup(context, "Cream fixed-damage Chomp GameTest done");
            }
            helper.assertTrue(Math.abs(dealt[0] - expected) < 0.01F,
                    "1.16 Chomp dealt its fixed " + expected
                            + " at a Stand damage multiplier of 2, the port dealt "
                            + dealt[0] + " (phase " + action.getPhase()
                            + " after " + ticks[0] + " checks)");
        });
    }

    // 1.16 CreamChomp had no stamina condition: StandEntityTask paid
    // staminaCost(340) after standPerform and ignored the result.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 140)
    public static void chompStartsOnEmptyStaminaBar(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-empty");
        placeTarget(context, true);
        // Creative stamina is infinite.
        context.owner().setGameMode(GameType.SURVIVAL);
        context.power().setStamina(0.0F);
        Ability ability = requireAbility(
                helper, context, CreamAddon.CREAM_CHOMP_NAME);
        EntityActionInstance action = press(
                helper,
                context,
                ability,
                ability,
                InputMethod.HOLD,
                (short) 196);
        float initialHealth = context.target().getHealth();
        float[] staminaWhenArmed = {-1.0F};
        boolean[] released = {false};
        boolean[] finished = {false};
        boolean[] landed = {false};
        int[] ticks = {0};
        StringBuilder trace = new StringBuilder();
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() != context.stand()
                    || staminaWhenArmed[0] >= 0.0F) {
                return;
            }
            trace.append(action.getPhase()).append('=')
                    .append(context.power().getStamina()).append(' ');
            if (Boolean.TRUE.equals(privateField(action, "armed"))) {
                staminaWhenArmed[0] = context.power().getStamina();
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);

        helper.succeedWhen(() -> {
            if (!finished[0]) {
                if (!released[0]
                        && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 196, context.owner());
                    released[0] = true;
                }
                synchronizeAim(context);
                boolean damaged =
                        context.target().getHealth() < initialHealth;
                if (!damaged && ++ticks[0] < 125) {
                    helper.assertTrue(false, "Chomp has not landed yet");
                }
                finished[0] = true;
                landed[0] = damaged;
                NeoForge.EVENT_BUS.unregister(standTick);
                cleanup(context, "Cream empty-bar Chomp GameTest done");
            }
            helper.assertTrue(landed[0],
                    "1.16 Chomp started and bit on an empty stamina bar: trace="
                            + trace);
            helper.assertTrue(staminaWhenArmed[0] == 0.0F,
                    "the 340 cost drains a short bar to 0 at perform start: "
                            + staminaWhenArmed[0] + " infinite="
                            + context.power().isStaminaInfinite()
                            + " trace=" + trace);
        });
    }

    // 1.16 CreamVoidDash had only staminaCostTick(4): StandEntityTask paid it
    // as a ticking cost with the result ignored, so Enter Cream's dash ran
    // its 20 ticks whatever the bar. Its own batch keeps its chunk tickets
    // apart from the empty-bar Chomp test's.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_dash")
    public static void voidDashRunsItsTicksOnAShortBar(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "dash-short");
        context.target().discard();
        // Creative stamina is infinite.
        context.owner().setGameMode(GameType.SURVIVAL);
        CreamEntity cream = (CreamEntity) context.stand();
        cream.beginVoidMode(CreamEntity.VoidMode.SEMI);
        // The 300 start cost leaves 10: the 4 a tick empties it on the third tick.
        context.power().setStamina(
                CreamConfig.VOID_DASH_STAMINA.get().floatValue() + 10.0F);
        Ability dash = requireAbility(
                helper, context, CreamAddon.CREAM_VOID_DASH_NAME);
        Ability ball = context.power().getAbility(
                CreamAddon.CREAM_VOID_BALL_NAME);
        int[] dashTicks = {0};
        float[] lastDashStamina = {-1.0F};
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() == cream
                    && cream.getVoidMode() == CreamEntity.VoidMode.DASH) {
                dashTicks[0] = Math.max(dashTicks[0], cream.getVoidTicks());
                lastDashStamina[0] = context.power().getStamina();
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            cleanup(context, "Cream short-bar dash GameTest done");
        };
        try {
            press(helper, context, dash, ball, InputMethod.CLICK, (short) 197);
            AbilityInput.keyRelease((short) 197, context.owner());
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        helper.runAfterDelay(CreamSpec.VOID_DASH_TICKS + 15, () -> {
            boolean stillInVoid = cream.isInVoidState();
            close.run();
            helper.assertTrue(dashTicks[0] >= CreamSpec.VOID_DASH_TICKS - 1,
                    "1.16 ran the dash for its " + CreamSpec.VOID_DASH_TICKS
                            + " ticks on a short bar, it lasted "
                            + dashTicks[0]);
            helper.assertTrue(lastDashStamina[0] == 0.0F,
                    "the dash's 4 a tick drains the bar to 0 and keeps"
                            + " going, its last tick held "
                            + lastDashStamina[0]);
            helper.assertTrue(!stillInVoid,
                    "the dash did not end after its "
                            + CreamSpec.VOID_DASH_TICKS + " ticks");
            helper.succeed();
        });
    }

    // 1.16 CreamEntity: idle withYOffset(0, 0.5, -0.5) and arms-only
    // withYOffset(0, 0, 0), (left, up, forward) from the user's feet, turned
    // by the view yaw; 0.5 is the default y of task offsets without their
    // own.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60)
    public static void idleCreamStandsWhereTheDonorPutIt(
            GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "cream-idle");
        Vec3 ownerPos = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 12, 1)));
        owner.setPos(ownerPos.x, ownerPos.y, ownerPos.z);
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        StandPower power = StandPower.get(owner);
        StandEntity stand = null;
        try {
            helper.assertTrue(power != null,
                    "Mock player has no StandPower attachment.");
            power.setStand(CreamAddon.CREAM.get());
            helper.assertTrue(CreamAddon.CREAM.get().summon(owner, power),
                    "Cream could not be summoned.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand instanceof CreamEntity
                            && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "Setup: Cream is not an idle full Stand following its user.");
            helper.assertTrue(stand.Y_OFFSET == 0.5D,
                    "1.16 Cream task offsets without a y took 0.5, got "
                            + stand.Y_OFFSET);
            turnIdleUser(owner, 0.0F, 50.0F);
            assertIdleAt(helper, owner, stand, 0.0D, 0.5D, -0.5D,
                    "view yaw 0, body yaw 50");
            turnIdleUser(owner, 90.0F, 0.0F);
            assertIdleAt(helper, owner, stand, 0.5D, 0.5D, 0.0D,
                    "view yaw 90, body yaw 0");
            turnIdleUser(owner, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertIdleAt(helper, owner, stand, 0.0D, 0.0D, 0.0D,
                    "arms-only, view yaw 0");
        }
        finally {
            if (stand != null) {
                stand.discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Cream idle placement GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 InitStands: eat item and eat item stack standOffsetFromUser(0.3,
    // 1.0, -0.7), (left, forward, y); void ball and semi void (0, 0, 0),
    // from the user's feet in full mode, turned by the view yaw, the void
    // ones through their charge too.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60)
    public static void creamActionsPlaceTheStandWhereTheDonorDid(
            GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "cream-actions");
        Vec3 ownerPos = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 12, 1)));
        owner.setPos(ownerPos.x, ownerPos.y, ownerPos.z);
        owner.setXRot(0.0F);
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        owner.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(Items.APPLE, 16));
        StandPower power = StandPower.get(owner);
        StandEntity stand = null;
        try {
            helper.assertTrue(power != null,
                    "Mock player has no StandPower attachment.");
            power.setStand(CreamAddon.CREAM.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(CreamAddon.CREAM.get().summon(owner, power),
                    "Cream could not be summoned.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand instanceof CreamEntity
                            && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "Setup: Cream is not a full Stand following its user.");
            stand.summonLockTicks = 0;
            TestContext context = new TestContext(
                    owner, power, stand, null, null);

            turnIdleUser(owner, 0.0F, 50.0F);
            EntityActionInstance eat = pressFor(helper, context,
                    CreamAddon.CREAM_EAT_ITEM_NAME,
                    CreamAddon.CREAM_EAT_ITEM_NAME,
                    InputMethod.CLICK, (short) 210);
            assertActionAt(helper, owner, stand, eat, 0.3D, -0.7D, 1.0D,
                    "view yaw 0, body yaw 50");
            turnIdleUser(owner, 90.0F, 0.0F);
            assertActionAt(helper, owner, stand, eat, -1.0D, -0.7D, 0.3D,
                    "view yaw 90, body yaw 0");
            clearAction(stand);

            turnIdleUser(owner, 0.0F, 50.0F);
            EntityActionInstance eatStack = pressFor(helper, context,
                    CreamAddon.CREAM_EAT_ITEM_STACK_NAME,
                    CreamAddon.CREAM_EAT_ITEM_NAME,
                    InputMethod.CLICK, (short) 211);
            assertActionAt(helper, owner, stand, eatStack, 0.3D, -0.7D, 1.0D,
                    "view yaw 0, body yaw 50");
            clearAction(stand);

            for (String name : List.of(CreamAddon.CREAM_VOID_BALL_NAME,
                    CreamAddon.CREAM_SEMI_VOID_NAME)) {
                turnIdleUser(owner, 0.0F, 50.0F);
                EntityActionInstance voidAction = pressFor(helper, context,
                        name, name, InputMethod.HOLD,
                        name.equals(CreamAddon.CREAM_VOID_BALL_NAME)
                                ? (short) 212 : (short) 213);
                helper.assertTrue(
                        voidAction.getPhase() == ActionPhase.BUTTON_CHARGE,
                        name + " is not charging: " + voidAction.getPhase());
                assertActionAt(helper, owner, stand, voidAction,
                        0.0D, 0.0D, 0.0D, "charging, view yaw 0, body yaw 50");
                clearAction(stand);
            }
        }
        finally {
            if (stand != null) {
                stand.discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Cream action placement GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 StandEntity.taskOffset: CreamChomp's task kept the entity it was
    // pressed at (checkTarget), so while it charged its offset was turned by
    // the yaw and pitch from the user's eyes to that entity, wherever the user
    // looked.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60)
    public static void chompTurnsItsOffsetToItsTargetAsIn116(
            GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "cream-chomp-turn");
        Vec3 ownerPos = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 12, 1)));
        owner.setPos(ownerPos.x, ownerPos.y, ownerPos.z);
        owner.setNoGravity(true);
        owner.setXRot(0.0F);
        owner.xRotO = 0.0F;
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        StandPower power = StandPower.get(owner);
        StandEntity stand = null;
        Cow cow = EntityType.COW.create(helper.getLevel());
        try {
            helper.assertTrue(power != null && cow != null,
                    "Setup: no StandPower attachment or no cow.");
            power.setStand(CreamAddon.CREAM.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(CreamAddon.CREAM.get().summon(owner, power),
                    "Cream could not be summoned.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand instanceof CreamEntity
                            && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "Setup: Cream is not a full Stand following its user.");
            stand.summonLockTicks = 0;
            cow.moveTo(ownerPos.x + 3.0D, ownerPos.y, ownerPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "Setup: the cow could not enter the level.");

            // pressed looking east at the cow
            ActionTarget atCow = new ActionTarget(cow);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(atCow);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(atCow);
            turnIdleUser(owner, -90.0F, -90.0F);
            TestContext context = new TestContext(owner, power, stand, cow, null);
            EntityActionInstance chomp = pressFor(helper, context,
                    CreamAddon.CREAM_CHOMP_NAME, CreamAddon.CREAM_CHOMP_NAME,
                    InputMethod.HOLD, (short) 214);
            helper.assertTrue(chomp.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: Chomp is not charging: " + chomp.getPhase());
            helper.assertTrue(
                    chomp.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "1.16 CreamChomp.checkTarget kept the cow it was pressed at;"
                            + " the task holds "
                            + chomp.getTaskTarget(helper.getLevel()));

            // then looks south, its body turned 50 degrees
            turnIdleUser(owner, 0.0F, 50.0F);
            stand.updatePosition(owner);
            Vec3 relative = stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(Math.abs(relative.x) < 1.0E-4D
                            && Math.abs(relative.y) < 1.0E-4D
                            && relative.z > 0.5D,
                    "Setup: Chomp's front offset is " + relative);
            // (0, 0, front) turned by the yaw and pitch to the cow's eyes
            Vec3 expected = cow.getEyePosition()
                    .subtract(owner.getEyePosition())
                    .normalize()
                    .scale(relative.z);
            Vec3 actual = stand.position().subtract(owner.position());
            helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                    "1.16 put Cream charging a Chomp pressed at a cow 3 blocks"
                            + " east of its user, who then looked south, at its"
                            + " user's feet + " + expected + " (turned to the"
                            + " cow), got " + actual + " (offset " + relative
                            + " " + stand.offsetFromUser.getRotations() + ").");
            clearAction(stand);
        }
        finally {
            if (stand != null) {
                stand.discard();
            }
            if (cow != null) {
                cow.discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Cream Chomp offset GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 CreamChomp: TargetRequirement.ENTITY, and checkTarget answered
    // "no_target_chomp" for anything but a living entity within 4 blocks of the
    // user; Action.checkRangeAndTarget answered "target_too_far" from the
    // Stand first (8 blocks, 4 without a line of sight). The click was refused.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompIsRefusedWithoutALivingTargetWithinFourBlocks(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-refused");
        try {
            Ability chomp = requireChompAbility(helper, context);
            Vec3 ownerPos = context.owner().position();
            Cow cow = context.target();
            StandEntity stand = context.stand();

            cow.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
            synchronizeAim(context);
            assertChompCheck(helper, chomp, context, null,
                    "a cow 3.5 blocks from its user, aimed at");

            cow.setPos(ownerPos.add(0.0D, 0.0D, 4.6D));
            synchronizeAim(context);
            assertChompCheck(helper, chomp, context, "no_target_chomp",
                    "a cow 4.6 blocks from its user, aimed at");

            context.owner().setXRot(-90.0F);
            assertChompCheck(helper, chomp, context, "",
                    "an aim at the sky");

            cow.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
            synchronizeAim(context);
            stand.moveTo(ownerPos.x, ownerPos.y, ownerPos.z - 10.0D,
                    0.0F, 0.0F);
            assertChompCheck(helper, chomp, context, "target_too_far",
                    "a cow 3.5 blocks from its user and 13.5 from the Stand");
        }
        finally {
            cleanup(context, "Cream refused Chomp GameTest done");
        }
        helper.succeed();
    }

    // 1.16 PowerBaseImpl.checkTarget first ran StandEntityAction
    // .overrideVanillaMouseTarget on the press: a crosshair on a torch gave way
    // to what Cream's collision ray met, a cow beside the crosshair line that
    // the user's own ray misses even with Cream's grown hitboxes, which Chomp
    // took and its task held.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompPressedAtATorchTakesTheCowItsStandsRayMeetsAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-torch");
        ServerLevel level = helper.getLevel();
        BlockPos torch = null;
        short key = 216;
        try {
            Ability chomp = requireChompAbility(helper, context);
            ServerPlayer owner = context.owner();
            StandEntity stand = context.stand();
            Cow cow = context.target();
            owner.setYRot(0.0F);
            owner.setYHeadRot(0.0F);
            owner.setXRot(0.0F);
            stand.moveTo(owner.getX() + 1.5D,
                    owner.getEyeY() - stand.getEyeHeight(),
                    owner.getZ() + 0.35D, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            Vec3 cowPos = new Vec3(owner.getX() + 1.5D,
                    owner.getEyeY() - cow.getBbHeight() / 2.0D,
                    owner.getZ() + 2.0D);
            context.chunks().force(cowPos);
            cow.setPos(cowPos);
            cow.setDeltaMovement(Vec3.ZERO);
            torch = BlockPos.containing(owner.getEyePosition()).south();
            level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                            .setValue(WallTorchBlock.FACING, Direction.NORTH),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ActionTarget atTorch = new ActionTarget(torch, Direction.NORTH);
            LivingComponentAction.getComponent(owner).entityAim
                    .setTarget(atTorch);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(atTorch);

            assertChompCheck(helper, chomp, context, null,
                    "a torch with a cow in Cream's collision ray behind it");
            EntityActionInstance action = press(
                    helper, context, chomp, chomp, InputMethod.HOLD, key);
            helper.assertTrue(
                    action.getTaskTarget(level).getEntity() == cow,
                    "1.16's Chomp pressed at a torch held the cow Cream's"
                            + " collision ray met; the task holds "
                            + action.getTaskTarget(level));
        }
        finally {
            AbilityInput.keyRelease(key, context.owner());
            if (torch != null) {
                level.setBlock(torch, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
            cleanup(context, "Cream Chomp torch GameTest done");
        }
        helper.succeed();
    }

    // 1.16 PowerBaseImpl.tickHeldAction ran checkRequirements on the crosshair
    // target on every held tick, and a hold that was not holdOnly ended once
    // the crosshair left the entity: nothing fired, and no stamina was paid.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompHoldEndsWhenTheCrosshairLeavesItsTarget(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-held");
        placeTarget(context, true);
        context.owner().setGameMode(GameType.SURVIVAL);
        Ability chomp = requireChompAbility(helper, context);
        float stamina = context.power().getStamina();
        EntityActionInstance action = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 215);
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(!action.isOver()
                                && action.getPhase() == ActionPhase.BUTTON_CHARGE
                                && action.getPhaseTick() >= 2.0F,
                        "setup: the Chomp held at its target is " + action.getPhase()
                                + " at tick " + action.getPhaseTick());
                context.owner().setXRot(-90.0F);
            }
            catch (RuntimeException | Error failure) {
                cleanup(context, "Cream held Chomp GameTest failed");
                throw failure;
            }
            helper.runAfterDelay(4, () -> {
                boolean over = action.isOver();
                ActionPhase phase = action.getPhase();
                float paid = stamina - context.power().getStamina();
                float health = context.target().getHealth();
                float maxHealth = context.target().getMaxHealth();
                cleanup(context, "Cream held Chomp GameTest done");
                helper.assertTrue(over && phase != ActionPhase.PERFORM,
                        "1.16 ended a held Chomp once the crosshair left its"
                                + " target; the action is " + phase + ", over="
                                + over);
                helper.assertTrue(paid <= 0.0F && health == maxHealth,
                        "1.16 paid no stamina and bit nothing for a Chomp ended"
                                + " by the held check; paid " + paid
                                + ", health " + health + "/" + maxHealth);
                helper.succeed();
            });
        });
    }

    // 1.16 StandEntityTask.makeServerSideTask gave a Chomp pressed at an entity
    // that checkTarget refused no target, so the Stand did not turn to it.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompDoesNotTurnTheStandToARefusedTarget(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-turn-refused");
        try {
            Vec3 ownerPos = context.owner().position();
            Cow cow = context.target();
            StandEntity stand = context.stand();
            Ability ability = requireChompAbility(helper, context);

            cow.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
            EntityActionInstance near = installDirect(helper, context, ability);
            helper.assertTrue(near.getTaskTarget(helper.getLevel()).getEntity() == cow
                            && near.standRotationTarget != null,
                    "setup: the Chomp pressed at a cow 3.5 blocks away holds "
                            + near.getTaskTarget(helper.getLevel()) + " and turns to "
                            + near.standRotationTarget);
            clearAction(stand);

            cow.setPos(ownerPos.add(0.0D, 0.0D, 4.6D));
            EntityActionInstance far = installDirect(helper, context, ability);
            helper.assertTrue(far.getTaskTarget(helper.getLevel()).getType()
                            == ActionTarget.TargetType.EMPTY
                            && far.standRotationTarget == null,
                    "1.16 gave a Chomp pressed at a cow 4.6 blocks from its user"
                            + " no task target, so the Stand did not turn to it;"
                            + " the task holds " + far.getTaskTarget(helper.getLevel())
                            + " and the Stand turns to " + far.standRotationTarget);
            clearAction(stand);
        }
        finally {
            cleanup(context, "Cream refused Chomp turn GameTest done");
        }
        helper.succeed();
    }

    // 1.16 InitStands: cream_chomp, cream_void_ball and cream_semi_void_state
    // had partsRequired(ARMS); StandAction.checkConditions refused the press
    // and PowerBaseImpl.tickHeldAction stopped the charge once the arms were
    // gone (no_stand_part.arms).
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompAndVoidChargesNeedTheStandsArmsAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "arms-held");
        placeTarget(context, true);
        StandInstance instance = context.power().getStandInstance().orElseThrow();
        Ability chomp = requireChompAbility(helper, context);
        Ability ball = requireAbility(
                helper, context, CreamAddon.CREAM_VOID_BALL_NAME);
        EntityActionInstance chompAction = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 216);
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(!chompAction.isOver()
                                && chompAction.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "setup: the Chomp held at its target is " + chompAction.getPhase());
                instance.removePart(StandPart.ARMS);
                assertHeldCheck(helper, chomp, chompAction, context,
                        "jojo.message.action_condition.no_stand_part.arms",
                        "a held Chomp whose Stand lost its arms");
            }
            catch (RuntimeException | Error failure) {
                AbilityInput.keyRelease((short) 216, context.owner());
                cleanup(context, "Cream arms GameTest failed");
                throw failure;
            }
            helper.runAfterDelay(3, () -> {
                EntityActionInstance ballAction = null;
                try {
                    helper.assertTrue(chompAction.isOver()
                                    && context.target().getHealth()
                                            == context.target().getMaxHealth(),
                            "1.16 stopped a held Chomp unfired once its Stand"
                                    + " lost its arms; the action is "
                                    + chompAction.getPhase() + ", over="
                                    + chompAction.isOver());
                    AbilityInput.keyRelease((short) 216, context.owner());
                    for (Ability pressed : List.of(chomp, ball)) {
                        assertCheck(helper, pressed.checkConditions(context.power()),
                                "jojo.message.action_condition.no_stand_part.arms",
                                "the press of " + pressed.getAbilityId().nameInMoveset()
                                        + " by a Stand without arms");
                    }
                    instance.addPart(StandPart.ARMS);
                    ballAction = press(helper, context, ball, ball,
                            InputMethod.HOLD, (short) 217);
                    helper.assertTrue(ballAction.getPhase() == ActionPhase.BUTTON_CHARGE,
                            "setup: Enter the Void is not charging: " + ballAction.getPhase());
                    instance.removePart(StandPart.ARMS);
                    assertHeldCheck(helper, ball, ballAction, context,
                            "jojo.message.action_condition.no_stand_part.arms",
                            "a charging Enter the Void whose Stand lost its arms");
                }
                catch (RuntimeException | Error failure) {
                    AbilityInput.keyRelease((short) 217, context.owner());
                    cleanup(context, "Cream arms GameTest failed");
                    throw failure;
                }
                EntityActionInstance charging = ballAction;
                helper.runAfterDelay(3, () -> {
                    boolean over = charging.isOver();
                    boolean inVoid = ((CreamEntity) context.stand()).isInVoidState();
                    AbilityInput.keyRelease((short) 217, context.owner());
                    cleanup(context, "Cream arms GameTest done");
                    helper.assertTrue(over && !inVoid,
                            "1.16 stopped the Enter the Void charge once its Stand"
                                    + " lost its arms; over=" + over + ", void="
                                    + inVoid);
                    helper.succeed();
                });
            });
        });
    }

    // 1.16 InitStands :68-72, :76-79, :81 gave the finishers, the heavy claw,
    // the heavy punch, the void cancels, the void dash and both eats
    // partsRequired(ARMS): a Stand without arms was refused their press.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void creamMovesNeedTheStandsArmsAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "arms-moves");
        StandInstance instance = context.power().getStandInstance().orElseThrow();
        try {
            context.owner().setItemInHand(InteractionHand.OFF_HAND,
                    new ItemStack(Items.APPLE, 3));
            for (String eat : List.of(CreamAddon.CREAM_EAT_ITEM_NAME,
                    CreamAddon.CREAM_EAT_ITEM_STACK_NAME)) {
                Ability ability = context.power().getAbility(eat);
                helper.assertTrue(ability != null, "Cream has no " + eat);
                assertCheck(helper, ability.checkConditions(context.power()),
                        null, "the press of " + eat
                                + " with arms and an apple in the off hand");
            }
            instance.removePart(StandPart.ARMS);
            for (String name : List.of(
                    CreamAddon.CREAM_FINISHER_PUNCH_NAME,
                    CreamAddon.CREAM_DOUBLE_CLAW_FINISHER_NAME,
                    CreamAddon.CREAM_HEAVY_CLAW_NAME,
                    "heavy_punch",
                    CreamAddon.CREAM_VOID_BALL_CANCEL_NAME,
                    CreamAddon.CREAM_VOID_DASH_NAME,
                    CreamAddon.CREAM_EAT_ITEM_NAME,
                    CreamAddon.CREAM_EAT_ITEM_STACK_NAME,
                    CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME)) {
                Ability ability = context.power().getAbility(name);
                helper.assertTrue(ability != null, "Cream has no " + name);
                assertCheck(helper, ability.checkConditions(context.power()),
                        "jojo.message.action_condition.no_stand_part.arms",
                        "the press of " + name + " by a Stand without arms");
            }
            Ability eat = context.power().getAbility(
                    CreamAddon.CREAM_EAT_ITEM_NAME);
            helper.assertTrue(!AbilityInput.withConditionCheck(
                            eat, context.owner())
                            && context.owner().getOffhandItem().getCount() == 3,
                    "1.16 refused Eat Item by a Stand without arms; the"
                            + " press went through or ate the apple");
        }
        finally {
            instance.addPart(StandPart.ARMS);
            context.owner().setItemInHand(InteractionHand.OFF_HAND,
                    ItemStack.EMPTY);
            cleanup(context, "Cream move arms GameTest done");
        }
        helper.succeed();
    }

    // 1.16 CreamVoidBall and CreamSemiVoidState.checkStandConditions: a
    // manually controlled Stand refused them ("message.cream.nu_uh"), and
    // the Ball also during the semi-void ("message.cream.nu_uh2"); asked on
    // the press and on every held tick of the charge.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void voidChargesFollowTheDonorStandConditions(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "void-conditions");
        context.target().discard();
        CreamEntity cream = (CreamEntity) context.stand();
        Ability ball = requireAbility(
                helper, context, CreamAddon.CREAM_VOID_BALL_NAME);
        Ability semi = requireAbility(
                helper, context, CreamAddon.CREAM_SEMI_VOID_NAME);
        EntityActionInstance action;
        try {
            cream.setManuallyControlled(true);
            for (Ability pressed : List.of(ball, semi)) {
                assertCheck(helper, pressed.checkConditions(context.power()),
                        "message.cream.nu_uh",
                        "the press of " + pressed.getAbilityId().nameInMoveset()
                                + " with Cream manually controlled");
            }
            cream.setManuallyControlled(false);
            cream.beginVoidMode(CreamEntity.VoidMode.SEMI);
            assertCheck(helper, ball.checkSpecificConditions(context.power()),
                    "message.cream.nu_uh2",
                    "Enter the Void itself pressed in the semi-void");
            cream.endVoidMode(false);
            action = press(helper, context, semi, semi,
                    InputMethod.HOLD, (short) 218);
            helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "setup: Enter Cream is not charging: " + action.getPhase());
            cream.setManuallyControlled(true);
            assertHeldCheck(helper, semi, action, context,
                    "message.cream.nu_uh",
                    "a charging Enter Cream whose Stand went under manual control");
        }
        catch (RuntimeException | Error failure) {
            cream.setManuallyControlled(false);
            AbilityInput.keyRelease((short) 218, context.owner());
            cleanup(context, "Cream void condition GameTest failed");
            throw failure;
        }
        EntityActionInstance charging = action;
        helper.runAfterDelay(3, () -> {
            boolean over = charging.isOver();
            boolean inVoid = cream.isInVoidState();
            cream.setManuallyControlled(false);
            AbilityInput.keyRelease((short) 218, context.owner());
            cleanup(context, "Cream void condition GameTest done");
            helper.assertTrue(over && !inVoid,
                    "1.16 stopped the Enter Cream charge once its Stand went"
                            + " under manual control; over=" + over + ", void="
                            + inVoid);
            helper.succeed();
        });
    }

    // 1.16 CreamChomp kept any target its Stand may attack
    // (StandEntityAction.checkStandTarget, StandEntity.canAttack): an enemy
    // Stand is bitten, through the core's Stand damage rules, and a Stand
    // whose user is a teammate without friendly fire is refused silently.
    // The ability name is the donor's own rule (chompNameOffersAnyLivingEntityWithinFourBlocksAsIn116).
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160)
    public static void chompBitesAnEnemyStandAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-stand");
        context.target().discard();
        context.owner().setGameMode(GameType.SURVIVAL);
        ServerPlayer enemy = makeCompatibleMockPlayer(helper, "cream-enemy");
        enemy.setGameMode(GameType.SURVIVAL);
        var enemyHealth = enemy.getAttribute(Attributes.MAX_HEALTH);
        enemyHealth.setBaseValue(2048.0D);
        enemy.setHealth(2048.0F);
        // a mock player is never ticked, so its spawn invulnerability would last
        setPrivateField(enemy, ServerPlayer.class, "spawnInvulnerableTime", 0);
        Vec3 ownerPos = context.owner().position();
        enemy.setPos(ownerPos.add(2.5D, 0.0D, 3.5D));
        StandPower enemyPower = StandPower.get(enemy);
        enemyPower.setStand(CreamAddon.CREAM.get());
        helper.assertTrue(CreamAddon.CREAM.get().summon(enemy, enemyPower),
                "setup: the enemy's Cream could not be summoned");
        StandEntity enemyStand = enemyPower.getSummonedStandEntity();
        enemyStand.summonLockTicks = 0;
        enemyStand.setCanFollowUser(false);
        enemyStand.setNoGravity(true);
        enemyStand.moveTo(ownerPos.x, ownerPos.y, ownerPos.z + 3.5D, 180.0F, 0.0F);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        String teamName = "cream-allies-" + helper.getTick();
        float[] standHurt = {0.0F};
        Consumer<LivingDamageEvent.Post> standDamage = event -> {
            if (event.getEntity() == enemyStand) {
                standHurt[0] += event.getNewDamage();
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, LivingDamageEvent.Post.class, standDamage);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standDamage);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
            enemyStand.discard();
            enemy.connection.disconnect(Component.literal("Cream enemy done"));
            cleanup(context, "Cream enemy Stand Chomp GameTest done");
        };
        Ability chomp = requireChompAbility(helper, context);
        // the client sends the aim it picks, as synchronizeAim does for the cow
        Runnable aim = () -> {
            ActionTarget atStand = new ActionTarget(enemyStand);
            LivingComponentAction.getComponent(context.owner())
                    .entityAim.setTarget(atStand);
            LivingComponentAction.getComponent(context.stand())
                    .entityAim.setTarget(atStand);
            context.owner().lookAt(
                    net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    enemyStand.getBoundingBox().getCenter());
        };
        EntityActionInstance action;
        try {
            aim.run();
            assertCheck(helper, chomp.checkSpecificConditions(context.power()),
                    null, "the press of Chomp at an enemy Stand 3.5 blocks ahead");
            helper.assertTrue(canBeChomped(context.owner(), null, enemyStand),
                    "1.16 let Chomp bite an enemy Stand with its own Stand away");
            helper.assertTrue(hudChompTarget(context.owner()) == enemyStand,
                    "the Chomp name must offer the enemy Stand 3.5 blocks ahead, it offers "
                            + hudChompTarget(context.owner()));

            PlayerTeam team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
            scoreboard.addPlayerToTeam(context.owner().getScoreboardName(), team);
            scoreboard.addPlayerToTeam(enemy.getScoreboardName(), team);
            assertCheck(helper, chomp.checkSpecificConditions(context.power()),
                    "", "the press of Chomp at the Stand of a teammate without friendly fire");
            helper.assertTrue(!canBeChomped(context.owner(), null, enemyStand),
                    "1.16 refused the Stand of a teammate without friendly fire with its own Stand away");
            helper.assertTrue(hudChompTarget(context.owner()) == enemyStand,
                    "1.16 named a teammate's Stand 3.5 blocks ahead on the Chomp name, though the"
                            + " press is refused: " + hudChompTarget(context.owner()));
            scoreboard.removePlayerTeam(team);

            action = press(helper, context, chomp, chomp,
                    InputMethod.HOLD, (short) 219);
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        float initial = enemy.getHealth();
        boolean[] released = {false};
        boolean[] finished = {false};
        float[] dealt = {0.0F};
        int[] ticks = {0};
        StringBuilder trace = new StringBuilder();
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                aim.run();
                String step = action.getPhase() + (Boolean.TRUE.equals(
                        privateField(action, "armed")) ? "+armed" : "");
                if (trace.length() == 0 || !trace.toString().endsWith(step + " ")) {
                    trace.append(step).append(' ');
                }
                if (!released[0]
                        && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 219, context.owner());
                    released[0] = true;
                }
                dealt[0] = initial - enemy.getHealth();
                if (dealt[0] <= 0.0F && ++ticks[0] < 140) {
                    helper.assertTrue(false, "Chomp has not landed yet");
                }
                finished[0] = true;
                AbilityInput.keyRelease((short) 219, context.owner());
                close.run();
            }
            helper.assertTrue(dealt[0] > 0.0F && standHurt[0] > 0.0F,
                    "1.16 Chomp bit an enemy Stand it may attack; the Stand took "
                            + standHurt[0] + " and its user lost " + dealt[0]
                            + " health (phases " + trace + "after "
                            + ticks[0] + " checks)");
        });
    }

    // 1.16 StandEntityAction.isCancelable: cream_chomp (holdToFire(40), no recovery ticks) gave way to any other
    // Stand action once it fired, its own press excepted (canClickDuringTask): in the windup the fire tick started,
    // and in the perform, its first tick too, which came a tick after the windup's last.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void firedChompGivesWayToAnotherStandActionAsIn116(GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-fired");
        try {
            placeTarget(context, true);
            Ability chomp = requireChompAbility(helper, context);
            Ability punch = context.power().getAbility("punch");
            helper.assertTrue(punch != null, "Cream has no punch.");
            Object[][] rows = {
                    { ActionPhase.WINDUP, 0, punch, true },
                    { ActionPhase.WINDUP, 19, punch, true },
                    { ActionPhase.PERFORM, 0, punch, true },
                    { ActionPhase.PERFORM, 12, punch, true },
                    { ActionPhase.WINDUP, 5, chomp, false },
                    { ActionPhase.PERFORM, 5, chomp, false } };
            short key = 230;
            for (Object[] row : rows) {
                ActionPhase phase = (ActionPhase) row[0];
                int tick = (Integer) row[1];
                Ability pressed = (Ability) row[2];
                boolean replaces = (Boolean) row[3];
                EntityActionInstance fired = installDirect(helper, context, chomp);
                fired.setPhase(phase, tick);
                context.power().setAbilityCooldown(pressed.name(), 0, 0);
                context.power().setStamina(context.power().getMaxStamina());
                BufferingState state = BufferingState.clickCanBuffer();
                FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AbilityInput.keyPress(key, pressed, context.owner(), input, InputMethod.CLICK, 0.0F, state,
                            pressed.abilityId);
                }
                finally {
                    input.release();
                    AbilityInput.keyRelease(key, context.owner());
                }
                key++;
                EntityActionInstance now = context.stand().getCurStandAction();
                boolean replaced = state.isActionSuccess && !state.shouldBuffer && fired.isOver() && now != null
                        && now != fired && now.ability == pressed;
                boolean waits = !state.isActionSuccess && now == fired && !fired.isOver();
                helper.assertTrue(replaces ? replaced : waits, "a fired Chomp in " + phase + " tick " + tick
                        + " pressed with " + pressed.getAbilityId().nameInMoveset() + ": 1.16 "
                        + (replaces ? "let it replace the Chomp at once" : "kept the Chomp")
                        + " (started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
                        + ", the Stand's action " + now + ")");
                context.owner().getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
                clearAction(context.stand());
            }
        }
        finally {
            cleanup(context, "Cream fired Chomp GameTest done");
        }
        helper.succeed();
    }

    // 1.16 InitStands :73: cream_chomp held 40 ticks, then standWindupDuration(20) (no stun yet, CreamChomp
    // .standPerform stunned at the perform's start), standPerformDuration(20) with the bite on its last tick, and no
    // standRecoveryTicks: the task ended with the bite, which set the cooldown (onTaskStopped).
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160)
    public static void chompWindsUpAfterItsChargeAndEndsWithTheBiteAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-phases");
        placeTarget(context, true);
        // the core's stun skips a mob without AI; this cow keeps its AI but cannot walk
        context.target().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        context.target().setNoAi(false);
        context.owner().setGameMode(GameType.SURVIVAL);
        Ability chomp = requireChompAbility(helper, context);
        context.power().setAbilityCooldown(chomp.name(), 0, 0);
        EntityActionInstance action = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 231);
        float initialHealth = context.target().getHealth();
        int[] tick = {0};
        int[] firstWindup = {-1};
        int[] firstPerform = {-1};
        int[] over = {-1};
        int[] bitten = {-1};
        boolean[] stunnedInWindup = {false};
        boolean[] stunnedInPerform = {false};
        boolean[] recovery = {false};
        boolean[] released = {false};
        boolean[] cooldownAtEnd = {false};
        StringBuilder trace = new StringBuilder();
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() != context.stand() || over[0] >= 0) {
                return;
            }
            tick[0]++;
            ActionPhase phase = action.getPhase();
            boolean stunned = ModStatusEffects.isStunned(context.target());
            String step = String.valueOf(phase);
            if (!trace.toString().contains(step + "@")) {
                trace.append(step).append('@').append(tick[0]).append(' ');
            }
            if (bitten[0] < 0 && context.target().getHealth() < initialHealth) {
                bitten[0] = tick[0];
            }
            if (phase == ActionPhase.WINDUP) {
                if (firstWindup[0] < 0) {
                    firstWindup[0] = tick[0];
                }
                stunnedInWindup[0] |= stunned;
            }
            else if (phase == ActionPhase.PERFORM) {
                if (firstPerform[0] < 0) {
                    firstPerform[0] = tick[0];
                }
                if (action.getPhaseTick() >= 2.0F) {
                    stunnedInPerform[0] |= stunned;
                }
            }
            else if (phase == ActionPhase.RECOVERY) {
                recovery[0] = true;
            }
            if (action.isOver() || context.stand().getCurStandAction() != action) {
                over[0] = tick[0];
                cooldownAtEnd[0] = context.power().isAbilityOnCooldown(chomp.name());
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease((short) 231, context.owner());
            cleanup(context, "Cream Chomp phases GameTest done");
        };
        boolean[] finished = {false};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                synchronizeAim(context);
                if (!released[0] && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 231, context.owner());
                    released[0] = true;
                }
                if (over[0] < 0 && tick[0] < 140) {
                    helper.assertTrue(false, "Chomp has not ended yet");
                }
                finished[0] = true;
                close.run();
            }
            String seen = " (phases " + trace + ")";
            // the Stand's task ran the 40 charge ticks and began the windup in its next tick (windup in ticks 41-60,
            // perform 61-80), so each phase is first seen after the tick before its own first tick
            helper.assertTrue(firstWindup[0] == CreamSpec.CHOMP_CHARGE_TICKS
                            && firstPerform[0] - firstWindup[0] == CreamSpec.CHOMP_WINDUP_TICKS,
                    "1.16 ran a 20-tick windup after the 40-tick charge: windup from " + firstWindup[0]
                            + ", perform from " + firstPerform[0] + seen);
            helper.assertTrue(!stunnedInWindup[0] && stunnedInPerform[0],
                    "1.16 stunned the target as the perform started, not in the windup: stunned in the windup "
                            + stunnedInWindup[0] + ", in the perform " + stunnedInPerform[0] + seen);
            helper.assertTrue(!recovery[0] && over[0] - firstPerform[0] == CreamSpec.CHOMP_PERFORM_TICKS
                            && bitten[0] == over[0],
                    "1.16 ended the Chomp with the bite on its 20th perform tick, with no recovery: perform from "
                            + firstPerform[0] + ", bite at " + bitten[0] + ", over at " + over[0]
                            + ", recovery seen " + recovery[0] + seen);
            helper.assertTrue(cooldownAtEnd[0],
                    "1.16 set the Chomp cooldown as the bitten task stopped" + seen);
        });
    }

    // 1.16 CreamChomp.standTickPerform :111-116: on the 20th perform tick the bite hurt its target and, whatever the
    // hurt returned, played cream_chomp once as a world sound at the Stand (world.playSound with the Stand's sound
    // source). A listener refuses the bite's damage here; its own batch keeps that away from the other tests.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160,
            batch = "cream_chomp_bite_sound")
    public static void chompBiteSoundsWhateverItsHitDidAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-bite-sound");
        placeTarget(context, true);
        context.owner().setGameMode(GameType.SURVIVAL);
        Ability chomp = requireChompAbility(helper, context);
        context.power().setAbilityCooldown(chomp.name(), 0, 0);
        List<String> bites = new ArrayList<>();
        int[] refused = {0};
        Consumer<LivingIncomingDamageEvent> shield = event -> {
            if (event.getEntity() == context.target()) {
                refused[0]++;
                event.setCanceled(true);
            }
        };
        Consumer<PlayLevelSoundEvent.AtPosition> sounds = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == CreamAddon.CREAM_CHOMP_SOUND.get()) {
                bites.add(event.getSource() + " " + (event.getSource() == context.stand().getSoundSource()) + " "
                        + String.format(java.util.Locale.ROOT, "%.3f",
                                event.getPosition().distanceTo(context.stand().position())));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, shield);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, sounds);
        EntityActionInstance action = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 233);
        float initialHealth = context.target().getHealth();
        boolean[] released = {false};
        boolean[] finished = {false};
        int[] checks = {0};
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(shield);
            NeoForge.EVENT_BUS.unregister(sounds);
            AbilityInput.keyRelease((short) 233, context.owner());
            cleanup(context, "Cream Chomp bite sound GameTest done");
        };
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                synchronizeAim(context);
                if (!released[0] && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 233, context.owner());
                    released[0] = true;
                }
                if (!Boolean.TRUE.equals(privateField(action, "chomped")) && ++checks[0] < 140) {
                    helper.assertTrue(false, "Chomp has not bitten yet");
                }
                finished[0] = true;
                close.run();
            }
            helper.assertTrue(refused[0] > 0 && context.target().getHealth() == initialHealth,
                    "fixture: the bite was not refused (refused " + refused[0] + ", health "
                            + initialHealth + " -> " + context.target().getHealth() + ")");
            helper.assertTrue(bites.size() == 1 && bites.get(0).endsWith(" true 0.000"),
                    "1.16 played cream_chomp once, a world sound at the Stand with its sound source, whatever the "
                            + "hurt returned; the port played " + bites);
        });
    }

    // 1.16 InitStands :72, :78, :79 gave cream_chop, cream_eat_item and cream_eat_item_stack no builder cooldown;
    // CreamChop.getStandWindupTicks and CreamEatItem(Stack).onTaskStopped set CHOP_COOLDOWN, EAT_ITEM_COOLDOWN and
    // EAT_ITEM_STACK_COOLDOWN with setCooldownTimer (no Resolve multiplier) only for a user not in Creative.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_timers")
    public static void chopAndEatTimersSkipCreativeAndIgnoreResolveAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "timers");
        placeTarget(context, false);
        Ability chop = requireAbility(helper, context, CreamAddon.CREAM_CHOP_NAME);
        Ability eat = context.power().getAbility(CreamAddon.CREAM_EAT_ITEM_NAME);
        Ability eatStack = context.power().getAbility(CreamAddon.CREAM_EAT_ITEM_STACK_NAME);
        helper.assertTrue(eat != null && eatStack != null, "Cream has no eat abilities.");
        List<String> wrong = new ArrayList<>();
        short[] key = {234};
        GameTestSequence sequence = helper.startSequence();
        for (String mode : List.of("survival", "resolve", "creative")) {
            boolean creative = mode.equals("creative");
            sequence.thenExecute(() -> {
                context.owner().removeEffect(ModStatusEffects.RESOLVE);
                context.owner().setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
                if (mode.equals("resolve")) {
                    context.owner().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
                }
                context.power().setStamina(context.power().getMaxStamina());
                context.power().setAbilityCooldown(chop.name(), 0, 0);
                try {
                    synchronizeAim(context);
                    press(helper, context, chop, chop, InputMethod.CLICK, key[0]);
                    int set = context.power().getAbilityCooldown(chop.name());
                    int expected = creative ? 0 : CreamConfig.CHOP_COOLDOWN.get();
                    if (set != expected || chop.getCooldownTechnical(context.power()) != 0) {
                        wrong.add(mode + " cream_chop: cooldown " + set + " technical "
                                + chop.getCooldownTechnical(context.power()) + ", 1.16 " + expected + " technical 0");
                    }
                }
                catch (RuntimeException | Error failure) {
                    wrong.add(mode + " cream_chop: " + failure.getMessage());
                }
                finally {
                    AbilityInput.keyRelease(key[0]++, context.owner());
                    clearAction(context.stand());
                }
            });
            for (Ability eating : List.of(eat, eatStack)) {
                boolean stack = eating == eatStack;
                sequence.thenExecute(() -> {
                    context.power().setAbilityCooldown(eating.name(), 0, 0);
                    context.owner().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE, 3));
                    try {
                        press(helper, context, eating, eating, InputMethod.CLICK, key[0]);
                    }
                    catch (RuntimeException | Error failure) {
                        wrong.add(mode + " " + eating.name() + ": " + failure.getMessage());
                    }
                    finally {
                        AbilityInput.keyRelease(key[0]++, context.owner());
                    }
                }).thenIdle(3).thenExecute(() -> {
                    // the user's tick has counted the cooldown down since; the total is what was set
                    int set = context.power().getAbilityCooldownTotal(eating.name());
                    int expected = creative ? 0 : stack
                            ? CreamConfig.EAT_ITEM_STACK_COOLDOWN.get()
                            : CreamConfig.EAT_ITEM_COOLDOWN.get();
                    int left = context.owner().getOffhandItem().getCount();
                    if (set != expected || eating.getCooldownTechnical(context.power()) != 0
                            || left != (stack ? 0 : 2)) {
                        wrong.add(mode + " " + eating.name() + ": cooldown " + set + " technical "
                                + eating.getCooldownTechnical(context.power()) + " apples left " + left
                                + ", 1.16 " + expected + " technical 0 apples left " + (stack ? 0 : 2));
                    }
                    clearAction(context.stand());
                });
            }
        }
        boolean[] closed = {false};
        sequence.thenExecute(() -> {
            if (!closed[0]) {
                closed[0] = true;
                context.owner().removeEffect(ModStatusEffects.RESOLVE);
                context.owner().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                cleanup(context, "Cream timers GameTest done");
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 set Cream's Chop and Eat timers only outside Creative and"
                    + " without the Resolve multiplier: " + String.join("; ", wrong));
        }).thenSucceed();
    }

    // 1.16 CreamChomp.onTaskStopped :123-127: after a bite, setCooldownTimer(CHOMP_COOLDOWN) only for a user not in
    // Creative, with no Resolve multiplier. A Creative Chomp first, then a Survival one in Resolve.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 320,
            batch = "cream_chomp_timer")
    public static void chompTimerSkipsCreativeAndIgnoresResolveAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-timer");
        placeTarget(context, true);
        context.owner().setGameMode(GameType.CREATIVE);
        Ability chomp = requireChompAbility(helper, context);
        context.power().setAbilityCooldown(chomp.name(), 0, 0);
        EntityActionInstance[] action = {
                press(helper, context, chomp, chomp, InputMethod.HOLD, (short) 241) };
        short[] key = {241};
        int[] stage = {0};
        float[] healthBefore = {context.target().getHealth()};
        float[] dealt = {0.0F, 0.0F};
        int[] cooldown = {-1, -1};
        boolean[] over = {false};
        Consumer<EntityTickEvent.Post> standTick = event -> {
            EntityActionInstance running = action[0];
            if (event.getEntity() != context.stand() || over[0]) {
                return;
            }
            if (running.isOver() || context.stand().getCurStandAction() != running) {
                over[0] = true;
                dealt[stage[0]] = healthBefore[0] - context.target().getHealth();
                cooldown[stage[0]] = context.power().getAbilityCooldown(chomp.name());
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease(key[0], context.owner());
            context.owner().removeEffect(ModStatusEffects.RESOLVE);
            cleanup(context, "Cream Chomp timer GameTest done");
        };
        boolean[] released = {false};
        boolean[] finished = {false};
        int[] checks = {0};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                synchronizeAim(context);
                EntityActionInstance running = action[0];
                if (!released[0] && running.getPhase() != null
                        && running.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease(key[0], context.owner());
                    released[0] = true;
                }
                if (!over[0] && ++checks[0] < 300) {
                    helper.assertTrue(false, "Chomp has not ended yet");
                }
                if (over[0] && stage[0] == 0) {
                    stage[0] = 1;
                    over[0] = false;
                    released[0] = false;
                    context.owner().setGameMode(GameType.SURVIVAL);
                    context.owner().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
                    context.power().setStamina(context.power().getMaxStamina());
                    healthBefore[0] = context.target().getHealth();
                    context.power().setAbilityCooldown(chomp.name(), 0, 0);
                    key[0]++;
                    try {
                        action[0] = press(helper, context, chomp, chomp, InputMethod.HOLD, key[0]);
                    }
                    catch (RuntimeException | Error failure) {
                        finished[0] = true;
                        close.run();
                        throw failure;
                    }
                    helper.assertTrue(false, "the Resolve Chomp has not ended yet");
                }
                finished[0] = true;
                close.run();
            }
            helper.assertTrue(dealt[0] > 0.0F && cooldown[0] == 0,
                    "1.16 set no Chomp cooldown for a Creative user who bit: bit for " + dealt[0]
                            + ", cooldown " + cooldown[0]);
            helper.assertTrue(dealt[1] > 0.0F && cooldown[1] == CreamConfig.CHOMP_COOLDOWN.get(),
                    "1.16 set the whole " + CreamConfig.CHOMP_COOLDOWN.get() + "-tick Chomp cooldown for a user in"
                            + " Resolve who bit: bit for " + dealt[1] + ", cooldown " + cooldown[1]);
        });
    }

    // 1.16 StandEntityTask.tick ran keepTarget on every server tick of the task, the windup after the fired charge
    // too: a target that failed CreamChomp.checkTarget (a living entity within 4 blocks of the USER) was set to EMPTY
    // for good, so CreamChomp.standPerform stunned nothing and standTickPerform moved and bit nothing (no cooldown);
    // StandEntityTask still paid the 340 stamina. Here the cow steps to 4.6 blocks from the user, 3.1 from Cream.
    // The perform itself still ran its 20 ticks with nothing to bite (StandEntityTask.tick moved on only as the ticks
    // ran out), Cream held in the pose and any other Stand action able to cut it.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160,
            batch = "cream_chomp_walked_off")
    public static void chompLosesATargetThatLeftItsUsersFourBlocksInTheWindupAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-walked-off");
        placeTarget(context, true);
        // the core's stun skips a mob without AI; this cow keeps its AI but cannot walk
        context.target().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        context.target().setNoAi(false);
        context.owner().setGameMode(GameType.SURVIVAL);
        Ability chomp = requireChompAbility(helper, context);
        context.power().setAbilityCooldown(chomp.name(), 0, 0);
        Vec3 away = context.owner().position().add(0.0D, 0.0D, 4.6D);
        EntityActionInstance action = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 242);
        float initialHealth = context.target().getHealth();
        boolean[] moved = {false};
        boolean[] stunned = {false};
        boolean[] over = {false};
        boolean[] cooldown = {false};
        boolean[] recovery = {false};
        boolean[] cutByAPunch = {false};
        int[] performTicks = {0};
        Ability punch = context.power().getAbility("punch");
        helper.assertTrue(punch != null, "Cream has no punch.");
        float[] windupStamina = {-1.0F};
        float[] paid = {-1.0F};
        double[] creamToCow = {-1.0D};
        double[] userToCow = {-1.0D};
        StringBuilder trace = new StringBuilder();
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() != context.stand() || over[0]) {
                return;
            }
            ActionPhase phase = action.getPhase();
            String step = String.valueOf(phase);
            if (!trace.toString().contains(step + " ")) {
                trace.append(step).append(' ');
            }
            stunned[0] |= ModStatusEffects.isStunned(context.target());
            if (phase == ActionPhase.PERFORM) {
                performTicks[0]++;
                if (action.getPhaseTick() >= 10.0F) {
                    cutByAPunch[0] |= action.canBeCancelledInto((EntityActionType) punch);
                }
            }
            else if (phase == ActionPhase.RECOVERY) {
                recovery[0] = true;
            }
            if (phase == ActionPhase.WINDUP) {
                if (!moved[0]) {
                    moved[0] = true;
                    context.target().setPos(away);
                    context.target().setDeltaMovement(Vec3.ZERO);
                }
                windupStamina[0] = context.power().getStamina();
                creamToCow[0] = context.stand().distanceTo(context.target());
                userToCow[0] = context.owner().distanceTo(context.target());
            }
            else if (windupStamina[0] >= 0.0F) {
                // the perform starts the tick after the windup's last
                paid[0] = Math.max(paid[0], windupStamina[0] - context.power().getStamina());
            }
            if (action.isOver() || context.stand().getCurStandAction() != action) {
                over[0] = true;
                cooldown[0] = context.power().isAbilityOnCooldown(chomp.name());
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease((short) 242, context.owner());
            cleanup(context, "Cream walked-off Chomp GameTest done");
        };
        boolean[] released = {false};
        boolean[] finished = {false};
        int[] checks = {0};
        float[] dealt = {0.0F};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                synchronizeAim(context);
                if (!released[0] && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 242, context.owner());
                    released[0] = true;
                }
                if (!over[0] && ++checks[0] < 140) {
                    helper.assertTrue(false, "Chomp has not ended yet");
                }
                dealt[0] = initialHealth - context.target().getHealth();
                finished[0] = true;
                close.run();
            }
            String seen = " (phases " + trace + ")";
            helper.assertTrue(moved[0] && creamToCow[0] <= CreamSpec.CHOMP_RANGE
                            && userToCow[0] > CreamSpec.CHOMP_RANGE,
                    "setup: the cow did not end the windup more than 4 blocks from the user and within 4 of Cream:"
                            + " moved " + moved[0] + ", from the user " + userToCow[0] + ", from Cream "
                            + creamToCow[0] + seen);
            helper.assertTrue(over[0] && !stunned[0] && dealt[0] == 0.0F && !cooldown[0],
                    "1.16 dropped the Chomp's target once it was more than 4 blocks from the user, so nothing was"
                            + " stunned or bitten and no cooldown set: over " + over[0] + ", stunned " + stunned[0]
                            + ", bitten for " + dealt[0] + ", cooldown " + cooldown[0] + seen);
            helper.assertTrue(Math.abs(paid[0] - CreamSpec.CHOMP_STAMINA) < 10.0F,
                    "1.16 still paid the " + CreamSpec.CHOMP_STAMINA + " stamina as the perform began, the port paid "
                            + paid[0] + seen);
            helper.assertTrue(performTicks[0] == CreamSpec.CHOMP_PERFORM_TICKS && !recovery[0] && cutByAPunch[0],
                    "1.16 still held Cream in the 20-tick perform with nothing to bite, any other Stand action able"
                            + " to cut it, and ended it after those ticks: perform ticks " + performTicks[0]
                            + ", recovery seen " + recovery[0] + ", a punch could cut it in its second half "
                            + cutByAPunch[0] + seen);
        });
    }

    // 1.16 StandEntityTask.tick dropped the target in the perform too: a stunned target pushed more than 4 blocks
    // from the user before the bite was no longer moved to or bitten, and no cooldown was set.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160,
            batch = "cream_chomp_pushed_off")
    public static void chompLetsGoOfATargetPushedPastFourBlocksBeforeTheBiteAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-pushed-off");
        placeTarget(context, true);
        context.target().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        context.target().setNoAi(false);
        context.owner().setGameMode(GameType.SURVIVAL);
        Ability chomp = requireChompAbility(helper, context);
        context.power().setAbilityCooldown(chomp.name(), 0, 0);
        Vec3 away = context.owner().position().add(0.0D, 0.0D, 5.5D);
        EntityActionInstance action = press(
                helper, context, chomp, chomp, InputMethod.HOLD, (short) 243);
        float initialHealth = context.target().getHealth();
        boolean[] armedSeen = {false};
        boolean[] pushed = {false};
        boolean[] over = {false};
        boolean[] cooldown = {false};
        boolean[] chomped = {false};
        StringBuilder trace = new StringBuilder();
        Consumer<EntityTickEvent.Post> standTick = event -> {
            if (event.getEntity() != context.stand() || over[0]) {
                return;
            }
            ActionPhase phase = action.getPhase();
            String step = String.valueOf(phase);
            if (!trace.toString().contains(step + " ")) {
                trace.append(step).append(' ');
            }
            if (phase == ActionPhase.PERFORM && Boolean.TRUE.equals(privateField(action, "armed"))
                    && ModStatusEffects.isStunned(context.target())) {
                armedSeen[0] = true;
                if (!pushed[0] && action.getPhaseTick() >= 5.0F) {
                    pushed[0] = true;
                    context.target().setPos(away);
                    context.target().setDeltaMovement(Vec3.ZERO);
                }
            }
            if (action.isOver() || context.stand().getCurStandAction() != action) {
                over[0] = true;
                cooldown[0] = context.power().isAbilityOnCooldown(chomp.name());
                chomped[0] = Boolean.TRUE.equals(privateField(action, "chomped"));
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(standTick);
            AbilityInput.keyRelease((short) 243, context.owner());
            cleanup(context, "Cream pushed-off Chomp GameTest done");
        };
        boolean[] released = {false};
        boolean[] finished = {false};
        int[] checks = {0};
        float[] dealt = {0.0F};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                synchronizeAim(context);
                if (!released[0] && action.getPhase() != null
                        && action.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 243, context.owner());
                    released[0] = true;
                }
                if (!over[0] && ++checks[0] < 140) {
                    helper.assertTrue(false, "Chomp has not ended yet");
                }
                dealt[0] = initialHealth - context.target().getHealth();
                finished[0] = true;
                close.run();
            }
            String seen = " (phases " + trace + ")";
            helper.assertTrue(armedSeen[0] && pushed[0],
                    "setup: the Chomp did not stun the cow and reach its fifth perform tick: armed " + armedSeen[0]
                            + ", pushed " + pushed[0] + seen);
            helper.assertTrue(over[0] && !chomped[0] && dealt[0] == 0.0F && !cooldown[0],
                    "1.16 let go of a Chomp target pushed more than 4 blocks from the user before the bite: over "
                            + over[0] + ", bit " + chomped[0] + " for " + dealt[0] + ", cooldown " + cooldown[0]
                            + seen);
        });
    }

    // 1.16 took a Chomp press's target from the client's mouse target, which with the Stand away was the user's own
    // pick; the press summoned Cream and the task kept that target, so the bite landed.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 160,
            batch = "cream_chomp_stand_away")
    public static void chompPressedWithItsStandAwayBitesThePressTargetAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-stand-away");
        placeTarget(context, true);
        CreamAddon.CREAM.get().forceUnsummon(context.owner(), context.power());
        Ability chomp = requireChompAbility(helper, context);
        Runnable aim = () -> {
            LivingComponentAction.getComponent(context.owner())
                    .entityAim.setTarget(new ActionTarget(context.target()));
            context.owner().lookAt(
                    net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    context.target().getBoundingBox().getCenter());
        };
        StandEntity[] summoned = {null};
        Runnable close = () -> {
            AbilityInput.keyRelease((short) 244, context.owner());
            if (summoned[0] != null) {
                summoned[0].discard();
            }
            cleanup(context, "Cream Stand-away Chomp GameTest done");
        };
        EntityActionInstance action;
        try {
            helper.assertTrue(context.power().getSummonedStandEntity() == null,
                    "setup: Cream is still summoned");
            aim.run();
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress((short) 244, chomp, context.owner(), input, InputMethod.HOLD, 0.0F,
                        BufferingState.clickCanBuffer(), chomp.abilityId);
            }
            finally {
                input.release();
            }
            summoned[0] = context.power().getSummonedStandEntity();
            helper.assertTrue(summoned[0] != null, "the Chomp press did not summon Cream");
            action = summoned[0].getCurStandAction();
            helper.assertTrue(action != null && action.ability == chomp,
                    "the Chomp press did not start the Chomp: " + action);
            summoned[0].summonLockTicks = 0;
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        EntityActionInstance chompAction = action;
        float initialHealth = context.target().getHealth();
        boolean[] released = {false};
        boolean[] finished = {false};
        int[] checks = {0};
        float[] dealt = {0.0F};
        Object[] taskTarget = {null};
        helper.succeedWhen(() -> {
            if (!finished[0]) {
                aim.run();
                if (taskTarget[0] == null && chompAction.getPhase() != null) {
                    taskTarget[0] = chompAction.getTaskTarget(helper.getLevel()).getEntity();
                }
                if (!released[0] && chompAction.getPhase() != null
                        && chompAction.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    AbilityInput.keyRelease((short) 244, context.owner());
                    released[0] = true;
                }
                dealt[0] = initialHealth - context.target().getHealth();
                if (dealt[0] <= 0.0F && !chompAction.isOver() && ++checks[0] < 140) {
                    helper.assertTrue(false, "Chomp has not landed yet");
                }
                finished[0] = true;
                close.run();
            }
            helper.assertTrue(taskTarget[0] == context.target() && dealt[0] > 0.0F,
                    "1.16 kept the cow a Chomp was pressed at with Cream away and bit it: the task held "
                            + taskTarget[0] + ", bit for " + dealt[0]);
        });
    }

    // 1.16 CreamVoidDash: standPerformDuration(20), no standRecoveryTicks (InitStands :77); standTickPerform dashed on
    // each of the 20 perform ticks, the first one included, and onTaskStopped ended the void as the perform ended.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_dash_end")
    public static void voidDashEndsWithItsTwentiethDashTickAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "dash-end");
        context.target().discard();
        CreamEntity cream = (CreamEntity) context.stand();
        cream.beginVoidMode(CreamEntity.VoidMode.SEMI);
        Ability dash = requireAbility(
                helper, context, CreamAddon.CREAM_VOID_DASH_NAME);
        Ability ball = context.power().getAbility(
                CreamAddon.CREAM_VOID_BALL_NAME);
        int[] dashTicks = {0};
        boolean[] recovery = {false};
        boolean[] ended = {false};
        boolean[] voidAtEnd = {true};
        EntityActionInstance[] dashAction = {null};
        // runs before the core's handler ticks the Stand's action, after Cream's own tick dashed
        Consumer<EntityTickEvent.Post> afterCreamTick = event -> {
            if (event.getEntity() == cream && cream.getVoidMode() == CreamEntity.VoidMode.DASH) {
                dashTicks[0] = Math.max(dashTicks[0], cream.getVoidTicks());
            }
        };
        Consumer<EntityTickEvent.Post> afterActionTick = event -> {
            EntityActionInstance running = dashAction[0];
            if (event.getEntity() != cream || running == null || ended[0]) {
                return;
            }
            if (cream.getVoidMode() == CreamEntity.VoidMode.DASH) {
                dashTicks[0] = Math.max(dashTicks[0], cream.getVoidTicks());
            }
            if (running.getPhase() == ActionPhase.RECOVERY) {
                recovery[0] = true;
            }
            if (running.isOver() || cream.getCurStandAction() != running) {
                ended[0] = true;
                voidAtEnd[0] = cream.isInVoidState();
            }
        };
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST, EntityTickEvent.Post.class, afterCreamTick);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, EntityTickEvent.Post.class, afterActionTick);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(afterCreamTick);
            NeoForge.EVENT_BUS.unregister(afterActionTick);
            cleanup(context, "Cream dash end GameTest done");
        };
        try {
            dashAction[0] = press(helper, context, dash, ball, InputMethod.CLICK, (short) 232);
            AbilityInput.keyRelease((short) 232, context.owner());
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        helper.runAfterDelay(CreamSpec.VOID_DASH_TICKS + 15, () -> {
            close.run();
            helper.assertTrue(ended[0] && !recovery[0] && !voidAtEnd[0],
                    "1.16 ended the dash and the void as its 20-tick perform ended, with no recovery tick:"
                            + " ended " + ended[0] + ", recovery seen " + recovery[0]
                            + ", still in the void then " + voidAtEnd[0]);
            helper.assertTrue(dashTicks[0] == CreamSpec.VOID_DASH_TICKS,
                    "1.16 dashed on each of the 20 perform ticks, the port dashed " + dashTicks[0]);
            helper.succeed();
        });
    }

    // 1.16 CreamVoidBall.standPerform and CreamSemiVoidState.standPerform: setCooldownTimer(cancel, 20), a plain timer
    // with no Creative check, and PowerBaseImpl refused an action on cooldown without a word: the cancel could not
    // end the void in its first 20 ticks, in Creative as in Survival. Four rows, each a real press of the Ball or the
    // Semi-void key resolved to its cancel variation.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 400,
            batch = "cream_void_cancel_lock")
    public static void voidCancelWaitsTwentyTicksAfterTheVoidStartsAsIn116(GameTestHelper helper) {
        TestContext context = prepareCream(helper, "void-cancel-lock");
        context.target().discard();
        CreamEntity cream = (CreamEntity) context.stand();
        record Row(String base, String cancel, CreamEntity.VoidMode mode, boolean creative) {}
        List<Row> rows = List.of(
                new Row(CreamAddon.CREAM_VOID_BALL_NAME, CreamAddon.CREAM_VOID_BALL_CANCEL_NAME,
                        CreamEntity.VoidMode.BALL, false),
                new Row(CreamAddon.CREAM_VOID_BALL_NAME, CreamAddon.CREAM_VOID_BALL_CANCEL_NAME,
                        CreamEntity.VoidMode.BALL, true),
                new Row(CreamAddon.CREAM_SEMI_VOID_NAME, CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME,
                        CreamEntity.VoidMode.SEMI, false),
                new Row(CreamAddon.CREAM_SEMI_VOID_NAME, CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME,
                        CreamEntity.VoidMode.SEMI, true));
        int[] row = {0};
        int[] step = {0};
        int[] ticks = {0};
        int[] firstLock = {-1};
        short[] key = {250};
        EntityActionInstance[] running = {null};
        boolean[] closed = {false};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            Row now = rows.get(row[0]);
            String label = now.cancel() + (now.creative() ? " in Creative" : " in Survival");
            try {
                Ability base = context.power().getAbility(now.base());
                Ability cancel = context.power().getAbility(now.cancel());
                helper.assertTrue(base != null && cancel != null, "Cream has no " + now.base() + " or its cancel");
                if (step[0] != 0) {
                    context.power().setStamina(context.power().getMaxStamina());
                }
                switch (step[0]) {
                    case 0 -> {
                        context.owner().setGameMode(now.creative() ? GameType.CREATIVE : GameType.SURVIVAL);
                        context.power().setStamina(context.power().getMaxStamina());
                        for (String name : List.of(CreamAddon.CREAM_VOID_BALL_NAME, CreamAddon.CREAM_SEMI_VOID_NAME,
                                CreamAddon.CREAM_VOID_BALL_CANCEL_NAME, CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME)) {
                            context.power().setAbilityCooldown(name, 0, 0);
                        }
                        running[0] = press(helper, context, base, base, InputMethod.HOLD, key[0]);
                        running[0].setPhase(ActionPhase.PERFORM, 0);
                        AbilityInput.keyRelease(key[0]++, context.owner());
                        ticks[0] = 0;
                        step[0] = 1;
                    }
                    case 1 -> {
                        if (cream.getVoidMode() == now.mode()) {
                            firstLock[0] = context.power().getAbilityCooldown(now.cancel());
                            ticks[0] = 0;
                            step[0] = 2;
                        }
                        else {
                            helper.assertTrue(++ticks[0] <= 5, label + ": the void did not start (mode "
                                    + cream.getVoidMode() + ")");
                        }
                    }
                    case 2 -> {
                        ticks[0]++;
                        int left = context.power().getAbilityCooldown(now.cancel());
                        if (ticks[0] == 5) {
                            assertCancelPress(helper, context, base, cancel, key[0]++, false, label + " at +5 ticks");
                        }
                        if (ticks[0] > 5) {
                            helper.assertTrue(cream.getVoidMode() == now.mode() && !running[0].isOver(),
                                    "1.16 kept the void up while its cancel was on the 20-tick timer: " + label
                                            + ", mode " + cream.getVoidMode() + " at +" + ticks[0]
                                            + " ticks, cancel timer " + left);
                        }
                        if (left <= 0) {
                            helper.assertTrue(firstLock[0] >= 19 && firstLock[0] <= 20 && ticks[0] == firstLock[0],
                                    "1.16 locked the cancel for 20 ticks from the void's start: " + label
                                            + ", timer " + firstLock[0] + " when the void began, free after "
                                            + ticks[0] + " ticks");
                            assertCancelPress(helper, context, base, cancel, key[0]++, true, label + " at +20 ticks");
                            ticks[0] = 0;
                            step[0] = 3;
                        }
                        else {
                            helper.assertTrue(ticks[0] <= 25, label + ": the cancel timer never ran out");
                        }
                    }
                    case 3 -> {
                        if (cream.getVoidMode() == CreamEntity.VoidMode.NONE) {
                            clearAction(context.stand());
                            if (++row[0] == rows.size()) {
                                closed[0] = true;
                                cleanup(context, "Cream void cancel lock GameTest done");
                                helper.succeed();
                                return;
                            }
                            step[0] = 0;
                        }
                        else {
                            helper.assertTrue(++ticks[0] <= 5, label + ": the cancel pressed after the timer did not"
                                    + " end the void (mode " + cream.getVoidMode() + ")");
                        }
                    }
                    default -> throw new IllegalStateException("step " + step[0]);
                }
            }
            catch (RuntimeException | Error failure) {
                closed[0] = true;
                AbilityInput.keyRelease((short) (key[0] - 1), context.owner());
                cleanup(context, "Cream void cancel lock GameTest failed");
                throw failure;
            }
        });
    }

    // What the server resolves the base key to now (the moves are cached once a tick, so this asks again).
    private static AbilityConditionCheck resolveKey(TestContext context, Ability base) {
        context.power()._curAvailableMoves.update(context.power(), context.power().getMoveset());
        return context.power().updateAvailableMoves().getContextVariationContainer(base);
    }

    // The server's input route (ClAbilityInputPacket): the key resolved to its variation, the condition check, then the
    // press itself.
    private static void assertCancelPress(GameTestHelper helper, TestContext context, Ability base,
            Ability cancel, short key, boolean accepted, String when) {
        AbilityConditionCheck container = resolveKey(context, base);
        helper.assertTrue(container != null && container.ability == cancel,
                when + ": the key resolved to " + (container == null ? "nothing" : container.ability.name())
                        + ", not " + cancel.name());
        boolean silent = !container.conditionCheck.isPositive() && container.conditionCheck.getWarning() == null;
        helper.assertTrue(accepted ? container.conditionCheck.isPositive() : silent,
                "1.16 " + (accepted ? "accepted" : "refused without a word") + " " + when + ", the port's check was "
                        + (container.conditionCheck.isPositive() ? "positive"
                                : silent ? "a silent refusal" : "a refusal with a message"));
        BufferingState state = BufferingState.clickCanBuffer();
        if (AbilityInput.withConditionCheck(container, context.owner(), InputMethod.CLICK)) {
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(key, cancel, context.owner(), input, InputMethod.CLICK, 0.0F, state,
                        base.abilityId);
            }
            finally {
                input.release();
                AbilityInput.keyRelease(key, context.owner());
            }
        }
        helper.assertTrue(state.isActionSuccess == accepted && !state.shouldBuffer,
                "1.16 " + (accepted ? "started" : "refused and did not queue") + " " + when + ": started "
                        + state.isActionSuccess + ", buffered " + state.shouldBuffer);
    }

    // 1.16 StandEntityAction.canClickDuringTask :495-497 refused the running action's own ability pressed again
    // (clickedAction == this, isChainable false; CreamVoidDash overrode nothing). In the port the dash's void mode
    // begins in its first Stand tick, so for that tick the Ball or Semi-void key still resolves to the dash while the
    // dash is the Stand's action: that press must be refused silently and leave the running action alone. The dash has
    // no hold or recovery.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60,
            batch = "cream_void_pressed_again")
    public static void voidDashPressedAgainIsRefusedSilentlyAsIn116(GameTestHelper helper) {
        TestContext context = prepareCream(helper, "dash-again");
        try {
            context.target().discard();
            CreamEntity cream = (CreamEntity) context.stand();
            cream.beginVoidMode(CreamEntity.VoidMode.SEMI);
            Ability ball = context.power().getAbility(CreamAddon.CREAM_VOID_BALL_NAME);
            Ability dash = context.power().getAbility(CreamAddon.CREAM_VOID_DASH_NAME);
            helper.assertTrue(ball != null && dash != null, "Cream has no Enter the Void or Void Dash.");
            AbilityConditionCheck free = resolveKey(context, ball);
            helper.assertTrue(free != null && free.ability == dash && free.conditionCheck.isPositive(),
                    "setup: in the semi-void the Enter the Void key does not offer the dash: "
                            + (free == null ? "nothing" : free.ability.name() + " " + free.conditionCheck.isPositive()));
            EntityActionInstance running = installDirect(helper, context, dash);
            AbilityConditionCheck again = resolveKey(context, ball);
            helper.assertTrue(again != null && again.ability == dash && !again.conditionCheck.isPositive()
                            && again.conditionCheck.getWarning() == null,
                    "1.16 refused the dash pressed again during the dash without a word, the port's check was "
                            + (again == null ? "nothing" : again.ability.name() + (again.conditionCheck.isPositive()
                                    ? " positive" : " refused with a message " + again.conditionCheck.getWarning())));
            BufferingState state = BufferingState.clickCanBuffer();
            if (AbilityInput.withConditionCheck(again, context.owner(), InputMethod.CLICK)) {
                FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AbilityInput.keyPress((short) 260, dash, context.owner(), input, InputMethod.CLICK, 0.0F, state,
                            ball.abilityId);
                }
                finally {
                    input.release();
                    AbilityInput.keyRelease((short) 260, context.owner());
                }
            }
            EntityActionInstance now = context.stand().getCurStandAction();
            helper.assertTrue(!state.isActionSuccess && !state.shouldBuffer && now == running && !running.isOver(),
                    "1.16 neither restarted nor queued the dash pressed again: started " + state.isActionSuccess
                            + ", buffered " + state.shouldBuffer + ", the Stand's action " + now + " (was " + running
                            + ")");
        }
        finally {
            cleanup(context, "Cream dash pressed again GameTest done");
        }
        helper.succeed();
    }

    // 1.16 CreamVoidBall :256-257 and CreamVoidDash :162-163 gathered without spectators; the ball also spared a player
    // whose abilities.invulnerable was set (Creative), the dash any entity with the invulnerable flag, living or not,
    // and its out-of-world hit still reached a Creative player. Four rows (mode x voidInstaKills), each a real void
    // tick; the config value is global, so every row has its own batch.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_void_targets_ball_kill")
    public static void voidBallSparesCreativePlayersAndSpectatorsAsIn116(GameTestHelper helper) {
        voidTargetsRow(helper, CreamEntity.VoidMode.BALL, true, "void-targets-ball-kill");
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_void_targets_ball_damage")
    public static void voidBallSparesCreativePlayersAndSpectatorsWithoutInstakillsAsIn116(GameTestHelper helper) {
        voidTargetsRow(helper, CreamEntity.VoidMode.BALL, false, "void-targets-ball-damage");
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_void_targets_dash_kill")
    public static void voidDashSparesInvulnerableEntitiesAndSpectatorsAsIn116(GameTestHelper helper) {
        voidTargetsRow(helper, CreamEntity.VoidMode.DASH, true, "void-targets-dash-kill");
    }

    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100,
            batch = "cream_void_targets_dash_damage")
    public static void voidDashSparesInvulnerableEntitiesAndSpectatorsWithoutInstakillsAsIn116(
            GameTestHelper helper) {
        voidTargetsRow(helper, CreamEntity.VoidMode.DASH, false, "void-targets-dash-damage");
    }

    private static void voidTargetsRow(GameTestHelper helper, CreamEntity.VoidMode mode, boolean instaKills,
            String route) {
        boolean ball = mode == CreamEntity.VoidMode.BALL;
        ModConfigSpec.BooleanValue instaConfig = CreamConfig.VOID_INSTAKILLS;
        boolean previousInstaKills = instaConfig.get();
        Runnable restoreConfig = () -> instaConfig.set(previousInstaKills);
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                restoreConfig.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                restoreConfig.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
                restoreConfig.run();
            }
        });
        instaConfig.set(instaKills);

        TestContext context = prepareCream(helper, route);
        context.target().discard();
        // Creative stamina is infinite, and the user is not the subject here.
        context.owner().setGameMode(GameType.SURVIVAL);
        CreamEntity cream = (CreamEntity) context.stand();
        ServerPlayer user = context.owner();
        Vec3 center = user.position();
        List<ServerPlayer> players = new ArrayList<>();
        Runnable close = () -> {
            for (ServerPlayer player : players) {
                player.connection.disconnect(Component.literal("Cream void targets GameTest done"));
            }
            restoreConfig.run();
            cleanup(context, "Cream void targets GameTest done");
        };
        ServerPlayer creative;
        ServerPlayer spectator;
        ServerPlayer survival;
        Cow invulnerableCow;
        Boat invulnerableBoat;
        Boat plainBoat;
        try {
            creative = makeCompatibleMockPlayer(helper, "cream-" + route + "-cr");
            players.add(creative);
            spectator = makeCompatibleMockPlayer(helper, "cream-" + route + "-sp");
            players.add(spectator);
            survival = makeCompatibleMockPlayer(helper, "cream-" + route + "-su");
            players.add(survival);
            creative.setGameMode(GameType.CREATIVE);
            spectator.setGameMode(GameType.SPECTATOR);
            survival.setGameMode(GameType.SURVIVAL);
            creative.setPos(center.add(0.8D, 0.0D, 0.0D));
            spectator.setPos(center.add(-0.8D, 0.0D, 0.0D));
            survival.setPos(center.add(0.0D, 0.0D, 0.8D));
            invulnerableCow = EntityType.COW.create(helper.getLevel());
            invulnerableBoat = EntityType.BOAT.create(helper.getLevel());
            plainBoat = EntityType.BOAT.create(helper.getLevel());
            helper.assertTrue(invulnerableCow != null && invulnerableBoat != null && plainBoat != null,
                    "fixture: the void targets could not be created");
            invulnerableCow.setNoAi(true);
            invulnerableCow.setNoGravity(true);
            invulnerableCow.setInvulnerable(true);
            invulnerableCow.setPos(center.add(0.0D, 0.0D, -0.8D));
            invulnerableBoat.setNoGravity(true);
            invulnerableBoat.setInvulnerable(true);
            invulnerableBoat.setPos(center.add(1.0D, 0.0D, -1.0D));
            plainBoat.setNoGravity(true);
            plainBoat.setPos(center.add(-1.0D, 0.0D, -1.0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(invulnerableCow)
                            && helper.getLevel().addFreshEntity(invulnerableBoat)
                            && helper.getLevel().addFreshEntity(plainBoat),
                    "fixture: the void targets did not enter the level");
            helper.assertTrue(JojoModUtil.canHarm(user, creative),
                    "fixture: canHarm refuses the Creative player, so the exclusion would not be what spares it");
            helper.assertTrue(JojoModUtil.canHarm(user, invulnerableCow),
                    "fixture: canHarm refuses the invulnerable cow, so the exclusion would not be what spares it");
            helper.assertTrue(creative.getAbilities().invulnerable && !creative.isSpectator()
                            && spectator.isSpectator() && !survival.getAbilities().invulnerable
                            && !user.getAbilities().invulnerable,
                    "fixture: the game modes did not apply");
            if (ball) {
                cream.beginVoidMode(CreamEntity.VoidMode.BALL);
            }
            else {
                cream.beginVoidMode(CreamEntity.VoidMode.SEMI);
                Ability dash = requireAbility(helper, context, CreamAddon.CREAM_VOID_DASH_NAME);
                Ability base = context.power().getAbility(CreamAddon.CREAM_VOID_BALL_NAME);
                press(helper, context, dash, base, InputMethod.CLICK, (short) 233);
                AbilityInput.keyRelease((short) 233, user);
            }
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        ServerPlayer creativeFinal = creative;
        ServerPlayer spectatorFinal = spectator;
        ServerPlayer survivalFinal = survival;
        Cow cowFinal = invulnerableCow;
        Boat invulnerableBoatFinal = invulnerableBoat;
        Boat plainBoatFinal = plainBoat;
        helper.runAfterDelay(8, () -> {
            boolean creativeHit = voidHit(creativeFinal);
            boolean spectatorHit = voidHit(spectatorFinal);
            boolean survivalHit = voidHit(survivalFinal);
            boolean cowHit = voidHit(cowFinal);
            boolean invulnerableBoatHit = voidHit(invulnerableBoatFinal);
            boolean plainBoatHit = voidHit(plainBoatFinal);
            boolean inVoid = cream.isInVoidState();
            close.run();
            String state = (ball ? "ball" : "dash") + (instaKills ? " with" : " without") + " voidInstaKills: creative "
                    + creativeHit + ", spectator " + spectatorHit + ", survival " + survivalHit + ", invulnerable cow "
                    + cowHit + ", invulnerable boat " + invulnerableBoatHit + ", plain boat " + plainBoatHit;
            helper.assertTrue(inVoid, "fixture: the void ended before the check, " + state);
            helper.assertTrue(survivalHit && plainBoatHit,
                    "1.16 erased a Survival player and an ordinary entity in the void, the port's: " + state);
            helper.assertTrue(!spectatorHit,
                    "1.16 never gathered a spectator into the void, the port's: " + state);
            if (ball) {
                helper.assertTrue(!creativeHit,
                        "1.16's ball spared a Creative player (abilities.invulnerable), the port's: " + state);
                helper.assertTrue(cowHit && invulnerableBoatHit,
                        "1.16's ball erased an invulnerable entity, the port's: " + state);
            }
            else {
                helper.assertTrue(creativeHit,
                        "1.16's dash still hit a Creative player with its out-of-world damage, the port's: " + state);
                helper.assertTrue(!cowHit && !invulnerableBoatHit,
                        "1.16's dash skipped an entity with the invulnerable flag, living or not, the port's: " + state);
            }
            helper.succeed();
        });
    }

    private static boolean voidHit(Entity entity) {
        return entity.isRemoved()
                || entity instanceof LivingEntity living
                        && (!living.isAlive() || living.getHealth() < living.getMaxHealth());
    }

    // 1.16 InitStands :70-75, :77, :80 and the action classes' tick overrides: cream_chop (CreamChop) windup 8, the
    // light attack's perform (StandEntityLightAttack.getStandActionTicks) and getHeavyAttackRecovery(attack speed,
    // last heavy finisher value); cream_finisher_punch (CreamDoublePunchFinisher) windup 10; cream_heavy_claw's
    // standWindupDuration(60) was not read (StandEntityHeavyAttack.getStandWindupTicks takes the formula); the void
    // ball, the semi-void and the void dash had no standRecoveryTicks.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60)
    public static void creamPhaseLengthsFollowTheDonorAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "phase-lengths");
        try {
            StandEntity stand = context.stand();
            double speed = stand.getAttackSpeed();
            helper.assertTrue(speed > 0.0D, "setup: Cream has no attack speed");
            for (float lastHeavy : new float[] {0.0F, 1.0F}) {
                stand.setFinisherMeter(lastHeavy);
                stand.setHeavyPunchFinisher();
                stand.setFinisherMeter(0.0F);
                EntityActionInstance chop = configuredAction(helper, context, CreamAddon.CREAM_CHOP_NAME);
                assertPhases(helper, chop, "cream_chop after a heavy finisher value of " + lastHeavy,
                        8.0F, lightPerform116(speed, 0.0F), heavyRecovery116(speed, lastHeavy));
            }
            for (float meter : new float[] {0.0F, 1.0F}) {
                stand.setFinisherMeter(meter);
                EntityActionInstance finisher = configuredAction(
                        helper, context, CreamAddon.CREAM_FINISHER_PUNCH_NAME);
                helper.assertTrue(finisher.phasesLength.getFloat(ActionPhase.WINDUP) == 10.0F,
                        "1.16 CreamDoublePunchFinisher wound up 10 ticks at a finisher meter of " + meter
                                + ", the port " + finisher.phasesLength.getFloat(ActionPhase.WINDUP));
                EntityActionInstance heavyClaw = configuredAction(
                        helper, context, CreamAddon.CREAM_HEAVY_CLAW_NAME);
                float clawWindup = heavyWindup116(stand.getAttackSpeed(), meter);
                helper.assertTrue(heavyClaw.phasesLength.getFloat(ActionPhase.WINDUP) == clawWindup,
                        "1.16 cream_heavy_claw wound up getHeavyAttackWindup ticks (" + clawWindup
                                + ") at a finisher meter of " + meter + ", the port "
                                + heavyClaw.phasesLength.getFloat(ActionPhase.WINDUP));
            }
            stand.setFinisherMeter(0.0F);
            for (String name : List.of(CreamAddon.CREAM_VOID_BALL_NAME, CreamAddon.CREAM_SEMI_VOID_NAME,
                    CreamAddon.CREAM_VOID_DASH_NAME)) {
                EntityActionInstance voidAction = configuredAction(helper, context, name);
                helper.assertTrue(voidAction.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
                        "1.16 " + name + " had no recovery ticks, the port has "
                                + voidAction.phasesLength.getFloat(ActionPhase.RECOVERY));
            }
        }
        finally {
            cleanup(context, "Cream phase lengths GameTest done");
        }
        helper.succeed();
    }

    // 1.16 038 InitStands :71 cream_heavy_punch (and its finisher's fallback clip) set no standPose and CreamModel
    // .initActionPoses :63-119 puts no HEAVY_ATTACK pose, so Cream drew the core's default humanoid heavy: the port's
    // copy is the core default heavy_punch (the official hooking punch) with the core's one-tick markers.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 20)
    public static void heavyPunchKeepsTheCoreDefaultHookingPunch(
            GameTestHelper helper) {
        JsonObject coreHeavy = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/stand_skins/star_platinum/assets/jojo_ripples/animations/"
                        + "star_platinum.animation.json", "heavy_punch");
        JsonObject coreDefault = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/animations/stand_default.animation.json", "heavy_punch");
        JsonObject markers = JsonParser.parseString("{\"0.0\":\"phase = WINDUP;\","
                + "\"0.4333\":\"phase = PERFORM;\",\"0.4833\":\"phase = RECOVERY;\"}").getAsJsonObject();
        helper.assertTrue(markers.equals(coreDefault.get("timeline"))
                        && markers.equals(coreHeavy.get("timeline")),
                "setup: the core default heavy_punch no longer has PERFORM 0.4333 / RECOVERY 0.4833: "
                        + coreDefault.get("timeline") + ", Star Platinum " + coreHeavy.get("timeline"));
        JsonObject coreBones = coreHeavy.getAsJsonObject("bones");
        for (String skin : List.of("cream", "skina", "skinb", "skinc", "skind", "skine")) {
            JsonObject heavy = animationClip(helper, CreamAddon.MOD_ID,
                    "assets/rotp_cream/stand_skins/" + skin + "/assets/rotp_cream/animations/cream.animation.json",
                    "heavy_punch");
            helper.assertTrue(markers.equals(heavy.get("timeline")),
                    "Cream's heavy_punch (" + skin + ") must keep the core default's PERFORM 0.4333 / RECOVERY"
                            + " 0.4833 (the thrust lands at 0.4667 in the one-tick perform): " + heavy.get("timeline"));
            helper.assertTrue(coreHeavy.get("animation_length").equals(heavy.get("animation_length"))
                            && coreHeavy.get("loop").equals(heavy.get("loop")),
                    "Cream's heavy_punch (" + skin + ") length or loop differs from the core default's");
            JsonObject bones = heavy.getAsJsonObject("bones");
            helper.assertTrue(bones.has("right_arm") && bones.has("right_arm_bend"),
                    "Cream's heavy_punch (" + skin + ") lost its striking arm: " + bones.keySet());
            for (String bone : bones.keySet()) {
                helper.assertTrue(bones.get(bone).equals(coreBones.get(bone)),
                        "Cream's heavy_punch (" + skin + ") must stay the core default's hooking punch: " + bone
                                + " " + bones.get(bone) + ", core " + coreBones.get(bone));
            }
        }
        helper.succeed();
    }

    // 1.16 038 InitStands :74 cream_block is a CreamBlock (a StandEntityBlock, StandPose.BLOCK) and CreamModel puts no
    // BLOCK pose, so Cream drew HumanoidStandModel's guard (:590-607): crossed forearms, arms following the look pitch up
    // to 60 degrees. The core's stand_default block is that guard; Star Platinum's static one is its own clip. 1.16
    // LegacyStandAnimator.poseStand :73 posed Cream's whole idle (CreamModel.initIdlePose :126) first and the BLOCK pose
    // names no leg and no torso2, so the guard kept Cream's idle legs and lower torso, not stand_default's leg stance.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 20)
    public static void guardKeepsTheDefaultPitchFollowingGuardAsIn116(
            GameTestHelper helper) {
        JsonObject core = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/animations/stand_default.animation.json", "block");
        JsonObject star = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/stand_skins/star_platinum/assets/jojo_ripples/animations/"
                        + "star_platinum.animation.json", "block");
        JsonObject coreBones = core.getAsJsonObject("bones");
        helper.assertTrue(coreBones.has("right_arm")
                        && coreBones.get("right_arm").toString().contains("math.min(60, query.head_x_rotation)")
                        && !coreBones.equals(star.getAsJsonObject("bones")),
                "setup: the core default block is no longer the pitch-following humanoid guard: " + coreBones);
        List<String> idleBones = List.of("left_leg", "right_leg", "left_leg_bend", "right_leg_bend", "torso_lower");
        for (String skin : List.of("cream", "skina", "skinb", "skinc", "skind", "skine")) {
            String file = "assets/rotp_cream/stand_skins/" + skin + "/assets/rotp_cream/animations/cream.animation.json";
            JsonObject guard = animationClip(helper, CreamAddon.MOD_ID, file, "block");
            JsonObject idle = animationClip(helper, CreamAddon.MOD_ID, file, "idle").getAsJsonObject("bones");
            helper.assertTrue(Objects.equals(core.get("loop"), guard.get("loop"))
                            && Objects.equals(core.get("animation_length"), guard.get("animation_length")),
                    "Cream's block (" + skin + ") loop or length differs from the core default guard's");
            JsonObject bones = guard.getAsJsonObject("bones");
            Set<String> expected = new LinkedHashSet<>(coreBones.keySet());
            expected.addAll(idleBones);
            helper.assertTrue(bones.keySet().equals(expected),
                    "Cream's block (" + skin + ") must hold the core default guard's bones plus Cream's idle legs and"
                            + " lower torso: " + bones.keySet());
            for (String bone : bones.keySet()) {
                if (idleBones.contains(bone)) {
                    JsonElement idleFirst = idle.getAsJsonObject(bone).getAsJsonObject("rotation").get("0.0");
                    JsonElement held = bones.getAsJsonObject(bone).getAsJsonObject("rotation").get("vector");
                    helper.assertTrue(idleFirst != null && idleFirst.equals(held)
                                    && !bones.get(bone).equals(coreBones.get(bone)),
                            "Cream's block (" + skin + ") must keep Cream's own idle " + bone + " as 1.16's guard did: "
                                    + bones.get(bone) + ", idle " + idleFirst + ", core default " + coreBones.get(bone));
                    continue;
                }
                helper.assertTrue(bones.get(bone).equals(coreBones.get(bone)),
                        "Cream's block (" + skin + ") must stay the core default pitch-following guard: " + bone
                                + " " + bones.get(bone) + ", core " + coreBones.get(bone));
            }
        }
        helper.succeed();
    }

    private static JsonObject animationClip(GameTestHelper helper, String modId, String file, String clip) {
        JsonObject animations;
        try {
            Path path = ModList.get().getModFileById(modId).getFile().findResource(file);
            animations = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + modId + " " + file, e);
        }
        helper.assertTrue(animations != null && animations.has(clip), modId + " " + file + " has no " + clip);
        return animations.getAsJsonObject(clip);
    }

    // 1.16 StandStatFormulas.getLightAttackRecovery (the light attack's perform)
    private static float lightPerform116(double speed, float finisherMeter) {
        double val = (24 - speed) / 2;
        if (val <= 1) {
            return 1;
        }
        if (val > 4) {
            val = Math.max(val * (1.0F - finisherMeter * 0.4F), 4);
        }
        return Mth.ceil(val);
    }

    // 1.16 StandStatFormulas.getHeavyAttackRecovery(speed, finisher)
    private static float heavyRecovery116(double speed, float finisher) {
        float max = (40 - (float) speed * 1.25F) * 0.75F;
        float min = max / 2;
        return Mth.floor(Mth.lerp(finisher, max, min));
    }

    // 1.16 StandStatFormulas.getHeavyAttackWindup
    private static float heavyWindup116(double speed, float finisherMeter) {
        float f = 40 - (float) speed * 1.25F;
        return Mth.ceil(Mth.lerp(finisherMeter, f * 2 / 3, f / 3));
    }

    private static void assertPhases(GameTestHelper helper, EntityActionInstance action, String what,
            float windup, float perform, float recovery) {
        float[] actual = {
                action.phasesLength.getFloat(ActionPhase.WINDUP),
                action.phasesLength.getFloat(ActionPhase.PERFORM),
                action.phasesLength.getFloat(ActionPhase.RECOVERY) };
        helper.assertTrue(actual[0] == windup && actual[1] == perform && actual[2] == recovery,
                "1.16 " + what + " ran windup " + windup + ", perform " + perform + ", recovery " + recovery
                        + "; the port " + actual[0] + ", " + actual[1] + ", " + actual[2]);
    }

    // The action a press of the ability would set, with its phase lengths from the Stand now; not installed.
    private static EntityActionInstance configuredAction(GameTestHelper helper, TestContext context, String name) {
        Ability ability = context.power().getAbility(name);
        helper.assertTrue(ability instanceof EntityActionType, "Cream has no action ability " + name);
        EntityActionType type = (EntityActionType) ability;
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), context.owner(), context.stand());
        action.setStartingPhase();
        return action;
    }

    // 1.16 CreamChomp.getTranslatedName named the first living entity but the user on a 4-block ray from the
    // user's eyes, whatever the press would do with it: the Stand's reach, teams, pets and the void state.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompNameOffersAnyLivingEntityWithinFourBlocksAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-name");
        Wolf wolf = null;
        PlayerTeam team = null;
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        try {
            Ability chomp = requireChompAbility(helper, context);
            Vec3 ownerPos = context.owner().position();
            Cow cow = context.target();
            StandEntity stand = context.stand();

            cow.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
            synchronizeAim(context);
            helper.assertTrue(hudChompTarget(context.owner()) == cow,
                    "setup: the Chomp name does not offer a cow 3.5 blocks ahead: "
                            + hudChompTarget(context.owner()));

            stand.moveTo(ownerPos.x, ownerPos.y, ownerPos.z - 10.0D, 0.0F, 0.0F);
            assertChompCheck(helper, chomp, context, "target_too_far",
                    "a cow 3.5 blocks from its user and 13.5 from the Stand");
            helper.assertTrue(hudChompTarget(context.owner()) == cow,
                    "1.16 named a cow 3.5 blocks from the user beyond the Stand's reach, though"
                            + " the press is refused (target_too_far): " + hudChompTarget(context.owner()));
            stand.moveTo(ownerPos.x, ownerPos.y, ownerPos.z + 1.5D, 0.0F, 0.0F);

            team = scoreboard.addPlayerTeam("cream-name-" + helper.getTick());
            team.setAllowFriendlyFire(false);
            scoreboard.addPlayerToTeam(context.owner().getScoreboardName(), team);
            scoreboard.addPlayerToTeam(cow.getScoreboardName(), team);
            assertChompCheck(helper, chomp, context, "",
                    "a cow of the user's team without friendly fire");
            helper.assertTrue(hudChompTarget(context.owner()) == cow,
                    "1.16 named a teammate 3.5 blocks ahead, though the press is refused: "
                            + hudChompTarget(context.owner()));
            scoreboard.removePlayerTeam(team);
            team = null;

            wolf = EntityType.WOLF.create(helper.getLevel());
            helper.assertTrue(wolf != null && helper.getLevel().addFreshEntity(wolf),
                    "setup: the wolf could not enter the level");
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            wolf.tame(context.owner());
            cow.setPos(ownerPos.add(0.0D, 40.0D, 0.0D));
            wolf.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
            synchronizeAimAt(context, wolf);
            assertChompCheck(helper, chomp, context, "",
                    "the user's own tamed wolf");
            helper.assertTrue(hudChompTarget(context.owner()) == wolf,
                    "1.16 named the user's tamed wolf on the Chomp name, though the press is"
                            + " refused: " + hudChompTarget(context.owner()));

            ((CreamEntity) stand).beginVoidMode(CreamEntity.VoidMode.BALL);
            helper.assertTrue(hudChompTarget(context.owner()) == wolf,
                    "1.16 named the entity ahead in the void state too: "
                            + hudChompTarget(context.owner()));
            ((CreamEntity) stand).endVoidMode(false);

            wolf.setPos(ownerPos.add(0.0D, 0.0D, 4.6D));
            synchronizeAimAt(context, wolf);
            helper.assertTrue(hudChompTarget(context.owner()) == null,
                    "1.16 named nothing beyond 4 blocks of the user: "
                            + hudChompTarget(context.owner()));
        }
        finally {
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
            if (wolf != null) {
                wolf.discard();
            }
            cleanup(context, "Cream Chomp name GameTest done");
        }
        helper.succeed();
    }

    // 1.16 StandEntity.canAttack (:1932-1949), which StandEntityAction.checkStandTarget asked of every Chomp
    // target: not the user's own tamed animal, not an animal riding the user's vehicle, not a teammate without
    // friendly fire. With the Stand not summoned (the press before the auto-summon) the same rules hold for the
    // user. Asked through the press, and through canBeChomped for the cases a crosshair cannot single out.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void chompRefusesTamedAnimalsAndSharedRidesWithOrWithoutItsStandAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "chomp-animals");
        Wolf wild = null;
        Wolf tamed = null;
        Boat boat = null;
        PlayerTeam team = null;
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        try {
            Ability chomp = requireChompAbility(helper, context);
            ServerPlayer owner = context.owner();
            Vec3 ownerPos = owner.position();
            Cow cow = context.target();
            wild = EntityType.WOLF.create(helper.getLevel());
            tamed = EntityType.WOLF.create(helper.getLevel());
            boat = EntityType.BOAT.create(helper.getLevel());
            helper.assertTrue(wild != null && tamed != null && boat != null
                            && helper.getLevel().addFreshEntity(wild)
                            && helper.getLevel().addFreshEntity(tamed)
                            && helper.getLevel().addFreshEntity(boat),
                    "setup: the wolves or the boat could not enter the level");
            for (Wolf wolf : List.of(wild, tamed)) {
                wolf.setNoAi(true);
                wolf.setNoGravity(true);
            }
            tamed.tame(owner);
            boat.setNoGravity(true);
            boat.setPos(ownerPos.add(0.0D, 40.0D, 0.0D));
            cow.setPos(ownerPos.add(0.0D, 40.0D, 5.0D));
            for (boolean summoned : new boolean[] {true, false}) {
                if (!summoned) {
                    CreamAddon.CREAM.get().forceUnsummon(owner, context.power());
                    context.stand().discard();
                    helper.assertTrue(context.power().getSummonedStandEntity() == null,
                            "setup: Cream is still summoned");
                }
                StandEntity stand = summoned ? context.stand() : null;
                String when = summoned ? " with its Stand summoned" : " with its Stand away";
                owner.setPos(ownerPos);
                boat.setPos(ownerPos.add(0.0D, 40.0D, 0.0D));
                cow.setPos(ownerPos.add(0.0D, 40.0D, 5.0D));

                wild.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
                tamed.setPos(ownerPos.add(0.0D, 40.0D, 0.0D));
                synchronizeAimAt(context, wild);
                assertChompCheck(helper, chomp, context, null, "a wild wolf 3.5 blocks ahead" + when);
                helper.assertTrue(canBeChomped(owner, stand, wild),
                        "1.16 let Chomp bite a wild wolf" + when);

                wild.setPos(ownerPos.add(0.0D, 40.0D, 0.0D));
                tamed.setPos(ownerPos.add(0.0D, 0.0D, 3.5D));
                synchronizeAimAt(context, tamed);
                assertChompCheck(helper, chomp, context, "", "the user's own tamed wolf" + when);
                helper.assertTrue(!canBeChomped(owner, stand, tamed),
                        "1.16 refused the user's own tamed wolf" + when);

                helper.assertTrue(canBeChomped(owner, stand, cow),
                        "1.16 let Chomp bite a cow riding nothing" + when);
                boat.setPos(ownerPos);
                cow.setPos(ownerPos);
                owner.startRiding(boat, true);
                cow.startRiding(boat, true);
                helper.assertTrue(cow.isPassengerOfSameVehicle(owner),
                        "setup: the cow does not share the user's boat");
                boolean sharedRide = canBeChomped(owner, stand, cow);
                cow.stopRiding();
                owner.stopRiding();
                helper.assertTrue(!sharedRide,
                        "1.16 refused an animal riding the user's vehicle" + when);

                team = scoreboard.addPlayerTeam("cream-animals-" + helper.getTick() + summoned);
                team.setAllowFriendlyFire(false);
                scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
                scoreboard.addPlayerToTeam(cow.getScoreboardName(), team);
                boolean teammate = canBeChomped(owner, stand, cow);
                scoreboard.removePlayerTeam(team);
                team = null;
                helper.assertTrue(!teammate,
                        "1.16 refused a teammate without friendly fire" + when);
            }
        }
        finally {
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
            context.owner().stopRiding();
            for (Entity extra : new Entity[] {wild, tamed, boat}) {
                if (extra != null) {
                    extra.discard();
                }
            }
            cleanup(context, "Cream Chomp animal rules GameTest done");
        }
        helper.succeed();
    }

    // 1.16 CreamEatItem.checkSpecificConditions: the void states refused without a word, an empty off hand
    // said "Cream needs an item in your off hand" (no_item_in_offhand_cream); CreamEatItemStack the same.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 100)
    public static void eatItemSaysWhenTheOffHandIsEmptyAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "eat-empty");
        CreamEntity cream = (CreamEntity) context.stand();
        try {
            for (String eat : List.of(CreamAddon.CREAM_EAT_ITEM_NAME,
                    CreamAddon.CREAM_EAT_ITEM_STACK_NAME)) {
                Ability ability = context.power().getAbility(eat);
                helper.assertTrue(ability != null, "Cream has no " + eat);
                context.owner().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                assertCheck(helper, ability.checkConditions(context.power()),
                        "jojo.message.action_condition.no_item_in_offhand_cream",
                        "the press of " + eat + " with an empty off hand");
                context.owner().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE, 3));
                assertCheck(helper, ability.checkConditions(context.power()), null,
                        "the press of " + eat + " with an apple in the off hand");
                cream.beginVoidMode(CreamEntity.VoidMode.BALL);
                assertCheck(helper, ability.checkConditions(context.power()), "",
                        "the press of " + eat + " in the void with an apple in the off hand");
                context.owner().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                assertCheck(helper, ability.checkConditions(context.power()), "",
                        "the press of " + eat + " in the void with an empty off hand");
                cream.endVoidMode(false);
            }
        }
        finally {
            cream.endVoidMode(false);
            context.owner().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            cleanup(context, "Cream eat empty off hand GameTest done");
        }
        helper.succeed();
    }

    // 1.16 noAdheringToUserOffset kept Cream off its user's offset in the
    // void on every side; a client that does not know the user's power asks
    // the client fallback, which must agree.
    @GameTest(
            templateNamespace = TEMPLATE_NAMESPACE,
            template = "empty",
            timeoutTicks = 60)
    public static void voidStateKeepsCreamOffItsUserOnEveryClient(
            GameTestHelper helper) {
        TestContext context = prepareCream(helper, "void-fallback");
        try {
            CreamEntity cream = (CreamEntity) context.stand();
            for (String name : List.of(CreamAddon.CREAM_VOID_BALL_NAME,
                    CreamAddon.CREAM_SEMI_VOID_NAME,
                    CreamAddon.CREAM_VOID_DASH_NAME)) {
                StandEntityAbility ability = (StandEntityAbility)
                        context.power().getAbility(name);
                helper.assertTrue(ability != null, "Cream has no " + name);
                for (CreamEntity.VoidMode mode : List.of(
                        CreamEntity.VoidMode.NONE, CreamEntity.VoidMode.BALL)) {
                    if (mode != CreamEntity.VoidMode.NONE) {
                        cream.beginVoidMode(mode);
                    }
                    boolean server = ability.noAdheringToUserOffset(
                            context.power(), cream);
                    boolean client = ability.noAdheringToUserOffsetClientFallback(cream);
                    cream.endVoidMode(false);
                    helper.assertTrue(server == (mode != CreamEntity.VoidMode.NONE)
                                    && client == server,
                            name + " in void mode " + mode + ": the server keeps Cream"
                                    + " off its user's offset " + server
                                    + ", a client without the power " + client);
                }
            }
        }
        finally {
            cleanup(context, "Cream void fallback GameTest done");
        }
        helper.succeed();
    }

    // The entity Chomp's ability name offers (getName itself loads client
    // classes on a dedicated server).
    private static Entity hudChompTarget(LivingEntity user) {
        try {
            Method method = CreamChompAbility.class.getDeclaredMethod(
                    "chompTarget", LivingEntity.class);
            method.setAccessible(true);
            return (Entity) method.invoke(null, user);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not ask Chomp's name target", exception);
        }
    }

    // CreamChompAbility.ChompAction.canBeChomped, the rule shared by the press, the hold and the bite.
    private static boolean canBeChomped(LivingEntity user, StandEntity stand, Entity target) {
        try {
            Method method = CreamChompAbility.ChompAction.class.getDeclaredMethod(
                    "canBeChomped", LivingEntity.class, StandEntity.class, Entity.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, user, stand, target);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not ask canBeChomped", exception);
        }
    }

    private static void assertHeldCheck(GameTestHelper helper, Ability ability,
            EntityActionInstance action, TestContext context, String warning,
            String when) {
        assertCheck(helper, ((EntityActionAbility) ability)
                        .checkHeldActionConditions(action, context.power()),
                warning, when + " (held check)");
    }

    // warning: null for a positive check, "" for a silent refusal, else the key
    private static void assertCheck(GameTestHelper helper, ConditionCheck check,
            String warning, String when) {
        String actual;
        if (check.isPositive()) {
            actual = null;
        }
        else if (check.getWarning() == null) {
            actual = "";
        }
        else if (check.getWarning().getContents()
                instanceof TranslatableContents translatable) {
            actual = translatable.getKey();
        }
        else {
            actual = "?";
        }
        helper.assertTrue(Objects.equals(warning, actual),
                "1.16 answered " + when + " with " + describeCheck(warning)
                        + ", the port answered " + describeCheck(actual));
    }

    private static Ability requireChompAbility(
            GameTestHelper helper, TestContext context) {
        Ability chomp = context.power().getAbility(CreamAddon.CREAM_CHOMP_NAME);
        helper.assertTrue(chomp != null, "Cream has no Chomp.");
        return chomp;
    }

    // warning: null for a positive check, "" for a silent refusal
    private static void assertChompCheck(GameTestHelper helper, Ability chomp,
            TestContext context, String warning, String when) {
        ConditionCheck check = chomp.checkSpecificConditions(context.power());
        String actual;
        if (check.isPositive()) {
            actual = null;
        }
        else if (check.getWarning() == null) {
            actual = "";
        }
        else if (check.getWarning().getContents()
                instanceof TranslatableContents translatable) {
            actual = translatable.getKey();
        }
        else {
            actual = "?";
        }
        String expected = warning == null || warning.isEmpty() ? warning
                : "jojo.message.action_condition." + warning;
        helper.assertTrue(Objects.equals(expected, actual),
                "1.16 answered the click of Chomp at " + when + " with "
                        + describeCheck(expected) + ", the port answered "
                        + describeCheck(actual));
    }

    private static String describeCheck(String warning) {
        return warning == null ? "a positive check"
                : warning.isEmpty() ? "a silent refusal" : warning;
    }

    private static EntityActionInstance installDirect(GameTestHelper helper,
            TestContext context, Ability ability) {
        EntityActionType type = (EntityActionType) ability;
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), context.owner(),
                context.stand());
        action.setStartingPhase();
        synchronizeAim(context);
        LivingComponentAction.getComponent(context.stand())
                .setAction(action, context.owner(), SyncType.NO_SYNC);
        helper.assertTrue(context.stand().getCurStandAction() == action,
                "setup: the Chomp did not become the Stand's action");
        return action;
    }

    private static EntityActionInstance pressFor(GameTestHelper helper,
            TestContext context, String activeName, String baseName,
            InputMethod method, short key) {
        Ability active = requireAbility(helper, context, activeName);
        Ability base = requireAbility(helper, context, baseName);
        return press(helper, context, active, base, method, key);
    }

    private static void clearAction(StandEntity stand) {
        LivingComponentAction.getComponent(stand)
                .setAction(null, SyncType.TRACKING_AND_SELF);
    }

    private static void assertActionAt(GameTestHelper helper,
            ServerPlayer user, StandEntity stand, EntityActionInstance action,
            double dx, double dy, double dz, String when) {
        helper.assertTrue(
                LivingComponentAction.getCurEntityAction(stand) == action,
                action.ability.getAbilityId().nameInMoveset()
                        + " is no longer the Stand's action");
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D
                        && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put Cream at its user's feet + (" + dx + ", " + dy
                        + ", " + dz + ") during "
                        + action.ability.getAbilityId().nameInMoveset()
                        + " with " + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ").");
    }

    private static void turnIdleUser(
            ServerPlayer user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertIdleAt(GameTestHelper helper, ServerPlayer user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D
                        && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put an idle Cream at its user's feet + (" + dx + ", "
                        + dy + ", " + dz + ") with " + when + ", got " + actual
                        + " (offset " + stand.offsetFromUser.getRelativeOffset()
                        + " " + stand.offsetFromUser.getRotations() + ").");
    }

    private static void runClickRoute(
            GameTestHelper helper,
            String activeName,
            String baseName,
            boolean finisher,
            short key) {
        TestContext context = prepareCream(helper, activeName);
        placeTarget(context, false);
        if (finisher) {
            context.stand().setFinisherMeter(1.0F);
        }
        Ability active = requireAbility(helper, context, activeName);
        Ability base = requireAbility(helper, context, baseName);
        EntityActionInstance action = press(
                helper,
                context,
                active,
                base,
                InputMethod.CLICK,
                key);
        AbilityInput.keyRelease(key, context.owner());
        RouteProbe probe = new RouteProbe(
                activeName,
                context.target().getUUID(),
                context.target().getHealth());

        helper.succeedWhen(() -> {
            probe.observe(action, context.target());
            ActionTarget punched = action.punchedTarget;
            Entity resolved = punched != null
                    ? punched.getMainEntity() : null;
            if (resolved != null) {
                probe.resolvedTargetUuid = resolved.getUUID();
                probe.resolvedTargetType = resolved.getType()
                        .builtInRegistryHolder().key().location().toString();
            }
            probe.attackAttempted = action.punchedTarget != null;
            probe.attackReturned = action.lastDamageDealtToLiving > 0.0F;
            finishOrFail(helper, context, action, probe, 100);
        });
    }

    private static TestContext prepareCream(
            GameTestHelper helper, String route) {
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "cream-" + route.replace('_', '-'));
        Vec3 ownerPos = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 2, 1)));
        ChunkHold chunks = new ChunkHold(helper);
        chunks.force(ownerPos);
        chunks.force(ownerPos.add(0.0D, 0.0D, 1.5D));
        chunks.force(ownerPos.add(0.0D, 0.0D, 3.0D));
        owner.setPos(ownerPos.x, ownerPos.y, ownerPos.z);
        owner.setXRot(0.0F);
        owner.setYRot(0.0F);
        owner.setYHeadRot(0.0F);

        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null,
                "Mock player has no StandPower attachment.");
        if (power == null) {
            throw new IllegalStateException("Missing StandPower");
        }
        power.setStand(CreamAddon.CREAM.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(CreamAddon.CREAM.get().summon(owner, power),
                "Cream could not be summoned.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null && stand.isAddedToLevel(),
                "Cream Stand entity is missing.");
        if (stand == null) {
            throw new IllegalStateException("Missing Cream Stand");
        }
        stand.summonLockTicks = 0;
        stand.setCanFollowUser(false);
        stand.setNoGravity(true);
        stand.setDeltaMovement(Vec3.ZERO);
        Vec3 standPos = ownerPos.add(0.0D, 0.0D, 1.5D);
        stand.moveTo(
                standPos.x, standPos.y, standPos.z,
                owner.getYRot(), 0.0F);

        Cow target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(target != null,
                "Cream target could not be created.");
        if (target == null) {
            throw new IllegalStateException("Missing Cream target");
        }
        var maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth != null,
                "Cream target has no max-health attribute.");
        if (maxHealth == null) {
            throw new IllegalStateException("Missing max-health attribute");
        }
        maxHealth.setBaseValue(2048.0D);
        target.setHealth((float) maxHealth.getValue());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setSilent(true);
        target.setPersistenceRequired();
        target.setPos(standPos.add(0.0D, 0.0D, 1.5D));
        helper.assertTrue(helper.getLevel().addFreshEntity(target),
                "Cream target could not enter the server level.");

        TestContext context = new TestContext(owner, power, stand, target, chunks);
        synchronizeAim(context);
        return context;
    }

    private static void placeTarget(
            TestContext context, boolean chomp) {
        Vec3 standPos = context.stand().position();
        // 1.16 CreamChomp.checkTarget: within 4 blocks of the user, who
        // stands 1.5 behind the Stand
        Vec3 targetPos = chomp
                ? standPos.add(0.0D, 0.0D, 2.0D)
                : standPos.add(0.0D, 0.0D, 1.75D);
        context.chunks().force(targetPos);
        context.target().setPos(targetPos);
        context.target().setDeltaMovement(Vec3.ZERO);
        synchronizeAim(context);
    }

    private static void synchronizeAim(TestContext context) {
        synchronizeAimAt(context, context.target());
    }

    private static void synchronizeAimAt(TestContext context, Entity aimed) {
        ActionTarget target = new ActionTarget(aimed);
        LivingComponentAction.getComponent(context.owner())
                .entityAim.setTarget(target);
        LivingComponentAction.getComponent(context.stand())
                .entityAim.setTarget(target);
        Vec3 center = aimed.getBoundingBox().getCenter();
        context.owner().lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument
                        .Anchor.EYES,
                center);
        context.stand().lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument
                        .Anchor.EYES,
                center);
    }

    private static Ability requireAbility(
            GameTestHelper helper,
            TestContext context,
            String name) {
        Ability ability = context.power().getAbility(name);
        helper.assertTrue(
                ability != null
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "Production preflight rejected Cream route " + name + '.');
        return ability;
    }

    private static EntityActionInstance press(
            GameTestHelper helper,
            TestContext context,
            Ability active,
            Ability base,
            InputMethod method,
            short key) {
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        BufferingState buffering = BufferingState.clickCanBuffer();
        Object held;
        try {
            held = AbilityInput.keyPress(
                    key,
                    active,
                    context.owner(),
                    input,
                    method,
                    0.0F,
                    buffering,
                    base.abilityId);
        }
        finally {
            input.release();
        }
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        helper.assertTrue(
                (held != null || buffering.isActionSuccess)
                        && action != null
                        && action.ability == active,
                "Production input did not install "
                        + active.getAbilityId().nameInMoveset() + '.');
        if (action == null) {
            throw new IllegalStateException("Missing Cream action");
        }
        return action;
    }

    private static void finishOrFail(
            GameTestHelper helper,
            TestContext context,
            EntityActionInstance action,
            RouteProbe probe,
            int timeout) {
        synchronizeAim(context);
        if (context.target().getHealth() < probe.initialHealth) {
            helper.assertTrue(probe.performReached,
                    probe.message(action, context.target()));
            helper.assertTrue(
                    context.targetUuid().equals(
                            probe.resolvedTargetUuid),
                    probe.message(action, context.target()));
            helper.assertTrue(
                    "minecraft:cow".equals(probe.resolvedTargetType),
                    probe.message(action, context.target()));
            cleanup(context, "Cream melee GameTest complete");
            helper.succeed();
            return;
        }
        if (++probe.observationTicks >= timeout) {
            cleanup(context, "Cream melee GameTest failed");
            helper.assertTrue(false,
                    probe.message(action, context.target()));
        }
    }

    private static void setPrivateField(Object target, Class<?> owner,
            String fieldName, Object value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not set " + fieldName, exception);
        }
    }

    private static Object privateField(
            Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not inspect Cream action field " + fieldName,
                    exception);
        }
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String playerName) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), playerName);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void cleanup(TestContext context, String message) {
        context.target().discard();
        context.stand().discard();
        context.owner().connection.disconnect(Component.literal(message));
    }

    /**
     * Forces every chunk within 3 blocks of a position: the Stand and the
     * target stand beside the owner, and only a forced chunk ticks its
     * entities here. A newly forced chunk ticks entities only after the ring
     * around it has generated, which takes real time under load, so force()
     * returns once all of them tick. The game test server does not pace its
     * ticks, hence the budget is real time and the server thread runs the
     * chunk tasks meanwhile. The hold never unforces: GameTestRunner unforces
     * every forced chunk when the batch ends, and for a batch's last test it
     * does so, and forces the next batch's structures, before this listener
     * runs. Forced chunks are a set, so unforcing here could take a chunk from
     * a test that has not started, and a test whose chunks do not tick
     * entities never starts and never times out.
     */
    private static final class ChunkHold implements GameTestListener {
        private static final double MARGIN = 3.0D;
        private static final long WAIT_NANOS = 30_000_000_000L;
        private static final long PAUSE_NANOS = 1_000_000L;
        private final ServerLevel level;
        private final Set<ChunkPos> chunks = new LinkedHashSet<>();

        private ChunkHold(GameTestHelper helper) {
            level = helper.getLevel();
            helper.testInfo.addListener(this);
        }

        private void force(Vec3 position) {
            int minX = SectionPos.blockToSectionCoord(
                    Mth.floor(position.x - MARGIN));
            int maxX = SectionPos.blockToSectionCoord(
                    Mth.floor(position.x + MARGIN));
            int minZ = SectionPos.blockToSectionCoord(
                    Mth.floor(position.z - MARGIN));
            int maxZ = SectionPos.blockToSectionCoord(
                    Mth.floor(position.z + MARGIN));
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (chunks.add(new ChunkPos(x, z))) {
                        level.setChunkForced(x, z, true);
                        level.getChunk(x, z);
                    }
                }
            }
            awaitEntityTicking();
        }

        private void awaitEntityTicking() {
            ServerChunkCache cache = level.getChunkSource();
            long start = System.nanoTime();
            List<ChunkPos> idle;
            while (!(idle = notTickingEntities()).isEmpty()) {
                long waited = System.nanoTime() - start;
                if (waited > WAIT_NANOS) {
                    throw new GameTestAssertException(
                            "The forced chunks " + idle
                                    + " do not tick entities after "
                                    + waited / 1_000_000L + " ms");
                }
                if (!cache.pollTask()) {
                    LockSupport.parkNanos(PAUSE_NANOS);
                }
            }
        }

        private List<ChunkPos> notTickingEntities() {
            List<ChunkPos> idle = new ArrayList<>();
            for (ChunkPos chunk : chunks) {
                if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                    idle.add(chunk);
                }
            }
            return idle;
        }

        private void release() {
            chunks.clear();
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testAddedForRerun(
                GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            release();
        }
    }

    private record TestContext(
            ServerPlayer owner,
            StandPower power,
            StandEntity stand,
            Cow target,
            ChunkHold chunks) {
        private UUID targetUuid() {
            return target.getUUID();
        }
    }

    private static final class RouteProbe {
        private final String route;
        private final UUID expectedTargetUuid;
        private final float initialHealth;
        private boolean performReached;
        private boolean attackAttempted;
        private boolean attackReturned;
        private boolean released;
        private int observationTicks;
        private ActionPhase lastPhase;
        private float maxPhaseTick = -1.0F;
        private UUID resolvedTargetUuid;
        private String resolvedTargetType = "none";

        private RouteProbe(
                String route,
                UUID expectedTargetUuid,
                float initialHealth) {
            this.route = route;
            this.expectedTargetUuid = expectedTargetUuid;
            this.initialHealth = initialHealth;
        }

        private void observe(
                EntityActionInstance action,
                LivingEntity target) {
            ActionPhase phase = action.getPhase();
            if (phase != null) {
                lastPhase = phase;
                maxPhaseTick = Math.max(
                        maxPhaseTick, action.getPhaseTick());
            }
            performReached |= phase == ActionPhase.PERFORM
                    || action.punchedTarget != null
                    || target.getHealth() < initialHealth;
        }

        private String message(
                EntityActionInstance action,
                LivingEntity target) {
            return "Cream route " + route
                    + " failed: perform=" + performReached
                    + ", lastPhase=" + lastPhase
                    + ", phaseNow=" + action.getPhase()
                    + ", maxPhaseTick=" + maxPhaseTick
                    + ", expectedTarget=" + expectedTargetUuid
                    + ", resolvedTarget=" + resolvedTargetUuid
                    + ", resolvedType=" + resolvedTargetType
                    + ", attackAttempted=" + attackAttempted
                    + ", attackReturned=" + attackReturned
                    + ", lastDamage="
                    + action.lastDamageDealtToLiving
                    + ", health=" + initialHealth + "->"
                    + target.getHealth();
        }
    }
}
