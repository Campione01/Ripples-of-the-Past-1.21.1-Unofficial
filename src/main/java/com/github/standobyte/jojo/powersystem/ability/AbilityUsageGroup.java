package com.github.standobyte.jojo.powersystem.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

public class AbilityUsageGroup {
	public static List<AbilityUsageGroup> values = new ArrayList<>();
	
	public static final AbilityUsageGroup COMBAT = create("combat");
	public static final AbilityUsageGroup GRAB = create("grab");
	public static final AbilityUsageGroup UTILITY = create("utility");
	
	public static final AbilityUsageGroup SPECIAL = create("special");
	public static final AbilityUsageGroup INVENTORY = create("inventory", group -> group.canAddToHotbar = false);
	
	protected String key;
	protected Component name;
	public boolean canAddToHotbar = true;
	protected AbilityUsageGroup(String name) {
		this.key = name;
		this.name = Component.translatable("jojo_ripples.ability_group." + name);
	}
	
	public static AbilityUsageGroup create(String name) {
		return create(name, null);
	}
	
	public static AbilityUsageGroup create(String name, Consumer<AbilityUsageGroup> init) {
		AbilityUsageGroup group = new AbilityUsageGroup(name);
		if (init != null) init.accept(group);
		values.add(group);
		return group;
	}
}
