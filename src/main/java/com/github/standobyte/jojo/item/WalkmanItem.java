package com.github.standobyte.jojo.item;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.item.cassette.WalkmanData;
import com.github.standobyte.jojo.item.cassette.WalkmanMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class WalkmanItem extends Item {
	public WalkmanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			editWalkmanData(stack, data -> data.idInitialized() ? data : data.withInitializedId(ServerSavedData.get(level.getServer()).incWalkmanId()));
			serverPlayer.openMenu(new Provider(stack, hand), buf -> WalkmanMenu.writeAdditionalData(buf, hand));
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		getWalkmanData(stack).filter(data -> !data.cassette().isEmpty()).ifPresent(data -> {
			tooltip.add(Component.translatable("item.jojo_ripples.walkman.cassette",
					data.cassette().getHoverName()).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.empty());
		});
		tooltip.add(Component.translatable("item.jojo_ripples.walkman.reference_quote").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static WalkmanData getOrDefault(ItemStack stack) {
		return Optional.ofNullable(stack.get(ModItemDataComponents.WALKMAN_DATA.get())).orElse(WalkmanData.EMPTY);
	}

	public static Optional<WalkmanData> getWalkmanData(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(getOrDefault(stack));
	}

	public static void editWalkmanData(ItemStack stack, UnaryOperator<WalkmanData> action) {
		if (!stack.isEmpty()) {
			stack.set(ModItemDataComponents.WALKMAN_DATA.get(), action.apply(getOrDefault(stack)));
		}
	}

	private record Provider(ItemStack walkmanItem, InteractionHand hand) implements MenuProvider {
		@Override
		public Component getDisplayName() {
			return Component.translatable("item.jojo_ripples.walkman");
		}

		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			return WalkmanMenu.server(containerId, inventory, hand, walkmanItem);
		}
	}
}
