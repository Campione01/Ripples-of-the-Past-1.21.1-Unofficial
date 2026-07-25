package com.github.standobyte.jojo.mixin.entity_like_player.npc;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attributes") public AttributeMap getAttributes();
	@Accessor("attributes") @Mutable public void setAttributes(AttributeMap attributes);
	
	@Accessor("combatTracker") public CombatTracker getCombatTracker();
	@Accessor("combatTracker") @Mutable public void setCombatTracker(CombatTracker combatTracker);
	
	@Accessor("activeEffects") public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffects();
	@Accessor("activeEffects") @Mutable public void setActiveEffects(Map<Holder<MobEffect>, MobEffectInstance> activeEffects);
}
