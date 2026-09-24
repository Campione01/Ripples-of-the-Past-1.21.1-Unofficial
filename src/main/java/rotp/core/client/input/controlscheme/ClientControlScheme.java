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
		public Map<ClientKey, InputsByKeyModifier> getBinds() {
			bindsMap.clear();
			for (Bind bind : binds) {
				if (bind.input != null) {
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

			return bindsMap;
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
		
		public Bind(ClientInputBind input, InputMethod inputMethod, AbilityControlsEntry ability) {
			this.input = input;
			this.inputMethod = inputMethod;
			this.ability = ability;
		}
	}

	public static class Hotbar {
		public ClientInputBind useAbilityKey;
		@Nullable public ClientInputBind switchAbilityKey;
		public List<HotbarSlot> slots = new ArrayList<>();
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
	}
	
	public static class HotbarSlot {
		public int index;
		public final InputsByKeyModifier binds = new InputsByKeyModifier(true);
		
		public HotbarSlot(int index) {
			this.index = index;
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
	
	public List<AbilityControlsEntry> getBindsWithModifier(InputMethod keyInputMethod, ClientKey key, KeyModifier currentModifier) {
		ClientControlScheme.MoveGroup controls = getCurGroup();
		
		for (Hotbar hotbar : controls.hotbars) {
			if (hotbarUseKeyMatches(hotbar, key, currentModifier)) {
				HotbarSlot slot = hotbar.getSelected();
				if (slot != null) {
					return slot.getBinds().getAll(currentModifier, keyInputMethod);
				}
				return Collections.emptyList();
			}
		}
		
		InputsByKeyModifier allBindsInKey = controls.getBinds().get(key);
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
				}
				group.hotbars.add(clientHotbar);
			}
		}
		
		return controls;
	}
	
}
