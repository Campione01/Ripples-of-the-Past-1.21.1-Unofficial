package rotp.addons.stickyfingers.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.client.render.ScopedHumanoidArmorVisibility;
import rotp.core.api.client.render.ScopedPlayerModelPose;
import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.network.DetachedBodyControlPayload;
import rotp.addons.stickyfingers.state.DetachedBodyPartMasks;
import rotp.addons.stickyfingers.state.DetachedPartType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        value = Dist.CLIENT)
public final class StickyFingersBodyPresentation {
    public static final EnumProxy<HumanoidModel.ArmPose>
            CARRIED_PART_ARM_POSE =
                    new EnumProxy<HumanoidModel.ArmPose>(
                    HumanoidModel.ArmPose.class,
                    false,
                    (IArmPoseTransformer)
                            StickyFingersBodyPresentation::applyCarryPose);

    private static final Map<UUID, Integer> PART_MASKS =
            new HashMap<>();
    private static final IdentityHashMap<
            HumanoidModel<?>, boolean[]> MOB_VISIBILITY =
                    new IdentityHashMap<>();
    private static final IdentityHashMap<
            HumanoidModel<?>, HumanoidModel.ArmPose[]> MOB_ARM_POSES =
                    new IdentityHashMap<>();

    @Nullable private static HumanoidModel<?> activeMobModel;
    @Nullable private static IllagerModel<?> activeIllagerModel;
    @Nullable private static boolean[] activeIllagerVisibility;
    private static float activeIllagerBodyY;

    private StickyFingersBodyPresentation() {}

    public static void applySnapshot(
            UUID targetUuid, int partMask) {
        int sanitized = partMask
                & DetachedPartType.allPartsMask();
        DetachedBodyPartMasks.updateClientMask(
                targetUuid, sanitized);
        if (sanitized == 0) {
            PART_MASKS.remove(targetUuid);
        } else {
            PART_MASKS.put(targetUuid, sanitized);
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
                && player.getUUID().equals(targetUuid)
                && !hasPart(sanitized, DetachedPartType.HEAD)) {
            DetachedHeadCameraController.release();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        int mask = player != null
                ? PART_MASKS.getOrDefault(
                        player.getUUID(), 0)
                : 0;
        DetachedHeadCameraController.update(
                minecraft,
                hasPart(mask, DetachedPartType.HEAD));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenInit(
            ScreenEvent.Init.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getScreen() instanceof ChatScreen screen)
                || minecraft.level == null
                || !DetachedHeadCameraController.isActive()) {
            return;
        }
        Component label = Component.translatable(
                minecraft.level.getLevelData().isHardcore()
                        ? "deathScreen.spectate"
                        : "deathScreen.respawn");
        event.addListener(Button.builder(
                        label,
                        button -> {
                            button.active = false;
                            PacketDistributor.sendToServer(
                                    new DetachedBodyControlPayload(
                                            DetachedBodyControlPayload
                                                    .RESPAWN_FROM_HEAD));
                        })
                .bounds(
                        screen.width / 2 - 100,
                        screen.height - 40,
                        200,
                        20)
                .build());
    }

    @SubscribeEvent
    public static void onPlayerRender(
            RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        int mask = PART_MASKS.getOrDefault(
                player.getUUID(), 0);
        PlayerModel<?> model =
                event.getRenderer().getModel();
        hidePlayerParts(player, model, mask);
        if (hasCarriedPart(player)) {
            ScopedPlayerModelPose.setArmPoseForCurrentRender(
                    player,
                    model,
                    player.getMainArm().getOpposite(),
                    CARRIED_PART_ARM_POSE.getValue());
        }
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true)
    public static void onLivingRender(
            RenderLivingEvent.Pre<?, ?> event) {
        restorePresentationModels();
        if (event.isCanceled()
                || event.getEntity()
                        instanceof AbstractClientPlayer) {
            return;
        }
        LivingEntity entity = event.getEntity();
        int mask = PART_MASKS.getOrDefault(
                entity.getUUID(), 0);
        boolean carried = hasCarriedPart(entity);
        if (mask == 0 && !carried) {
            return;
        }
        hideArmorParts(entity, mask);
        if (entity instanceof AbstractIllager
                && event.getRenderer().getModel()
                        instanceof IllagerModel<?> model) {
            captureIllagerModel(model);
            hideIllagerParts(model, mask);
            return;
        }
        if (!(event.getRenderer().getModel()
                instanceof HumanoidModel<?> model)) {
            return;
        }
        captureMobModel(model);
        hideMobParts(model, mask);
        if (carried) {
            setMobCarryPose(
                    model,
                    entity.getMainArm().getOpposite());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterLivingRender(
            RenderLivingEvent.Post<?, ?> event) {
        if (event.getRenderer().getModel()
                        == activeMobModel
                || event.getRenderer().getModel()
                        == activeIllagerModel) {
            restorePresentationModels();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (DetachedHeadCameraController.isActive()) {
            event.setCanceled(true);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HumanoidArm arm =
                event.getHand() == InteractionHand.MAIN_HAND
                        ? player.getMainArm()
                        : player.getMainArm().getOpposite();
        DetachedPartType part =
                arm == HumanoidArm.LEFT
                        ? DetachedPartType.LEFT_ARM
                        : DetachedPartType.RIGHT_ARM;
        if (hasPart(
                PART_MASKS.getOrDefault(
                        player.getUUID(), 0),
                part)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void afterEntityStage(
            RenderLevelStageEvent event) {
        if (event.getStage()
                == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            restorePresentationModels();
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        restorePresentationModels();
        PART_MASKS.clear();
        DetachedBodyPartMasks.clearClientMasks();
        DetachedHeadCameraController.release();
    }

    private static void hidePlayerParts(
            AbstractClientPlayer player,
            PlayerModel<?> model,
            int mask) {
        if (hasPart(mask, DetachedPartType.HEAD)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player, model, Part.HEAD, Part.HAT);
        }
        if (hasPart(mask, DetachedPartType.LEFT_ARM)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.LEFT_ARM,
                    Part.LEFT_SLEEVE);
        }
        if (hasPart(mask, DetachedPartType.RIGHT_ARM)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.RIGHT_ARM,
                    Part.RIGHT_SLEEVE);
        }
        if (hasPart(mask, DetachedPartType.LEFT_LEG)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.LEFT_LEG,
                    Part.LEFT_PANTS);
        }
        if (hasPart(mask, DetachedPartType.RIGHT_LEG)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.RIGHT_LEG,
                    Part.RIGHT_PANTS);
        }
        hideArmorParts(player, mask);
    }

    private static void hideArmorParts(
            LivingEntity entity, int mask) {
        if (hasPart(mask, DetachedPartType.HEAD)) {
            ScopedHumanoidArmorVisibility
                    .hidePartsForCurrentRender(
                            entity,
                            ScopedHumanoidArmorVisibility
                                    .Part.HEAD);
        }
        if (hasPart(mask, DetachedPartType.LEFT_ARM)) {
            ScopedHumanoidArmorVisibility
                    .hidePartsForCurrentRender(
                            entity,
                            ScopedHumanoidArmorVisibility
                                    .Part.LEFT_ARM);
        }
        if (hasPart(mask, DetachedPartType.RIGHT_ARM)) {
            ScopedHumanoidArmorVisibility
                    .hidePartsForCurrentRender(
                            entity,
                            ScopedHumanoidArmorVisibility
                                    .Part.RIGHT_ARM);
        }
        if (hasPart(mask, DetachedPartType.LEFT_LEG)) {
            ScopedHumanoidArmorVisibility
                    .hidePartsForCurrentRender(
                            entity,
                            ScopedHumanoidArmorVisibility
                                    .Part.LEFT_LEG);
        }
        if (hasPart(mask, DetachedPartType.RIGHT_LEG)) {
            ScopedHumanoidArmorVisibility
                    .hidePartsForCurrentRender(
                            entity,
                            ScopedHumanoidArmorVisibility
                                    .Part.RIGHT_LEG);
        }
    }

    private static void hideMobParts(
            HumanoidModel<?> model, int mask) {
        if (hasPart(mask, DetachedPartType.HEAD)) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if (hasPart(mask, DetachedPartType.LEFT_ARM)) {
            model.leftArm.visible = false;
        }
        if (hasPart(mask, DetachedPartType.RIGHT_ARM)) {
            model.rightArm.visible = false;
        }
        if (hasPart(mask, DetachedPartType.LEFT_LEG)) {
            model.leftLeg.visible = false;
        }
        if (hasPart(mask, DetachedPartType.RIGHT_LEG)) {
            model.rightLeg.visible = false;
        }
    }

    private static void hideIllagerParts(
            IllagerModel<?> model, int mask) {
        ModelPart root = model.root();
        ModelPart head = model.getHead();
        ModelPart arms = root.getChild("arms");
        ModelPart leftArm = root.getChild("left_arm");
        ModelPart rightArm = root.getChild("right_arm");
        ModelPart leftLeg = root.getChild("left_leg");
        ModelPart rightLeg = root.getChild("right_leg");
        if (hasPart(mask, DetachedPartType.HEAD)) {
            head.visible = false;
        }
        boolean leftArmDetached =
                hasPart(mask, DetachedPartType.LEFT_ARM);
        boolean rightArmDetached =
                hasPart(mask, DetachedPartType.RIGHT_ARM);
        if (leftArmDetached && rightArmDetached) {
            arms.visible = false;
        }
        if (leftArmDetached) {
            leftArm.visible = false;
        }
        if (rightArmDetached) {
            rightArm.visible = false;
        }
        boolean leftLegDetached =
                hasPart(mask, DetachedPartType.LEFT_LEG);
        boolean rightLegDetached =
                hasPart(mask, DetachedPartType.RIGHT_LEG);
        if (leftLegDetached) {
            leftLeg.visible = false;
        }
        if (rightLegDetached) {
            rightLeg.visible = false;
        }
        if (leftLegDetached && rightLegDetached) {
            root.getChild("body").y = 6.0F;
        }
    }

    private static void captureMobModel(
            HumanoidModel<?> model) {
        MOB_VISIBILITY.put(model, new boolean[] {
                model.head.visible,
                model.hat.visible,
                model.leftArm.visible,
                model.rightArm.visible,
                model.leftLeg.visible,
                model.rightLeg.visible
        });
        MOB_ARM_POSES.put(model, new HumanoidModel.ArmPose[] {
                model.leftArmPose,
                model.rightArmPose
        });
        activeMobModel = model;
    }

    private static void captureIllagerModel(
            IllagerModel<?> model) {
        ModelPart root = model.root();
        activeIllagerVisibility = new boolean[] {
                model.getHead().visible,
                root.getChild("arms").visible,
                root.getChild("left_arm").visible,
                root.getChild("right_arm").visible,
                root.getChild("left_leg").visible,
                root.getChild("right_leg").visible
        };
        activeIllagerBodyY = root.getChild("body").y;
        activeIllagerModel = model;
    }

    private static void restorePresentationModels() {
        restoreMobModel();
        restoreIllagerModel();
    }

    private static void restoreMobModel() {
        HumanoidModel<?> model = activeMobModel;
        activeMobModel = null;
        if (model == null) {
            return;
        }
        boolean[] visibility = MOB_VISIBILITY.remove(model);
        if (visibility != null) {
            model.head.visible = visibility[0];
            model.hat.visible = visibility[1];
            model.leftArm.visible = visibility[2];
            model.rightArm.visible = visibility[3];
            model.leftLeg.visible = visibility[4];
            model.rightLeg.visible = visibility[5];
        }
        HumanoidModel.ArmPose[] poses =
                MOB_ARM_POSES.remove(model);
        if (poses != null) {
            model.leftArmPose = poses[0];
            model.rightArmPose = poses[1];
        }
    }

    private static void restoreIllagerModel() {
        IllagerModel<?> model = activeIllagerModel;
        boolean[] visibility = activeIllagerVisibility;
        activeIllagerModel = null;
        activeIllagerVisibility = null;
        if (model == null || visibility == null) {
            return;
        }
        ModelPart root = model.root();
        model.getHead().visible = visibility[0];
        root.getChild("arms").visible = visibility[1];
        root.getChild("left_arm").visible = visibility[2];
        root.getChild("right_arm").visible = visibility[3];
        root.getChild("left_leg").visible = visibility[4];
        root.getChild("right_leg").visible = visibility[5];
        root.getChild("body").y = activeIllagerBodyY;
    }

    private static void setMobCarryPose(
            HumanoidModel<?> model, HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            model.leftArmPose =
                    CARRIED_PART_ARM_POSE.getValue();
        } else {
            model.rightArmPose =
                    CARRIED_PART_ARM_POSE.getValue();
        }
    }

    private static void applyCarryPose(
            HumanoidModel<?> model,
            LivingEntity entity,
            HumanoidArm arm) {
        ModelPart modelPart = arm == HumanoidArm.LEFT
                ? model.leftArm
                : model.rightArm;
        modelPart.xRot = -Mth.PI / 10.0F;
        modelPart.zRot = 0.0F;
    }

    public static void applyFinalCarryPose(
            LivingEntity entity, HumanoidModel<?> model) {
        if (hasCarriedPart(entity)) {
            applyCarryPose(
                    model,
                    entity,
                    entity.getMainArm().getOpposite());
        }
    }

    private static boolean hasCarriedPart(
            LivingEntity carrier) {
        for (var passenger : carrier.getPassengers()) {
            if (passenger instanceof DetachedBodyPartEntity part
                    && part.getCarrier() == carrier) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPart(
            int mask, DetachedPartType part) {
        return (mask & part.mask()) != 0;
    }
}
