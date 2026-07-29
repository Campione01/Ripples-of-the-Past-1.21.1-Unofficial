package com.github.standobyte.jojoimpl.powers.vampirism.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HungryZombieRenderer extends AbstractZombieRenderer<
		HungryZombieEntity, HungryZombieModel> {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/hungry_zombie.png");

	public HungryZombieRenderer(EntityRendererProvider.Context context) {
		super(context,
				new HungryZombieModel(
						context.bakeLayer(ModelLayers.ZOMBIE)),
				new HungryZombieModel(
						context.bakeLayer(
								ModelLayers.ZOMBIE_INNER_ARMOR)),
				new HungryZombieModel(
						context.bakeLayer(
								ModelLayers.ZOMBIE_OUTER_ARMOR)));
	}

	@Override
	public ResourceLocation getTextureLocation(HungryZombieEntity entity) {
		return TEXTURE;
	}
}
