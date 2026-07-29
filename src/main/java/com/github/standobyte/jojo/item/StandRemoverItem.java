package com.github.standobyte.jojo.item;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Result;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.TransitionContext;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mechanics.standdisc.StandDiscItem;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.util.functions.ItemUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class StandRemoverItem extends Item {
	private final Mode mode;
	private final boolean oneTimeUse;

	public StandRemoverItem(
			Properties properties,
			Mode mode,
			boolean oneTimeUse) {
		super(properties);
		this.mode = mode;
		this.oneTimeUse = oneTimeUse;
		DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior() {
			@Override
			protected ItemStack execute(
					BlockSource blockSource,
					ItemStack stack) {
				Direction direction = blockSource.state()
						.getValue(DispenserBlock.FACING);
				BlockPos targetPos = blockSource.pos().relative(direction);
				List<LivingEntity> targets = blockSource.level()
						.getEntitiesOfClass(
								LivingEntity.class,
								new AABB(targetPos),
								EntitySelector.NO_SPECTATORS);
				for (LivingEntity target : targets) {
					if (useOn(target, null)) {
						if (oneTimeUse) {
							stack.shrink(1);
						}
						return stack;
					}
				}
				return super.execute(blockSource, stack);
			}
		});
	}

	@Override
	public InteractionResultHolder<ItemStack> use(
			Level level,
			Player player,
			InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		StandPower power = StandPower.get(player);
		if (level.isClientSide()) {
			return power != null && power.hasPower()
					? InteractionResultHolder.success(stack)
					: InteractionResultHolder.fail(stack);
		}
		if (!useOn(player, player)) {
			return InteractionResultHolder.fail(stack);
		}
		if (oneTimeUse && !player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResultHolder.success(stack);
	}

	private boolean useOn(LivingEntity target, Entity actor) {
		StandPower power = StandPower.get(target);
		if (power == null || !power.hasPower()) {
			return false;
		}

		TransitionContext context =
				new TransitionContext(mode.sourceId(), actor);
		Result result;
		ItemStack ejectedDisc = ItemStack.EMPTY;
		switch (mode) {
		case REMOVE:
			result = StandPowerTransitions.clear(power, context);
			break;
		case EJECT:
			Optional<StandInstance> current = power.getStandInstance()
					.map(StandInstance::copy);
			if (current.isEmpty()) {
				return false;
			}
			StandInstance exactStand = current.get();
			ejectedDisc = StandDiscItem.withStand(exactStand);
			result = StandPowerTransitions.extract(
					power, exactStand.getStandId(), context);
			break;
		case FULL_CLEAR:
			result = StandPowerTransitions.fullReset(power, context);
			break;
		default:
			throw new IllegalStateException("Unhandled mode: " + mode);
		}

		if (!result.applied()) {
			return false;
		}
		if (!ejectedDisc.isEmpty()) {
			ItemUtil.giveItemTo(target, ejectedDisc, true);
		}
		return true;
	}

	@Override
	public void appendHoverText(
			ItemStack stack,
			Item.TooltipContext context,
			List<Component> tooltip,
			TooltipFlag flag) {
		if (mode == Mode.FULL_CLEAR) {
			tooltip.add(Component.translatable(
					"item.jojo_ripples.stand_full_clear.hint")
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable(
				"item.jojo_ripples.creative_only_tooltip")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	public enum Mode {
		REMOVE("stand_remover_one_time"),
		EJECT("stand_eject_one_time"),
		FULL_CLEAR("stand_full_clear_one_time");

		private final ResourceLocation sourceId;

		Mode(String sourcePath) {
			this.sourceId = JojoMod.resLoc(sourcePath);
		}

		public ResourceLocation sourceId() {
			return sourceId;
		}
	}
}
