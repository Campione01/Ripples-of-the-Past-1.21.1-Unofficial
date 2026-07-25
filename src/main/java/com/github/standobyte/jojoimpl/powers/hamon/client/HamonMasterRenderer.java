package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonMasterEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HamonMasterRenderer extends HumanoidMobRenderer<HamonMasterEntity, HamonMasterModel> {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/hamon_master.png");
	private static final ResourceLocation EXTRA_TEXTURE = JojoMod.resLoc("textures/entity/biped/hamon_master_extra.png");

	public HamonMasterRenderer(EntityRendererProvider.Context context) {
		super(context, new HamonMasterModel(context.bakeLayer(ModEntityTypeRenderers.HAMON_MASTER)), 0.5F);
		addLayer(new HamonMasterExtraLayer(this,
				new HamonMasterModel(context.bakeLayer(ModEntityTypeRenderers.HAMON_MASTER_EXTRA)), EXTRA_TEXTURE));
	}

	@Override
	public ResourceLocation getTextureLocation(HamonMasterEntity entity) {
		return TEXTURE;
	}
}
