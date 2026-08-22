package com.github.standobyte.jojo.client.entityrender.stand;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.api.client.render.StandMaterialTintPolicies;
import com.github.standobyte.jojo.client.entityanim.AnimationSet;
import com.github.standobyte.jojo.client.entityanim.barrage.BarrageSwings;
import com.github.standobyte.jojo.client.entityanim.PreFrameEntityAnimCalc;
import com.github.standobyte.jojo.client.entityanim.RotpAnimDefinition;
import com.github.standobyte.jojo.client.entityanim.RotpAnimDefinition.AnimWithId;
import com.github.standobyte.jojo.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityrender.EntityActionRenderState;
import com.github.standobyte.jojo.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.standpower.client_screens.StandInfoScreen;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.objects_java.LazyNullable;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import com.github.standobyte.v1_21_4_stuff.renderstate.ArmedEntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.LivingEntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class StandEntityRenderer<
				T extends StandEntity, 
				S extends StandEntityRenderState, 
				M extends StandEntityModel<T, S>> 
		extends LivingEntityRenderer<T, M> {
	@Deprecated(forRemoval = true)
	public final S reusedState = this.createRenderState();
	public final S outOfLevelRenderState = createRenderState();
	protected LazyNullable<M> missingSkinModel;
	protected final Set<String> missingSkinUiModelWarnings = new HashSet<>();
	protected final Set<String> missingEntityModelWarnings = new HashSet<>();
	protected final Set<String> transparentEntityWarnings = new HashSet<>();
	protected final Set<String> firstEntityRenderLogs = new HashSet<>();
	private final StandGlowLayer<T, S, M> standGlowLayer;
	private final MagiciansRedFlameLayer<T, S, M> magiciansRedFlameLayer;
	private final SilverChariotRapierFlameLayer<T, S, M> silverChariotRapierFlameLayer;
	private final StandFireLayer standFireLayer = new StandFireLayer();

	public StandEntityRenderer(Context context) {
		this(context, 0);
	}

	public StandEntityRenderer(Context context, float shadowRadius) {
		super(context, null, shadowRadius);
		this.missingSkinModel = LazyNullable.of(() -> {
			LayerDefinition defaultModel = RotpGeckoModelLoader.getInstance().getModelDefinition(JojoMod.resLoc("stand_default"));
			return defaultModel != null ? createStandModel(defaultModel) : null;
		});
		this.addLayer(new StandClassicObstructionLayer<>(this));
		this.addLayer(new StandItemInHandLayer<>(this, context.getItemInHandRenderer()));
		this.standGlowLayer = new StandGlowLayer<>(this);
		this.magiciansRedFlameLayer = new MagiciansRedFlameLayer<>(this);
		this.silverChariotRapierFlameLayer = new SilverChariotRapierFlameLayer<>(this);
		this.addLayer(standGlowLayer);
		this.addLayer(magiciansRedFlameLayer);
		this.addLayer(silverChariotRapierFlameLayer);
	}
	
	public final S createRenderState(T entity, float partialTick) {
		S s = this.reusedState;
		this.extractRenderState(entity, s, partialTick);
		return s;
	}

	/**
	 * If you extend StandEntityRenderer and put a sub-class of StandEntityRenderState as S, 
	 * don't forget to override this method too to actually create the new render state object.
	 */
	@SuppressWarnings("unchecked")
//	@Override // 1.21.2+
	public S createRenderState() {
		return (S) new StandEntityRenderState();
	}
	
	@SuppressWarnings("unchecked")
	public M createStandModel(LayerDefinition definition) {
		return (M) new StandEntityModel<>(definition.bakeRoot());
	}
	
	public static final ActionAnimIdentifier IDLE_ANIM = ActionAnimIdentifier.getOrCreate("idle", true);
//	@Override // 1.21.2+
	public void extractRenderState(T entity, S renderState, float partialTick) {
//		super.extractRenderState(entity, renderState, partialTick); // 1.21.2+
		LivingEntityRenderState.extract(entity, renderState, this, entityRenderDispatcher, partialTick);
		ArmedEntityRenderState.extractArmedEntityRenderState(entity, renderState/*, this.itemModelResolver*/);
		EntityActionRenderState.extract(renderState.action, entity, partialTick);
		StandEntityRenderState.extractStandRenderState(entity, renderState, partialTick);
		renderState.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		renderState.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		
		renderState.skin = getStandSkin(entity);
		renderState.doScalingFromStandSkin = true;

		StandVisualContext context = renderState.visualContext;
		if (context != null && context.classicObstruction()) {
			applyClassicObstruction(entity, renderState, partialTick, context.classicOutline());
		}
	}

	private void applyClassicObstruction(T entity, S renderState, float partialTick, boolean outlineEnabled) {
		ClassicStandObstruction.Result obstruction = ClassicStandObstruction.resolve(
				entity, partialTick, isVisibleToLocalPlayer(entity), outlineEnabled);
		if (obstruction == ClassicStandObstruction.Result.NONE) {
			return;
		}

		HumanoidPart[] poseVisibleParts = renderState.visibleParts;
		renderState.classicSolidParts = renderState.action.armsObstructView
				? HumanoidPart.NONE
				: HumanoidPart.reduce(poseVisibleParts, HumanoidPart.ARMS_ONLY);
		if (obstruction == ClassicStandObstruction.Result.ARMS_ONLY_OUTLINE) {
			HumanoidPart[] availableParts = StandEntityRenderState.filterMissingParts(entity, HumanoidPart.ALL);
			renderState.classicOutlineParts = renderState.action.armsObstructView
					? availableParts
					: HumanoidPart.reduce(availableParts, HumanoidPart.NON_ARMS);
			renderState.obstructionRenderMode = ObstructionRenderMode.CLASSIC_OUTLINE;
			renderState.classicOutlineColor = renderState.skin != null ? renderState.skin.getColor() : 0xFFFFFF;
		}
		else {
			renderState.obstructionRenderMode = ObstructionRenderMode.CLASSIC_ARMS_ONLY;
		}
	}

	public StandSkin getStandSkin(T entity) {
		return StandSkinsLoader.getInstance().getSkin(entity);
	}
	
	public void extractSkinMenuRenderState(S renderState, StandSkin skin, ResourceLocation standId, float ticks, int tint, MenuType menuType) {
		renderState.skin = skin;
		renderState.visibleParts = HumanoidPart.ALL;
		renderState.tint = tint;
		renderState.visualContext = StandVisualContext.preview();
		renderState.alpha = 1;
		renderState.doScalingFromStandSkin = true;
		renderState.silverChariotArmorVisible = true;
		renderState.silverChariotRapierVisible = true;
		renderState.silverChariotRapierOnFire = false;
		renderState.visibleForSpectator = false;
		renderState.action.barrageSwings = null;
		renderState.action.animId = null;
		renderState.action.armsObstructView = false;
		renderState.resetObstruction();
		
		AnimFramePose pose = null;
		switch (menuType) {
			case STAND_SKINS -> {
				ActionAnimIdentifier animId = StandEntityRenderer.IDLE_ANIM;
				AnimWithId animWithId = PreFrameEntityAnimCalc.getStandAnim(skin, animId, StandEntityRenderer.IDLE_ANIM);
				RotpAnimDefinition anim = animWithId.anim;
				if (anim != null) {
					float seconds = anim.getAnimTime(ticks);
					AnimMolangVariables molangVars = AnimMolangVariables.set(0, 0, 0);
					pose = anim.calcAnimPose(molangVars, null, seconds, 1);
				}
			}
			case STAND_INFO -> {
				if (skin != null) {
					AnimationSet anims = skin.getAnimations();
					if (anims != null) {
						List<AnimFramePose> poses = anims.coolPoses;
						if (poses != null && !poses.isEmpty()) {
							pose = poses.get(StandInfoScreen.rand % poses.size());
						}
					}
				}
			}
		}
		renderState.action.pose = pose;
	}
	
	public enum MenuType {
		STAND_SKINS,
		STAND_INFO
	}
	
	
	protected static final ResourceLocation MISSING_TEXTURE = JojoMod.resLoc("textures/entity/stand_default.png");
//	@Override // 1.21.1+
	public ResourceLocation getTextureLocation(S renderState) {
		StandSkin standSkin = renderState.skin;
		ResourceLocation texture = standSkin != null ? standSkin.getStandTexture(MISSING_TEXTURE) : null;
		return texture != null ? texture : MISSING_TEXTURE;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ResourceLocation getTextureLocation(T entity) {
		if (RenderStateCrutches.currentEntityRenderState != null) {
			return getTextureLocation((S) RenderStateCrutches.currentEntityRenderState);
		}
		StandSkin standSkin = getStandSkin(entity);
		ResourceLocation texture = standSkin != null ? standSkin.getStandTexture(MISSING_TEXTURE) : null;
		return texture != null ? texture : MISSING_TEXTURE;
	}

//	@Override
//	protected int getModelTint(S renderState) {
//		return renderState.tint;
//	}

	protected M modelFrom(S renderState) {
		M model = getEntityModel(renderState);
		if (model == missingSkinModel.get() && renderState.skin != null) {
			renderState.tint = renderState.skin.getColor();
		}
		return model;
	}
	
	public M getEntityModel(T entity) {
		return getEntityModel(getStandSkin(entity));
	}
	
	public M getEntityModel(S renderState) {
		StandSkin standSkin = renderState.skin;
		return getEntityModel(standSkin);
	}
	
	public M getEntityModel(StandSkin standSkin) {
		M model = standSkin != null ? (M) standSkin.getStandModel(this) : null;
		if (model == null) {
			model = missingSkinModel.get();
		}
		return model;
	}
	
	
	public void renderForStandSkinUI(PoseStack poseStack, MultiBufferSource bufferSource, Consumer<S> extractRenderState) {
		S renderState = outOfLevelRenderState;
		extractRenderState.accept(renderState);
		RenderStateCrutches.Snapshot crutchSnapshot = preRender(renderState);
		M previousModel = this.model;
		try {
			this.model = modelFrom(renderState);
			M model = this.model;
			if (model == null) {
				String standId = renderState.skin != null ? renderState.skin.standTypeId.toString() : "<no skin>";
				String skinId = renderState.skin != null ? renderState.skin.skinId.toString() : "<no skin>";
				if (missingSkinUiModelWarnings.add(standId + "|" + skinId)) {
					JojoMod.getLogger().error(
							"Cannot render Stand skin UI model for stand {}, skin {}: no stand model or stand_default fallback model is loaded.",
							standId, skinId);
				}
				return;
			}

			poseStack.pushPose();
			try {
				poseStack.translate(0, 1.5f, 0);
				poseStack.scale(1, -1, 1);
				poseStack.mulPose(Axis.YP.rotationDegrees(180));
				poseStack.scale(-1, 1, 1);
				
//				Optional<ResourceLocation> nonDefaultSkin = standSkin.getNonDefaultLocation();
				model.attackTime = 0;
				model.riding = false;
				model.young = false;

				model.setupAnim(renderState);

				ResourceLocation texture = getTextureLocation(renderState);
				RenderType renderType = model.renderType(texture);
				int packedLight = ClientUtil.MAX_LIGHT;
				if (renderType != null) {
					VertexConsumer vertexBuilder = bufferSource.getBuffer(renderType);
					int packedOverlay = OverlayTexture.NO_OVERLAY;
					model.renderToBuffer(poseStack, vertexBuilder, packedLight, packedOverlay, renderState.tint);

					for (RenderLayer<T, M> layerRenderer : this.layers) {
						if (layerRenderer instanceof StandSkinUiLayer uiLayer) {
							uiLayer.renderForStandSkinUI(poseStack, bufferSource, renderState);
						}
					}
				}
			}
			finally {
				poseStack.popPose();
			}
		}
		finally {
			this.model = previousModel;
			postRender(crutchSnapshot);
		}
	}
	
	// 1.21.2+
//	public void renderWithRenderState(Consumer<S> renderState, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
//		renderState.accept(outOfLevelRenderState);
//		render(outOfLevelRenderState, poseStack, bufferSource, light);
//	}
	
	
//	@Override // 1.21.2+
//	public void render(S renderState, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
//		setModelFrom(renderState);
//		if (this.model != null) {
//			super.render(renderState, poseStack, bufferSource, light);
//		}
//	}
	
	public RenderStateCrutches.Snapshot preRender(S renderState) {
		return RenderStateCrutches.pushStand(renderState);
	}
	
	public void postRender(RenderStateCrutches.Snapshot snapshot) {
		RenderStateCrutches.restore(snapshot);
	}

	public void setupFirstPersonRotations(T entity, S renderState, PoseStack poseStack) {
		if (isMagiciansRedFlameBurstFirstPerson(entity, renderState)) {
			M model = modelFrom(renderState);
			if (model != null) {
				model.setupAnim(renderState);
				float xRotRad = renderState.xRot * MathUtil.DEG_TO_RAD;
				poseStack.mulPose(Axis.XP.rotation(xRotRad));
				poseStack.translate(0, -entity.getBbHeight() * 0.25F, 0.25F);
				float headXRot = model.head != null ? model.head.xRot : xRotRad;
				poseStack.mulPose(Axis.XP.rotation(headXRot - xRotRad));
				poseStack.translate(0, entity.getBbHeight() * 0.25F, -0.25F);
				poseStack.mulPose(Axis.YP.rotationDegrees(180 + renderState.bodyRot));
				poseStack.translate(0, -entity.getEyeHeight(), 0);
				return;
			}
		}
		poseStack.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
		poseStack.mulPose(Axis.YP.rotationDegrees(180 + renderState.bodyRot));
		poseStack.translate(0, -entity.getEyeHeight(), 0);
	}

	private boolean isMagiciansRedFlameBurstFirstPerson(T entity, S renderState) {
		ActionAnimIdentifier animId = renderState.action.animId;
		return JojoMod.resLoc("magicians_red").equals(entity.getStandType())
				&& animId != null
				&& ("flameBurst".equals(animId.name()) || "flame_burst".equals(animId.name()));
	}

	private boolean isVisibleToLocalPlayer(T entity) {
		return !entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player);
	}

	private boolean isPartiallyVisibleToLocalPlayer(T entity) {
		return entity.isInvisible() && !entity.isInvisibleTo(Minecraft.getInstance().player);
	}

	@Override
	public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
		S renderState = this.createRenderState(entity, partialTicks);
		boolean displayFire = renderState.displayFireAnimation;
		if (!EntityMaskPostEffect.isCapturePass()) {
			entity.setNoFireAnimFrame();
		}
		render(entity, renderState, entityYaw, partialTicks, poseStack, bufferSource, light, displayFire);
	}

	public void renderAfterimage(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource bufferSource, int light) {
		S renderState = this.createRenderState(entity, partialTicks);
		render(entity, renderState, entityYaw, partialTicks, poseStack, bufferSource, light);
	}

	public void render(T entity, S renderState, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
		render(entity, renderState, entityYaw, partialTicks, poseStack, bufferSource, light, false);
	}

	private void render(T entity, S renderState, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource bufferSource, int light, boolean displayFire) {
		RenderStateCrutches.Snapshot crutchSnapshot = preRender(renderState);
		try {
			bufferSource = StandMaterialTintPolicies.wrap(
					bufferSource, entity, partialTicks);
			this.model = modelFrom(renderState);
			logRuntimeModelState(entity, renderState);

			if (this.model != null) {
				this.model.setAlpha(renderState.alpha);
				BarrageSwings previousBarrage = setupStandBarrageRendering(renderState);
				try {
					this.doRender(entity, entityYaw, partialTicks, poseStack, bufferSource, light);
				}
				finally {
					BarrageSwings.setupToRender(previousBarrage);
				}
			}
			standFireLayer.render(poseStack, bufferSource, entity, renderState, displayFire,
					Mth.rotationAroundAxis(Mth.Y_AXIS, this.entityRenderDispatcher.cameraOrientation(), new org.joml.Quaternionf()));
		}
		finally {
			postRender(crutchSnapshot);
		}
	}

	private void logRuntimeModelState(T entity, S renderState) {
		String standId = renderState.skin != null ? renderState.skin.standTypeId.toString() : String.valueOf(entity.getStandType());
		String skinId = renderState.skin != null ? renderState.skin.skinId.toString() : "<no skin>";
		String key = standId + "|" + skinId;
		String cameraType = Minecraft.getInstance().options.getCameraType().name();
		if (firstEntityRenderLogs.add(entity.getId() + "|" + cameraType)) {
			JojoMod.getLogger().info(
					"Stand entity renderer reached: entityId={}, type={}, stand={}, skin={}, model={}, alpha={}, rangeEfficiency={}, modelAlpha={}, visibleParts={}, invisible={}, invisibleToPlayer={}, pos={}.",
					entity.getId(), entity.getType().builtInRegistryHolder().key().location(), standId, skinId,
					this.model != null ? this.model.getClass().getName() : "<null>",
					renderState.alpha, entity.rangeEfficiency, entity.modelAlpha.get(), renderState.visibleParts,
					entity.isInvisible(), renderState.isInvisibleToPlayer, entity.position());
		}
		if (this.model == null && missingEntityModelWarnings.add(key)) {
			JojoMod.getLogger().error(
					"Cannot render Stand entity model for stand {}, skin {}: no stand model or stand_default fallback model is loaded.",
					standId, skinId);
		}
		if (entity.tickCount > 20 && renderState.alpha <= 0.01F && !entity.isInvisible() && transparentEntityWarnings.add(key)) {
			JojoMod.getLogger().warn(
					"Stand entity render alpha is nearly zero for stand {}, skin {}: alpha={}, rangeEfficiency={}, visibleParts={}.",
					standId, skinId, renderState.alpha, entity.rangeEfficiency, renderState.visibleParts);
		}
	}

    public void doRender(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
		super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, light);
    }

	private BarrageSwings setupStandBarrageRendering(S renderState) {
		BarrageSwings previousBarrage = BarrageSwings.currentlyRendering;
		BarrageSwings.setupToRender(renderState.action.barrageSwings);
		return previousBarrage;
	}

	@Override
	protected RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		ResourceLocation texture = this.getTextureLocation(entity);
		boolean useStandAlphaMaterial = false;
		if (RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState renderState) {
			if (renderState.obstructionRenderMode == ObstructionRenderMode.CLASSIC_OUTLINE) {
				return null;
			}
			if (renderState.alpha <= 0.0F) {
				return glowing ? RenderType.outline(texture) : null;
			}
			useStandAlphaMaterial = renderState.alpha < 1.0F;
		}
		if (translucent) {
			return standTranslucentRenderType(texture);
		}
		else if (bodyVisible) {
			return useStandAlphaMaterial ? standTranslucentRenderType(texture) : this.model.renderType(texture);
		}
		else {
			return glowing ? RenderType.outline(texture) : null;
		}
    }

	RenderType classicSolidRenderType(T entity) {
		ResourceLocation texture = this.getTextureLocation(entity);
		boolean useStandAlphaMaterial = isPartiallyVisibleToLocalPlayer(entity);
		if (RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState renderState) {
			useStandAlphaMaterial |= renderState.alpha < 1.0F;
		}
		return useStandAlphaMaterial ? standTranslucentRenderType(texture) : this.model.renderType(texture);
	}

	protected RenderType standTranslucentRenderType(ResourceLocation texture) {
		// The 1.16.5 Stand models were authored for two-sided translucency.
		// Keep culling only where it protects the first-person camera from
		// internal and far-side model surfaces.
		return Minecraft.getInstance().options.getCameraType().isFirstPerson()
				? ModRenderTypes.standTranslucentCull(texture)
				: ModRenderTypes.standTranslucent(texture);
	}

	int classicSolidColor(T entity) {
		return isPartiallyVisibleToLocalPlayer(entity)
				? FastColor.ARGB32.color(FastColor.as8BitChannel(0.15F), 0xFFFFFF)
				: -1;
	}

	RenderType classicOutlineRenderType(T entity) {
		return ModRenderTypes.standOutline(this.getTextureLocation(entity));
	}

	void renderClassicOutlineLayers(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			T entity, S renderState) {
		standGlowLayer.renderClassicOutline(poseStack, bufferSource, renderState);
		magiciansRedFlameLayer.renderClassicOutline(poseStack, bufferSource, entity, renderState);
		silverChariotRapierFlameLayer.renderClassicOutline(poseStack, bufferSource, renderState);
	}
	
	@Override
	protected void scale(T entity, PoseStack poseStack, float partialTick) {
		if (RenderStateCrutches.currentEntityRenderState instanceof StandEntityRenderState renderState
				&& renderState.doScalingFromStandSkin) {
			StandSkin standSkin = renderState.skin;
			if (standSkin != null && standSkin.hasModelScale()) {
				float[] scale = standSkin.getModelScale();
				poseStack.scale(scale[0], scale[1], scale[0]);
				return;
			}
		}
	}

	private static final float OVERLAY_TICKS = 10.0F;

	int getPackedOverlay(T entity, float partialTick) {
		return LivingEntityRenderer.getOverlayCoords(entity, getWhiteOverlayProgress(entity, partialTick));
	}

	@Override
	protected float getWhiteOverlayProgress(T entity, float partialTick) {
		if (entity.isArmsOnlyMode() || entity.overlayTickCount > OVERLAY_TICKS) {
			return 0.0F;
		}
		return (OVERLAY_TICKS - Mth.clamp(entity.overlayTickCount + partialTick, 0.0F, OVERLAY_TICKS)) / OVERLAY_TICKS;
	}

	@Override
	protected boolean shouldShowName(T entity/*, double distSqr*/) {
		return false;
	}

}
