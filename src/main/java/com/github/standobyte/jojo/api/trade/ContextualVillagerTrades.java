package com.github.standobyte.jojo.api.trade;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Owner-keyed, server-authoritative contextual villager trade providers.
 */
public final class ContextualVillagerTrades {
	public static final ResourceLocation EXPERT_STRUCTURE_MAP_GROUP =
			JojoMod.resLoc("expert_structure_map");

	private static final String PERSISTENT_ROOT =
			JojoMod.MOD_ID + ":ContextualVillagerTrades";
	private static final String GAVE_UNIQUE_TRADE =
			"GaveUniqueTrade";
	private static final String TRIED_PLAYERS = "TriedPlayers";
	private static final String RESULT_OWNER =
			JojoMod.MOD_ID + ":ContextualTradeOwner";
	private static final String RESULT_GROUP =
			JojoMod.MOD_ID + ":ContextualTradeGroup";

	private static final Map<ResourceLocation, Binding> BY_OWNER =
			new LinkedHashMap<>();
	private static final Map<ResourceLocation, List<Binding>> BY_GROUP =
			new LinkedHashMap<>();

	private ContextualVillagerTrades() {}

	/**
	 * Replaying the same owner, group, and stateless provider is a no-op.
	 * Reusing an owner with a different group or callback is rejected.
	 */
	public static synchronized void register(
			ResourceLocation owner,
			ResourceLocation group,
			ContextualVillagerTradeProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(group, "group");
		Objects.requireNonNull(provider, "provider");
		Binding existing = BY_OWNER.get(owner);
		if (existing != null) {
			if (existing.group().equals(group)
					&& sameCallbackShape(
							existing.provider(), provider)) {
				return;
			}
			throw new IllegalStateException(
					"Conflicting contextual villager trade owner: "
							+ owner);
		}
		Binding binding = new Binding(owner, group, provider);
		BY_OWNER.put(owner, binding);
		BY_GROUP.computeIfAbsent(
				group, ignored -> new ArrayList<>())
				.add(binding);
	}

	public static synchronized List<ResourceLocation>
			registeredOwners() {
		return List.copyOf(BY_OWNER.keySet());
	}

	/**
	 * Attempts every registered group once for this player and villager.
	 * A failed eligible attempt is deliberately retained.
	 */
	public static void attemptFirstOffers(
			ServerPlayer player,
			Villager villager) {
		if (player == null
				|| villager == null
				|| player.level().isClientSide()) {
			return;
		}
		Map<ResourceLocation, List<Binding>> groups =
				snapshotGroups();
		if (groups.isEmpty()) {
			return;
		}

		CompoundTag persistent = villager.getPersistentData();
		CompoundTag root = persistent.getCompound(PERSISTENT_ROOT);
		for (Map.Entry<ResourceLocation, List<Binding>> entry
				: groups.entrySet()) {
			ResourceLocation group = entry.getKey();
			List<Binding> eligible = eligibleBindings(
					entry.getValue(), group, player, villager);
			if (eligible.isEmpty()
					|| !markAttemptIfEligible(
							root, group, player.getUUID())) {
				continue;
			}

			shuffle(eligible, villager);
			for (Binding binding : eligible) {
				ContextualVillagerTradeContext context =
						new ContextualVillagerTradeContext(
								binding.owner(),
								group,
								player,
								villager);
				MerchantOffer offer;
				try {
					offer = binding.provider()
							.createOffer(context);
				}
				catch (RuntimeException error) {
					JojoMod.getLogger().error(
							"Contextual villager trade provider {} failed for {}",
							binding.owner(),
							player.getScoreboardName(),
							error);
					continue;
				}
				if (offer == null) {
					continue;
				}
				markResult(
						offer.getResult(),
						binding.owner(),
						group);
				villager.getOffers().add(offer);
				markSuccess(root, group);
				break;
			}
		}
		persistent.put(PERSISTENT_ROOT, root);
	}

	public static void onTrade(
			ServerPlayer player,
			Villager villager,
			MerchantOffer offer) {
		if (player == null
				|| villager == null
				|| offer == null
				|| player.level().isClientSide()
				|| offer.getUses() != 1) {
			return;
		}
		ResourceLocation owner =
				readResultId(offer.getResult(), RESULT_OWNER);
		ResourceLocation group =
				readResultId(offer.getResult(), RESULT_GROUP);
		if (owner == null || group == null) {
			return;
		}
		Binding binding;
		synchronized (ContextualVillagerTrades.class) {
			binding = BY_OWNER.get(owner);
		}
		if (binding == null || !binding.group().equals(group)) {
			return;
		}
		CompoundTag root = villager.getPersistentData()
				.getCompound(PERSISTENT_ROOT);
		if (!groupState(root, group)
				.getBoolean(GAVE_UNIQUE_TRADE)) {
			return;
		}
		ContextualVillagerTradeContext context =
				new ContextualVillagerTradeContext(
						owner, group, player, villager);
		try {
			binding.provider().onFirstPurchase(
					context, offer);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Contextual villager trade purchase callback {} failed for {}",
					owner,
					player.getScoreboardName(),
					error);
		}
	}

	public static boolean isContextualResult(
			ItemStack result) {
		return readResultId(result, RESULT_OWNER) != null;
	}

	static boolean markAttemptIfEligible(
			CompoundTag root,
			ResourceLocation group,
			UUID playerId) {
		CompoundTag state = groupState(root, group);
		CompoundTag tried =
				state.getCompound(TRIED_PLAYERS);
		String playerKey = playerId.toString();
		if (state.getBoolean(GAVE_UNIQUE_TRADE)
				|| tried.getBoolean(playerKey)) {
			return false;
		}
		tried.putBoolean(playerKey, true);
		state.put(TRIED_PLAYERS, tried);
		root.put(group.toString(), state);
		return true;
	}

	static void markSuccess(
			CompoundTag root,
			ResourceLocation group) {
		CompoundTag state = groupState(root, group);
		state.putBoolean(GAVE_UNIQUE_TRADE, true);
		root.put(group.toString(), state);
	}

	static boolean hasSucceeded(
			CompoundTag root,
			ResourceLocation group) {
		return groupState(root, group)
				.getBoolean(GAVE_UNIQUE_TRADE);
	}

	static synchronized void clearForTests() {
		BY_OWNER.clear();
		BY_GROUP.clear();
	}

	private static synchronized Map<ResourceLocation, List<Binding>>
			snapshotGroups() {
		Map<ResourceLocation, List<Binding>> copy =
				new LinkedHashMap<>();
		BY_GROUP.forEach((group, bindings) ->
				copy.put(group, List.copyOf(bindings)));
		return copy;
	}

	private static boolean sameCallbackShape(
			Object registered, Object candidate) {
		if (registered == candidate) {
			return true;
		}
		Class<?> callbackClass = registered.getClass();
		if (callbackClass != candidate.getClass()
				|| (!callbackClass.isHidden()
						&& !callbackClass.isSynthetic())) {
			return false;
		}
		for (Class<?> type = callbackClass;
				type != null;
				type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					return false;
				}
			}
		}
		return true;
	}

	private static List<Binding> eligibleBindings(
			List<Binding> bindings,
			ResourceLocation group,
			ServerPlayer player,
			Villager villager) {
		List<Binding> eligible = new ArrayList<>();
		for (Binding binding : bindings) {
			ContextualVillagerTradeContext context =
					new ContextualVillagerTradeContext(
							binding.owner(),
							group,
							player,
							villager);
			try {
				if (binding.provider().isEligible(context)) {
					eligible.add(binding);
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Contextual villager trade eligibility {} failed for {}",
						binding.owner(),
						player.getScoreboardName(),
						error);
			}
		}
		return eligible;
	}

	private static void shuffle(
			List<Binding> bindings,
			Villager villager) {
		for (int i = bindings.size() - 1; i > 0; --i) {
			int swap = villager.getRandom().nextInt(i + 1);
			Binding current = bindings.get(i);
			bindings.set(i, bindings.get(swap));
			bindings.set(swap, current);
		}
	}

	private static CompoundTag groupState(
			CompoundTag root,
			ResourceLocation group) {
		return root.getCompound(group.toString());
	}

	private static void markResult(
			ItemStack result,
			ResourceLocation owner,
			ResourceLocation group) {
		CustomData.update(
				DataComponents.CUSTOM_DATA,
				result,
				tag -> {
					tag.putString(
							RESULT_OWNER,
							owner.toString());
					tag.putString(
							RESULT_GROUP,
							group.toString());
				});
	}

	@Nullable
	private static ResourceLocation readResultId(
			ItemStack result,
			String key) {
		if (result == null || result.isEmpty()) {
			return null;
		}
		CustomData data = result.getOrDefault(
				DataComponents.CUSTOM_DATA,
				CustomData.EMPTY);
		String value = data.copyTag().getString(key);
		return ResourceLocation.tryParse(value);
	}

	private record Binding(
			ResourceLocation owner,
			ResourceLocation group,
			ContextualVillagerTradeProvider provider) {}
}
