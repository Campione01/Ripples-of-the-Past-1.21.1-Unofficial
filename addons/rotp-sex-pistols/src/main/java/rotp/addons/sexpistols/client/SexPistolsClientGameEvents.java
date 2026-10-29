package rotp.addons.sexpistols.client;

import java.util.OptionalInt;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entityglow.EntityGlowChannel;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMemberState;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class SexPistolsClientGameEvents {
    private static final int SCOUT_GLOW_COLOR = 0xE75D2F;
    private static final int SCOUT_GLOW_TICKS = 30;

    private SexPistolsClientGameEvents() {}

    @SubscribeEvent
    public static void onLogin(
            ClientPlayerNetworkEvent.LoggingIn event) {
        SexPistolsClientConfigSnapshot.beginSession(
                event.getConnection());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SexPistolsClientConfigSnapshot.endSession(null);
        RevolverVisualClientHandler.tickPendingKickFlashes();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.level == null
                || !player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !power.isSummoned()) {
            return;
        }

        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(player);
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            Entity entity = minecraft.level.getEntity(
                    coordinator.childEntityId(number));
            if (!(entity instanceof SexPistolsEntity member)
                    || member.getMemberState()
                            != SexPistolsMemberState.SCOUTING
                    || StandVisualContext.world(member, 1.0F)
                            .effectiveAlpha() <= 0.0F) {
                continue;
            }
            EntityGlowChannel.STAND_VIEW_OBSTRUCTION.apply(
                    member,
                    OptionalInt.of(SCOUT_GLOW_COLOR),
                    SCOUT_GLOW_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        SexPistolsClientConfigSnapshot.endSession(
                event.getConnection());
        RevolverVisualClientHandler.clearPendingKickFlashes();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            RevolverVisualClientHandler.clearPendingKickFlashes();
            SexPistolsVisualDiagnostics.recordLevelUnloadCleanup();
        }
    }
}
