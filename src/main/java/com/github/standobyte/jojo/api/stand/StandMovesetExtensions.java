package com.github.standobyte.jojo.api.stand;

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
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;

import net.minecraft.resources.ResourceLocation;

/**
 * Ordered, declarative additions to an existing Stand moveset.
 * <p>
 * Extensions never create control schemes or hotbars. Ability, skill, scheme,
 * and hotbar references are validated when a matching Stand moveset is built.
 */
public final class StandMovesetExtensions {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Extension> BY_ID = new HashMap<>();
	private static final Map<ResourceLocation, List<Extension>> BY_TARGET = new HashMap<>();
	private static final Map<ResourceLocation, Long> TARGET_REVISIONS = new HashMap<>();
	private static final Comparator<Extension> APPLICATION_ORDER =
			Comparator.comparingInt(Extension::order)
					.thenComparing(extension -> extension.extensionId().toString());

	private StandMovesetExtensions() {}

	public static Builder builder(ResourceLocation targetStandId,
			ResourceLocation extensionId, int order) {
		return new Builder(targetStandId, extensionId, order);
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
						"Conflicting Stand moveset extension definition for "
								+ extension.extensionId());
			}

			BY_ID.put(extension.extensionId(), extension);
			List<Extension> targetExtensions = BY_TARGET.computeIfAbsent(
					extension.targetStandId(), __ -> new ArrayList<>());
			targetExtensions.add(extension);
			targetExtensions.sort(APPLICATION_ORDER);
			TARGET_REVISIONS.merge(extension.targetStandId(), 1L, Long::sum);
		}
	}

	@ApiStatus.Internal
	public static MovesetBuilder applyRegisteredExtensions(
			ResourceLocation targetStandId, MovesetBuilder moveset) {
		Objects.requireNonNull(targetStandId, "targetStandId");
		Objects.requireNonNull(moveset, "moveset");

		List<Extension> extensions;
		synchronized (LOCK) {
			extensions = List.copyOf(
					BY_TARGET.getOrDefault(targetStandId, List.of()));
		}

		for (Extension extension : extensions) {
			if (moveset.hasStandMovesetExtension(extension.extensionId())) {
				continue;
			}
			try {
				MovesetBuilder staged = moveset.deepCopy();
				for (Operation operation : extension.operations) {
					operation.apply(staged);
				}
				moveset.commitStandMovesetExtension(
						staged, extension.extensionId());
			}
			catch (RuntimeException error) {
				throw new IllegalStateException(
						"Failed to apply Stand moveset extension "
								+ extension.extensionId() + " to "
								+ targetStandId + ": " + error.getMessage(),
						error);
			}
		}
		return moveset;
	}

	@ApiStatus.Internal
	public static long targetRevision(ResourceLocation targetStandId) {
		if (targetStandId == null) {
			return 0;
		}
		synchronized (LOCK) {
			return TARGET_REVISIONS.getOrDefault(targetStandId, 0L);
		}
	}

	public static final class Builder {
		private final ResourceLocation targetStandId;
		private final ResourceLocation extensionId;
		private final int order;
		private final List<Operation> operations = new ArrayList<>();

		private Builder(ResourceLocation targetStandId,
				ResourceLocation extensionId, int order) {
			this.targetStandId =
					Objects.requireNonNull(targetStandId, "targetStandId");
			this.extensionId =
					Objects.requireNonNull(extensionId, "extensionId");
			this.order = order;
		}

		public Builder addAbility(String abilityName,
				ResourceLocation abilityTypeId,
				Supplier<? extends AbilityType<?>> abilityType) {
			operations.add(new AddAbility(
					requireName(abilityName, "abilityName"),
					Objects.requireNonNull(abilityTypeId, "abilityTypeId"),
					Objects.requireNonNull(abilityType, "abilityType")));
			return this;
		}

		/**
		 * Replaces an existing ability factory without changing its moveset name.
		 * Skills, aliases, control schemes, and hotbar entries therefore keep
		 * referring to the same logical ability slot.
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

		public Builder addSkill(StandSkill skill) {
			operations.add(new AddSkill(
					Objects.requireNonNull(skill, "skill")));
			return this;
		}

		public Builder appendToHotbar(String controlSchemeName,
				int hotbarId, String abilityName, InputMethod inputMethod) {
			if (hotbarId < 0) {
				throw new IllegalArgumentException(
						"hotbarId must be non-negative");
			}
			if (inputMethod != InputMethod.CLICK
					&& inputMethod != InputMethod.HOLD) {
				throw new IllegalArgumentException(
						"Stand moveset extensions only support CLICK or HOLD");
			}
			operations.add(new AppendToHotbar(
					requireName(controlSchemeName, "controlSchemeName"),
					hotbarId,
					requireName(abilityName, "abilityName"),
					inputMethod));
			return this;
		}

		public Extension build() {
			if (operations.isEmpty()) {
				throw new IllegalStateException(
						"A Stand moveset extension must declare at least one operation");
			}
			return new Extension(
					targetStandId, extensionId, order, operations);
		}
	}

	public static final class Extension {
		private final ResourceLocation targetStandId;
		private final ResourceLocation extensionId;
		private final int order;
		private final List<Operation> operations;

		private Extension(ResourceLocation targetStandId,
				ResourceLocation extensionId, int order,
				List<Operation> operations) {
			this.targetStandId = targetStandId;
			this.extensionId = extensionId;
			this.order = order;
			this.operations = List.copyOf(operations);
		}

		public ResourceLocation targetStandId() {
			return targetStandId;
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
					&& targetStandId.equals(other.targetStandId)
					&& extensionId.equals(other.extensionId)
					&& operations.equals(other.operations);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
					targetStandId, extensionId, order, operations);
		}
	}

	/**
	 * Declarative Stand skill supported by extension ABI v1.
	 */
	public record StandSkill(
			String skillName,
			int expToUnlock,
			boolean starting,
			boolean hidden,
			List<String> unlocksAbilities,
			List<String> prerequisiteSkills) {

		public StandSkill {
			skillName = requireName(skillName, "skillName");
			if (expToUnlock < 0) {
				throw new IllegalArgumentException(
						"expToUnlock must be non-negative");
			}
			if (starting && expToUnlock != 0) {
				throw new IllegalArgumentException(
						"A starting skill must have zero unlock experience");
			}
			unlocksAbilities = copyNames(
					unlocksAbilities, "unlocksAbilities");
			prerequisiteSkills = copyNames(
					prerequisiteSkills, "prerequisiteSkills");
		}

		public static StandSkill startingAbility(String abilityName) {
			String name = requireName(abilityName, "abilityName");
			return new StandSkill(
					name, 0, true, false, List.of(name), List.of());
		}

		public static StandSkill unlockableAbility(
				String abilityName, int expToUnlock,
				List<String> prerequisiteSkills) {
			String name = requireName(abilityName, "abilityName");
			return new StandSkill(
					name, expToUnlock, false, false,
					List.of(name), prerequisiteSkills);
		}

		private StandUnlockableSkill create() {
			StandUnlockableSkill skill =
					new StandUnlockableSkill(skillName);
			skill.setExpToUnlock(expToUnlock);
			if (starting) {
				skill.setIsStartingSkill();
			}
			if (hidden) {
				skill.setHidden();
			}
			skill.unlocksAbilities.addAll(unlocksAbilities);
			skill.prerequisiteSkills.addAll(prerequisiteSkills);
			return skill;
		}
	}

	private interface Operation {
		void apply(MovesetBuilder moveset);
	}

	private static final class AddAbility implements Operation {
		private final String abilityName;
		private final ResourceLocation abilityTypeId;
		private final Supplier<? extends AbilityType<?>> abilityType;

		private AddAbility(String abilityName,
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
		private static void addAbility(MovesetBuilder moveset,
				String abilityName, AbilityType<?> abilityType) {
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

		private ReplaceAbility(String abilityName,
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
		private static void replaceAbility(MovesetBuilder moveset,
				String abilityName, AbilityType<?> replacement) {
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

	private record AddSkill(StandSkill skill) implements Operation {
		@Override
		public void apply(MovesetBuilder moveset) {
			if (moveset.unlockableSkills.containsKey(skill.skillName())) {
				throw new IllegalStateException(
						"skill already exists: " + skill.skillName());
			}
			for (String abilityName : skill.unlocksAbilities()) {
				if (!moveset.abilities.containsKey(abilityName)) {
					throw new IllegalStateException(
							"skill references missing ability: "
									+ abilityName);
				}
			}
			moveset.addSkill(skill.create());
		}
	}

	private record AppendToHotbar(
			String controlSchemeName,
			int hotbarId,
			String abilityName,
			InputMethod inputMethod) implements Operation {

		@Override
		public void apply(MovesetBuilder moveset) {
			moveset.appendStandMovesetExtensionHotbar(
					controlSchemeName, hotbarId,
					abilityName, inputMethod);
		}
	}

	private static String requireName(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value;
	}

	private static List<String> copyNames(
			List<String> values, String field) {
		Objects.requireNonNull(values, field);
		List<String> copy = new ArrayList<>(values.size());
		for (String value : values) {
			copy.add(requireName(value, field));
		}
		return List.copyOf(copy);
	}
}
