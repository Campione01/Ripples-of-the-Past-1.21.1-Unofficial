package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntityTypeToInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class EntityTypeIcon {
    private static final Map<EntitySubtype<?>, ResourceLocation> ICONS_CACHE = new HashMap<>();
    private static final ResourceLocation UNKNOWN = ResourceLocation.withDefaultNamespace("textures/entity_icon/unknown.png");

    private EntityTypeIcon() {
    }

    public static void renderIcon(EntityType<?> entityType, PoseStack poseStack, float x, float y) {
        renderIcon(EntitySubtype.base(entityType), poseStack, x, y);
    }

    public static void renderIcon(EntitySubtype<?> entityType, PoseStack poseStack, float x, float y) {
        ResourceLocation icon = getIcon(entityType);
        if (icon != UNKNOWN) {
            BlitFloat.blit(poseStack, Minecraft.getInstance(), icon, x, y, 16, 16, 0, BlitFloat.NO_TINT);
        }
        else {
            renderFallbackLetters(entityType.getDescription(), poseStack, x, y);
        }
    }

    private static ResourceLocation getIcon(EntitySubtype<?> entityType) {
        return ICONS_CACHE.computeIfAbsent(entityType, EntityTypeIcon::createIconPath);
    }

    private static ResourceLocation createIconPath(EntitySubtype<?> entitySubtype) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation entityTexture = getEntityTexture(entitySubtype);
        if (entityTexture == null) {
            return UNKNOWN;
        }

        String path = entityTexture.getPath();
        if (!path.contains("/entity/")) {
            return UNKNOWN;
        }

        if (path.contains("/model/entity/")) {
            path = path.replace("/model/entity/", "/entity_icon/");
        }
        else {
            path = path.replace("/entity/", "/entity_icon/");
        }
        ResourceLocation baseIcon = ResourceLocation.fromNamespaceAndPath(entityTexture.getNamespace(), path);

        String subtypeId = entitySubtype.getId().getSubtypeId();
        if (subtypeId != null) {
            ResourceLocation subtypeIcon = ResourceLocation.fromNamespaceAndPath(
                    baseIcon.getNamespace(),
                    path.substring(0, path.length() - 4) + "." + subtypeId + path.substring(path.length() - 4));
            if (mc.getResourceManager().getResource(subtypeIcon).isPresent()) {
                return subtypeIcon;
            }
        }

        return mc.getResourceManager().getResource(baseIcon).isPresent() ? baseIcon : UNKNOWN;
    }

    @Nullable
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ResourceLocation getEntityTexture(EntitySubtype<?> entityType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity entity = EntityTypeToInstance.getEntityInstance((EntitySubtype) entityType, mc.level);
        if (entity == null) {
            return null;
        }
        try {
            EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(entity);
            return renderer.getTextureLocation(entity);
        }
        catch (Exception e) {
            JojoMod.LOGGER.debug("Failed to resolve entity icon texture for {}", entityType.getId(), e);
            return null;
        }
    }

    private static void renderFallbackLetters(Component name, PoseStack poseStack, float x, float y) {
        Font font = Minecraft.getInstance().font;
        String string = name.getString();
        if (string.isEmpty()) {
            return;
        }
        String letters = "";
        int width = 0;
        for (int i = 1; i <= string.length(); i++) {
            String candidate = string.substring(0, i);
            int candidateWidth = font.width(candidate);
            if (candidateWidth > 12) {
                break;
            }
            letters = candidate;
            width = candidateWidth;
        }
        if (letters.isEmpty()) {
            letters = string.substring(0, 1);
            width = font.width(letters);
        }

        RenderSystem.disableDepthTest();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        font.drawInBatch(letters, x + (16 - width) / 2.0F, y + (16 - font.lineHeight + 1) / 2.0F,
                0xFFFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0x00F000F0);
        buffer.endBatch();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
    }
}
