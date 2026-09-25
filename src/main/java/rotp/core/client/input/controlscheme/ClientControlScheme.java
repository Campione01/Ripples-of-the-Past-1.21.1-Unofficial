package rotp.core.client.input.controlscheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.AbilityInputState;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.InputHandler.BaseAndActiveAbility;
import rotp.core.config.client.ClientModSettings;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.KeyActiveType;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.OnKeyPress;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate.AbilitiesHotbar;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate.SeparateBindTemplate;
import com.mojang.datafixers.util.Pair;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.TriState;

public class ClientControlScheme {
	public PowerClass<?> powerClassCosmetic;
	@Nullable public PowerType powerType;
	/** 1.16 ControlScheme#hotbarsEnabled: off leaves the HUD hotbars empty, custom keys still work. */
	public boolean hotbarsEnabled = true;
	@ApiStatus.Internal public final Map<String, MoveGroup> moveGroups = new LinkedHashMap<>();
	@ApiStatus.Internal protected MoveGroup curGroup;
	protected static final MoveGroup EMPTY = new MoveGroup("", Component.empty(), null);
	
	public static class MoveGroup {
		public String internalName;
		@ApiStatus.Internal public Component name;
		@ApiStatus.Internal public ClientInputBind toggleHudKey;
		
		@ApiStatus.Internal public List<Bind> binds = new ArrayList<>();
		@ApiStatus.Internal public List<Hotbar> hotbars = new ArrayList<>();
		
		protected Map<ClientKey, InputsByKeyModifier> bindsMap = new TreeMap<>(Comparator.comparingInt(ClientKey::keyOrder));
		
		public MoveGroup(String internalName, Component name, ClientInputBind toggleHudKey) {
			this.internalName = internalName;
			this.name = name;
			this.toggleHudKey = toggleHudKey;
		}
		
		/* TODO cache the binds map
		 *   only clear the cache when any key changes
		 *   (*including* the vanilla keybinds, on KepMapping#setKey(InputConstants.Key))
		 */
		/**
		 * Keys shown in the open HUD (1.16 ActionsOverlayGui#updateHotkeyUi): its own keys, custom ones
		 * by hudIcon and KeyActiveType whatever their OnKeyPress, plus the other owned powers' visible
		 * OUTSIDE_HUD / ALWAYS custom keys.
		 */
		public Map<ClientKey, InputsByKeyModifier> getBinds() {
			getBinds(true, true);
			addOffHudKeys();
			return bindsMap;
		}

		/** Other owned powers' custom keys that work with their HUD closed (AllControlSchemes#getCustomKeyScheme). */
		protected void addOffHudKeys() {
			List<MoveGroup> earlier = new ArrayList<>();
			for (PowerClass<?> powerClass : PowerClass.values()) {
				Power<?> power = ClientPowerCache.getPower(powerClass);
				ClientControlScheme scheme = power != null && power.hasPower() ? AllControlSchemes.getForPowerType(power.getPowerType()) : null;
				MoveGroup group = scheme != null && !scheme.moveGroups.containsValue(this) ? scheme.peekCurGroup() : null;
				if (group == null) continue;
				List<Bind> shown = new ArrayList<>();
				for (Bind bind : group.binds) {
					ClientKey key = bind.input != null && bind.shownInHud(false) ? bind.input.getKey() : null;
					if (key == null) continue;
					KeyModifier modifier = bind.input.getKeyModifier();
					// the open HUD's own custom key, or an earlier power's, takes the press
					if (hasCustomKey(this, key, modifier, true) || earlier.stream().anyMatch(g -> hasCustomKey(g, key, modifier, false))) continue;
					shown.add(bind);
				}
				for (Bind bind : shown) {
					// this power's key replaces the open HUD's template binds on it
					InputsByKeyModifier onKey = bindsMap.get(bind.input.getKey());
					if (onKey != null) onKey.movesByModifier.remove(bind.input.getKeyModifier());
				}
				shown.forEach(this::addToMap);
				earlier.add(group);
			}
		}

		private static boolean hasCustomKey(MoveGroup group, ClientKey key, KeyModifier modifier, boolean hudActive) {
			for (Bind bind : group.binds) {
				if (bind.customKeyMatches(key, modifier, hudActive)) return true;
			}
			return false;
		}

		/** @param hudActive the power's HUD is the open one (decides custom keys' 1.16 KeyActiveType) */
		public Map<ClientKey, InputsByKeyModifier> getBinds(boolean hudActive) {
			return getBinds(hudActive, false);
		}

		protected Map<ClientKey, InputsByKeyModifier> getBinds(boolean hudActive, boolean forHudDisplay) {
			bindsMap.clear();
			for (Bind bind : binds) {
				if (bind.input != null && bind.listedIn(hudActive, forHudDisplay)) {
					addToMap(bind);
				}
			}

			return bindsMap;
		}

		private void addToMap(Bind bind) {
			ClientKey key = bind.input.getKey();
			if (key != null) {
				KeyModifier keyModifier = bind.input.getKeyModifier();
				String abilityName = bind.ability.abilityName;
				InputMethod inputMethod = bind.inputMethod;
				PowerClass<?> powerClass = bind.ability.powerClass;

				InputsByKeyModifier keyAllBinds = bindsMap.computeIfAbsent(key,
						__ -> new InputsByKeyModifier());
				Map<InputMethod, List<AbilityControlsEntry>> modifierKeyBinds = keyAllBinds.movesByModifier.computeIfAbsent(keyModifier,
						__ -> new EnumMap<>(InputMethod.class));
				List<AbilityControlsEntry> byInputMethod = modifierKeyBinds.computeIfAbsent(inputMethod,
						__ -> new ArrayList<>());
				byInputMethod.add(new AbilityControlsEntry(powerClass, abilityName));
			}
		}
	}
	
	public static record AbilityControlsEntry(PowerClass<?> powerClass, String abilityName) {
		
		public AbilityConditionCheck getAbility() {
			AvailableAbilities allAbilities = ClientPowerCache.getAvailableAbilities(this.powerClass);
			AbilityConditionCheck ability = allAbilities.getContextVariationContainer(this.abilityName);
			if (ability == null && ClientModSettings.getSettingsReadOnly().showLockedSlots) {
				Power<?> power = ClientPowerCache.getPower(this.powerClass);
				Ability baseAbility = power != null && power.getMoveset() != null ? power.getMoveset().getAbility(this.abilityName) : null;
				if (baseAbility != null) {
					ConditionCheck unlockCheck = baseAbility.getUnlockConditionCheck(power);
					if (!unlockCheck.isPositive()) {
						ability = allAbilities.getDisplayOnlyContainerFor(baseAbility, unlockCheck);
					}
				}
				if (ability != null) {
					ability.clientInputState = baseAbility.cl_abilityInputState(power)._value;
				}
			}
			return ability;
		}
		
		/** In the current moveset right now (unlocked and valid in the context), not just shown as locked. */
		public boolean isAvailable() {
			AvailableAbilities allAbilities = this.powerClass != null ? ClientPowerCache.getAvailableAbilities(this.powerClass) : null;
			return allAbilities != null && allAbilities.getContextVariationContainer(this.abilityName) != null;
		}
	}
	
	public static class Bind {
		public ClientInputBind input;
		public InputMethod inputMethod;
		public AbilityControlsEntry ability;
		/** Added by the player in the controls editor (saved per power type). */
		public boolean custom;
		public OnKeyPress onKeyPress = OnKeyPress.PERFORM;
		public KeyActiveType activeType = KeyActiveType.INSIDE_HUD;
		public boolean visibleInHud = true;

		public Bind(ClientInputBind input, InputMethod inputMethod, AbilityControlsEntry ability) {
			this.input = input;
			this.inputMethod = inputMethod;
			this.ability = ability;
		}

		public boolean performsWith(boolean hudActive) {
			return !custom || onKeyPress == OnKeyPress.PERFORM && activeType.canTrigger(hudActive);
		}

		/** 1.16 HUD hotkey list: isVisibleInHud() && getHudInteraction().canTrigger(open), whatever the OnKeyPress. */
		public boolean shownInHud(boolean hudActive) {
			return custom ? visibleInHud && activeType.canTrigger(hudActive) : hudActive;
		}

		/** In a group's key map: the HUD display list, or the keys that perform on press (input). */
		public boolean listedIn(boolean hudActive, boolean forHudDisplay) {
			return forHudDisplay ? shownInHud(hudActive) : performsWith(hudActive);
		}

		public boolean customKeyMatches(ClientKey key, KeyModifier curModifier, boolean hudActive) {
			return custom && input != null && activeType.canTrigger(hudActive) && input.keyMatches(key, curModifier);
		}
	}

	public static class Hotbar {
		public ClientInputBind useAbilityKey;
		@Nullable public ClientInputBind switchAbilityKey;
		/** Slots shown in the HUD, in order (layout minus hidden ones). */
		public List<HotbarSlot> slots = new ArrayList<>();
		/** Every slot in the player's order, hidden ones included. */
		public final List<HotbarSlot> layout = new ArrayList<>();
		public int slotIndex = 0;

		public Hotbar(ClientInputBind useAbilityKey, @Nullable ClientInputBind switchAbilityKey) {
			this.useAbilityKey = useAbilityKey;
			this.switchAbilityKey = switchAbilityKey;
		}
		
		@Nullable
		public HotbarSlot getSelected() {
			return this.slotIndex >= 0 && this.slotIndex < this.slots.size() ? this.slots.get(this.slotIndex) : null;
		}
		
		public boolean alwaysSwitchAbility() {
			return switchAbilityKey == null || switchAbilityKey.getKey() == null;
		}

		/** Rebuilds the HUD slots from the layout (1.16 ActionsHotbar#getEnabledActions). */
		public void applyLayout(boolean hotbarsEnabled) {
			slots.clear();
			if (hotbarsEnabled) {
				for (HotbarSlot slot : layout) {
					if (!slot.hidden) {
						slot.index = slots.size();
						slots.add(slot);
					}
				}
			}
			if (slotIndex >= slots.size()) {
				slotIndex = 0;
			}
		}
	}
	
	public static class HotbarSlot {
		public int index;
		public final InputsByKeyModifier binds = new InputsByKeyModifier(true);
		/** Disabled in the controls editor: kept out of the HUD hotbar. */
		public boolean hidden;

		public HotbarSlot(int index) {
			this.index = index;
		}

		/** Name the saved layout uses for this slot: its first base ability. */
		@Nullable
		public String layoutKey() {
			AbilityControlsEntry first = null;
			for (var byModifier : binds.movesByModifier.entrySet()) {
				for (InputMethod inputMethod : InputMethod.values()) {
					List<AbilityControlsEntry> entries = byModifier.getValue().get(inputMethod);
					if (entries != null && !entries.isEmpty()) {
						if (byModifier.getKey() == KeyModifier.NONE) return entries.get(0).abilityName();
						if (first == null) first = entries.get(0);
					}
				}
			}
			return first != null ? first.abilityName() : null;
		}

		public boolean hasAbility(String abilityName) {
			for (var byModifier : binds.movesByModifier.values()) {
				for (List<AbilityControlsEntry> entries : byModifier.values()) {
					for (AbilityControlsEntry entry : entries) {
						if (entry.abilityName().equals(abilityName)) return true;
					}
				}
			}
			return false;
		}

		public InputsByKeyModifier getBinds() {
			return binds;
		}
		
		@Nullable
		public AbilityControlsEntry getBaseBind(InputMethod inputMethod) {
			return binds.getFirst(KeyModifier.NONE, inputMethod);
		}

		@Nullable
		public AbilityConditionCheck showAbility() {
			for (InputMethod inputMethod : InputMethod.values()) {
				AbilityControlsEntry abilityEntry = getBaseBind(inputMethod);
				if (abilityEntry != null) {
					AbilityConditionCheck ability = abilityEntry.getAbility();
					if (ability != null) {
						boolean showAbility = AbilityInputState.showAbilityInHUD(ability, TriState.FALSE)
								|| AbilityInputState.showAbilityInHUD(ability, TriState.TRUE);
						if (showAbility) {
							return ability;
						}
					}
				}
			}
			return null;
		}
		
		public static int numberKey(int slotIndex) {
			if (slotIndex >= 0 && slotIndex < 9) {
				return slotIndex + 1;
			}
			if (slotIndex == 9) return 0;
			return -1;
		}
	}
	
	public static class InputsByKeyModifier {
		public final Map<KeyModifier, Map<InputMethod, List<AbilityControlsEntry>>> movesByModifier = new EnumMap<>(KeyModifier.class);
		private final boolean lockedShiftUsesBase;
		
		public InputsByKeyModifier() {
			this(false);
		}
		
		/**
		 * @param lockedShiftUsesBase hotbar slots: a SHIFT variation that isn't available leaves
		 * SHIFT on the slot's base ability. 1.16 (ActionsOverlayGui#resolveVisibleActionInSlot)
		 * only switched to the shift variation once it was unlocked, and then for the whole slot,
		 * so a modifier with its own variation never falls back to the base per input method.
		 */
		public InputsByKeyModifier(boolean lockedShiftUsesBase) {
			this.lockedShiftUsesBase = lockedShiftUsesBase;
		}
		
		public List<AbilityControlsEntry> getAll(@Nonnull KeyModifier curModifier, InputMethod inputMethod) {
			return getAll(curModifier, inputMethod, AbilityControlsEntry::isAvailable);
		}
		
		List<AbilityControlsEntry> getAll(@Nonnull KeyModifier curModifier, InputMethod inputMethod,
				Predicate<AbilityControlsEntry> isAvailable) {
			List<AbilityControlsEntry> list = null;
			Map<InputMethod, List<AbilityControlsEntry>> modifiedBinds = movesByModifier.get(curModifier);
			if (modifiedBinds != null) {
				list = modifiedBinds.get(inputMethod);
			}
			if ((list == null || list.isEmpty()) && curModifier != KeyModifier.NONE) {
				// 1.16 swapped the whole hotbar slot to its shift variation: a variation bound only as a click
				// (or only as a hold) must not borrow the base ability for the other input method.
				boolean slotVariation = lockedShiftUsesBase && modifierOwnsSlot(curModifier, isAvailable);
				Map<InputMethod, List<AbilityControlsEntry>> fallbackBinds = slotVariation ? null : movesByModifier.get(KeyModifier.NONE);
				if (fallbackBinds != null) {
					list = fallbackBinds.get(inputMethod);
				}
			}
			else if (lockedShiftUsesBase && curModifier == KeyModifier.SHIFT
					&& list.stream().noneMatch(isAvailable)) {
				Map<InputMethod, List<AbilityControlsEntry>> baseBinds = movesByModifier.get(KeyModifier.NONE);
				List<AbilityControlsEntry> baseList = baseBinds != null ? baseBinds.get(inputMethod) : null;
				if (baseList != null && !baseList.isEmpty()) {
					list = baseList;
				}
			}
			return list != null ? list : Collections.emptyList();
		}
		
		/** The modifier has its own variation on this slot (SHIFT: one that is available) under any input method. */
		private boolean modifierOwnsSlot(KeyModifier modifier, Predicate<AbilityControlsEntry> isAvailable) {
			Map<InputMethod, List<AbilityControlsEntry>> binds = movesByModifier.get(modifier);
			if (binds != null) {
				for (List<AbilityControlsEntry> entries : binds.values()) {
					if (entries != null && !entries.isEmpty()
							&& (modifier != KeyModifier.SHIFT || entries.stream().anyMatch(isAvailable))) {
						return true;
					}
				}
			}
			return false;
		}
		
		@Nullable
		public AbilityControlsEntry getFirst(@Nonnull KeyModifier curModifier, InputMethod inputMethod) {
			List<AbilityControlsEntry> list = getAll(curModifier, inputMethod);
			return !list.isEmpty() ? list.get(0) : null;
		}
	}


	public boolean hasAbility(Predicate<AbilityControlsEntry> condition) {
		MoveGroup moves = this.getCurGroup();
		for (var bind : moves.binds) {
			if (condition.test(bind.ability)) {
				return true;
			}
		}
		
		for (var hotbar : moves.hotbars) {
			for (var hotbarSlot : hotbar.slots) {
				for (var byModifier : hotbarSlot.binds.movesByModifier.entrySet()) {
					for (var byInputMethod : byModifier.getValue().entrySet()) {
						for (var ability : byInputMethod.getValue()) {
							if (condition.test(ability)) { // looks cursed, I know
								return true;
							}
						}
					}
				}
			}
		}
		
		return false;
	}
	
	
	@Nonnull
	public MoveGroup getCurGroup() {
		if (curGroup == null) {
			setCurGroup(moveGroups.values().stream().findFirst().orElse(EMPTY));
		}
		return curGroup;
	}

	/** getCurGroup without selecting it (selecting clears the hotbar selection). */
	@Nullable
	protected MoveGroup peekCurGroup() {
		return curGroup != null ? curGroup : moveGroups.values().stream().findFirst().orElse(null);
	}

	private static ClientControlScheme noHud;

	/** No power's HUD open: a scheme whose key list only has the owned powers' off-HUD custom keys. */
	public static ClientControlScheme noHudKeys() {
		if (noHud == null) {
			noHud = new ClientControlScheme();
			noHud.curGroup = EMPTY;
		}
		return noHud;
	}

	protected void setCurGroup(MoveGroup moveGroup) {
		if (this.curGroup != moveGroup) {
			this.curGroup = moveGroup;
			InputHandler.getInstance().onUpdatedControls(moveGroup);
		}
	}

	public void resetToFirstGroup() {
		setCurGroup(moveGroups.values().stream().findFirst().orElse(EMPTY));
	}

	public boolean selectNextGroup() {
		MoveGroup current = getCurGroup();
		boolean selectNext = false;
		for (MoveGroup group : moveGroups.values()) {
			if (selectNext) {
				setCurGroup(group);
				return true;
			}
			selectNext = group == current;
		}
		return false;
	}
	
	/** This power's HUD is the open one, so its hotbars and template keys are in use. */
	public boolean isHudActive() {
		return InputHandler.getInstance().getActiveControlScheme() == this;
	}

	/** A custom key of the current group is bound to this input and active in this HUD state. */
	public boolean hasCustomBindFor(ClientKey key, KeyModifier currentModifier, boolean hudActive) {
		for (Bind bind : getCurGroup().binds) {
			if (bind.customKeyMatches(key, currentModifier, hudActive)) {
				return true;
			}
		}
		return false;
	}

	/** 1.16 OnKeyPress.SELECT: the key selects its ability's hotbar slot and opens this power's HUD. */
	protected boolean selectByCustomKey(ClientKey key, KeyModifier currentModifier, boolean hudActive) {
		if (!hotbarsEnabled) return false;
		MoveGroup controls = getCurGroup();
		for (Bind bind : controls.binds) {
			if (bind.onKeyPress == OnKeyPress.SELECT && bind.customKeyMatches(key, currentModifier, hudActive)) {
				for (Hotbar hotbar : controls.hotbars) {
					for (int i = 0; i < hotbar.slots.size(); i++) {
						if (hotbar.slots.get(i).hasAbility(bind.ability.abilityName())) {
							hotbar.slotIndex = i;
							if (!hudActive) {
								InputHandler.getInstance().curPowerClassToggle = powerClassCosmetic;
							}
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public List<AbilityControlsEntry> getBindsWithModifier(InputMethod keyInputMethod, ClientKey key, KeyModifier currentModifier) {
		ClientControlScheme.MoveGroup controls = getCurGroup();
		boolean hudActive = isHudActive();
		if (keyInputMethod == InputMethod.CLICK && selectByCustomKey(key, currentModifier, hudActive)) {
			return Collections.emptyList();
		}
		if (!hudActive && hasCustomBindFor(key, currentModifier, false)) {
			// reached through a custom key of a closed HUD: that key's binds only, not this power's hotbars
			InputsByKeyModifier customBinds = controls.getBinds(false).get(key);
			return customBinds != null ? customBinds.getAll(currentModifier, keyInputMethod) : Collections.emptyList();
		}

		for (Hotbar hotbar : controls.hotbars) {
			if (hotbarUseKeyMatches(hotbar, key, currentModifier)) {
				HotbarSlot slot = hotbar.getSelected();
				if (slot != null) {
					return slot.getBinds().getAll(currentModifier, keyInputMethod);
				}
				return Collections.emptyList();
			}
		}
		
		InputsByKeyModifier allBindsInKey = controls.getBinds(hudActive).get(key);
		if (allBindsInKey != null) {
			return allBindsInKey.getAll(currentModifier, keyInputMethod);
		}
		
		return Collections.emptyList();
	}

	private boolean hotbarUseKeyMatches(Hotbar hotbar, ClientKey key, KeyModifier currentModifier) {
		ClientInputBind hotbarKey = hotbar.useAbilityKey;
		return hotbarKey != null && hotbarKey.keyMatches(key, currentModifier);
	}
	
	public static void setPrioritizedAbility(BaseAndActiveAbility dest, 
			List<AbilityControlsEntry> abilityNames, @Nullable Predicate<AbilityInputState> filter) {
		dest.reset();
		// FIXME shit code
		Stream<Pair<Ability, AbilityConditionCheck>> stream = abilityNames.stream()
				.map(abilityName -> {
					Power<?> power = ClientPowerCache.getPower(abilityName.powerClass);
					if (power == null) {
						return null;
					}
					AvailableAbilities allAbilities = ClientPowerCache.getAvailableAbilities(abilityName.powerClass, power);
					Ability baseAbility = power.getMoveset().getAbility(abilityName.abilityName);
					AbilityConditionCheck resolvedAbility = abilityName.getAbility();
					return baseAbility != null && resolvedAbility != null ? Pair.of(baseAbility, resolvedAbility) : null;
				})
				.filter(Objects::nonNull);
		if (filter != null) {
			stream = stream.filter(a -> filter.test(AbilityInputState.withValue(a.getSecond().clientInputState)));
		}
		
		Pair<Ability, AbilityConditionCheck> ability = stream
				.sorted(Comparator.comparingInt(a -> abilityPriority(a.getSecond(), ClientPowerCache.getPower(a.getFirst().abilityId.powerClass()))))
				.findFirst().orElse(null);
		if (ability != null) {
			dest.set(ability.getFirst(), ability.getSecond());
		}
	}

	static BaseAndActiveAbility target = new BaseAndActiveAbility();
	@Nullable
	public static AbilityConditionCheck prioritizedAbility(
			List<AbilityControlsEntry> abilityNames, @Nullable Predicate<AbilityInputState> filter) {
		setPrioritizedAbility(target, abilityNames, filter);
		return target.curActiveAbility;
	}
	
	protected static int abilityPriority(AbilityConditionCheck ability, Power<?> abilityCtx) {
		if (!ability.conditionCheck.isPositive()) {
			return 3;
		}
		if (ability.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB) {
			return 0;
		}
		return AbilityInputState.withValue(ability.clientInputState).getFlag(AbilityInputState.HIGH_PRIORITY) ? 1 : 2;
	}
	
	
	public static ClientControlScheme create(ControlSchemeTemplate template, PowerType powerType) {
		ClientControlScheme controls = new ClientControlScheme();
		PowerClass<?> powerClass = powerType.getPowerClass();
		controls.powerClassCosmetic = powerClass;
		controls.powerType = powerType;

		for (ControlSchemeTemplate.GroupTemplate groupTemplate : template.groups.values()) {
			if (groupTemplate.isEmpty()) continue;
			
			InputBindTemplate toggleHudKey = groupTemplate.toggleHudKey;
			if (toggleHudKey == null) {
				if (powerClass == PowerClass.STAND) {
					toggleHudKey = new InputUseVanillaMapping(InputHandler.getInstance().vanillaKeybinds.standArmsOnlyHUD);
				}
				else {
					toggleHudKey = new InputUseVanillaMapping(InputHandler.getInstance().vanillaKeybinds.playerPowerHUD);
				}
			}
			ClientInputBind toggleHudKeybind = ClientInputBind.toClientInput(toggleHudKey);
			
			ClientControlScheme.MoveGroup group = new ClientControlScheme.MoveGroup(groupTemplate.name, 
					Component.translatable(groupTemplate.name), toggleHudKeybind);
			controls.moveGroups.put(groupTemplate.name, group);
			
			// separate binds
			for (Map.Entry<String, Pair<InputMethod, InputBindTemplate>> bind : groupTemplate.separateBinds.entrySet()) {
				var input = bind.getValue();
				InputBindTemplate inputBindTemplate = input.getSecond();
				ClientInputBind inputBind = ClientInputBind.toClientInput(inputBindTemplate);
				if (inputBind != null) {
					InputMethod inputMethod = input.getFirst();
					String abilityName = bind.getKey();
					AbilityControlsEntry ability = new AbilityControlsEntry(powerClass, abilityName);
					group.binds.add(new Bind(inputBind, inputMethod, ability));
				}
			}
			for (SeparateBindTemplate bind :
					groupTemplate.additionalSeparateBinds) {
				ClientInputBind inputBind =
						ClientInputBind.toClientInput(bind.input());
				if (inputBind != null) {
					AbilityControlsEntry ability = new AbilityControlsEntry(
							powerClass, bind.ability());
					group.binds.add(new Bind(
							inputBind, bind.inputMethod(), ability));
				}
			}
			
			// ability hotbars
			for (AbilitiesHotbar hotbarTemplate : groupTemplate.hotbars) {
				int i = 0;
				Hotbar clientHotbar = new Hotbar(
						ClientInputBind.toClientInput(hotbarTemplate.useAbilityKey), 
						ClientInputBind.toClientInput(hotbarTemplate.switchAbilityKey));
				for (Map<InputKey.Modifier, Map<InputMethod, String>> slotTemplate : hotbarTemplate.slots) {
					HotbarSlot slot = new HotbarSlot(i++);
					for (var slotVariation : slotTemplate.entrySet()) {
						InputKey.Modifier modifier = slotVariation.getKey();
						for (var abilityEntry : slotVariation.getValue().entrySet()) {
							InputMethod inputMethod = abilityEntry.getKey();
							String ability = abilityEntry.getValue();
							Map<InputMethod, List<AbilityControlsEntry>> byModifier = slot.binds.movesByModifier.computeIfAbsent(ClientInputBind.toClientModifier(modifier), 
									__ -> new EnumMap<>(InputMethod.class));
							byModifier.put(inputMethod, 
									Collections.singletonList(new AbilityControlsEntry(powerClass, ability)));
						}
					}
					clientHotbar.slots.add(slot);
					clientHotbar.layout.add(slot);
				}
				group.hotbars.add(clientHotbar);
			}
		}
		
		return controls;
	}
	
}
