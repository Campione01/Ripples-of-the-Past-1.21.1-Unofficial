package rotp.addons.madeinheaven.gametest;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.LightSpeedDashAbility;
import rotp.addons.madeinheaven.ability.TimeAuraAbility;
import rotp.addons.madeinheaven.ability.TimeSystemAbility;
import rotp.addons.madeinheaven.ability.UniverseResetAbility;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import rotp.addons.madeinheaven.state.TimeAccelerationState;
import rotp.core.customobjects.AfterimageEntity;
import rotp.core.JojoModConfig;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
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
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 mih_dash (InitStands 64, LightSpeedDash): a hold of at most 5 ticks that asks for 50 stamina, takes 150 on
 * the Stand's first perform tick, summons only the off arm, and leaves a 5-tick afterimage of the user that deals
 * 1 generic damage to every other living entity within 2 blocks of it.
 *
 * <p>The acceleration belongs to a player the server's player list knows, so its tick clears a test player's
 * acceleration at the end of the tick. Each case therefore presses and ticks the Stand's action component within
 * one callback; only the afterimage, which no longer needs the acceleration, runs on real server ticks.
 */
@GameTestHolder(LightSpeedDashGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class LightSpeedDashGameTests {
    public static final String NAMESPACE = "rotp_mih_dash";

    private static final short DASH_KEY = 41;
    private static final short PUNCH_KEY = 42;
    private static final short AURA_KEY = 43;
    private static final short SYSTEM_KEY = 44;
    private static final short RESET_KEY = 45;
    private static WarmDaylightSuite warmDaylight;
    private static final String NO_ACCELERATION =
            "jojo.message.action_condition.cant_use_without_acceleration";
    private static final String STUN = "jojo.message.action_condition.stun";
    private static final String LEGS = "jojo.message.action_condition.no_stand_part.legs";
    private static final String MAIN_BODY = "jojo.message.action_condition.no_stand_part.main_body";
    private static final String ALREADY_MANIPULATED =
            "jojo.message.action_condition.already_time_manipulation";

    private LightSpeedDashGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashLowStamina")
    public static void dashStartsFrom50StaminaAndDrainsTheBar(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-low", true);
        Ability dash = fixture.dash();
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> guarded(() -> cleanup(fixture), () -> {
            accelerate(fixture);
            power.setStamina(49.0F);
            ConditionCheck below = dash.checkConditions(power);
            helper.assertTrue(!below.isPositive() && key(below) == null,
                    "1.16 LightSpeedDash refuses below 50 stamina with a plain NEGATIVE, got "
                            + describe(below));
            int messages = fixture.listener().overlayMessages().size();
            helper.assertTrue(!tryDash(fixture, InputMethod.HOLD)
                            && LivingComponentAction.getCurEntityAction(fixture.stand()) == null
                            && power.getStamina() == 49.0F
                            && dashAfterimages(fixture).isEmpty(),
                    "A dash refused at 49 stamina still started, spent stamina or left an afterimage");
            helper.assertTrue(fixture.listener().overlayMessages().size() == messages,
                    "1.16 refused a dash below 50 stamina without a message, but the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
        }));
        // Two ticks later: the server caches a power's usable abilities for the tick.
        helper.runAfterDelay(4, () -> {
            try {
                accelerate(fixture);
                power.setStamina(100.0F);
                ConditionCheck check = dash.checkConditions(power);
                helper.assertTrue(check.isPositive(),
                        "1.16 LightSpeedDash.checkConditions refuses the dash only below 50 stamina, but at 100"
                                + " it was refused: " + describe(check));
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD),
                        "The server input gate refused a dash at 100 stamina");
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(action != null && action.ability == dash
                                && action.getPhase() == ActionPhase.PERFORM,
                        "The dash at 100 stamina did not start its perform phase");
                helper.assertTrue(power.getStamina() == 100.0F,
                        "1.16 StandEntityTask takes the cost on the first perform tick, not on the press: "
                                + power.getStamina());

                tickStand(fixture);
                helper.assertTrue(power.getStamina() == 0.0F,
                        "1.16 StandPower.consumeStamina: a user who cannot pay the 150 is drained to 0, but has "
                                + power.getStamina());
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "An underfunded dash must still take the 50 + 50 cooldown, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashHeldRecheck")
    public static void dashEndsOnTheTickThatLeavesLessThan50Stamina(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-199", true);
        helper.runAfterDelay(2, () -> {
            try {
                accelerate(fixture);
                StandPower power = fixture.power();
                power.setStamina(199.0F);
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash at 199 stamina did not start");
                int messages = fixture.listener().overlayMessages().size();

                tickStand(fixture);
                helper.assertTrue(power.getStamina() == 49.0F, "The first perform tick must take 150 stamina once, left "
                        + power.getStamina());
                // 1.16 PowerBaseImpl.tickHeldAction asked checkConditions again after the Stand's tick.
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                        "1.16 re-asked the 50 stamina condition on every held tick, so a dash left with 49 ends on"
                                + " its first tick; it kept running");
                helper.assertTrue(!fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)
                                && !fixture.player().hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !fixture.player().hasEffect(MobEffects.DOLPHINS_GRACE),
                        "A dash that ended on its first tick left its effects on the user");
                helper.assertTrue(fixture.listener().overlayMessages().size() == messages,
                        "1.16 ended that hold with a silent NEGATIVE, but the user was sent "
                                + fixture.listener().overlayMessages());
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "The cut-short dash must take the 50 + 50 cooldown, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
                helper.assertTrue(dashAfterimages(fixture).size() == 1,
                        "The afterimage must outlive the hold");
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashAccelerationLost")
    public static void losingTheAccelerationEndsTheHeldDashWithItsMessage(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-lost", true);
        helper.runAfterDelay(2, () -> {
            try {
                accelerate(fixture);
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash did not start");
                tickStand(fixture);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) != null
                                && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                        "Setup: the dash was not running after its first tick");
                int messages = fixture.listener().overlayMessages().size();

                // The acceleration ends between two ticks, as the server's own tick ends it.
                MadeInHeavenAddon.accelerationState(player.level()).clear();
                tickStand(fixture);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                        "1.16 re-asked checkConditions on every held tick, so a dash whose acceleration ended"
                                + " stops; it kept running");
                List<String> sent = fixture.listener().overlayMessages();
                helper.assertTrue(sent.size() == messages + 1 && NO_ACCELERATION.equals(sent.getLast()),
                        "1.16 ended that hold with the acceleration message, but the user was sent "
                                + sent.subList(messages, sent.size()));
                helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !player.hasEffect(MobEffects.DOLPHINS_GRACE)
                                && power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "A dash that lost its acceleration must drop its effects and take the cooldown, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashFullHold")
    public static void fullDashLastsFiveHeldTicksAndCostsOnce(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-full", true);
        helper.runAfterDelay(2, () -> {
            try {
                accelerate(fixture);
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                power.setStamina(200.0F);
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash at 200 stamina did not start");
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 0,
                        "1.16 stopHeldAction set a hold-only action's cooldown when the hold stopped, but it was"
                                + " already " + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)
                                + " on the press");
                int multiplier = TimeAccelerationService.calculatedMultiplier(TimeAccelerationService.BUFF_PHASE);
                for (int tick = 1; tick <= 4; tick++) {
                    tickStand(fixture);
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(fixture.stand());
                    helper.assertTrue(action != null && action.getPhase() == ActionPhase.PERFORM,
                            "A held dash with 50 stamina left ended after " + tick + " tick(s); 1.16 held it for 5");
                    helper.assertTrue(power.getStamina() == 50.0F,
                            "The 150 stamina are taken once, on the first perform tick; after tick " + tick + ": "
                                    + power.getStamina());
                    assertEffect(helper, player.getEffect(MobEffects.DAMAGE_RESISTANCE), 14, "Resistance", tick);
                    assertEffect(helper, player.getEffect(MobEffects.MOVEMENT_SPEED), multiplier * 10, "Speed", tick);
                    assertEffect(helper, player.getEffect(MobEffects.DOLPHINS_GRACE), 14, "Dolphin's Grace", tick);
                }
                helper.assertTrue(dashAfterimages(fixture).size() == 1,
                        "1.16 spawns one afterimage of the user per dash, found " + dashAfterimages(fixture).size());
                helper.assertTrue(!afterimagesOf(fixture, fixture.stand()).isEmpty(),
                        "The port's Stand afterimage (an addition kept for the owner to decide) is gone");

                tickStand(fixture);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                        "1.16 holdType(5): the hold ends after 5 ticks, but the dash is still running");
                helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !player.hasEffect(MobEffects.DOLPHINS_GRACE),
                        "1.16 onTaskStopped removes the three effects when the dash ends");
                helper.assertTrue(power.getStamina() == 50.0F, "The dash cost more than 150 stamina: "
                        + power.getStamina());
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "1.16 cooldown(50, 50) starts when the hold ends, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashOffArm")
    public static void dashAutoSummonsOnlyTheOffArm(GameTestHelper helper) {
        Fixture retracted = user(helper, "mih-dash-arm-a", false);
        Fixture armsOut = user(helper, "mih-dash-arm-b", true);
        helper.runAfterDelay(2, () -> {
            try {
                accelerate(retracted);
                retracted.power().setStamina(retracted.power().getMaxStamina());
                helper.assertTrue(tryDash(retracted, InputMethod.HOLD),
                        "Setup: the dash with the Stand retracted did not start");
                StandEntity summoned = retracted.power().getSummonedStandEntity();
                helper.assertTrue(summoned != null && summoned.isAddedToLevel(),
                        "The dash did not bring any part of the Stand out");
                helper.assertTrue(summoned.isArmsOnlyMode(),
                        "1.16 standAutoSummonMode(OFF_ARM): a dash with the Stand retracted summons only the off"
                                + " arm, but the whole Stand came out");
                helper.assertTrue(summoned.showArm(InteractionHand.OFF_HAND)
                                && !summoned.showArm(InteractionHand.MAIN_HAND),
                        "The auto-summoned arms are not the off arm alone: main="
                                + summoned.showArm(InteractionHand.MAIN_HAND) + ", off="
                                + summoned.showArm(InteractionHand.OFF_HAND));
                // An arms-only Stand is not left idle: the retraction replaces the finished dash.
                EntityActionInstance armDash = LivingComponentAction.getCurEntityAction(summoned);
                for (int tick = 0; tick < 5; tick++) {
                    tickStand(retracted);
                }
                helper.assertTrue(armDash != null && armDash.isOver()
                                && LivingComponentAction.getCurEntityAction(summoned) != armDash,
                        "The off arm's dash did not end after its 5 held ticks");
                helper.assertTrue(retracted.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100
                                && !retracted.player().hasEffect(MobEffects.MOVEMENT_SPEED),
                        "A dash that the off arm's retraction replaces must still take its cooldown and drop its"
                                + " effects; cooldown "
                                + retracted.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
                AbilityInput.keyRelease(DASH_KEY, retracted.player());

                accelerate(armsOut);
                StandEntity stand = armsOut.stand();
                stand.setArmsOnlyMode(true, false);
                armsOut.power().setStamina(armsOut.power().getMaxStamina());
                helper.assertTrue(tryDash(armsOut, InputMethod.HOLD),
                        "Setup: the dash with the main arm out did not start");
                helper.assertTrue(stand.isArmsOnlyMode(),
                        "1.16 OFF_ARM adds the off arm to an arms-only Stand; the dash made it a full summon");
                helper.assertTrue(stand.showArm(InteractionHand.OFF_HAND)
                                && stand.showArm(InteractionHand.MAIN_HAND),
                        "The dash did not add the off arm to the main arm that was out");
            } finally {
                cleanup(retracted);
                cleanup(armsOut);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "mihDashAfterimageBox")
    public static void afterimageHurtsAlliesInsideTheTwoBlockCube(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-box", true);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fixture.player();
        Vec3 feet = player.position();
        // The cube is 2 blocks around the afterimage's centre; the cow at 2.6 only touches the old, larger box.
        Mob enemy = target(level, EntityType.COW, feet.add(1.2D, 0.0D, 0.0D));
        Mob ally = target(level, EntityType.COW, feet.add(-1.2D, 0.0D, 0.0D));
        Mob outside = target(level, EntityType.COW, feet.add(0.0D, 0.0D, 2.6D));
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("mih_dash_allies");
        float userHealth = player.getHealth();
        Runnable end = () -> {
            scoreboard.removePlayerTeam(team);
            cleanup(fixture, enemy, ally, outside);
        };
        helper.runAfterDelay(2, () -> guarded(end, () -> {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
            helper.assertTrue(ally.isAlliedTo(player) && !enemy.isAlliedTo(player),
                    "Setup: the team did not make the second cow the user's ally");
            helper.assertTrue(outside.getBoundingBox().intersects(player.getBoundingBox().inflate(2.0D))
                            && outside.getBoundingBox().minZ > feet.z + 2.0D,
                    "Setup: the far cow must touch only the user's box inflated by 2");
            accelerate(fixture);
            fixture.power().setStamina(fixture.power().getMaxStamina());
            helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash did not start");
            // The afterimage is on its own from here: the hold ends before the Stand's first tick.
            AbilityInput.keyRelease(DASH_KEY, player);
        }));
        helper.runAfterDelay(5, () -> guarded(end, () -> helper.assertTrue(
                dashAfterimages(fixture).size() == 1,
                "1.16 setLifeSpan(5): three ticks after the press the user's afterimage must still be there,"
                        + " found " + dashAfterimages(fixture).size())));
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(ally.getHealth() == ally.getMaxHealth() - 1.0F,
                        "1.16 hurts every living entity around the afterimage except the user, allies included;"
                                + " the user's teammate lost " + (ally.getMaxHealth() - ally.getHealth()));
                helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth() - 1.0F,
                        "The afterimage must deal 1 damage to a cow 1.2 blocks away, dealt "
                                + (enemy.getMaxHealth() - enemy.getHealth()));
                helper.assertTrue(outside.getHealth() == outside.getMaxHealth(),
                        "1.16 MCUtil.entitiesAround: the box is 2 blocks around the afterimage's centre, but a"
                                + " cow beyond it lost " + (outside.getMaxHealth() - outside.getHealth()));
                helper.assertTrue(player.getHealth() == userHealth,
                        "The afterimage hurt its own user or their Stand: " + player.getHealth());
                helper.assertTrue(dashAfterimages(fixture).isEmpty(),
                        "1.16 setLifeSpan(5): the afterimage is removed on its sixth tick, but ten ticks after"
                                + " the press it is still there");
            } finally {
                end.run();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "mihDashAfterimageType")
    public static void afterimageDamageIsGeneric(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-type", true);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fixture.player();
        Vec3 feet = player.position();
        Mob cow = target(level, EntityType.COW, feet.add(1.2D, 0.0D, 0.0D));
        Mob witch = target(level, EntityType.WITCH, feet.add(-1.2D, 0.0D, 0.0D));
        List<DamageSource> sources = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> observe = event -> {
            if (event.getEntity() == cow) {
                sources.add(event.getSource());
            }
        };
        Runnable end = () -> {
            NeoForge.EVENT_BUS.unregister(observe);
            cleanup(fixture, cow, witch);
        };
        helper.runAfterDelay(2, () -> guarded(end, () -> {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, observe);
            accelerate(fixture);
            fixture.power().setStamina(fixture.power().getMaxStamina());
            helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash did not start");
            AbilityInput.keyRelease(DASH_KEY, player);
        }));
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(!sources.isEmpty(), "The dash dealt no damage to a cow 1.2 blocks away");
                DamageSource source = sources.getFirst();
                helper.assertTrue(source.is(DamageTypes.GENERIC),
                        "1.16 hurts with DamageSource.GENERIC (field_76377_j), but the cow was hit with "
                                + source.getMsgId());
                helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null,
                        "1.16 GENERIC names no attacker, but the hit names " + source.getEntity());
                // A witch takes 15% of magic damage and all of generic damage.
                helper.assertTrue(witch.getHealth() == witch.getMaxHealth() - 1.0F,
                        "A witch must lose the full 1 damage of a generic hit, lost "
                                + (witch.getMaxHealth() - witch.getHealth()));
            } finally {
                end.run();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashRelease")
    public static void releasingTheKeyEndsTheDash(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-hold", true);
        helper.runAfterDelay(2, () -> {
            try {
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                helper.assertTrue(hotbarInput(power) == InputMethod.HOLD,
                        "1.16 mih_dash is holdType(5), so the special hotbar must bind it as a hold, found "
                                + hotbarInput(power));

                accelerate(fixture);
                float full = power.getMaxStamina();
                power.setStamina(full);
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the held dash did not start");
                tickStand(fixture);
                tickStand(fixture);
                EntityActionInstance held = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(held != null && held.getPhase() == ActionPhase.PERFORM
                                && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                        "Setup: the dash was not running after two held ticks");
                AbilityInput.keyRelease(DASH_KEY, player);
                helper.assertTrue(held.isOver(),
                        "1.16 stoppedHolding: letting go of the key ends the dash, but it kept running");
                helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !player.hasEffect(MobEffects.DOLPHINS_GRACE),
                        "Letting go of the key left the dash effects on the user");
                tickStand(fixture);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null
                                && power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "A released dash must be cleared and take the full 50 + 50 cooldown, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
                helper.assertTrue(power.getStamina() == full - 150.0F,
                        "A dash released after two ticks costs 150 stamina, spent " + (full - power.getStamina()));

                // A player who rebinds the dash to a click still holds it: the release ends it.
                // Released before the Stand's first tick, 1.16 took the cooldown but no stamina.
                power.setAbilityCooldown(MadeInHeavenAddon.DASH_NAME, 0);
                power.setStamina(full);
                helper.assertTrue(tryDash(fixture, InputMethod.CLICK), "Setup: the clicked dash did not start");
                EntityActionInstance clicked = LivingComponentAction.getCurEntityAction(fixture.stand());
                AbilityInput.keyRelease(DASH_KEY, player);
                helper.assertTrue(clicked != null && clicked.isOver(),
                        "A dash bound to a click must also end when its key is released");
                tickStand(fixture);
                helper.assertTrue(power.getStamina() == full
                                && power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "1.16: a hold released before the Stand's first tick takes the cooldown but no stamina;"
                                + " stamina " + power.getStamina() + ", cooldown "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashCreativeCooldown")
    public static void creativeDashKeepsTheTechnicalCooldown(GameTestHelper helper) {
        Fixture creative = user(helper, "mih-dash-creative", true);
        Fixture setting = user(helper, "mih-dash-no-cooldown", true);
        helper.runAfterDelay(2, () -> {
            try {
                for (Fixture fixture : List.of(creative, setting)) {
                    boolean noCooldown = fixture == setting;
                    ServerPlayer player = fixture.player();
                    player.setGameMode(GameType.CREATIVE);
                    player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = noCooldown;
                    accelerate(fixture);
                    helper.assertTrue(fixture.power().isUserCreative()
                                    && PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(fixture.power()) == noCooldown,
                            "Setup: a Creative user, with the no-cooldown setting " + noCooldown);
                    helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the Creative dash did not start");
                    tickStand(fixture);
                    AbilityInput.keyRelease(DASH_KEY, player);
                    tickStand(fixture);
                    int cooldown = fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME);
                    helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null
                                    && cooldown == (noCooldown ? 0 : LightSpeedDashAbility.COOLDOWN_TICKS),
                            noCooldown
                                    ? "The port's no-cooldown setting must still remove the whole dash cooldown, got " + cooldown
                                    : "1.16 Action.getCooldownAdditional: Creative drops only the additional 50 of"
                                            + " cooldown(50, 50), so a Creative dash takes 50, got " + cooldown);
                    cleanup(fixture);
                }
            } finally {
                cleanup(creative);
                cleanup(setting);
            }
            helper.succeed();
        });
    }

    private record CooldownRow(String ability, int technical, int additional) {}

    // 1.16 041 InitStands.java:64 mih_dash cooldown(50, 50) (technical 50, additional 50, StandAction's multiplier 0);
    // :62 chop cooldown(25) and :68 time_aura cooldown(50) are plain, so they have no technical part
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashCooldownSplit")
    public static void theDashKeepsTheDonorsTechnicalAndAdditionalCooldown(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-cooldown-split", false);
        List<String> wrong = new ArrayList<>();
        try {
            List<CooldownRow> rows = List.of(
                    new CooldownRow(MadeInHeavenAddon.DASH_NAME, 50, 50),
                    new CooldownRow(MadeInHeavenAddon.CHOP_NAME, 0, 25),
                    new CooldownRow(MadeInHeavenAddon.TIME_AURA_NAME, 0, 50));
            helper.assertTrue(!fixture.power().isUserCreative(), "Setup: the dash user is in Creative");
            for (CooldownRow row : rows) {
                Ability ability = fixture.power().getAbility(row.ability());
                helper.assertTrue(ability != null, "Setup: Made in Heaven has no " + row.ability());
                int technical = ability.getCooldownTechnical(fixture.power());
                int full = ability.getCooldown(fixture.power(), -1);
                if (technical != row.technical() || full != row.technical() + row.additional()) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + " of " + (row.technical() + row.additional()));
                }
            }
            fixture.player().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
            for (CooldownRow row : rows) {
                int inResolve = fixture.power().getAbility(row.ability()).getCooldown(fixture.power(), -1);
                if (inResolve != row.technical()) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + row.technical()
                            + " (multiplier 0 drops the additional part)");
                }
            }
            // Action.getCooldownAdditional :373-375 was 0 for a Creative user and getCooldownTechnical was not, with
            // Resolve or without; the port's no-stand-cooldown setting drops the whole cooldown, Creative or not
            fixture.player().setGameMode(GameType.CREATIVE);
            helper.assertTrue(fixture.power().isUserCreative(), "Setup: the dash user is not in Creative");
            collectCooldownMisses(fixture, rows, "as a Creative user in Resolve", false, wrong);
            fixture.player().removeEffect(ModStatusEffects.RESOLVE);
            collectCooldownMisses(fixture, rows, "as a Creative user", false, wrong);
            fixture.player().getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
            collectCooldownMisses(fixture, rows, "as a Creative user with the no-cooldown setting", true, wrong);
            fixture.player().setGameMode(GameType.SURVIVAL);
            collectCooldownMisses(fixture, rows, "with the no-cooldown setting", true, wrong);
        } finally {
            cleanup(fixture);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Made in Heaven cooldown(technical, additional): " + String.join("; ", wrong));
        helper.succeed();
    }

    // A Creative user keeps only the technical part of the donor's split (none at all with the no-cooldown setting).
    private static void collectCooldownMisses(
            Fixture fixture, List<CooldownRow> rows, String when, boolean none, List<String> wrong) {
        for (CooldownRow row : rows) {
            int expected = none ? 0 : row.technical();
            int actual = fixture.power().getAbility(row.ability()).getCooldown(fixture.power(), -1);
            if (actual != expected) {
                wrong.add(row.ability() + " takes " + actual + " ticks " + when + ", the donor's " + expected);
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashCutByPunch")
    public static void anotherStandActionCutsTheDashOffOnThePress(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-cut", true);
        helper.runAfterDelay(2, () -> {
            try {
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                accelerate(fixture);
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash did not start");
                tickStand(fixture);
                EntityActionInstance dash = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(dash != null && dash.getPhase() == ActionPhase.PERFORM
                                && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                        "Setup: the dash was not running after its first held tick");
                helper.assertTrue(dash.isHoldTypeStandAction(),
                        "1.16 mih_dash was a holdType(5) Stand action, ended by another action of the power that went"
                                + " off: the dash must be a hold-type Stand action");

                // The server's own order for the punch: the input gate, then the press that may buffer.
                Ability punch = power.getAbility("punch");
                AbilityConditionCheck checked = power.updateAvailableMoves().getContextVariationContainer(punch);
                helper.assertTrue(checked != null && AbilityInput.withConditionCheck(checked, player, InputMethod.CLICK),
                        "Setup: the punch input was refused mid-dash");
                BufferingState state = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(PUNCH_KEY, checked.ability, player, null, InputMethod.CLICK, 0.0F, state,
                        punch.getAbilityId());
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && current != null && current != dash
                                && current.ability == checked.ability,
                        "1.16 StandEntityAction.isCancelable: a punch pressed mid-dash replaces the dash on the press,"
                                + " but it was " + (state.shouldBuffer ? "buffered until the dash ends" : "refused"));
                helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && !player.hasEffect(MobEffects.DOLPHINS_GRACE),
                        "1.16 onTaskStopped removes the three dash effects when another action cuts the dash");
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "1.16 stopHeldAction starts the 50 + 50 cooldown when the dash is cut, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));

                AbilityInput.keyRelease(DASH_KEY, player);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == current
                                && power.getAbilityCooldown(MadeInHeavenAddon.DASH_NAME) == 100,
                        "Letting go of the dash key after the cut ended the punch or restarted the dash cooldown");
            } finally {
                AbilityInput.keyRelease(PUNCH_KEY, fixture.player());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihAuraShortBar")
    public static void timeAuraStartsAndRunsOnAShortBar(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-aura-bar", true);
            try {
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                Ability aura = power.getAbility(MadeInHeavenAddon.TIME_AURA_NAME);
                helper.assertTrue(aura != null && !power.isStaminaInfinite(), "Setup: finite stamina and the aura");
                power.setStamina(0.0F);
                ConditionCheck empty = aura.checkConditions(power);
                helper.assertTrue(empty.isPositive(),
                        "1.16 Tpaura has no stamina condition, but the aura was refused on an empty bar: "
                                + describe(empty));
                power.setStamina(100.0F);
                int messages = fixture.listener().overlayMessages().size();
                helper.assertTrue(tryAbility(fixture, aura, AURA_KEY, InputMethod.HOLD),
                        "1.16 started the aura at 100 stamina, under its 150 cost");
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(action instanceof TimeAuraAbility.TimeAuraAction,
                        "The aura press did not install the aura action: " + action);
                for (int tick = 1; tick <= 3; tick++) {
                    tickStand(fixture);
                    helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == action
                                    && !action.isOver() && action.getPhase() == ActionPhase.PERFORM,
                            "1.16 StandEntityTask ignored a failed stamina payment, so the aura goes on; it ended"
                                    + " after " + tick + " tick(s)");
                    helper.assertTrue(power.getStamina() == 0.0F,
                            "1.16 consumeStamina drains a short bar to 0, left " + power.getStamina());
                    MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
                    helper.assertTrue(resistance != null && resistance.getAmplifier() == 2,
                            "The running aura must give Resistance III on tick " + tick + ": " + resistance);
                }
                helper.assertTrue(fixture.listener().overlayMessages().size() == messages,
                        "1.16 showed no stamina message for the aura, but the user was sent "
                                + fixture.listener().overlayMessages().subList(
                                        messages, fixture.listener().overlayMessages().size()));
            } finally {
                AbilityInput.keyRelease(AURA_KEY, fixture.player());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihAuraCutByPunch")
    public static void anotherStandActionCutsTheAuraOffOnThePress(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-aura-cut", true);
            try {
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                power.setStamina(power.getMaxStamina());
                Ability aura = power.getAbility(MadeInHeavenAddon.TIME_AURA_NAME);
                helper.assertTrue(tryAbility(fixture, aura, AURA_KEY, InputMethod.HOLD), "Setup: the aura did not start");
                tickStand(fixture);
                EntityActionInstance running = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(running instanceof TimeAuraAbility.TimeAuraAction
                                && running.getPhase() == ActionPhase.PERFORM
                                && power.getAbilityCooldown(MadeInHeavenAddon.TIME_AURA_NAME) == 0,
                        "Setup: the aura was not running without a cooldown after its first tick");
                helper.assertTrue(running.isHoldTypeStandAction(),
                        "1.16 tpaura was a holdType(1000) Stand action, ended by another action of the power that went"
                                + " off: the aura must be a hold-type Stand action");

                Ability punch = power.getAbility("punch");
                AbilityConditionCheck checked = power.updateAvailableMoves().getContextVariationContainer(punch);
                helper.assertTrue(checked != null && AbilityInput.withConditionCheck(checked, player, InputMethod.CLICK),
                        "Setup: the punch input was refused during the aura");
                BufferingState state = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(PUNCH_KEY, checked.ability, player, null, InputMethod.CLICK, 0.0F, state,
                        punch.getAbilityId());
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(fixture.stand());
                helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && current != null && current != running
                                && current.ability == checked.ability,
                        "1.16 StandEntityAction.isCancelable: a punch pressed during the aura replaces it on the"
                                + " press, but it was " + (state.shouldBuffer ? "buffered until the aura ends" : "refused"));
                helper.assertTrue(power.getAbilityCooldown(MadeInHeavenAddon.TIME_AURA_NAME)
                                == TimeAuraAbility.COOLDOWN_TICKS,
                        "1.16 stopHeldAction starts the aura's 50-tick cooldown when another action cuts it, got "
                                + power.getAbilityCooldown(MadeInHeavenAddon.TIME_AURA_NAME));

                AbilityInput.keyRelease(AURA_KEY, player);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == current,
                        "Letting go of the aura key after the cut ended the punch");
            } finally {
                AbilityInput.keyRelease(AURA_KEY, fixture.player());
                AbilityInput.keyRelease(PUNCH_KEY, fixture.player());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    /*
     * 1.16's client sent no press of a held action (getHoldDurationMax above 0) while the power held one
     * (ActionsOverlayGui.onActionClick :2121-2123): mih_dash (holdType(5)), tpaura (holdType(1000)) and
     * mih_time_system (holdToFire(25, false), start and clear alike) pressed during the held guard did nothing and the
     * guard went on; the input buffer keeps such a press until the hold ends (owner decision N2). 1.16 queued no held
     * action behind the Stand's task (StandEntityAction.canBeQueued: getHoldDurationMax == 0), so in the heavy punch's
     * windup they are drawn by their own checks, as the port draws its other held actions, while the punch is queued.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihHeldPressWaits")
    public static void heldAbilitiesPressedDuringTheHeldGuardWaitAsIn116(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-held-waits", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                ServerPlayer player = fixture.player();
                StandEntity stand = fixture.stand();
                LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(player.level());
                Ability guard = power.getAbility("guard");
                String[] held = {MadeInHeavenAddon.DASH_NAME, MadeInHeavenAddon.TIME_AURA_NAME,
                        MadeInHeavenAddon.TIME_SYSTEM_NAME, MadeInHeavenAddon.TIME_SYSTEM_CLEAR_NAME};
                for (String name : held) {
                    boolean accelerated = !name.equals(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                    Ability pressed = power.getAbility(name);
                    state.clear();
                    if (accelerated) {
                        accelerate(fixture);
                    }
                    power.setStamina(power.getMaxStamina());
                    BufferingState guardPress = press(fixture, guard, PUNCH_KEY, InputMethod.HOLD);
                    EntityActionInstance hold = standAction.getAction();
                    helper.assertTrue(guardPress.isActionSuccess && hold != null && hold.ability == guard
                                    && AbilityInput.isHeldByKey(player, hold)
                                    && hold.canBeCancelledInto((EntityActionType) pressed),
                            "Setup: the guard is not held or would not let " + name + " replace it: started "
                                    + guardPress.isActionSuccess + ", the Stand's action " + hold);
                    ActionPhase phase = hold.getPhase();
                    BufferingState press = press(fixture, pressed, SYSTEM_KEY, InputMethod.HOLD);
                    helper.assertTrue(press.shouldBuffer && !press.isActionSuccess && standAction.getAction() == hold
                                    && !hold.isOver() && hold.getPhase() == phase
                                    && AbilityInput.isHeldByKey(player, hold),
                            "1.16's client sent no " + name + " (a held action) pressed while the guard was held, which"
                                    + " the input buffer keeps: buffered " + press.shouldBuffer + ", started "
                                    + press.isActionSuccess + ", the Stand's action " + standAction.getAction()
                                    + ", the guard in " + hold.getPhase());
                    AbilityInput.keyRelease(SYSTEM_KEY, player);
                    AbilityInput.keyRelease(PUNCH_KEY, player);
                    standAction.setAction(null, player, SyncType.NO_SYNC);
                    power.setAbilityCooldown("guard", 0, 0);

                    stand.setFinisherMeter(0.0F);
                    EntityActionInstance heavy = ((EntityActionAbility) power.getAbility("heavy_punch"))
                            .initActionOnAbilityUse(helper.getLevel(), player, stand, null);
                    standAction.setAction(heavy, player, SyncType.NO_SYNC);
                    helper.assertTrue(heavy != null && standAction.getAction() == heavy
                                    && heavy.getPhase() == ActionPhase.WINDUP
                                    && resolve(power, "punch").drawnCondition(player) == ConditionCheck.NEGATIVE_QUEUED,
                            "Setup: the heavy punch's windup does not hold a punch's press back: " + heavy);
                    AbilityConditionCheck slot = resolve(power, name);
                    helper.assertTrue(slot != null && slot.ability == pressed && slot.conditionCheck.isPositive()
                                    && slot.drawnCondition(player) == slot.conditionCheck,
                            "1.16 queued no held action behind the Stand's task, so " + name + " is drawn by its own"
                                    + " check in the heavy punch's windup: its check "
                                    + (slot != null ? describe(slot.conditionCheck) : "none") + ", drawn "
                                    + (slot == null ? "nothing" : slot.drawnCondition(player)
                                            == ConditionCheck.NEGATIVE_QUEUED ? "queued (0.75)"
                                            : describe(slot.drawnCondition(player))));
                    standAction.setAction(null, player, SyncType.NO_SYNC);
                }
                for (String name : held) {
                    helper.assertTrue(power.getAbility(name) instanceof EntityActionAbility ability
                                    && ability.isHoldAction(),
                            "1.16 " + name + " was a held action (getHoldDurationMax above 0): isHoldAction must say so");
                }
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                AbilityInput.keyRelease(PUNCH_KEY, fixture.player());
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    private static BufferingState press(Fixture fixture, Ability ability, short key, InputMethod method) {
        BufferingState state = BufferingState.clickCanBuffer();
        AbilityConditionCheck checked = resolve(fixture.power(), ability.getAbilityId().nameInMoveset());
        if (checked != null && AbilityInput.withConditionCheck(checked, fixture.player(), method)) {
            AbilityInput.keyPress(key, checked.ability, fixture.player(), null, method, 0.0F, state,
                    ability.getAbilityId());
        }
        return state;
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static AbilityConditionCheck resolve(StandPower power, String name) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemFloor")
    public static void timeSystemAndUniverseResetRefuseSilentlyBelow300(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-floor", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                Ability clear = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_CLEAR_NAME);
                Ability reset = power.getAbility(MadeInHeavenAddon.UNIVERSE_RESET_NAME);
                helper.assertTrue(fixture.player().serverLevel() == overworld && !power.isStaminaInfinite()
                                && power.isAbilityUnlocked(MadeInHeavenAddon.UNIVERSE_RESET_NAME),
                        "Setup: an Overworld user with finite stamina and every Made in Heaven ability");
                for (boolean resolve : new boolean[] {false, true}) {
                    if (resolve) {
                        helper.assertTrue(fixture.player().addEffect(
                                        new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false))
                                        && ResolveModeEffect.getResolveEffectLvl(fixture.player()) >= 0,
                                "Setup: the Resolve effect could not be installed");
                    }
                    String mode = resolve ? " under Resolve" : "";
                    MadeInHeavenAddon.accelerationState(fixture.player().level()).clear();
                    power.setStamina(299.0F);
                    ConditionCheck startShort = start.checkConditions(power);
                    ConditionCheck resetShort = reset.checkConditions(power);
                    helper.assertTrue(!startShort.isPositive() && key(startShort) == null,
                            "1.16 TimeSystem.checkConditions refuses below 300 stamina with a plain NEGATIVE" + mode
                                    + ", got " + describe(startShort));
                    helper.assertTrue(!resetShort.isPositive() && key(resetShort) == null,
                            "1.16 UniverseReset.checkStandConditions refuses below 300 stamina with a plain NEGATIVE"
                                    + mode + ", got " + describe(resetShort));
                    accelerate(fixture);
                    ConditionCheck clearShort = clear.checkConditions(power);
                    helper.assertTrue(!clearShort.isPositive() && key(clearShort) == null,
                            "1.16 used the same TimeSystem condition to clear the acceleration, so clearing below 300"
                                    + " stamina is a plain NEGATIVE" + mode + ", got " + describe(clearShort));

                    power.setStamina(300.0F);
                    ConditionCheck clearFull = clear.checkConditions(power);
                    ConditionCheck resetFull = reset.checkConditions(power);
                    MadeInHeavenAddon.accelerationState(fixture.player().level()).clear();
                    ConditionCheck startFull = start.checkConditions(power);
                    helper.assertTrue(startFull.isPositive() && clearFull.isPositive() && resetFull.isPositive(),
                            "At 300 stamina" + mode + " the start, the clear and the reset must pass: "
                                    + describe(startFull) + ", " + describe(clearFull) + ", " + describe(resetFull));
                }
            } finally {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemHold")
    public static void timeSystemHoldEndsWhenTheBarFallsBelow300(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-hold", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(fixture.player().level());
                state.clear();
                power.setStamina(300.0F);
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the time system hold did not start at 300 stamina");
                EntityActionInstance hold = LivingComponentAction.getCurEntityAction(fixture.stand());
                tickStand(fixture);
                tickStand(fixture);
                helper.assertTrue(hold != null && hold.getPhase() == ActionPhase.WINDUP && !hold.isOver(),
                        "Setup: the time system was not in its 25-tick hold");
                int messages = fixture.listener().overlayMessages().size();
                power.setStamina(299.0F);
                tickStand(fixture);
                helper.assertTrue(hold.isOver(),
                        "1.16 PowerBaseImpl.tickHeldAction asked TimeSystem.checkConditions on every held tick, so"
                                + " the hold ends once the bar is below 300; it went on");
                for (int tick = 0; tick < TimeSystemAbility.WINDUP_TICKS + 5; tick++) {
                    tickStand(fixture);
                }
                helper.assertTrue(!state.active() && LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                        "The stopped hold still started the acceleration");
                helper.assertTrue(fixture.listener().overlayMessages().size() == messages,
                        "1.16 ended that hold with a plain NEGATIVE, but the user was sent "
                                + fixture.listener().overlayMessages().subList(
                                        messages, fixture.listener().overlayMessages().size()));
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    // 1.16 TimeSystem.checkConditions began with TimeUtil.checkConditions (the Overworld, the daylight cycle, no time
    // manipulation but the user's own), and tickHeldAction asked it on every tick of the 25-tick hold.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemHoldTime")
    public static void timeSystemHoldEndsWhenTimeCannotBeManipulated(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture other = user(helper, "mih-system-other", true);
            Fixture rule = user(helper, "mih-system-rule", true);
            Fixture owner = user(helper, "mih-system-owner", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(other.player().level());
                // The user's own acceleration passes the check: a hold that clears it goes on.
                accelerate(owner);
                owner.power().setStamina(owner.power().getMaxStamina());
                Ability clear = owner.power().getAbility(MadeInHeavenAddon.TIME_SYSTEM_CLEAR_NAME);
                helper.assertTrue(tryAbility(owner, clear, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the hold that clears the user's own acceleration did not start");
                EntityActionInstance clearing = LivingComponentAction.getCurEntityAction(owner.stand());
                tickStand(owner);
                tickStand(owner);
                helper.assertTrue(clearing != null && clearing.getPhase() == ActionPhase.WINDUP && !clearing.isOver(),
                        "1.16 TimeUtil.checkConditions let the user who manipulates time hold the time system; the"
                                + " hold that clears the user's own acceleration stopped");
                AbilityInput.keyRelease(SYSTEM_KEY, owner.player());
                tickStand(owner);
                state.clear();
                for (Fixture fixture : List.of(other, rule)) {
                    boolean byRule = fixture == rule;
                    String why = byRule ? "the daylight cycle was turned off" : "another user started the acceleration";
                    StandPower power = fixture.power();
                    power.setStamina(power.getMaxStamina());
                    Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                    helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                            "Setup: the time system hold did not start for the case where " + why);
                    EntityActionInstance hold = LivingComponentAction.getCurEntityAction(fixture.stand());
                    tickStand(fixture);
                    helper.assertTrue(hold != null && hold.getPhase() == ActionPhase.WINDUP && !hold.isOver(),
                            "Setup: the time system was not in its 25-tick hold");
                    int messages = fixture.listener().overlayMessages().size();
                    if (byRule) {
                        overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, overworld.getServer());
                    }
                    else {
                        state.begin(UUID.randomUUID());
                    }
                    tickStand(fixture);
                    helper.assertTrue(hold.isOver(),
                            "1.16 PowerBaseImpl.tickHeldAction asked TimeUtil.checkConditions on every held tick, so"
                                    + " the hold ends once " + why + "; it went on");
                    List<String> sent = fixture.listener().overlayMessages();
                    helper.assertTrue(sent.size() == messages + 1 && ALREADY_MANIPULATED.equals(sent.getLast()),
                            "1.16 ended that hold with the already_time_manipulation message once " + why
                                    + ", but the user was sent " + sent.subList(messages, sent.size()));
                    helper.assertTrue(!state.isOwner(fixture.player().getUUID())
                                    && power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME) == 0,
                            "The stopped hold still started the acceleration or set its cooldown once " + why);
                    AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                    state.clear();
                    overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                }
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, other.player());
                AbilityInput.keyRelease(SYSTEM_KEY, rule.player());
                AbilityInput.keyRelease(SYSTEM_KEY, owner.player());
                MadeInHeavenAddon.accelerationState(overworld).clear();
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(other);
                cleanup(rule);
                cleanup(owner);
            }
            helper.succeed();
        });
    }

    // 1.16 LightSpeedDash.checkConditions replaced the check of the builder's partsRequired(LEGS) by accident, so a
    // Stand without legs could dash. The owner kept the port's rule (2026-10-18, "Made in Heaven dash needs legs"):
    // the dash is refused with the port's message, on the press and on a held tick.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashNoLegsCheck")
    public static void dashStillAsksForTheStandsLegsAsTheOwnerKept(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-legs", true);
        helper.runAfterDelay(2, () -> guarded(() -> cleanup(fixture), () -> {
            StandPower power = fixture.power();
            accelerate(fixture);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(fixture.dash().checkConditions(power).isPositive(),
                    "Setup: the dash of a Stand with its legs was refused: "
                            + describe(fixture.dash().checkConditions(power)));
            helper.assertTrue(power.getStandInstance().map(stand -> stand.removePart(StandPart.LEGS)).orElse(false)
                            && !power.getStandInstance().get().hasPart(StandPart.LEGS),
                    "Setup: the Stand could not lose its legs");
            ConditionCheck check = fixture.dash().checkConditions(power);
            helper.assertTrue(!check.isPositive() && LEGS.equals(key(check)),
                    "The owner kept the dash needing the Stand's legs, but a legless Stand's dash was not refused"
                            + " with the legs message: " + describe(check));
            int messages = fixture.listener().overlayMessages().size();
            helper.assertTrue(!tryDash(fixture, InputMethod.HOLD)
                            && LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                    "The press of a legless Stand's dash was not refused");
            helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                            fixture.listener().overlayMessages().size()).equals(List.of(LEGS)),
                    "The legless dash was refused with the legs message, the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
            cleanup(fixture);
            helper.succeed();
        }));
    }

    // A dash that is running when the Stand loses its legs ends on the next held tick, as the other parts rule does.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashLegsLost")
    public static void dashEndsWhenTheStandLosesItsLegs(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-legs-lost", true);
        helper.runAfterDelay(2, () -> guarded(() -> cleanup(fixture), () -> {
            StandPower power = fixture.power();
            accelerate(fixture);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the dash did not start");
            tickStand(fixture);
            EntityActionInstance running = LivingComponentAction.getCurEntityAction(fixture.stand());
            helper.assertTrue(running != null && running.ability == fixture.dash() && !running.isOver(),
                    "Setup: the dash did not run its first tick");
            helper.assertTrue(power.getStandInstance().map(stand -> stand.removePart(StandPart.LEGS)).orElse(false),
                    "Setup: the Stand could not lose its legs");
            tickStand(fixture);
            helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                    "The dash needs the Stand's legs on every held tick, but it went on without them");
            cleanup(fixture);
            helper.succeed();
        }));
    }

    // 1.16 TimeSystem.checkConditions replaced the check of the builder's partsRequired(MAIN_BODY) by accident. The
    // owner kept the dash needing legs (2026-10-18) and the coordinator applied that to the time system: a Stand
    // without a main body is refused with the port's message on the press.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemNoBodyCheck")
    public static void timeSystemStillAsksForTheStandsMainBody(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-body", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                MadeInHeavenAddon.accelerationState(overworld).clear();
                power.setStamina(power.getMaxStamina());
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                helper.assertTrue(start.checkConditions(power).isPositive(),
                        "Setup: the time system of a Stand with its main body was refused: "
                                + describe(start.checkConditions(power)));
                helper.assertTrue(power.getStandInstance().map(stand -> stand.removePart(StandPart.MAIN_BODY)).orElse(false)
                                && !power.getStandInstance().get().hasPart(StandPart.MAIN_BODY),
                        "Setup: the Stand could not lose its main body");
                ConditionCheck check = start.checkConditions(power);
                helper.assertTrue(!check.isPositive() && MAIN_BODY.equals(key(check)),
                        "The time system needs the Stand's main body, but a Stand without one was not refused with"
                                + " the main body message: " + describe(check));
                int messages = fixture.listener().overlayMessages().size();
                helper.assertTrue(!tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD)
                                && LivingComponentAction.getCurEntityAction(fixture.stand()) == null,
                        "The press of the time system of a Stand without a main body was not refused");
                helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                                fixture.listener().overlayMessages().size()).equals(List.of(MAIN_BODY)),
                        "The refusal was the main body message, the user was sent "
                                + fixture.listener().overlayMessages().subList(
                                        messages, fixture.listener().overlayMessages().size()));
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                MadeInHeavenAddon.accelerationState(overworld).clear();
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    // A time system hold running when the Stand is cut down to its arms (arms-only mode) ends on the next held tick.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "mihTimeSystemArmsOnly")
    public static void timeSystemHoldEndsWhenTheStandLosesItsMainBody(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-arms", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(overworld);
                state.clear();
                power.setStamina(power.getMaxStamina());
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the time system hold did not start");
                tickStand(fixture);
                tickStand(fixture);
                helper.assertTrue(power.getStandInstance().map(stand -> stand.removePart(StandPart.MAIN_BODY)
                                        & stand.removePart(StandPart.LEGS)).orElse(false)
                                && power.getStandInstance().get().hasPart(StandPart.ARMS)
                                && !power.getStandInstance().get().hasPart(StandPart.MAIN_BODY),
                        "Setup: the Stand could not be cut down to its arms");
                StandEntity stand = fixture.stand();
                stand.setArmsOnlyMode();
                for (int tick = 0; tick < TimeSystemAbility.WINDUP_TICKS + 5 && fixture.stand() == stand; tick++) {
                    tickStand(fixture);
                }
                helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == null && !state.active(),
                        "The time system needs the Stand's main body on every held tick, but an arms-only Stand"
                                + " went on and started the acceleration; active " + state.active());
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                MadeInHeavenAddon.accelerationState(overworld).clear();
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    // 1.16 asked the stun of an action's performer in PowerBaseImpl.checkRequirements (:264-266), outside the
    // checkConditions that LightSpeedDash and TimeSystem replaced: a stunned Stand's dash was refused with the stun
    // message, and a dash held when the stun came ended with it (tickHeldAction asked checkRequirements again).
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "mihDashStunned")
    public static void dashIsRefusedAndEndedByAStunnedStandAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-stun", true);
        helper.runAfterDelay(2, () -> guarded(() -> cleanup(fixture), () -> {
            StandPower power = fixture.power();
            accelerate(fixture);
            power.setStamina(power.getMaxStamina());
            StandEntity stand = fixture.stand();
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
            helper.assertTrue(ModStatusEffects.isStunned(stand), "Setup: the Stand is not stunned");
            int messages = fixture.listener().overlayMessages().size();
            helper.assertTrue(!tryDash(fixture, InputMethod.HOLD)
                            && LivingComponentAction.getCurEntityAction(stand) == null,
                    "A stunned Stand's dash was not refused on the press");
            helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                            fixture.listener().overlayMessages().size()).equals(List.of(STUN)),
                    "1.16 sent the stun message for a stunned performer's dash, the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
            stand.removeEffect(ModStatusEffects.STUN);
        }));
        // Two ticks later: the server caches a power's usable abilities for the tick.
        helper.runAfterDelay(4, () -> guarded(() -> cleanup(fixture), () -> {
            StandPower power = fixture.power();
            StandEntity stand = fixture.stand();
            accelerate(fixture);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(tryDash(fixture, InputMethod.HOLD), "Setup: the unstunned dash did not start");
            tickStand(fixture);
            EntityActionInstance running = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(running != null && running.ability == fixture.dash() && !running.isOver(),
                    "Setup: the dash did not run its first tick");
            int messages = fixture.listener().overlayMessages().size();
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
            tickStand(fixture);
            helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == null,
                    "1.16 asked the performer's stun on every held tick, so a dash held when the stun came ends; it went on");
            helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                            fixture.listener().overlayMessages().size()).equals(List.of(STUN)),
                    "1.16 ended the stunned dash with the stun message, the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
            cleanup(fixture);
            helper.succeed();
        }));
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "mihTimeSystemStunned")
    public static void timeSystemIsRefusedAndEndedByAStunnedStandAsIn116(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        Fixture fixture = user(helper, "mih-system-stun", true);
        Runnable cleanup = () -> {
            AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
            MadeInHeavenAddon.accelerationState(overworld).clear();
            overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
            cleanup(fixture);
        };
        helper.runAfterDelay(2, () -> guarded(cleanup, () -> {
            overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
            StandPower power = fixture.power();
            MadeInHeavenAddon.accelerationState(overworld).clear();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = fixture.stand();
            Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
            helper.assertTrue(ModStatusEffects.isStunned(stand), "Setup: the Stand is not stunned");
            int messages = fixture.listener().overlayMessages().size();
            helper.assertTrue(!tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD)
                            && LivingComponentAction.getCurEntityAction(stand) == null,
                    "A stunned Stand's time system was not refused on the press");
            helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                            fixture.listener().overlayMessages().size()).equals(List.of(STUN)),
                    "1.16 sent the stun message for a stunned performer's time system, the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
            stand.removeEffect(ModStatusEffects.STUN);
        }));
        // Two ticks later: the server caches a power's usable abilities for the tick.
        helper.runAfterDelay(4, () -> guarded(cleanup, () -> {
            StandPower power = fixture.power();
            StandEntity stand = fixture.stand();
            Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
            helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                    "Setup: the unstunned time system hold did not start");
            for (int tick = 0; tick < 3; tick++) {
                tickStand(fixture);
            }
            EntityActionInstance hold = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(hold != null && hold.getPhase() == ActionPhase.WINDUP && !hold.isOver(),
                    "Setup: the time system hold is not in its windup");
            int messages = fixture.listener().overlayMessages().size();
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
            tickStand(fixture);
            helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == null,
                    "1.16 asked the performer's stun on every held tick, so a time system hold ends with the stun;"
                            + " it went on");
            helper.assertTrue(fixture.listener().overlayMessages().subList(messages,
                            fixture.listener().overlayMessages().size()).equals(List.of(STUN)),
                    "1.16 ended the stunned hold with the stun message, the user was sent "
                            + fixture.listener().overlayMessages().subList(
                                    messages, fixture.listener().overlayMessages().size()));
            cleanup.run();
            helper.succeed();
        }));
    }

    // The port's start and clear are two actions; the owner kept that (2026-10-18, "Made in Heaven clear is its own
    // action"), so each acts as the variant that was pressed: a clear hold whose acceleration ended meanwhile does
    // nothing (1.16's one action would have started a new one), and a start hold whose user's own acceleration began
    // meanwhile does not clear it (1.16 would have).
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemFiresOnTheWorld")
    public static void timeSystemHoldActsOnTheAccelerationAtTheEndOfTheHold(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-fire", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(fixture.player().level());
                state.clear();
                power.setStamina(power.getMaxStamina());
                accelerate(fixture);
                Ability clear = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_CLEAR_NAME);
                helper.assertTrue(tryAbility(fixture, clear, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the hold that clears the user's own acceleration did not start");
                EntityActionInstance hold = LivingComponentAction.getCurEntityAction(fixture.stand());
                tickStand(fixture);
                tickStand(fixture);
                helper.assertTrue(hold != null && hold.getPhase() == ActionPhase.WINDUP && !hold.isOver(),
                        "Setup: the clear hold was not in its 25-tick windup");
                state.clear();
                for (int tick = 0; tick < TimeSystemAbility.WINDUP_TICKS + 5; tick++) {
                    tickStand(fixture);
                }
                helper.assertTrue(!state.active()
                                && power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME) == 0,
                        "The clear is its own action: a clear hold whose acceleration ended mid-charge does nothing,"
                                + " but it started a new acceleration; active " + state.active() + ", cooldown "
                                + power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME));
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                tickStand(fixture);

                state.clear();
                power.setAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME, 0);
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the start hold did not start with no acceleration running");
                EntityActionInstance startHold = LivingComponentAction.getCurEntityAction(fixture.stand());
                tickStand(fixture);
                helper.assertTrue(startHold != null && startHold.getPhase() == ActionPhase.WINDUP && !startHold.isOver(),
                        "Setup: the start hold was not in its 25-tick windup");
                accelerate(fixture);
                for (int tick = 0; tick < TimeSystemAbility.WINDUP_TICKS + 5; tick++) {
                    tickStand(fixture);
                }
                helper.assertTrue(state.active() && state.isOwner(fixture.player().getUUID()),
                        "The clear is its own action: a start hold that found the user's own acceleration running"
                                + " must not clear it; active " + state.active());
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    // 1.16 PowerBaseImpl.tickHeldAction gave the tick a holdToFire(N, false) hold fires in to stopHeldAction(true), whose
    // check fired nothing on a failure and said nothing: the time system's own held check stays silent there.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihTimeSystemFiringTick")
    public static void timeSystemHoldFailingInItsFiringTickEndsSilently(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean originalRule = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        helper.runAfterDelay(2, () -> {
            Fixture fixture = user(helper, "mih-system-firing", true);
            try {
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, overworld.getServer());
                StandPower power = fixture.power();
                TimeAccelerationState state = MadeInHeavenAddon.accelerationState(fixture.player().level());
                state.clear();
                power.setStamina(power.getMaxStamina());
                Ability start = power.getAbility(MadeInHeavenAddon.TIME_SYSTEM_NAME);
                helper.assertTrue(tryAbility(fixture, start, SYSTEM_KEY, InputMethod.HOLD),
                        "Setup: the time system hold did not start");
                EntityActionInstance hold = LivingComponentAction.getCurEntityAction(fixture.stand());
                for (int tick = 0; tick < TimeSystemAbility.WINDUP_TICKS + 5
                        && hold != null && !hold.isOver() && !hold.isChargeFiringTick(); tick++) {
                    tickStand(fixture);
                }
                helper.assertTrue(hold != null && !hold.isOver() && hold.isChargeFiringTick()
                                && hold.getPhase() == ActionPhase.WINDUP,
                        "Setup: the hold did not stand at the start of its firing tick");
                int messages = fixture.listener().overlayMessages().size();
                state.begin(UUID.randomUUID());
                tickStand(fixture);
                helper.assertTrue(hold.isOver() && !state.isOwner(fixture.player().getUUID())
                                && power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME) == 0,
                        "A hold whose check failed as it fired still started the acceleration or went on");
                List<String> sent = fixture.listener().overlayMessages();
                helper.assertTrue(sent.size() == messages,
                        "1.16 stopHeldAction(true) ended the firing tick's failed check silently, but the user was sent "
                                + sent.subList(messages, sent.size()));
            } finally {
                AbilityInput.keyRelease(SYSTEM_KEY, fixture.player());
                MadeInHeavenAddon.accelerationState(overworld).clear();
                overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule, overworld.getServer());
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihUniverseResetBar")
    public static void universeResetRunsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            Fixture pressed = user(helper, "mih-reset-press", true);
            Fixture automatic = user(helper, "mih-reset-auto", true);
            try {
                StandPower power = pressed.power();
                power.setStamina(300.0F);
                Ability reset = power.getAbility(MadeInHeavenAddon.UNIVERSE_RESET_NAME);
                helper.assertTrue(power.isAbilityUnlocked(MadeInHeavenAddon.UNIVERSE_RESET_NAME)
                                && tryAbility(pressed, reset, RESET_KEY, InputMethod.CLICK),
                        "Setup: the universe reset did not start at 300 stamina");
                tickStand(pressed);
                tickStand(pressed);
                MobEffectInstance resistance = pressed.player().getEffect(MobEffects.DAMAGE_RESISTANCE);
                helper.assertTrue(resistance != null && resistance.getAmplifier() == 4,
                        "1.16 StandEntityTask performed the reset first and paid its 500 after, so a reset started"
                                + " at 300 stamina still happens; the user has no Resistance V: " + resistance);
                helper.assertTrue(power.getStamina() == 0.0F
                                && power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME)
                                        == UniverseResetAbility.RESET_TICKS,
                        "The reset must drain the 300 bar to 0 and set the time system cooldown, got stamina "
                                + power.getStamina() + ", cooldown "
                                + power.getAbilityCooldown(MadeInHeavenAddon.TIME_SYSTEM_NAME));

                // The automatic reset at the end of phase 31 clicked the action, so its condition applied.
                StandPower ownerPower = automatic.power();
                ServerPlayer owner = automatic.player();
                ownerPower.setStamina(299.0F);
                helper.assertTrue(!UniverseResetAbility.execute(owner)
                                && !owner.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && ownerPower.getStamina() == 299.0F,
                        "1.16 refused the automatic reset below 300 stamina without touching the bar; stamina "
                                + ownerPower.getStamina());
                ownerPower.setStamina(450.0F);
                helper.assertTrue(UniverseResetAbility.execute(owner)
                                && owner.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                                && ownerPower.getStamina() == 0.0F,
                        "1.16 ran the automatic reset at 450 stamina and drained the bar to 0; stamina "
                                + ownerPower.getStamina());
            } finally {
                AbilityInput.keyRelease(RESET_KEY, pressed.player());
                cleanup(pressed);
                cleanup(automatic);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "mihDashResolveGate")
    public static void dashNeedsResolveLevelOneToUnlock(GameTestHelper helper) {
        Fixture fixture = user(helper, "mih-dash-gate", false, false);
        helper.runAfterDelay(2, () -> {
            try {
                StandPower power = fixture.power();
                Ability dash = fixture.dash();
                helper.assertTrue(!power.wasProgressionSkipped() && !StandPower.playerSkipsActionTraining(fixture.player()),
                        "Setup: the user must not skip the Stand progression");
                helper.assertTrue(power.getResolveLevel() == 0, "Setup: a new Stand starts at Resolve level 0");
                helper.assertTrue(dash.getRequiredResolveLevel(power) == 1,
                        "1.16 mih_dash is resolveLevelToUnlock(1), but the port asks for Resolve level "
                                + dash.getRequiredResolveLevel(power));
                helper.assertTrue(!dash.getResolveUnlockConditionCheck(power).isPositive(),
                        "The dash passed the Resolve check at Resolve level 0");

                power.addExp(1000.0F);
                StandTypePersistentData data = power.getCurTypeData();
                UnlockableSkill skill = data.getAllSkills().get(MadeInHeavenAddon.DASH_NAME);
                helper.assertTrue(skill != null, "Setup: the dash skill is missing");
                helper.assertTrue(!skill.canUnlockFromMenu(power, data).isPositive(),
                        "A user at Resolve level 0 with plenty of exp could buy the dash skill");
                helper.assertTrue(!data.unlockSkill(power, MadeInHeavenAddon.DASH_NAME)
                                && !data.isSkillUnlocked(MadeInHeavenAddon.DASH_NAME),
                        "The dash skill was unlocked at Resolve level 0");

                power.setResolveLevel(1);
                helper.assertTrue(power.getResolveLevel() == 1, "Setup: Resolve level 1 was not reached");
                helper.assertTrue(dash.getResolveUnlockConditionCheck(power).isPositive()
                                && skill.canUnlockFromMenu(power, data).isPositive(),
                        "At Resolve level 1 with the exp the dash skill must be available");
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template="empty", templateNamespace=NAMESPACE, timeoutTicks=40, batch="mihDashPhysicalFloor07")
    public static void dashResolveStillRequiresPhysical50AfterEffectInit(GameTestHelper helper) {
        nativeIdentities(helper);
        helper.runAfterDelay(2,()->{
            NativeAssertions assertions=new NativeAssertions(helper,"07");
            ModConfigSpec.BooleanValue config=(ModConfigSpec.BooleanValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standStamina"));
            helper.assertTrue(config!=null,"Setup: actual Stand stamina config spec exists");
            boolean original=config.get();
            try {
                float[] starts={0,49,49.99F,50,150,199,200,0,0};
                for(int index=0;index<starts.length;index++){
                    boolean creative=index==7,configOff=index==8,resolve=index<7;
                    config.set(!configOff);
                    Fixture fixture=user(helper,"mih-floor-"+index,true);
                    assertions.scene(resolve?"RESOLVE_"+starts[index]:creative?"CREATIVE_STORED0":"CONFIG_OFF_STORED0");
                    try {
                        if(creative)fixture.player().setGameMode(GameType.CREATIVE);
                        if(resolve){
                            helper.assertTrue(fixture.player().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE,200,0,false,false)),"Setup: native Resolve effect could not be installed");
                            helper.assertTrue(ResolveModeEffect.getResolveEffectLvl(fixture.player())>=0&&fixture.power().resolveCounter.getResolveValue()>0,"Setup: actual Resolve onAdded initialized before setting finite stamina");
                        }
                        accelerate(fixture);
                        fixture.power().setStamina(starts[index]);
                        float stored=storedStamina(fixture),effective=fixture.power().getStamina();
                        boolean infinite=creative||configOff,expected=effective>=50;
                        helper.assertTrue(stored==starts[index]&&fixture.power().isStaminaInfinite()==infinite&&effective==(infinite?fixture.power().getMaxStamina():stored),"Setup: actual stored/effective/infinite values after Resolve initialization");
                        helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standStamina.get()==!configOff&&hotbarInput(fixture.power())==InputMethod.HOLD,"Setup: actual config and unchanged hotbar HOLD input");
                        dashLog("PRE fix=07 scene="+assertions.current+" stored="+stored+" effective="+effective+" max="+fixture.power().getMaxStamina()+" infinite="+fixture.power().isStaminaInfinite()+" creative="+fixture.player().isCreative()+" config="+JojoModConfig.getCommonConfigInstance(false).standStamina.get()+" resolve="+ResolveModeEffect.getResolveEffectLvl(fixture.player()));
                        ConditionCheck condition=fixture.dash().checkConditions(fixture.power());
                        assertions.check(condition.isPositive()==expected,"actual condition follows the literal getter50 floor");
                        boolean pressed=tryDash(fixture,InputMethod.HOLD);
                        EntityActionInstance action=LivingComponentAction.getCurEntityAction(fixture.stand());
                        assertions.check(pressed==expected&&(action instanceof LightSpeedDashAbility.LightSpeedDashAction)==expected,"registered HOLD/factory admission follows physical floor");
                        assertions.check(storedStamina(fixture)==stored,"press does not take the one150 debit");
                        assertions.check(dashAfterimages(fixture).size()==(expected?1:0),"refused input has no afterimage; admitted input creates one user afterimage");
                        if(expected){
                            helper.assertTrue(pressed&&action!=null&&action.getPhase()==ActionPhase.PERFORM,"Setup: admitted native factory entered PERFORM");
                            int messages=fixture.listener().overlayMessages().size();
                            tickStand(fixture);
                            float after=storedStamina(fixture),afterEffective=fixture.power().getStamina();
                            float expectedStored=infinite?stored:Math.max(stored-150,0);
                            boolean continueExpected=afterEffective>=50,continuing=LivingComponentAction.getCurEntityAction(fixture.stand())!=null;
                            assertions.check(after==expectedStored,"first native perform tick takes150 once or retains genuine infinite storage");
                            assertions.check(continuing==continueExpected,"post-debit continuation uses the same physical50 getter floor");
                            boolean effects=fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&fixture.player().hasEffect(MobEffects.DAMAGE_RESISTANCE)&&fixture.player().hasEffect(MobEffects.DOLPHINS_GRACE);
                            assertions.check(effects==continueExpected,"ended dash removes all three effects; admitted hold keeps them");
                            assertions.check(fixture.listener().overlayMessages().size()==messages,"stamina continuation refusal stays silent");
                            assertions.check(fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==(continueExpected?0:50),"finite Resolve stopped-hold cooldown preserves its technical50 split");
                            tickStand(fixture);
                            assertions.check(storedStamina(fixture)==after&&(LivingComponentAction.getCurEntityAction(fixture.stand())!=null)==continueExpected,"second native action tick does not take another debit or revive a stopped hold");
                            dashLog("IMPACT fix=07 scene="+assertions.current+" storedAfter="+after+" effectiveAfter="+afterEffective+" continuing="+continuing+" cooldown="+fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME));
                        }
                    } finally { cleanup(fixture); }
                }
            } finally {config.set(original);dashLog("CLEANUP fix=07 config="+JojoModConfig.getCommonConfigInstance(false).standStamina.get());}
            assertions.finish();helper.succeed();
        });
    }

    @GameTest(template="empty", templateNamespace=NAMESPACE, timeoutTicks=40, batch="mihDashCurrentDaylight08")
    public static void dashReadsCurrentOverworldDaylightWithoutClearingOwner(GameTestHelper helper) {
        nativeIdentities(helper);
        helper.runAfterDelay(2,()->{
            NativeAssertions assertions=new NativeAssertions(helper,"08");
            ServerLevel overworld=helper.getLevel().getServer().overworld();
            boolean original=overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            try {
                for(String mode:List.of("ENTRY_OFF","HELD_OFF","ENABLED_RELEASE","PHASE_BELOW")){
                    overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,overworld.getServer());
                    Fixture fixture=user(helper,"mih-daylight",true);
                    assertions.scene(mode);
                    try {
                        accelerate(fixture);fixture.power().setStamina(600);
                        TimeAccelerationState state=MadeInHeavenAddon.accelerationState(fixture.player().level());
                        if(mode.equals("PHASE_BELOW"))state.advance(TimeAccelerationService.BUFF_PHASE-1);
                        if(mode.equals("ENTRY_OFF"))overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,overworld.getServer());
                        helper.assertTrue(fixture.player().serverLevel()==overworld&&state.isOwner(fixture.player().getUUID())&&state.phase()==(mode.equals("PHASE_BELOW")?3:4)&&!fixture.power().isStaminaInfinite()&&storedStamina(fixture)==600,"Setup: actual Overworld/owned cache/phase/finite stored stamina before input");
                        helper.assertTrue(overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)==!mode.equals("ENTRY_OFF"),"Setup: actual current Overworld daylight before input");
                        dashLog("PRE fix=08 scene="+mode+" OverworldDaylight="+overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)+" owner="+state.ownerUuid()+" active="+state.active()+" phase="+state.phase()+" stored="+storedStamina(fixture));
                        if(mode.equals("ENTRY_OFF")){
                            ConditionCheck check=fixture.dash().checkConditions(fixture.power());
                            assertions.check(!check.isPositive()&&NO_ACCELERATION.equals(key(check)),"current rule-off admission refuses with the existing acceleration warning");
                            assertions.check(!tryDash(fixture,InputMethod.HOLD)&&LivingComponentAction.getCurEntityAction(fixture.stand())==null,"registered rule-off HOLD does not start a native dash");
                            assertions.check(dashAfterimages(fixture).isEmpty(),"refused rule-off input creates no afterimage");
                            assertions.check(storedStamina(fixture)==600,"refused input does not take stamina");
                            assertions.check(state.active()&&state.isOwner(fixture.player().getUUID()),"dash refusal does not clear global acceleration ownership");
                            assertions.check(state.phase()==4,"dash refusal does not reset acceleration phase");
                        }else if(mode.equals("HELD_OFF")){
                            helper.assertTrue(tryDash(fixture,InputMethod.HOLD),"Setup: rule-on HOLD starts through native input");
                            tickStand(fixture);
                            helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand())!=null&&fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&storedStamina(fixture)==450,"Setup: native first tick takes150 and remains held before changing the rule");
                            int messages=fixture.listener().overlayMessages().size();
                            overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,overworld.getServer());
                            helper.assertTrue(!overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)&&state.isOwner(fixture.player().getUUID())&&state.phase()==4,"Setup: current Overworld rule is off while the actual owner cache stays admitted");
                            tickStand(fixture);
                            assertions.check(LivingComponentAction.getCurEntityAction(fixture.stand())==null,"held native action stops on the changed current Overworld rule");
                            assertions.check(!fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&!fixture.player().hasEffect(MobEffects.DAMAGE_RESISTANCE)&&!fixture.player().hasEffect(MobEffects.DOLPHINS_GRACE),"held rule refusal removes all three dash effects");
                            List<String> sent=fixture.listener().overlayMessages();
                            assertions.check(sent.size()==messages+1&&NO_ACCELERATION.equals(sent.getLast()),"held rule refusal retains the one acceleration warning");
                            assertions.check(storedStamina(fixture)==450,"changed-rule stop does not take another150 debit");
                            assertions.check(fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==100,"changed-rule stop preserves the normal50+50 cooldown");
                            assertions.check(state.active()&&state.isOwner(fixture.player().getUUID())&&state.phase()==4,"stopping this dash leaves actual acceleration owner/phase intact");
                            assertions.check(dashAfterimages(fixture).size()==1,"user afterimage outlives the stopped hold");
                        }else if(mode.equals("ENABLED_RELEASE")){
                            boolean pressed=tryDash(fixture,InputMethod.HOLD);
                            assertions.check(pressed&&LivingComponentAction.getCurEntityAction(fixture.stand()) instanceof LightSpeedDashAbility.LightSpeedDashAction,"rule-on registered HOLD/native factory remains admitted");
                            helper.assertTrue(pressed,"Setup: positive rule-on native input succeeded");
                            tickStand(fixture);
                            assertions.check(storedStamina(fixture)==450&&LivingComponentAction.getCurEntityAction(fixture.stand())!=null,"rule-on native tick retains the one150 debit/hold");
                            assertions.check(fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&fixture.player().hasEffect(MobEffects.DAMAGE_RESISTANCE)&&fixture.player().hasEffect(MobEffects.DOLPHINS_GRACE),"rule-on hold keeps native dash effects");
                            EntityActionInstance released=LivingComponentAction.getCurEntityAction(fixture.stand());
                            AbilityInput.keyRelease(DASH_KEY,fixture.player());
                            helper.assertTrue(released!=null&&released.isOver(),"Setup: native release marks the current hold over before its clearing tick");
                            tickStand(fixture);
                            assertions.check(LivingComponentAction.getCurEntityAction(fixture.stand())==null&&!fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==100,"normal HOLD release/cleanup/cooldown remain unchanged");
                            assertions.check(state.isOwner(fixture.player().getUUID())&&state.phase()==4&&dashAfterimages(fixture).size()==1,"normal release preserves owner/phase and user afterimage");
                        }else{
                            ConditionCheck below=fixture.dash().checkConditions(fixture.power());
                            assertions.check(!below.isPositive(),"daylight-on phase-below-buff still refuses");
                            assertions.check(key(below)==null,"1.16 refused a phase below GIVE_BUFFS with a plain NEGATIVE, got "+describe(below));
                            int messages=fixture.listener().overlayMessages().size();
                            assertions.check(!tryDash(fixture,InputMethod.HOLD)&&LivingComponentAction.getCurEntityAction(fixture.stand())==null,"phase safety remains in the registered input gate");
                            assertions.check(fixture.listener().overlayMessages().size()==messages,"1.16 phase-below-buff press shows no message, but the user was sent "+fixture.listener().overlayMessages().subList(messages,fixture.listener().overlayMessages().size()));
                            assertions.check(storedStamina(fixture)==600&&dashAfterimages(fixture).isEmpty(),"phase refusal keeps stamina and creates no image");
                            assertions.check(state.isOwner(fixture.player().getUUID())&&state.phase()==3,"phase refusal does not clear actual cached ownership");
                        }
                        dashLog("IMPACT fix=08 scene="+mode+" OverworldDaylight="+overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)+" active="+state.active()+" phase="+state.phase()+" stored="+storedStamina(fixture)+" action="+LivingComponentAction.getCurEntityAction(fixture.stand()));
                    }finally{cleanup(fixture);}
                }
            }finally{overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(original,overworld.getServer());dashLog("CLEANUP fix=08 OverworldDaylight="+overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));}
            assertions.finish();helper.succeed();
        });
    }

    @GameTest(template="empty", templateNamespace=NAMESPACE, timeoutTicks=40, batch="mihDashWalkField09")
    public static void dashWalkFieldMatchesDonorPhaseRuleWithoutVelocityMutation(GameTestHelper helper) {
        nativeIdentities(helper);
        helper.runAfterDelay(2,()->{
            NativeAssertions assertions=new NativeAssertions(helper,"09");
            for(boolean snapshot:new boolean[]{false,true}){
                Fixture fixture=user(helper,"mih-walk-field",true);
                assertions.scene(snapshot?"PUBLIC_SNAPSHOT_FIELDS":"NATURAL_PERFORM_FIELDS");
                try {
                    accelerate(fixture);fixture.power().setStamina(600);
                    Vec3 velocity=new Vec3(0.12,0,0.08);fixture.player().setDeltaMovement(velocity);
                    if(!snapshot)assertions.check(effectiveWalkField(fixture)==1&&LivingComponentAction.getCurEntityAction(fixture.stand())==null,"absent action has effective native walk factor1");
                    helper.assertTrue(tryDash(fixture,InputMethod.HOLD),"Setup: registered native HOLD creates the production dash action");
                    EntityActionInstance action=LivingComponentAction.getCurEntityAction(fixture.stand());
                    helper.assertTrue(action instanceof LightSpeedDashAbility.LightSpeedDashAction&&action.getPhase()==ActionPhase.PERFORM&&action.phasesLength.getFloat(ActionPhase.WINDUP)==0&&action.phasesLength.getFloat(ActionPhase.PERFORM)==5&&action.phasesLength.getFloat(ActionPhase.RECOVERY)==0,"Setup: actual native factory and unchanged natural zeroW/fiveP/zeroR configuration");
                    dashLog("PRE fix=09 scene="+assertions.current+" phase="+action.getPhase()+" rawWalk="+action.userWalkSpeed+" effectiveWalk="+effectiveWalkField(fixture)+" velocity="+fixture.player().getDeltaMovement()+" naturalLengths="+action.phasesLength);
                    if(!snapshot){
                        assertions.check(action.userWalkSpeed==1.0F&&effectiveWalkField(fixture)==1.0F,"actual registered natural PERFORM keeps full walk speed (only the Stand guard slows its user)");
                        for(int tick=1;tick<=4;tick++){
                            tickStand(fixture);
                            EntityActionInstance current=LivingComponentAction.getCurEntityAction(fixture.stand());
                            assertions.check(current==action&&current.getPhase()==ActionPhase.PERFORM&&current.userWalkSpeed==1.0F&&effectiveWalkField(fixture)==1.0F,"natural held perform tick"+tick+" keeps full walk speed");
                            assertions.check(fixture.player().getDeltaMovement().equals(velocity)&&storedStamina(fixture)==450&&fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&fixture.player().getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==14&&fixture.player().getEffect(MobEffects.DOLPHINS_GRACE).getAmplifier()==14&&dashAfterimages(fixture).size()==1,"native action fields preserve velocity/one150 debit/effects/user image on tick"+tick);
                            dashLog("NATURAL fix=09 tick="+tick+" rawWalk="+current.userWalkSpeed+" effectiveWalk="+effectiveWalkField(fixture)+" velocity="+fixture.player().getDeltaMovement());
                        }
                        tickStand(fixture);
                        assertions.check(LivingComponentAction.getCurEntityAction(fixture.stand())==null&&effectiveWalkField(fixture)==1&&fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==100,"natural five-tick clear returns effective walk1 and unchanged cooldown");
                        assertions.check(storedStamina(fixture)==450&&fixture.player().getDeltaMovement().equals(velocity)&&!fixture.player().hasEffect(MobEffects.MOVEMENT_SPEED)&&dashAfterimages(fixture).size()==1,"natural clear preserves velocity/cost/image and removes dash effects");
                    }else{
                        EntityActionInstance.InputLifecycleSnapshot original=action.captureInputLifecycle();
                        for(ActionPhase phase:ActionPhase.values()){
                            EntityActionInstance.InputLifecycleSnapshot next=new EntityActionInstance.InputLifecycleSnapshot(phase,0,original.phasesLength().getFloat(phase),0,false,original.phasesLength(),original.skippedWindupPhase());
                            action.restoreInputLifecycle(next,null);
                            float expected=1.0F;
                            assertions.check(action.getPhase()==phase&&action.userWalkSpeed==expected&&effectiveWalkField(fixture)==expected,"public snapshot-only "+phase+" field stays at full walk speed");
                            assertions.check(storedStamina(fixture)==600&&fixture.player().getDeltaMovement().equals(velocity)&&dashAfterimages(fixture).size()==1,"snapshot-only field restoration does not tick damage/cost/velocity or duplicate user image");
                            dashLog("SNAPSHOT-ONLY fix=09 phase="+phase+" rawWalk="+action.userWalkSpeed+" effectiveWalk="+effectiveWalkField(fixture)+" expected="+expected);
                        }
                        action.restoreInputLifecycle(original,null);
                        assertions.check(action.getPhase()==ActionPhase.PERFORM&&action.userWalkSpeed==1.0F&&effectiveWalkField(fixture)==1.0F,"public original PERFORM snapshot restoration keeps full walk speed");
                        AbilityInput.keyRelease(DASH_KEY,fixture.player());tickStand(fixture);
                        assertions.check(LivingComponentAction.getCurEntityAction(fixture.stand())==null&&effectiveWalkField(fixture)==1&&storedStamina(fixture)==600&&fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==100,"released snapshot field control clears before first debit and returns effective1");
                    }
                }finally{cleanup(fixture);}
            }
            dashLog("CLEANUP fix=09 actors=0");assertions.finish();helper.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace=NAMESPACE,timeoutTicks=180,batch="mihDashWarmDaylight08R1")
    public static void warmDaylightCacheMustNotInstallDamageImage(GameTestHelper helper){
        nativeIdentities(helper);
        helper.assertTrue(warmDaylight==null,"Overlapping warm daylight suites");
        WarmDaylightSuite suite=new WarmDaylightSuite(helper);warmDaylight=suite;
        suite.listen();helper.onEachTick(suite::tick);
    }
    @AfterBatch(batch="mihDashWarmDaylight08R1")
    public static void releaseWarmDaylight(ServerLevel level){if(warmDaylight!=null)warmDaylight.close();dashLog("WARM-BATCH-CLEANUP active="+(warmDaylight!=null));}
    @GameTest(template="empty",templateNamespace=NAMESPACE,timeoutTicks=240,batch="mihDashWarmStamina07R1")
    public static void warmStaminaCacheMustNotInstallDamageImage(GameTestHelper helper){
        nativeIdentities(helper);
        helper.assertTrue(warmDaylight==null,"Overlapping warm admission suites");
        WarmDaylightSuite suite=new WarmDaylightSuite(helper,true);warmDaylight=suite;
        suite.listen();helper.onEachTick(suite::tick);
    }
    @AfterBatch(batch="mihDashWarmStamina07R1")
    public static void releaseWarmStamina(ServerLevel level){releaseWarmDaylight(level);}
    private static final class WarmDaylightSuite implements GameTestListener {
        final GameTestHelper helper;final NativeAssertions assertions;final ServerLevel overworld;final boolean originalRule;
        final boolean stamina;final ModConfigSpec.BooleanValue config;final boolean originalConfig;
        Consumer<LivingIncomingDamageEvent> damage;Fixture fixture;Mob victim;AfterimageEntity image;float health;
        String mode;int index,age,delay,hits,afterStopHits,maxImageTick,firstStopAge;boolean closed;
        WarmDaylightSuite(GameTestHelper helper){this(helper,false);}
        WarmDaylightSuite(GameTestHelper helper,boolean stamina){this.helper=helper;this.stamina=stamina;assertions=new NativeAssertions(helper,stamina?"07-R1":"08-R1");overworld=helper.getLevel().getServer().overworld();originalRule=overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);config=(ModConfigSpec.BooleanValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standStamina"));originalConfig=config.get();helper.testInfo.addListener(this);}
        String fix(){return assertions.fix;}
        List<String> modes(){return stamina?List.of("WARM_49_CONSUME_SUMMONED","WARM_0_SUMMONED","WARM_0_UNSUMMONED","WARM_POSITIVE","COLD_49","CREATIVE_STORED0","CONFIG_OFF_STORED0"):List.of("WARM_OFF_SUMMONED","WARM_OFF_UNSUMMONED","WARM_POSITIVE","COLD_OFF");}
        boolean valid(){return mode.equals("WARM_POSITIVE")||mode.equals("CREATIVE_STORED0")||mode.equals("CONFIG_OFF_STORED0");}
        void listen(){damage=event->{if(closed||event.getEntity()!=victim)return;helper.assertTrue(event.getSource().is(DamageTypes.GENERIC)&&event.getAmount()==1&&event.getSource().getDirectEntity()==null&&event.getSource().getEntity()==null,"Natural victim hurt must use actual unowned generic1 source");hits++;if(currentAction()==null)afterStopHits++;dashLog("NATURAL-HURT fix="+fix()+" scene="+mode+" amount="+event.getAmount()+" source="+event.getSource().getMsgId()+" direct="+event.getSource().getDirectEntity()+" causing="+event.getSource().getEntity()+" action="+currentAction()+" imageTick="+(image==null?-1:image.tickCount)+" victimHP="+victim.getHealth());};NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);}
        void tick(){if(closed)return;try{
            if(fixture==null){if(++delay<3)return;if(index==modes().size()){close();assertions.finish();helper.succeed();return;}begin();return;}
            age++;if(image!=null)maxImageTick=Math.max(maxImageTick,image.tickCount);if(firstStopAge<0&&currentAction()==null)firstStopAge=age;
            if(age<8)return;
            boolean valid=valid();
            assertions.check(victim.getHealth()==health-(valid?1:0),"natural healthy victim HP changes only for a legitimately admitted image");
            assertions.check(hits==(valid?6:0)&&afterStopHits==hits,"actual generic1 virtual attempts after real hold stop exist only for valid entry");
            assertions.check(valid?image!=null&&image.isRemoved()&&maxImageTick==6&&dashAfterimages(fixture).isEmpty():image==null&&dashAfterimages(fixture).isEmpty(),"natural image lifecycle6/removal is retained for valid entry, absent for refusal");
            dashLog("NATURAL-END fix="+fix()+" scene="+mode+" initialHP="+health+" actualHP="+victim.getHealth()+" genericAttempts="+hits+" afterStoppedAttempts="+afterStopHits+" firstStopAge="+firstStopAge+" maxImageTick="+maxImageTick+" removed="+(image==null?"absent":image.isRemoved())+" liveImages="+dashAfterimages(fixture).size());
            cleanup(fixture,victim);fixture=null;victim=null;image=null;delay=0;
        }catch(RuntimeException|Error failure){close();throw failure;}}
        void begin(){
            mode=modes().get(index++);assertions.scene(mode);
            boolean summon=!mode.endsWith("UNSUMMONED"),cold=mode.startsWith("COLD"),valid=valid(),creative=mode.equals("CREATIVE_STORED0"),configOff=mode.equals("CONFIG_OFF_STORED0");
            overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(stamina||!cold,overworld.getServer());if(stamina)config.set(!configOff);
            fixture=user(helper,"mih-warm-rule",summon);accelerate(fixture);fixture.power().setStamina(600);
            if(creative)fixture.player().setGameMode(GameType.CREATIVE);if(stamina&&cold)fixture.power().setStamina(49);
            victim=target(helper.getLevel(),EntityType.COW,fixture.player().position().add(0.8,0,0));victim.noPhysics=true;victim.setDeltaMovement(Vec3.ZERO);health=victim.getHealth();
            helper.assertTrue(victim.isAlive()&&health==victim.getMaxHealth()&&health>2&&fixture.power().isStaminaInfinite()==(creative||configOff)&&storedStamina(fixture)==(stamina&&cold?49:600)&&TimeAccelerationService.isActiveOwner(fixture.player())&&TimeAccelerationService.phase(fixture.player().level())==4,"Setup: healthy native victim, actual finite/infinite stored getter and owner/phase4");
            Ability dash=fixture.dash();AbilityConditionCheck cached=fixture.power().updateAvailableMoves().getContextVariationContainer(dash);
            helper.assertTrue(cached!=null&&cached.ability==dash&&cached.conditionCheck.isPositive()==!cold,"Setup: genuine registered positive warm or negative cold container");
            int tick=fixture.player().tickCount;
            if(stamina){
                if(mode.equals("WARM_49_CONSUME_SUMMONED"))helper.assertTrue(fixture.power().consumeStamina(551),"Setup: public finite consume600->49 succeeded");
                else if(mode.startsWith("WARM_0")||creative||configOff)fixture.power().setStamina(0);
                helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standStamina.get()==!configOff&&fixture.power().getStamina()==((creative||configOff)?fixture.power().getMaxStamina():storedStamina(fixture)),"Setup: actual effective finite/infinite getter after public mutation");
            }else if(!valid&&!cold)overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,overworld.getServer());
            boolean live=dash.checkSpecificConditions(fixture.power()).isPositive();
            helper.assertTrue(fixture.power().updateAvailableMoves().getContextVariationContainer(dash)==cached&&cached.conditionCheck.isPositive()==!cold&&fixture.player().tickCount==tick&&live==valid,"Setup: same real cached object remains positive while live condition is negative, no intervening power/entity tick or fabricated container");
            float entryStored=storedStamina(fixture);int messages=fixture.listener().overlayMessages().size();boolean gate=AbilityInput.withConditionCheck(cached,fixture.player(),InputMethod.HOLD);
            helper.assertTrue(gate==!cold,"Setup: actual cached-container condition gate preserves the warm result");
            BufferingState state=BufferingState.clickCanBuffer();
            if(gate){FriendlyByteBuf input=new FriendlyByteBuf(Unpooled.buffer());try{AbilityInput.keyPress(DASH_KEY,cached.ability,fixture.player(),input,InputMethod.HOLD,0,state,dash.getAbilityId());}finally{input.release();}}
            boolean started=state.isActionSuccess;List<AfterimageEntity> images=dashAfterimages(fixture);image=images.isEmpty()?null:images.getFirst();
            assertions.check(started==valid,"fresh server keyPress guard prevents stale eligibility action success");
            assertions.check((currentAction()!=null)==valid,"fresh server gate prevents action installation before super");
            assertions.check(images.size()==(valid?1:0),"fresh server gate creates no damage-capable image for stale/cold refusal");
            assertions.check(fixture.power().isSummoned()==(summon||valid),"stale absent-Stand input is refused before off-arm auto-summon");
            assertions.check(storedStamina(fixture)==entryStored&&fixture.power().getAbilityCooldown(MadeInHeavenAddon.DASH_NAME)==0,"input refusal/press does not debit or start cooldown");
            assertions.check(fixture.listener().overlayMessages().size()==messages+(valid||stamina?0:1)&&(!valid&&!stamina?NO_ACCELERATION.equals(fixture.listener().overlayMessages().getLast()):true),"fresh refusal keeps the acceleration warning; a stamina refusal stays silent as 1.16's plain NEGATIVE");
            assertions.check(!state.shouldBuffer,"early fresh refusal does not invent a buffered hold");
            dashLog("WARM-PRESS fix="+fix()+" scene="+mode+" cachedPositive="+cached.conditionCheck.isPositive()+" livePositive="+live+" gate="+gate+" actualSuccess="+started+" autoSummoned="+fixture.power().isSummoned()+" images="+images.size()+" stored="+storedStamina(fixture)+" effective="+fixture.power().getStamina()+" infinite="+fixture.power().isStaminaInfinite());
            if(currentAction()!=null){tickStand(fixture);if(valid){AbilityInput.keyRelease(DASH_KEY,fixture.player());tickStand(fixture);}else if(currentAction()!=null&&currentAction().isOver())tickStand(fixture);}
            if(!mode.endsWith("UNSUMMONED"))helper.assertTrue(currentAction()==null,"Setup: actual held refusal or legitimate release clears action before natural image/victim ticks");
            helper.assertTrue(image==null||image.isAlive(),"Setup: independently ticking image actually survives the real hold stop");
            age=hits=afterStopHits=maxImageTick=0;firstStopAge=currentAction()==null?0:-1;dashLog("HOLD-STATE fix="+fix()+" scene="+mode+" action="+(currentAction()==null?"cleared":"pending native summon/clear")+" image="+(image==null?"absent":"alive")+" stored="+storedStamina(fixture));
        }
        EntityActionInstance currentAction(){return fixture==null||fixture.stand()==null?null:LivingComponentAction.getCurEntityAction(fixture.stand());}
        void close(){if(closed)return;closed=true;if(damage!=null)NeoForge.EVENT_BUS.unregister(damage);if(fixture!=null)cleanup(fixture,victim);fixture=null;victim=null;overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalRule,overworld.getServer());if(stamina)config.set(originalConfig);warmDaylight=null;dashLog("CLEANUP fix="+fix()+" actors=0 listeners=0 OverworldDaylight="+overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)+" config="+JojoModConfig.getCommonConfigInstance(false).standStamina.get());}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }

    private static float effectiveWalkField(Fixture fixture){EntityActionInstance action=LivingComponentAction.getCurEntityAction(fixture.stand());return action==null?1:fixture.stand().getUserWalkSpeed(action.userWalkSpeed);}
    private static float storedStamina(Fixture fixture){return fixture.power().serializeNBT(fixture.player().registryAccess()).getFloat("Stamina");}
    private static void nativeIdentities(GameTestHelper helper){
        for(Class<?> type:new Class<?>[]{MadeInHeavenAddon.class,LightSpeedDashAbility.class,LightSpeedDashAbility.LightSpeedDashAction.class,TimeAccelerationService.class,TimeAccelerationState.class,StandPower.class,AbilityInput.class,StandAbilityStamina.class,ResolveModeEffect.class,EntityActionInstance.class,LightSpeedDashGameTests.class}){
            try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
                String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())),expected=System.getProperty("mih.dash.expected."+type.getName());
                helper.assertTrue(expected==null||expected.equalsIgnoreCase(actual),"Native class mismatch "+type.getName());
                dashLog("IDENTITY "+type.getName()+"="+actual+" codeSource="+type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString());
            }catch(Exception failure){throw new IllegalStateException("Missing native dash identity "+type.getName(),failure);}
        }
    }
    private static final class NativeAssertions {
        final GameTestHelper helper;final String fix;final List<String> failures=new ArrayList<>();String current;int scenes,checks;
        NativeAssertions(GameTestHelper helper,String fix){this.helper=helper;this.fix=fix;}
        void scene(String name){current=name;scenes++;}
        void check(boolean passed,String message){checks++;dashLog((passed?"PASS ":"FAIL ")+"fix="+fix+" scene="+current+" "+message);if(!passed)failures.add(current+": "+message);}
        void finish(){dashLog("SUMMARY fix="+fix+" scenes="+scenes+" checks="+checks+" failures="+failures.size()+" details="+failures);helper.assertTrue(failures.isEmpty(),"Dash native facet failed; see SUMMARY");}
    }
    private static void dashLog(String message){JojoMod.getLogger().info("D-MIH-DASH "+message);}

    private static void assertEffect(
            GameTestHelper helper, MobEffectInstance effect, int amplifier, String name, int tick) {
        helper.assertTrue(effect != null && effect.getAmplifier() == amplifier && effect.getDuration() == 10
                        && !effect.isVisible(),
                "1.16 standTickPerform gives hidden " + name + " " + amplifier + " for 10 ticks on every held tick;"
                        + " after tick " + tick + ": " + effect);
    }

    private static boolean tryAbility(Fixture fixture, Ability ability, short key, InputMethod method) {
        if (!AbilityInput.withConditionCheck(ability, fixture.player())) {
            return false;
        }
        BufferingState state = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    key, ability, fixture.player(), input, method, 0.0F, state, ability.getAbilityId());
        } finally {
            input.release();
        }
        return state.isActionSuccess;
    }

    private static boolean tryDash(Fixture fixture, InputMethod method) {
        Ability dash = fixture.dash();
        // The server's own order: the input gate, then the press.
        if (!AbilityInput.withConditionCheck(dash, fixture.player())) {
            return false;
        }
        BufferingState state = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    DASH_KEY, dash, fixture.player(), input, method, 0.0F, state, dash.getAbilityId());
        } finally {
            input.release();
        }
        return state.isActionSuccess;
    }

    private static void tickStand(Fixture fixture) {
        StandEntity stand = fixture.stand();
        stand.summonLockTicks = 0;
        LivingComponentAction.getComponent(stand).tick();
    }

    private static void accelerate(Fixture fixture) {
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(fixture.player().level());
        state.begin(fixture.player().getUUID());
        state.advance(TimeAccelerationService.BUFF_PHASE);
    }

    private static List<AfterimageEntity> dashAfterimages(Fixture fixture) {
        return afterimagesOf(fixture, fixture.player());
    }

    private static List<AfterimageEntity> afterimagesOf(Fixture fixture, LivingEntity origin) {
        return fixture.player().level().getEntitiesOfClass(
                AfterimageEntity.class,
                fixture.player().getBoundingBox().inflate(16.0D),
                afterimage -> afterimage.getOriginEntity() == origin);
    }

    private static InputMethod hotbarInput(StandPower power) {
        ControlSchemeTemplate template = power.getPowerType().makeDefaultControlSchemeTemplate();
        return template.groups.values().stream()
                .flatMap(group -> group.hotbars.stream())
                .flatMap(hotbar -> hotbar.slots.stream())
                .map(slot -> slot.get(null))
                .filter(inputs -> inputs != null)
                .flatMap(inputs -> inputs.entrySet().stream())
                .filter(entry -> MadeInHeavenAddon.DASH_NAME.equals(entry.getValue()))
                .map(entry -> entry.getKey())
                .findFirst()
                .orElse(null);
    }

    private static String key(ConditionCheck check) {
        return check.getWarning() != null
                && check.getWarning().getContents() instanceof TranslatableContents contents
                ? contents.getKey()
                : null;
    }

    private static String describe(ConditionCheck check) {
        if (check.isPositive()) {
            return "positive";
        }
        String key = key(check);
        return key != null ? key : "a silent refusal";
    }

    private static <T extends Mob> T target(ServerLevel level, EntityType<T> type, Vec3 position) {
        T mob = type.create(level);
        mob.setPos(position);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    // A failed step ends the test, so its later steps never run: clean up here.
    private static void guarded(Runnable cleanup, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static Fixture user(GameTestHelper helper, String name, boolean summon) {
        return user(helper, name, summon, true);
    }

    private static Fixture user(GameTestHelper helper, String name, boolean summon, boolean skipProgression) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CapturingListener listener = new CapturingListener(
                server, connection, player, CommonListenerCookie.createInitial(profile, false));
        Vec3 position = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        player.moveTo(position.x, position.y, position.z);
        level.addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(MadeInHeavenAddon.MADE_IN_HEAVEN.get());
        if (skipProgression) {
            power.skipProgression();
        }
        helper.assertTrue(power.getMaxStamina() >= 400.0F,
                "Setup: the test needs at least 400 maximum stamina, has " + power.getMaxStamina());
        if (summon) {
            helper.assertTrue(MadeInHeavenAddon.MADE_IN_HEAVEN.get().summon(player, power),
                    "Setup: Made in Heaven could not be summoned");
            power.getSummonedStandEntity().summonLockTicks = 0;
        }
        return new Fixture(player, power, listener);
    }

    private static void cleanup(Fixture fixture, Entity... others) {
        ServerPlayer player = fixture.player();
        AbilityInput.keyRelease(DASH_KEY, player);
        MadeInHeavenAddon.accelerationState(player.level()).clear();
        for (AfterimageEntity afterimage : player.level().getEntitiesOfClass(
                AfterimageEntity.class, player.getBoundingBox().inflate(16.0D))) {
            afterimage.discard();
        }
        StandEntity stand = fixture.power().getSummonedStandEntity();
        if (stand != null) {
            fixture.power().setSummonedStand(null);
            stand.discard();
        }
        for (Entity other : others) {
            other.discard();
        }
        player.discard();
    }

    private record Fixture(ServerPlayer player, StandPower power, CapturingListener listener) {
        private StandEntity stand() {
            return power.getSummonedStandEntity();
        }

        private Ability dash() {
            return power.getAbility(MadeInHeavenAddon.DASH_NAME);
        }
    }

    private static final class CapturingListener extends ServerGamePacketListenerImpl {
        private final List<String> overlayMessages = new ArrayList<>();

        private CapturingListener(
                MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
            capture(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {
            capture(packet);
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
                overlayMessages.add(chat.content().getContents() instanceof TranslatableContents contents
                        ? contents.getKey()
                        : chat.content().getString());
            }
        }

        private List<String> overlayMessages() {
            return overlayMessages;
        }
    }
}
