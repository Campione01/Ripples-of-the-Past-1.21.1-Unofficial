package rotp.addons.scarymonsters.client;

import java.util.ArrayList;
import java.util.HashMap;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 1.16 drew this Stand with GeoExampleStandRenderer (:22), whose EmptyStandModel rendered nothing: no shape in the
 * world, in the skins screen or in the Stand info screen. The skin's geometry is the donor's gecko model, which no
 * 1.16 renderer used, so every model this renderer makes has no parts at all.
 */
public final class ScaryMonstersStandRenderer extends StandEntityRenderer<
        ScaryMonstersStandEntity,
        StandEntityRenderState,
        StandEntityModel<ScaryMonstersStandEntity, StandEntityRenderState>> {
    public ScaryMonstersStandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public StandEntityModel<ScaryMonstersStandEntity, StandEntityRenderState> createStandModel(
            LayerDefinition definition) {
        return new StandEntityModel<>(new ModelPart(new ArrayList<>(), new HashMap<>()));
    }
}
