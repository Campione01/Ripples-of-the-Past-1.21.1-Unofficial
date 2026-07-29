package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.block.StoneMaskBlock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.advancements.CriteriaTriggers;

public class StoneMaskItem extends ArmorItem {
	public static final String NBT_ACTIVATION_KEY = "Activated";
	private final StoneMaskBlock block;

	public enum MaskActivationResult {
		PASS,
		ACTIVATED,
		REJECTED
	}

	public StoneMaskItem(Properties properties, StoneMaskBlock block) {
		super(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, properties);
		this.block = block;
		BY_BLOCK.put(block, this);
	}

	public static void setActivatedArmorTexture(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putByte(NBT_ACTIVATION_KEY, (byte) 101));
	}

	public static int getActivatedTicks(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getByte(NBT_ACTIVATION_KEY);
	}

	/**
	 * Handles addon-specific blood activation before the core mask rules.
	 * Implementations returning {@link MaskActivationResult#ACTIVATED} own
	 * their gameplay mutation and should call
	 * {@code BleedingEffect.applyActivationEffects} when the normal mask
	 * sound, activation texture, and durability cost are required.
	 */
	public MaskActivationResult tryBloodActivation(
			LivingEntity wearer,
			ItemStack stack) {
		return MaskActivationResult.PASS;
	}

	public ResourceLocation getArmorTexture(ItemStack stack) {
		ResourceLocation itemId =
				BuiltInRegistries.ITEM.getKey(this);
		String suffix = getActivatedTicks(stack) > 0
				? "_activated"
				: "";
		return ResourceLocation.fromNamespaceAndPath(
				itemId.getNamespace(),
				"textures/armor/" + itemId.getPath()
						+ suffix + ".png");
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int inventorySlot, boolean isSelected) {
		super.inventoryTick(stack, level, entity, inventorySlot, isSelected);
		int textureTicks = getActivatedTicks(stack);
		if (textureTicks <= 0) {
			return;
		}

		byte nextTicks = (byte) (textureTicks - 1);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (nextTicks > 0) {
				tag.putByte(NBT_ACTIVATION_KEY, nextTicks);
			}
			else {
				tag.remove(NBT_ACTIVATION_KEY);
			}
		});

		if (nextTicks == 0 && entity instanceof LivingEntity living && living.getItemBySlot(EquipmentSlot.HEAD) == stack) {
			living.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			entity.spawnAtLocation(stack);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return place(new BlockPlaceContext(context));
	}

	public InteractionResult place(BlockPlaceContext context) {
		if (!context.canPlace()) {
			return InteractionResult.FAIL;
		}

		BlockState blockState = getBlock().getStateForPlacement(context);
		if (blockState == null || !blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
			return InteractionResult.FAIL;
		}

		Level level = context.getLevel();
		if (!level.setBlock(context.getClickedPos(), blockState, 11)) {
			return InteractionResult.FAIL;
		}

		BlockState placedState = level.getBlockState(context.getClickedPos());
		if (placedState.is(blockState.getBlock())) {
			placedState.getBlock().setPlacedBy(level, context.getClickedPos(), placedState, context.getPlayer(), context.getItemInHand());
			if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
				CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, context.getClickedPos(), context.getItemInHand());
			}
		}

		SoundType soundType = placedState.getSoundType(level, context.getClickedPos(), context.getPlayer());
		level.playSound(context.getPlayer(), context.getClickedPos(), soundType.getPlaceSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
		if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public String getDescriptionId() {
		return getBlock().getDescriptionId();
	}

	public Block getBlock() {
		return block;
	}
}
