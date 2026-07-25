package com.github.standobyte.jojo.powersystem.ability;

import java.util.function.BiFunction;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resources.ResourceLocation;

@ApiStatus.NonExtendable
public class AbilityType<A extends Ability> {
	public final ResourceLocation registryKey;
	protected BiFunction<AbilityType<A>, AbilityId, A> constructor;
	public final Ability _defaultAbilityInstance;
	
	public AbilityType(ResourceLocation registryKey, BiFunction<AbilityType<A>, AbilityId, A> constructor) {
		this.registryKey = registryKey;
		this.constructor = constructor;
		this._defaultAbilityInstance = AbilityId.makeDefaultAbilityInstance(this);//constructor.apply(new AbilityId(null, null, registryKey.toString()));
	}
	
	public A createInstance(AbilityId abilityId) {
		return constructor.apply(this, abilityId);
	}
	
}
