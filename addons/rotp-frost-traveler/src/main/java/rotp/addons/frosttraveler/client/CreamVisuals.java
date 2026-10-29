package rotp.addons.frosttraveler.client;

import javax.annotation.Nullable;

import rotp.core.api.client.render.ClientRenderCompatibility;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.entity.HealSprayEntity;
import rotp.addons.frosttraveler.entity.SprayBulletEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CreamVisuals {
    private CreamVisuals() {}

    static RenderType renderType(ResourceLocation texture) {
        // GUI portraits and item slots must not write into the Stand world buffer.
        if (RenderSystem.getVertexSorting() == VertexSorting.ORTHOGRAPHIC_Z
                && !ClientRenderCompatibility.isIrisShadowPass()) {
            return RenderType.entityTranslucentCull(texture);
        }
        return ModRenderTypes.standTranslucentCull(texture);
    }

    @Nullable
    static StandVisualContext context(
            Entity standOwnedSurface, float partialTick) {
        Entity owner = standOwnedSurface instanceof SprayBulletEntity bullet
                ? bullet.getOwner()
                : standOwnedSurface instanceof HealSprayEntity spray
                        ? spray.getOwner()
                        : standOwnedSurface;
        StandEntity controller = owner instanceof LivingEntity living
                ? FrostTravelerAddon.controller(living)
                : null;
        return context(controller, partialTick);
    }

    @Nullable
    static StandVisualContext context(
            LivingEntity owner, float partialTick) {
        return context(
                FrostTravelerAddon.controller(owner),
                partialTick);
    }

    @Nullable
    private static StandVisualContext context(
            @Nullable StandEntity controller, float partialTick) {
        if (controller == null
                || controller.clientStuff == null
                || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
