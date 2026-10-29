package rotp.addons.killerqueenlion;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.ability.BitesTheDustLock;
import rotp.addons.killerqueenlion.ability.KillerQueenLightAttack;
import rotp.addons.killerqueenlion.ability.PrimaryBombDetonateAbility;
import rotp.addons.killerqueenlion.btd.BitesTheDustService;
import rotp.addons.killerqueenlion.entity.BubbleBombEntity;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.entity.SnowBombEntity;
import rotp.addons.killerqueenlion.state.ItemBombService;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import rotp.core.JojoModConfig;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 PowerBaseImpl.tickHeldAction ran checkRequirements on every held tick. Killer Queen's held actions
 * (rotp_zkq8 EntityExplode kq_entity_explo, KQBarrage, KQBlock) replaced Action.checkConditions without calling it: the only
 * condition of their own was BitesZaDustHandler.userToVictim, so a Bites the Dust victim bound during the hold
 * stopped it silently with nothing fired, while no Stand part was ever asked for and a bombed mob that died left
 * the detonation charging; its standPerform then found nothing and StandEntityTask still paid the 250.
 */
@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KillerQueenHeldRecheckGameTests {
    private KillerQueenHeldRecheckGameTests() {}

    @GameTest(template = "empty", batch = "kqHeldRecheckBtd", timeoutTicks = 80)
    public static void heldKitStopsSilentlyOnceBitesTheDustIsArmed(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        short key = 141;
        for (String name : List.of(
                KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, KillerQueenLionAddon.BARRAGE_NAME,
                KillerQueenLionAddon.BLOCK_NAME, KillerQueenLionAddon.HEAVY_CHARGED_NAME)) {
            try (Scene scene = new Scene(helper, "kql-held-btd-" + (key - 140))) {
                EntityActionInstance held = scene.pressAndHold(name, key);
                if (held == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                float stamina = scene.power.getStamina();
                float cowHealth = scene.cow.getHealth();
                scene.armBitesTheDust();
                scene.tick();
                if (scene.held(held)) {
                    wrong.add(name + " is still held after Bites the Dust was armed (phase " + held.getPhase() + ")");
                }
                if (scene.power.getStamina() < stamina) {
                    wrong.add(name + " paid stamina after the stop: " + stamina + " -> " + scene.power.getStamina());
                }
                if (name.equals(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME)
                        && (scene.cow.getHealth() < cowHealth || !scene.state.hasPrimaryBomb())) {
                    wrong.add(name + " fired after the stop: cow health " + scene.cow.getHealth()
                            + ", bomb kept " + scene.state.hasPrimaryBomb());
                }
                if (!scene.owner.messages.isEmpty()) {
                    wrong.add(name + " stopped with a message, 1.16 said nothing: " + scene.owner.keys());
                }
            } finally {
                key++;
            }
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 re-ran the KQ action's checkConditions (BitesZaDustHandler.userToVictim) on every held tick: "
                        + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "kqHeldRecheckArms", timeoutTicks = 80)
    public static void heldKitNeedsNoStandArms(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        short key = 151;
        for (String name : List.of(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, KillerQueenLionAddon.BARRAGE_NAME,
                KillerQueenLionAddon.BLOCK_NAME)) {
            try (Scene scene = new Scene(helper, "kql-held-arms-" + (key - 150))) {
                StandInstance instance = scene.power.getStandInstance().orElse(null);
                if (instance == null || !instance.removePart(StandPart.ARMS)) {
                    wrong.add(name + ": fixture, the Stand's arms could not be removed");
                    continue;
                }
                try {
                    EntityActionInstance held = scene.pressAndHold(name, key);
                    if (held == null) {
                        wrong.add(name + " without the Stand's arms: " + scene.fixtureFailure);
                        continue;
                    }
                    scene.tick();
                    if (!scene.held(held)) {
                        wrong.add(name + " stopped on a held tick without the Stand's arms (phase "
                                + held.getPhase() + ", messages " + scene.owner.keys() + ")");
                    }
                } finally {
                    instance.addPart(StandPart.ARMS);
                }
            } finally {
                key++;
            }
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 EntityExplode, KQBarrage and KQBlock replaced StandAction.checkConditions, so no Stand part "
                        + "was asked for at the press or on a held tick: " + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "kqHeldRecheckDeadBomb", timeoutTicks = 80)
    public static void detonationChargeGoesOnAfterTheBombedMobDiesAndStillPays(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "kql-held-dead-bomb")) {
            EntityActionInstance held = scene.pressAndHold(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, (short) 161);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            scene.cow.kill();
            helper.assertTrue(!scene.cow.isAlive(), "fixture: the bombed cow did not die");
            scene.tick();
            helper.assertTrue(scene.held(held) && held.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "1.16 did not recheck the bomb while kq_entity_explo was held, so its charge went on after the "
                            + "bombed mob died; it stopped (phase " + held.getPhase() + ", messages "
                            + scene.owner.keys() + ")");
            float stamina = scene.power.getStamina();
            held.setPhaseStart(ActionPhase.PERFORM);
            held._tickAction();
            helper.assertTrue(Math.abs(stamina - scene.power.getStamina() - 250.0F) < 0.01F,
                    "1.16 StandEntityTask paid the 250 after standPerform whatever it found; stamina " + stamina
                            + " -> " + scene.power.getStamina());
            helper.succeed();
        }
    }

    /**
     * 1.16 InitStands.java:99: kq_reset was ResetDay extends StandEntityAction (holdToFire(60, false), standPose
     * DETONATE): the press summoned Killer Queen (StandEntityAction.onClick, AutoSummonMode.FULL) and the Stand held the
     * reset as its task; PowerBaseImpl.checkRequirements read the stun of that performer on every held tick, so a stun
     * on the Stand alone (StandEntity.standCrash) ended the hold with nothing paid, rewound or put on cooldown.
     */
    @GameTest(template = "empty", batch = "kqResetStandAction", timeoutTicks = 80)
    public static void resetIsHeldByTheStandAndAStandStunStopsIt(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "kq-reset-stand")) {
            scene.awakenAndMarkTime();
            KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(scene.owner, scene.power);
            helper.assertTrue(!scene.power.isSummoned(), "fixture: Killer Queen is still out");
            EntityActionInstance held = scene.pressAndHold(KillerQueenLionAddon.RESET_DAY_NAME, (short) 171);
            helper.assertTrue(held != null,
                    "1.16 ResetDay was a Stand task: the press summoned Killer Queen and the Stand held kq_reset; "
                            + scene.fixtureFailure);
            StandEntity stand = scene.power.getSummonedStandEntity();
            float stamina = scene.power.getStamina();
            long dayTime = helper.getLevel().getDayTime();
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
            helper.assertTrue(!ModStatusEffects.isStunned(scene.owner), "fixture: the stun reached the user");
            scene.tick();
            helper.assertTrue(!scene.held(held) && scene.power.getStamina() == stamina
                            && scene.state.hasBitesTheDustMarker() && helper.getLevel().getDayTime() == dayTime
                            && !scene.power.isAbilityOnCooldown(KillerQueenLionAddon.RESET_DAY_NAME),
                    "1.16 ended the reset's hold once its performer, the Stand, was stunned, with nothing paid or "
                            + "rewound; held " + scene.held(held) + " (phase " + held.getPhase() + "), stamina "
                            + stamina + " -> " + scene.power.getStamina() + ", cooldown "
                            + scene.power.getAbilityCooldown(KillerQueenLionAddon.RESET_DAY_NAME));
            helper.succeed();
        }
    }

    /**
     * The reset still fires as earlier rounds kept it (1.16 ResetDay.standPerform, StandEntityTask paying 500, the
     * 2000-tick cooldown set when the hold fired), now from the Stand's task.
     */
    @GameTest(template = "empty", batch = "kqResetStandFires", timeoutTicks = 120)
    public static void resetFiresFromTheStandsTask(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "kq-reset-fires")) {
            scene.awakenAndMarkTime();
            long markedDayTime = scene.state.bitesTheDustMarkerTime();
            helper.getLevel().setDayTime(markedDayTime + 600L);
            EntityActionInstance held = scene.pressAndHold(KillerQueenLionAddon.RESET_DAY_NAME, (short) 172);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            helper.assertTrue(!scene.power.isAbilityOnCooldown(KillerQueenLionAddon.RESET_DAY_NAME),
                    "1.16 set kq_reset's cooldown when the hold fired, not at the press");
            float stamina = scene.power.getStamina();
            for (int tick = 0; tick < 80 && scene.held(held); tick++) {
                scene.tick();
            }
            scene.tick();
            helper.assertTrue(!scene.held(held) && Math.abs(stamina - scene.power.getStamina() - 500.0F) < 0.01F
                            && scene.power.isAbilityOnCooldown(KillerQueenLionAddon.RESET_DAY_NAME)
                            && helper.getLevel().getDayTime() == markedDayTime && scene.owner.messages.isEmpty(),
                    "the reset did not fire from the Stand's task: held " + scene.held(held) + " (phase "
                            + held.getPhase() + "), stamina " + stamina + " -> " + scene.power.getStamina()
                            + ", cooldown " + scene.power.getAbilityCooldown(KillerQueenLionAddon.RESET_DAY_NAME)
                            + ", day time " + helper.getLevel().getDayTime() + " (marked " + markedDayTime
                            + "), messages " + scene.owner.keys());
            helper.succeed();
        }
    }

    /**
     * 1.16 EntityExplode (kq_entity_explo) was a StandEntityLightAttack: once fired, its perform held other Stand actions
     * back (isCancelable with its recovery ticks), and its recovery gave way to any Stand action pressed then, a new
     * detonation too (StandEntityLightAttack.isCancelable :127-129, isChainable, isFreeRecovery).
     */
    @GameTest(template = "empty", batch = "kqDetonationRecovery", timeoutTicks = 80)
    public static void detonationRecoveryGivesWayToAnyStandActionAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        Object[][] rows = {
                { ActionPhase.PERFORM, 0, KillerQueenLionAddon.PUNCH_NAME, false },
                { ActionPhase.RECOVERY, 0, KillerQueenLionAddon.PUNCH_NAME, true },
                { ActionPhase.RECOVERY, 6, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, true } };
        short key = 181;
        for (Object[] row : rows) {
            ActionPhase phase = (ActionPhase) row[0];
            int tick = (Integer) row[1];
            String pressed = (String) row[2];
            boolean replaces = (Boolean) row[3];
            short otherKey = (short) (key + 10);
            try (Scene scene = new Scene(helper, "kql-detonate-recovery-" + (key - 180))) {
                EntityActionInstance fired = scene.pressAndHold(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, key);
                if (fired == null) {
                    wrong.add(phase + " " + tick + ": " + scene.fixtureFailure);
                    continue;
                }
                fired.setPhase(phase, tick);
                BufferingState state = scene.press(pressed,
                        pressed.equals(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME) ? InputMethod.HOLD : InputMethod.CLICK,
                        otherKey);
                EntityActionInstance now = scene.stand.getCurStandAction();
                boolean replaced = state.isActionSuccess && !state.shouldBuffer && fired.isOver() && now != null
                        && now != fired && now.ability == scene.power.getAbility(pressed);
                boolean waits = !state.isActionSuccess && now == fired && !fired.isOver();
                if (replaces ? !replaced : !waits) {
                    wrong.add(pressed + " pressed in the fired detonation's " + phase + " tick " + tick + ": 1.16 "
                            + (replaces ? "let it replace the detonation at once" : "kept the detonation")
                            + ", the port started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
                            + ", the Stand's action " + now + " " + scene.owner.keys());
                }
            } finally {
                key++;
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entity_explo recovery: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 kq_entity_explo (100 InitStands :86) played killer_queen_boom (KQ_BOMB) at the Stand as its recovery began
     * (standSound(Phase.RECOVERY), StandEntityTask.moveToPhase, StandEntityAction.playSoundAtStand), only as
     * StandEntityLightAttack.getSounds :120-124 let it: an entity as the task's target (what the press aimed at, which
     * the light attack kept, standKeepsTarget :165-167, and the Stand turned to), not in arms-only mode, an empty
     * finisher bar. EntityExplode.standPerform made no sound of its own beside the explosion's.
     */
    @GameTest(template = "empty", batch = "kqDetonationBoom", timeoutTicks = 60)
    public static void detonationBoomsAtTheStandAsItsRecoveryBeginsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        // aimed at the bombed cow, finisher meter at the recovery, arms only at the recovery, booms
        Object[][] rows = {
                { true, 0.0F, false, true },
                { false, 0.0F, false, false },
                { true, 0.5F, false, false },
                { true, 0.0F, true, false } };
        short key = 221;
        for (Object[] row : rows) {
            boolean aimed = (Boolean) row[0];
            float meter = (Float) row[1];
            boolean armsOnly = (Boolean) row[2];
            boolean booms = (Boolean) row[3];
            String what = (aimed ? "aimed at the bombed cow" : "aimed at nothing") + ", finisher " + meter
                    + (armsOnly ? ", arms only" : "");
            List<String> booming = new ArrayList<>();
            Consumer<PlayLevelSoundEvent.AtPosition> sounds = event -> {
                if (event.getLevel() == helper.getLevel() && event.getSound() != null
                        && event.getSound().value() == KillerQueenLionAddon.KILLER_QUEEN_BOOM.get()) {
                    booming.add(event.getPosition().toString());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, sounds);
            try (Scene scene = new Scene(helper, "kql-detonation-boom-" + (key - 220))) {
                LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
                component.entityAim.setTarget(aimed ? new ActionTarget(scene.cow) : ActionTarget.EMPTY);
                EntityActionInstance fired = scene.pressAndHold(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, key);
                component.entityAim.setTarget(ActionTarget.EMPTY);
                if (!(fired instanceof PrimaryBombDetonateAbility.DetonateAction detonation)) {
                    wrong.add(what + ": " + scene.fixtureFailure);
                    continue;
                }
                ActionTarget task = fired.getTaskTarget(helper.getLevel());
                ActionTarget turned = fired.standRotationTarget;
                boolean kept = task.getType() == ActionTarget.TargetType.ENTITY && task.getEntity() == scene.cow;
                boolean turnedToCow = turned != null && turned.getType() == ActionTarget.TargetType.ENTITY
                        && turned.getEntity() == scene.cow;
                if (kept != aimed || turnedToCow != aimed) {
                    wrong.add(what + ": 1.16 EntityExplode's task " + (aimed ? "kept the cow and the Stand turned to it"
                            : "kept nothing") + ", the port's task holds " + task + ", the Stand turns to " + turned);
                }
                if (fired.ability instanceof StandEntityAbility standAbility
                        && standAbility.lockOnTargetPosition(scene.power, scene.stand, fired)) {
                    wrong.add(what + ": 1.16 StandEntityLightAttack.lockOnTargetPosition was false, the port turns the "
                            + "Stand's offsets to the task's target");
                }
                fired.setPhaseStart(ActionPhase.PERFORM);
                fired._tickAction();
                if (KillerQueenStateService.primaryBombTarget(scene.owner) != null) {
                    wrong.add(what + ": fixture, the perform did not detonate the bomb");
                }
                if (!booming.isEmpty()) {
                    wrong.add(what + ": the perform played killer_queen_boom at " + booming
                            + "; 1.16 EntityExplode.standPerform played only the explosion");
                }
                scene.stand.setFinisherMeter(meter);
                if (armsOnly) {
                    scene.stand.setArmsOnlyMode(true, false);
                }
                fired.setPhaseStart(ActionPhase.RECOVERY);
                if (fired.getPhase() != ActionPhase.RECOVERY || detonation.boomsAsRecoveryBegins(scene.stand) != booms) {
                    wrong.add(what + ": 1.16 " + (booms ? "played" : "did not play") + " killer_queen_boom at the Stand"
                            + " as the recovery began, the port " + (booms ? "does not" : "does") + " (phase "
                            + fired.getPhase() + ", recovery " + fired.phasesLength.getFloat(ActionPhase.RECOVERY)
                            + ", task " + fired.getTaskTarget(helper.getLevel()) + ")");
                }
                scene.stand.setArmsOnlyMode(false);
            } finally {
                NeoForge.EVENT_BUS.unregister(sounds);
                key++;
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entity_explo boom: " + wrong);
        helper.succeed();
    }

    private static final List<String> FRONT_BOMB_MOVES = List.of(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME,
            KillerQueenLionAddon.ITEM_BOMB_DETONATE_NAME, KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME,
            KillerQueenLionAddon.BLOCK_BOMB_CLEAR_NAME, KillerQueenLionAddon.PROJECTILE_DETONATE_NAME,
            KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME);

    /**
     * 1.16 kq_entity_explo, kq_item_explo, kq_block_explode, kq_block_quit, snow_explode and kq_bubble_block_explode
     * (100 InitStands :85-86, :89-90, :95, :97) kept StandEntityLightAttack.getOffsetFromUser :169-176: front3dOffset put
     * the Stand before its user,
     * the horizontal distance from the user's feet to the task's target less 1 and half its width, within 0.5 to 2
     * blocks (with no target the user's crosshair within 2 blocks, else 2), along the user's view, which it followed
     * (lockOnTargetPosition :178-181 false); a task pressed at nothing measured it to the target it took over; arms-only
     * mode gave no offset of its own.
     */
    @GameTest(template = "empty", batch = "kqlLightBombFront", timeoutTicks = 40)
    public static void bombMovesStandBeforeTheirUserAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-front")) {
            scene.cow.setPos(scene.owner.position().add(0.0D, 0.0D, 2.5D));
            double far = Math.min(2.0D, scene.stand.getMaxRange());
            double front = Mth.clamp(2.5D - (1.0D + scene.cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, scene.stand.getEffectiveRange()), far);
            helper.assertTrue(front > 0.5D && front < far, "fixture: the cow does not set the front offset: " + front);
            ActionTarget cow = new ActionTarget(scene.cow);
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            for (String name : FRONT_BOMB_MOVES) {
                // pressed at the cow 2.5 blocks south, then the user looks east
                scene.clearStandAction();
                scene.turn(0.0F);
                EntityActionInstance atCow = scene.setOnStand(name, cow);
                if (atCow == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                ActionTarget task = atCow.getTaskTarget(helper.getLevel());
                if (task.getEntity() != scene.cow || atCow.standRotationTarget == null
                        || !atCow.standRotationTarget.sameTarget(cow)) {
                    wrong.add(name + " pressed at the cow: 1.16 StandEntityLightAttack.standKeepsTarget kept it and the "
                            + "Stand turned to it; the task holds " + task + ", the Stand turns to "
                            + atCow.standRotationTarget);
                }
                scene.turn(-90.0F);
                String at = scene.frontError(front);
                if (at != null) {
                    wrong.add(name + " pressed at the cow, the user then looking east: 1.16 put the Stand " + at);
                }
                // pressed at nothing during the move at the cow: the task takes the cow over
                if (scene.power.getAbility(name) instanceof EntityActionType type) {
                    EntityActionInstance next = type.initActionOnAbilityUse(helper.getLevel(), scene.owner,
                            scene.stand, null);
                    component.setAction(next, scene.owner, SyncType.NO_SYNC);
                    at = scene.frontError(front);
                    if (component.getAction() != next || next.getTaskTarget(helper.getLevel()).getEntity() != scene.cow
                            || at != null) {
                        wrong.add(name + " pressed at nothing after the move at the cow: 1.16 took the cow over and put "
                                + "the Stand " + (at != null ? at : "where it is") + ", the task holds "
                                + next.getTaskTarget(helper.getLevel()));
                    }
                }
                // pressed at nothing, nothing within 2 blocks of the user's view
                scene.clearStandAction();
                if (scene.setOnStand(name, ActionTarget.EMPTY) != null && (at = scene.frontError(far)) != null) {
                    wrong.add(name + " pressed at nothing: 1.16 put the Stand " + at);
                }
                scene.clearStandAction();
            }
            // arms-only last, each action replacing the one before: an arms-only Stand whose action is cleared leaves
            scene.clearStandAction();
            scene.stand.setArmsOnlyMode(true, false);
            for (String name : FRONT_BOMB_MOVES) {
                if (scene.power.getAbility(name) instanceof EntityActionType type) {
                    component.entityAim.setTarget(cow);
                    EntityActionInstance arms = type.initActionOnAbilityUse(helper.getLevel(), scene.owner,
                            scene.stand, null);
                    component.setAction(arms, scene.owner, SyncType.NO_SYNC);
                    component.entityAim.setTarget(ActionTarget.EMPTY);
                    if (component.getAction() != arms || arms.getSyncedStandOffset() != null) {
                        wrong.add(name + " pressed at the cow in arms-only mode: 1.16 front3dOffset gave no offset, "
                                + "the port " + arms.getSyncedStandOffset() + " (the Stand's action "
                                + component.getAction() + ")");
                    }
                }
            }
            scene.stand.setArmsOnlyMode(false);
            scene.clearStandAction();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack bomb moves' front offset: " + wrong);
        helper.succeed();
    }

    // the detonations and kq_itembomb / kq_entitybomb (ItemFBomb, PunchBomb: no getOffsetFromUser of their own either)
    private static final List<String> FOLLOW_BOMB_MOVES = followBombMoves();

    private static List<String> followBombMoves() {
        List<String> moves = new ArrayList<>(FRONT_BOMB_MOVES);
        moves.add(KillerQueenLionAddon.ITEM_BOMB_NAME);
        moves.add(KillerQueenLionAddon.PRIMARY_BOMB_NAME);
        return List.copyOf(moves);
    }

    /**
     * 1.16 StandEntity.updatePosition asked the task's getOffsetFromUser on every tick, before the task's own tick, so
     * the front3dOffset of the light-attack bomb moves (StandEntityLightAttack.getOffsetFromUser :169-176) followed in
     * that same position update a task target that moved, the user's own steps and, with no task target, what the
     * user's crosshair met within 2 blocks. kq_entitybomb's ENTITY requirement refused a press at nothing.
     */
    @GameTest(template = "empty", batch = "kqlLightBombFollow", timeoutTicks = 40)
    public static void bombMovesFollowTheirTargetInTheSameTickAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-follow")) {
            double near = Math.min(0.5D, scene.stand.getEffectiveRange());
            double far = Math.min(2.0D, scene.stand.getMaxRange());
            double backAway = 1.0D + scene.cow.getBbWidth() / 2.0D;
            Vec3 home = scene.owner.position();
            ActionTarget cow = new ActionTarget(scene.cow);
            for (String name : FOLLOW_BOMB_MOVES) {
                scene.owner.setPos(home);
                scene.cow.setPos(home.add(0.0D, 0.0D, 2.5D));
                scene.clearStandAction();
                scene.turn(0.0F);
                if (scene.setOnStand(name, cow) == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                String at = scene.frontError(Mth.clamp(2.5D - backAway, near, far));
                if (at != null) {
                    wrong.add(name + " pressed at the cow 2.5 blocks south: fixture, the Stand " + at);
                }
                // no tick of the action between the moves and the position update
                scene.cow.setPos(home.add(0.0D, 0.0D, 3.0D));
                if ((at = scene.frontError(Mth.clamp(3.0D - backAway, near, far))) != null) {
                    wrong.add(name + ", its cow then 3 blocks south: 1.16 followed it in that position update, the "
                            + "Stand " + at);
                }
                scene.owner.setPos(home.add(0.0D, 0.0D, 0.4D));
                if ((at = scene.frontError(Mth.clamp(2.6D - backAway, near, far))) != null) {
                    wrong.add(name + ", its user then 0.4 blocks nearer the cow: 1.16 measured from where the user "
                            + "stood in that position update, the Stand " + at);
                }
                // pressed at nothing, nothing ahead; then the cow comes into the user's view 2.3 blocks east
                scene.owner.setPos(home);
                scene.clearStandAction();
                if (name.equals(KillerQueenLionAddon.PRIMARY_BOMB_NAME)) {
                    continue;
                }
                scene.turn(-90.0F);
                if (scene.setOnStand(name, ActionTarget.EMPTY) == null) {
                    wrong.add(name + " pressed at nothing: " + scene.fixtureFailure);
                    continue;
                }
                if ((at = scene.frontError(far)) != null) {
                    wrong.add(name + " pressed at nothing: fixture, the Stand " + at);
                }
                scene.cow.setPos(home.add(2.3D, 0.5D, 0.0D));
                if ((at = scene.frontError(Mth.clamp(2.3D - backAway, near, far))) != null) {
                    wrong.add(name + " pressed at nothing, the cow then 2.3 blocks ahead in the user's view: 1.16's "
                            + "front3dOffset met it with the user's crosshair in that position update, the Stand "
                            + at);
                }
                scene.clearStandAction();
            }
            scene.owner.setPos(home);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack bomb moves' front offset on each tick: "
                + wrong);
        helper.succeed();
    }

    private static final List<String> PERFORM_SOUND_MOVES = List.of(KillerQueenLionAddon.ITEM_BOMB_DETONATE_NAME,
            KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME, KillerQueenLionAddon.BLOCK_BOMB_CLEAR_NAME,
            KillerQueenLionAddon.PROJECTILE_DETONATE_NAME, KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME);

    /**
     * 1.16 InitStands gave kq_item_explo (100 :85), kq_block_explode (:89), snow_explode (:95) and kq_bubble_block_explode
     * (:97) standSound(KQ_BOMB) and kq_block_quit (:90) standSound(KQ_UNBOMB), both for the PERFORM phase: StandEntityTask.moveToPhase played it
     * as the perform began, at the Stand for the players who hear Stands (StandEntityAction.playSoundAtStand,
     * StandEntity.playSound), whatever the perform then found or blew up, and only as StandEntityLightAttack.getSounds
     * :120-124 let it: an entity as the task's target (the one pressed at, standKeepsTarget :165-167), not in arms-only
     * mode, an empty finisher bar. No other sound of these moves played it.
     */
    @GameTest(template = "empty", batch = "kqlPerformBooms", timeoutTicks = 60)
    public static void bombMovesSoundAtTheStandAsTheirPerformBeginsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        // aimed at the cow, finisher meter at the perform, arms only, sounds
        Object[][] rows = {
                { true, 0.0F, false, true },
                { false, 0.0F, false, false },
                { true, 0.5F, false, false },
                { true, 0.0F, true, false } };
        SoundEvent boom = KillerQueenLionAddon.KILLER_QUEEN_BOOM.get();
        SoundEvent unboom = KillerQueenLionAddon.KILLER_QUEEN_UNBOOM.get();
        List<String> levelSounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> atPosition = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && (event.getSound().value() == boom || event.getSound().value() == unboom)) {
                levelSounds.add(event.getSound().value().getLocation().getPath() + " at " + event.getPosition());
            }
        };
        Consumer<PlayLevelSoundEvent.AtEntity> atEntity = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && (event.getSound().value() == boom || event.getSound().value() == unboom)) {
                levelSounds.add(event.getSound().value().getLocation().getPath() + " at " + event.getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, atPosition);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtEntity.class, atEntity);
        ChunkMap.TrackedEntity tracked = null;
        SoundWatcher watcher = new SoundWatcher(helper.getLevel());
        BlockPos block = helper.absolutePos(new BlockPos(1, 3, -5));
        try (Scene scene = new Scene(helper, "kql-perform-sounds")) {
            tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(scene.stand.getId());
            helper.assertTrue(tracked != null, "fixture: Killer Queen is not tracked");
            tracked.seenBy.add(watcher.connection);
            for (String name : PERFORM_SOUND_MOVES) {
                SoundEvent sound = name.equals(KillerQueenLionAddon.BLOCK_BOMB_CLEAR_NAME) ? unboom : boom;
                for (Object[] row : rows) {
                    boolean aimed = (Boolean) row[0];
                    float meter = (Float) row[1];
                    boolean armsOnly = (Boolean) row[2];
                    boolean sounds = (Boolean) row[3];
                    String what = name + (aimed ? " aimed at the cow" : " aimed at nothing") + ", finisher " + meter
                            + (armsOnly ? ", arms only" : "");
                    scene.power.setStamina(scene.power.getMaxStamina());
                    helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                    helper.assertTrue(KillerQueenStateService.markBlockBomb(scene.owner, block),
                            "fixture: the block bomb was rejected");
                    KillerQueenStateService.markBubbleBlock(scene.owner, block);
                    helper.assertTrue(KillerQueenStateService.hasBubbleBlockMarks(scene.owner),
                            "fixture: the bubble block mark was rejected");
                    EntityActionInstance action = scene.setOnStand(name,
                            aimed ? new ActionTarget(scene.cow) : ActionTarget.EMPTY);
                    if (action == null) {
                        wrong.add(what + ": " + scene.fixtureFailure);
                        continue;
                    }
                    scene.stand.setFinisherMeter(meter);
                    if (armsOnly) {
                        scene.stand.setArmsOnlyMode(true, false);
                    }
                    watcher.sent.clear();
                    levelSounds.clear();
                    action.setPhaseStart(ActionPhase.PERFORM);
                    action._tickAction();
                    int atStand = watcher.standSounds(scene.stand, sound);
                    if (atStand != (sounds ? 1 : 0) || !levelSounds.isEmpty()) {
                        wrong.add(what + ": 1.16 " + (sounds ? "played " : "did not play ")
                                + sound.getLocation().getPath() + " at the Stand as the perform began, and nothing "
                                + "else; the port sent it at the Stand " + atStand + " times, into the level "
                                + levelSounds);
                    }
                    scene.stand.setArmsOnlyMode(false);
                    scene.stand.setFinisherMeter(0.0F);
                    scene.clearStandAction();
                    KillerQueenStateService.clearBlockBomb(scene.owner);
                    scene.state.clearBubbleBlockMarks();
                }
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(atPosition);
            NeoForge.EVENT_BUS.unregister(atEntity);
            if (tracked != null) {
                tracked.seenBy.remove(watcher.connection);
            }
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack bomb moves' perform sounds: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 StandEntityLightAttack.Builder :189-194 set standAutoSummonMode(MAIN_ARM), which kq_item_explo,
     * kq_entity_explo, kq_block_explode, kq_block_quit, snow_explode and kq_bubble_block_explode kept (100 InitStands
     * :85-86, :89-90, :95, :97): StandEntityAction.onClick :202-240 summoned only the main arm of a Stand that was away
     * and added the main arm to a Stand out as arms only, never the whole Stand; EntityExplode.onTaskSet then showed
     * the main arm alone.
     */
    @GameTest(template = "empty", batch = "kqlLightBombSummon", timeoutTicks = 40)
    public static void lightBombDetonationsBringOutTheMainArmOnlyAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-summon")) {
            for (String name : FRONT_BOMB_MOVES) {
                if (!(scene.power.getAbility(name) instanceof StandEntityAbility ability)) {
                    wrong.add(name + " is missing");
                    continue;
                }
                InputMethod method = name.equals(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME)
                        ? InputMethod.HOLD : InputMethod.CLICK;
                boolean mainOnly = name.equals(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME);
                // pressed with Killer Queen away
                scene.unsummon();
                ability.onKeyPress(helper.getLevel(), scene.owner, null, method, 0, BufferingState.clickOnly());
                StandEntity away = scene.power.getSummonedStandEntity();
                if (away == null || !away.isArmsOnlyMode() || !away.showArm(InteractionHand.MAIN_HAND)
                        || away.showArm(InteractionHand.OFF_HAND)) {
                    wrong.add(name + " pressed with Killer Queen away: 1.16 brought out its main arm only; the port "
                            + Scene.arms(away));
                }
                // pressed with Killer Queen out as its off arm only
                scene.unsummon();
                helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(scene.owner, scene.power,
                                stand -> stand.setArmsOnlyMode(false, true), true),
                        "fixture: Killer Queen's off arm could not be summoned");
                StandEntity arms = scene.power.getSummonedStandEntity();
                ability.onKeyPress(helper.getLevel(), scene.owner, null, method, 0, BufferingState.clickOnly());
                StandEntity after = scene.power.getSummonedStandEntity();
                if (after != arms || !arms.isArmsOnlyMode() || !arms.showArm(InteractionHand.MAIN_HAND)
                        || arms.showArm(InteractionHand.OFF_HAND) != !mainOnly) {
                    wrong.add(name + " pressed with Killer Queen's off arm out: 1.16 added the main arm"
                            + (mainOnly ? " and showed it alone" : "") + "; the port " + Scene.arms(after));
                }
            }
            scene.unsummon();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack detonations' auto-summon: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 StandEntityLightAttack.standTickPerform :89-91 (IHasStandPunch.playPunchSwingSound :56-64): the detonations
     * built on it played the punch swing sound (Builder :187, ModSounds.STAND_PUNCH_SWING) on each client once in their
     * perform, on its first tick when the perform ran under 5 ticks, else with 5 ticks of it left; never in the windup or
     * the recovery. The client plays it (KillerQueenLightAttack.SwingSound.tick, pinned by the contract); this checks
     * when it is due.
     */
    @GameTest(template = "empty", batch = "kqlLightBombSwing", timeoutTicks = 40)
    public static void lightBombDetonationsSwingInTheirPerformAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-swing")) {
            for (String name : swingBombMoves()) {
                EntityActionInstance action = scene.setOnStand(name, ActionTarget.EMPTY);
                if (action == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                float perform = action.phasesLength.getFloat(ActionPhase.PERFORM);
                List<String> got = new ArrayList<>();
                action.setPhase(ActionPhase.WINDUP, 0);
                got.add("windup " + KillerQueenLightAttack.SwingSound.due(action));
                action.setPhase(ActionPhase.PERFORM, 0);
                got.add("perform of " + perform + " at its tick 0 " + KillerQueenLightAttack.SwingSound.due(action));
                action.setPhase(ActionPhase.RECOVERY, 0);
                got.add("recovery " + KillerQueenLightAttack.SwingSound.due(action));
                action.phasesLength.put(ActionPhase.PERFORM, 8.0F);
                action.setPhase(ActionPhase.PERFORM, 2);
                got.add("perform of 8 at its tick 2 " + KillerQueenLightAttack.SwingSound.due(action));
                action.setPhase(ActionPhase.RECOVERY, 0);
                action.setPhase(ActionPhase.PERFORM, 3);
                got.add("perform of 8 at its tick 3 " + KillerQueenLightAttack.SwingSound.due(action));
                List<String> expected = List.of("windup false", "perform of " + perform + " at its tick 0 true",
                        "recovery false", "perform of 8 at its tick 2 false", "perform of 8 at its tick 3 true");
                if (perform > 5.0F || !got.equals(expected)) {
                    wrong.add(name + ": 1.16 swung " + expected + ", the port " + got);
                }
                scene.clearStandAction();
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack detonations' swing sound: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 kq_block_bomb (020 InitStands :87, 100 :88) registered standSound(USER_KQ), user_kira, on a plain
     * StandEntityAction (BlockBombAction): StandEntityTask.moveToPhase played it as the perform began, at the Stand for the
     * players who hear Stands (StandEntityAction.playSoundAtStand, StandEntity.playSound), with no light-attack gate
     * (one-argument standSound plays in arms-only mode too) and whatever standPerform then did, also below its stamina
     * floor; never in the windup.
     */
    @GameTest(template = "empty", batch = "kqlBlockBombVoice", timeoutTicks = 60)
    public static void blockBombVoicesKiraAtTheStandAsItsPerformBeginsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        SoundEvent kira = KillerQueenLionAddon.USER_KIRA.get();
        ChunkMap.TrackedEntity tracked = null;
        SoundWatcher watcher = new SoundWatcher(helper.getLevel());
        BlockPos block = helper.absolutePos(new BlockPos(3, 2, 3));
        try (Scene scene = new Scene(helper, "kql-block-bomb-voice")) {
            tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(scene.stand.getId());
            helper.assertTrue(tracked != null, "fixture: Killer Queen is not tracked");
            tracked.seenBy.add(watcher.connection);
            helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            ActionTarget stone = new ActionTarget(block, Direction.UP);
            // what it was pressed at, finisher meter, arms only, stamina, row
            Object[][] rows = {
                    { stone, 0.0F, false, -1.0F, "pressed at stone" },
                    { new ActionTarget(scene.cow), 0.0F, false, -1.0F, "pressed at the cow" },
                    { ActionTarget.EMPTY, 0.0F, false, -1.0F, "pressed at nothing" },
                    { stone, 0.5F, false, -1.0F, "pressed at stone, finisher 0.5" },
                    { stone, 0.0F, true, -1.0F, "pressed at stone, arms only" },
                    { stone, 0.0F, false, 0.0F, "pressed at stone with no stamina" } };
            for (Object[] row : rows) {
                String what = (String) row[4];
                float stamina = (Float) row[3];
                scene.power.setStamina(stamina < 0.0F ? scene.power.getMaxStamina() : stamina);
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.BLOCK_BOMB_NAME,
                        (ActionTarget) row[0]);
                if (action == null) {
                    wrong.add(what + ": " + scene.fixtureFailure);
                    continue;
                }
                scene.stand.setFinisherMeter((Float) row[1]);
                if ((Boolean) row[2]) {
                    scene.stand.setArmsOnlyMode(true, false);
                }
                watcher.sent.clear();
                action._tickAction();
                int inWindup = watcher.standSounds(scene.stand, kira);
                watcher.sent.clear();
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                int atPerform = watcher.standSounds(scene.stand, kira);
                if (inWindup != 0 || atPerform != 1) {
                    wrong.add(what + ": 1.16 played user_kira at the Stand once, as the perform began; the port sent it "
                            + inWindup + " times in the windup, " + atPerform + " times as the perform began");
                }
                scene.stand.setArmsOnlyMode(false);
                scene.stand.setFinisherMeter(0.0F);
                scene.clearStandAction();
                KillerQueenStateService.clearBlockBomb(scene.owner);
            }
        } finally {
            if (tracked != null) {
                tracked.seenBy.remove(watcher.connection);
            }
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen block bomb's voice: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 ExplodeBlockAction.standPerform :19-30 (020; Lion 100 the same for the Stand's block bomb): the bombed block
     * was set to air only when its explosion resistance was below 3600000, and the blast, which broke no block, started at
     * the block's integer corner with radius sqrt(standDamageMultiplier) * 2.
     */
    @GameTest(template = "empty", batch = "kqlBlockBombBlast", timeoutTicks = 60)
    public static void blockBombDetonationFollowsExplodeBlockActionAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        BlockPos block = helper.absolutePos(new BlockPos(1, 3, -5));
        ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        helper.assertTrue(multiplier != null, "fixture: the Stand damage multiplier config value was not found");
        double previous = multiplier.get();
        List<String> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = null;
        try (Scene scene = new Scene(helper, "kql-block-bomb-blast")) {
            scene.owner.setInvulnerable(true);
            listener = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getExplosion().getDirectSourceEntity() == scene.owner) {
                    blasts.add(blast(event.getExplosion().center(), event.getExplosion().radius()));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Start.class, listener);
            // the bombed block, the multiplier, whether 1.16 removed the block
            Object[][] rows = {
                    { Blocks.STONE, 1.0D, true },
                    { Blocks.OBSIDIAN, 4.0D, true },
                    { Blocks.BEDROCK, 1.0D, false } };
            for (Object[] row : rows) {
                Block kind = (Block) row[0];
                double factor = (Double) row[1];
                boolean removed = (Boolean) row[2];
                String what = kind.getDescriptionId() + " at a multiplier of " + factor;
                helper.getLevel().setBlockAndUpdate(block, kind.defaultBlockState());
                helper.assertTrue(KillerQueenStateService.markBlockBomb(scene.owner, block),
                        "fixture: the block bomb was rejected");
                scene.power.setStamina(scene.power.getMaxStamina());
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME,
                        ActionTarget.EMPTY);
                if (action == null) {
                    wrong.add(what + ": " + scene.fixtureFailure);
                    continue;
                }
                multiplier.set(factor);
                blasts.clear();
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                multiplier.set(previous);
                List<String> expected = List.of(blast(Vec3.atLowerCornerOf(block), (float) Math.sqrt(factor) * 2.0F));
                boolean air = helper.getLevel().getBlockState(block).isAir();
                if (!blasts.equals(expected) || air != removed) {
                    wrong.add(what + ": 1.16 " + (removed ? "removed" : "kept") + " the block and exploded "
                            + expected + "; the port " + (air ? "removed" : "kept") + " it and exploded " + blasts);
                }
                helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
                scene.clearStandAction();
                KillerQueenStateService.clearBlockBomb(scene.owner);
            }
            scene.owner.setInvulnerable(false);
        } finally {
            multiplier.set(previous);
            if (listener != null) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen block bomb detonation: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 StandEntityLightAttack.onTaskSet :131-140: the light-attack bomb moves with no onTaskSet of their own,
     * kq_itembomb, kq_item_explo, kq_block_explode, kq_block_quit, snow_explode and
     * kq_bubble_block_explode (100 InitStands :83, :85, :89, :90, :95, :97), alternated the Stand's punching hand with every task,
     * and an arms-only Stand whose swing went to the off hand showed both arms; kq_entitybomb (PunchBomb.onTaskSet
     * :90-97) and kq_entity_explo (EntityExplode.onTaskSet :88-92) replaced it and kept the hand.
     */
    @GameTest(template = "empty", batch = "kqLightBombHands", timeoutTicks = 40)
    public static void lightAttackBombMovesAlternateTheStandsHandsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-hands")) {
            for (String name : LIGHT_BOMB_MOVES) {
                boolean alternates = !name.equals("kq_entitybomb") && !name.equals("kq_entity_explo");
                for (boolean armsOnly : new boolean[] { false, true }) {
                    if (armsOnly) {
                        scene.stand.setArmsOnlyMode(true, false);
                    }
                    // the last swing was the main hand's, a few ticks ago
                    scene.stand.applySyncedPunchingHand(InteractionHand.MAIN_HAND);
                    scene.stand.tickCount += 2;
                    EntityActionInstance action = scene.setOnStand(name, ActionTarget.EMPTY);
                    if (action == null) {
                        wrong.add(name + ": " + scene.fixtureFailure);
                    }
                    else {
                        InteractionHand hand = scene.stand.swingingArm;
                        boolean bothArms = scene.stand.isArmsOnlyMode()
                                && scene.stand.showArm(InteractionHand.MAIN_HAND)
                                && scene.stand.showArm(InteractionHand.OFF_HAND);
                        if (hand != (alternates ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                                || armsOnly && bothArms != alternates) {
                            wrong.add(name + (armsOnly ? " with only its main arm out" : "") + ": 1.16 "
                                    + (alternates ? "swung the off hand" + (armsOnly ? " and showed both arms" : "")
                                            : "kept the main hand")
                                    + "; the port swung the " + hand
                                    + (armsOnly ? " and " + Scene.arms(scene.stand) : ""));
                        }
                    }
                    scene.clearStandAction();
                    scene.stand.setArmsOnlyMode(false);
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen light-attack bomb moves' hands: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 PunchBomb.stopOnHeavyAttack :77 and EntityExplode.stopOnHeavyAttack :84 (100 action/stand/punch/PunchBomb, action/stand/EntityExplode) returned true:
     * StandEntityHeavyAttack.afterAttack :352-358 sent a Stand hurt by a heavy punch to its recovery
     * (stopTaskWithRecovery), so the plant that was winding up planted nothing and the detonation that was winding up
     * after its hold set nothing off, neither paying its stamina (StandEntityTask paid it with the perform).
     */
    @GameTest(template = "empty", batch = "kqlHeavyHitStopsBomb", timeoutTicks = 80)
    public static void aHeavyPunchHurtingTheStandStopsTheBombMovesAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        int row = 0;
        for (String name : List.of(KillerQueenLionAddon.PRIMARY_BOMB_NAME, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME)) {
            boolean plant = name.equals(KillerQueenLionAddon.PRIMARY_BOMB_NAME);
            for (boolean hit : new boolean[] { true, false }) {
                String what = name + (hit ? " hit by a heavy punch" : " left alone") + " in its windup";
                short key = (short) (231 + row);
                try (Scene scene = new Scene(helper, "kql-heavy-stop-" + row++)) {
                    EntityActionInstance action;
                    if (plant) {
                        KillerQueenStateService.clearPrimaryBomb(scene.owner);
                        scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                        action = scene.setOnStand(name, new ActionTarget(scene.cow));
                        scene.stand.updatePosition(scene.owner);
                    }
                    else {
                        action = scene.pressAndHold(name, key);
                        for (int tick = 0; action != null && tick < 40 && action.getPhase() == ActionPhase.BUTTON_CHARGE
                                && !action.isOver(); tick++) {
                            scene.tick();
                        }
                        if (action != null) {
                            AbilityInput.keyRelease(key, scene.owner);
                        }
                    }
                    if (action == null || action.getPhase() != ActionPhase.WINDUP) {
                        wrong.add(what + ": fixture, " + (action == null ? scene.fixtureFailure
                                : "the action is in " + action.getPhase() + " with a windup of "
                                        + action.phasesLength.getFloat(ActionPhase.WINDUP)));
                        continue;
                    }
                    float stamina = scene.power.getStamina();
                    if (hit) {
                        EntityActionAbility.onHitByHeavyAttack(scene.stand);
                    }
                    boolean recovery = action.getPhase() == ActionPhase.RECOVERY && !action.isOver();
                    boolean performed = false;
                    for (int tick = 0; tick < 200 && !action.isOver(); tick++) {
                        action._tickAction();
                        performed |= action.getPhase() == ActionPhase.PERFORM;
                    }
                    boolean marked = KillerQueenStateService.primaryBombTarget(scene.owner) == scene.cow;
                    boolean paid = scene.power.getStamina() < stamina;
                    // the plant leaves the bomb on the cow, the detonation takes it off
                    boolean acted = plant ? marked : !marked;
                    if (recovery != hit || performed == hit || acted == hit || paid == hit) {
                        wrong.add(what + ": 1.16 " + (hit
                                ? "moved the task to its recovery at once and neither " + (plant ? "planted" : "set off")
                                        + " the bomb nor paid for it"
                                : (plant ? "planted" : "set off") + " the bomb and paid for it")
                                + "; the port went to recovery " + recovery + ", performed " + performed + ", "
                                + (plant ? "planted" : "set off") + " " + acted + ", paid " + paid);
                    }
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen bomb moves hit by a heavy punch: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 MCUtil.canHarm ended in a plain-true canAttack for a player attacker, so Killer Queen's user planted the
     * primary bomb (PunchBomb.punchEntity) on a Creative player, an invulnerable entity and a player on Peaceful, where
     * 1.21's canAttack refuses all three (canBeSeenAsEnemy). The Peaceful row sets the server's difficulty and restores
     * it before the test returns.
     */
    @GameTest(template = "empty", batch = "kqPlantCanHarm", timeoutTicks = 80)
    public static void primaryBombIsPlantedOnATargetCanHarmTakesAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        int row = 0;
        for (String kind : List.of("a Creative player", "an invulnerable cow", "a player on Peaceful", "an ordinary cow")) {
            try (Scene scene = new Scene(helper, "kq-plant-harm-" + row++)) {
                LivingEntity target = scene.cow;
                Entity extra = null;
                scene.cow.setPos(scene.owner.getX() + 8.0D, scene.owner.getY(), scene.owner.getZ());
                try {
                    switch (kind) {
                        case "a Creative player" -> extra = target = harmTargetPlayer(helper, scene, GameType.CREATIVE);
                        case "a player on Peaceful" -> {
                            extra = target = harmTargetPlayer(helper, scene, GameType.SURVIVAL);
                            server.setDifficulty(Difficulty.PEACEFUL, true);
                        }
                        case "an invulnerable cow" -> {
                            scene.cow.setInvulnerable(true);
                            scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                        }
                        default -> scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                    }
                    if (!kind.equals("an ordinary cow") && scene.owner.canAttack(target)) {
                        wrong.add(kind + ": fixture, 1.21's canAttack does not refuse it, so the row proves nothing");
                        continue;
                    }
                    KillerQueenStateService.clearPrimaryBomb(scene.owner);
                    EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.PRIMARY_BOMB_NAME, new ActionTarget(target));
                    scene.stand.updatePosition(scene.owner);
                    if (action == null) {
                        wrong.add(kind + ": fixture, " + scene.fixtureFailure);
                        continue;
                    }
                    for (int tick = 0; tick < 200 && !action.isOver(); tick++) {
                        action._tickAction();
                    }
                    if (KillerQueenStateService.primaryBombTarget(scene.owner) != target) {
                        wrong.add(kind + ": 1.16 planted the bomb on it (canHarm a plain true for a player user), the port"
                                + " planted none");
                    }
                } finally {
                    server.setDifficulty(difficulty, true);
                    if (extra != null) {
                        extra.discard();
                    }
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen bomb planted by a player user: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 ItemFBombExplode.standPerform blew up every carrier of the user's item bomb and asked no canAttack or
     * canHarm of it: a Creative carrier, an invulnerable one, a player on Peaceful and a teammate without friendly fire
     * (JojoModUtil.canHarm's team rule) all blew up, once for each slot of the carrier that held a bomb item
     * (ExplodePlayers, ExplodeEntity): a cow with two bomb items and a player with two blew up twice.
     */
    @GameTest(template = "empty", batch = "kqItemBombCanHarm", timeoutTicks = 40)
    public static void itemBombBlowsUpACarrierCanHarmTakesAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        int row = 0;
        for (String kind : List.of("a Creative player", "an invulnerable cow", "a player on Peaceful",
                "a teammate cow without friendly fire", "a cow with two bomb items",
                "a player with two bomb items", "an ordinary cow")) {
            List<Vec3> centers = new ArrayList<>();
            Consumer<ExplosionEvent.Start> listener = null;
            net.minecraft.world.scores.PlayerTeam team = null;
            try (Scene scene = new Scene(helper, "kq-item-harm-" + row++)) {
                scene.owner.setInvulnerable(true);
                LivingEntity carrier = scene.cow;
                Entity extra = null;
                scene.cow.setPos(scene.owner.getX() + 8.0D, scene.owner.getY(), scene.owner.getZ() + 8.0D);
                ItemStack bomb = new ItemStack(Items.DIAMOND);
                CustomData.update(DataComponents.CUSTOM_DATA, bomb,
                        tag -> tag.putUUID(ItemBombService.OWNER_TAG, scene.owner.getUUID()));
                listener = event -> {
                    if (event.getLevel() == helper.getLevel()
                            && event.getExplosion().getDirectSourceEntity() == scene.owner) {
                        centers.add(event.getExplosion().center());
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Start.class, listener);
                try {
                    switch (kind) {
                        case "a Creative player" -> {
                            Player player = harmTargetPlayer(helper, scene, GameType.CREATIVE);
                            player.getInventory().setItem(0, bomb);
                            extra = carrier = player;
                        }
                        case "a player on Peaceful" -> {
                            Player player = harmTargetPlayer(helper, scene, GameType.SURVIVAL);
                            player.getInventory().setItem(0, bomb);
                            extra = carrier = player;
                            server.setDifficulty(Difficulty.PEACEFUL, true);
                        }
                        case "an invulnerable cow" -> {
                            scene.cow.setInvulnerable(true);
                            scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                            scene.cow.setItemSlot(EquipmentSlot.MAINHAND, bomb);
                        }
                        case "a cow with two bomb items" -> {
                            scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                            scene.cow.setItemSlot(EquipmentSlot.MAINHAND, bomb);
                            scene.cow.setItemSlot(EquipmentSlot.OFFHAND, bomb.copy());
                        }
                        case "a player with two bomb items" -> {
                            Player player = harmTargetPlayer(helper, scene, GameType.SURVIVAL);
                            player.getInventory().setItem(0, bomb);
                            player.getInventory().setItem(1, bomb.copy());
                            extra = carrier = player;
                        }
                        case "a teammate cow without friendly fire" -> {
                            scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                            scene.cow.setItemSlot(EquipmentSlot.MAINHAND, bomb);
                            net.minecraft.world.scores.Scoreboard board = server.getScoreboard();
                            team = board.addPlayerTeam("kq-item-bomb-team");
                            team.setAllowFriendlyFire(false);
                            board.addPlayerToTeam(scene.owner.getScoreboardName(), team);
                            board.addPlayerToTeam(scene.cow.getScoreboardName(), team);
                        }
                        default -> {
                            scene.cow.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D);
                            scene.cow.setItemSlot(EquipmentSlot.MAINHAND, bomb);
                        }
                    }
                    boolean refused = kind.startsWith("a teammate")
                            ? !rotp.core.util.functions.JojoModUtil.canHarm(scene.owner, carrier)
                            : scene.owner.canAttack(carrier);
                    if (!kind.equals("an ordinary cow") && !kind.contains("two bomb items") && !kind.startsWith("a teammate")
                            && refused) {
                        wrong.add(kind + ": fixture, 1.21's canAttack does not refuse it, so the row proves nothing");
                        continue;
                    }
                    if (kind.startsWith("a teammate") && !refused) {
                        wrong.add(kind + ": fixture, canHarm does not refuse a teammate, so the row proves nothing");
                        continue;
                    }
                    int detonated = ItemBombService.detonate(scene.owner, ItemBombService.LEGACY_RANGE);
                    // 1.16 exploded once for each slot of the carrier that held a bomb item
                    int expected = kind.contains("two bomb items") ? 2 : 1;
                    Vec3 carrierAt = carrier.position();
                    boolean blasted = centers.size() == expected
                            && centers.stream().allMatch(center -> center.distanceTo(carrierAt) < 1.0E-6D);
                    if (detonated != expected || !blasted) {
                        wrong.add(kind + ": 1.16 blew up the carrier of the item bomb " + expected + " time(s); the port "
                                + "detonated " + detonated + " and blew up " + centers + " (the carrier at "
                                + carrier.position() + ")");
                    }
                } finally {
                    server.setDifficulty(difficulty, true);
                    if (team != null) {
                        server.getScoreboard().removePlayerTeam(team);
                    }
                    if (extra != null) {
                        extra.discard();
                    }
                    NeoForge.EVENT_BUS.unregister(listener);
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen item bomb carried by a target: " + wrong);
        helper.succeed();
    }

    private static Player harmTargetPlayer(GameTestHelper helper, Scene scene, GameType mode) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "kq-harm-" + mode.getName()));
        player.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.0D, 0.0F, 0.0F);
        player.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(player), "fixture: the target player could not join the level");
        player.setGameMode(mode);
        return player;
    }

    /**
     * 1.16 MCUtil.canHarm ended in canAttack of the attacker: a plain true for a player, so Killer Queen's user plants the
     * primary bomb on a Creative player, but refused a spectator (no 1.16 caller met one), and a Mob's canAttack
     * (MobEntity) refused a player whose abilities make them invulnerable, a Creative or spectator player. The module
     * runs its bombs for players only (a Stand-bearing mob has no Killer Queen kit), so the mob rows ask the call the
     * module makes, JojoModUtil.canHarm, of a mob user directly.
     */
    @GameTest(template = "empty", batch = "kqBombUsersCanHarm", timeoutTicks = 100)
    public static void bombUsersFollowCanHarmForCreativeAndSpectatorTargetsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kq-user-harm")) {
            scene.cow.setPos(scene.owner.getX() + 8.0D, scene.owner.getY(), scene.owner.getZ());
            for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
                Player target = harmTargetPlayer(helper, scene, mode);
                try {
                    KillerQueenStateService.clearPrimaryBomb(scene.owner);
                    EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.PRIMARY_BOMB_NAME, new ActionTarget(target));
                    scene.stand.updatePosition(scene.owner);
                    if (action == null) {
                        wrong.add("a player user, a " + mode.getName() + " player: fixture, " + scene.fixtureFailure);
                        continue;
                    }
                    for (int tick = 0; tick < 200 && !action.isOver(); tick++) {
                        action._tickAction();
                    }
                    boolean planted = KillerQueenStateService.primaryBombTarget(scene.owner) == target;
                    if (planted != (mode == GameType.CREATIVE)) {
                        wrong.add("a player user's bomb " + (planted ? "was" : "was not") + " planted on a " + mode.getName()
                                + " player; 1.16 " + (mode == GameType.CREATIVE ? "planted it (canAttack a plain true)"
                                        : "never met a spectator, and the port refuses one to every user but a Stand"));
                    }
                } finally {
                    target.discard();
                }
            }
            net.minecraft.world.entity.monster.Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
            try {
                for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR, GameType.SURVIVAL)) {
                    Player target = harmTargetPlayer(helper, scene, mode);
                    try {
                        boolean harms = rotp.core.util.functions.JojoModUtil.canHarm(mob, target);
                        if (harms != (mode == GameType.SURVIVAL)) {
                            wrong.add("a mob user's bomb " + (harms ? "takes" : "refuses") + " a " + mode.getName()
                                    + " player; 1.16 MobEntity.canAttack "
                                    + (mode == GameType.SURVIVAL ? "took" : "refused") + " it");
                        }
                    } finally {
                        target.discard();
                    }
                }
            } finally {
                mob.discard();
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen bombs by the kind of user: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 PunchBomb.onTaskSet :90-97 (100 action/stand/punch/PunchBomb, action/stand/EntityExplode): an arms-only Stand whose last swing was the off hand was set to its
     * main arm alone (setArmsOnlyMode(true, false)) when kq_entitybomb began; after a main-hand swing, or with the
     * whole Stand out, it left the arms as they were. (It is not a light-attack task that alternates the hands.)
     */
    @GameTest(template = "empty", batch = "kqlBombMainArm", timeoutTicks = 40)
    public static void entityBombShowsTheMainArmAloneAfterAnOffHandSwingAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-bomb-main-arm")) {
            KillerQueenStateService.clearPrimaryBomb(scene.owner);
            // what is out (arms only, its off arm too), the last swing, whether both arms stay out
            Object[][] rows = {
                    { "both arms out after an off-hand swing", true, true, InteractionHand.OFF_HAND, false },
                    { "both arms out after a main-hand swing", true, true, InteractionHand.MAIN_HAND, true },
                    { "the main arm out after an off-hand swing", true, false, InteractionHand.OFF_HAND, false },
                    { "the whole Stand out after an off-hand swing", false, false, InteractionHand.OFF_HAND, false } };
            for (Object[] row : rows) {
                boolean armsOnly = (Boolean) row[1];
                scene.stand.setArmsOnlyMode(false);
                if (armsOnly) {
                    scene.stand.setArmsOnlyMode(true, (Boolean) row[2]);
                }
                scene.stand.applySyncedPunchingHand((InteractionHand) row[3]);
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.PRIMARY_BOMB_NAME,
                        new ActionTarget(scene.cow));
                if (action == null) {
                    wrong.add(row[0] + ": " + scene.fixtureFailure);
                    continue;
                }
                boolean bothArms = scene.stand.isArmsOnlyMode() && scene.stand.showArm(InteractionHand.MAIN_HAND)
                        && scene.stand.showArm(InteractionHand.OFF_HAND);
                if (scene.stand.isArmsOnlyMode() != armsOnly
                        || armsOnly && (bothArms != (Boolean) row[4] || !scene.stand.showArm(InteractionHand.MAIN_HAND))) {
                    wrong.add(row[0] + ": 1.16 " + (!armsOnly ? "kept the whole Stand" : (Boolean) row[4]
                            ? "kept both arms" : "showed the main arm alone") + " as kq_entitybomb began; the port "
                            + Scene.arms(scene.stand));
                }
                scene.clearStandAction();
            }
            scene.stand.setArmsOnlyMode(false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entitybomb's arms after an off-hand swing: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 Lion's kq_block_explode and kq_bubble_block_explode were both ExplodeBlockAction (100 InitStands :89, :97),
     * whose standPerform blew up every stored bubble mark and then the Stand's block bomb, whichever of the two was
     * pressed: both blocks set to air, a blast at each block's integer corner, no mark and no bomb left.
     */
    @GameTest(template = "empty", batch = "kqlBlockDetonationsTogether", timeoutTicks = 60)
    public static void bothBlockDetonationsBlowUpTheBubbleMarksAndTheBlockBombAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        BlockPos mark = helper.absolutePos(new BlockPos(1, 3, -5));
        BlockPos bomb = helper.absolutePos(new BlockPos(4, 3, -5));
        List<String> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = null;
        try (Scene scene = new Scene(helper, "kql-block-detonations")) {
            scene.owner.setInvulnerable(true);
            listener = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getExplosion().getDirectSourceEntity() == scene.owner) {
                    blasts.add(blast(event.getExplosion().center(), event.getExplosion().radius()));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Start.class, listener);
            float radius = KillerQueenStateService.blockBlastRadius();
            for (String name : List.of(KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME,
                    KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME)) {
                helper.getLevel().setBlockAndUpdate(mark, Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(bomb, Blocks.STONE.defaultBlockState());
                helper.assertTrue(KillerQueenStateService.markBubbleBlock(scene.owner, mark)
                                && KillerQueenStateService.markBlockBomb(scene.owner, bomb),
                        "fixture: the bubble mark or the block bomb was rejected");
                scene.power.setStamina(scene.power.getMaxStamina());
                EntityActionInstance action = scene.setOnStand(name, ActionTarget.EMPTY);
                if (action == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                blasts.clear();
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                List<String> expected = List.of(blast(Vec3.atLowerCornerOf(mark), radius),
                        blast(Vec3.atLowerCornerOf(bomb), radius));
                boolean markGone = helper.getLevel().getBlockState(mark).isAir();
                boolean bombGone = helper.getLevel().getBlockState(bomb).isAir();
                boolean marksLeft = KillerQueenStateService.hasBubbleBlockMarks(scene.owner);
                boolean bombLeft = scene.state.hasBlockBomb();
                if (!blasts.equals(expected) || !markGone || !bombGone || marksLeft || bombLeft) {
                    wrong.add(name + ": 1.16 removed the marked and the bombed block, exploded " + expected
                            + " and left no mark and no bomb; the port " + (markGone ? "removed" : "kept")
                            + " the marked block, " + (bombGone ? "removed" : "kept") + " the bombed block, exploded "
                            + blasts + ", marks left " + marksLeft + ", bomb left " + bombLeft);
                }
                scene.clearStandAction();
                KillerQueenStateService.clearBlockBomb(scene.owner);
                scene.state.clearBubbleBlockMarks();
            }
            scene.owner.setInvulnerable(false);
        } finally {
            if (listener != null) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            helper.getLevel().setBlockAndUpdate(mark, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(bomb, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen Lion block detonations: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 Lion's ExplodeBlockAction.standPerform (100 :89, :97) set a marked block to air only when its explosion
     * resistance was below 3600000, as for the block bomb: a nether portal (no hardness, no resistance) went, bedrock
     * stayed.
     */
    @GameTest(template = "empty", batch = "kqlBubbleMarkResistance", timeoutTicks = 60)
    public static void bubbleMarksFollowTheExplosionResistanceLimitAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        BlockPos mark = helper.absolutePos(new BlockPos(1, 3, -5));
        try (Scene scene = new Scene(helper, "kql-bubble-resistance")) {
            scene.owner.setInvulnerable(true);
            // the marked block, whether 1.16 removed it
            Object[][] rows = {
                    { Blocks.STONE, true },
                    { Blocks.NETHER_PORTAL, true },
                    { Blocks.BEDROCK, false } };
            for (Object[] row : rows) {
                Block kind = (Block) row[0];
                boolean removed = (Boolean) row[1];
                helper.getLevel().setBlock(mark, kind.defaultBlockState(), Block.UPDATE_CLIENTS);
                helper.assertTrue(KillerQueenStateService.markBubbleBlock(scene.owner, mark),
                        "fixture: the bubble mark was rejected");
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME,
                        ActionTarget.EMPTY);
                if (action == null) {
                    wrong.add(kind.getDescriptionId() + ": " + scene.fixtureFailure);
                    continue;
                }
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                boolean air = helper.getLevel().getBlockState(mark).isAir();
                if (air != removed) {
                    wrong.add(kind.getDescriptionId() + ": 1.16 " + (removed ? "removed" : "kept")
                            + " the marked block; the port " + (air ? "removed" : "kept") + " it");
                }
                helper.getLevel().setBlock(mark, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                scene.clearStandAction();
            }
            scene.owner.setInvulnerable(false);
        } finally {
            helper.getLevel().setBlockAndUpdate(mark, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen Lion bubble marks' resistance limit: " + wrong);
        helper.succeed();
    }

    // kq_entitybomb (PunchBomb) kept the light attack's swing as well
    private static List<String> swingBombMoves() {
        List<String> moves = new ArrayList<>(FRONT_BOMB_MOVES);
        moves.add(0, "kq_entitybomb");
        return moves;
    }

    private static String blast(Vec3 center, float radius) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f) radius %.3f", center.x, center.y, center.z,
                radius);
    }

    private static final List<String> LIGHT_BOMB_MOVES = List.of("kq_itembomb", "kq_entitybomb", "kq_item_explo",
            "kq_entity_explo", "kq_block_explode", "kq_block_quit", "snow_explode", "kq_bubble_block_explode");
    private static final List<String> NO_RECOVERY_MOVES = List.of("kq_block_bomb", "sheer_heart", "shear_heart_back",
            "kq_snow_bomb", "kq_bubble_mark");

    /**
     * 1.16 InitStands (020 :82-94, 100 :83-97): the bomb moves built on StandEntityLightAttack (kq_itembomb, kq_entitybomb,
     * kq_item_explo, kq_entity_explo, kq_block_explode, kq_block_quit, snow_explode, Lion's kq_bubble_block_explode) took
     * its formula lengths whatever their builders set (getStandWindupTicks :99-105, getStandActionTicks :107-111,
     * getStandRecoveryTicks :113-117; PunchBomb.getStandWindupTicks :99-102 the heavy windup; EntityExplode after its
     * hold not halved as a first press), and any action, the same one too, replaced them in their recovery
     * (isCancelable :127-129, isChainable, isFreeRecovery). kq_block_bomb, sheer_heart and shear_heart_back had no
     * standRecoveryTicks; kq_snow_bomb (and Lion's kq_bubble_mark) were StandActions with no Stand task at all.
     */
    @GameTest(template = "empty", batch = "kqBombMoveTimings", timeoutTicks = 80)
    public static void bombMovesTakeTheDonorTimingsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kq-bomb-timings")) {
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            component.setAction(null, SyncType.NO_SYNC);
            scene.stand.setFinisherMeter(0.0F);
            double speed = scene.stand.getAttackSpeed();
            double light = (24.0D - speed) / 4.0D;
            if (light > 2.0D) {
                light = Math.max(light, 2.0D);
            }
            float perform = lightPerform116(speed);
            for (String name : LIGHT_BOMB_MOVES) {
                Ability ability = scene.power.getAbility(name);
                if (!(ability instanceof EntityActionType type)) {
                    wrong.add(name + " is missing");
                    continue;
                }
                EntityActionInstance action = type.initActionOnAbilityUse(helper.getLevel(), scene.owner, scene.stand, null);
                float windup = action.phasesLength.getFloat(ActionPhase.WINDUP);
                boolean windupOk;
                if (name.equals("kq_entitybomb")) {
                    windupOk = windup == heavyWindup116(speed);
                }
                else {
                    double value = name.equals("kq_entity_explo") ? light : light / 2.0D;
                    windupOk = windup == (float) Math.floor(value) || windup == (float) Math.ceil(value);
                }
                if (!windupOk || action.phasesLength.getFloat(ActionPhase.PERFORM) != perform
                        || action.phasesLength.getFloat(ActionPhase.RECOVERY) != perform * 4) {
                    wrong.add(name + " ran windup " + windup + ", perform "
                            + action.phasesLength.getFloat(ActionPhase.PERFORM) + ", recovery "
                            + action.phasesLength.getFloat(ActionPhase.RECOVERY) + " (1.16: perform " + perform
                            + ", recovery " + perform * 4 + ")");
                }
                component.setAction(action, scene.owner, SyncType.NO_SYNC);
                action.setPhase(ActionPhase.RECOVERY, 0);
                if (!action.canBeCancelledInto(type)) {
                    wrong.add(name + " held its own press back in its recovery");
                }
                component.setAction(null, SyncType.NO_SYNC);
            }
            for (String name : NO_RECOVERY_MOVES) {
                Ability ability = scene.power.getAbility(name);
                if (!(ability instanceof EntityActionType type)) {
                    wrong.add(name + " is missing");
                    continue;
                }
                EntityActionInstance action = type.initActionOnAbilityUse(helper.getLevel(), scene.owner, scene.stand, null);
                float windup = action.phasesLength.getFloat(ActionPhase.WINDUP);
                float expectedWindup = name.equals("kq_block_bomb") ? 5.0F : 0.0F;
                if (windup != expectedWindup || action.phasesLength.getFloat(ActionPhase.RECOVERY) != 0.0F) {
                    wrong.add(name + " ran windup " + windup + ", recovery "
                            + action.phasesLength.getFloat(ActionPhase.RECOVERY) + " (1.16: windup " + expectedWindup
                            + ", no recovery)");
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen bomb move timings: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 PowerBaseImpl.checkTarget gave a Stand action the client's mouse target only if StandEntityAction.checkTarget
     * (its target requirement, else standKeepsTarget) and checkStandTarget (a living entity the Stand may attack) took
     * it, and the Stand turned to its task's target alone. BlockBombAction kept a block only (standKeepsTarget),
     * SheerHeartAttack nothing (no standKeepsTarget), so its standPerform never gave the bomb the aimed mob; ItemFBomb and
     * PunchBomb, light attacks, kept any target (StandEntityLightAttack.standKeepsTarget :165-167).
     */
    @GameTest(template = "empty", batch = "kqPressedTargets", timeoutTicks = 40)
    public static void pressedTargetsFollowTheDonorStandTargetRulesAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-pressed-targets")) {
            ServerLevel level = helper.getLevel();
            BlockPos block = helper.absolutePos(new BlockPos(3, 2, 3));
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            ActionTarget cow = new ActionTarget(scene.cow);
            ActionTarget stone = new ActionTarget(block, Direction.UP);
            Object[][] rows = {
                    { KillerQueenLionAddon.BLOCK_BOMB_NAME, cow, null },
                    { KillerQueenLionAddon.BLOCK_BOMB_NAME, stone, stone },
                    { KillerQueenLionAddon.ITEM_BOMB_NAME, cow, cow },
                    { KillerQueenLionAddon.PRIMARY_BOMB_NAME, cow, cow } };
            for (Object[] row : rows) {
                String name = (String) row[0];
                ActionTarget aimed = (ActionTarget) row[1];
                ActionTarget turned = (ActionTarget) row[2];
                EntityActionInstance action = scene.setOnStand(name, aimed);
                if (action == null) {
                    wrong.add(name + ": " + scene.fixtureFailure);
                    continue;
                }
                ActionTarget rotation = action.standRotationTarget;
                boolean empty = rotation == null || rotation.getType() == ActionTarget.TargetType.EMPTY;
                if (turned == null ? !empty : empty || !rotation.sameTarget(turned)) {
                    wrong.add(name + " pressed at " + aimed.getType() + ": 1.16 turned the Stand to "
                            + (turned == null ? "nothing" : turned.getType()) + ", the port to " + rotation);
                }
            }
            EntityActionInstance summon = scene.setOnStand(KillerQueenLionAddon.SHEER_HEART_NAME, cow);
            if (summon == null) {
                wrong.add("sheer_heart: " + scene.fixtureFailure);
            } else {
                summon.setPhaseStart(ActionPhase.PERFORM);
                summon._tickAction();
                List<SheerHeartAttackEntity> bombs = level.getEntitiesOfClass(SheerHeartAttackEntity.class,
                        scene.owner.getBoundingBox().inflate(20.0D));
                if (bombs.isEmpty()) {
                    wrong.add("sheer_heart sent no bomb");
                }
                if (bombs.stream().anyMatch(bomb -> bomb.getTarget() != null)) {
                    wrong.add("sheer_heart pressed at the cow: 1.16 gave its bombs no target (SheerHeartAttack kept "
                            + "none), the port gave " + bombs.stream().map(bomb -> bomb.getTarget()).toList());
                }
                KillerQueenStateService.recallSheerHeart(scene.owner);
                bombs.forEach(SheerHeartAttackEntity::discard);
            }
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Killer Queen pressed targets (checkStandTarget, standKeepsTarget): "
                + wrong);
        helper.succeed();
    }

    /**
     * 1.16 PowerBaseImpl.checkTarget ran StandEntityAction.overrideVanillaMouseTarget on a press before its target
     * requirement: kq_entitybomb (PunchBomb, ENTITY) takes an entity, so a pressed block without a collision shape gave
     * way to what the performer's collision ray met (JojoModUtil.rayTraceMultipleEntities from the summoned Stand's eyes,
     * BlockMode.COLLIDER, its reach). Pressed at a torch with a cow behind it, it took the cow; with nothing behind the
     * torch the target was empty and the press refused without a message.
     */
    @GameTest(template = "empty", batch = "kqlPressedTorchTorchEntityBomb", timeoutTicks = 20)
    public static void entityBombPressedAtATorchTakesTheMobBehindAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-torch-entity-bomb")) {
            BlockPos torch = pressedTorchScene(scene);
            try {
                ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);
                Ability bomb = scene.power.getAbility(KillerQueenLionAddon.PRIMARY_BOMB_NAME);
                helper.assertTrue(bomb != null, "fixture: no entity bomb");
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(pressedAt);
                scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                        scene.owner.getZ() + 2.0D);
                ConditionCheck behind = bomb.checkSpecificConditions(scene.power);
                if (!behind.isPositive()) {
                    wrong.add("pressed at a torch with a cow behind it: 1.16 took the cow, the port refused the press ("
                            + (behind.getWarning() != null ? behind.getWarning().getString() : "no message") + ")");
                }
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.PRIMARY_BOMB_NAME, pressedAt);
                if (action == null) {
                    wrong.add("fixture: " + scene.fixtureFailure);
                } else {
                    ActionTarget held = action.getTaskTarget(helper.getLevel());
                    if (held.getType() != ActionTarget.TargetType.ENTITY || held.getEntity() != scene.cow) {
                        wrong.add("pressed at a torch with a cow behind it: 1.16's task held the cow, the port's " + held);
                    }
                    LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                }
                scene.cow.setPos(scene.owner.getX() + 3.0D, scene.owner.getY(), scene.owner.getZ());
                ConditionCheck nothing = bomb.checkSpecificConditions(scene.power);
                if (nothing.isPositive() || nothing.getWarning() != null) {
                    wrong.add("pressed at a torch with nothing behind it: 1.16 refused it without a message, the port "
                            + (nothing.isPositive() ? "took it" : "said " + nothing.getWarning().getString()));
                }
            } finally {
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
                clearPressedTorchScene(scene, torch);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entitybomb pressed at a block without a collision shape "
                + "(overrideVanillaMouseTarget): " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 BlockBombAction, a StandEntityAction whose target requirement (NONE) takes an entity: a pressed torch gave way
     * for the task's target (overrideVanillaMouseTarget), so the Stand turned to the stone behind it that its collision
     * ray met (standKeepsTarget: a block). Its standPerform cast a ray of its own from the user's eyes by block outlines
     * (JojoModUtil.rayTrace, BlockMode.OUTLINE), which met the torch: the bomb went on the torch.
     */
    @GameTest(template = "empty", batch = "kqlPressedTorchTorchBlockBomb", timeoutTicks = 20)
    public static void blockBombPressedAtATorchBombsTheTorchAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-torch-block-bomb")) {
            BlockPos torch = pressedTorchScene(scene);
            BlockPos stone = torch.south();
            try {
                helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.BLOCK_BOMB_NAME,
                        new ActionTarget(torch, Direction.NORTH));
                helper.assertTrue(action != null, "fixture: " + scene.fixtureFailure);
                ActionTarget turned = action.standRotationTarget != null ? action.standRotationTarget
                        : ActionTarget.EMPTY;
                ActionTarget held = action.getTaskTarget(helper.getLevel());
                if (turned.getType() != ActionTarget.TargetType.BLOCK || !stone.equals(turned.getBlockPos())
                        || held.getType() != ActionTarget.TargetType.BLOCK || !stone.equals(held.getBlockPos())) {
                    wrong.add("1.16 turned the Stand to the stone behind the torch and its task held it, the port turned"
                            + " it to " + turned + " and holds " + held);
                }
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                BlockPos bombed = KillerQueenStateService.blockBombPos(scene.owner);
                if (!torch.equals(bombed)) {
                    wrong.add("1.16's perform put the bomb on the torch, the port on " + bombed + " (torch " + torch
                            + ", stone " + stone + ")");
                }
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            } finally {
                KillerQueenStateService.clearBlockBomb(scene.owner);
                clearPressedTorchScene(scene, torch);
                helper.getLevel().setBlock(stone, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_block_bomb pressed at a torch: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 RemoveTimeMarker and SelectHayato (remov_mark, put_dust) were StandActions, which had no
     * overrideVanillaMouseTarget: a pressed torch stayed the mouse target. RemoveTimeMarker.replaceAction (100 :43-48)
     * offered put_dust only for a living entity, so shift + time_mark at a torch with a cow behind it stayed remov_mark
     * and its press removed the time mark; SelectHayato.checkSpecificConditions (:50-63) refused a block.
     */
    @GameTest(template = "empty", batch = "kqlPressedTorchBtd", timeoutTicks = 20)
    public static void bitesTheDustVictimPressedAtATorchStaysTheTorchAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-torch-btd")) {
            BlockPos torch = pressedTorchScene(scene);
            try {
                scene.awakenAndMarkTime();
                scene.owner.setShiftKeyDown(true);
                scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                        scene.owner.getZ() + 3.0D);
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
                helper.assertTrue(BitesTheDustService.targetedVictim(scene.owner) == scene.cow,
                        "fixture: with nothing aimed the Stand's precision ray does not reach the cow behind the torch");
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(
                        new ActionTarget(torch, Direction.NORTH));
                LivingEntity victim = BitesTheDustService.targetedVictim(scene.owner);
                if (victim != null) {
                    wrong.add("aimed at a torch with a cow behind it: 1.16 kept the torch, no victim; the port took "
                            + victim);
                }
                Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
                Ability putDust = scene.power.getAbility(KillerQueenLionAddon.PUT_DUST_NAME);
                helper.assertTrue(root != null && putDust != null, "fixture: no time_mark or put_dust");
                ConditionCheck put = putDust.checkSpecificConditions(scene.power);
                if (put.isPositive()) {
                    wrong.add("put_dust pressed at the torch: 1.16 SelectHayato refused a block, the port took it");
                }
                AvailableAbilities available = new AvailableAbilities();
                available.update(scene.power, scene.power.getMoveset());
                AbilityConditionCheck selected = available.getContextVariationContainer(root);
                if (selected == null || !selected.ability.name().equals(KillerQueenLionAddon.REMOVE_MARK_NAME)) {
                    wrong.add("shift + time_mark at the torch: 1.16 showed remov_mark, the port "
                            + (selected != null ? selected.ability.name() : "nothing"));
                } else {
                    BufferingState press = BufferingState.clickCanBuffer();
                    if (AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
                        AbilityInput.keyPress((short) 431, selected.ability, scene.owner, null, InputMethod.CLICK, 0,
                                press, selected.ability.getAbilityId());
                    }
                    AbilityInput.keyRelease((short) 431, scene.owner);
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner,
                            PowerClass.STAND);
                    for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
                        action._tickAction();
                    }
                    if (!press.isActionSuccess || scene.state.hasBitesTheDustMarker()
                            || scene.state.hasBitesTheDustVictim()) {
                        wrong.add("its press: 1.16 removed the time mark and chose no victim; the port started "
                                + press.isActionSuccess + ", marker " + scene.state.hasBitesTheDustMarker()
                                + ", victim " + scene.state.hasBitesTheDustVictim() + " " + scene.owner.keys());
                    }
                }
            } finally {
                LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
                scene.owner.setShiftKeyDown(false);
                clearPressedTorchScene(scene, torch);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Bites the Dust victim pressed at a torch (no "
                + "overrideVanillaMouseTarget for a StandAction): " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 SelectHayato (put_dust, 100 action/stand/BitesZaDust) checked and performed on one target, the mouse target
     * (InputHandler.pickMouseTarget): the vanilla pick and, when that met nothing with the Stand out, the Stand's
     * precision ray from its user's eyes at its user's reach (EntityStandType.clientHitResult :122-151); with the Stand
     * away nothing past the vanilla pick; an entity the server could not find emptied it (PowerBaseImpl.checkTarget).
     * RemoveTimeMarker.replaceAction (:43-48) showed put_dust for a living entity there, else remov_mark.
     */
    @GameTest(template = "empty", batch = "kqlBtdVictimTarget", timeoutTicks = 40)
    public static void bitesTheDustVictimIsTheOneTargetOfItsCheckAndPerformAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-btd-victim-target")) {
            scene.awakenAndMarkTime();
            short key = 461;
            victimRow(scene, "Killer Queen out, nothing under the crosshair, the cow 4.5 blocks ahead in the Stand's "
                    + "precision ray", true, 4.5D, null, false, true, key++, wrong);
            victimRow(scene, "Killer Queen away, nothing under the crosshair, the cow 6 blocks ahead", false, 6.0D, null,
                    false, false, key++, wrong);
            victimRow(scene, "Killer Queen out, nothing under the crosshair, the cow 7.5 blocks ahead, past the Stand's "
                    + "precision ray", true, 7.5D, null, false, false, key++, wrong);
            victimRow(scene, "Killer Queen out, the cow under the crosshair 2.5 blocks ahead", true, 2.5D,
                    new ActionTarget(scene.cow), false, true, key++, wrong);
            victimRow(scene, "Killer Queen out, the client aimed at an entity the server does not have, the cow 4.5 "
                    + "blocks ahead in the Stand's precision ray", true, 4.5D, null, true, false, key++, wrong);
            scene.owner.setShiftKeyDown(false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 put_dust's one target: " + wrong);
        helper.succeed();
    }

    // shift + the time_mark slot with the time marked and this aim; put_dust's check and, when it is offered, its real
    // press and perform
    private static void victimRow(Scene scene, String row, boolean standOut, double cowAhead, ActionTarget aimed,
            boolean aimedEntityMissing, boolean selects, short key, List<String> wrong) {
        btdStand(scene, standOut);
        BitesTheDustService.removeVictim(scene.owner);
        if (!scene.state.hasBitesTheDustMarker()) {
            scene.helper.assertTrue(BitesTheDustService.markTime(scene.owner), "fixture: the time could not be marked");
        }
        scene.owner.setShiftKeyDown(true);
        scene.turn(0.0F);
        scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                scene.owner.getZ() + cowAhead);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(aimed != null ? aimed : ActionTarget.EMPTY,
                aimedEntityMissing);
        scene.owner.messages.clear();
        Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
        Ability putDust = scene.power.getAbility(KillerQueenLionAddon.PUT_DUST_NAME);
        AvailableAbilities available = new AvailableAbilities();
        available.update(scene.power, scene.power.getMoveset());
        AbilityConditionCheck selected = available.getContextVariationContainer(root);
        String slot = selected != null ? selected.ability.name() : "nothing";
        boolean checked = putDust.checkSpecificConditions(scene.power).isPositive();
        BufferingState state = BufferingState.clickCanBuffer();
        if (selected != null && selected.ability == putDust
                && AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, selected.ability, scene.owner, null, InputMethod.CLICK, 0, state,
                    root.getAbilityId());
        }
        AbilityInput.keyRelease(key, scene.owner);
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
        for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
            action._tickAction();
        }
        boolean chosen = scene.cow.getUUID().equals(scene.state.bitesTheDustVictimUuid());
        String expectedSlot = selects ? KillerQueenLionAddon.PUT_DUST_NAME : KillerQueenLionAddon.REMOVE_MARK_NAME;
        if (!slot.equals(expectedSlot) || checked != selects || state.isActionSuccess != selects || chosen != selects) {
            wrong.add(row + ": 1.16 showed " + expectedSlot + (selects ? " and its press chose the cow" : ", put_dust "
                    + "refused and nothing chosen") + "; the port showed " + slot + ", put_dust's check " + checked
                    + ", press started " + state.isActionSuccess + ", the cow chosen " + chosen + " "
                    + scene.owner.keys());
        }
        LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
        BitesTheDustService.removeVictim(scene.owner);
    }

    /**
     * 1.16 RemoveTimeMarker.replaceAction (100 :43-48) offered put_dust for any living entity under the mouse target, and
     * SelectHayato.checkSpecificConditions (:50-63) refused the press with its own messages: an entity some user already
     * chose 'bites_za_has' (:53-55), a Stand user 'is_stand_user' (:56-60). The time mark stays.
     */
    @GameTest(template = "empty", batch = "kqlBtdVictimRefusals", timeoutTicks = 40)
    public static void bitesTheDustVictimPressFollowsSelectHayatosRefusalsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        try (Scene scene = new Scene(helper, "kql-btd-victim-refusals")) {
            RecordingPlayer other = new RecordingPlayer(level, new GameProfile(UUID.randomUUID(), "kql-btd-other-user"));
            other.setPos(scene.owner.getX() + 3.0D, scene.owner.getY(), scene.owner.getZ());
            level.addNewPlayer(other);
            StandPower otherPower = PowerClass.STAND.attachGet(other);
            try {
                otherPower.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
                scene.awakenAndMarkTime();
                Vec3 ahead = new Vec3(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + 2.5D);
                other.setPos(ahead);
                refusalRow(scene, "aimed at a Stand user", other, "jojo.message.action_condition.is_stand_user",
                        (short) 471, wrong);
                other.setPos(scene.owner.getX() + 3.0D, scene.owner.getY(), scene.owner.getZ());
                scene.cow.setPos(ahead);
                helper.assertTrue(BitesTheDustService.markTime(other)
                                && BitesTheDustService.selectVictim(other, scene.cow),
                        "fixture: the other user could not choose the cow");
                refusalRow(scene, "aimed at a cow another user chose", scene.cow,
                        "jojo.message.action_condition.bites_za_has", (short) 473, wrong);
            } finally {
                BitesTheDustService.removeTimeMarker(other);
                if (otherPower.isSummoned()) {
                    KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(other, otherPower);
                }
                EventHooks.firePlayerLoggedOut(other);
                other.discard();
                scene.owner.setShiftKeyDown(false);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 SelectHayato's refusals: " + wrong);
        helper.succeed();
    }

    // shift + the time_mark slot aimed at this entity with the time marked: put_dust offered, its real press refused
    // with this condition message, the mark kept
    private static void refusalRow(Scene scene, String row, LivingEntity aimed, String message, short key,
            List<String> wrong) {
        scene.owner.setShiftKeyDown(true);
        scene.turn(0.0F);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(new ActionTarget(aimed));
        scene.owner.messages.clear();
        Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
        AvailableAbilities available = new AvailableAbilities();
        available.update(scene.power, scene.power.getMoveset());
        AbilityConditionCheck selected = available.getContextVariationContainer(root);
        String slot = selected != null ? selected.ability.name() : "nothing";
        BufferingState state = BufferingState.clickCanBuffer();
        if (selected != null && slot.equals(KillerQueenLionAddon.PUT_DUST_NAME)
                && AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, selected.ability, scene.owner, null, InputMethod.CLICK, 0, state,
                    root.getAbilityId());
        }
        AbilityInput.keyRelease(key, scene.owner);
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
        for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
            action._tickAction();
        }
        if (!slot.equals(KillerQueenLionAddon.PUT_DUST_NAME) || state.isActionSuccess
                || !scene.owner.keys().equals(List.of(message)) || !scene.state.hasBitesTheDustMarker()
                || scene.state.hasBitesTheDustVictim()) {
            wrong.add(row + ": 1.16 showed put_dust, refused its press with " + message + " and kept the mark; the "
                    + "port showed " + slot + ", started " + state.isActionSuccess + ", messages " + scene.owner.keys()
                    + ", marker " + scene.state.hasBitesTheDustMarker() + ", victim "
                    + scene.state.hasBitesTheDustVictim());
        }
        LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
    }

    /**
     * 1.16 SelectHayato (put_dust, 100 action/stand/BitesZaDust :50-75) took its target through Action.checkRangeAndTarget (1.16 core :162-203, run by
     * PowerBaseImpl.checkTarget :304): within 8 blocks of its performer, the summoned Stand, else the user
     * (StandAction.getPerformer), measured to the target's box (JojoModUtil.getDistance), 4 when the performer could not
     * see the target; a farther target was emptied and the press refused. RemoveTimeMarker.replaceAction showed put_dust
     * for it all the same.
     */
    @GameTest(template = "empty", batch = "kqlBtdVictimRange", timeoutTicks = 40)
    public static void bitesTheDustVictimRangeIsMeasuredFromThePerformerAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        try (Scene scene = new Scene(helper, "kql-btd-victim-range")) {
            scene.awakenAndMarkTime();
            BlockPos wall = BlockPos.containing(scene.owner.getX(), scene.owner.getEyeY(), scene.owner.getZ() + 1.5D);
            List<BlockPos> stones = List.of(wall.below(), wall, wall.above());
            short key = 481;
            try {
                stones.forEach(stone -> level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState()));
                rangeRow(scene, "Killer Queen out at its user's feet, the cow 6 blocks ahead behind a wall", true, 0.0D,
                        6.0D, false, key++, wrong);
                rangeRow(scene, "Killer Queen out at its user's feet, the cow 3 blocks ahead behind a wall", true, 0.0D,
                        3.0D, true, key++, wrong);
                rangeRow(scene, "Killer Queen away, the cow 3 blocks ahead behind a wall", false, 0.0D, 3.0D, true,
                        key++, wrong);
            } finally {
                stones.forEach(stone -> level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState()));
            }
            rangeRow(scene, "Killer Queen 5 blocks ahead of its user, the cow 11 blocks ahead, 6 from the Stand", true,
                    5.0D, 11.0D, true, key++, wrong);
            rangeRow(scene, "Killer Queen 5 blocks behind its user, the cow 4.5 blocks ahead, 9.5 from the Stand",
                    true, -5.0D, 4.5D, false, key++, wrong);
            scene.owner.setShiftKeyDown(false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 put_dust's range (Action.checkRangeAndTarget): " + wrong);
        helper.succeed();
    }

    // The cow straight ahead under the user's crosshair (the synced aim), Killer Queen out this far ahead of its user
    // or away
    private static void rangeRow(Scene scene, String row, boolean standOut, double standAhead, double cowAhead,
            boolean selects, short key, List<String> wrong) {
        StandEntity stand = btdStand(scene, standOut);
        if (stand != null) {
            stand.setCanFollowUser(false);
            stand.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ() + standAhead);
        }
        scene.turn(0.0F);
        scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                scene.owner.getZ() + cowAhead);
        putDustRow(scene, row, new ActionTarget(scene.cow), true, selects, key, wrong);
        if (stand != null) {
            stand.setCanFollowUser(true);
        }
        btdStand(scene, false);
    }

    /**
     * 1.16's mouse target for put_dust (InputHandler.pickMouseTarget, EntityStandType.clientHitResult :122-151): when the
     * vanilla pick met nothing with the Stand out, the Stand's precision ray from the camera, which under manual control
     * is the Stand, at the Stand's own reach (StandEntity.getAimDistance :1709-1718). The port's aim assist setting
     * (owner: follow it) keeps that ray with the assist on and takes the crosshair line alone, by the entities' own
     * boxes, with it off.
     */
    @GameTest(template = "empty", batch = "kqlBtdVictimAim", timeoutTicks = 40)
    public static void bitesTheDustVictimAimFollowsManualControlAndTheAimAssistAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-btd-victim-aim")) {
            PlayerClientBroadcastedSettings settings =
                    scene.owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
            scene.awakenAndMarkTime();
            short key = 491;
            try {
                StandEntity stand = btdStand(scene, true);
                stand.setCanFollowUser(false);
                stand.setManuallyControlled(true);
                helper.assertTrue(stand.isManuallyControlled(), "fixture: Killer Queen is not manually controlled");
                stand.moveTo(scene.owner.getX() + 3.0D, scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
                scene.turn(180.0F);
                scene.cow.setPos(stand.getX(), stand.getEyeY() - scene.cow.getBbHeight() / 2.0D, stand.getZ() + 2.0D);
                putDustRow(scene, "Killer Queen under manual control, looking at the cow 2 blocks ahead of it, its user "
                        + "turned away, nothing under the crosshair", ActionTarget.EMPTY, true, true, key++, wrong);
                if (scene.power.isSummoned()) {
                    stand.setManuallyControlled(false);
                    stand.setCanFollowUser(true);
                }
                btdStand(scene, false);
                for (boolean assist : new boolean[] { true, false }) {
                    settings.standAimAssist = assist;
                    btdStand(scene, true);
                    scene.turn(0.0F);
                    scene.cow.setPos(scene.owner.getX() + 0.9D,
                            scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D, scene.owner.getZ() + 3.0D);
                    putDustRow(scene, "aim assist " + (assist ? "on" : "off") + ", Killer Queen out, nothing under the "
                            + "crosshair, the cow 3 blocks ahead and 0.9 aside", ActionTarget.EMPTY, assist, assist,
                            key++, wrong);
                }
                btdStand(scene, true);
                scene.turn(0.0F);
                scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                        scene.owner.getZ() + 3.0D);
                putDustRow(scene, "aim assist off, Killer Queen out, nothing synced, the cow 3 blocks ahead on the "
                        + "crosshair line", ActionTarget.EMPTY, true, true, key++, wrong);
            } finally {
                settings.standAimAssist = true;
                scene.owner.setShiftKeyDown(false);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 put_dust's mouse target under manual control and the aim assist: "
                + wrong);
        helper.succeed();
    }

    // shift + the time_mark slot with the time marked and this synced aim: whether the slot showed put_dust and its
    // real press chose the cow
    private static void putDustRow(Scene scene, String row, ActionTarget aimed, boolean shown, boolean selects,
            short key, List<String> wrong) {
        BitesTheDustService.removeVictim(scene.owner);
        if (!scene.state.hasBitesTheDustMarker()) {
            scene.helper.assertTrue(BitesTheDustService.markTime(scene.owner), "fixture: the time could not be marked");
        }
        scene.owner.setShiftKeyDown(true);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(aimed);
        scene.owner.messages.clear();
        Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
        Ability putDust = scene.power.getAbility(KillerQueenLionAddon.PUT_DUST_NAME);
        AvailableAbilities available = new AvailableAbilities();
        available.update(scene.power, scene.power.getMoveset());
        AbilityConditionCheck selected = available.getContextVariationContainer(root);
        String slot = selected != null ? selected.ability.name() : "nothing";
        boolean checked = putDust.checkSpecificConditions(scene.power).isPositive();
        BufferingState state = BufferingState.clickCanBuffer();
        if (selected != null && selected.ability == putDust
                && AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, selected.ability, scene.owner, null, InputMethod.CLICK, 0, state,
                    root.getAbilityId());
        }
        AbilityInput.keyRelease(key, scene.owner);
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
        for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
            action._tickAction();
        }
        boolean chosen = scene.cow.getUUID().equals(scene.state.bitesTheDustVictimUuid());
        String expectedSlot = shown ? KillerQueenLionAddon.PUT_DUST_NAME : KillerQueenLionAddon.REMOVE_MARK_NAME;
        if (!slot.equals(expectedSlot) || checked != selects || state.isActionSuccess != selects || chosen != selects) {
            wrong.add(row + ": 1.16 showed " + expectedSlot + (selects ? " and its press chose the cow"
                    : shown ? ", refused the press and chose nothing" : ", put_dust refused and nothing chosen")
                    + "; the port showed " + slot + ", put_dust's check " + checked + ", press started "
                    + state.isActionSuccess + ", the cow chosen " + chosen + " " + scene.owner.keys());
        }
        LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
        BitesTheDustService.removeVictim(scene.owner);
    }

    /**
     * 1.16 kq_entitybomb (PunchBomb, ENTITY target) took, for an empty vanilla pick with the Stand out, the Stand's
     * precision ray (EntityStandType.clientHitResult :122-151), which met entities through blocks. The owner's wall rule
     * (StandSkillWalls, the manga's rule) stops the pick of a skill that is not marked through walls, and Killer Queen's
     * entity bomb, a touch, is not: a cow behind a wall is no target and the press is refused without a message.
     */
    @GameTest(template = "empty", batch = "kqlEntityBombWall", timeoutTicks = 20)
    public static void entityBombPrecisionRayStopsAtAWallByTheOwnersRule(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        try (Scene scene = new Scene(helper, "kql-entity-bomb-wall")) {
            PlayerClientBroadcastedSettings settings =
                    scene.owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
            Ability bomb = scene.power.getAbility(KillerQueenLionAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(bomb != null, "fixture: no entity bomb");
            btdStand(scene, true);
            scene.turn(0.0F);
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
            scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                    scene.owner.getZ() + 3.0D);
            BlockPos wall = BlockPos.containing(scene.owner.getX(), scene.owner.getEyeY(), scene.owner.getZ() + 1.5D);
            List<BlockPos> stones = List.of(wall.below(), wall, wall.above());
            try {
                ConditionCheck open = bomb.checkSpecificConditions(scene.power);
                helper.assertTrue(open.isPositive(), "fixture: with no wall the Stand's precision ray does not take the "
                        + "cow 3 blocks ahead (" + (open.getWarning() != null ? open.getWarning().getString()
                                : "no message") + ")");
                stones.forEach(stone -> level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState()));
                for (boolean assist : new boolean[] { true, false }) {
                    settings.standAimAssist = assist;
                    ConditionCheck walled = bomb.checkSpecificConditions(scene.power);
                    if (walled.isPositive() || walled.getWarning() != null) {
                        wrong.add("aim assist " + (assist ? "on" : "off") + ", nothing under the crosshair, the cow 3 "
                                + "blocks ahead behind a wall: the wall stops an unmarked skill's pick and the press is "
                                + "refused without a message; the port "
                                + (walled.isPositive() ? "took it" : "said " + walled.getWarning().getString()));
                    }
                }
            } finally {
                settings.standAimAssist = true;
                stones.forEach(stone -> level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState()));
            }
        }
        helper.assertTrue(wrong.isEmpty(), "Killer Queen's entity bomb behind a wall (StandSkillWalls): " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 BlockBombAction.standPerform (100 :71-85) cast its own ray as it performed, whatever the press had aimed at:
     * from the user's eyes (the Stand's under manual control) along their look over the Stand's max range, entities
     * first and through blocks (JojoModUtil.rayTrace). An entity on it other than the Stand and its user left no bomb;
     * else the bomb went on the block whose outline it met and the Stand moved to the point it met.
     */
    @GameTest(template = "empty", batch = "kqlBlockBombRay", timeoutTicks = 20)
    public static void blockBombPerformCastsItsOwnRayAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        List<BlockPos> placed = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-block-bomb-ray")) {
            scene.turn(0.0F);
            scene.stand.setCanFollowUser(false);
            scene.cow.setPos(scene.owner.getX() + 6.0D, scene.owner.getY(), scene.owner.getZ());
            BlockPos eye = BlockPos.containing(scene.owner.getEyePosition());
            BlockPos ahead = eye.south(2);
            BlockPos aside = ahead.east(2);
            double range = scene.stand.getMaxRange();
            BlockPos far = eye.south((int) Math.ceil(range) + 2);
            for (BlockPos pos : List.of(ahead, aside, far)) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                placed.add(pos);
            }
            level.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

            // pressed at the stone aside, its user then looking at the stone ahead
            Vec3[] standAt = new Vec3[1];
            BlockPos bombed = blockBombRow(scene, new ActionTarget(aside, Direction.UP), standAt, wrong);
            Vec3 met = new Vec3(scene.owner.getX(), scene.owner.getEyeY(), ahead.getZ());
            if (!ahead.equals(bombed)) {
                wrong.add("pressed at the stone aside, its user looking at the stone ahead: 1.16 bombed the stone "
                        + "ahead " + ahead + ", the port " + bombed);
            } else if (standAt[0].distanceTo(met) > 1.0E-6D) {
                wrong.add("1.16 moved the Stand to the point its ray met, " + met + "; the port to " + standAt[0]);
            }

            // a mob behind the stone, on the ray within the Stand's max range
            scene.cow.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.cow.getBbHeight() / 2.0D,
                    ahead.getZ() + 1.5D);
            bombed = blockBombRow(scene, new ActionTarget(ahead, Direction.NORTH), standAt, wrong);
            if (bombed != null) {
                wrong.add("a cow behind the stone on its ray: 1.16 met the cow first and set no bomb, the port bombed "
                        + bombed);
            }
            scene.cow.setPos(scene.owner.getX() + 6.0D, scene.owner.getY(), scene.owner.getZ());

            // a stone past the Stand's max range
            level.setBlock(ahead, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(far, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            bombed = blockBombRow(scene, new ActionTarget(far, Direction.NORTH), standAt, wrong);
            if (bombed != null) {
                wrong.add("a stone " + far + " past the Stand's max range " + range + ": 1.16's ray met nothing, the "
                        + "port bombed " + bombed);
            }
            level.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ahead, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);

            // under manual control the Stand's eyes, its user looking away
            scene.turn(180.0F);
            scene.stand.setManuallyControlled(true);
            helper.assertTrue(scene.stand.isManuallyControlled(), "fixture: Killer Queen is not manually controlled");
            bombed = blockBombRow(scene, ActionTarget.EMPTY, standAt, wrong);
            scene.stand.setManuallyControlled(false);
            if (!ahead.equals(bombed)) {
                wrong.add("Killer Queen under manual control looking at the stone ahead, its user away: 1.16 bombed "
                        + ahead + " from the Stand's eyes, the port " + bombed);
            }
        } finally {
            placed.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_block_bomb's perform ray: " + wrong);
        helper.succeed();
    }

    // The block bomb set on the Stand pressed at this target, Killer Queen's eyes level with its user's 0.35 south and
    // looking south, then performed; the block bombed, or null, and where the perform left the Stand
    private static BlockPos blockBombRow(Scene scene, ActionTarget pressedAt, Vec3[] standAt, List<String> wrong) {
        KillerQueenStateService.clearBlockBomb(scene.owner);
        scene.power.setStamina(scene.power.getMaxStamina());
        scene.stand.moveTo(scene.owner.getX(), scene.owner.getEyeY() - scene.stand.getEyeHeight(),
                scene.owner.getZ() + 0.35D, 0.0F, 0.0F);
        scene.stand.setYHeadRot(0.0F);
        EntityActionInstance action = scene.setOnStand(KillerQueenLionAddon.BLOCK_BOMB_NAME, pressedAt);
        if (action == null) {
            wrong.add("fixture: " + scene.fixtureFailure);
            return null;
        }
        action.setPhaseStart(ActionPhase.PERFORM);
        action._tickAction();
        standAt[0] = scene.stand.position();
        BlockPos bombed = KillerQueenStateService.blockBombPos(scene.owner);
        LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
        KillerQueenStateService.clearBlockBomb(scene.owner);
        return bombed;
    }

    // The user looks south (pitch 0) at a wall torch on its crosshair line a block ahead, kept up without neighbour
    // updates; Killer Queen, not following, has its eyes level with its user's, 0.35 south (past the user's box), and
    // looks the same way, so its collision ray runs along the crosshair line
    private static BlockPos pressedTorchScene(Scene scene) {
        scene.turn(0.0F);
        BlockPos torch = BlockPos.containing(scene.owner.getEyePosition()).south();
        scene.helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        scene.stand.setCanFollowUser(false);
        scene.stand.moveTo(scene.owner.getX(), scene.owner.getEyeY() - scene.stand.getEyeHeight(),
                scene.owner.getZ() + 0.35D, 0.0F, 0.0F);
        scene.stand.setYHeadRot(0.0F);
        BlockState placed = scene.helper.getLevel().getBlockState(torch);
        scene.helper.assertTrue(placed.is(Blocks.WALL_TORCH)
                        && placed.getCollisionShape(scene.helper.getLevel(), torch).isEmpty(),
                "fixture: no wall torch without a collision shape at " + torch);
        return torch;
    }

    private static void clearPressedTorchScene(Scene scene, BlockPos torch) {
        scene.helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        scene.stand.setCanFollowUser(true);
    }

    /**
     * 1.16 SnowBomb and HBubbleBomb (100 InitStands :93-94) and BubbleMarkAction (:96) were StandActions of the user: PowerBaseImpl.onClickAction
     * performed them at the press (the bomb left the user, StandAction.onPerform paid the stamina), StandAction.onClick
     * summoned nothing and no Stand task was started, so a Stand action already running went on.
     */
    @GameTest(template = "empty", batch = "kqUserBombLaunch", timeoutTicks = 40)
    public static void snowAndBubbleBombsAreTheUsersOwnActionsAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-user-bombs")) {
            scene.unlock(KillerQueenLionAddon.SNOW_BOMB_NAME);
            scene.owner.getInventory().add(new ItemStack(Items.SNOWBALL, 4));
            EntityActionInstance punch = scene.setOnStand(KillerQueenLionAddon.PUNCH_NAME, ActionTarget.EMPTY);
            helper.assertTrue(punch != null, "fixture: " + scene.fixtureFailure);
            launchRow(scene, KillerQueenLionAddon.BUBBLE_MARK_NAME, KillerQueenLionAddon.BUBBLE_MARK_NAME,
                    BubbleMarkEntity.class, (short) 201, "while Killer Queen punches", wrong);
            if (scene.stand.getCurStandAction() != punch || punch.isOver()) {
                wrong.add("the bubble mark pressed during a punch stopped it: 1.16 started no Stand task, the Stand's "
                        + "action is " + scene.stand.getCurStandAction());
            }
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            stunRows(scene, KillerQueenLionAddon.BUBBLE_MARK_NAME, BubbleMarkEntity.class, (short) 205, wrong);
            stunRows(scene, KillerQueenLionAddon.SNOW_BOMB_NAME, SnowBombEntity.class, (short) 207, wrong);
            KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(scene.owner, scene.power);
            helper.assertTrue(!scene.power.isSummoned(), "fixture: Killer Queen is still out");
            launchRow(scene, KillerQueenLionAddon.SNOW_BOMB_NAME, KillerQueenLionAddon.SNOW_BOMB_NAME, SnowBombEntity.class,
                    (short) 202, "with Killer Queen away", wrong);
            launchRow(scene, KillerQueenLionAddon.BUBBLE_BOMB_NAME, KillerQueenLionAddon.BUBBLE_BOMB_NAME,
                    BubbleBombEntity.class, (short) 203, "with Killer Queen away", wrong);
            launchRow(scene, KillerQueenLionAddon.BUBBLE_MARK_NAME, KillerQueenLionAddon.BUBBLE_MARK_NAME,
                    BubbleMarkEntity.class, (short) 204, "with Killer Queen away", wrong);
            scene.owner.getInventory().clearContent();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_snow_bomb / kq_bubble_bomb / kq_bubble_mark were the user's StandActions: " + wrong);
        helper.succeed();
    }

    // A real press of the slot; the projectile it launched must be out at once and fly on without the Stand
    private static void launchRow(Scene scene, String slot, String pressed, Class<? extends Entity> kind, short key,
            String when, List<String> wrong) {
        boolean summoned = scene.power.isSummoned();
        int snowballs = scene.owner.getInventory().countItem(Items.SNOWBALL);
        float stamina = scene.power.getStamina();
        BufferingState state = scene.press(slot, InputMethod.CLICK, key);
        List<? extends Entity> bombs = ownedBombs(scene, kind);
        if (!state.isActionSuccess || state.shouldBuffer || bombs.isEmpty()) {
            wrong.add(pressed + " " + when + ": 1.16 launched it at the press; the port started " + state.isActionSuccess
                    + ", buffered " + state.shouldBuffer + ", " + bombs.size() + " " + kind.getSimpleName() + " "
                    + scene.owner.keys());
        }
        if (scene.power.isSummoned() != summoned) {
            wrong.add(pressed + " " + when + " summoned Killer Queen: 1.16 StandAction.onClick summoned nothing");
        }
        if (kind == SnowBombEntity.class && scene.owner.getInventory().countItem(Items.SNOWBALL) != snowballs - 1) {
            wrong.add(pressed + " " + when + " did not take one snowball: " + snowballs + " -> "
                    + scene.owner.getInventory().countItem(Items.SNOWBALL));
        }
        if (!(scene.power.getStamina() < stamina) || !scene.power.isAbilityOnCooldown(pressed)) {
            wrong.add(pressed + " " + when + " did not pay its stamina and cooldown at the press: stamina " + stamina
                    + " -> " + scene.power.getStamina() + ", cooldown " + scene.power.getAbilityCooldown(pressed));
        }
        for (Entity bomb : bombs) {
            bomb.tick();
            if (bomb.isRemoved()) {
                wrong.add(pressed + " " + when + ": its " + kind.getSimpleName() + " was dropped on its first tick");
            }
            bomb.discard();
        }
        scene.power.setStamina(scene.power.getMaxStamina());
    }

    /*
     * 1.16 PowerBaseImpl.checkRequirements refused an action whose performer was stunned, and a StandAction's performer
     * was the summoned Stand, else its user (StandAction.getPerformer): with Killer Queen out its stun refused the press
     * ("stun"), while a shock on the user alone (Hamon Shock, which the Stand does not share) did not.
     */
    private static void stunRows(Scene scene, String slot, Class<? extends Entity> kind, short key, List<String> wrong) {
        scene.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
        scene.helper.assertTrue(ModStatusEffects.isStunned(scene.stand) && !ModStatusEffects.isStunned(scene.owner),
                "fixture: the Stand alone is not stunned");
        scene.owner.messages.clear();
        BufferingState stunned = scene.press(slot, InputMethod.CLICK, key);
        List<? extends Entity> bombs = ownedBombs(scene, kind);
        if (stunned.isActionSuccess || !bombs.isEmpty()
                || !scene.owner.keys().contains("jojo.message.action_condition.stun")) {
            wrong.add(slot + " pressed with Killer Queen stunned: 1.16 refused it with 'stun' (the summoned Stand "
                    + "performed it), the port started " + stunned.isActionSuccess + ", " + bombs.size() + " "
                    + kind.getSimpleName() + " " + scene.owner.keys());
        }
        bombs.forEach(Entity::discard);
        scene.owner.messages.clear();
        scene.stand.removeEffect(ModStatusEffects.STUN);
        scene.owner.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK, 40));
        scene.helper.assertTrue(ModStatusEffects.isStunned(scene.owner) && !ModStatusEffects.isStunned(scene.stand),
                "fixture: the user alone is not shocked");
        launchRow(scene, slot, slot, kind, (short) (key + 1), "with its user shocked and Killer Queen out", wrong);
        scene.owner.removeEffect(ModStatusEffects.HAMON_SHOCK);
    }

    private static List<? extends Entity> ownedBombs(Scene scene, Class<? extends Entity> kind) {
        return scene.helper.getLevel().getEntitiesOfClass(kind, scene.owner.getBoundingBox().inflate(8.0D),
                bomb -> bomb instanceof ModdedProjectileEntity projectile && projectile.getOwner() == scene.owner);
    }

    private static final List<String> BTD_USER_MOVES = List.of(KillerQueenLionAddon.TIME_MARK_NAME, KillerQueenLionAddon.REMOVE_MARK_NAME,
            KillerQueenLionAddon.PUT_DUST_NAME, KillerQueenLionAddon.QUIT_DUST_NAME);

    /**
     * 1.16 TimeMarker, RemoveTimeMarker, SelectHayato and RemoveHayato (time_mark, remov_mark, put_dust, quit_dust;
     * 100 InitStands :98, :100, :101, :103) were StandActions, which the user's power held, never the Stand's task: PowerBaseImpl.checkRequirements
     * refused one whose performer was stunned ("stun"), and StandAction.getPerformer :191-193 made that the summoned Stand
     * while it was out, else the user. A stunned Killer Queen refused them, a shock on the user alone with Killer Queen
     * out did not, and with Killer Queen away the user's own stun refused them.
     */
    @GameTest(template = "empty", batch = "kqlBtdPerformer", timeoutTicks = 60)
    public static void bitesTheDustUserMovesMeetTheStandsStunAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-btd-performer")) {
            scene.unlock(KillerQueenLionAddon.TIME_MARK_NAME);
            scene.state.setBitesTheDustAwakened(true);
            short key = 401;
            for (String name : BTD_USER_MOVES) {
                // Killer Queen out and stunned alone: refused
                btdPerformerRow(scene, name, true, true, false, false, key++, wrong);
                // its user shocked alone, Killer Queen out: goes off, performed by the Stand
                btdPerformerRow(scene, name, true, false, true, true, key++, wrong);
                // Killer Queen away, its user shocked: refused
                btdPerformerRow(scene, name, false, false, true, false, key++, wrong);
                // Killer Queen away, no stun: goes off, performed by the user
                btdPerformerRow(scene, name, false, false, false, true, key++, wrong);
            }
            scene.owner.setShiftKeyDown(false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Bites the Dust StandActions' performer: " + wrong);
        helper.succeed();
    }

    // One real press of the time_mark slot, set up so that it resolves to this move
    private static void btdPerformerRow(Scene scene, String name, boolean standOut, boolean standStunned,
            boolean userShocked, boolean goesOff, short key, List<String> wrong) {
        String row = name + (standOut ? " with Killer Queen out" : " with Killer Queen away")
                + (standStunned ? ", Killer Queen stunned" : "") + (userShocked ? ", its user shocked" : "");
        StandEntity stand = btdStand(scene, standOut);
        BitesTheDustService.removeTimeMarker(scene.owner);
        if (!name.equals(KillerQueenLionAddon.TIME_MARK_NAME)) {
            scene.helper.assertTrue(BitesTheDustService.markTime(scene.owner), "fixture: the time could not be marked");
        }
        if (name.equals(KillerQueenLionAddon.QUIT_DUST_NAME)) {
            scene.helper.assertTrue(scene.state.selectBitesTheDustVictim(UUID.randomUUID(),
                    scene.helper.getLevel().dimension().location()), "fixture: no victim could be armed");
        }
        boolean put = name.equals(KillerQueenLionAddon.PUT_DUST_NAME);
        scene.owner.setShiftKeyDown(put || name.equals(KillerQueenLionAddon.REMOVE_MARK_NAME));
        scene.turn(put ? 0.0F : 180.0F);
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(
                put ? new ActionTarget(scene.cow) : ActionTarget.EMPTY);
        if (standStunned) {
            stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
        }
        if (userShocked) {
            scene.owner.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK, 40));
        }
        scene.helper.assertTrue(ModStatusEffects.isStunned(scene.owner) == userShocked
                && (stand == null || ModStatusEffects.isStunned(stand) == standStunned),
                "fixture: the stuns are not as set for " + row);
        boolean hadMarker = scene.state.hasBitesTheDustMarker();
        boolean hadVictim = scene.state.hasBitesTheDustVictim();
        scene.owner.messages.clear();
        Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
        AvailableAbilities available = new AvailableAbilities();
        available.update(scene.power, scene.power.getMoveset());
        AbilityConditionCheck selected = available.getContextVariationContainer(root);
        BufferingState state = BufferingState.clickCanBuffer();
        if (selected == null || !selected.ability.name().equals(name)) {
            wrong.add(row + ": fixture, the time_mark slot resolved to "
                    + (selected != null ? selected.ability.name() : "nothing"));
        } else if (AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, selected.ability, scene.owner, null, InputMethod.CLICK, 0, state,
                    selected.ability.getAbilityId());
        }
        AbilityInput.keyRelease(key, scene.owner);
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
        LivingEntity performer = action != null && selected != null && action.ability == selected.ability
                ? action.getPerformer() : null;
        for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
            action._tickAction();
        }
        boolean done = switch (name) {
            case KillerQueenLionAddon.TIME_MARK_NAME -> !hadMarker && scene.state.hasBitesTheDustMarker();
            case KillerQueenLionAddon.REMOVE_MARK_NAME -> hadMarker && !scene.state.hasBitesTheDustMarker();
            case KillerQueenLionAddon.PUT_DUST_NAME -> !hadVictim && scene.state.hasBitesTheDustVictim();
            default -> hadVictim && !scene.state.hasBitesTheDustVictim();
        };
        LivingEntity expected = standOut ? stand : scene.owner;
        if (goesOff && (!state.isActionSuccess || !done || performer != expected)) {
            wrong.add(row + ": 1.16 performed it (performer " + (standOut ? "the Stand" : "the user")
                    + "); the port started " + state.isActionSuccess + ", done " + done + ", performer " + performer
                    + " " + scene.owner.keys());
        }
        if (!goesOff && (state.isActionSuccess || done
                || !scene.owner.keys().contains("jojo.message.action_condition.stun"))) {
            wrong.add(row + ": 1.16 refused it with 'stun' (performer " + (standOut ? "the Stand" : "the user")
                    + "); the port started " + state.isActionSuccess + ", done " + done + " " + scene.owner.keys());
        }
        LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
        scene.owner.removeEffect(ModStatusEffects.HAMON_SHOCK);
        StandEntity summoned = scene.power.getSummonedStandEntity();
        if (summoned != null) {
            summoned.removeEffect(ModStatusEffects.STUN);
        }
        scene.power.setStamina(scene.power.getMaxStamina());
    }

    // Killer Queen out (summoned again if a move called it back) or away, with no action of its own
    private static StandEntity btdStand(Scene scene, boolean out) {
        if (!out) {
            if (scene.power.isSummoned()) {
                KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(scene.owner, scene.power);
            }
            scene.helper.assertTrue(!scene.power.isSummoned(), "fixture: Killer Queen is still out");
            return null;
        }
        if (!scene.power.isSummoned()) {
            scene.helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(scene.owner, scene.power),
                    "fixture: Killer Queen could not be summoned again");
        }
        StandEntity stand = scene.power.getSummonedStandEntity();
        scene.helper.assertTrue(stand != null, "fixture: no summoned Killer Queen");
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.setPos(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ());
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        return stand;
    }

    /**
     * 1.16 PowerBaseImpl.checkRequirements :255-257 refused every other action of the Stand power, without a message,
     * while the power held one that let none through (StandEntityAction.heldAllowsOtherAction, false for a holdToFire
     * action): kq_entity_explo's 20-tick charge (100 InitStands :86, holdToFire(20, false)) and kq_reset's 60 (:99) refused
     * time_mark, remov_mark and put_dust, StandActions of the same power. Performed by the summoned Stand, such a press
     * waits in the input buffer behind the Stand's charge (owner decision N2) and goes off once the charge is let go.
     */
    @GameTest(template = "empty", batch = "kqlBtdHeldCharge", timeoutTicks = 60)
    public static void bitesTheDustPressWaitsBehindAHeldStandChargeAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "kql-btd-held-detonation")) {
            scene.unlock(KillerQueenLionAddon.TIME_MARK_NAME);
            scene.state.setBitesTheDustAwakened(true);
            EntityActionInstance held = scene.pressAndHold(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, (short) 451);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            btdPressBehindCharge(scene, held, (short) 451, (short) 452, KillerQueenLionAddon.TIME_MARK_NAME, wrong);
        }
        try (Scene scene = new Scene(helper, "kql-btd-held-reset")) {
            scene.awakenAndMarkTime();
            EntityActionInstance held = scene.pressAndHold(KillerQueenLionAddon.RESET_DAY_NAME, (short) 453);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            scene.owner.setShiftKeyDown(true);
            scene.turn(180.0F);
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
            btdPressBehindCharge(scene, held, (short) 453, (short) 454, KillerQueenLionAddon.REMOVE_MARK_NAME, wrong);
            scene.owner.setShiftKeyDown(false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 refused the Bites the Dust StandActions during a held Stand charge: "
                + wrong);
        helper.succeed();
    }

    // A real press of the time_mark slot by another key while the charge is held, set up to resolve to this move; then the
    // charge let go and the input buffer ticked
    private static void btdPressBehindCharge(Scene scene, EntityActionInstance held, short heldKey, short key, String name,
            List<String> wrong) {
        String charge = held.ability instanceof Ability ability ? ability.name() : String.valueOf(held.ability);
        boolean hadMarker = scene.state.hasBitesTheDustMarker();
        float stamina = scene.power.getStamina();
        Ability root = scene.power.getAbility(KillerQueenLionAddon.TIME_MARK_NAME);
        AvailableAbilities available = new AvailableAbilities();
        available.update(scene.power, scene.power.getMoveset());
        AbilityConditionCheck selected = available.getContextVariationContainer(root);
        if (selected == null || !selected.ability.name().equals(name)) {
            wrong.add(name + ": fixture, the time_mark slot resolved to "
                    + (selected != null ? selected.ability.name() : "nothing"));
            return;
        }
        BufferingState state = BufferingState.clickCanBuffer();
        if (AbilityInput.withConditionCheck(selected, scene.owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, selected.ability, scene.owner, null, InputMethod.CLICK, 0, state,
                    root.getAbilityId());
        }
        AbilityInput.keyRelease(key, scene.owner);
        scene.tick();
        scene.tickInputBuffer();
        if (!state.shouldBuffer || state.isActionSuccess || scene.state.hasBitesTheDustMarker() != hadMarker
                || scene.power.getStamina() != stamina || !scene.held(held)
                || held.getPhase() != ActionPhase.BUTTON_CHARGE) {
            wrong.add(name + " pressed during " + charge + "'s held charge: 1.16 refused it and the charge went on; the "
                    + "port buffered " + state.shouldBuffer + ", started " + state.isActionSuccess + ", marker " + hadMarker
                    + " -> " + scene.state.hasBitesTheDustMarker() + ", stamina " + stamina + " -> "
                    + scene.power.getStamina() + ", charge held " + scene.held(held) + " (" + held.getPhase() + ") "
                    + scene.owner.keys());
        }
        AbilityInput.keyRelease(heldKey, scene.owner);
        scene.tickInputBuffer();
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
        for (int tick = 0; tick < 5 && action != null && !action.isOver(); tick++) {
            action._tickAction();
        }
        if (!held.isOver() || scene.state.hasBitesTheDustMarker() == hadMarker) {
            wrong.add(name + " waiting behind " + charge + "'s charge: the press goes off once the charge is let go; "
                    + "charge over " + held.isOver() + ", the user's action " + action + ", marker " + hadMarker + " -> "
                    + scene.state.hasBitesTheDustMarker() + " " + scene.owner.keys());
        }
        LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
    }

    // 1.16 StandStatFormulas.getLightAttackRecovery at a finisher meter of 0
    private static float lightPerform116(double speed) {
        double val = (24.0D - speed) / 2.0D;
        return val <= 1.0D ? 1.0F : (float) Math.ceil(Math.max(val, val > 4.0D ? 4.0D : val));
    }

    // 1.16 StandStatFormulas.getHeavyAttackWindup at a finisher meter of 0
    private static float heavyWindup116(double speed) {
        float f = 40.0F - (float) speed * 1.25F;
        return (float) Math.ceil(f * 2.0F / 3.0F);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final RecordingPlayer owner;
        final Cow cow;
        final StandPower power;
        final StandEntity stand;
        final KillerQueenState state;
        String fixtureFailure = "";
        private short key = -1;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            ServerLevel level = helper.getLevel();
            owner = new RecordingPlayer(level, new GameProfile(UUID.randomUUID(), name));
            owner.getAbilities().instabuild = false;
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 3.0D, 1.5D)));
            level.addNewPlayer(owner);
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "fixture: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(new Vec3(1.5D, 3.0D, 5.5D)));
            helper.assertTrue(level.addFreshEntity(cow), "fixture: the cow could not join the level");
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                    "fixture: Killer Queen could not be summoned");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "fixture: no summoned Killer Queen");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            state = KillerQueenLionAddon.state(owner);
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                    "fixture: the primary bomb was rejected");
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
            power.tick(); // moves are cached per tick
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(!power.isStaminaInfinite(), "fixture: the stamina bar is infinite");
        }

        // A real held key press, then the action ticked until it holds; null when it did not get there
        EntityActionInstance pressAndHold(String name, short keyId) {
            Ability ability = power.getAbility(name);
            if (!(ability instanceof EntityActionAbility entityAbility)) {
                fixtureFailure = name + " is not an action";
                return null;
            }
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            AbilityConditionCheck selected = available.getContextVariationContainer(ability);
            if (selected == null) {
                fixtureFailure = "not offered";
                return null;
            }
            if (!AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD)) {
                fixtureFailure = "the press was refused: " + selected.conditionCheck.getWarning() + " "
                        + owner.keys();
                return null;
            }
            key = keyId;
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(keyId, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            StandEntity performer = power.getSummonedStandEntity();
            EntityActionInstance action = performer != null ? performer.getCurStandAction() : null;
            if (!press.isActionSuccess || action == null || action.ability != ability) {
                fixtureFailure = "the press did not start it on the Stand (the user's action: "
                        + LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND) + ")";
                return null;
            }
            for (int tick = 0; tick < 20 && !entityAbility.isActionHeld(action) && !action.isOver(); tick++) {
                action._tickAction();
            }
            // one held tick that passes the recheck
            tick();
            if (!held(action)) {
                fixtureFailure = "it was not held after the press (phase " + action.getPhase() + ", messages "
                        + owner.keys() + ")";
                return null;
            }
            owner.messages.clear();
            return action;
        }

        // A real press by another key; the action it started, buffered or not, is left as it is
        BufferingState press(String name, InputMethod method, short keyId) {
            Ability ability = power.getAbility(name);
            power.setAbilityCooldown(name, 0, 0);
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            AbilityConditionCheck selected = available.getContextVariationContainer(ability);
            BufferingState state = BufferingState.clickCanBuffer();
            if (selected != null && AbilityInput.withConditionCheck(selected, owner, method)) {
                AbilityInput.keyPress(keyId, selected.ability, owner, null, method, 0, state,
                        selected.ability.getAbilityId());
            }
            AbilityInput.keyRelease(keyId, owner);
            return state;
        }

        // The ability's action set on the Stand as its press sets it, the Stand aimed at the target meanwhile
        EntityActionInstance setOnStand(String name, ActionTarget aimed) {
            if (!(power.getAbility(name) instanceof EntityActionType type)) {
                fixtureFailure = name + " is missing";
                return null;
            }
            LivingComponentAction component = LivingComponentAction.getComponent(stand);
            component.setAction(null, SyncType.NO_SYNC);
            component.entityAim.setTarget(aimed);
            EntityActionInstance action = type.initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
            component.setAction(action, owner, SyncType.NO_SYNC);
            component.entityAim.setTarget(ActionTarget.EMPTY);
            if (component.getAction() != action) {
                fixtureFailure = name + " was not set on the Stand";
                return null;
            }
            return action;
        }

        // The Stand's action cleared with the Stand at its user's feet: a Stand 2 blocks or more away would be retracted
        void clearStandAction() {
            stand.setPos(owner.getX(), owner.getY(), owner.getZ());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        }

        // Killer Queen called back at once, its action cleared
        void unsummon() {
            StandEntity summoned = power.getSummonedStandEntity();
            if (summoned != null) {
                LivingComponentAction.getComponent(summoned).setAction(null, SyncType.NO_SYNC);
            }
            if (power.isSummoned()) {
                KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(owner, power);
            }
        }

        static String arms(StandEntity stand) {
            return stand == null ? "summoned nothing"
                    : stand.isArmsOnlyMode() ? "showed the main arm " + stand.showArm(InteractionHand.MAIN_HAND)
                            + ", the off arm " + stand.showArm(InteractionHand.OFF_HAND)
                    : "brought out the whole Stand";
        }

        // The user's view, head and body turned to this yaw, level
        void turn(float yaw) {
            owner.setYRot(yaw);
            owner.yRotO = yaw;
            owner.setYHeadRot(yaw);
            owner.yHeadRotO = yaw;
            owner.setXRot(0.0F);
            owner.xRotO = 0.0F;
            owner.yBodyRot = yaw;
            owner.yBodyRotO = yaw;
        }

        // Null when the Stand stands at its user's feet + (0, 0, front) turned by the user's view
        String frontError(double front) {
            Vec3 expected = new Vec3(0.0D, 0.0D, front)
                    .xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
                    .yRot(-owner.getYRot() * Mth.DEG_TO_RAD);
            stand.updatePosition(owner);
            Vec3 actual = stand.position().subtract(owner.position());
            return actual.distanceTo(expected) < 1.0E-4D ? null
                    : "at its user's feet + " + expected + ", the port at " + actual + " (offset "
                            + stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations()
                            + ")";
        }

        // A skill learned as the Resolve progression would
        void unlock(String skill) {
            PowerData data = power.getCurTypeData();
            helper.assertTrue(data != null && (data.isSkillUnlocked(skill) || data._setSkillUnlocked(skill, true, true)),
                    "fixture: " + skill + " could not be unlocked");
        }

        boolean held(EntityActionInstance action) {
            StandEntity performer = power.getSummonedStandEntity();
            return performer != null && performer.getCurStandAction() == action && !action.isOver()
                    && ((EntityActionAbility) action.ability).isActionHeld(action);
        }

        // One server tick of the running action, its key still down
        void tick() {
            StandEntity performer = power.getSummonedStandEntity();
            EntityActionInstance action = performer != null ? performer.getCurStandAction() : null;
            if (action != null && !action.isOver()) {
                action._tickAction();
            }
        }

        // One server tick of the user's input buffer; the power resolves its keys once a tick, and the test does not
        // tick the power
        void tickInputBuffer() {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            EntityActionInputState input = owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
            input.inputBuffer.tickInputBuffer(input);
        }

        // Bites the Dust awakened (as a Stand Arrow does) and the time marked
        void awakenAndMarkTime() {
            PowerData data = power.getCurTypeData();
            helper.assertTrue(data != null && (data.isSkillUnlocked(KillerQueenLionAddon.TIME_MARK_NAME)
                            || data._setSkillUnlocked(KillerQueenLionAddon.TIME_MARK_NAME, true, true)),
                    "fixture: Bites the Dust could not be unlocked");
            state.setBitesTheDustAwakened(true);
            helper.assertTrue(BitesTheDustService.markTime(owner), "fixture: the time could not be marked");
        }

        // BitesZaDustHandler.userToVictim filled during the hold; the Stand stays out
        void armBitesTheDust() {
            ServerLevel level = helper.getLevel();
            ResourceLocation dimension = level.dimension().location();
            helper.assertTrue(state.setBitesTheDustMarker(level.getDayTime(), level.getGameTime(), dimension,
                            List.of())
                            | state.selectBitesTheDustVictim(UUID.randomUUID(), dimension),
                    "fixture: Bites the Dust could not be armed");
            helper.assertTrue(BitesTheDustLock.applies(power),
                    "fixture: the armed Bites the Dust does not lock the kit");
        }

        @Override
        public void close() {
            if (key >= 0) {
                AbilityInput.keyRelease(key, owner);
            }
            state.clearBitesTheDust();
            KillerQueenStateService.clearAll(owner);
            if (power.isSummoned()) {
                KillerQueenLionAddon.KILLER_QUEEN.get().forceUnsummon(owner, power);
            }
            cow.discard();
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // A player outside the level whose connection records what the server sends it, as a client tracking whatever
    // seenBy it is added to
    private static final class SoundWatcher extends FakePlayer {
        final List<Packet<?>> sent = new ArrayList<>();

        SoundWatcher(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "kql-sound-watcher"));
            Connection channel = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(channel);
            connection = new ServerGamePacketListenerImpl(level.getServer(), channel, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    record(packet);
                }

                @Override
                public void send(Packet<?> packet, PacketSendListener listener) {
                    record(packet);
                }
            };
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::record);
            } else {
                sent.add(packet);
            }
        }

        // The Stand entity sounds of this event sent for the Stand (StandUtil.playStandEntitySound)
        int standSounds(StandEntity stand, SoundEvent sound) {
            int count = 0;
            for (Packet<?> packet : sent) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof StandEntitySoundPacket played
                        && played.entityId() == stand.getId() && played.sound().value() == sound) {
                    count++;
                }
            }
            return count;
        }
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

        List<String> keys() {
            List<String> found = new ArrayList<>();
            for (Component message : messages) {
                found.add(message.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey()
                        : message.getString());
            }
            return found;
        }
    }
}
