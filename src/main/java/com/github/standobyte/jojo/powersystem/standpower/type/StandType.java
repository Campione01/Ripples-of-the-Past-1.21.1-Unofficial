package com.github.standobyte.jojo.powersystem.standpower.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsScreen;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.SimpleTagKey;
import com.github.standobyte.jojo.network.s2c.StandSkinSoundPacket;
import com.github.standobyte.jojo.network.s2c.TrNonEntityStandSummonPacket;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.StandAwakening.AwakeningStage;
import com.github.standobyte.jojo.powersystem.standpower.datapack.DataDrivenStandsLoader;
import com.github.standobyte.jojo.powersystem.standpower.datapack.StandTypeClass;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandControlType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.powersystem.standpower.type.SummonedStand.BlankSummonedStand;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.sound.MultiSoundEventResolver;
import com.github.standobyte.jojo.util.sound.OstSoundList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IConfigurable;

public class StandType extends PowerType {
	static {
		SimpleTagKey.SimpleTagType.registerType(StandType.class, StandType::getId, "stands");
	}

	protected final ResourceLocation standTypeId;
	protected StandStats stats;
	protected boolean isEnabled;
	protected boolean playSummonSound = true;
	protected boolean playUnsummonSound = true;
	protected Supplier<? extends Holder<SoundEvent>> summonSoundSupplier = () -> ModSoundEvents.STAND_SUMMON;
	protected Supplier<? extends Holder<SoundEvent>> unsummonSoundSupplier = () -> ModSoundEvents.STAND_UNSUMMON;
	protected Supplier<? extends Holder<SoundEvent>> summonShoutSupplier = () -> null;
	protected Supplier<OstSoundList> ostSupplier = () -> null;
	@Nullable
	private StandControlType nonEntityStandControlType;
	private boolean nonEntityManualControl;
	private boolean nonEntityDistanceStrengthDecay;
	private boolean nonEntityStandControlPolicyConfigured;
	
	public StandCreationSource createdIn = StandCreationSource.REGISTRY;
	public List<Component> discExtraTooltip = new ArrayList<>();
	/** The lesser this field is, the earlier the disc will appear in the creative tab. Default is 100. */
	public int discCategoryPriority = 100;
	public boolean translucentDisc = false;
	public int discStoryPartPriority = 100;
	
	public StandType(StandStats stats, MovesetBuilder moveset, 
			ResourceLocation id) {
		super(moveset);
		this.standTypeId = id;
		if (stats == null) stats = new StandStats(0, 0, 0, 0, 0, 0);
		this.stats = stats;
		this.isEnabled = true;
		addAddonCredits();
	}
	
	public <T extends StandType> T init(Consumer<T> init) {
		@SuppressWarnings("unchecked")
		T cast = (T) this;
		init.accept(cast);
		return cast;
	}

	/**
	 * Declares the control topology of a Stand that does not use a live
	 * {@link StandEntity}. Entity-backed Stands must use
	 * {@link EntityStandType#standControlType(StandControlType)} instead.
	 */
	public <T extends StandType> T nonEntityStandControlPolicy(
			StandControlType controlType,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		if (this instanceof EntityStandType) {
			throw new IllegalStateException(
					"Entity Stands must use the entity control policy");
		}
		if (controlType != StandControlType.AUTOMATIC
				&& controlType != StandControlType.COLONY
				&& controlType != StandControlType.PHENOMENON) {
			throw new IllegalStateException(
					"Non-entity Stands require an automatic, colony, or phenomenon control type");
		}
		if (manualControl || distanceStrengthDecay) {
			throw new IllegalStateException(
					"Non-entity Stands cannot use generic manual control or distance decay");
		}
		this.nonEntityStandControlType = controlType;
		this.nonEntityManualControl = false;
		this.nonEntityDistanceStrengthDecay = false;
		this.nonEntityStandControlPolicyConfigured = true;
		@SuppressWarnings("unchecked")
		T cast = (T) this;
		return cast;
	}

	private void validateNonEntityStandControlPolicy() {
		if (!nonEntityStandControlPolicyConfigured
				|| nonEntityStandControlType == null) {
			throw new IllegalStateException(
					"standControlType is required for " + getId());
		}
	}

	public StandControlType getStandControlType() {
		validateNonEntityStandControlPolicy();
		return nonEntityStandControlType;
	}

	public boolean canBeManuallyControlled() {
		validateNonEntityStandControlPolicy();
		return nonEntityManualControl;
	}

	public boolean usesDistanceStrengthDecay() {
		validateNonEntityStandControlPolicy();
		return nonEntityDistanceStrengthDecay;
	}

	public <T extends StandType> T summonShout(Supplier<? extends Holder<SoundEvent>> summonShoutSupplier) {
		return init(stand -> stand.summonShoutSupplier = summonShoutSupplier != null ? summonShoutSupplier : () -> null);
	}

	public <T extends StandType> T summonSound(Supplier<? extends Holder<SoundEvent>> summonSoundSupplier) {
		return init(stand -> stand.summonSoundSupplier = summonSoundSupplier != null ? summonSoundSupplier : () -> ModSoundEvents.STAND_SUMMON);
	}

	public <T extends StandType> T unsummonSound(Supplier<? extends Holder<SoundEvent>> unsummonSoundSupplier) {
		return init(stand -> stand.unsummonSoundSupplier = unsummonSoundSupplier != null ? unsummonSoundSupplier : () -> ModSoundEvents.STAND_UNSUMMON);
	}

	public <T extends StandType> T ost(Supplier<OstSoundList> ostSupplier) {
		return init(stand -> stand.ostSupplier = ostSupplier != null ? ostSupplier : () -> null);
	}

	public Holder<SoundEvent> getSummonSound() {
		Holder<SoundEvent> sound = summonSoundSupplier.get();
		return sound != null ? sound : ModSoundEvents.STAND_SUMMON;
	}

	public Holder<SoundEvent> getUnsummonSound() {
		Holder<SoundEvent> sound = unsummonSoundSupplier.get();
		return sound != null ? sound : ModSoundEvents.STAND_UNSUMMON;
	}

	@Nullable
	public OstSoundList getOst(@Nullable LivingEntity user) {
		return ostSupplier.get();
	}
	
	public <T extends StandType> T discTooltipWIP() { return discTooltipWIP(false); }
	public <T extends StandType> T discTooltipWIP(boolean translucentDisc) { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.wip")
					.withStyle(ChatFormatting.ITALIC).withColor(0x808000));
			stand.discCategoryPriority = 200;
			stand.translucentDisc = translucentDisc;
		});
	}
	
	public <T extends StandType> T discTooltipOld() { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.old")
					.withStyle(ChatFormatting.ITALIC).withColor(0x808000));
			stand.discCategoryPriority = 201;
			stand.translucentDisc = true;
		});
	}
	
	public <T extends StandType> T discTooltipExperimental() { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.experimental")
					.withStyle(ChatFormatting.ITALIC).withColor(0x800000));
			stand.discCategoryPriority = 300;
			stand.translucentDisc = true;
		});
	}
	
	public void discTooltipDatapack() { 
		this.createdIn = StandCreationSource.DATAPACK;
		this.discExtraTooltip.add(
				Component.translatable("item.jojo_ripples.stand_disc.data_pack")
				.withStyle(ChatFormatting.ITALIC).withColor(0x6060ff));
		this.discCategoryPriority = 400;
	}

	public void addAddonCredits() {
		if (createdIn == StandCreationSource.REGISTRY) {
			String modId = standTypeId.getNamespace();
			if (!modId.equals(JojoMod.MOD_ID)) {
				ModList.get().getModContainerById(modId)
				.map(mod -> mod.getModInfo())
				.flatMap(modInfo -> modInfo instanceof IConfigurable ? ((IConfigurable) modInfo).getConfigElement("authors") : Optional.empty())
				.map(authorsString -> authorsString instanceof String ? (String) authorsString : null)
				.ifPresent(authors -> {
					authors = authors.replace(", StandoByte", "").replace("StandoByte, ", "");
					discExtraTooltip.add(
							Component.translatable("item.jojo_ripples.stand_disc.addon_author", authors)
							.withStyle(ChatFormatting.ITALIC).withColor(0x00b0b0));
				});
			}
		}
	}
	
	
	@Override
	public JsonObject makeConfigTemplate() {
		StandControlType controlType = getStandControlType();
		JsonObject json = super.makeConfigTemplate();
		StandTypeClass.addClassToJson(json, this.getClass());
		json.add("stats", stats.makeConfigTemplate());
		json.addProperty("standControlType", controlType.name());
		json.addProperty(
				"manualControlEnabled",
				canBeManuallyControlled());
		json.addProperty(
				"distanceStrengthDecayEnabled",
				usesDistanceStrengthDecay());
		return json;
	}
	
	@Override
	public void applyConfig(JsonElement json) {
		super.applyConfig(json);
		JsonObject config = json.getAsJsonObject();
		Optional.ofNullable(config.get("stats")).ifPresent(stats::applyConfig);
	}

	@Override
	public void restoreDefaults() {
		super.restoreDefaults();
		stats.restoreDefaults();
	}
	
	
	@Override
	public boolean isEnabled() {
		return isEnabled;
	}
	
	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}
	
	
	public StandStats getStandStats() {
		return stats;
	}
	
	
	@Nonnull
	@Override
	public StandTypePersistentData newDataInstance() {
		return new StandTypePersistentData(this);
	}
	
	
	public void onUserSummonCommand(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned() && onTrySummon(user, standPower)) {
			summon(user, standPower);
		}
		else {
			unsummon(user, standPower);
		}
	}
	
	public boolean summon(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned() && standPower.canUsePower()) {
			SummonedStand summonedStand = makeSummonedStand();
			if (summonedStand == null) return false;
			
			standPower.setSummonedStand(summonedStand);
			if (user != null && !user.level().isClientSide()) {
				playSummonShout(user);
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrNonEntityStandSummonPacket(user.getId(), true));
				if (playSummonSound) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, StandSkinSoundPacket.play(
							user.position(), MultiSoundEventResolver.resolve(getSummonSound()),
							standPower, user.getSoundSource(), 1, 1));
				}
				if (user instanceof net.minecraft.server.level.ServerPlayer player) {
					ModCriteriaTriggers.triggerSummonStand(player);
				}
			}
			return true;
		}
		return false;
	}

	protected void playSummonShout(LivingEntity user) {
		if (user != null && !user.level().isClientSide() && !user.isShiftKeyDown()) {
			Holder<SoundEvent> shout = summonShoutSupplier.get();
			if (shout != null) {
				JojoModUtil.sayVoiceLine(user, shout);
			}
		}
	}
	
	public boolean onTrySummon(LivingEntity user, StandPower standPower) {
		AwakeningStage awakeningStage = standPower.userStandAwakeningState.stage;
		return switch (awakeningStage) {
			case FULL_CONTROL -> true;
			case PARTIALLY_AWAKENED -> {
				user.sendSystemMessage(Component.translatable("stand_summon.not_in_full_control"));
				yield false;
			}
			case AWAKENING_PASSIVE -> {
				user.sendSystemMessage(Component.translatable("stand_summon.dormant"));
				yield false;
			}
		};
	}
	
	/**
	 * If this is overriden to return null, the Stand type will have no summon/unsummon mechanic.
	 */
	protected SummonedStand makeSummonedStand() {
		return new BlankSummonedStand();
	}
	
	public void unsummon(LivingEntity user, StandPower standPower) {
		forceUnsummon(user, standPower);
	}
	
	public void forceUnsummon(LivingEntity user, StandPower standPower) {
		if (standPower.isSummoned()) {
			standPower.setSummonedStand(null);
			if (user != null && !user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrNonEntityStandSummonPacket(user.getId(), false));
				if (playUnsummonSound) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, StandSkinSoundPacket.play(
							user.position(), MultiSoundEventResolver.resolve(getUnsummonSound()), 
							standPower, user.getSoundSource(), 1, 1));
				}
			}
		}
	}
	
	
	public boolean usesStamina(StandPower standPower) {
		return true;
	}
	
	public float getMaxStamina(StandPower standPower) {
		return getBaseMaxStamina(standPower) * getStaminaMultiplier(standPower);
	}
	
	protected float getBaseMaxStamina(StandPower standPower) {
		return 1000;
	}
	
	public float getStaminaRegen(StandPower standPower) {
		return getBaseStaminaRegen(standPower) * getStaminaMultiplier(standPower);
	}

	public float getStaminaRegenBeforeExternalModifiers(StandPower standPower) {
		return getBaseStaminaRegen(standPower);
	}

	public float getStaminaDurabilityMultiplier(StandPower standPower) {
		return getStaminaMultiplier(standPower);
	}
	
	protected float getBaseStaminaRegen(StandPower standPower) {
		if (standPower.isSummoned()) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				EntityActionInstance action = LivingComponentAction.getCurEntityAction(standEntity);
				if (action != null && !action.canStaminaRegen(standPower, standEntity)) {
					return 0;
				}
			}
			return 1.5f;
		}
		return 3;
	}
	
	protected static float getStaminaMultiplier(StandPower standPower) {
		double durability = AttributeUtil.getValueOrDefault(standPower.getUser(), ModEntityAttributes.STAND_DURABILITY);
		return StandStatFormulas.getStaminaMultiplier(durability);
	}
	
	
	public boolean usesResolve(StandPower standPower) {
		return true;
	}

	public boolean keepOnDeath(StandPower standPower) {
		return JojoModConfig.getCommonConfigInstance(false).keepStandOnDeath.get();
	}

	public int getMaxResolveLevel() {
		return StandTypePersistentData.MAX_RESOLVE_LEVEL;
	}

	public void onNewResolveLevel(StandPower standPower) {
		StandTypePersistentData data = standPower != null ? standPower.getCurTypeData() : null;
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (data != null && user != null && !user.level().isClientSide()
				&& data.unlockOriginalResolveSkills(standPower)) {
			data.syncOnUpdate(user);
		}
	}
	
	
	@Override
	public Map<String, StandUnlockableSkill> getUnlockableSkills() {
		return (Map<String, StandUnlockableSkill>) super.getUnlockableSkills();
	}
	
	
	@Override
	public ResourceLocation getId() {
		return standTypeId;
	}

	@Override
	protected ResourceLocation getMovesetExtensionTargetId() {
		return standTypeId;
	}
	
	@Nullable
	public static StandType fromId(ResourceLocation id) {
		StandType stand = DataDrivenStandsLoader.getDatapackStand(id);
		if (stand == null) {
			stand = JojoRegistries.DEFAULT_STANDS_REG.get(id);
		}
		if (stand != null && !stand.isEnabled()) {
			stand = null;
		}
		return stand;
	}
	
	public static Stream<StandType> getAllEnabledStands() {
		return Stream.concat(
				JojoRegistries.DEFAULT_STANDS_REG.entrySet().stream().map(Map.Entry::getValue).filter(StandType::isEnabled), 
				DataDrivenStandsLoader.getAllDatapackStands());
	}
	
//	public static final StreamCodec<ByteBuf, StandType> SYNC_VIA_ID = 
//			ResourceLocation.STREAM_CODEC.map(StandType::fromId, StandType::getId);
//	
	
	
	@Override
	public PowerClass<StandPower> getPowerClass() {
		return PowerClass.STAND;
	}
	
	public Lazy<Component> name = Lazy.of(() -> Component.translatable(Util.makeDescriptionId("stand", this.getId())));
	@Override
	public Component getName(Power<?> playerPowerData) {
		return ((StandPower) playerPowerData).getStandInstance()
				.map(stand -> stand.getStandName(playerPowerData.getUser().level().isClientSide()))
				.orElseGet(this.name::get);
	}
	
	
	public StandSkinsScreen.SkinView makeSkinUIElement(StandSkin skin, StandSkinsScreen screen, int x, int y, int standY, int row, int column, boolean isBottomRow) {
		return new StandSkinsScreen.SkinView(this, skin, screen, x, y, standY, row, column, isBottomRow);
	}
	
}
