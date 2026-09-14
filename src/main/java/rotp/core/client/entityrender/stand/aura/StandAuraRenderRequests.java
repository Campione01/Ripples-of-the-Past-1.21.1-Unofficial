package rotp.core.client.entityrender.stand.aura;

import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandAuraSettings;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

final class StandAuraRenderRequests {
    private static final FrameRequestQueue<Entity> REQUESTS =
            new FrameRequestQueue<>();

    private StandAuraRenderRequests() {}

    static void queue(Entity entity, Integer color) {
        REQUESTS.queue(entity, color);
    }

    static Decision decisionFor(Entity entity) {
        FrameRequestQueue.Request request = REQUESTS.consume(entity);
        StandAuraSettings settings = settings();
        if (!settings.enabled) {
            return new Decision(false, null);
        }
        if (request != null) {
            return new Decision(true, request.color());
        }

        LivingEntity owner = entity instanceof StandEntity stand
                ? stand.getUser()
                : entity instanceof LivingEntity living ? living : null;
        return new Decision(automaticFor(owner, settings), null);
    }

    private static boolean automaticFor(
            LivingEntity owner, StandAuraSettings settings) {
        if (owner == null) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return settings.automaticAura(
                ResolveModeEffect.getResolveEffectLvl(owner) >= 0,
                power != null && power.hasPower());
    }

    static void endFrame(long frameId) {
        REQUESTS.clear();
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        REQUESTS.clear();
    }

    static int queuedForTest() {
        return REQUESTS.sizeForTest();
    }

    private static StandAuraSettings settings() {
        return ClientModSettings.getSettingsReadOnly().standAura;
    }

    record Decision(boolean render, Integer color) {}
}
