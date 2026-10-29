package rotp.addons.d4c.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class D4CStandRenderer extends StandEntityRenderer<
        StandEntity,
        StandEntityRenderState,
        StandEntityModel<StandEntity, StandEntityRenderState>> {
    public D4CStandRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new D4CHarmLayer(this));
    }
}
