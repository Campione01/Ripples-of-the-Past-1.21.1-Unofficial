package com.github.standobyte.jojo.client.entityanim.barrage;

import com.github.standobyte.jojo.client.entityanim.RotpAnimDefinition;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class OneHandedBarrageLoopSwing extends TwoHandedBarrageLoopSwing {
	private static final float LOOP_LEN = 2;

	public OneHandedBarrageLoopSwing(RotpAnimDefinition barrageAnim, LivingEntity entity,
			float startingAnim, HumanoidArm side, double maxOffset, float animTimeOffset) {
		super(barrageAnim, entity, startingAnim, LOOP_LEN, side, maxOffset, animTimeOffset);
	}

	public static void addMainHandSwings(BarrageSwings swings, RotpAnimDefinition barrageAnim,
			LivingEntity entity, float entityTicks, float animTimeSecs) {
		if (!isPerformingBarrage(entity)) return;
		float ticks = entityTicks;
		float lastLoop = swings.loopLast;
		float loop = ticks / LOOP_LEN;
		if (swings.isBarragingAnim && loop > lastLoop) {
			float hits = swings.barrageSwingsPerSecond / 20F * Math.min(loop - lastLoop, 1) * LOOP_LEN;
			int swingsToAdd = MathUtil.fractionRandomInc(hits);
			if (swingsToAdd > 0) {
				HumanoidArm side = getMainBarrageArm(entity);
				double maxOffset = 1 - swings.barragePrecision / 40;

				for (int i = 0; i < swingsToAdd; i++) {
					float x = ((float) i + (entity.getRandom().nextFloat() - 0.5F) * 0.4F) / swingsToAdd;
					float f = x * LOOP_LEN * 0.5F;
					float addTime = animTimeSecs - animTimeSecs % LOOP_LEN;
					swings.barrageSwings.add(new OneHandedBarrageLoopSwing(
							barrageAnim, entity, f, side, maxOffset, addTime));
				}
			}
		}
		swings.loopLast = loop;
	}

	private static boolean isPerformingBarrage(LivingEntity entity) {
		return LivingComponentAction.getCurEntityAction(entity) != null
				&& LivingComponentAction.getCurEntityAction(entity).getPhase() == ActionPhase.PERFORM;
	}

	private static HumanoidArm getMainBarrageArm(LivingEntity entity) {
		if (entity instanceof StandEntity stand) {
			InteractionHand punchingHand = stand.getPunchingHand();
			return UtilFunctions.getHandSide(stand, punchingHand);
		}
		return entity.getMainArm();
	}
}
