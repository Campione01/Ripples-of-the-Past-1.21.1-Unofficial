package rotp.addons.tickettoride.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.sounds.SoundEvent;

public final class TicketToRideClientSounds {
    private TicketToRideClientSounds() {}

    // 1.16 StandEntityAction.playSoundAtStand for an action that can be
    // cancelled: the sound follows the Stand and is cut when the phase it
    // was started in ends.
    public static void playPhaseSound(
            StandEntity stand,
            SoundEvent sound,
            EntityActionInstance action,
            ActionPhase phase) {
        if (!ClientGlobals.canHearStand(stand)) {
            return;
        }
        ClientsideSoundsHelper.playNonVanillaClassSound(
                new EntityStoppableSoundInstance(
                        ClientsideSoundsHelper.withStandSkin(
                                sound, stand),
                        stand.getSoundSource(),
                        1.0F,
                        1.0F,
                        stand,
                        stand.level().random.nextLong(),
                        () -> action.isOver()
                                || action.getPhase() != phase));
    }
}
