package rotp.addons.stand_bearing_creatures.client;

import java.util.ArrayDeque;
import java.util.Deque;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;

@EventBusSubscriber(
        modid = MobsWithPowersAddon.MOD_ID,
        value = Dist.CLIENT)
public final class MobStandClientEvents {
    private static final ThreadLocal<Deque<Integer>> SCALED_RENDERS =
            ThreadLocal.withInitial(ArrayDeque::new);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeStandRender(
            RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof StandEntity stand)
                || !(stand.getUser() instanceof Mob owner)
                || (!owner.isBaby()
                        && owner.getBbHeight() >= 1.5F)) {
            return;
        }
        float lifecycleAlpha = stand.clientStuff.getAlpha(
                stand,
                event.getPartialTick());
        StandVisualContext visualContext =
                StandVisualContext.world(stand, lifecycleAlpha);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(0.5F, 0.5F, 0.5F);
        SCALED_RENDERS.get().push(stand.getId());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterStandRender(
            RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof StandEntity stand)) {
            return;
        }
        Deque<Integer> renders = SCALED_RENDERS.get();
        if (!renders.isEmpty() && renders.peek() == stand.getId()) {
            renders.pop();
            event.getPoseStack().popPose();
        }
        if (renders.isEmpty()) {
            SCALED_RENDERS.remove();
        }
    }

    private MobStandClientEvents() {}
}
