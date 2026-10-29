package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * 1.16 InitStands registers kingcrimson_groundpunch with standSound(Phase.WINDUP, DIAVOLO_COMBOFINISHER): a sound
 * of the Stand when its windup starts, with no sneak rule, not the user's shout.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundPunchSoundGameTests {
    private static final short INPUT = 193;
    private KingCrimsonGroundPunchSoundGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_groundpunch_sound")
    public static void comboFinisherLineIsAStandWindupSound(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundPunchSoundGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (boolean sneaking : new boolean[] {false, true}) scene(helper, checks, sneaking);
        checks.finish(helper, "KC-GROUNDPUNCH-WINDUP-SOUND", 2);
    }

    private static void scene(GameTestHelper helper, KingCrimsonGroundGameTestFixture.Checks checks, boolean sneaking) {
        String id = "W1/" + (sneaking ? "sneaking" : "standing");
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 24, 1)));
        KingCrimsonChunkTickets chunks = new KingCrimsonChunkTickets(level);
        FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "kc-finisher-sound"));
        Recording connection = new Recording(user);
        List<PlayLevelSoundEvent.AtPosition> shouts = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null && event.getSound().value() == KingCrimsonAddon.DIAVOLO_COMBOFINISHER.get()) shouts.add(event);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class, listener);
        StandPower power = null;
        StandType type = KingCrimsonAddon.KING_CRIMSON.get();
        StandEntity stand = null;
        try {
            chunks.force(origin);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            helper.assertTrue(level.addFreshEntity(user), "KC-SOUND PREMISE Stand owner added");
            // The owner's chunk counts as sent, so the owner tracks its Stand once the Stand is added.
            connection.chunkSender.dropChunk(user, user.chunkPosition());
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "KC-SOUND PREMISE Registered KC grant");
            power.skipProgression(); power.setResolveLevel(4);
            helper.assertTrue(type.summon(user, power), "KC-SOUND PREMISE Registered KC full summon");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isAddedToLevel() && !stand.isArmsOnlyMode(), "KC-SOUND PREMISE Real full KC body");
            stand.setNoGravity(true); stand.setCanFollowUser(false);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            power.setStamina(power.getMaxStamina());
            user.setShiftKeyDown(sneaking);
            stand.setFinisherMeter(0.75F);
            Ability base = power.getAbility("heavy_punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            helper.assertTrue(selected != null && selected.ability.name().equals("kingcrimson_groundpunch")
                    && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "KC-SOUND PREMISE Ground Punch admitted");
            int before = standSounds(connection, stand);
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(INPUT, selected.ability, user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess && stand.getCurStandAction() != null
                    && stand.getCurStandAction().ability == selected.ability, "KC-SOUND PREMISE Ground Punch windup started");
            int sounds = standSounds(connection, stand) - before;
            checks.check(sounds == 1, id + " one combo-finisher sound at the Stand when the windup starts: " + sounds);
            checks.check(shouts.isEmpty(), id + " no combo-finisher voice line from the user: " + shouts.size());
        }
        finally {
            AbilityInput.keyRelease(INPUT, user);
            if (stand != null) LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            user.discard();
            NeoForge.EVENT_BUS.unregister(listener);
            chunks.release();
        }
    }

    private static int standSounds(Recording connection, StandEntity stand) {
        int count = 0;
        for (Packet<?> packet : connection.packets) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof StandEntitySoundPacket sound
                    && sound.entityId() == stand.getId() && sound.sound().value() == KingCrimsonAddon.DIAVOLO_COMBOFINISHER.get()) {
                count++;
            }
        }
        return count;
    }

    private static final class Recording extends ServerGamePacketListenerImpl {
        final List<Packet<?>> packets = new ArrayList<>();

        Recording(FakePlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
    }
}
