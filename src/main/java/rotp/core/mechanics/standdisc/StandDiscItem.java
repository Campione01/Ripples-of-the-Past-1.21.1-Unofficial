package rotp.core.mechanics.standdisc;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerDuplicateCounter;
import rotp.core.subsystems.StoryPart;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class StandDiscItem extends Item {

	public StandDiscItem(Properties properties) {
		super(properties);
		// 1.16: a dispenser inserts the disc into the entity in front of it
		DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior() {
			@Override
			protected ItemStack execute(BlockSource blockSource, ItemStack stack) {
				Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
				BlockPos targetPos = blockSource.pos().relative(direction);
				List<LivingEntity> targets = blockSource.level().getEntitiesOfClass(
						LivingEntity.class, new AABB(targetPos), EntitySelector.NO_SPECTATORS);
				for (LivingEntity target : targets) {
					if (giveStandFromDisc(target, stack)) {
						stack.shrink(1);
						return stack;
					}
				}
				return super.execute(blockSource, stack);
			}
		});
	}

	// 1.16 giveStandFromDisc: only an entity that can hold a Stand and has none gets it
	public static boolean giveStandFromDisc(LivingEntity target, ItemStack discItem) {
		if (target.level().isClientSide()) return false;
		StandWrittenOnDisc discStand = discItem.get(ModItemDataComponents.DISC_STAND.get());
		if (discStand == null || !discStand.isValid()) return false;
		if (StandUtil.isStandBanned(discStand.getInstance().getStandType(), false)) return false;
		if (target instanceof Player) {
			PowerClass.STAND.attachPower(target);
		}
		StandPower stand = PowerClass.STAND.get(target);
		if (stand == null || stand.getStandInstance().isPresent()) return false;
		StandPowerTransitions.TransitionContext context =
				new StandPowerTransitions.TransitionContext(ModItems.STAND_DISC.getId(), null);
		if (!StandPowerTransitions.insert(stand, discStand.copyStandInstance(), context).applied()) {
			return false;
		}
		// the caller uses the disc up
		takePutOut(target, discItem);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack item, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flags) {
		StandWrittenOnDisc discStand = item.get(ModItemDataComponents.DISC_STAND.get());
		if (discStand == null || !discStand.isValid()) return;
		
		StandInstance standInstance = discStand.getInstance();
		StandType standType = standInstance.getStandType();
		Component standName = standInstance.getStandName(true);
		if (standName != null) {
			tooltip.add(standName);
		}

		StandSkin skin = StandSkinsLoader.getInstance().getSkin(standInstance);
		if (skin != null) {
			Holder<StoryPart> storyPart = skin.getStoryPart(ctx.registries());
			if (storyPart != null) {
				tooltip.add(storyPart.value().getPartName());
			}
		}

		for (StandPart standPart : StandPart.values()) {
			if (!standInstance.hasPart(standPart)) {
				tooltip.add(Component.translatable("jojo.disc.missing_part." + standPart.serializedName())
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		
		if (standType != null && !standType.discExtraTooltip.isEmpty()) {
			tooltip.add(CommonComponents.EMPTY);
			tooltip.addAll(standType.discExtraTooltip);
		}
	}
	
	@Nullable
	public String getCreatorModId(ItemStack itemStack) {
		ResourceLocation id;
		StandInstance stand = getStandInstance(itemStack);
		if (stand != null) {
			id = stand.getStandId();
			if (id != null) {
				return id.getNamespace();
			}
		}
		return super.getCreatorModId(itemStack);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack discItem = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			StandWrittenOnDisc discStand = discItem.get(ModItemDataComponents.DISC_STAND.get());
			if (discStand == null || !discStand.isValid()) return InteractionResultHolder.fail(discItem);
			// 1.16: a banned Stand's disc can't be used
			if (StandUtil.isStandBanned(discStand.getInstance().getStandType(), false)) {
				return InteractionResultHolder.fail(discItem);
			}

			PowerClass.STAND.attachPower(player);
			StandPower stand = PowerClass.STAND.get(player);
			if (stand != null) {
				StandInstance replacement = discStand.copyStandInstance();
				StandPowerTransitions.TransitionContext context =
						new StandPowerTransitions.TransitionContext(
								ModItems.STAND_DISC.getId(),
								player);
				StandPowerTransitions.Result transition = stand.getStandInstance()
						.map(current -> StandPowerTransitions.replace(
								stand,
								current.getStandId(),
								replacement,
								context))
						.orElseGet(() -> StandPowerTransitions.insert(
								stand, replacement, context));
				if (!transition.applied()) {
					return InteractionResultHolder.fail(discItem);
				}
				// Creative keeps the disc and makes none: the old Stand is uncounted, as 1.16 clear()
				if (!player.getAbilities().instabuild) {
					takePutOut(player, discItem);
					discItem.shrink(1);
					transition.previous().filter(StandInstance::standExists).ifPresent(prev -> {
						ItemStack prevDisc = withStand(prev);
						markPutOut(player, prevDisc);
						ItemEntity discItemEntity = player.drop(prevDisc, false);
						if (discItemEntity != null) {
							discItemEntity.setPickUpDelay(5);
							discItemEntity.setTarget(player.getUUID());
						}
					});
				}
			}
			else {
				return InteractionResultHolder.fail(discItem);
			}
			return InteractionResultHolder.success(discItem);
		}
		return InteractionResultHolder.consume(discItem);
	}


	@Nullable
	public static StandInstance getStandInstance(ItemStack discItem) {
		StandWrittenOnDisc discStand = discItem.get(ModItemDataComponents.DISC_STAND.get());
		if (discStand == null || !discStand.isValid()) return null;

		return discStand.copyStandInstance();
	}

	public static ItemStack withStand(StandInstance standInstance) {
		ItemStack disc = new ItemStack(ModItems.STAND_DISC.get());
		disc.set(ModItemDataComponents.DISC_STAND.get(), new StandWrittenOnDisc(standInstance));
		return disc;
	}

	// 1.16 WS_TAG: the disc holds a player's Stand that still counts as taken (standArrowMode)
	public static final String PUT_OUT_TAG = "WSPutOut";

	// 1.16 putOutStand = clear(false): a player's Stand moved onto this disc stays counted
	public static void markPutOut(LivingEntity previousUser, ItemStack disc) {
		MinecraftServer server = previousUser.getServer();
		StandInstance stand = getStandInstance(disc);
		if (server == null || !(previousUser instanceof ServerPlayer)
				|| stand == null || stand.getStandId() == null || isPutOut(disc)) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, disc, tag -> tag.putBoolean(PUT_OUT_TAG, true));
		ServerDuplicateCounter.StandHolders.get(server).putOnDisc(stand.getStandId());
	}

	public static boolean isPutOut(ItemStack disc) {
		return disc.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(PUT_OUT_TAG);
	}

	// 1.16 giveStandFromInstance(standExistedInWorld): call before the disc is used up;
	// a disc without the tag (Creative tab, loot, /give) counts as a new Stand
	public static void takePutOut(LivingEntity newUser, ItemStack disc) {
		if (!isPutOut(disc)) {
			return;
		}
		StandInstance stand = getStandInstance(disc);
		CustomData.update(DataComponents.CUSTOM_DATA, disc, tag -> tag.remove(PUT_OUT_TAG));
		MinecraftServer server = newUser.getServer();
		// a mob holder keeps it taken, as 1.16 did
		if (server != null && newUser instanceof ServerPlayer && stand != null && stand.getStandId() != null) {
			ServerDuplicateCounter.StandHolders.get(server).takeFromDisc(stand.getStandId());
		}
	}

}
