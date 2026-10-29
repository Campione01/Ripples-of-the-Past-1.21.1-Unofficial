package rotp.addons.cmoon.client;

import java.util.function.BooleanSupplier;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.sounds.SoundEvent;

/** Logical client only: callers check level().isClientSide() first. */
public final class CMoonClientSounds {
    private CMoonClientSounds() {}

    public static void playUntil(
            StandEntity stand,
            SoundEvent sound,
            BooleanSupplier stopWhen) {
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
                        stopWhen));
    }
}
