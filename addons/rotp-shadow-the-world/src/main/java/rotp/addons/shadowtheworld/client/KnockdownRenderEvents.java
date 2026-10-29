package rotp.addons.shadowtheworld.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 1.16 ClientEventHandler: a knocked-down player is drawn lying on its
 * side. A pose cannot do it: Player.updatePlayerPose resets SLEEPING.
 */
@EventBusSubscriber(
        modid = ShadowTheWorldAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KnockdownRenderEvents {
    // yaw locked when a knockdown starts, per player and effect instance
    private record Lock(MobEffectInstance effect, float yaw) {}

    private static final Map<Player, Lock> LOCKS = new WeakHashMap<>();
    private static final Set<Player> PUSHED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private KnockdownRenderEvents() {}

    // LOWEST: a cancelled Pre never gets here, so Post pops every push
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        MobEffectInstance knockdown =
                player.getEffect(ShadowTheWorldAddon.KNOCKDOWN);
        if (knockdown == null) {
            LOCKS.remove(player);
            return;
        }
        Lock lock = LOCKS.get(player);
        if (lock == null || lock.effect() != knockdown) {
            lock = new Lock(knockdown, player.getYRot());
            LOCKS.put(player, lock);
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.9D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-lock.yaw()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.translate(-1.0D, -0.9D, 0.0D);
        PUSHED.add(player);
    }

    // HIGHEST: pop before other mods' Post handlers, mirroring Pre
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (PUSHED.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }
}
