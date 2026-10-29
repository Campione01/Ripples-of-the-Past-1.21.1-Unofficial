package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeSkipAbility;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonProtectionAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 KingCrimsonTimeSkip (062 action/KingCrimsonTimeSkip.java) was a StandAction and an IPlayerAction: perform
 * (:74) set its continuous player action, which stopped itself after 10 server ticks (Instance.invTicks, :93-111).
 * While it ran, Action.checkConditions (:140-143) refused, first and without a message, every action of its user whose
 * canBeUsedDuringPlayerAction was false: the non-Stand ones (Action :241-243); Stand actions went through (StandAction
 * :138-141).
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonTimeSkipPlayerActionGameTests {
    private static final short SKIP_KEY = 231;
    private static final short PROTECTION_KEY = 232;
    private static final short OVERDRIVE_KEY = 233;
    private static final short PUNCH_KEY = 234;
    private static final short GUARD_KEY = 235;
    private static final short BARRAGE_KEY = 236;
    private static final String SKIP = "kingcrimson_timeskip";

    private KingCrimsonTimeSkipPlayerActionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_time_skip_player_action")
    public static void hamonIsRefusedSilentlyDuringTheTimeSkipAndStandMovesGoThroughAsIn116(GameTestHelper helper) {
        try (Scene s = new Scene(helper)) {
            s.messages.clear();
            ConditionCheck.sendActionFailedMessage(s.standPower.getAbility("kingcrimson_timeskip"),
                    ConditionCheck.createNegative("stun"), s.player);
            s.premise(s.messages.size() == 1, "the message capture sees a refusal message: " + s.messages);
            s.messages.clear();
            AbilityConditionCheck protectionBefore = s.resolve(s.hamon, "hamon_protection");
            AbilityConditionCheck overdriveBefore = s.resolve(s.hamon, "hamon_overdrive");
            s.premise(protectionBefore != null && protectionBefore.conditionCheck.isPositive()
                    && overdriveBefore != null && overdriveBefore.conditionCheck.isPositive(),
                    "Hamon Protection and Hamon Overdrive can be used before the skip: " + describe(protectionBefore)
                            + ", " + describe(overdriveBefore));

            Ability timeSkip = s.standPower.getAbility("kingcrimson_timeskip");
            BufferingState skip = s.press(s.standPower, "kingcrimson_timeskip", SKIP_KEY);
            EntityActionInstance action = s.standPowerSlot().getAction();
            helper.assertTrue(skip.isActionSuccess
                    && KingCrimsonAddon.temporalState(s.player).isSkipping(s.player.level().getGameTime())
                    && action instanceof KingCrimsonTimeSkipAbility.TimeSkipPlayerAction
                    && action.ability == timeSkip && action.getPhase() == ActionPhase.PERFORM
                    && action.getPowerUser() == s.player && s.own().getAction() == null,
                    "1.16's time skip set its player action as it performed: skipped " + skip.isActionSuccess
                            + ", the user's Stand-power action " + action + ", its own action " + s.own().getAction());

            s.messages.clear();
            boolean protectionOn = HamonProtectionAbility.isProtectionEnabled(s.hamon);
            AbilityConditionCheck protectionKey = s.resolve(s.hamon, "hamon_protection");
            BufferingState protection = s.press(s.hamon, "hamon_protection", PROTECTION_KEY);
            helper.assertTrue(!protection.isActionSuccess && !protection.shouldBuffer
                    && HamonProtectionAbility.isProtectionEnabled(s.hamon) == protectionOn && s.messages.isEmpty()
                    && protectionKey != null && protectionKey.ability.isHeldBackByHeldAction(s.player),
                    "1.16 refused Hamon Protection during the time skip's player action, without a message, and drew it"
                            + " unavailable: started " + protection.isActionSuccess + ", buffered " + protection.shouldBuffer
                            + ", toggled " + (HamonProtectionAbility.isProtectionEnabled(s.hamon) != protectionOn)
                            + ", messages " + s.messages + ", drawn unavailable "
                            + (protectionKey != null && protectionKey.ability.isHeldBackByHeldAction(s.player)));

            BufferingState overdrive = s.press(s.hamon, "hamon_overdrive", OVERDRIVE_KEY);
            helper.assertTrue(!overdrive.isActionSuccess && !overdrive.shouldBuffer && s.own().getAction() == null
                    && s.messages.isEmpty(),
                    "1.16 refused Hamon Overdrive during the time skip's player action, without a message (no slot of"
                            + " the skip's holds it back): started " + overdrive.isActionSuccess + ", buffered "
                            + overdrive.shouldBuffer + ", the user's action " + s.own().getAction() + ", messages "
                            + s.messages);

            helper.assertTrue(action.isPlayerAction(), "the time skip's action is no player action");

            s.player.setYRot(90.0F);
            s.player.yBodyRot = 0.0F;
            s.standPowerSlot().tick();
            int ticked = 1;
            helper.assertTrue(s.standPowerSlot().getAction() == action && s.player.yBodyRot == 0.0F,
                    "the skip's player action ticks without turning its user's body: action " + s.standPowerSlot().getAction()
                            + ", body yaw " + s.player.yBodyRot);

            EntityActionInstance clientCopy = s.clientCopyOf(action);
            helper.assertTrue(clientCopy instanceof KingCrimsonTimeSkipAbility.TimeSkipPlayerAction
                    && clientCopy.isPlayerAction() && clientCopy.ability instanceof KingCrimsonTimeSkipAbility,
                    "the user's client's copy of the skip's action is no player action: " + clientCopy);

            StandEntity stand = s.summon();
            s.messages.clear();
            Ability punchAbility = s.standPower.getAbility("punch");
            BufferingState punch = s.press(s.standPower, "punch", PUNCH_KEY);
            EntityActionInstance punchAction = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(punch.isActionSuccess && !punch.shouldBuffer && punchAction != null
                    && !punchAction.isOver() && s.standPowerSlot().getAction() == action
                    && !punchAbility.isHeldBackByHeldAction(s.player) && s.messages.isEmpty(),
                    "1.16 let King Crimson's punch through the time skip's player action (StandAction"
                            + ".canBeUsedDuringPlayerAction): started " + punch.isActionSuccess + ", buffered "
                            + punch.shouldBuffer + ", the Stand's action " + punchAction + ", the skip's action kept "
                            + (s.standPowerSlot().getAction() == action) + ", messages " + s.messages);
            helper.assertTrue(action.getPerformer() == stand
                    && LivingComponentAction.getPoseComponent(s.player) != s.standPowerSlot(),
                    "with King Crimson out the skip's action is the Stand's, as 1.16 StandAction.getPerformer, and does not"
                            + " pose its user: performer " + action.getPerformer());

            for (; ticked < KingCrimsonPhase5Spec.TIME_SKIP_INVULNERABLE_TICKS - 1; ticked++) {
                s.standPowerSlot().tick();
            }
            helper.assertTrue(s.standPowerSlot().getAction() == action && !action.isOver(),
                    "1.16's time skip kept its player action for " + KingCrimsonPhase5Spec.TIME_SKIP_INVULNERABLE_TICKS
                            + " ticks: over after " + ticked + " ticks");
            s.standPowerSlot().tick();
            helper.assertTrue(s.standPowerSlot().getAction() == null && action.isOver(),
                    "1.16's time skip stopped its player action after " + KingCrimsonPhase5Spec.TIME_SKIP_INVULNERABLE_TICKS
                            + " ticks (invTicks): still " + s.standPowerSlot().getAction());

            AbilityConditionCheck overdriveAfter = s.resolve(s.hamon, "hamon_overdrive");
            BufferingState protectionAfter = s.press(s.hamon, "hamon_protection", PROTECTION_KEY);
            helper.assertTrue(protectionAfter.isActionSuccess
                    && HamonProtectionAbility.isProtectionEnabled(s.hamon) != protectionOn
                    && overdriveAfter != null && overdriveAfter.conditionCheck.isPositive(),
                    "once the time skip's player action is over 1.16 took Hamon again: Protection toggled "
                            + (HamonProtectionAbility.isProtectionEnabled(s.hamon) != protectionOn)
                            + ", Overdrive usable " + (overdriveAfter != null && overdriveAfter.conditionCheck.isPositive()));
        }
        helper.succeed();
    }

    /**
     * 1.16 KINGCRIMSON_TIMESKIP was a StandAction: PowerBaseImpl.onClickAction performed it and then called
     * stopHeldAction(false), which ended the power's held action (the guard, a barrage: holdType, so
     * heldAllowsOtherAction let the skip through). Core Ability#endsOtherHoldOnPress, true for a 1.16 action.
     */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_time_skip_ends_hold")
    public static void aTimeSkipGoingOffEndsAHeldGuardAndBarrageAsIn116(GameTestHelper helper) {
        try (Scene s = new Scene(helper)) {
            StandEntity stand = s.summon();
            for (String name : new String[] {"guard", "barrage"}) {
                short key = name.equals("guard") ? GUARD_KEY : BARRAGE_KEY;
                s.standPower.setStamina(s.standPower.getMaxStamina());
                s.standPower.setAbilityCooldown(SKIP, 0, 0);
                // at a high Resolve the barrage is open to a hurt user only (barrage_health)
                s.player.setHealth(1.0F);
                BufferingState held = s.hold(s.standPower, name, key);
                EntityActionInstance hold = LivingComponentAction.getCurEntityAction(stand);
                s.premise(held.isActionSuccess && hold != null && hold.ability == s.standPower.getAbility(name)
                                && AbilityInput.isHeldByKey(s.player, hold),
                        "the " + name + " is held by its key: started " + held.isActionSuccess + ", " + hold + ", "
                                + describe(s.resolve(s.standPower, name)));
                BufferingState skip = s.press(s.standPower, SKIP, SKIP_KEY);
                helper.assertTrue(skip.isActionSuccess && !AbilityInput.isHeldByKey(s.player, hold)
                                && !s.input().heldKeys.containsKey(key),
                        "1.16 ended the held " + name + " when the time skip went off (stopHeldAction(false)): skipped "
                                + skip.isActionSuccess + ", still held by its key "
                                + AbilityInput.isHeldByKey(s.player, hold) + ", its key still down "
                                + s.input().heldKeys.containsKey(key));
                s.input().heldKeys.clear();
                KingCrimsonTemporalStateService.terminate(s.player);
                s.standPowerSlot().setAction(null, SyncType.NO_SYNC);
                LivingComponentAction.getComponent(stand).setAction(null, s.player, SyncType.NO_SYNC);
            }
        }
        helper.succeed();
    }

    /**
     * 1.16 ContinuousActionInstance took actionCooldown = getCooldown(power, -1) as the skip's action was created
     * (:29-37) and, on the server, set it again whenever the action stopped (onStop :105-109): the cooldown
     * PowerBaseImpl.performAction set at the press (:333-335) restarted as the 10 ticks ended, so in Survival it ran out
     * 70 ticks after the press. In Creative and Resolve getCooldown was 0 and nothing was set.
     */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_time_skip_cooldown_restart")
    public static void theTimeSkipCooldownRestartsAsItsPlayerActionStopsAsIn116(GameTestHelper helper) {
        int expected = KingCrimsonConfig.timeSkipCooldownTicks();
        int length = KingCrimsonPhase5Spec.TIME_SKIP_INVULNERABLE_TICKS;
        helper.assertTrue(expected > 0, "premise: the time skip has a cooldown in Survival: " + expected);
        try (Scene s = new Scene(helper)) {
            EntityActionInstance action = s.pressSkip(expected);
            int ticked = 0;
            for (; ticked < length - 1; ticked++) {
                s.standPowerSlot().tick();
                s.standPower.tick();
            }
            int running = s.cooldown();
            helper.assertTrue(s.standPowerSlot().getAction() == action && running == expected - ticked,
                    "the skip's cooldown runs during its player action: action " + s.standPowerSlot().getAction()
                            + ", cooldown " + running + " after " + ticked + " ticks");
            s.standPowerSlot().tick();
            float ratio = s.standPower.getAbilityCooldownRatio(SKIP, 0.0F);
            helper.assertTrue(s.standPowerSlot().getAction() == null && s.cooldown() == expected && ratio == 1.0F,
                    "1.16 set the time skip's cooldown again as its player action stopped after " + length
                            + " ticks (onStop): action " + s.standPowerSlot().getAction() + ", cooldown " + s.cooldown()
                            + " (" + running + " a tick before), full " + ratio + ", expected " + expected);
        }

        // cut short, and with the user in Creative by then: the value taken at the start is the one set
        try (Scene s = new Scene(helper)) {
            EntityActionInstance action = s.pressSkip(expected);
            for (int ticked = 0; ticked < 3; ticked++) {
                s.standPowerSlot().tick();
                s.standPower.tick();
            }
            s.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            s.premise(s.standPower.isUserCreative() && s.timeSkip().getCooldown(s.standPower, -1) == 0,
                    "a Creative user takes no time skip cooldown");
            action.forceStop();
            s.standPowerSlot().tick();
            helper.assertTrue(s.standPowerSlot().getAction() == null && s.cooldown() == expected,
                    "1.16 set the cooldown taken at the start again when the skip's player action was cut short: action "
                            + s.standPowerSlot().getAction() + ", cooldown " + s.cooldown() + ", expected " + expected);
        }

        for (boolean creative : new boolean[] {true, false}) {
            try (Scene s = new Scene(helper)) {
                if (creative) {
                    s.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                }
                else {
                    s.player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
                }
                s.pressSkip(0);
                for (int ticked = 0; ticked < length; ticked++) {
                    s.standPowerSlot().tick();
                }
                helper.assertTrue(s.standPowerSlot().getAction() == null && s.cooldown() == 0,
                        "1.16 set no time skip cooldown " + (creative ? "in Creative" : "in Resolve")
                                + " when its player action stopped: action " + s.standPowerSlot().getAction()
                                + ", cooldown " + s.cooldown());
            }
        }
        helper.succeed();
    }

    private static String describe(AbilityConditionCheck container) {
        return container == null ? "no key" : container.ability.name() + " " + container.conditionCheck.isPositive()
                + " " + container.conditionCheck.getWarning();
    }

    /** A Survival King Crimson user with Hamon, standing on a block and looking down, so the skip moves it nowhere. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final StandPower standPower;
        final PlayerPower hamon;
        final Cow cow;
        final List<Component> messages = new ArrayList<>();

        Scene(GameTestHelper helper) {
            this.helper = helper;
            var level = helper.getLevel();
            GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-skip-gate");
            player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(level.getServer(), connection, player,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) { capture(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { capture(packet); }
            };
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            BlockPos spawn = helper.absolutePos(new BlockPos(1, 2, 1));
            player.moveTo(Vec3.atBottomCenterOf(spawn).x, spawn.getY(), Vec3.atBottomCenterOf(spawn).z, 0.0F, 90.0F);
            level.addNewPlayer(player);
            standPower = PowerClass.STAND.attachGet(player);
            standPower.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            standPower.skipProgression();
            standPower.setStamina(standPower.getMaxStamina());
            hamon = PowerClass.PLAYER_POWER.attachGet(player);
            hamon.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData data = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
            data.learnSkill(ModHamonSkills.OVERDRIVE.get());
            data.learnSkill(ModHamonSkills.PROTECTION.get());
            data.setBreathStability(data.getMaxBreathStability());
            data.setEnergy(data.getMaxEnergy());
            // Hamon Overdrive needs an entity aimed at (1.16 TargetRequirement.ENTITY)
            cow = EntityType.COW.create(level);
            premise(cow != null, "cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(player.getX(), player.getY(), player.getZ() + 1.5D, 180.0F, 0.0F);
            premise(level.addFreshEntity(cow), "cow added");
            own().entityAim.setTarget(new ActionTarget(cow));
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chat) messages.add(chat.content());
            else if (packet instanceof ClientboundSetActionBarTextPacket bar) messages.add(bar.text());
        }

        LivingComponentAction own() {
            return LivingComponentAction.getComponent(player);
        }

        LivingComponentAction standPowerSlot() {
            return LivingComponentAction.getComponent(player, PowerClass.STAND);
        }

        Ability timeSkip() {
            return standPower.getAbility(SKIP);
        }

        int cooldown() {
            return standPower.getAbilityCooldown(SKIP);
        }

        EntityActionInstance pressSkip(int expectedCooldown) {
            premise(timeSkip().getCooldown(standPower, -1) == expectedCooldown,
                    "the time skip takes " + expectedCooldown + " ticks: " + timeSkip().getCooldown(standPower, -1));
            BufferingState skip = press(standPower, SKIP, SKIP_KEY);
            EntityActionInstance action = standPowerSlot().getAction();
            premise(skip.isActionSuccess && action instanceof KingCrimsonTimeSkipAbility.TimeSkipPlayerAction
                    && cooldown() == expectedCooldown, "the skip goes off with its player action and its cooldown: "
                            + skip.isActionSuccess + ", " + action + ", cooldown " + cooldown());
            return action;
        }

        EntityActionInputState input() {
            return player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
        }

        StandEntity summon() {
            premise(KingCrimsonAddon.KING_CRIMSON.get().summon(player, standPower), "King Crimson summoned");
            StandEntity stand = standPower.getSummonedStandEntity();
            premise(stand != null, "King Crimson entity");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            return stand;
        }

        // as a press packet is handled: the key's resolved ability, its condition check, then the press
        BufferingState press(Power<?> pressed, String name, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = pressed.getAbility(name);
            premise(base != null, "missing ability " + name);
            AbilityConditionCheck container = resolve(pressed, name);
            if (container != null && AbilityInput.withConditionCheck(container, player, InputMethod.CLICK)) {
                AbilityInput.keyPress(key, container.ability, player, null, InputMethod.CLICK, 0, state,
                        base.getAbilityId());
            }
            AbilityInput.keyRelease(key, player);
            return state;
        }

        // a held key, kept down until the test releases it
        BufferingState hold(Power<?> pressed, String name, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = pressed.getAbility(name);
            premise(base != null, "missing ability " + name);
            AbilityConditionCheck container = resolve(pressed, name);
            if (container != null && AbilityInput.withConditionCheck(container, player, InputMethod.HOLD)) {
                FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AbilityInput.keyPress(key, container.ability, player, input, InputMethod.HOLD, 0, state,
                            base.getAbilityId());
                }
                finally {
                    input.release();
                }
            }
            return state;
        }

        // the power resolves its keys once a tick (Power.updateAvailableMoves); the test does not tick it
        AbilityConditionCheck resolve(Power<?> resolved, String name) {
            resolved.updateAvailableMoves().update(resolved, resolved.getMoveset());
            return resolved.updateAvailableMoves().getContextVariationContainer(name);
        }

        // the action as the user's client holds it, decoded from what the server sends (EntityActionInstance.encode)
        EntityActionInstance clientCopyOf(EntityActionInstance action) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            try {
                EntityActionInstance.encode(buf, action);
                return EntityActionInstance.decode(helper.getLevel(), buf);
            }
            finally {
                buf.release();
            }
        }

        void premise(boolean condition, String detail) {
            helper.assertTrue(condition, "premise: " + detail);
        }

        @Override
        public void close() {
            input().heldKeys.clear();
            own().entityAim.setTarget(ActionTarget.EMPTY);
            own().setAction(null, SyncType.NO_SYNC);
            standPowerSlot().setAction(null, SyncType.NO_SYNC);
            KingCrimsonTemporalStateService.terminate(player);
            StandEntity stand = standPower.getSummonedStandEntity();
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                standPower.setSummonedStand(null);
                stand.discard();
            }
            cow.discard();
            player.discard();
        }
    }
}
