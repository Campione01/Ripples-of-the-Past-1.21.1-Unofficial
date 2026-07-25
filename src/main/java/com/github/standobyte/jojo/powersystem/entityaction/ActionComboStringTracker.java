package com.github.standobyte.jojo.powersystem.entityaction;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;

public class ActionComboStringTracker {
	public List<AbilityId> _abilities = new ArrayList<>();
	
	@Nullable
	public AbilityId getLast() {
		return !_abilities.isEmpty() ? _abilities.get(_abilities.size() - 1) : null;
	}
	
	public void onNewAction(EntityActionInstance action) {
		this._abilities.add(action.ability.getAbilityId());
	}
	
	public void clear() {
		_abilities.clear();
	}
	
}
