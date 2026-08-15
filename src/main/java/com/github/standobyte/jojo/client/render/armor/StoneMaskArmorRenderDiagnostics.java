package com.github.standobyte.jojo.client.render.armor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.item.StoneMaskItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Read-only receipts from the production humanoid armor-model callback. */
public final class StoneMaskArmorRenderDiagnostics {
	private static final int MAX_TRACKED_ENTITIES = 256;
	private static final Map<UUID, Observation> OBSERVATIONS =
			new LinkedHashMap<>(32, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<UUID, Observation> eldest) {
					return size() > MAX_TRACKED_ENTITIES;
				}
			};
	private static long sequence;

	private StoneMaskArmorRenderDiagnostics() {}

	static synchronized boolean isTracking(@Nullable LivingEntity entity) {
		return entity != null && OBSERVATIONS.containsKey(entity.getUUID());
	}

	static synchronized void record(
			LivingEntity entity,
			ItemStack stack,
			EquipmentSlot slot,
			HumanoidModel<?> original,
			HumanoidModel<?> resolved) {
		if (entity == null || stack == null || slot == null
				|| original == null || resolved == null) {
			return;
		}
		Item item = stack.getItem();
		recordState(
				entity.getUUID(),
				item,
				hasActivatedTexture(stack),
				slot,
				original.getClass(),
				resolved.getClass(),
				resolved == original,
				item instanceof StoneMaskItem mask ? mask : null,
				stack);
	}

	static synchronized void recordState(
			UUID entityUuid,
			Item item,
			boolean activatedTexture,
			EquipmentSlot slot,
			Class<?> originalModelClass,
			Class<?> resolvedModelClass,
			boolean originalModelReturned,
			@Nullable StoneMaskItem mask,
			@Nullable ItemStack stack) {
		recordState(
				entityUuid,
				item,
				activatedTexture,
				slot,
				originalModelClass,
				resolvedModelClass,
				originalModelReturned,
				mask,
				stack,
				false,
				null,
				null);
	}

	static synchronized void recordStateForTests(
			UUID entityUuid,
			Object itemIdentity,
			boolean activatedTexture,
			EquipmentSlot slot,
			Class<?> originalModelClass,
			Class<?> resolvedModelClass,
			boolean originalModelReturned,
			String itemRegistryId,
			String textureRegistryId) {
		recordState(
				entityUuid,
				itemIdentity,
				activatedTexture,
				slot,
				originalModelClass,
				resolvedModelClass,
				originalModelReturned,
				null,
				null,
				true,
				itemRegistryId,
				textureRegistryId);
	}

	private static void recordState(
			UUID entityUuid,
			Object itemIdentity,
			boolean activatedTexture,
			EquipmentSlot slot,
			Class<?> originalModelClass,
			Class<?> resolvedModelClass,
			boolean originalModelReturned,
			@Nullable StoneMaskItem mask,
			@Nullable ItemStack stack,
			boolean identifiersProvided,
			@Nullable String itemRegistryId,
			@Nullable String textureRegistryId) {
		if (entityUuid == null || itemIdentity == null || slot == null
				|| originalModelClass == null || resolvedModelClass == null) {
			return;
		}
		Observation observation = OBSERVATIONS.computeIfAbsent(
				entityUuid,
				ignored -> new Observation());
		observation.entityInvocationCount++;
		boolean stackChanged = stack != null
				&& (observation.observedStack == null
						|| !ItemStack.matches(
								observation.observedStack, stack));
		boolean stateChanged = !observation.initialized
				|| observation.itemIdentity != itemIdentity
				|| observation.activatedTexture != activatedTexture
				|| observation.slot != slot
				|| observation.originalModelClass != originalModelClass
				|| observation.resolvedModelClass != resolvedModelClass
				|| observation.originalModelReturned
						!= originalModelReturned
				|| stackChanged;
		if (stack != null) {
			observation.observedStack = stack.copy();
		}
		if (!stateChanged && !observation.receiptRequested) {
			return;
		}

		if (!identifiersProvided) {
			Item item = (Item) itemIdentity;
			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
			ResourceLocation texture = mask != null && stack != null
					? mask.getArmorTexture(stack)
					: null;
			itemRegistryId = String.valueOf(itemId);
			textureRegistryId = texture != null ? texture.toString() : "none";
		}
		observation.itemIdentity = itemIdentity;
		observation.activatedTexture = activatedTexture;
		observation.slot = slot;
		observation.originalModelClass = originalModelClass;
		observation.resolvedModelClass = resolvedModelClass;
		observation.originalModelReturned = originalModelReturned;
		observation.initialized = true;
		observation.receiptRequested = false;
		observation.snapshot = new Snapshot(
				++sequence,
				observation.entityInvocationCount,
				entityUuid,
				itemRegistryId,
				slot.getName(),
				originalModelClass.getName(),
				resolvedModelClass.getName(),
				textureRegistryId,
				activatedTexture,
				originalModelReturned);
	}

	private static boolean hasActivatedTexture(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
				.getUnsafe()
				.getByte(StoneMaskItem.NBT_ACTIVATION_KEY) > 0;
	}

	public static synchronized long beginObservation(UUID entityUuid) {
		if (entityUuid != null) {
			OBSERVATIONS.computeIfAbsent(
					entityUuid,
					ignored -> new Observation()).receiptRequested = true;
		}
		return sequence;
	}

	public static synchronized long beginObservation(
			@Nullable LivingEntity entity) {
		return beginObservation(entity != null ? entity.getUUID() : null);
	}

	@Nullable
	public static synchronized Snapshot snapshot(UUID entityUuid) {
		Observation observation = OBSERVATIONS.get(entityUuid);
		return observation != null ? observation.snapshot : null;
	}

	@Nullable
	public static synchronized Snapshot snapshot(LivingEntity entity) {
		return entity != null ? snapshot(entity.getUUID()) : null;
	}

	public static synchronized long latestSequence() {
		return sequence;
	}

	public static synchronized long invocationCount(UUID entityUuid) {
		Observation observation = OBSERVATIONS.get(entityUuid);
		return observation != null
				? observation.entityInvocationCount
				: 0L;
	}

	public static synchronized boolean matchesRenderedStack(
			UUID entityUuid, ItemStack stack) {
		Observation observation = OBSERVATIONS.get(entityUuid);
		return observation != null
				&& observation.observedStack != null
				&& stack != null
				&& ItemStack.matches(observation.observedStack, stack);
	}

	private static final class Observation {
		private Object itemIdentity;
		private boolean activatedTexture;
		private EquipmentSlot slot;
		private Class<?> originalModelClass;
		private Class<?> resolvedModelClass;
		private boolean originalModelReturned;
		private ItemStack observedStack;
		private long entityInvocationCount;
		private boolean initialized;
		private boolean receiptRequested;
		private Snapshot snapshot;
	}

	public record Snapshot(
			long sequence,
			long entityInvocationCount,
			UUID entityUuid,
			String itemRegistryId,
			String equipmentSlot,
			String originalModelClass,
			String resolvedModelClass,
			String textureRegistryId,
			boolean activatedTexture,
			boolean originalModelReturned) {}
}
