package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.ui.utils.GuiIcon;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class MetEntityTypeToast implements Toast {
    private static final Component NAME = Component.translatable("ge_new_lifeform.toast.title");
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/recipe");
    private static final long DISPLAY_TIME = 5000L;

    private final List<EntityType<?>> entityTypes = new ArrayList<>();
    private long lastChanged;
    private boolean changed;

    private MetEntityTypeToast(EntityType<?> entityType) {
        this.entityTypes.add(entityType);
    }

    @Override
    public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long delta) {
        if (changed) {
            lastChanged = delta;
            changed = false;
        }

        if (entityTypes.isEmpty()) {
            return Toast.Visibility.HIDE;
        }

        Minecraft mc = toastComponent.getMinecraft();
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());
        guiGraphics.drawString(mc.font, NAME, 30, 7, 0xFF500050, false);

        EntityType<?> entityType = entityTypes.get((int) (delta
                / Math.max(1L, DISPLAY_TIME / (long) entityTypes.size())
                % (long) entityTypes.size()));
        guiGraphics.drawString(mc.font, entityType.getDescription(), 30, 18, 0xFF000000, false);
        EntityTypeIcon.renderIcon(entityType, guiGraphics.pose(), 8, 8);
        renderStandIcon(guiGraphics.pose());

        long displayTime = (long) (DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier());
        return delta - this.lastChanged >= displayTime ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    private static void renderStandIcon(PoseStack poseStack) {
        StandSkin skin = getGoldExperienceSkin();
        if (skin == null) {
            return;
        }

        GuiIcon icon;
        try {
            icon = skin.getStandIcon();
        }
        catch (IllegalStateException e) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 1.0F);
        icon.render(poseStack, 3, 3);
        poseStack.popPose();
    }

    private static StandSkin getGoldExperienceSkin() {
        StandSkinsLoader loader = StandSkinsLoader.getInstance();
        if (loader == null) {
            return null;
        }

        StandPower power = ClientPowerCache.getPower(PowerClass.STAND);
        if (power != null && power.hasPower() && power.getPowerType() == ModStands.GOLD_EXPERIENCE.get()) {
            StandSkin skin = loader.getSkin(power);
            if (skin != null) {
                return skin;
            }
        }

        return loader.getDefaultSkin(ModStands.GOLD_EXPERIENCE.get().getId());
    }

    protected void addMetType(EntityType<?> entityType) {
        if (entityTypes.add(entityType)) {
            changed = true;
        }
    }

    public static void addOrUpdate(ToastComponent toastComponent, EntityType<?> entityType) {
        MetEntityTypeToast toast = toastComponent.getToast(MetEntityTypeToast.class, Toast.NO_TOKEN);
        if (toast == null) {
            toastComponent.addToast(new MetEntityTypeToast(entityType));
        }
        else {
            toast.addMetType(entityType);
        }
    }
}
