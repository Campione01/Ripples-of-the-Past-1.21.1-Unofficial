package rotp.addons.badcompany.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;

import rotp.addons.badcompany.entity.BadCompanyOwnedEntity;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import net.minecraft.resources.ResourceLocation;

final class BadCompanyVisuals {
    private BadCompanyVisuals() {}

    @Nullable
    static StandVisualContext context(
            BadCompanyOwnedEntity entity,
            float partialTick) {
        return context(
                entity.resolveCoordinator(), partialTick);
    }

    static ResourceLocation texture(
            BadCompanyOwnedEntity entity,
            ResourceLocation baseTexture) {
        return texture(
                entity.resolveCoordinator(), baseTexture);
    }

    static ResourceLocation texture(
            @Nullable BadCompanyStandEntity coordinator,
            ResourceLocation baseTexture) {
        StandSkin skin = coordinator != null
                ? StandSkinsLoader.getInstance().getSkin(coordinator)
                : null;
        return skin != null
                ? skin.getTexture(baseTexture, baseTexture)
                : baseTexture;
    }

    @Nullable
    private static StandVisualContext context(
            @Nullable BadCompanyStandEntity coordinator,
            float partialTick) {
        if (coordinator == null || !coordinator.isAlive()) {
            return null;
        }
        float lifecycleAlpha = coordinator.clientStuff != null
                ? coordinator.clientStuff.getAlpha(
                        coordinator, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                coordinator, lifecycleAlpha);
    }
}
