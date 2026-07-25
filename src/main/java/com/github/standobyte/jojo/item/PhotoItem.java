package com.github.standobyte.jojo.item;

import java.util.List;

import com.github.standobyte.jojo.client.polaroid.PhotosCache;
import com.github.standobyte.jojo.client.polaroid.PhotosCache.PhotoHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PhotoItem extends Item {
	public static final int PHOTO_DEVELOPMENT_TICKS = 160;
	private static final String PHOTO_ID_TAG = "PhotoId";
	private static final String DEV_TICKS_TAG = "DevTicks";

	public PhotoItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int inventorySlot, boolean isSelected) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.contains(DEV_TICKS_TAG)) {
			int textureTicks = tag.getInt(DEV_TICKS_TAG);
			if (textureTicks > 0) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
					if (textureTicks == 1) {
						data.remove(DEV_TICKS_TAG);
					}
					else {
						data.putInt(DEV_TICKS_TAG, textureTicks - 1);
					}
				});
			}
		}
		if (level.isClientSide()) {
			long photoId = getPhotoId(stack);
			if (photoId > -1) {
				PhotosCache.getOrTryLoadPhoto(PhotosCache.currentServerId(), photoId);
			}
		}
	}

	public static long getPhotoId(ItemStack photoItem) {
		CompoundTag tag = photoItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(PHOTO_ID_TAG) ? tag.getLong(PHOTO_ID_TAG) : -1L;
	}

	public static void setPhotoId(ItemStack photoItem, long id) {
		CustomData.update(DataComponents.CUSTOM_DATA, photoItem, tag -> tag.putLong(PHOTO_ID_TAG, id));
	}

	public static void setPhotoAnimTicks(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(DEV_TICKS_TAG, PHOTO_DEVELOPMENT_TICKS));
	}

	public static float getPhotoAlpha(ItemStack stack, float partialTick) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.contains(DEV_TICKS_TAG)) {
			float timeLeft = tag.getInt(DEV_TICKS_TAG) - partialTick;
			return timeLeft > 120.0F ? 0.0F : 1.0F - timeLeft / 120.0F;
		}
		return 1.0F;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (flag.isAdvanced()) {
			long photoId = getPhotoId(stack);
			if (photoId > -1) {
				PhotoHolder.Status status = PhotosCache.getCacheStatus(PhotosCache.currentServerId(), photoId);
				tooltip.add(Component.literal("Id: " + photoId).withStyle(ChatFormatting.DARK_GRAY));
				tooltip.add(Component.literal("Status: " + String.valueOf(status)).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}
}
