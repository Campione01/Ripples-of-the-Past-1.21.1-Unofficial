package rotp.core.mechanics.standarrow;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.entityattachment.custom_effect.EntityCustomEffect;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectsClass;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectsMap;
import rotp.core.init.ModEntityCustomEffects;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// is pretty much only needed to show up in the UI, the actual logic is now in StandVirusActualEffect class
public class StandVirusEffect extends StatusEffectModified implements StatusEffectApplicable {

    public StandVirusEffect(MobEffectCategory category, int color) {
        super(category, color);
        this.isUncurable = true;
    }

	
	public static void onNearbyVirusSource(LivingEntity entity) {
    	// TODO event
		if (!entity.getType().is(EntityTypeTags.UNDEAD)
				&& entity.getHealth() < entity.getMaxHealth()
				&& !StandVirusEffect.isImmuneToMeteoriteStrain(entity)
				&& !entity.hasEffect(ModStatusEffects.STAND_VIRUS)) {
			entity.addEffect(new MobEffectInstance(ModStatusEffects.STAND_VIRUS, 600, 0, false, false, true));
		}
	}

	public static boolean isImmuneToMeteoriteStrain(LivingEntity entity) {
		if (entity instanceof StandEntity) return true;
		StandPower stand = StandPower.get(entity);
		return stand != null && (stand.hasPower() || stand.userStandAwakeningState.hadStandBefore);
	}


    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !StandUtil.isEntityStandUser(entity);
    }

    @Override
	public void onAdded(LivingEntity entity, MobEffectInstance instance, @Nullable Entity source) {
    	super.onAdded(entity, instance, source);
    	if (!entity.level().isClientSide()) {
    		getActualVirusEffect(entity, true);
    	}
    }

    @Override
    public void onRemoved(LivingEntity entity, MobEffectInstance instance) {
        super.onRemoved(entity, instance);
        if (!entity.level().isClientSide()) {
        	StandVirusActualEffect effect = getActualVirusEffect(entity, false);
        	if (effect != null) effect.remove();
        }
    }
    
    
    @Nullable
    public static StandVirusActualEffect getActualVirusEffect(LivingEntity entity, boolean createIfAbsent) {
    	EntityCustomEffectsMap<EntityCustomEffect> effects = EntityCustomEffectsClass.getCustomEffects(entity, createIfAbsent);
    	if (effects != null) {
    		Optional<StandVirusActualEffect> existing = effects.getEffects().stream()
    				.filter(effect -> effect.is(ModEntityCustomEffects.STAND_VIRUS))
    				.map(effect -> (StandVirusActualEffect) effect)
    				.findAny();
    		if (existing.isPresent()) return existing.get();
    		
    		if (createIfAbsent) {
    			StandVirusActualEffect newEffect = new StandVirusActualEffect();
    			effects.addEffect(newEffect);
    			return newEffect;
    		}
    	}
    	
    	return null;
    }
}
