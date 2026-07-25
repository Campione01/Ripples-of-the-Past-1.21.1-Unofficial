package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.util.functions.ItemUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrazyDRepairItemAbility extends StandEntityAbility implements TrainableAbility {
	private static final String REPAIR_ITEM_ABILITY = "repair_item";
	private static final String PREVIOUS_STATE_SKILL = "revert_state";
	private static final ActionAnimIdentifier ITEM_FIX_ANIM = ActionAnimIdentifier.getOrCreate("repair_item", false);
	public static final float REPAIR_STAMINA_COST_TICK = 0.2F;
	private static final float PREVIOUS_STATE_MAX_TRAINING = 1.0F;

	public CrazyDRepairItemAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ItemRepair::new);
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}
	
	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ItemStack itemToRepair = itemToRepair(context.getUser());
		if (itemToRepair.isEmpty()) {
			return ConditionCheck.createNegative("item_offhand");
		}
		if (!canBeRepaired(itemToRepair)) {
			return ConditionCheck.createNegative("no_repair");
		}
		
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, REPAIR_STAMINA_COST_TICK) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return ITEM_FIX_ANIM;
	}

	@Override
	public String getLearningAbilityName() {
		return REPAIR_ITEM_ABILITY;
	}

	@Override
	public float getMaxTrainingPoints(StandPower power) {
		return PREVIOUS_STATE_MAX_TRAINING;
	}

	@Override
	public void onMaxTraining(StandPower power) {
		StandTypePersistentData data = power != null ? power.getCurTypeData() : null;
		if (data != null && !data.isSkillUnlocked(PREVIOUS_STATE_SKILL)) {
			data._setSkillUnlocked(PREVIOUS_STATE_SKILL, true, false);
		}
	}

	@Override
	public void appendWarnings(List<Component> warnings, @Nullable Power<?> context, Player clientPlayerUser) {
		ItemStack itemToRepair = itemToRepair(clientPlayerUser);
		if (!itemToRepair.isEmpty() && itemToRepair.isEnchanted()) {
			warnings.add(Component.translatable("jojo.crazy_diamond_fix.warning", itemToRepair.getHoverName())
					.withStyle(ChatFormatting.RED));
		}
	}

	public static class ItemRepair extends EntityActionInstance {
		private boolean clientRepairEffectsStarted;

		public ItemRepair(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			if (performer instanceof StandEntity stand) {
				setItemFixStandOffset(this, stand);
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();
			if (performer instanceof StandEntity stand) {
				setItemFixStandOffset(this, stand);
			}
			if (level.isClientSide()) {
				tickClientRepairEffects();
			}
			else {
				if (performer instanceof StandEntity stand) {
					LivingEntity user = powerUser.getEntityLiving(level);
					ItemStack itemToRepair = itemToRepair(user);
					if (!canBeRepaired(itemToRepair)) {
						setPhase(ActionPhase.RECOVERY, 0);
						syncPhaseChanges();
						return;
					}
					if (!consumeRepairStaminaTick(user)) {
						startRecovery();
						return;
					}
					ItemRepairResult result = repairTick(user, stand, itemToRepair, curPhaseTick);
					if (result.previousStateLearnPoints > 0) {
						StandPower userPower = stand.getUserPower();
						StandTypePersistentData data = userPower != null ? userPower.getCurTypeData() : null;
						if (data != null) {
							data.addAbilityLearningProgressPoints(REPAIR_ITEM_ABILITY, result.previousStateLearnPoints,
									PREVIOUS_STATE_MAX_TRAINING, userPower);
						}
					}
				}
			}
		}

		@Override
		public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {
			applyItemFixStandRotation(standEntity, user);
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (level().isClientSide()) {
				if (newPhase == ActionPhase.PERFORM) {
					startClientRepairEffects();
				}
				else {
					stopClientRepairEffects();
				}
			}
		}

		@Override
		public void onActionCleared(@Nullable EntityActionInstance newAction) {
			if (level().isClientSide()) {
				stopClientRepairEffects();
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}

		private void tickClientRepairEffects() {
			startClientRepairEffects();
			if (performer instanceof StandEntity standEntity) {
				LivingEntity user = standEntity.getUser();
				if (user != null) {
					CustomParticlesHelper.createCDRestorationParticle(user, InteractionHand.OFF_HAND);
				}
			}
		}

		private void startClientRepairEffects() {
			if (clientRepairEffectsStarted || !(performer instanceof StandEntity standEntity)
					|| !ClientGlobals.canHearStand(standEntity)) {
				return;
			}
			Level level = level();
			clientRepairEffectsStarted = true;
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, standEntity, level));
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_LOOP.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, true, standEntity, level.random.nextLong(),
					() -> this.isOver() || this.phase != ActionPhase.PERFORM));
		}

		private void stopClientRepairEffects() {
			if (!clientRepairEffectsStarted) {
				return;
			}
			clientRepairEffectsStarted = false;
			if (performer instanceof StandEntity standEntity && ClientGlobals.canHearStand(standEntity)) {
				ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
						ModSoundEvents.CRAZY_DIAMOND_FIX_ENDED.get(), standEntity),
						standEntity.getSoundSource(), 1, 1, standEntity, level()));
			}
		}

	}

	private static ItemStack itemToRepair(LivingEntity entity) {
		return entity != null ? entity.getOffhandItem() : ItemStack.EMPTY;
	}

	public static boolean consumeRepairStaminaTick(LivingEntity user) {
		StandPower standPower = user != null ? StandPower.get(user) : null;
		return standPower != null && standPower.consumeStamina(
				StandAbilityStamina.effectiveCost(standPower, REPAIR_STAMINA_COST_TICK), true);
	}

	public static boolean canBeRepaired(ItemStack itemStack) {
		if (itemStack == null || itemStack.isEmpty()) {
			return false;
		}

		if (itemStack.isDamaged() || itemStack.isEnchanted()
				|| itemStack.getItem() == Items.CHIPPED_ANVIL || itemStack.getItem() == Items.DAMAGED_ANVIL
				|| itemStack.getItem() == Items.COBBLESTONE) {
			return true;
		}

		ResourceLocation itemId = UtilFunctions.getItemId(itemStack);
		if (itemId.getPath().contains("cracked")) {
			ResourceLocation uncracked = itemId.withPath(path -> path.replace("cracked_", ""));
			if (BuiltInRegistries.ITEM.containsKey(uncracked)) {
				return true;
			}
		}

		return false;
	}

	public static void setItemFixStandOffset(EntityActionInstance action, StandEntity standEntity) {
		if (standEntity.isArmsOnlyMode()) {
			return;
		}
		LivingEntity user = standEntity.getUser();
		if (user == null) {
			return;
		}
		double left = user.getMainArm() == HumanoidArm.LEFT ? -0.667 : 0.667;
		action._setStandOffset(standEntity, new Vec3(left, 0.0, 0.2), StandOffsetFromUser.Rotations.BODY, false, false);
	}

	public static void applyItemFixStandRotation(StandEntity standEntity, LivingEntity user) {
		if (standEntity.isArmsOnlyMode()) {
			return;
		}
		float rotationOffset = user.getMainArm() == HumanoidArm.RIGHT ? 15.0F : -15.0F;
		float yRot = user.yBodyRot + rotationOffset;
		standEntity.setYRot(yRot);
		standEntity.setXRot(user.getXRot());
		standEntity.setYHeadRot(yRot);
		standEntity.setYBodyRot(yRot);
	}
	
	public static class ItemRepairResult {
		static ItemRepairResult instance = new ItemRepairResult();
		
		public boolean isRepairing;
		public float previousStateLearnPoints;
	}

	public static ItemRepairResult repairTick(LivingEntity user, StandEntity standEntity, ItemStack itemStack, int taskTicks) {
		return repairTick(user, standEntity, itemStack, taskTicks, true);
	}

	public static ItemRepairResult repairTick(LivingEntity user, StandEntity standEntity, ItemStack itemStack, int taskTicks, boolean transformInOffhand) {
		ItemRepairResult result = ItemRepairResult.instance;
		result.isRepairing = false;
		
		if (itemStack.isEmpty()) {
			result.previousStateLearnPoints = 0;
			return result;
		}
		
		int damage = 0;
		float multiplier = 1;
		ItemStack newStack = null;
		
		if (itemStack.getItem() == Items.CHIPPED_ANVIL) { 
			newStack = new ItemStack(Items.ANVIL);
			damage += 125;
		}
		else if (itemStack.getItem() == Items.DAMAGED_ANVIL) {
			newStack = new ItemStack(Items.CHIPPED_ANVIL);
			damage += 125;
		}
		else if (itemStack.getItem() == Items.COBBLESTONE) {
			newStack = new ItemStack(Items.STONE);
			damage += 1;
		}
		else {
			ResourceLocation itemId = UtilFunctions.getItemId(itemStack);
			if (itemId.getPath().contains("cracked")) {
				ResourceLocation uncracked = itemId.withPath(path -> path.replace("cracked_", ""));
				if (BuiltInRegistries.ITEM.containsKey(uncracked)) {
					damage += 1;
					newStack = new ItemStack(BuiltInRegistries.ITEM.get(uncracked));
				}
			}
		}
		
		switch (itemStack.getRarity()) {
		case UNCOMMON:
			multiplier += 0.5f;
			break;
		case RARE:
			multiplier += 1.5f;
			break;
		case EPIC:
			multiplier += 3;
			break;
		default:
			break;
		}
		
		if (itemStack.isDamageableItem()) {
			if (itemStack.getItem() instanceof TieredItem tieredItem) {
				multiplier += (float) getVanillaTierLevel(tieredItem) / 2;
			}
			int damageToRestore = Math.min(itemStack.getDamageValue(), (int) (CrazyDHealAbility.crazyDRestorationSpeed(standEntity) * 40));
			damage += damageToRestore;
			itemStack.setDamageValue(itemStack.getDamageValue() - damageToRestore);
			itemStack.set(DataComponents.REPAIR_COST, 0);
			if (damageToRestore > 0) {
				result.isRepairing = true;
			}
		}
		boolean transformTick = isItemTransformationTick(taskTicks, standEntity);
		
		int xp = getFullExperienceAmount(itemStack);
		if (xp > 0) {
			if (damage > 0) {
				damage += xp * 5;
			}
			dropExperience(user, xp);
		}
		DataComponentType<ItemEnchantments> enchComponentType = EnchantmentHelper.getComponentType(itemStack);
		if (itemStack.has(enchComponentType)) {
			itemStack.set(enchComponentType, ItemEnchantments.EMPTY);
		}

		if (newStack != null) {
			result.isRepairing = true;
			if (transformTick) {
				transformRepairedStack(user, itemStack, newStack, transformInOffhand);
			}
			else {
				damage = -1;
			}
		}

		result.previousStateLearnPoints = (float) damage * multiplier * PREVIOUS_STATE_LEARNING_RATE;
		return result;
	}

	private static void transformRepairedStack(LivingEntity user, ItemStack itemStack, ItemStack newStack, boolean transformInOffhand) {
		if (transformInOffhand && user instanceof Player player) {
			user.setItemInHand(InteractionHand.OFF_HAND, ItemUtils.createFilledResult(itemStack, player, newStack, false));
			if (player.getAbilities().instabuild) {
				itemStack.shrink(1);
			}
		}
		else {
			itemStack.shrink(1);
			ItemUtil.giveItemTo(user, newStack, true);
		}
	}
	private static final float PREVIOUS_STATE_LEARNING_RATE = 0.002f / 13f;

	private static int getVanillaTierLevel(TieredItem tieredItem) {
		var tier = tieredItem.getTier();
		if (tier == Tiers.STONE) {
			return 1;
		}
		if (tier == Tiers.IRON) {
			return 2;
		}
		if (tier == Tiers.DIAMOND) {
			return 3;
		}
		if (tier == Tiers.NETHERITE) {
			return 4;
		}
		return 0;
	}

	public static boolean isItemTransformationTick(int taskTicks, StandEntity standEntity) {
		int ticks = (int) (10 / CrazyDHealAbility.crazyDRestorationSpeed(standEntity));
		return taskTicks % ticks == ticks - 1;
	}
	
	public static int dropExperience(LivingEntity entity, int xp) {
		Level level = entity.level();
		if (!level.isClientSide() && xp > 0) {
			int i1 = (int) Math.ceil((double)xp / 2.0);
			xp = i1 + level.random.nextInt(i1);
			
			int xpToDrop = xp;
			Vec3 pos = entity.position().add(new Vec3(
					entity.getBbWidth() * 0.6 * (entity.getMainArm() == HumanoidArm.LEFT ? -1 : 1), 
					entity.getBbHeight() * (entity.isShiftKeyDown() ? 0.25 : 0.45), 
					entity.getBbWidth() * 0.7)
					.yRot(-entity.yBodyRot * MathUtil.DEG_TO_RAD));
			while (xpToDrop > 0) {
				int xpThisOrb = ExperienceOrb.getExperienceValue(xpToDrop);
				xpToDrop -= xpThisOrb;
				level.addFreshEntity(new ExperienceOrb(level, pos.x, pos.y, pos.z, xpThisOrb));
			}
			
			return xp;
		}
		return 0;
	}

	private static int getFullExperienceAmount(ItemStack item) {
		int xp = 0;

		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(item);
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			int enchLvl = entry.getIntValue();
			if (!enchantment.is(EnchantmentTags.CURSE)) {
				xp += enchantment.value().getMinCost(enchLvl);
			}
		}
		
		return xp;
	}

}
