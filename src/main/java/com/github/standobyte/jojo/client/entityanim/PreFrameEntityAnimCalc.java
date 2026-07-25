package com.github.standobyte.jojo.client.entityanim;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.entityanim.RotpAnimDefinition.AnimWithId;
import com.github.standobyte.jojo.client.entityanim.barrage.BarrageSwings;
import com.github.standobyte.jojo.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityanim.pose.AnimatedEntity;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityModel;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderer;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PreFrameEntityAnimCalc {
	
	public static class LivingAnimState {
		@Nullable public ResourceLocation animSet;
		@Nullable public ActionAnimIdentifier animId;
		public float time;
		@Nullable public ActionPhase actionPhase;
		public float phaseTime;
		public float phaseCompletion;
		
		public void reset() {
			this.animSet = null;
			this.animId = null;
			this.time = -1;
			this.actionPhase = null;
			this.phaseTime = -1;
			this.phaseCompletion = -1;
		}
		
		public static LivingAnimState reusedInstance = new LivingAnimState();
	}
	
	private static final ActionAnimIdentifier SUMMON_ANIM = ActionAnimIdentifier.getOrCreate("summon", false);
	private static final ActionAnimIdentifier SC_NO_RAPIER_IDLE_ANIM = ActionAnimIdentifier.getOrCreate("no_rapier_idle", true);
	private static final ActionAnimIdentifier SC_NO_ARMOR_IDLE_ANIM = ActionAnimIdentifier.getOrCreate("no_armor_idle", true);

	public static void onBeforeEntitiesRender(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		DeltaTracker deltaTracker = mc.getTimer();
		TickRateManager tickRateManager = level.tickRateManager();
		for (Entity entity : level.entitiesForRendering()) {
			float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
			@Nullable AnimFramePose pose = null;
			if (entity instanceof LivingEntity living) {
				pose = getLivingPose(living, partialTick, true);
			}
			AnimatedEntity animatedEntity = (AnimatedEntity) entity;
			if (pose == null) {
				animatedEntity.jojo_ripples$setModelPose(AnimatedEntity.PoseType.UNMODIFIED, null);
			}
			animatedEntity.jojo_ripples$setModelPose(AnimatedEntity.PoseType.FINAL, pose);
		}
	}
	
	// TODO get rid of instanceof
	// TODO get rid of newFrame argument
	public static AnimFramePose getLivingPose(LivingEntity living, float partialTick, boolean newFrame) {
		AnimFramePose pose = null;
		LivingComponentAction actionComponent = LivingComponentAction.getExistingComponent(living);
		EntityActionInstance action = actionComponent != null ? actionComponent.getAction() : null;
		@Nullable StandEntity stand = living instanceof StandEntity __ ? __ : null;
		@Nullable HamonData persistentHamonPose = stand == null && living instanceof Player
				? PlayerPower.getPowerData(living, ModPlayerPowers.HAMON)
						.filter(hamon -> hamon.isMeditating() || !living.isPassenger() && hamon.isWallClimbing())
						.orElse(null)
				: null;
		@Nullable PillarmanData persistentStoneForm = action == null && stand == null && persistentHamonPose == null
				&& living instanceof Player && !living.isPassenger()
				? PlayerPower.getPowerData(living, PillarmanPowerType.PILLAR_MAN)
						.filter(PillarmanData::isStoneFormEnabled).orElse(null)
				: null;
		
		LivingAnimState animVariables = LivingAnimState.reusedInstance;
		if (action != null || stand != null || persistentHamonPose != null || persistentStoneForm != null) {
			if (persistentHamonPose != null) {
				animVariables.reset();
				animVariables.animSet = ModPlayerPowers.HAMON.get().getId();
				if (persistentHamonPose.isWallClimbing()) {
					animVariables.animId = ActionAnimIdentifier.getOrCreate(
							persistentHamonPose.getWallClimbAnimationName(), false);
					animVariables.time = persistentHamonPose.getWallClimbAnimationTicks(partialTick);
				}
				else {
					animVariables.animId = ActionAnimIdentifier.getOrCreate("meditation", false);
					animVariables.time = persistentHamonPose.getMeditationPoseTicks() + partialTick;
				}
			}
			else if (action != null) {
				action.extractAnim(animVariables, living, partialTick);
			}
			else {
				animVariables.reset();
				if (persistentStoneForm != null) {
					animVariables.animSet = PillarmanPowerType.PILLAR_MAN.get().getId();
					animVariables.animId = ActionAnimIdentifier.getOrCreate("pillarman_stone_form",
							persistentStoneForm.getStoneFormPose(), false);
					animVariables.time = persistentStoneForm.getStoneFormAnimTicks() + partialTick;
				}
			}

			RotpAnimDefinition anim;
			if (stand != null) {
				StandSkin standSkin = StandSkinsLoader.getInstance().getSkin(stand);
				ActionAnimIdentifier idleAnim = StandEntityRenderer.IDLE_ANIM;
				idleAnim = selectStandIdleAnim(stand, idleAnim);

				if (animVariables.animId == null) {
					float standTicks = stand.tickCount + partialTick;
					RotpAnimDefinition summonAnim = standSkin != null ?
							standSkin.getStandAnimation(anims -> anims.getSummonAnim(SUMMON_ANIM.name(), stand.getSummonPoseRandomByte())) : null;
					if (summonAnim != null && standTicks <= summonAnim.lengthInSeconds * 20) {
						animVariables.animId = ActionAnimIdentifier.getOrCreate(SUMMON_ANIM.name(), stand.getSummonPoseRandomByte(), false);
						animVariables.time = standTicks;
					}
					else {
						float idleTime = stand.tickCount - stand.nonIdlePoseTimeStamp + partialTick;
						animVariables.animId = idleAnim;
						animVariables.time = idleTime;
					}
				}
				if (newFrame && !animVariables.animId.isIdle()) {
					stand.nonIdlePoseTimeStamp = stand.tickCount;
				}

				AnimWithId animPossiblyReplaced = getStandAnim(standSkin, animVariables.animId, idleAnim, stand.isArmsOnlyMode());
				anim = animPossiblyReplaced.anim;
				animVariables.animId = animPossiblyReplaced.animId;
			}
			else {
				anim = getPlayerAnim(animVariables.animSet, animVariables.animId);
			}
			
			if (anim != null) {
				float timeSeconds = anim.getAnimTime(animVariables);
				pose = anim.calcAnimPose(AnimMolangVariables.extract(living, partialTick), 
						actionComponent != null ? actionComponent.clPrevPunchPose : null, timeSeconds, 1);
				((AnimatedEntity) living).jojo_ripples$setModelPose(AnimatedEntity.PoseType.UNMODIFIED, pose);
				
				if (newFrame) {
					BarrageSwings barrageSwings = getBarrageSwings(living);
					if (barrageSwings != null) {
						barrageSwings.frameStandBarrage(Minecraft.getInstance(), anim, timeSeconds, living, living.tickCount + partialTick);
					}
				}
				
				if (ClientModSettings.getSettingsReadOnly().standMotionTilt && stand != null) {
					EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(living);
					if (renderer instanceof StandEntityRenderer standEntityRenderer) {
						StandEntityModel model = standEntityRenderer.getEntityModel(stand);
						if (model != null) {
							Vec3 motionTiltVec = model.prepareMotionTilt(stand, partialTick);
							boolean idlePose = animVariables.animId != null && animVariables.animId.isIdle();
							model.doMotionTilt(motionTiltVec, pose, idlePose);
						}
					}
				}
			}
		}
		return pose;
	}

	private static ActionAnimIdentifier selectStandIdleAnim(StandEntity stand, ActionAnimIdentifier baseIdleAnim) {
		if (!StandEntityRenderer.IDLE_ANIM.equals(baseIdleAnim)) {
			return baseIdleAnim;
		}
		if (!stand.isSilverChariotRapierVisible()) {
			return SC_NO_RAPIER_IDLE_ANIM;
		}
		if (!stand.isSilverChariotArmorVisible()) {
			return SC_NO_ARMOR_IDLE_ANIM;
		}
		return baseIdleAnim;
	}
	
	public static RotpAnimDefinition getPlayerAnim(ResourceLocation animSetPath, ActionAnimIdentifier animId) {
		if (animSetPath != null && animId != null) {
			AnimationSet animSet = AnimationLoader.getInstance().getAnimSet(animSetPath);
			if (animSet != null) {
				RotpAnimDefinition anim = animSet.getNamedAnim(animId);
				return anim;
			}
		}
		return null;
	}
	
	public static AnimWithId getStandAnim(StandSkin skin, ActionAnimIdentifier animId, ActionAnimIdentifier curIdleAnim) {
		return getStandAnim(skin, animId, curIdleAnim, false);
	}
	
	public static AnimWithId getStandAnim(StandSkin skin, ActionAnimIdentifier animId, ActionAnimIdentifier curIdleAnim, boolean armsOnly) {
		if (skin != null) {
			if (animId != null) {
				RotpAnimDefinition anim = skin.getStandAnimation(anims -> anims.getNamedAnim(animId, armsOnly));
				if (anim == null) {
					anim = skin.getStandAnimation(anims -> anims.getNamedAnim(curIdleAnim));
					if (anim != null) {
						return AnimWithId.with(curIdleAnim, anim);
					}
					if (curIdleAnim != null && curIdleAnim.isIdle() && !StandEntityRenderer.IDLE_ANIM.equals(curIdleAnim)) {
						anim = skin.getStandAnimation(anims -> anims.getNamedAnim(StandEntityRenderer.IDLE_ANIM));
						if (anim != null) {
							return AnimWithId.with(StandEntityRenderer.IDLE_ANIM, anim);
						}
					}
				}
				return AnimWithId.with(animId, anim);
			}
		}
		return AnimWithId.with(null, null);
	}
	
	@Nullable
	public static BarrageSwings getBarrageSwings(LivingEntity entity) {
		if (entity instanceof StandEntity stand) {
			return stand.clientStuff.barrageSwings;
		}
		return ((AnimatedEntity) entity).jojo_ripples$getBarrageSwings();
	}

}
