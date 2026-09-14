package rotp.core.client.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuScreen;
import rotp.core.client.ui.screen_jojomenu.JojoMenuTabs;
import rotp.core.client.ui.screen_jojomenu.Tab;
import rotp.core.config.SettingsField;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.network.c2s.ClNoParamsPacket;
import rotp.core.network.c2s.ClNoParamsPacket.PacketType;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.powers.hamon.ClHamonInteractAskTeacherPacket;
import rotp.core.impl.powers.hamon.ClHamonInteractTeachPacket;
import rotp.core.impl.powers.hamon.ClHamonMeditationPacket;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

public class VanillaKeybinds {
	public static final String MAIN_CATEGORY = "key.categories." + JojoMod.MOD_ID;
	private static final IKeyConflictContext GRAB_CHARGED_HEAVY_CONFLICT_CONTEXT =
			new IKeyConflictContext() {
				@Override
				public boolean isActive() {
					InputHandler inputHandler = InputHandler.getInstance();
					boolean inGameContextActive = KeyConflictContext.IN_GAME.isActive();
					boolean hasBindingOwner = inGameContextActive && inputHandler != null
							&& inputHandler.hasActiveGrabChargedHeavyBinding();
					boolean preserveVanillaUse = hasBindingOwner
							&& inputHandler
									.shouldPreserveSemanticVanillaUsePress();
					return shouldActivateGrabChargedHeavyConflictContext(
							inGameContextActive,
							preserveVanillaUse, hasBindingOwner);
				}

				@Override
				public boolean conflicts(IKeyConflictContext other) {
					return KeyConflictContext.IN_GAME.conflicts(
							other == this
									? KeyConflictContext.IN_GAME
									: other);
				}
			};

	@ApiStatus.Internal
	public static boolean shouldActivateGrabChargedHeavyConflictContext(
			boolean inGameContextActive,
			boolean preserveSemanticVanillaUsePress) {
		return shouldActivateGrabChargedHeavyConflictContext(
				inGameContextActive, preserveSemanticVanillaUsePress, true);
	}

	@ApiStatus.Internal
	public static boolean shouldActivateGrabChargedHeavyConflictContext(
			boolean inGameContextActive,
			boolean preserveSemanticVanillaUsePress,
			boolean hasActiveBindingOwner) {
		return inGameContextActive && hasActiveBindingOwner && !preserveSemanticVanillaUsePress;
	}

	public KeyMapping summonStand;
	public KeyMapping standArmsOnlyHUD;
	public KeyMapping playerPowerHUD;
	public KeyMapping hamonBreath;
	public KeyMapping grabChargedHeavy;
	public KeyMapping useAbility;
	public KeyMapping switchSpecial;
	public KeyMapping disableHUDControls;
	public KeyMapping jojoStuffMenu;
	public KeyMapping hamonMeditation;
	public KeyMapping jojoLmbRmb;
	
	public static VanillaKeybinds register(RegisterKeyMappingsEvent event) {
		VanillaKeybinds binds = new VanillaKeybinds();
		event.register(binds.summonStand = new Jokerge(
				JojoMod.MOD_ID + ".key.toggle_stand", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.standArmsOnlyHUD = new Jokerge(
				JojoMod.MOD_ID + ".key.stand_mode", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.playerPowerHUD = new Jokerge(
				JojoMod.MOD_ID + ".key.non_stand_mode", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.hamonBreath = new Jokerge(
				JojoMod.MOD_ID + ".key.hamon_breath", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.grabChargedHeavy = new Jokerge(
				MovesetBuilder.GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME,
				GRAB_CHARGED_HEAVY_CONFLICT_CONTEXT, KeyModifier.SHIFT,
				InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.useAbility = new Jokerge(
				JojoMod.MOD_ID + ".key.use_special_ability", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.switchSpecial = new Jokerge(
				JojoMod.MOD_ID + ".key.ability_hotbar", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.disableHUDControls = new Jokerge(
				JojoMod.MOD_ID + ".key.disable_hotbars", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip().canBeHoldOrToggle(new SettingsField<Boolean>() {
					@Override public Boolean get() { return ClientModSettings.getSettingsReadOnly().toggleDisableHotbars; }
					@Override public void set(Boolean value) {
						ClientModSettings.edit(settings -> {
							settings.toggleDisableHotbars = value;
						}, false);
					}
				}));
		event.register(binds.jojoStuffMenu = new Jokerge(
				JojoMod.MOD_ID + ".key.jojo_menu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.hamonMeditation = new Jokerge(
				JojoMod.MOD_ID + ".key.meditation", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, MAIN_CATEGORY)
				.inInitOrder().withDescTooltip());
		event.register(binds.jojoLmbRmb = new LmbRmbKeyMapping(
				JojoMod.MOD_ID + ".key.jojo_test", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, MAIN_CATEGORY)
				.inInitOrder());
		return binds;
	}

	public void handleTick() {
		InputHandler inputHandler = InputHandler.getInstance();
		if (standArmsOnlyHUD.consumeClick()) {
			inputHandler.curPowerClassToggle = inputHandler.curPowerClassToggle != PowerClass.STAND ? PowerClass.STAND : null;
		}
		
		if (playerPowerHUD.consumeClick()) {
			cyclePlayerPowerHud();
		}
//		
		if (summonStand.consumeClick()) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			if (standPower != null && standPower.hasPower() && !standPower.isSummoned()) {
				inputHandler.curPowerClassToggle = null;
			}
			PacketDistributor.sendToServer(ClNoParamsPacket.of(PacketType.SUMMON_STAND));
		}
		
		if (ClientModSettings.getSettingsReadOnly().toggleDisableHotbars && disableHUDControls.consumeClick()) {
			InputHandler.inputsDisabled = !InputHandler.inputsDisabled;
		}
		
		if (jojoStuffMenu.consumeClick()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen instanceof IJojoMenuScreen) {
				mc.popGuiLayer();
			}
			else if (!tryHamonTeacherInteraction(mc)) {
				Tab tab = JojoMenuTabs.getTabToOpenOnMenuKey();
				if (tab == null || !tab.onClick(mc, mc.screen)) {
					JojoMod.getLogger().error(
							"Failed to open the JoJo menu for player {}.",
							mc.player != null
									? mc.player.getScoreboardName()
									: "<no player>");
				}
			}
		}
		
		if (hamonMeditation.consumeClick()) {
			PacketDistributor.sendToServer(new ClHamonMeditationPacket());
		}
		
		while (jojoLmbRmb.consumeClick()) {
			inputHandler.doTheThing();
		}
	}

	public void cyclePlayerPowerHud() {
		InputHandler inputHandler = InputHandler.getInstance();
		if (inputHandler.curPowerClassToggle != PowerClass.PLAYER_POWER) {
			inputHandler.curPowerClassToggle = PowerClass.PLAYER_POWER;
			var controlScheme = inputHandler.getActiveControlScheme();
			if (controlScheme != null) {
				controlScheme.resetToFirstGroup();
			}
		}
		else {
			var controlScheme = inputHandler.getActiveControlScheme();
			if (controlScheme == null || !controlScheme.selectNextGroup()) {
				inputHandler.curPowerClassToggle = null;
			}
		}
	}

	private static boolean tryHamonTeacherInteraction(Minecraft mc) {
		if (mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
				|| !(mc.hitResult instanceof EntityHitResult hitResult)) {
			return false;
		}
		Entity target = hitResult.getEntity();
		if (target == mc.player || !target.isAlive() || target.isSpectator()) {
			return false;
		}
		PlayerPower playerPower = PlayerPower.get(mc.player);
		if (playerPower == null) {
			return false;
		}
		if (target instanceof Player learner
				&& playerPower.getCurTypeData(ModPlayerPowers.HAMON)
						.map(hamon -> hamon.playerWantsToLearn(learner)).orElse(false)) {
			PacketDistributor.sendToServer(new ClHamonInteractTeachPacket(learner.getId()));
			return true;
		}
		if (!playerPower.hasPower() && target instanceof LivingEntity teacher
				&& PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).isPresent()) {
			PacketDistributor.sendToServer(new ClHamonInteractAskTeacherPacket(teacher.getId()));
			return true;
		}
		return false;
	}
	
	
	
	public static final Set<String> ADD_DESC_TOOLTIP = new HashSet<>();
	public static final Map<String, SettingsField<Boolean>> HOLD_OR_TOGGLE = new HashMap<>();
	
	public static class Jokerge extends KeyMapping {
		protected static Map<String, MutableInt> PER_CATEGORY = new HashMap<String, MutableInt>();
		protected int orderIndex = Integer.MAX_VALUE;

		public Jokerge(String name, int keyCode, String category) {	super(name, keyCode, category); }
		public Jokerge(String name, InputConstants.Type type, int keyCode, String category) { super(name, type, keyCode, category); }
		public Jokerge(String description, IKeyConflictContext keyConflictContext, InputConstants.Type inputType, int keyCode, String category) { super(description, keyConflictContext, inputType, keyCode, category); }
		public Jokerge(String description, IKeyConflictContext keyConflictContext, InputConstants.Key keyCode, String category) { super(description, keyConflictContext, keyCode, category); }
		public Jokerge(String description, IKeyConflictContext keyConflictContext, KeyModifier keyModifier, InputConstants.Type inputType, int keyCode, String category) { super(description, keyConflictContext, keyModifier, inputType, keyCode, category); }
		public Jokerge(String description, IKeyConflictContext keyConflictContext, KeyModifier keyModifier, InputConstants.Key keyCode, String category) { super(description, keyConflictContext, keyModifier, keyCode, category); }
		
		public Jokerge inInitOrder() {
			this.orderIndex = PER_CATEGORY.computeIfAbsent(this.getCategory(), __ -> new MutableInt()).getAndIncrement();
			return this;
		}
		
		public Jokerge withDescTooltip() {
			ADD_DESC_TOOLTIP.add(this.getName());
			return this;
		}
		
		public Jokerge canBeHoldOrToggle(SettingsField<Boolean> clientSetting) {
			HOLD_OR_TOGGLE.put(this.getName(), clientSetting);
			return this;
		}

		@Override
		public int compareTo(KeyMapping other) {
			if (this.getCategory() == other.getCategory() && other instanceof Jokerge jokerge) {
				int compare = Integer.compare(this.orderIndex, jokerge.orderIndex);
				if (compare != 0) return compare;
			}
			return super.compareTo(other);
		}
	}
	
	public static class LmbRmbKeyMapping extends Jokerge {
		public LmbRmbKeyMapping(String description, IKeyConflictContext keyConflictContext, InputConstants.Type inputType, int keyCode, String category) {
			super(description, keyConflictContext, inputType, keyCode, category);
		}
		
		@Override
		public void setKey(InputConstants.Key key) {
			super.setKey(key);
			ClientModSettings.edit(settings -> {
				settings.poseOnLmbRmb = key.equals(getDefaultKey());
			}, false);
		}
		
		@Override
		public boolean isDefault() {
			return super.isDefault() && ClientModSettings.getSettingsReadOnly().poseOnLmbRmb;
		}
		
		@Override
		public Component getTranslatedKeyMessage() {
			return isDefault() ? Component.translatable("key.mouse.lmb_and_rmb") : super.getTranslatedKeyMessage();
		}
	}
	
}
