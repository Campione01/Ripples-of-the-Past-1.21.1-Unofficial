package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonDonorGameplaySpec;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;

/**
 * 1.16 KC barrages are hold-only actions: PowerBaseImpl.stopHeldAction sets their 60-tick cooldown when the hold
 * ends, and tickHeldAction runs their health gate (checkStandConditions) on every held tick.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonBarrageHoldGameTests {
    private static final short HOLD_KEY = 194;
    private static final String NORMAL = "barrage";
    private static final String DOPPIO = "kingcrimson_doppio_barrage";
    private static final float LOW_HEALTH = 60;
    private static final float HIGH_HEALTH = 150;
    private KingCrimsonBarrageHoldGameTests() {}

    enum End { RELEASE, MAX_DURATION, CLEARED, CREATIVE_AT_END, RESOLVE_AT_END, SURVIVAL_AT_END, HEALED, LOW_HEALTH_KEPT, REPLAYED }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_barrage_hold")
    public static void barrageCooldownAndHealthGateFollowTheHold(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonBarrageHoldGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        int scenes = 0;
        for (String variant : List.of(NORMAL, DOPPIO)) {
            for (End end : End.values()) {
                scene(helper, checks, variant, end);
                scenes++;
            }
        }
        checks.finish(helper, "KC-BARRAGE-HOLD", scenes);
    }

    private static void scene(GameTestHelper helper, KingCrimsonGroundGameTestFixture.Checks checks, String variant, End end) {
        String id = "B1/" + variant + "/" + end;
        try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
            try { run(checks, scene, id, variant, end); }
            finally { AbilityInput.keyRelease(HOLD_KEY, scene.user); }
        }
    }

    private static void run(KingCrimsonGroundGameTestFixture.Checks checks, KingCrimsonGroundGameTestFixture scene,
            String id, String variant, End end) {
        Recording connection = new Recording(scene.user);
        if (variant.equals(DOPPIO)) scene.stand.setArmsOnlyMode(true, true);
        scene.user.setHealth(LOW_HEALTH);
        if (end == End.SURVIVAL_AT_END) scene.user.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        int cooldown = KingCrimsonDonorGameplaySpec.BARRAGE_COOLDOWN_TICKS;
        Ability base = scene.power.getAbility(NORMAL);
        var selected = scene.power.updateAvailableMoves().getContextVariationContainer(base);
        scene.premise(selected != null && selected.ability.name().equals(variant)
                && AbilityInput.withConditionCheck(selected, scene.user, InputMethod.HOLD), "Registered barrage admission " + variant);
        Ability barrage = selected.ability;
        EntityActionInstance action;
        if (end == End.REPLAYED) {
            // What ActionInputBuffer.tickInputBuffer calls for a buffered press.
            BufferingState replay = BufferingState.buffered();
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try { barrage.onKeyPress(scene.level, scene.user, input, InputMethod.HOLD, 0, replay); }
            finally { input.release(); }
            action = scene.stand.getCurStandAction();
            scene.premise(replay.isActionSuccess && action != null && action.ability == barrage, "Buffered replay started the barrage");
        }
        else {
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(HOLD_KEY, barrage, scene.user, null, InputMethod.HOLD, 0, buffer, base.getAbilityId());
            action = scene.stand.getCurStandAction();
            scene.premise(buffer.isActionSuccess && action != null && action.ability == barrage, "Held barrage started");
        }
        checks.check(scene.power.getAbilityCooldown(variant) == 0,
                id + " no cooldown when the hold starts: " + scene.power.getAbilityCooldown(variant));
        for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 40; tick++) action._tickAction();
        scene.premise(action.getPhase() == ActionPhase.PERFORM, "Barrage reached PERFORM");
        for (int tick = 0; tick < 5; tick++) action._tickAction();
        scene.premise(action.getPhase() == ActionPhase.PERFORM, "Barrage still held");
        checks.check(scene.power.getAbilityCooldown(variant) == 0,
                id + " no cooldown while the barrage is held: " + scene.power.getAbilityCooldown(variant));
        int expected = cooldown;
        switch (end) {
            case RELEASE, REPLAYED -> release(scene, action, end);
            case MAX_DURATION -> {
                for (int tick = 0; action.getPhase() == ActionPhase.PERFORM && tick < 2000; tick++) action._tickAction();
                scene.premise(action.getPhase() != ActionPhase.PERFORM, "Barrage ran to its maximum duration");
            }
            case CLEARED -> LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            case CREATIVE_AT_END -> {
                scene.user.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                release(scene, action, end);
                expected = 0;
            }
            case RESOLVE_AT_END -> {
                scene.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 1200, 0));
                release(scene, action, end);
                expected = 0;
            }
            case SURVIVAL_AT_END -> {
                scene.user.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                release(scene, action, end);
            }
            case HEALED, LOW_HEALTH_KEPT -> {
                if (end == End.HEALED) scene.user.setHealth(HIGH_HEALTH);
                connection.packets.clear();
                action._tickAction();
                boolean stopped = action.getPhase() != ActionPhase.PERFORM;
                String key = "jojo.message.action_condition.rotp_kingcrimson.message."
                        + (variant.equals(DOPPIO) ? "barrage_health_doppio" : "barrage_health");
                if (end == End.HEALED) {
                    checks.check(stopped, id + " healing above the threshold stops the held barrage: " + action.getPhase());
                    checks.check(messages(connection, key) == 1, id + " the stop sends " + key + ": " + messages(connection, key));
                }
                else {
                    checks.check(!stopped && messages(connection, key) == 0, id + " a low-health hold goes on: " + action.getPhase());
                    expected = 0;
                }
            }
        }
        checks.check(scene.power.getAbilityCooldown(variant) == expected,
                id + " cooldown when the hold ends=" + scene.power.getAbilityCooldown(variant) + " expected=" + expected
                        + " phase=" + action.getPhase());
    }

    private static void release(KingCrimsonGroundGameTestFixture scene, EntityActionInstance action, End end) {
        if (end == End.REPLAYED) action.onKeyRelease(scene.user);
        else AbilityInput.keyRelease(HOLD_KEY, scene.user);
        scene.premise(action.getPhase() != ActionPhase.PERFORM, "Release ended the hold");
    }

    private static int messages(Recording connection, String key) {
        int count = 0;
        for (Packet<?> packet : connection.packets) {
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()
                    && chat.content().getContents() instanceof TranslatableContents translatable
                    && translatable.getKey().equals(key)) {
                count++;
            }
        }
        return count;
    }

    private static final class Recording extends ServerGamePacketListenerImpl {
        final List<Packet<?>> packets = new ArrayList<>();

        Recording(ServerPlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
    }
}
