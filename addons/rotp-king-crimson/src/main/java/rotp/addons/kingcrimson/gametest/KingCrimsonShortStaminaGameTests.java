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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.EpitaphStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 tested no stamina before these King Crimson abilities: StandAction.onPerform and StandEntityTask paid the cost
 * and ignored the result, so a short bar dropped to 0 and the ability still ran, with no stamina message. Only Time
 * Erase's own upkeep (KingCrimsonTimeErase.standTickPerform) ended the erase, silently, once a tick could not be paid.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonShortStaminaGameTests {
    private static final short INPUT_KEY = 217;
    private static final String STAMINA_MESSAGE = "jojo.message.action_condition.not_enough_stamina";

    private KingCrimsonShortStaminaGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_short_stamina")
    public static void kingCrimsonAbilitiesRunOnAShortBar(GameTestHelper helper) {
        var checks = new KingCrimsonGroundGameTestFixture.Checks();

        try (var scene = new Scene(helper, "probe")) {
            ConditionCheck.sendActionFailedMessage(scene.power.getAbility("kingcrimson_timeskip"),
                    ConditionCheck.createNegative("not_enough_stamina"), scene.player);
            scene.premise(scene.staminaMessages() == 1, "The message capture sees a stamina refusal");
        }

        for (float stamina : new float[] {0, 100}) {
            String id = "S1/timeskip/" + (int) stamina;
            try (var scene = new Scene(helper, id)) {
                scene.power.setStamina(stamina);
                Ability timeSkip = scene.power.getAbility("kingcrimson_timeskip");
                checks.check(timeSkip.checkConditions(scene.power).isPositive(), id + " conditions pass on a short bar");
                BufferingState state = scene.press(timeSkip, InputMethod.CLICK);
                checks.check(state.isActionSuccess && !KingCrimsonAddon.temporalState(scene.player).isIdle()
                                && scene.power.getAbilityCooldown("kingcrimson_timeskip") > 0,
                        id + " Time Skip starts and goes on cooldown: success=" + state.isActionSuccess
                                + " mode=" + KingCrimsonAddon.temporalState(scene.player).mode()
                                + " cooldown=" + scene.power.getAbilityCooldown("kingcrimson_timeskip"));
                checks.check(scene.power.getStamina() <= 0.01F && scene.staminaMessages() == 0,
                        id + " the cost drops the bar to 0 with no message: stamina=" + scene.power.getStamina()
                                + " messages=" + scene.staminaMessages());
            }
        }

        float activation = KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA;
        for (float stamina : new float[] {0, activation + 0.2F, activation + 50}) {
            boolean shortBar = stamina < activation;
            boolean upkeepFails = stamina < activation + KingCrimsonConfig.timeEraseStaminaPerTick();
            String id = "S2/timeerase/" + stamina;
            try (var scene = new Scene(helper, id)) {
                StandEntity stand = scene.summon();
                scene.power.setStamina(stamina);
                Ability erase = scene.power.getAbility("kingcrimson_timeerase");
                checks.check(erase.checkConditions(scene.power).isPositive(), id + " conditions pass");
                scene.press(erase, InputMethod.HOLD);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                scene.premise(action != null && action.ability == erase, "Time Erase action installed");
                for (int tick = 0; tick < 30 && !action.isOver()
                        && !KingCrimsonAddon.temporalState(scene.player).isErasing(scene.gameTime()); tick++) {
                    action._tickAction();
                }
                float paid = stamina - scene.power.getStamina();
                checks.check(KingCrimsonAddon.temporalState(scene.player).isErasing(scene.gameTime()),
                        id + " Time Erase begins: phase=" + action.getPhase());
                checks.check(shortBar ? scene.power.getStamina() <= 0.01F
                                : KingCrimsonGroundGameTestFixture.near(paid, activation),
                        id + " the activation cost is paid: stamina=" + scene.power.getStamina());
                KingCrimsonTemporalStateService.tick(scene.player);
                boolean ended = KingCrimsonAddon.temporalState(scene.player).isIdle();
                checks.check(ended == upkeepFails && scene.staminaMessages() == 0,
                        id + (upkeepFails ? " the unpaid upkeep ends the erase" : " the paid upkeep keeps the erase")
                                + " without a message: ended=" + ended + " messages=" + scene.staminaMessages());
            }
        }

        // 1.16 KingCrimsonEpitaph is a hold-only StandAction: PowerBaseImpl only set it as the held action and
        // stopHeldAction never performed it, so StandAction.onPerform never paid its staminaCost(175). It had no
        // stamina condition either, so it arms on an empty bar too.
        for (float stamina : new float[] {0, 300}) {
            try (var scene = new Scene(helper, "S3/epitaph/" + (int) stamina)) {
                StandEntity stand = scene.summon();
                scene.power.setStamina(stamina);
                Ability epitaph = scene.power.getAbility("kingcrimson_epitaph");
                checks.check(epitaph.checkConditions(scene.power).isPositive(), scene.id + " conditions pass");
                scene.press(epitaph, InputMethod.HOLD);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                scene.premise(action != null && action.ability == epitaph, "Epitaph action installed");
                for (int tick = 0; tick < 30 && !action.isOver()
                        && !KingCrimsonAddon.epitaphState(scene.player).isArmed(scene.gameTime()); tick++) {
                    action._tickAction();
                }
                checks.check(KingCrimsonAddon.epitaphState(scene.player).isArmed(scene.gameTime())
                                && scene.power.getAbilityCooldown("kingcrimson_epitaph") > 0,
                        scene.id + " Epitaph arms and goes on cooldown: phase=" + action.getPhase()
                                + " cooldown=" + scene.power.getAbilityCooldown("kingcrimson_epitaph"));
                checks.check(scene.power.getStamina() == stamina && scene.staminaMessages() == 0,
                        scene.id + " arming takes no stamina and shows no message: stamina=" + scene.power.getStamina()
                                + " messages=" + scene.staminaMessages());
                // Owner rule of 2026-10-15: only a Stand's guard slows its user, so Epitaph holds no hidden Slowness II
                for (int tick = 0; tick < 5 && !action.isOver(); tick++) {
                    action._tickAction();
                }
                checks.check(!action.isOver() && !scene.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                && action.userWalkSpeed == 1.0F,
                        scene.id + " the holder keeps full speed: over=" + action.isOver()
                                + " slowness=" + scene.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                + " walk=" + action.userWalkSpeed);
            }
        }

        for (String strike : new String[] {"kingcrimson_chop", "kingcrimson_impale"}) {
            String id = "S4/" + strike + "/0";
            try (var scene = new Scene(helper, id)) {
                StandEntity stand = scene.summon();
                scene.power.setStamina(0);
                Ability ability = scene.power.getAbility(strike);
                checks.check(ability.checkConditions(scene.power).isPositive(), id + " conditions pass on an empty bar");
                scene.press(ability, InputMethod.CLICK);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                checks.check(action != null && action.ability == ability, id + " the strike starts");
                if (action != null) {
                    for (int tick = 0; tick < 60 && !action.isOver() && action.punchedTarget == null; tick++) {
                        action._tickAction();
                    }
                    checks.check(action.punchedTarget != null && scene.power.getStamina() <= 0.01F
                                    && scene.staminaMessages() == 0,
                            id + " PERFORM runs and pays with no message: performed=" + (action.punchedTarget != null)
                                    + " stamina=" + scene.power.getStamina() + " messages=" + scene.staminaMessages());
                }
            }
        }
        checks.finish(helper, "KC-SHORT-STAMINA", 9);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final String id;
        final ServerPlayer player;
        final StandPower power;
        final List<Component> messages = new ArrayList<>();

        Scene(GameTestHelper helper, String id) {
            this.helper = helper;
            this.id = id;
            var level = helper.getLevel();
            GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-stamina");
            player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(level.getServer(), connection, player,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) { capture(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { capture(packet); }
            };
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            BlockPos spawn = helper.absolutePos(new BlockPos(1, 2, 1));
            player.moveTo(Vec3.atBottomCenterOf(spawn));
            level.addNewPlayer(player);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chat) messages.add(chat.content());
            else if (packet instanceof ClientboundSetActionBarTextPacket bar) messages.add(bar.text());
        }

        long staminaMessages() {
            return messages.stream().filter(message -> message.getContents() instanceof TranslatableContents contents
                    && STAMINA_MESSAGE.equals(contents.getKey())).count();
        }

        long gameTime() {
            return player.level().getGameTime();
        }

        StandEntity summon() {
            premise(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "King Crimson summoned");
            StandEntity stand = power.getSummonedStandEntity();
            premise(stand != null, "King Crimson entity");
            stand.summonLockTicks = 0;
            return stand;
        }

        BufferingState press(Ability ability, InputMethod method) {
            BufferingState state = BufferingState.clickCanBuffer();
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(INPUT_KEY, ability, player, input, method, 0.0F, state, ability.getAbilityId());
            } finally {
                input.release();
            }
            return state;
        }

        void premise(boolean condition, String detail) {
            helper.assertTrue(condition, id + " premise: " + detail);
        }

        @Override
        public void close() {
            KingCrimsonTemporalStateService.terminate(player);
            EpitaphStateService.terminateIfArmed(player);
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                power.setSummonedStand(null);
                stand.discard();
            }
            player.discard();
        }
    }
}
