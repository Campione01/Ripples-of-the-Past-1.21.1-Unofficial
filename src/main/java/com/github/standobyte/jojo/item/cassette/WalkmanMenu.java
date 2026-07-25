package com.github.standobyte.jojo.item.cassette;

import com.github.standobyte.jojo.init.ModContainers;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.WalkmanItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class WalkmanMenu extends AbstractContainerMenu {
	private static final int WALKMAN_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;

	private final InteractionHand hand;
	private final ItemStack walkmanItem;
	private final CassetteSlot cassetteSlot;

	public static WalkmanMenu server(int containerId, Inventory inventory, InteractionHand hand, ItemStack walkmanItem) {
		return new WalkmanMenu(containerId, inventory, hand, walkmanItem);
	}

	public static WalkmanMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf dataFromServer) {
		InteractionHand hand = dataFromServer.readEnum(InteractionHand.class);
		return new WalkmanMenu(containerId, inventory, hand, inventory.player.getItemInHand(hand));
	}

	public static void writeAdditionalData(RegistryFriendlyByteBuf buffer, InteractionHand hand) {
		buffer.writeEnum(hand);
	}

	private WalkmanMenu(int containerId, Inventory inventory, InteractionHand hand, ItemStack walkmanItem) {
		super(ModContainers.WALKMAN.get(), containerId);
		this.hand = hand;
		this.walkmanItem = walkmanItem;
		this.cassetteSlot = new CassetteSlot(walkmanItem);
		addSlot(new SlotItemHandler(cassetteSlot, 0, 10, 111));

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 18 + col * 18, 142 + row * 18));
			}
		}
		for (int col = 0; col < 9; ++col) {
			addSlot(new Slot(inventory, col, 18 + col * 18, 200));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player.getItemInHand(hand) == walkmanItem && !walkmanItem.isEmpty() && walkmanItem.is(ModItems.WALKMAN.get());
	}

	public ItemStack getWalkmanItem() {
		return walkmanItem;
	}

	public ItemStack getCassetteItem() {
		return cassetteSlot.getStackInSlot(0);
	}

	public InteractionHand getHand() {
		return hand;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack clicked = slot.getItem();
			original = clicked.copy();
			if (index == WALKMAN_SLOT) {
				if (!moveItemStackTo(clicked, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickCraft(clicked, original);
			}
			else if (clicked.is(ModItems.CASSETTE_RECORDED.get())) {
				if (!moveItemStackTo(clicked, WALKMAN_SLOT, WALKMAN_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			}
			else if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
				if (!moveItemStackTo(clicked, HOTBAR_START, HOTBAR_END, false)) {
					return ItemStack.EMPTY;
				}
			}
			else if (index >= HOTBAR_START && index < HOTBAR_END) {
				if (!moveItemStackTo(clicked, PLAYER_INV_START, PLAYER_INV_END, false)) {
					return ItemStack.EMPTY;
				}
			}
			else if (!moveItemStackTo(clicked, PLAYER_INV_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}

			if (clicked.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			}
			else {
				slot.setChanged();
			}

			if (clicked.getCount() == original.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, clicked);
		}
		return original;
	}

	private static class CassetteSlot extends ItemStackHandler {
		private final ItemStack walkmanItem;

		private CassetteSlot(ItemStack walkmanItem) {
			super(1);
			this.walkmanItem = walkmanItem;
			setStackInSlot(0, WalkmanItem.getOrDefault(walkmanItem).cassette());
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.isEmpty() || stack.is(ModItems.CASSETTE_RECORDED.get());
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		protected void onContentsChanged(int slot) {
			if (!walkmanItem.isEmpty()) {
				ItemStack cassette = getStackInSlot(0);
				WalkmanItem.editWalkmanData(walkmanItem, data -> data.withCassette(cassette));
			}
		}
	}
}
