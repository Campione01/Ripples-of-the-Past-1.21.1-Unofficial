package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.client.util.functions.RGBUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonCutterEntity;

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
