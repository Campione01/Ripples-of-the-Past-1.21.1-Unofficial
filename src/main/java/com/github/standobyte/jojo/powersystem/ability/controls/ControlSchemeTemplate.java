package com.github.standobyte.jojo.powersystem.ability.controls;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.Util;

public class ControlSchemeTemplate {
	public Map<String, GroupTemplate> groups = new LinkedHashMap<>();
	public GroupTemplate defaultGroup = new GroupTemplate("moveset_default_group", null);
	private transient Int2ObjectMap<AbilitiesHotbar> hotbarsById = new Int2ObjectArrayMap<>();

	public ControlSchemeTemplate deepCopy() {
		ControlSchemeTemplate copy = new ControlSchemeTemplate();
		copy.groups.clear();
		copy.hotbarsById.clear();
		Map<AbilitiesHotbar, AbilitiesHotbar> hotbarCopies = new IdentityHashMap<>();
		
		for (GroupTemplate group : this.groups.values()) {
			GroupTemplate groupCopy = group == this.defaultGroup 
					? copy.defaultGroup 
					: new GroupTemplate(group.name, group.toggleHudKey);
			groupCopy.separateBinds.putAll(group.separateBinds);
			for (AbilitiesHotbar hotbar : group.hotbars) {
				AbilitiesHotbar hotbarCopy = copyHotbar(hotbar);
				hotbarCopies.put(hotbar, hotbarCopy);
				groupCopy.hotbars.add(hotbarCopy);
			}
			copy.groups.put(groupCopy.name, groupCopy);
		}
		
		for (var entry : this.hotbarsById.int2ObjectEntrySet()) {
			AbilitiesHotbar hotbarCopy = hotbarCopies.get(entry.getValue());
			if (hotbarCopy != null) {
				copy.hotbarsById.put(entry.getIntKey(), hotbarCopy);
			}
		}
		
		copy._curGroup = this._curGroup == this.defaultGroup
				? copy.defaultGroup
				: copy.groups.getOrDefault(this._curGroup.name, copy.defaultGroup);
		return copy;
	}

	private static AbilitiesHotbar copyHotbar(AbilitiesHotbar hotbar) {
		AbilitiesHotbar copy = new AbilitiesHotbar(hotbar.useAbilityKey, hotbar.switchAbilityKey);
		for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
			copy.slots.add(copySlot(slot));
		}
		return copy;
	}

	private static Map<InputKey.Modifier, Map<InputMethod, String>> copySlot(
			Map<InputKey.Modifier, Map<InputMethod, String>> slot) {
		Map<InputKey.Modifier, Map<InputMethod, String>> copy = new HashMap<>();
		for (var entry : slot.entrySet()) {
			copy.put(entry.getKey(), copyInputMap(entry.getValue()));
		}
		return copy;
	}

	private static Map<InputMethod, String> copyInputMap(Map<InputMethod, String> inputMap) {
		return new EnumMap<>(inputMap);
	}

	public static class GroupTemplate {
		public final String name;
		@Nullable public final InputBindTemplate toggleHudKey;

		public Map<String, Pair<InputMethod, InputBindTemplate>> separateBinds = new LinkedHashMap<>();
		public List<AbilitiesHotbar> hotbars = new ArrayList<>();

		public GroupTemplate(String name, InputBindTemplate toggleHudKey) {
			this.name = name;
			this.toggleHudKey = toggleHudKey;
		}

		public boolean isEmpty() {
			return separateBinds.isEmpty() && hotbars.isEmpty();
		}
	}

	public static class AbilitiesHotbar {
		public List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots = new ArrayList<>();
		public InputBindTemplate useAbilityKey;
		@Nullable public InputBindTemplate switchAbilityKey;

		public AbilitiesHotbar(InputBindTemplate useAbilityKey, @Nullable InputBindTemplate switchAbilityKey) {
			this.useAbilityKey = useAbilityKey;
			this.switchAbilityKey = switchAbilityKey;
		}
	}

	public ControlSchemeTemplate() {
		groups.put(defaultGroup.name, defaultGroup);
	}


	@ApiStatus.Internal
	public MovesetBuilder curMovesetBuilder;

	@ApiStatus.Internal
	public MovesetBuilder finalizeControlScheme() {
		MovesetBuilder movesetBuilder = curMovesetBuilder;
		this.curMovesetBuilder = null;
		return movesetBuilder;
	}


	public ControlSchemeTemplate bind(String ability, InputMethod inputMethod, InputBindTemplate key) {
		_curGroup.separateBinds.put(ability, Pair.of(inputMethod, key));
		return this;
	}

	public ControlSchemeTemplate makeHotbar(int hotbarId, InputBindTemplate useAbilityKey, @Nullable InputBindTemplate switchAbilityKey) {
		if (_curGroup != null) {
			AbilitiesHotbar hotbar = new AbilitiesHotbar(useAbilityKey, switchAbilityKey);
			hotbarsById.put(hotbarId, hotbar);
			_curGroup.hotbars.add(hotbar);
		}
		return this;
	}

	public ControlSchemeTemplate addToHotbar(String ability, int hotbarId, InputMethod inputMethod) {
		Map<InputKey.Modifier, Map<InputMethod, String>> slot = new HashMap<>();
		slot.put(null, Util.make(new EnumMap<>(InputMethod.class), map -> map.put(inputMethod, ability)));
		hotbarsById.get(hotbarId).slots.add(slot);
		return this;
	}

	public ControlSchemeTemplate addHotbarSlotVariation(String ability, String baseAbility, @Nullable InputKey.Modifier modifier, InputMethod inputMethod) {
		for (AbilitiesHotbar hotbar : hotbarsById.values()) {
			for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
				Map<InputMethod, String> baseVariation = slot.get(null);
				if (baseVariation != null && baseVariation.values().contains(baseAbility)) {
					Map<InputMethod, String> byInputMethod = slot.computeIfAbsent(modifier, 
							__ -> new EnumMap<>(InputMethod.class));
					byInputMethod.put(inputMethod, ability);
				}
			}
		}
		return this;
	}
	
	
	
	@ApiStatus.Internal
	public GroupTemplate _curGroup = defaultGroup;
	public ControlSchemeTemplate makeMovesetGroup(String name, InputBindTemplate toggleHudKey) {
		GroupTemplate group = groups.computeIfAbsent(name, _name -> new GroupTemplate(_name, toggleHudKey));
		this._curGroup = group;
		return this;
	}
	
	public ControlSchemeTemplate setMovesetGroup(@Nullable String name) {
		this._curGroup = getMovesetGroup(name);
		return this;
	}

	public GroupTemplate getMovesetGroup(@Nullable String name) {
		return name == null ? defaultGroup : groups.get(name);
	}

}
