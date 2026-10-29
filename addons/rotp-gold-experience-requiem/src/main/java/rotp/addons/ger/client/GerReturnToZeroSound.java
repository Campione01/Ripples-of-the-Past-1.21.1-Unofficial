package rotp.addons.ger.client;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class GerReturnToZeroSound
        extends AbstractTickableSoundInstance {
    private final StartPayload trigger;
    private int elapsed;

    public GerReturnToZeroSound(StartPayload trigger) {
        super(
                GoldExperienceRequiemAddon.RETURN_TO_ZERO_SOUND.get(),
                SoundSource.PLAYERS,
                RandomSource.create());
        this.trigger = trigger;
        looping = true;
        delay = 0;
        volume = 1.0F;
        pitch = 1.0F;
        x = trigger.center().x;
        y = trigger.center().y;
        z = trigger.center().z;
        elapsed = trigger.elapsedTicks();
    }

    @Override
    public void tick() {
        if (!GerRtzClientState.isCurrent(trigger)
                || elapsed >= trigger.keepTicks()) {
            stop();
            return;
        }
        elapsed++;
        int fadeOut = Math.min(
                trigger.fadeOutTicks(),
                Math.max(0, trigger.keepTicks() - 1));
        int remaining = trigger.keepTicks() - elapsed;
        volume = fadeOut > 0 && remaining < fadeOut
                ? Math.max(0.0F, remaining / (float) fadeOut)
                : 1.0F;
    }
}
