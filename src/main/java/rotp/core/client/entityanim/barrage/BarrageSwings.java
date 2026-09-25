package rotp.core.client.entityanim.barrage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.RotpAnimDefinition.TimelineKeys;
import rotp.core.client.entityanim.playerbend.IPlayerBendModel;
import rotp.core.client.entityrender.EntityActionRenderState;
import rotp.core.client.entityrender.stand.HumanoidPart;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.rendertype.BarrageVertexConsumer;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.compat.v1_21_4.renderstate.EntityRenderState;
import rotp.core.compat.v1_21_4.renderstate.LivingEntityRenderState;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class BarrageSwings {
	@ApiStatus.Internal public List<BarrageSwing> barrageSwings = new LinkedList<>();
	@ApiStatus.Internal public float loopLast = -1;

	@ApiStatus.Internal public boolean isBarragingAnim = false;
	@ApiStatus.Internal public String barrageType;
	@ApiStatus.Internal public AddBarrageSwing addSwingFunction;
	
	public float barragePrecision = 12;
	public float barrageSwingsPerSecond = 80;
	
	protected float lastTicks = -1;


	public void frameStandBarrage(Minecraft mc, RotpAnimDefinition barrageAnim, 
			float curAnimTimeSecs, LivingEntity entity, float ticks) {
		if (this.lastTicks == ticks) return;
		this.lastTicks = ticks;
		
		if (entity instanceof StandEntity stand) {
			barrageSwingsPerSecond = StandStatFormulas.getBarrageHitsPerSecond(stand.getAttackSpeed());
			barragePrecision = (float) stand.getPrecision();
		}
		frameUpdateSwings(mc);
		String barrageTypeName = barrageAnim.instructionTimelines.getStringTimelineVal(TimelineKeys.BARRAGE, curAnimTimeSecs);
		frameUpdateBarrageType(barrageTypeName);
		if (isBarragingAnim) {
				frameSetValuesAndAddNewSwings(barrageAnim, entity, ticks, curAnimTimeSecs);
		}
	}

	public void frameUpdateSwings(Minecraft mc) {
		if (!mc.isPaused() && !barrageSwings.isEmpty()) {
			float timeDelta = mc.getTimer().getGameTimeDeltaTicks();
			Iterator<BarrageSwing> iter = barrageSwings.iterator();
			while (iter.hasNext()) {
				BarrageSwing swing = iter.next();
				swing.addDelta(timeDelta);
				if (swing.removeSwing()) {
					iter.remove();
				}
			}
		}
	}

	public void frameUpdateBarrageType(String barrageTypeName) {
		this.isBarragingAnim = false;
		this.barrageType = barrageTypeName;
		this.addSwingFunction = null;

		if (barrageType != null) {
			AddBarrageSwing addSwingFunction = BARRAGE_SWING_TYPES.get(barrageType);
			if (addSwingFunction != null) {
				this.isBarragingAnim = true;
				this.addSwingFunction = addSwingFunction;
			}
		}
	}

	public void frameSetValuesAndAddNewSwings(RotpAnimDefinition barrageAnim, LivingEntity entity, float entityTicks, float curAnimTimeSecs) {
		addSwingFunction.addSwings(this, barrageAnim, entity, entityTicks, curAnimTimeSecs);
	}

	
	public boolean hasSmthToRender() {
		return !barrageSwings.isEmpty();
	}
	
	public void renderLayerBarrage(EntityModel<?> model, 
			PoseStack poseStack, VertexConsumer buffer, 
			int packedLight, int packedOverlay, int color, float xRot) {
		try {
			for (BarrageSwing swing : barrageSwings) {
				swing.poseAndRender(model, poseStack, BarrageVertexConsumer.forBarrage(buffer, swing),
						packedLight, packedOverlay, color, xRot);
			}
		}
		finally {
			restoreModelAfterBarrage(model, RenderStateCrutches.currentStandEntityRenderState);
		}
	}

	/**
	 * Afterimages of a humanoid (player or mob), drawn once with the entity's own model.
	 * 1.16 (BarrageFistAfterimagesLayer) used a separate model copy, so the pose and visibility
	 * that the later layers read (armor, held items) are put back, and the layers draw no afterimages.
	 */
	public static void renderHumanoidAfterimages(HumanoidModel<?> model,
			PoseStack poseStack, VertexConsumer buffer,
			int packedLight, int packedOverlay, int color) {
		BarrageSwings swings = currentlyRendering;
		if (swings == null) {
			return;
		}
		// only the main body model draws them
		currentlyRendering = null;
		float xRot = RenderStateCrutches.currentEntityRenderState != null
				? RenderStateCrutches.currentEntityRenderState.xRot : 0;
		List<ModelPart> parts = humanoidStateParts(model);
		float[] saved = saveModelState(parts);
		try {
			swings.renderLayerBarrage(model, poseStack, buffer, packedLight, packedOverlay, color, xRot);
		}
		finally {
			loadModelState(parts, saved);
		}
	}

	private static List<ModelPart> humanoidStateParts(HumanoidModel<?> model) {
		List<ModelPart> parts = new ArrayList<>();
		ModelPart root = ((Model_1_21_2plus) model).jojo_ripples$root();
		if (root != null) {
			root.getAllParts().forEach(parts::add);
		}
		else {
			parts.addAll(List.of(model.head, model.hat, model.body,
					model.rightArm, model.leftArm, model.rightLeg, model.leftLeg));
		}
		// the swing clip also poses the bend parts, which translateToHand reads
		if (model instanceof IPlayerBendModel bends) {
			for (ModelPart part : new ModelPart[] {
					bends.jojo_ripples$animMainBody(), bends.jojo_ripples$animTorso(), bends.jojo_ripples$animTorsoBend(),
					bends.jojo_ripples$animRightArmBend(), bends.jojo_ripples$animLeftArmBend(),
					bends.jojo_ripples$animRightLegBend(), bends.jojo_ripples$animLeftLegBend(),
					bends.jojo_ripples$animRightItem(), bends.jojo_ripples$animLeftItem(), bends.jojo_ripples$animCapeBend() }) {
				if (part != null) {
					parts.add(part);
				}
			}
		}
		return parts;
	}

	private static final int PART_STATE_SIZE = 10;

	private static float[] saveModelState(List<ModelPart> parts) {
		float[] state = new float[parts.size() * PART_STATE_SIZE];
		int i = 0;
		for (ModelPart part : parts) {
			state[i++] = part.x;
			state[i++] = part.y;
			state[i++] = part.z;
			state[i++] = part.xRot;
			state[i++] = part.yRot;
			state[i++] = part.zRot;
			state[i++] = part.xScale;
			state[i++] = part.yScale;
			state[i++] = part.zScale;
			state[i++] = part.visible ? 1 : 0;
		}
		return state;
	}

	private static void loadModelState(List<ModelPart> parts, float[] state) {
		int i = 0;
		for (ModelPart part : parts) {
			part.x = state[i++];
			part.y = state[i++];
			part.z = state[i++];
			part.xRot = state[i++];
			part.yRot = state[i++];
			part.zRot = state[i++];
			part.xScale = state[i++];
			part.yScale = state[i++];
			part.zScale = state[i++];
			part.visible = state[i++] != 0;
		}
	}





	public static final Map<String, AddBarrageSwing> BARRAGE_SWING_TYPES = Util.make(new HashMap<>(), map -> {
		map.put("TWO_HANDED", (swings, barrageAnim, entity, entityTicks, animTimeSecs) -> {
			TwoHandedBarrageLoopSwing.addSwings(swings, barrageAnim, entity, entityTicks, animTimeSecs);
		});
		map.put("ONE_HANDED_MAIN", OneHandedBarrageLoopSwing::addMainHandSwings);
		map.put("GRAB_RIGHT", (BarrageSwings swings, RotpAnimDefinition barrageAnim,
				LivingEntity entity, float entityTicks, float animTimeSecs)
				-> {
					GrabBarrageLoopSwing.addSwings(swings, barrageAnim, entity, entityTicks, animTimeSecs, HumanoidArm.RIGHT);
				});
	});

	@FunctionalInterface
	public static interface AddBarrageSwing {
		void addSwings(BarrageSwings swings, RotpAnimDefinition barrageAnim, 
				LivingEntity entity, float entityTicks, float curAnimTimeSecs);
	}



	public abstract static class BarrageSwing {
		protected static final Random RANDOM = new Random();
		protected static final LivingEntityRenderState sharedRenderState = new LivingEntityRenderState();
		protected static final EntityActionRenderState sharedActionRenderState = new EntityActionRenderState();
		
		protected RotpAnimDefinition barrageAnim;
		protected float ticks;
		protected float ticksMax;
		
		public BarrageSwing(RotpAnimDefinition barrageAnim, float startingAnim, float animMax) {
			this.barrageAnim = barrageAnim;
			this.ticks = startingAnim;
			this.ticksMax = animMax;
		}

		public void addDelta(float delta) {
			ticks += delta * 0.75F;
		}

		public boolean removeSwing() {
			return ticks >= ticksMax * 0.75F;
		}

		public abstract void poseAndRender(EntityModel<?> model, 
				PoseStack poseStack, VertexConsumer buffer, 
				int packedLight, int packedOverlay, int color, float xRot);
	}


	public static ModelPart getNoXRotArm(EntityModel<?> model, HumanoidArm side) {
		return switch (model) {
			case StandEntityModel<?, ?> standModel -> {
				yield switch (side) {
					case LEFT -> standModel.left_arm;
					case RIGHT -> standModel.right_arm;
				};
			}
			case HumanoidModel<?> humanoidModel -> {
				yield switch (side) {
					case LEFT -> humanoidModel.leftArm;
					case RIGHT -> humanoidModel.rightArm;
				};
			}
			default -> null;
		};
	}
	
	public static boolean setOnlyOneArmVisible(EntityModel<?> model, HumanoidArm side) {
		return switch (model) {
			case StandEntityModel<?, ?> standModel -> {
				if (getNoXRotArm(standModel, side) == null) {
					yield false;
				}
				HumanoidPart.setPartsVisible(standModel, switch (side) {
					case LEFT -> HumanoidPart.LEFT_ARM_ONLY;
					case RIGHT -> HumanoidPart.RIGHT_ARM_ONLY;
				});
				yield true;
			}
			case HumanoidModel<?> humanoidModel -> {
				humanoidModel.setAllVisible(false);
				(switch (side) {
					case LEFT -> humanoidModel.leftArm;
					case RIGHT -> humanoidModel.rightArm;
				}).visible = true;
				yield true;
			}
			default -> false;
		};
	}

	public static void resetStandPoseForBarrage(EntityModel<?> model) {
		if (model instanceof StandEntityModel<?, ?>) {
			EntityRenderState.resetPose(model);
		}
	}
	
	public static void restoreModelAfterBarrage(EntityModel<?> model, @Nullable StandEntityRenderState standRenderState) {
		switch (model) {
			case StandEntityModel<?, ?> standModel -> {
				if (standRenderState != null) {
					setupStandModelAnim(standModel, standRenderState);
				}
				else {
					standModel.setAllVisible(true);
				}
			}
			case HumanoidModel<?> humanoidModel -> {
				humanoidModel.setAllVisible(true);
			}
			default -> {}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void setupStandModelAnim(StandEntityModel standModel, StandEntityRenderState standRenderState) {
		standModel.setupAnim(standRenderState);
	}
	
	
	@Nullable
	public static BarrageSwings getBarrageSwings(LivingEntityRenderState renderState) {
		EntityActionRenderState action = EntityActionRenderState.getFrom(renderState);
		return action != null ? action.barrageSwings : null;
	}
	
	@Nullable public static BarrageSwings currentlyRendering = null;
	
	public static void setupToRender(BarrageSwings barrage) {
		BarrageSwings.currentlyRendering = barrage;
	}

}
