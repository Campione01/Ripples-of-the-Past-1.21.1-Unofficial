package com.github.standobyte.jojo.powersystem.ability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.github.standobyte.jojo.client.input.AbilityInputState;
import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.ui.hud_power.WindupIndicator;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.finisher.AbilityStandFinisherData;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopClientAwareness;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.StringUtil;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class Ability {
	public final AbilityType<?> abilityType;
	public final AbilityId abilityId;
	protected String spriteName;
	protected Component name;
	@Nullable private String resourceNamespaceOverride;
	
	public AbilityUsageGroup usageGroup = AbilityUsageGroup.SPECIAL;
	public boolean isSubAbility = false;
	private final EnumSet<StandPart> partsRequired = EnumSet.noneOf(StandPart.class);
	private int resolveLevelToUnlock = -1;
	protected int cooldownTicks;
	protected int cooldownTechnicalTicks;
	protected int cooldownAdditionalTicks;
	protected float cooldownResolveMultiplier = 1.0F;
	protected boolean cooldownResolveMultiplierApplies;
	
	@Nullable public AbilityStandFinisherData isStandFinisherOf = null;

	public Ability(AbilityType<?> abilityType, AbilityId abilityId) {
		this.abilityType = abilityType;
		this.abilityId = abilityId;
		this.spriteName = StringUtil.splitIntAtTheEnd(abilityId.nameInMoveset()).getFirst();
		this.name = Component.translatable(abilityTranslationKey(abilityType, abilityId, ""));
		String abilityName = abilityId.nameInMoveset();
		this.isSubAbility = !abilityName.isEmpty() && Character.isDigit(abilityName.charAt(abilityName.length() - 1));
		initVariationAssets();
	}
	
	protected static Component abilityName(AbilityId abilityId, String postfix) {
		return Component.translatable(abilityTranslationKey(null, abilityId, postfix));
	}

	protected Component abilityName(Power<?> context, String postfix, Object... args) {
		String translationKey = getTranslationKey(postfix);
		if (context instanceof StandPower standPower && StandSkinsLoader.getInstance() != null) {
			StandSkin skin = StandSkinsLoader.getInstance().getSkin(standPower);
			if (skin != null) {
				return skin.translatable(translationKey, args);
			}
		}
		return Component.translatable(translationKey, args);
	}

	private static String abilityTranslationKey(
			@Nullable AbilityType<?> abilityType, AbilityId abilityId, String postfix) {
		return resourceNamespace(abilityType, abilityId) + ".ability."
				+ abilityId.nameInMoveset() + postfix;
	}

	private static String resourceNamespace(
			@Nullable AbilityType<?> abilityType, AbilityId abilityId) {
		if (abilityId.powerTypeId() != null) {
			return abilityId.powerTypeId().getNamespace();
		}
		if (abilityType != null && abilityType.registryKey != null) {
			return abilityType.registryKey.getNamespace();
		}
		String abilityName = abilityId.nameInMoveset();
		int namespaceSeparator = abilityName.indexOf(':');
		return namespaceSeparator > 0
				? abilityName.substring(0, namespaceSeparator)
				: JojoMod.MOD_ID;
	}
	
	public AbilityId getAbilityId() {
		return abilityId;
	}
	
	public final String name() {
		return abilityId.nameInMoveset();
	}
	
	public Power<?> getUserPower(LivingEntity user) {
		return this.abilityId.powerClass().get(user);
	}
	
	public boolean addToControlSchemeEditing() {
		return !isSubAbility;
	}
	
	
	public AbilityUsageGroup getAbilityUsageCategory() {
		return usageGroup;
	}
	
	public Ability partsRequired(StandPart... parts) {
		partsRequired.clear();
		Collections.addAll(partsRequired, parts);
		return this;
	}

	public Ability noResolveUnlock() {
		return resolveLevelToUnlock(-1);
	}

	public Ability resolveLevelToUnlock(int level) {
		this.resolveLevelToUnlock = level;
		return this;
	}

	public int getResolveLevelToUnlock() {
		return resolveLevelToUnlock;
	}

	public Ability cooldown(int ticks) {
		int cooldown = Math.max(ticks, 0);
		this.cooldownTicks = cooldown;
		this.cooldownTechnicalTicks = 0;
		this.cooldownAdditionalTicks = cooldown;
		this.cooldownResolveMultiplier = 1.0F;
		this.cooldownResolveMultiplierApplies = false;
		return this;
	}

	public Ability cooldown(int technicalTicks, int additionalTicks) {
		return cooldown(technicalTicks, additionalTicks, 0.0F);
	}

	public Ability cooldown(int technicalTicks, int additionalTicks, float resolveCooldownMultiplier) {
		this.cooldownTechnicalTicks = Math.max(technicalTicks, 0);
		this.cooldownAdditionalTicks = Math.max(additionalTicks, 0);
		this.cooldownTicks = this.cooldownTechnicalTicks + this.cooldownAdditionalTicks;
		this.cooldownResolveMultiplier = Math.max(0.0F, Math.min(resolveCooldownMultiplier, 1.0F));
		this.cooldownResolveMultiplierApplies = true;
		return this;
	}

	public int getCooldown(Power<?> context, int ticksHeld) {
		if (context instanceof StandPower standPower) {
			if (standPower.isUserCreative() || PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(standPower)) {
				return 0;
			}
		}
		int additional = cooldownAdditionalTicks;
		if (cooldownResolveMultiplierApplies && context instanceof StandPower standPower) {
			LivingEntity user = standPower.getUser();
			if (user != null && user.hasEffect(ModStatusEffects.RESOLVE)) {
				additional = (int) ((float) additional * cooldownResolveMultiplier);
			}
		}
		return cooldownTechnicalTicks + additional;
	}

	public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
		return true;
	}

	public boolean sendsConditionMessage() {
		return true;
	}

	public boolean canUserSeeInStoppedTime(LivingEntity user, Power<?> context) {
		return false;
	}

	public boolean canBeUsedInStoppedTime(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		return canUserSeeInStoppedTime(user, context);
	}

	public void setCooldownOnUse(Power<?> context) {
		setCooldownOnUse(context, -1);
	}

	public void setCooldownOnUse(Power<?> context, int ticksHeld) {
		if (context instanceof StandPower standPower) {
			int cooldown = getCooldown(context, ticksHeld);
			if (cooldown > 0) {
				standPower.setAbilityCooldown(name(), cooldown);
			}
		}
	}
	
	
	public void initIsFinisher(String basePunchName) { initIsFinisher(basePunchName, 0.5f); }
	public void initIsFinisher(float finisherValue) { initIsFinisher("heavy_punch", finisherValue); }
	public void initIsFinisher() { initIsFinisher("heavy_punch", 0.5f); }
	public void initIsFinisher(String basePunchName, float finisherValue) {
		this.isStandFinisherOf = new AbilityStandFinisherData(basePunchName, finisherValue);
		this.isSubAbility = true;
	}
	
	// Most of the methods below are called in AvailableAbilities#update(Power, Moveset)

	/**
	 * @deprecated Override {@link Ability#replaceWithSubAbility(Power, AvailableAbilities)} instead, this one is not used.
	 */
	@Deprecated
	public Ability replaceWithSubAbility(Power<?> context) {
		return replaceWithSubAbility(context, null);
	}
	
	/**
	 * @return A variation of this ability depending on the context
	 * (e.g. a specific punch in a combo string, a heavy punch finisher, etc.).
	 * You should also call {@link Ability#isAbilityAvailable(Power)} on each candidate yourself,
	 * to make sure the ability shows up when and only when it is unlocked.
	 */
	@ApiStatus.OverrideOnly
	@Nullable
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		return this;
	}
	
	/**
	 * Whether or not the ability should be visible in the HUD and available to the user.
	 * @return true if the ability is unlocked by the user, 
	 * and it makes sense for it to show up in the HUD in the current context 
	 * (e.g. only show the "Use item" ability if the Stand is holding an item).
	 */
	@ApiStatus.OverrideOnly
	public boolean isAbilityAvailable(Power<?> context) {
		return isAbilityUnlocked(context);
	}
	
	public boolean isAbilityUnlocked(Power<?> context) {
		return getUnlockConditionCheck(context).isPositive();
	}

	public ConditionCheck getUnlockConditionCheck(Power<?> context) {
		PowerData skillsData =
				context != null ? context.getDataForAbility(this) : null;
		if (skillsData == null || skillsData._lockedAbilities.contains(name())) {
			return ConditionCheck.createNegative("not_unlocked");
		}
		ConditionCheck resolveCheck = getResolveUnlockConditionCheck(context);
		return resolveCheck.isPositive() ? ConditionCheck.POSITIVE : resolveCheck;
	}

	public ConditionCheck getResolveUnlockConditionCheck(Power<?> context) {
		int requiredLevel = getRequiredResolveLevelToUnlock(context);
		return requiredLevel <= 0 || hasRequiredResolveLevel(context)
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative(Component.translatable("jojo.message.action_condition.resolve_level", requiredLevel));
	}

	protected int getRequiredResolveLevelToUnlock(Power<?> context) {
		return resolveLevelToUnlock;
	}

	protected boolean hasRequiredResolveLevel(Power<?> context) {
		int requiredLevel = getRequiredResolveLevelToUnlock(context);
		if (requiredLevel <= 0) {
			return true;
		}
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return false;
		}
		if (standPower.isUserCreative()) {
			return true;
		}
		LivingEntity user = standPower.getUser();
		return user != null && ResolveModeEffect.getEffectiveResolveLevel(user, standPower) >= requiredLevel;
	}
	
	/**
	 * A version of {@link Ability#checkSpecificConditions(Power)} with more control, allowing one ability to disable others dynamically
	 */
	// FIXME target parameter (or a simple enough getter)
	public void onConditionCheck(Power<?> context, AvailableAbilities abilities, AbilityConditionCheck thisAbility) {
		ConditionCheck check = checkConditions(context);
		thisAbility.conditionCheck = check;
		
		LivingEntity user = context.getUser();
		if (user != null && user.level().isClientSide() && user == ClientProxy.getClientPlayer()) {
			thisAbility.clientInputState = cl_abilityInputState(context)._value;
		}
	}
	
	public ConditionCheck checkConditions(Power<?> context) {
		ConditionCheck check = checkMainModLogicConditions(context);
		if (check.isPositive()) {
			check = checkSpecificConditions(context);
		}
		return check;
	}
	
	@ApiStatus.Internal
	public ConditionCheck checkMainModLogicConditions(Power<?> context) {
		if (!partsRequired.isEmpty()) {
			StandPower standPower = PowerClass.STAND.cast(context);
			if (standPower != null && standPower.hasPower()) {
				StandInstance standInstance = standPower.getStandInstance().orElse(null);
				if (standInstance != null) {
					for (StandPart part : partsRequired) {
						if (!standInstance.hasPart(part)) {
							return ConditionCheck.createNegative("no_stand_part." + part.serializedName());
						}
					}
				}
			}
		}
		if (!canBeUsedInStoppedTime(context) && isUserStoppedInTime(context)) {
			return ConditionCheck.NEGATIVE;
		}
		if (context instanceof StandPower standPower
				&& !PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(standPower)
				&& standPower.isAbilityOnCooldown(name())) {
			return ConditionCheck.createNegative("cooldown");
		}
		return ConditionCheck.POSITIVE;
	}

	private boolean isUserStoppedInTime(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return false;
		}
		Level level = user.level();
		if (level.isClientSide()) {
			return user == Minecraft.getInstance().player
					&& ClientTimeStopHandler.isTimeStoppedStatic()
					&& !TimeStopClientAwareness.canMove();
		}
		if (level instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			TimeStopState timeStop = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return timeStop.shouldFreeze(user);
		}
		return false;
	}
	
	@ApiStatus.OverrideOnly
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		return ConditionCheck.POSITIVE;
	}
	
	/**
	 * Is only called on physical client side, can be used to override whether or not an ability
	 * shows up in the HUD and if the input is active.
	 * For example, abilities that are used on items in the inventory (Crazy D's Item repair or item marking abilities)
	 * don't show up in the HUD regularly, but are active when you're hovering over a fitting item.
	 * This method is only called on the client, so it can reference client-only classes and methods.
	 */
	public AbilityInputState cl_abilityInputState(Power<?> context) {
		AbilityInputState state = AbilityInputState.init();
		if (InputHandler.inputsDisabled || Minecraft.getInstance().screen != null) {
			state.setFlag(AbilityInputState.IS_ACTIVE, false);
			state.setFlag(AbilityInputState.VISIBLE_WHEN_INACTIVE, true);
		}
		return state;
	}
	
	@Nullable
	public WindupIndicator cl_windupIndicator(LivingEntity clientPlayer, WindupIndicator indicator, float partialTick) {
		return null;
	}
	
	public Component getName(Power<?> context) {
		return abilityName(context, "");
	}

	public String getResourceNamespace() {
		return resourceNamespaceOverride != null
				? resourceNamespaceOverride
				: resourceNamespace(abilityType, abilityId);
	}

	@ApiStatus.Internal
	public final void useAbilityTypeResourceNamespace() {
		if (abilityType.registryKey == null) {
			throw new IllegalStateException(
					"Cannot use an unregistered ability type as a resource namespace");
		}
		resourceNamespaceOverride = abilityType.registryKey.getNamespace();
		name = Component.translatable(getTranslationKey());
	}

	public String getTranslationKey() {
		return getTranslationKey("");
	}

	public String getTranslationKey(String postfix) {
		return getResourceNamespace() + ".ability." + name() + postfix;
	}

	@ApiStatus.OverrideOnly
	public void appendWarnings(List<Component> warnings, @Nullable Power<?> context, Player clientPlayerUser) {}
	
	public String getSpriteName(Power<?> context) {
		return spriteName;
	}

	public ResourceLocation getSpriteId(Power<?> context) {
		return ResourceLocation.fromNamespaceAndPath(
				getResourceNamespace(), getSpriteName(context));
	}
	
	public void renderAbilityIcon(Power<?> context, GuiGraphics guiGraphics, TextureAtlasSprite sprite, float x, float y, int color) {
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), sprite, x, y, 16, 16, 0, color);
	}
	
	// 
	
	
	public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {}

	
	/**
	 * Is called in {@link com.github.standobyte.jojo.powersystem.ability.input.AbilityInput}
	 */
	@ApiStatus.OverrideOnly
	@Nullable
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput, 
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		bufferingState.isActionSuccess = true;
		onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime);
		onClick(level, user, extraClientInput);
		return null;
	}
	
	/** @deprecated Add a {@link BufferingState} argument to the end of this method's signature when overriding it. */
	@Deprecated
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput, 
			InputMethod inputMethod, float clickHoldResolveTime) {
		return null;
	}

	/**
	 * A simplified version of {@link Ability#onKeyPress(Level, LivingEntity, FriendlyByteBuf, InputMethod, float)}, 
	 * when all you want is to just do something the moment user clicks the ability
	 */
	@ApiStatus.OverrideOnly
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {}
	
	
	public JsonObject toConfigJson() {
		JsonObject json = new JsonObject();
		return json;
	}
	
	public void applyConfig(JsonObject config) {
		// TODO (ability config) reflection to edit field values?
	}
	
	
	protected void initVariationAssets() {}
	
}
