package rotp.addons.stone_legacy;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.stone_legacy.ability.PillarmanPetrifyAbility;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = StoneLegacyAddon.MOD_ID)
public final class StoneLegacyEvents {
    private StoneLegacyEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        if (!(event.getEntity()
                        instanceof ServerPlayer player)) {
            return;
        }
        PillarmanPetrifyAbility.tickPendingClear(player);
        PlayerPower power =
                PlayerPower.getOptional(player).orElse(null);
        if (power != null
                && power.getPowerType()
                        != ModPlayerPowers.PILLAR_MAN.get()
                && StoneLegacyAddon.state(player)
                        .hamonOrigin()) {
            StoneLegacyAddon.state(player)
                    .setHamonOrigin(player, false);
        }
    }
}
