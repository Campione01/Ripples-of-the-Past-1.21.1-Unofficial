package rotp.core.impl.stands.goldexperience.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.math.MatrixUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;

// 1.16 GoldExperienceMarkItem.ClientStuff: items marked by the local GE user render the golden
// "imbued with life" glint in place of the enchantment foil (hooked from ItemRendererMixin).
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GEImbuedGlint {
	public static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/item_imbued_with_life.png");

	private GEImbuedGlint() {}

	// Fixed buffers so the glint is drawn after the item geometry, like vanilla glint.
	@SubscribeEvent
	public static void registerRenderBuffers(RegisterRenderBuffersEvent event) {
		event.registerRenderBuffer(Types.GE_GLINT);
		event.registerRenderBuffer(Types.GE_GLINT_TRANSLUCENT);
	}

	public static boolean isMarked(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		return GEItemMarkEffect.rendersImbuedGlint(stack, player);
	}

	// ItemRenderer.getFoilBuffer with the GE glint.
	public static VertexConsumer foilBuffer(MultiBufferSource buffers, RenderType renderType) {
		RenderType glint = Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet()
				? Types.GE_GLINT_TRANSLUCENT : Types.GE_GLINT;
		return VertexMultiConsumer.create(buffers.getBuffer(glint), buffers.getBuffer(renderType));
	}

	// ItemRenderer.getFoilBufferDirect with the GE glint; compasses and clocks take the decal glint as in 1.16.
	public static VertexConsumer foilBufferDirect(MultiBufferSource buffers, RenderType renderType,
			ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack) {
		if (stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK)) {
			PoseStack.Pose pose = poseStack.last().copy();
			if (displayContext == ItemDisplayContext.GUI) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
			} else if (displayContext.firstPerson()) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
			}
			return compassFoilBuffer(buffers, renderType, pose);
		}
		return VertexMultiConsumer.create(buffers.getBuffer(Types.GE_GLINT), buffers.getBuffer(renderType));
	}

	// ItemRenderer.getCompassFoilBuffer with the GE glint.
	public static VertexConsumer compassFoilBuffer(MultiBufferSource buffers, RenderType renderType, PoseStack.Pose pose) {
		return VertexMultiConsumer.create(
				new SheetedDecalTextureGenerator(buffers.getBuffer(Types.GE_GLINT), pose, 0.0078125F),
				buffers.getBuffer(renderType));
	}

	// Copies of vanilla glint / glint_translucent with the GE texture.
	private static final class Types extends RenderType {
		static final RenderType GE_GLINT = create("jojo_ge_glint", DefaultVertexFormat.POSITION_TEX,
				VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
						.setShaderState(RENDERTYPE_GLINT_SHADER)
						.setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, true, false))
						.setWriteMaskState(COLOR_WRITE)
						.setCullState(NO_CULL)
						.setDepthTestState(EQUAL_DEPTH_TEST)
						.setTransparencyState(GLINT_TRANSPARENCY)
						.setTexturingState(GLINT_TEXTURING)
						.createCompositeState(false));

		static final RenderType GE_GLINT_TRANSLUCENT = create("jojo_ge_glint_translucent", DefaultVertexFormat.POSITION_TEX,
				VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
						.setShaderState(RENDERTYPE_GLINT_TRANSLUCENT_SHADER)
						.setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, true, false))
						.setWriteMaskState(COLOR_WRITE)
						.setCullState(NO_CULL)
						.setDepthTestState(EQUAL_DEPTH_TEST)
						.setTransparencyState(GLINT_TRANSPARENCY)
						.setTexturingState(GLINT_TEXTURING)
						.setOutputState(ITEM_ENTITY_TARGET)
						.createCompositeState(false));

		private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
				boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
			super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
		}
	}
}
