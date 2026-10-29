package rotp.addons.stickyfingers.client;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.network.ZipperSinkPayload;
import rotp.addons.stickyfingers.state.ZipperPhasing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StickyFingersAddon.MOD_ID, value = Dist.CLIENT)
public final class StickyFingersSinkClient {
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static boolean wasDown;
    private static boolean sinking;
    private static int firstPress = -1;
    private static int previousMovement;

    private StickyFingersSinkClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player != owner || minecraft.level != world) {
            ZipperPhasing.clearClientSinking();
            owner = player;
            world = minecraft.level;
            wasDown = minecraft.options.keyShift.isDown();
            sinking = false;
            firstPress = -1;
            previousMovement = 0;
        }
        if (player == null || minecraft.level == null) {
            return;
        }
        boolean down = minecraft.options.keyShift.isDown();
        int movement = (minecraft.options.keyUp.isDown() ? 1 : 0)
                | (minecraft.options.keyDown.isDown() ? 2 : 0)
                | (minecraft.options.keyLeft.isDown() ? 4 : 0)
                | (minecraft.options.keyRight.isDown() ? 8 : 0);
        boolean jumping = minecraft.options.keyJump.isDown();
        boolean eligible = ZipperPhasing.canSink(player);
        boolean next = sinking && down && eligible;
        if (!eligible || jumping || movement != previousMovement
                || (firstPress >= 0 && player.tickCount - firstPress >= 7)) {
            firstPress = -1;
        }
        if (eligible && !jumping && down && !wasDown && !sinking) {
            if (firstPress >= 0) {
                // Donor sendOnPress: the second press counts only on the ground.
                next = player.onGround();
                firstPress = -1;
            } else {
                firstPress = player.tickCount;
            }
        }
        if (next != sinking) {
            sinking = next;
            ZipperPhasing.setClientSinking(player, next);
            PacketDistributor.sendToServer(new ZipperSinkPayload(next));
        }
        wasDown = down;
        previousMovement = movement;
    }
}
