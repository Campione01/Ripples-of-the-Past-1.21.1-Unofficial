package com.github.standobyte.jojo.api.playerpower;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.config.ConfigAbilityFactory;
import com.github.standobyte.jojo.powersystem.ability.controls.InputBindTemplate;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.controls.InputUseVanillaMapping;

import net.minecraft.resources.ResourceLocation;

/**
 * Ordered, declarative extensions to an existing PlayerPower moveset.
 * <p>
 * The facade exposes only validated operations and never gives addons direct
 * access to a core moveset or control scheme.
 */
public final class PlayerPowerMovesetExtensions {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Extension> BY_ID = new HashMap<>();
	private static final Map<ResourceLocation, List<Extension>> BY_TARGET = new HashMap<>();
	private static final Map<ResourceLocation, Long> TARGET_REVISIONS = new HashMap<>();
	private static final Comparator<Extension> APPLICATION_ORDER =
			Comparator.comparingInt(Extension::order)
					.thenComparing(extension -> extension.extensionId().toString());

	private PlayerPowerMovesetExtensions() {}

	public static Builder builder(ResourceLocation targetPlayerPowerId,
			ResourceLocation extensionId, int order) {
		return new Builder(targetPlayerPowerId, extensionId, order);
	}

	/**
	 * Registers an extension. Re-registering an equal definition is a no-op;
	 * reusing an extension ID for a different definition is rejected.
	 */
	public static void register(Extension extension) {
		Objects.requireNonNull(extension, "extension");
		synchronized (LOCK) {
			Extension existing = BY_ID.get(extension.extensionId());
			if (existing != null) {
				if (existing.equals(extension)) {
					return;
				}
				throw new IllegalStateException(
						"Conflicting PlayerPower moveset extension definition for "
								+ extension.extensionId());
			}

			BY_ID.put(extension.extensionId(), extension);
			List<Extension> targetExtensions = BY_TARGET.computeIfAbsent(
					extension.targetPlayerPowerId(), __ -> new ArrayList<>());
			targetExtensions.add(extension);
			targetExtensions.sort(APPLICATION_ORDER);
			TARGET_REVISIONS.merge(
					extension.targetPlayerPowerId(), 1L, Long::sum);
		}
	}

	@ApiStatus.Internal
	public static MovesetBuilder applyRegisteredExtensions(
			ResourceLocation targetPlayerPowerId, MovesetBuilder moveset) {
		Objects.requireNonNull(targetPlayerPowerId, "targetPlayerPowerId");
		Objects.requireNonNull(moveset, "moveset");

		List<Extension> extensions;
		synchronized (LOCK) {
			extensions = List.copyOf(
					BY_TARGET.getOrDefault(
							targetPlayerPowerId, List.of()));
		}

		for (Extension extension : extensions) {
			if (moveset.hasPlayerPowerMovesetExtension(
					extension.extensionId())) {
				continue;
			}
			try {
				MovesetBuilder staged = moveset.deepCopy();
				for (Operation operation : extension.operations) {
					operation.apply(staged);
				}
				moveset.commitPlayerPowerMovesetExtension(
						staged, extension.extensionId());
			}
			catch (RuntimeException error) {
				throw new IllegalStateException(
						"Failed to apply PlayerPower moveset extension "
								+ extension.extensionId() + " to "
								+ targetPlayerPowerId + ": "
								+ error.getMessage(),
						error);
			}
		}
		return moveset;
	}

	@ApiStatus.Internal
	public static long targetRevision(
			ResourceLocation targetPlayerPowerId) {
		if (targetPlayerPowerId == null) {
			return 0L;
		}
		synchronized (LOCK) {
			return TARGET_REVISIONS.getOrDefault(
					targetPlayerPowerId, 0L);
		}
	}

	public static final class Builder {
		private final ResourceLocation targetPlayerPowerId;
		private final ResourceLocation extensionId;
		private final int order;
		private final List<Operation> operations = new ArrayList<>();

		private Builder(ResourceLocation targetPlayerPowerId,
				ResourceLocation extensionId, int order) {
			this.targetPlayerPowerId = Objects.requireNonNull(
					targetPlayerPowerId, "targetPlayerPowerId");
			this.extensionId = Objects.requireNonNull(
					extensionId, "extensionId");
			this.order = order;
		}

		public Builder addAbility(String abilityName,
				ResourceLocation abilityTypeId,
				Supplier<? extends AbilityType<?>> abilityType) {
			operations.add(new AddAbility(
					requireName(abilityName, "abilityName"),
					Objects.requireNonNull(
							abilityTypeId, "abilityTypeId"),
					Objects.requireNonNull(
							abilityType, "abilityType")));
			return this;
		}

		/**
		 * Replaces an existing ability factory without changing its moveset name.
		 * Controls and configured references keep pointing at the canonical slot.
		 */
		public Builder replaceAbility(String abilityName,
				ResourceLocation expectedCurrentAbilityTypeId,
				ResourceLocation replacementAbilityTypeId,
				Supplier<? extends AbilityType<?>> replacementAbilityType) {
			operations.add(new ReplaceAbility(
					requireName(abilityName, "abilityName"),
					Objects.requireNonNull(
							expectedCurrentAbilityTypeId,
							"expectedCurrentAbilityTypeId"),
					Objects.requireNonNull(
							replacementAbilityTypeId,
							"replacementAbilityTypeId"),
					Objects.requireNonNull(
							replacementAbilityType,
							"replacementAbilityType")));
			return this;
		}

		public Builder insertAfterInHotbar(
				String controlSchemeName,
				int hotbarId,
				String anchorAbilityName,
				String abilityName,
				InputMethod inputMethod) {
			requireHotbarId(hotbarId);
			requireInputMethod(inputMethod);
			operations.add(new InsertAfterInHotbar(
					requireName(
							controlSchemeName,
							"controlSchemeName"),
					hotbarId,
					requireName(
							anchorAbilityName,
							"anchorAbilityName"),
					requireName(abilityName, "abilityName"),
					inputMethod));
			return this;
		}

		public Builder addHotbarSlotVariation(
				String controlSchemeName,
				int hotbarId,
				String baseAbilityName,
				String abilityName,
				InputKey.Modifier modifier,
				InputMethod inputMethod) {
			requireHotbarId(hotbarId);
			requireInputMethod(inputMethod);
			operations.add(new AddHotbarSlotVariation(
					requireName(
							controlSchemeName,
							"controlSchemeName"),
					hotbarId,
					requireName(
							baseAbilityName,
							"baseAbilityName"),
					requireName(abilityName, "abilityName"),
					Objects.requireNonNull(modifier, "modifier"),
					inputMethod));
			return this;
		}

		/**
		 * Adds a direct input binding to an existing moveset group.
		 * The operation never creates a control scheme, group, or hotbar.
		 */
		public Builder bindInExistingGroup(
				String controlSchemeName,
				String movesetGroupName,
				String abilityName,
				InputMethod inputMethod,
				InputBindTemplate input) {
			requireInputMethod(inputMethod);
			operations.add(new BindInExistingGroup(
					requireName(
							controlSchemeName,
							"controlSchemeName"),
					requireName(
							movesetGroupName,
							"movesetGroupName"),
					requireName(abilityName, "abilityName"),
					inputMethod,
					Objects.requireNonNull(input, "input")));
			return this;
		}

		public Extension build() {
			if (operations.isEmpty()) {
				throw new IllegalStateException(
						"A PlayerPower moveset extension must declare "
								+ "at least one operation");
			}
			return new Extension(
					targetPlayerPowerId,
					extensionId,
					order,
					operations);
		}
	}

	public static final class Extension {
		private final ResourceLocation targetPlayerPowerId;
		private final ResourceLocation extensionId;
		private final int order;
		private final List<Operation> operations;

		private Extension(
				ResourceLocation targetPlayerPowerId,
				ResourceLocation extensionId,
				int order,
				List<Operation> operations) {
			this.targetPlayerPowerId = targetPlayerPowerId;
			this.extensionId = extensionId;
			this.order = order;
			this.operations = List.copyOf(operations);
		}

		public ResourceLocation targetPlayerPowerId() {
			return targetPlayerPowerId;
		}

		public ResourceLocation extensionId() {
			return extensionId;
		}

		public int order() {
			return order;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof Extension other)) {
				return false;
			}
			return order == other.order
					&& targetPlayerPowerId.equals(
							other.targetPlayerPowerId)
					&& extensionId.equals(other.extensionId)
					&& operations.equals(other.operations);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
					targetPlayerPowerId,
					extensionId,
					order,
					operations);
		}
	}

	private interface Operation {
		void apply(MovesetBuilder moveset);
	}

	private static final class AddAbility implements Operation {
		private final String abilityName;
		private final ResourceLocation abilityTypeId;
		private final Supplier<? extends AbilityType<?>> abilityType;

		private AddAbility(
				String abilityName,
				ResourceLocation abilityTypeId,
				Supplier<? extends AbilityType<?>> abilityType) {
			this.abilityName = abilityName;
			this.abilityTypeId = abilityTypeId;
			this.abilityType = abilityType;
		}

		@Override
		public void apply(MovesetBuilder moveset) {
			if (moveset.abilities.containsKey(abilityName)) {
				throw new IllegalStateException(
						"ability already exists: " + abilityName);
			}
			AbilityType<?> resolved = Objects.requireNonNull(
					abilityType.get(),
					"ability type supplier returned null for "
							+ abilityTypeId);
			if (!abilityTypeId.equals(resolved.registryKey)) {
				throw new IllegalStateException(
						"ability type ID mismatch: expected "
								+ abilityTypeId + ", got "
								+ resolved.registryKey);
			}
			addAbility(moveset, abilityName, resolved);
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private static void addAbility(
				MovesetBuilder moveset,
				String abilityName,
				AbilityType<?> abilityType) {
			moveset.addAbility(
					abilityName,
					(AbilityType) abilityType,
					ability -> ((Ability) ability)
							.useAbilityTypeResourceNamespace());
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof AddAbility other)) {
				return false;
			}
			return abilityName.equals(other.abilityName)
					&& abilityTypeId.equals(other.abilityTypeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(abilityName, abilityTypeId);
		}
	}

	private static final class ReplaceAbility implements Operation {
		private final String abilityName;
		private final ResourceLocation expectedCurrentAbilityTypeId;
		private final ResourceLocation replacementAbilityTypeId;
		private final Supplier<? extends AbilityType<?>> replacementAbilityType;

		private ReplaceAbility(
				String abilityName,
				ResourceLocation expectedCurrentAbilityTypeId,
				ResourceLocation replacementAbilityTypeId,
				Supplier<? extends AbilityType<?>> replacementAbilityType) {
			this.abilityName = abilityName;
			this.expectedCurrentAbilityTypeId =
					expectedCurrentAbilityTypeId;
			this.replacementAbilityTypeId = replacementAbilityTypeId;
			this.replacementAbilityType = replacementAbilityType;
		}

		@Override
		public void apply(MovesetBuilder moveset) {
			ConfigAbilityFactory<?> current =
					moveset.abilities.get(abilityName);
			if (current == null) {
				throw new IllegalStateException(
						"ability does not exist: " + abilityName);
			}
			if (!expectedCurrentAbilityTypeId.equals(
					current.abilityTypeId())) {
				throw new IllegalStateException(
						"current ability type ID mismatch for "
								+ abilityName + ": expected "
								+ expectedCurrentAbilityTypeId
								+ ", got " + current.abilityTypeId());
			}

			AbilityType<?> replacement = Objects.requireNonNull(
					replacementAbilityType.get(),
					"replacement ability type supplier returned null for "
							+ replacementAbilityTypeId);
			if (!replacementAbilityTypeId.equals(
					replacement.registryKey)) {
				throw new IllegalStateException(
						"replacement ability type ID mismatch: expected "
								+ replacementAbilityTypeId + ", got "
								+ replacement.registryKey);
			}
			replaceAbility(moveset, abilityName, replacement);
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private static void replaceAbility(
				MovesetBuilder moveset,
				String abilityName,
				AbilityType<?> replacement) {
			moveset.abilities.put(
					abilityName,
					new ConfigAbilityFactory(
							(AbilityType) replacement, null));
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof ReplaceAbility other)) {
				return false;
			}
			return abilityName.equals(other.abilityName)
					&& expectedCurrentAbilityTypeId.equals(
							other.expectedCurrentAbilityTypeId)
					&& replacementAbilityTypeId.equals(
							other.replacementAbilityTypeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
					abilityName,
					expectedCurrentAbilityTypeId,
					replacementAbilityTypeId);
		}
	}

	private record InsertAfterInHotbar(
			String controlSchemeName,
			int hotbarId,
			String anchorAbilityName,
			String abilityName,
			InputMethod inputMethod) implements Operation {

		@Override
		public void apply(MovesetBuilder moveset) {
			moveset.insertPlayerPowerMovesetExtensionHotbar(
					controlSchemeName,
					hotbarId,
					anchorAbilityName,
					abilityName,
					inputMethod);
		}
	}

	private record AddHotbarSlotVariation(
			String controlSchemeName,
			int hotbarId,
			String baseAbilityName,
			String abilityName,
			InputKey.Modifier modifier,
			InputMethod inputMethod) implements Operation {

		@Override
		public void apply(MovesetBuilder moveset) {
			moveset.addPlayerPowerMovesetExtensionHotbarVariation(
					controlSchemeName,
					hotbarId,
					baseAbilityName,
					abilityName,
					modifier,
					inputMethod);
		}
	}

	private static final class BindInExistingGroup
			implements Operation {
		private final String controlSchemeName;
		private final String movesetGroupName;
		private final String abilityName;
		private final InputMethod inputMethod;
		private final InputBindTemplate input;
		private final String inputIdentity;

		private BindInExistingGroup(
				String controlSchemeName,
				String movesetGroupName,
				String abilityName,
				InputMethod inputMethod,
				InputBindTemplate input) {
			this.controlSchemeName = controlSchemeName;
			this.movesetGroupName = movesetGroupName;
			this.abilityName = abilityName;
			this.inputMethod = inputMethod;
			this.input = input;
			this.inputIdentity = inputIdentity(input);
		}

		@Override
		public void apply(MovesetBuilder moveset) {
			moveset.bindPlayerPowerMovesetExtensionGroup(
					controlSchemeName,
					movesetGroupName,
					abilityName,
					inputMethod,
					input);
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof BindInExistingGroup other)) {
				return false;
			}
			return controlSchemeName.equals(other.controlSchemeName)
					&& movesetGroupName.equals(other.movesetGroupName)
					&& abilityName.equals(other.abilityName)
					&& inputMethod == other.inputMethod
					&& inputIdentity.equals(other.inputIdentity);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
					controlSchemeName,
					movesetGroupName,
					abilityName,
					inputMethod,
					inputIdentity);
		}
	}

	private static String inputIdentity(InputBindTemplate input) {
		if (input instanceof InputKey key) {
			return "key:" + key.device + ":" + key.keyCode + ":"
					+ key.modifier;
		}
		if (input instanceof InputUseVanillaMapping mapping) {
			return "mapping:" + requireName(
					mapping.keyMappingName, "keyMappingName");
		}
		throw new IllegalArgumentException(
				"Unsupported input binding template: "
						+ input.getClass().getName());
	}

	private static String requireName(
			String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) {
			throw new IllegalArgumentException(
					field + " must not be blank");
		}
		return value;
	}

	private static void requireHotbarId(int hotbarId) {
		if (hotbarId < 0) {
			throw new IllegalArgumentException(
					"hotbarId must be non-negative");
		}
	}

	private static void requireInputMethod(
			InputMethod inputMethod) {
		Objects.requireNonNull(inputMethod, "inputMethod");
		if (inputMethod != InputMethod.CLICK
				&& inputMethod != InputMethod.HOLD) {
			throw new IllegalArgumentException(
					"PlayerPower moveset extensions only support "
							+ "CLICK or HOLD");
		}
	}
}
