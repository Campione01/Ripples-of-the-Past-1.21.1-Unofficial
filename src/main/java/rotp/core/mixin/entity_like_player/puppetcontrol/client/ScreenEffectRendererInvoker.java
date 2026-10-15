package rotp.core.mixin.entity_like_player.puppetcontrol.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

@Mixin(ScreenEffectRenderer.class)
public interface ScreenEffectRendererInvoker {

	// vanilla's in-wall overlay quad
	@Invoker("renderTex")
	static void jojo_ripples$renderTex(TextureAtlasSprite texture, PoseStack poseStack) {
		throw new AssertionError();
	}
}
