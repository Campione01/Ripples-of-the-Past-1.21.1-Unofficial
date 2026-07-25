package com.github.standobyte.jojo.powersystem.ability.config;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;

@ApiStatus.Internal
public class ConfigAbilityFactory<A extends Ability> {
	private final AbilityType<A> abilityType;
	@Nullable private Consumer<A> onInit;

	public ConfigAbilityFactory(AbilityType<A> abilityType, @Nullable Consumer<A> onInit) {
		this.abilityType = abilityType;
		this.onInit = onInit;
	}
	
	public ConfigAbilityFactory<A> copy() {
		return new ConfigAbilityFactory<>(abilityType, onInit);
	}
	
	public void addInitBehavior(Consumer<A> onInit) {
		if (this.onInit == null) {
			this.onInit = onInit;
		}
		else {
			this.onInit = this.onInit.andThen(onInit);
		}
	}

	public A makeAbility(AbilityId abilityId) {
		A ability = abilityType.createInstance(abilityId);
		if (onInit != null) {
			onInit.accept(ability);
		}
		return ability;
	}
	
}
