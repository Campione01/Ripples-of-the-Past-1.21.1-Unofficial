package rotp.addons.tusk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;
import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.network.TuskPortalAcceleratePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TuskAddon.MOD_ID, value = Dist.CLIENT)
public final class TuskClientGameEvents {
    private static final DoubleCrouchDetector DOUBLE_CROUCH =
            new DoubleCrouchDetector();

    private TuskClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.isPaused()
                || player.input == null) {
            DOUBLE_CROUCH.reset();
            return;
        }
        int presses = DOUBLE_CROUCH.inputUpdate(player.input);
        if (presses == 2
                && player.isAlive()
                && player.onGround()
                && TuskAddon.isPortalActUser(player)) {
            PacketDistributor.sendToServer(
                    TuskPortalAcceleratePayload.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void hideFirstPersonArm(RenderArmEvent event) {
        var arm = TuskArmPortalLayer.findArm(event.getPlayer());
        if (arm == null || arm.armSide() != event.getArm()) {
            return;
        }
        TuskArmPortalLayer.renderFirstPersonPortal(
                arm,
                event.getArm(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                Minecraft.getInstance().getTimer()
                        .getGameTimeDeltaPartialTick(true));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideThirdPersonArm(
            RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        var arm = TuskArmPortalLayer.findArm(player);
        if (arm == null) {
            return;
        }
        var model = event.getRenderer().getModel();
        if (arm.isLeftArm()) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.LEFT_ARM,
                    Part.LEFT_SLEEVE);
        } else {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.RIGHT_ARM,
                    Part.RIGHT_SLEEVE);
        }
    }

    // Donor 058 ClientEvents.infiniteRotation: a victim of the tracked
    // effect spins partialTick * -225 degrees around Y and shakes on X/Z.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void spinInfiniteRotation(
            RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(TuskAddon.INFINITE_ROTATION)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.mulPose(Axis.YP.rotationDegrees(
                event.getPartialTick() * -225.0F));
        RandomSource random = entity.level().getRandom();
        poseStack.translate(
                (random.nextFloat() - random.nextFloat()) * 0.1D,
                0.0D,
                (random.nextFloat() - random.nextFloat()) * 0.1D);
    }

    static final class DoubleCrouchDetector {
        static final int WINDOW_TICKS = 7;

        private boolean hasPreviousInput;
        private boolean previousUp;
        private boolean previousDown;
        private boolean previousLeft;
        private boolean previousRight;
        private int presses;
        private int triggerTime;
        private boolean triggerGap;

        int inputUpdate(Input input) {
            boolean crouching = input.shiftKeyDown;
            boolean trigger = false;
            if (presses > 0
                    && (input.jumping || !sameMovement(input))) {
                reset();
            }
            if (triggerTime > 0 && --triggerTime == 0) {
                reset();
            }
            if (crouching) {
                trigger = triggerGap
                        && (presses == 0 || triggerTime > 0);
                if (trigger) {
                    triggerTime = WINDOW_TICKS;
                    if (presses++ == 0) {
                        saveMovement(input);
                    }
                }
            }
            triggerGap = !crouching;
            return trigger ? presses : 0;
        }

        private boolean sameMovement(Input input) {
            if (!hasPreviousInput) {
                saveMovement(input);
                return true;
            }
            return previousUp == input.up
                    && previousDown == input.down
                    && previousLeft == input.left
                    && previousRight == input.right;
        }

        private void saveMovement(Input input) {
            hasPreviousInput = true;
            previousUp = input.up;
            previousDown = input.down;
            previousLeft = input.left;
            previousRight = input.right;
        }

        void reset() {
            hasPreviousInput = false;
            presses = 0;
            triggerTime = 0;
        }
    }
}
