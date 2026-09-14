package rotp.core.client.entityrender.entities;

import rotp.core.client.ModEntityTypeRenderers;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ClackersRenderer extends SimpleEntityRenderer<ClackersEntity, ClackersModel> {
	public ClackersRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(JojoMod.resLoc("textures/entity/projectiles/clackers.png"), false);
		initModel(new ClackersModel(context.bakeLayer(ModEntityTypeRenderers.CLACKERS)));
	}
}
