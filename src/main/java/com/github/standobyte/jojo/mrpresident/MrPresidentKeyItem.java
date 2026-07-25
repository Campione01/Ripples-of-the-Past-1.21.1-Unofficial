package com.github.standobyte.jojo.mrpresident;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

public class MrPresidentKeyItem extends Item {
	private static final String TURTLE_UUID_TAG = "TurtleEntity";
	private final boolean masterKey;

	public MrPresidentKeyItem(Properties properties, boolean masterKey) {
		super(properties.stacksTo(1));
		this.masterKey = masterKey;
	}

	public boolean isMasterKey() {
		return masterKey;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
		if (interactionTarget instanceof CocoJumboTurtleEntity turtle) {
			return turtle.interactWithKeyItem(player, usedHand, stack, this);
		}
		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (masterKey) {
			tooltip.add(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.GRAY));
		}
	}

	public boolean hasAssignedTurtle(ItemStack stack) {
		return getAssignedTurtleUuid(stack) != null;
	}

	public UUID getAssignedTurtleUuid(ItemStack stack) {
		var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		var tag = data.copyTag();
		return tag.hasUUID(TURTLE_UUID_TAG) ? tag.getUUID(TURTLE_UUID_TAG) : null;
	}

	public void assignToTurtle(ItemStack stack, UUID turtleUuid, Component turtleName) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TURTLE_UUID_TAG, turtleUuid));
		stack.set(DataComponents.ITEM_NAME, Component.translatable(stack.getDescriptionId() + ".named", turtleName));
	}
}
