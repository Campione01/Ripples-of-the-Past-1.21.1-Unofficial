package rotp.core.impl.powers.hamon.client;

import rotp.core.client.util.functions.RGBUtil;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonCutterEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;

public class HamonCutterRenderer extends HamonModelRenderer<HamonCutterEntity, HamonCutterModel> {
    public HamonCutterRenderer(EntityRendererProvider.Context context) {
        super(context, HamonCutterModel.create(), JojoMod.resLoc("textures/entity/projectiles/hamon_cutter.png"),
                RenderType::entityCutoutNoCull);
    }

    @Override
    protected int getRenderColor(HamonCutterEntity entity, float partialTick) {
        float[] rgb = RGBUtil.rgb(entity.getColor());
        return ARGB32.color(255,
                Mth.clamp(Math.round(rgb[0] * 255.0F), 0, 255),
                Mth.clamp(Math.round(rgb[1] * 255.0F), 0, 255),
                Mth.clamp(Math.round(rgb[2] * 255.0F), 0, 255));
    }
}
