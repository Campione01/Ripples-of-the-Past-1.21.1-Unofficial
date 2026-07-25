package com.github.standobyte.jojo.item;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.TommyGunLoopSound;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.TommyGunBulletEntity;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData.HamonStat;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TommyGunItem extends Item {
	public static final int MAX_AMMO = 50;
	private static final int USE_DURATION = 100;
	private static final int BULLETS_PER_GUNPOWDER = 8;
	private static final ItemAttributeModifiers TOMMY_GUN_ATTRIBUTES = ItemAttributeModifiers.builder()
			.add(Attributes.BLOCK_INTERACTION_RANGE,
					new AttributeModifier(JojoMod.resLoc("tommy_gun.block_reach"), 0.5D, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ENTITY_INTERACTION_RANGE,
					new AttributeModifier(JojoMod.resLoc("tommy_gun.entity_reach"), 0.5D, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED,
					new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.0D, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
			.build();

	public TommyGunItem(Properties properties) {
		super(properties);
	}

	public static ItemStack fullAmmoStack() {
		ItemStack stack = new ItemStack(ModItems.TOMMY_GUN.get());
		setAmmo(stack, MAX_AMMO);
		return stack;
	}

	public static int getAmmo(ItemStack gun) {
		Integer ammo = gun.get(ModItemDataComponents.TOMMY_GUN_AMMO.get());
		return ammo == null ? 0 : Mth.clamp(ammo, 0, MAX_AMMO);
	}

	public static void setAmmo(ItemStack gun, int ammo) {
		gun.set(ModItemDataComponents.TOMMY_GUN_AMMO.get(), Mth.clamp(ammo, 0, MAX_AMMO));
	}

	public static boolean consumeAmmo(ItemStack gun, int amount) {
		int ammo = getAmmo(gun);
		if (ammo <= 0) {
			setAmmo(gun, 0);
			return false;
		}
		setAmmo(gun, Math.max(ammo - amount, 0));
		return true;
	}

	public static int getGunshotTick(ItemStack stack) {
		Integer ticks = stack.get(ModItemDataComponents.TOMMY_GUN_GUNSHOT_TICKS.get());
		return ticks == null ? 0 : Math.max(ticks, 0);
	}

	private static void setGunshotTick(ItemStack stack, int ticks) {
		stack.set(ModItemDataComponents.TOMMY_GUN_GUNSHOT_TICKS.get(), Math.max(ticks, 0));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			return reload(stack, player, level)
					? InteractionResultHolder.consume(stack)
					: InteractionResultHolder.fail(stack);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		int ammo = getAmmo(stack);
		int tick = getUseDuration(stack, entity) - remainingTicks;
		boolean shotTick = tick % 2 == 0;
		if (remainingTicks <= 1) {
			entity.releaseUsingItem();
			return;
		}
		if (!level.isClientSide()) {
			if (remainingTicks == getUseDuration(stack, entity) - 14 && ammo == MAX_AMMO - 7 && josephVoiceLine(entity)) {
				JojoModUtil.sayVoiceLine(entity, ModSoundEvents.JOSEPH_SCREAM_SHOOTING);
			}
			if (ammo > 0) {
				if (shotTick) {
					TommyGunBulletEntity bullet = new TommyGunBulletEntity(entity, level);
					Vec3 pos = entity.getEyePosition(1.0F)
							.subtract(0.0D, bullet.getBbHeight() / 2.0D, 0.0D)
							.add(entity.getLookAngle());
					bullet.shootFromRotation(entity, 2.0F, 0.0F);
					bullet.setPos(pos.x, pos.y, pos.z);
					level.addFreshEntity(bullet);
					level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
							ModSoundEvents.TOMMY_GUN_SHOT.get(), entity.getSoundSource(), 1.0F, 1.0F);
					if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
						consumeAmmo(stack, 1);
					}
				}
			}
			else {
				entity.releaseUsingItem();
			}
		}
		if (ammo > 0) {
			if (shotTick) {
				applyRecoil(level, entity, remainingTicks);
				if (!level.isClientSide()) {
					setGunshotTick(stack, 3);
				}
			}
		}
		else if (shotTick) {
			entity.playSound(ModSoundEvents.TOMMY_GUN_NO_AMMO.get(), 1.0F, 1.0F);
		}
		if (level.isClientSide() && remainingTicks == getUseDuration(stack, entity)) {
			ClientsideSoundsHelper.playNonVanillaClassSound(new TommyGunLoopSound(
					ModSoundEvents.TOMMY_GUN_LOOP.get(), entity.getSoundSource(), 1.0F, entity, stack));
		}
	}

	private static void applyRecoil(Level level, LivingEntity entity, int remainingTicks) {
		boolean shouldApply = entity instanceof Player ? level.isClientSide() : !level.isClientSide();
		if (!shouldApply) {
			return;
		}
		RandomSource random = entity.getRandom();
		float recoil = 1.0F + Math.min((1.0F - (float) remainingTicks / (float) USE_DURATION) * 6.0F, 3.0F);
		entity.setYRot(entity.getYRot() + (random.nextFloat() - 0.5F) * 0.3F * recoil);
		entity.setXRot(Mth.clamp(entity.getXRot() - random.nextFloat() * 0.75F * recoil, -90.0F, 90.0F));
	}

	private boolean reload(ItemStack stack, LivingEntity entity, Level level) {
		int ammoToLoad = MAX_AMMO - getAmmo(stack);
		if (ammoToLoad <= 0) {
			return false;
		}
		if (entity instanceof Player player) {
			ammoToLoad = consumeReloadItems(player, ammoToLoad);
			if (!level.isClientSide()) {
				player.getCooldowns().addCooldown(this, ammoToLoad * 2);
			}
		}
		if (ammoToLoad <= 0) {
			return false;
		}
		if (!level.isClientSide()) {
			setAmmo(stack, getAmmo(stack) + ammoToLoad);
		}
		return true;
	}

	private int consumeReloadItems(Player player, int ammoToLoad) {
		if (player.getAbilities().instabuild) {
			return ammoToLoad;
		}
		List<ItemStack> ironNuggets = new ArrayList<>();
		List<ItemStack> gunpowder = new ArrayList<>();
		int ironNuggetsCount = 0;
		int gunpowderCount = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack inventoryStack = player.getInventory().getItem(i);
			if (inventoryStack.is(Items.IRON_NUGGET)) {
				ironNuggets.add(inventoryStack);
				ironNuggetsCount += inventoryStack.getCount();
			}
			else if (inventoryStack.is(Items.GUNPOWDER)) {
				gunpowder.add(inventoryStack);
				gunpowderCount += inventoryStack.getCount();
			}
		}

		ammoToLoad = Math.min(Math.min(ironNuggetsCount, gunpowderCount * BULLETS_PER_GUNPOWDER), ammoToLoad);
		int ironToConsume = ammoToLoad;
		int gunpowderToConsume = Mth.ceil((float) ammoToLoad / (float) BULLETS_PER_GUNPOWDER);
		for (ItemStack ironNuggetStack : ironNuggets) {
			int consumed = Math.min(ironNuggetStack.getCount(), ironToConsume);
			if (!player.level().isClientSide()) {
				ironNuggetStack.shrink(consumed);
			}
			ironToConsume -= consumed;
			if (ironToConsume == 0) {
				break;
			}
		}
		for (ItemStack gunpowderStack : gunpowder) {
			int consumed = Math.min(gunpowderStack.getCount(), gunpowderToConsume);
			if (!player.level().isClientSide()) {
				gunpowderStack.shrink(consumed);
			}
			gunpowderToConsume -= consumed;
			if (gunpowderToConsume == 0) {
				break;
			}
		}
		return ammoToLoad;
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
		if (remainingTicks <= 1 && josephVoiceLine(entity)) {
			JojoModUtil.sayVoiceLine(entity, ModSoundEvents.JOSEPH_WAR_DECLARATION);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
		if (!level.isClientSide()) {
			int ticks = getGunshotTick(stack);
			if (ticks > 0) {
				setGunshotTick(stack, ticks - 1);
			}
		}
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_DURATION;
	}

	@Override
	public boolean hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity user) {
		return PlayerPower.getPowerData(user, HamonPowerType.HAMON).map(hamon -> {
			if (!hamon.characterIs(ModHamonSkills.CHARACTER_JOSEPH.get())) {
				return false;
			}
			if (!user.level().isClientSide() && hamon.consumeEnergy(200.0F, user)) {
				HamonAbilityHelpers.hamonHurt(target, user, 0.15F);
				target.invulnerableTime = 0;
				hamon.hamonPointsFromAction(HamonStat.STRENGTH, 200.0F);
				hamon.syncOnUpdate(user);
				JojoModUtil.sayVoiceLine(user, ModSoundEvents.JOSEPH_SHOOT);
				return true;
			}
			return user.level().isClientSide();
		}).orElse(false);
	}

	private static boolean josephVoiceLine(LivingEntity entity) {
		return PlayerPower.getPowerData(entity, HamonPowerType.HAMON)
				.map(hamon -> hamon.characterIs(ModHamonSkills.CHARACTER_JOSEPH.get()))
				.orElse(false);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return getAmmo(stack) < MAX_AMMO;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * getAmmo(stack) / (float) MAX_AMMO);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xB0B0B0;
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
		return TOMMY_GUN_ATTRIBUTES;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.jojo_ripples.tommy_gun.reload_prompt",
				Component.keybind("key.sneak"), Component.keybind("key.use")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.jojo_ripples.tommy_gun.reference_quote").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
