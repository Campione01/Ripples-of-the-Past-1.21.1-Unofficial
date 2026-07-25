package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility.CrazyDiamondHeavyPunch;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class CrazyDMisshapingPunchEffect extends StandEffectInstance {
	private static final int EFFECT_DURATION = 60;
	private static final int MISSHAPEN_DURATION = 200;

	public CrazyDMisshapingPunchEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		isFromStandAction = true;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide()) {
			if (standAction == null) {
				remove();
				return;
			}
			else if (standAction.getActionTicksLeft() <= 1 && standAction.punchedTarget != null && standAction.punchedTarget.getEntity() instanceof LivingEntity target) {
				LivingEntity affected = StandUtil.getStandUser(target);
				applyMisshaping(affected, targetHitPart(target));
				affected.heal(standAction.lastDamageDealtToLiving * 0.5F);
				remove();
			}
			else if (standAction.isOver()) {
				remove();
			}
		}
		else {
			tickClientRestorationVisuals();
		}
	}

	@Override
	protected void stop() {}

	private void applyMisshaping(LivingEntity target, CrazyDTargetHitPart hitPart) {
		switch (hitPart) {
		case HEAD -> {
			target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_DURATION, 0, false, false, true));
			target.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_FACE, MISSHAPEN_DURATION, 0, false, false, true));
		}
		case TORSO_ARMS -> {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION, 0, false, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_DURATION, 1, false, false, true));
			target.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_ARMS, MISSHAPEN_DURATION, 0, false, false, true));
		}
		case LEGS -> {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 1, false, false, true));
			target.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_LEGS, MISSHAPEN_DURATION, 0, false, false, true));
		}
		}
	}

	private CrazyDTargetHitPart targetHitPart(LivingEntity target) {
		if (standAction instanceof CrazyDiamondHeavyPunch cdPunch) {
			CrazyDTargetHitPart hitPart = cdPunch.getMisshapingTargetPart();
			if (hitPart != null) {
				return hitPart;
			}
		}
		Vec3 hitPos = standAction.punchedTarget.getClipPos()
				.orElseGet(() -> target.position().add(0, target.getBoundingBox().getYsize() * 0.5D, 0));
		return CrazyDTargetHitPart.getHitTarget(target, hitPos.y - target.getY());
	}

	private void tickClientRestorationVisuals() {
		if (standAction == null || standAction.isOver() || standAction.punchedTarget == null) {
			return;
		}
		Entity entity = standAction.punchedTarget.getEntity();
		if (entity instanceof LivingEntity target && target.isAlive()) {
			CrazyDHealAbility.addParticlesAround(StandUtil.getStandUser(target));
			if (tickCount == 1) {
				if (standAction.getPerformer() instanceof StandEntity stand) {
					if (ClientGlobals.canHearStand(stand)) {
						level.playLocalSound(target.getX(), target.getY(0.5), target.getZ(),
								ClientsideSoundsHelper.withStandSkin(ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), stand),
								stand.getSoundSource(), 1.0F, 1.0F, false);
					}
				}
				else {
					level.playLocalSound(target.getX(), target.getY(0.5), target.getZ(),
							ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), target.getSoundSource(), 1.0F, 1.0F, false);
				}
			}
		}
	}

}
