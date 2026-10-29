package rotp.addons.cream.client;

import java.util.Set;

import rotp.addons.cream.CreamAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(
        modid = CreamAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class CreamClientSoundEvents {
    private static final double WEAK_NECK_MULTIPLIER = 1.55D;

    private static final Set<ResourceLocation> ALLOWED_SOUNDS =
            Set.of(
                    CreamAddon.id("cream_void_form_short"),
                    CreamAddon.id("cream_void"),
                    CreamAddon.id("cream_void_start"),
                    CreamAddon.id("cream_void_end"));
    private static Double sensitivityLease;

    private CreamClientSoundEvents() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.hasEffect(
                        CreamAddon.TRUE_BLINDNESS)
                && !ALLOWED_SOUNDS.contains(
                        event.getOriginalSound().getLocation())) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean weakNeck = minecraft.player != null
                && minecraft.player.hasEffect(
                        CreamAddon.WEAK_NECK);
        if (weakNeck) {
            if (sensitivityLease == null) {
                sensitivityLease =
                        minecraft.options.sensitivity().get();
            }
            minecraft.options.sensitivity().set(
                    Math.min(
                            1.0D,
                            sensitivityLease
                                    * WEAK_NECK_MULTIPLIER));
        }
        else if (sensitivityLease != null) {
            minecraft.options.sensitivity().set(
                    sensitivityLease);
            sensitivityLease = null;
        }
    }
}
