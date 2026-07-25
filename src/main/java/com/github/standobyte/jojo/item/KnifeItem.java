package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.github.standobyte.jojo.mechanics.BleedingEffect;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public class KnifeItem extends Item implements ProjectileItem {
	public static final int MAX_KNIVES_THROW = 8;
	private static final Tool SINGLE_KNIFE_TOOL = SwordItem.createToolProperties();
	private static final ItemAttributeModifiers SINGLE_KNIFE_ATTRIBUTES = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();

	public KnifeItem(Properties properties) {
		super(properties);
		DispenserBlock.registerProjectileBehavior(this);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack handStack = player.getItemInHand(hand);
		int knivesToThrow = !player.isShiftKeyDown() ? Math.min(handStack.getCount(), MAX_KNIVES_THROW) : 1;
		if (!level.isClientSide()) {
			ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
			if (handStack.getCount() == 1 && headStack.getItem() instanceof StoneMaskItem && BleedingEffect.applyStoneMask(player, headStack)) {
				player.hurt(player.damageSources().playerAttack(player), 1.0F);
				return InteractionResultHolder.consume(handStack);
			}

			for (int i = 0; i < knivesToThrow; i++) {
				ItemStack projectileStack = handStack.copy();
				projectileStack.setCount(1);
				KnifeEntity knife = new KnifeEntity(level, player, projectileStack);
				knife.setTimeStopFlightTicks(5);
				knife.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, i == 0 ? 1.0F : 16.0F);
				ItemTracker tracker = ItemTracking.getItemTracker(projectileStack, level);
				if (tracker != null) {
					tracker.setAtEntity(projectileStack, knife.getId(), level, KnownItemState.ENTITY_IS_ITEM, null);
				}
				level.addFreshEntity(knife);
			}

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					knivesToThrow == 1 ? ModSoundEvents.KNIFE_THROW.get() : ModSoundEvents.KNIVES_THROW.get(),
					SoundSource.PLAYERS, 0 * 0.5F, 0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));

			player.getCooldowns().addCooldown(this, knivesToThrow * 3);
			if (!player.getAbilities().instabuild) {
				handStack.shrink(knivesToThrow);
			}

			StandEntity stand = StandPower.getOptional(player)
					.map(StandPower::getSummonedStandEntity)
					.orElse(null);
			if (stand != null) {
				stand.onKnivesThrow(level, player, handStack, knivesToThrow);
			}
		}
		return InteractionResultHolder.sidedSuccess(handStack, level.isClientSide());
	}

	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		ItemStack projectileStack = stack.copy();
		projectileStack.setCount(1);
		return new KnifeEntity(level, pos.x(), pos.y(), pos.z(), projectileStack);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return stack.getCount() == 1 ? SINGLE_KNIFE_TOOL.getMiningSpeed(state) : super.getDestroySpeed(stack, state);
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		return stack.getCount() == 1 && SINGLE_KNIFE_TOOL.isCorrectForDrops(state);
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
		return stack.getCount() == 1 ? SINGLE_KNIFE_ATTRIBUTES : super.getDefaultAttributeModifiers(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos blockPos = context.getClickedPos();
		BlockState state = level.getBlockState(blockPos);
		BlockState modifiedState = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
		if (modifiedState == null) {
			return InteractionResult.PASS;
		}

		Player player = context.getPlayer();
		level.playSound(player, blockPos, net.minecraft.sounds.SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (!level.isClientSide()) {
			context.getLevel().setBlock(blockPos, modifiedState, 11);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
		return itemAbility == ItemAbilities.AXE_STRIP || super.canPerformAction(stack, itemAbility);
	}
}
