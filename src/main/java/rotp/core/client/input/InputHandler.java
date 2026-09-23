package rotp.core.client.input;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.ClientTickHandler;
import rotp.core.client.input.clickhold.AmbiguousKeyPress;
import rotp.core.client.input.controlscheme.AllControlSchemes;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.input.controlscheme.ClientInputBind;
import rotp.core.client.input.controlscheme.ClientKey;
import rotp.core.client.ui.AbilitySelectionWheel;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.ClientModSettings;
import rotp.core.event.client.PreKeyInputEvent;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.item.KnifeItem;
import rotp.core.item.StoneMaskItem;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.network.c2s.ClAbilityInputPacket;
import rotp.core.network.c2s.ClNoParamsPacket;
import rotp.core.network.c2s.ClNoParamsPacket.PacketType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.AbilityInput.InputEventType;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.enums.Direction2D;
import rotp.core.util.mod.IPlayerLeap;
import rotp.core.impl.powers.hamon.ClHamonDoubleShiftPressPacket;
import rotp.core.impl.powers.hamon.ClHamonMeditationPacket;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public class InputHandler {
	private static InputHandler instance;
	private final Minecraft mc = Minecraft.getInstance();
	
	public static final ClientKey LMB = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT);
	public static final ClientKey RMB = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT);
	public static final ClientKey MMB = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE);
	
	public static void init(RegisterKeyMappingsEvent event) {
		if (instance == null) {
			instance = new InputHandler();
			instance.registerBindings(event);
			NeoForge.EVENT_BUS.register(instance);
		}
	}
	
	public static InputHandler getInstance() {
		return instance;
	}
	
	public VanillaKeybinds vanillaKeybinds;
	@Nullable public static ClientKey lastActionKey;
	private final DoubleShiftDetector hamonDoubleShift = new DoubleShiftDetector();
	
	private void registerBindings(RegisterKeyMappingsEvent event) {
		this.vanillaKeybinds = VanillaKeybinds.register(event);
	}
	
	public static boolean inputsDisabled;
	
	
	@SubscribeEvent
	public void handleKeyBindingsPost(ClientTickEvent.Post event) {
		vanillaKeybinds.handleTick();
		tickReleaseEventQueue();
		tickPendingReleaseUse();
		tickKeyPressIndication();
	}
	
	private void tickPendingReleaseUse() {
		if (pendingReleaseUse.isEmpty()) {
			return;
		}
		var iter = pendingReleaseUse.iterator();
		while (iter.hasNext()) {
			PendingHotbarUse pending = iter.next();
			// The ability selection wheel closes on its own render, and on the tick it does the selected ability can
			// still be flagged inactive client-side - resolving it then finds nothing, which is why this waits for it
			// to become usable instead of firing once and hoping.
			if (mc.screen == null && isSelectedAbilityUsable(pending.hotbar)) {
				useSelectedHotbarAbility(pending.hotbar);
				iter.remove();
			}
			else if (++pending.waitedTicks >= PENDING_RELEASE_USE_TICKS) {
				iter.remove();
			}
		}
	}
	
	/** Whether the hotbar's selected ability would resolve to something usable right now. */
	private boolean isSelectedAbilityUsable(Hotbar hotbar) {
		HotbarSlot slot = hotbar.getSelected();
		if (slot == null || slot.showAbility() == null) {
			return false;
		}
		List<AbilityControlsEntry> clickBound = slot.getBinds().getAll(getCurModifier(), InputMethod.CLICK);
		if (clickBound.isEmpty()) {
			return false;
		}
		CurInput probe = CurInput.instance;
		probe.reset();
		ClientControlScheme.setPrioritizedAbility(probe.clickAbility, clickBound,
				inputState -> AbilityInputState.isInputActive(inputState, PowerHud.isInContainerScreen()));
		boolean usable = probe.clickAbility.curActiveAbility != null;
		probe.reset();
		return usable;
	}
	
	private static final class PendingHotbarUse {
		private final Hotbar hotbar;
		private int waitedTicks;
		
		private PendingHotbarUse(Hotbar hotbar) {
			this.hotbar = hotbar;
		}
	}

	@SubscribeEvent
	public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		clearDisconnectedInputState();
	}

	private void clearDisconnectedInputState() {
		clearDisconnectCollections(
				_heldKeys,
				keyReleaseEventQueue,
				_recentlyPressed,
				modifiersQueue,
				hotbarsSelection,
				toggledHotbarsSelection);
		pendingReleaseUse.clear();
		hamonDoubleShift.reset();
		lastActionKey = null;
		inputsDisabled = false;
		curPowerClassToggle = null;
		hotbarsSelectionTimestamp = 0.0F;
	}

	@ApiStatus.Internal
	public static void clearDisconnectCollections(
			Map<?, ?> heldKeys,
			Queue<?> releaseQueue,
			Map<?, ?> recentlyPressed,
			List<?> modifiers,
			Map<?, ?> hotbarSelections,
			Set<?> toggledHotbarSelections) {
		heldKeys.clear();
		releaseQueue.clear();
		recentlyPressed.clear();
		modifiers.clear();
		hotbarSelections.clear();
		toggledHotbarSelections.clear();
	}
	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onFrameUpdate(RenderFrameEvent.Pre event) {
		if (!ClientModSettings.getSettingsReadOnly().toggleDisableHotbars) {
			inputsDisabled = _heldKeys.containsKey(ClientKey.fromVanillaKeybind(vanillaKeybinds.disableHUDControls));
		}
		
		float tickDelta = mc.getTimer()/*getDeltaTracker()*/.getRealtimeDeltaTicks();
		frameUpdateHeldKeys(tickDelta);
	}



	@SubscribeEvent(priority = EventPriority.LOW)
	public void handleKeyInput(PreKeyInputEvent event) {
		if (mc.getConnection() == null) return;

		InputConstants.Type keyType;
		int keyCode;
		if (event.getKey() == -1) {
			keyType = InputConstants.Type.SCANCODE;
			keyCode = event.getScanCode();
		}
		else {
			keyType = InputConstants.Type.KEYSYM;
			keyCode = event.getKey();
		}
		ClientKey key = ClientKey.make(keyType, keyCode);
		Key vanillaKey = key.getVanillaKey();
		if (event.getAction() == InputConstants.PRESS
				&& vanillaKey != null
				&& vanillaKeybinds.playerPowerHUD.isActiveAndMatches(vanillaKey)) {
			vanillaKeybinds.cyclePlayerPowerHud();
			event.setCanceled(true);
			return;
		}
		handleInputEvent(key, event.getAction(), event.getModifiers(), event);
	}
	
	@SubscribeEvent(priority = EventPriority.LOW)
	public void handleMouseInput(InputEvent.MouseButton.Pre event) {
		if (mc.getConnection() == null) return;
		
		handleInputEvent(ClientKey.make(InputConstants.Type.MOUSE, event.getButton()), event.getAction(), event.getModifiers(), event);
	}
	
	public void handleInputEvent(ClientKey key, int action, int modifiers, ICancellableEvent event) {
		if (input(key, action, modifiers)) {
			event.setCanceled(true);
		}
		
		Key vanillaKey = key.getVanillaKey();
		if (vanillaKey != null) {
			switch (action) {
				case InputConstants.PRESS -> addKeyModifier(vanillaKey);
				case InputConstants.RELEASE -> removeKeyModifier(vanillaKey);
			}
		}
	}
	
	
	protected boolean shouldQueueInput() {
		return !(mc.screen == null || PowerHud.isInContainerScreen() || mc.screen instanceof AbilitySelectionWheel);
	}
	
	protected void tickReleaseEventQueue() {
		if (!keyReleaseEventQueue.isEmpty() && mc.getConnection() != null && !shouldQueueInput()) {
			for (DelayedInput keyRelease : keyReleaseEventQueue) {
				input(keyRelease.key, keyRelease.action, keyRelease.modifiers);
			}
			keyReleaseEventQueue.clear();
		}
	}
	
	public static record DelayedInput(ClientKey key, int action, int modifiers) {}
	
	private Queue<DelayedInput> keyReleaseEventQueue = new ArrayDeque<>();

	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void handleMouseScroll1(InputEvent.MouseScrollingEvent event) {
		if (mc.getConnection() == null) return;
		if (mc.screen != null) return;
		if (hotbarScroll(event.getScrollDeltaY())) {
			event.setCanceled(true);
		}
	}
	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void handleMouseScrollWithWheelOpened(ScreenEvent.MouseScrolled.Pre event) {
		if (mc.getConnection() == null) return;
		if (hotbarScroll(event.getScrollDeltaY())) {
			event.setCanceled(true);
		}
	}
	
	
	public PowerClass<?> curPowerClassToggle = null;
	
	public ClientControlScheme getActiveControlScheme() {
		Power<?> curPower = null;
		
		if (mc.player != null) {
			if (curPowerClassToggle != null) {
				Power<?> power = ClientPowerCache.getPower(curPowerClassToggle);
				if (power != null && power.hasPower()) {
					curPower = power;
				}
			}
			else {
				StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
				if (standPower != null && isStandSummonedForDefaultControls(standPower)) {
					curPower = standPower;
				}
			}
		}
		if (curPower != null) {
			return AllControlSchemes.getForPowerType(curPower.getPowerType());
		}
		return null;
	}
	
	private boolean isStandSummonedForDefaultControls(StandPower standPower) {
		return standPower.hasPower() && standPower.canUsePower() && standPower.isSummoned()
				&& !ClientGlobals.isPlayerStandFullBodyUnsummoning();
	}

	private ClientControlScheme getActiveControlSchemeForInput(ClientKey key, KeyModifier keyModifier) {
		ClientControlScheme activeControlScheme = getActiveControlScheme();
		boolean vanillaUseTrigger =
				key.equals(ClientKey.fromVanillaKeybind(mc.options.keyUse));
		if (shouldPreserveStandDiscUse(vanillaUseTrigger)) {
			return null;
		}
		if (shouldPreserveStoneMaskKnifeUse(vanillaUseTrigger)) {
			return null;
		}
		if (shouldPreserveUnsummonedStandVanillaUsePress(
				activeControlScheme,
				vanillaUseTrigger,
				keyModifier)) {
			return null;
		}
		ClientControlScheme directPlayerPowerControlScheme = getDirectPlayerPowerControlScheme(key, keyModifier);
		if (directPlayerPowerControlScheme != null) {
			return directPlayerPowerControlScheme;
		}
		if (activeControlScheme != null) {
			return activeControlScheme;
		}
		if (curPowerClassToggle != null || !shouldAllowUnsummonedStandHotbarInput(key, keyModifier)) {
			return null;
		}
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower != null && standPower.hasPower() && standPower.canUsePower() && !standPower.isSummoned()) {
			return AllControlSchemes.getForPowerType(standPower.getPowerType());
		}
		return null;
	}

	boolean shouldPreserveSemanticVanillaUsePress() {
		return shouldPreserveStandDiscUse(true)
				|| shouldPreserveStoneMaskKnifeUse(true)
				|| shouldPreserveUnsummonedStandVanillaUsePress(
				getActiveControlScheme(), true, getCurModifier());
	}

	boolean hasActiveGrabChargedHeavyBinding() {
		if (inputsDisabled || vanillaKeybinds == null || vanillaKeybinds.grabChargedHeavy == null) {
			return false;
		}
		ClientControlScheme controls = getActiveControlScheme();
		return hasActiveGrabChargedHeavyBinding(
				controls != null ? controls.getCurGroup() : null, vanillaKeybinds.grabChargedHeavy, inputsDisabled);
	}

	@ApiStatus.Internal
	public static boolean hasActiveGrabChargedHeavyBinding(
			@Nullable ClientControlScheme.MoveGroup controls, @Nullable KeyMapping keyMapping, boolean controlsDisabled) {
		if (controlsDisabled || controls == null || keyMapping == null || keyMapping.isUnbound()) {
			return false;
		}
		// Mapping identity avoids re-entering its conflict context during ownership checks.
		for (ClientControlScheme.Bind bind : controls.binds) {
			if (bind.input != null && bind.ability != null && bind.inputMethod != null
					&& bind.input.usesVanillaMapping(keyMapping)) {
				return true;
			}
		}
		for (Hotbar hotbar : controls.hotbars) {
			HotbarSlot selected = hotbar.getSelected();
			if (hotbar.useAbilityKey != null && selected != null
					&& !selected.binds.movesByModifier.isEmpty()
					&& hotbar.useAbilityKey.usesVanillaMapping(keyMapping)) {
				return true;
			}
			if (hotbar.switchAbilityKey != null && hotbar.switchAbilityKey.usesVanillaMapping(keyMapping)
					&& hotbar.slots.stream().anyMatch(slot -> !slot.binds.movesByModifier.isEmpty())) {
				return true;
			}
		}
		return false;
	}

	private boolean shouldPreserveStandDiscUse(boolean vanillaUseTrigger) {
		Player player = mc.player;
		return player != null && shouldPreserveStandDiscUse(
				vanillaUseTrigger,
				player.getMainHandItem().getItem() instanceof StandDiscItem,
				player.getOffhandItem().getItem() instanceof StandDiscItem);
	}

	@ApiStatus.Internal
	public static boolean shouldPreserveStandDiscUse(
			boolean vanillaUseTrigger,
			boolean mainHandStandDisc,
			boolean offHandStandDisc) {
		return vanillaUseTrigger && (mainHandStandDisc || offHandStandDisc);
	}

	private boolean shouldPreserveStoneMaskKnifeUse(
			boolean vanillaUseTrigger) {
		Player player = mc.player;
		return player != null && shouldPreserveStoneMaskKnifeUse(
				vanillaUseTrigger,
				player.getItemBySlot(EquipmentSlot.HEAD).getItem()
						instanceof StoneMaskItem,
				stoneMaskKnifeCount(player.getMainHandItem()),
				stoneMaskKnifeCount(player.getOffhandItem()));
	}

	private static int stoneMaskKnifeCount(ItemStack stack) {
		return stack.getItem() instanceof KnifeItem
				? stack.getCount()
				: 0;
	}

	@ApiStatus.Internal
	public static boolean shouldPreserveStoneMaskKnifeUse(
			boolean vanillaUseTrigger,
			boolean stoneMaskWorn,
			int mainHandKnifeCount,
			int offHandKnifeCount) {
		// Main-hand knives consume Use before vanilla can try the off hand.
		return vanillaUseTrigger && stoneMaskWorn
				&& (mainHandKnifeCount == 1
						|| mainHandKnifeCount == 0 && offHandKnifeCount == 1);
	}

	private boolean shouldPreserveUnsummonedStandVanillaUsePress(
			@Nullable ClientControlScheme controlScheme,
			boolean vanillaUseTrigger,
			KeyModifier keyModifier) {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		StandEntity playerStand = ClientGlobals.playerStandEntity;
		boolean standPowerSummonedAndUsable = standPower != null
				&& standPower.hasPower() && standPower.canUsePower()
				&& standPower.isSummoned();
		boolean liveSynchronizedStandEntity = playerStand != null
				&& standPower != null
				&& standPower.getSummonedStandEntity() == playerStand
				&& playerStand.isAlive() && !playerStand.isRemoved();
		return shouldPreserveUnsummonedStandVanillaUsePress(
				controlScheme != null
						&& controlScheme.powerClassCosmetic == PowerClass.STAND,
				vanillaUseTrigger,
				keyModifier == KeyModifier.SHIFT,
				standPowerSummonedAndUsable,
				liveSynchronizedStandEntity,
				ClientGlobals.isPlayerStandFullBodyUnsummoning());
	}

	@ApiStatus.Internal
	public static boolean shouldPreserveUnsummonedStandVanillaUsePress(
			boolean standSchemeSelected,
			boolean vanillaUseTrigger,
			boolean shiftHeld,
			boolean standPowerSummonedAndUsable,
			boolean liveSynchronizedStandEntity,
			boolean fullBodyUnsummoning) {
		boolean liveReadyStand = standPowerSummonedAndUsable
				&& liveSynchronizedStandEntity
				&& !fullBodyUnsummoning;
		return standSchemeSelected && vanillaUseTrigger && shiftHeld
				&& !liveReadyStand;
	}

	@Nullable
	private ClientControlScheme getDirectPlayerPowerControlScheme(ClientKey key, KeyModifier keyModifier) {
		if (isHamonBreathInput(key, keyModifier) && canUseDirectHamonBreathInput()) {
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			return AllControlSchemes.getForPowerType(playerPower.getPowerType());
		}
		return null;
	}

	private boolean canUseDirectHamonBreathInput() {
		PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
		return playerPower != null && playerPower.hasPower() && playerPower.canUsePower()
				&& playerPower.getPowerType() == ModPlayerPowers.HAMON.get();
	}

	private boolean isHamonBreathInput(ClientKey key, KeyModifier keyModifier) {
		return vanillaKeybinds != null && vanillaKeybinds.hamonBreath != null
				&& new ClientInputBind(vanillaKeybinds.hamonBreath).keyMatches(key, keyModifier);
	}

	public boolean isHamonBreathInputHeld() {
		return vanillaKeybinds != null && vanillaKeybinds.hamonBreath != null
				&& _heldKeys.containsKey(ClientKey.fromVanillaKeybind(vanillaKeybinds.hamonBreath));
	}

	private boolean shouldAllowUnsummonedStandHotbarInput(ClientKey key, KeyModifier keyModifier) {
		if (vanillaKeybinds == null) {
			return false;
		}
		if (
				new ClientInputBind(vanillaKeybinds.useAbility).keyMatches(key, keyModifier)
				|| new ClientInputBind(vanillaKeybinds.switchSpecial).keyMatches(key, keyModifier)
				|| !hotbarsSelection.isEmpty() && isVanillaHotbarSlotKey(key)) {
			return true;
		}
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower == null || !standPower.hasPower()) {
			return false;
		}
		ClientControlScheme controlScheme = AllControlSchemes.getForPowerType(standPower.getPowerType());
		if (controlScheme == null) {
			return false;
		}
		for (Hotbar hotbar : controlScheme.getCurGroup().hotbars) {
			if (hotbar.useAbilityKey != null && hotbar.useAbilityKey.keyMatches(key, keyModifier)) {
				return true;
			}
			if (hotbar.switchAbilityKey != null && hotbar.switchAbilityKey.keyMatches(key, keyModifier)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private ClientControlScheme getActiveControlSchemeForHotbarOperation() {
		ClientControlScheme activeControlScheme = getActiveControlScheme();
		if (activeControlScheme != null) {
			return activeControlScheme;
		}
		if (curPowerClassToggle != null) {
			return null;
		}
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower != null && standPower.hasPower() && standPower.canUsePower() && !standPower.isSummoned()) {
			return AllControlSchemes.getForPowerType(standPower.getPowerType());
		}
		return null;
	}
	
	/**
	 * Handles the direct events of keyboard/mouse inputs to trigger abilities from the player's moveset.
	 * @return true if the vanilla input should be cancelled.
	 */
	public boolean input(ClientKey key, int inputType, int modifiers) {
		boolean cancelVanilla = false;
		short keyId = key.keyId();
		
		if (shouldQueueInput()) {
			if (inputType == InputConstants.RELEASE) {
				keyReleaseEventQueue.add(new DelayedInput(key, inputType, modifiers));
			}
			return false;
		}
		
		switch (inputType) {
			case InputConstants.PRESS -> {
				KeyModifier keyModifier = getCurModifier();
				ClientControlScheme controlScheme = getActiveControlSchemeForInput(key, keyModifier);
				if (controlScheme == null) return false;
				
				if (handleDefaultJojoLmbRmb(key, controlScheme)) {
					cancelVanilla = true;
					return true;
				}
				
				boolean secondKeyInDualPress = checkDualPress(key);
				if (secondKeyInDualPress) {
					cancelVanilla = true;
					return true;
				}
				
				cancelVanilla |= hotbarPickSlot(key);
				
				CurInput input = getInputAbilitiesOnClick(controlScheme, key, keyModifier);
				@Nullable BaseAndActiveAbility heldAbility = input.heldAbility.curActiveAbility != null ? input.heldAbility : null;
				@Nullable BaseAndActiveAbility clickAbility = input.clickAbility.curActiveAbility != null ? input.clickAbility : null;
				
				cancelVanilla |= heldAbility != null || clickAbility != null;
				HeldKeyTimer heldKeyTimer = new HeldKeyTimer(key, cancelVanilla, keyModifier);
				
				int ambiguity = 0;
				if (heldAbility != null) ambiguity++;
				if (clickAbility != null) ambiguity++;
				
				AmbiguousKeyPress ambiguousKeyPress = null;
				if (ambiguity >= 2) {
					ambiguousKeyPress = isGuardClickAmbiguity(heldAbility, clickAbility)
							? new AmbiguousKeyPress(AmbiguousKeyPress.guardClickTimeAssumeHold, AmbiguousKeyPress.guardClickTimeIsHold)
							: new AmbiguousKeyPress();
					
					if (heldAbility != null) {
						Ability heldBaseAbility = heldAbility.baseAbility;
						ambiguousKeyPress.onHold = (float ticksToResolveHeld) -> {
							AvailableAbilities curAbilities = ClientPowerCache.getAvailableAbilities(heldBaseAbility.abilityId.powerClass());
							AbilityConditionCheck abilityResolved = curAbilities.getContextVariationContainer(heldBaseAbility);
							doClickInput(InputEventType.PRESS_HOLD, key, heldBaseAbility, abilityResolved, ticksToResolveHeld);
						};
					}
					
					if (clickAbility != null) {
						Ability clickBaseAbility = clickAbility.baseAbility;
						ambiguousKeyPress.onClick = (float ticksToResolveClick) -> {
							AvailableAbilities curAbilities = ClientPowerCache.getAvailableAbilities(clickBaseAbility.abilityId.powerClass());
							AbilityConditionCheck abilityResolved = curAbilities.getContextVariationContainer(clickBaseAbility);
							doClickInput(InputEventType.PRESS_CLICK, key, clickBaseAbility, abilityResolved, ticksToResolveClick);
						};
					}
				}
				
				if (ambiguousKeyPress != null) {
					heldKeyTimer.setAmbiguousInputMethod(ambiguousKeyPress);
				}
				else {
					InputMethod inputMethod = 
							heldAbility != null ? InputMethod.HOLD : 
							clickAbility != null ? InputMethod.CLICK : 
							null;
					if (inputMethod != null) {
						switch (inputMethod) {
							case HOLD -> doClickInput(InputEventType.PRESS_HOLD, key, heldAbility.baseAbility, heldAbility.curActiveAbility, 0);
							case CLICK -> doClickInput(InputEventType.PRESS_CLICK, key, clickAbility.baseAbility, input.clickAbility.curActiveAbility, 0);
						}
					}
				}
				
				putHeldKeyTimer(key, heldKeyTimer);
				
				if (heldAbility == null && clickAbility == null && mc.screen == null) {
					checkStartHotbarSelection(key, controlScheme);
				}
			}
			case InputConstants.RELEASE -> {
				HeldKeyTimer heldTicks = getHeldKeyTimer(key);
				if (heldTicks != null) {
					clickHeldOnRelease(heldTicks, keyId);
					doReleaseInput(keyId);
					removeHeldKeyTimer(key);
				}

				checkStopHotbarSelection(key);
			}
			case InputConstants.REPEAT -> {
				HeldKeyTimer heldKey = getHeldKeyTimer(key);
				cancelVanilla |= heldKey != null && heldKey.cancelVanilla;
			}
		}
		return cancelVanilla;
	}

	private static boolean isGuardClickAmbiguity(@Nullable BaseAndActiveAbility heldAbility, @Nullable BaseAndActiveAbility clickAbility) {
		// A guard in any slot counts: 1.16 keyed the guard on StandPose.BLOCK, not on a slot name.
		return heldAbility != null && clickAbility != null
				&& heldAbility.baseAbility != null
				&& ("guard".equals(heldAbility.baseAbility.name())
						|| heldAbility.baseAbility instanceof StandEntityBlockAbility);
	}
	
	private boolean handleDefaultJojoLmbRmb(ClientKey key, ClientControlScheme controlScheme) {
		if (vanillaKeybinds == null || !vanillaKeybinds.jojoLmbRmb.isDefault()
				|| controlScheme.powerClassCosmetic != PowerClass.STAND) {
			return false;
		}
		if (key.equals(LMB)) {
			return _heldKeys.containsKey(RMB) && doTheThing();
		}
		if (key.equals(RMB)) {
			return _heldKeys.containsKey(LMB) && doTheThing();
		}
		return false;
	}
	
	public boolean doTheThing() {
		ClientControlScheme controlScheme = getActiveControlScheme();
		if (controlScheme != null && controlScheme.powerClassCosmetic == PowerClass.STAND) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.0F));
			return true;
		}
		return false;
	}
	
	private final FriendlyByteBuf extraInputBuf = new FriendlyByteBuf(
			Unpooled.buffer(
					256,
					NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES));
	private void doClickInput(InputEventType type, ClientKey key, 
			Ability baseAbility, AbilityConditionCheck abilityResolved, 
			float clickHoldResolveTime) {
		Player player = mc.player;
		if (abilityResolved == null || player == null) return;
		short keyId = key.keyId();
		long inputGeneration = AbilityInput.nextInputGeneration(player, keyId);

		ConditionCheck conditionCheck = abilityResolved.conditionCheck;
		InputMethod inputMethod = type.inputMethod;
		if (conditionCheck.isPositive() || inputMethod == InputMethod.HOLD && conditionCheck.shouldContinueHold()) {
			BufferingState bufferingState = BufferingState.clickCanBuffer();
			Ability ability = abilityResolved.ability;
			lastActionKey = key;
			try {
				ability.writeExtraInput(extraInputBuf, player, true);
				AbilityInput.keyPress(
						keyId, inputGeneration, ability, player, extraInputBuf,
						inputMethod, clickHoldResolveTime, bufferingState,
						baseAbility.abilityId);
			}
			finally {
				extraInputBuf.clear();
			}
		}
		PacketDistributor.sendToServer(ClAbilityInputPacket.keyPress(keyId, inputGeneration, player, baseAbility, abilityResolved.ability, type, clickHoldResolveTime,
				ClientsideAim.playerAim.getTarget(), ClientsideAim.standAim.getTarget()));
	}
	
	private void doReleaseInput(short keyId) {
		Player player = mc.player;
		long inputGeneration = AbilityInput.keyReleaseAndGetGeneration(
				keyId, player);
		if (inputGeneration > 0L) {
			PacketDistributor.sendToServer(
					ClAbilityInputPacket.releaseHold(
							keyId, inputGeneration));
		}
	}
	
	
	// Held keys stuff
	
	public Map<ClientKey, HeldKeyTimer> _heldKeys = new HashMap<>();
	public Map<ClientKey, MutableInt> _recentlyPressed = new HashMap<>();
	
	public HeldKeyTimer getHeldKeyTimer(ClientKey key) {
		return _heldKeys.get(key);
	}
	
	public void putHeldKeyTimer(ClientKey key, HeldKeyTimer timer) {
		_heldKeys.put(key, timer);
	}
	
	public HeldKeyTimer removeHeldKeyTimer(ClientKey key) {
		HeldKeyTimer timer = _heldKeys.remove(key);
		return timer;
	}
	
	
	public void onResolvedKeyAsClick(ClientKey key) {
		_recentlyPressed.computeIfAbsent(key, __ -> new MutableInt(0)).setValue(3);
	}
	
	public boolean wasKeyClickedRecently(ClientKey key) {
		MutableInt timer = _recentlyPressed.get(key);
		return timer != null && timer.intValue() >= 0;
	}
	
	protected void tickKeyPressIndication() {
		for (MutableInt timer : _recentlyPressed.values()) {
			if (timer.intValue() >= 0) {
				timer.decrement();
			}
		}
	}
	
	
	public boolean isHeld(ClientKey key, @Nullable KeyModifier modifier) {
		HeldKeyTimer timer = getHeldKeyTimer(key);
		if (timer != null) {
			return modifier == null || timer.modifier == modifier;
		}
		return false;
	}
	
	public boolean isKeyHeld(int keyCode) {
		return isHeld(ClientKey.make(InputConstants.Type.KEYSYM, keyCode), null);
	}
	
	private void clickHeldOnRelease(HeldKeyTimer heldKeyTimer, short keyId) {
		AmbiguousKeyPress inputResolution = heldKeyTimer.getAmbiguousInputMethod();
		if (inputResolution != null) {
			AmbiguousKeyPress.Result wasItClick = inputResolution.keyReleased();
			if (wasItClick != null && wasItClick.input() == AmbiguousKeyPress.InputState.CLICK) {
				if (inputResolution.onClick != null) {
					inputResolution.onClick.handleInput(wasItClick.timeTook());
				}
				heldKeyTimer.setAmbiguousInputMethod(null);
				onResolvedKeyAsClick(heldKeyTimer.key);
			}
		}
	}
	
	private void frameUpdateHeldKeys(float tickDelta) {
		for (HeldKeyTimer timer : _heldKeys.values()) {
			AmbiguousKeyPress.Result changedState = timer.frameUpdate(tickDelta);
			if (changedState != null) {
				AmbiguousKeyPress inputResolution = timer.getAmbiguousInputMethod();
				switch (changedState.input()) {
					case ASSUME_HOLD -> {}
					case HOLD -> {
						if (inputResolution.onHold != null) {
							inputResolution.onHold.handleInput(changedState.timeTook());
						}
						timer.setAmbiguousInputMethod(null);
					}
					default -> {}
				}
			}
		}
	}
	
	private boolean checkDualPress(ClientKey pressedKey) {
		boolean result = false;
		var iter = _heldKeys.entrySet().iterator();
		while (iter.hasNext()) {
			var heldKeyEntry = iter.next();
			HeldKeyTimer timer = heldKeyEntry.getValue();
			if (!timer.isDefinitelyHold() && timer.ambiguousInputMethod.onDualKeyClick != null
					&& timer.ambiguousInputMethod.onDualKeyClick.checkHandleInput(pressedKey, timer.timeHeld)) {
				result = true;
				iter.remove();
			}
		}
		return result;
	}
	
	
	// Key modifiers (Ctrl/Shift/Alt)
	
	private List<KeyModifier> modifiersQueue = new ArrayList<>();
	
	private void addKeyModifier(Key key) {
		KeyModifier modifier = KeyModifier.getKeyModifier(key);
		if (modifier != KeyModifier.NONE && !modifiersQueue.contains(modifier)) {
			modifiersQueue.add(modifier);
		}
	}
	
	private void removeKeyModifier(Key key) {
		KeyModifier modifier = KeyModifier.getKeyModifier(key);
		if (modifier != KeyModifier.NONE) {
			modifiersQueue.remove(modifier);
		}
	}
	
	@Nonnull
	public KeyModifier getCurModifier() {
		return !modifiersQueue.isEmpty() ? modifiersQueue.get(modifiersQueue.size() - 1) : KeyModifier.NONE;
	}
	
	
	// The function that figures out what ability has the player inputed.
	
	private CurInput getInputAbilitiesOnClick(ClientControlScheme controlScheme, ClientKey key, KeyModifier keyModifier) {
		CurInput input = CurInput.instance;
		input.reset();
		
		if (controlScheme != null) {
			List<AbilityControlsEntry> heldBound = getDirectHamonBreathHoldInput(key, keyModifier);
			if (heldBound.isEmpty()) {
				heldBound = controlScheme.getBindsWithModifier(InputMethod.HOLD, key, keyModifier);
			}
			List<AbilityControlsEntry> clickBound = controlScheme.getBindsWithModifier(InputMethod.CLICK, key, keyModifier);
			
			if (!(heldBound.isEmpty() && clickBound.isEmpty())) {
				Predicate<AbilityInputState> filter = inputState -> AbilityInputState.isInputActive(inputState, PowerHud.isInContainerScreen());
				ClientControlScheme.setPrioritizedAbility(input.heldAbility, heldBound, filter);
				ClientControlScheme.setPrioritizedAbility(input.clickAbility, clickBound, filter);
			}
		}
		
		return input;
	}

	private List<AbilityControlsEntry> getDirectHamonBreathHoldInput(ClientKey key, KeyModifier keyModifier) {
		if (isHamonBreathInput(key, keyModifier) && canUseDirectHamonBreathInput()) {
			return Collections.singletonList(new AbilityControlsEntry(PowerClass.PLAYER_POWER, "hamon_breath"));
		}
		return Collections.emptyList();
	}
	
	@Nullable
	protected ClientControlScheme getCurControlScheme(Power<?> power) {
		if (power != null && power.hasPower()) {
			return AllControlSchemes.getForPowerType(power.getPowerType());
		}
		return null;
	}
	
	static class CurInput {
		private static CurInput instance = new CurInput();
		
		public final BaseAndActiveAbility heldAbility = new BaseAndActiveAbility();
		public final BaseAndActiveAbility clickAbility = new BaseAndActiveAbility();
		
		public void reset() {
			heldAbility.reset();
			clickAbility.reset();
		}
	}
	
	public static class BaseAndActiveAbility {
		public Ability baseAbility;
		public AbilityConditionCheck curActiveAbility;
		
		public void set(Ability baseAbility, AbilityConditionCheck curActiveAbility) {
			this.baseAbility = baseAbility;
			this.curActiveAbility = curActiveAbility;
		}
		
		public void reset() {
			this.baseAbility = null;
			this.curActiveAbility = null;
		}
	}
	
	
	// Hotbar stuff
	
	public Map<Hotbar, ClientKey> hotbarsSelection = new IdentityHashMap<>();
	private final List<PendingHotbarUse> pendingReleaseUse = new ArrayList<>();
	/** How long to keep waiting for the selected ability to become usable before giving up (client ticks). */
	private static final int PENDING_RELEASE_USE_TICKS = 10;
	private final Set<Hotbar> toggledHotbarsSelection = Collections.newSetFromMap(new IdentityHashMap<>());
	protected float hotbarsSelectionTimestamp;
	
	public void checkStartHotbarSelection(ClientKey pressedKey, @Nullable ClientControlScheme controlScheme) {
		if (inputsDisabled) return;
		
		if (controlScheme != null) {
			Hotbar wheelHotbar = null;
			ClientControlScheme.MoveGroup curControls = controlScheme.getCurGroup();
			for (Hotbar abilityHotbar : curControls.hotbars) {
				if (abilityHotbar.switchAbilityKey != null && abilityHotbar.switchAbilityKey.keyMatches(pressedKey, getCurModifier())) {
					boolean selected = isHotbarToggleEnabled(abilityHotbar) 
							? switchToggledHotbarControls(abilityHotbar)
							: setSelectingAbility(abilityHotbar, pressedKey, true);
					if (selected && wheelHotbar == null) wheelHotbar = abilityHotbar;
				}
			}
			if (ClientModSettings.getSettingsReadOnly().abilitySelectionWheel && wheelHotbar != null) {
				mc.setScreen(new AbilitySelectionWheel(wheelHotbar));
			}
		}
	}
	
	public void checkStopHotbarSelection(ClientKey releasedKey) {
		if (!hotbarsSelection.isEmpty()) {
			boolean releaseToUse = ClientModSettings.getSettingsReadOnly().releaseToUseAbility;
			List<Hotbar> useOnRelease = null;
			var iter = hotbarsSelection.entrySet().iterator();
			while (iter.hasNext()) {
				var entry = iter.next();
				ClientKey hotbarKey = entry.getValue();
				if (hotbarKey.equals(releasedKey)) {
					if (mc.screen instanceof AbilitySelectionWheel wheel && wheel.abilities == entry.getKey()) {
						wheel.commitHoveredSelection();
					}
					if (releaseToUse) {
						if (useOnRelease == null) useOnRelease = new ArrayList<>(1);
						useOnRelease.add(entry.getKey());
					}
					iter.remove();
				}
			}
			if (useOnRelease != null) {
				// Deferred to the next client tick on purpose. When the ability selection wheel is open the release
				// arrives while that screen is still up, and the wheel only takes itself down on its next render, so
				// firing here would be swallowed. By the next tick the selection is gone, the wheel has closed and
				// the ability sees the hotbar as no longer being browsed.
				for (Hotbar hotbar : useOnRelease) {
					pendingReleaseUse.add(new PendingHotbarUse(hotbar));
				}
			}
		}
	}
	
	/**
	 * The classic "hold the hotbar key, pick, release to use it" flow, off by default
	 * ({@link ClientModSettings.Settings#releaseToUseAbility}). Releasing the key still commits the choice as it
	 * always did, and the hotbar's own use key keeps working, so both ways are available at once.
	 *
	 * <p>This replays a real press and release of that hotbar's use key rather than resolving the ability itself, so
	 * the release goes down exactly the path a manual press takes - hold/click disambiguation, cooldowns, the
	 * client-side ability state, and whatever key the player has rebound the use key to. Resolving it by hand looked
	 * equivalent but was not: on the tick the wheel closes the selected ability is not yet flagged active, so the
	 * hand-rolled version silently resolved nothing.
	 */
	private void useSelectedHotbarAbility(Hotbar hotbar) {
		if (inputsDisabled || mc.player == null || hotbar.useAbilityKey == null) {
			return;
		}
		if (mc.screen instanceof AbilitySelectionWheel wheel && wheel.abilities == hotbar) {
			wheel.onClose();
		}
		if (mc.screen != null) {
			return;
		}
		HotbarSlot slot = hotbar.getSelected();
		ClientKey useKey = hotbar.useAbilityKey.getKey();
		if (slot == null || slot.showAbility() == null || useKey == null) {
			return;
		}
		input(useKey, InputConstants.PRESS, 0);
		input(useKey, InputConstants.RELEASE, 0);
	}
	
	public boolean hotbarScroll(double scrollDelta) {
		@Nullable AbilitySelectionWheel curWheel = mc.screen instanceof AbilitySelectionWheel w ? w : null;
		if (mc.screen != null && curWheel == null) return false;
		if (scrollDelta == 0) return false;
		// Picking an ability by scrolling is its own mechanism, separate from the selection wheel, and each can be
		// turned off on its own. Inside the wheel the scroll still moves the highlight, since there the wheel itself
		// is the mechanism the player asked for.
		if (curWheel == null && !ClientModSettings.getSettingsReadOnly().abilitySelectionScroll) return false;
		
		boolean scrolledAHotbar = false;
		ClientControlScheme controlScheme = getActiveControlSchemeForHotbarOperation();
		if (controlScheme != null) {
			ClientControlScheme.MoveGroup curControls = controlScheme.getCurGroup();
			for (Hotbar hotbar : curControls.hotbars) {
				if (isSelectingAbility(hotbar)) {
					cycleHotbarSlot(hotbar, scrollDelta > 0, curWheel);
					scrolledAHotbar = true;
				}
			}
		}
		return scrolledAHotbar;
	}
	
	private boolean cycleHotbarSlot(Hotbar hotbar, boolean backwards, @Nullable AbilitySelectionWheel curWheel) {
		if (hotbar.slots.isEmpty()) {
			return false;
		}
		
		int n = hotbar.slots.size();
		int newIndex = hotbar.slotIndex;
		for (int attempts = 0; attempts < n; attempts++) {
			if (newIndex < 0 || newIndex >= n) {
				newIndex = backwards ? n - 1 : 0;
			}
			else {
				newIndex += backwards ? -1 : 1;
				if (newIndex < 0) newIndex = n - 1;
				else if (newIndex >= n) newIndex = 0;
			}
			
			HotbarSlot slot = hotbar.slots.get(newIndex);
			if (slot.showAbility() != null) {
				if (newIndex != hotbar.slotIndex) {
					hotbar.slotIndex = newIndex;
					if (curWheel != null && curWheel.abilities == hotbar) {
						curWheel.setIgnoreMouseUntilMove(OptionalInt.of(newIndex));
					}
				}
				return true;
			}
		}
		return false;
	}
	
	public boolean hotbarPickSlot(ClientKey digitKey) {
		if (!isVanillaHotbarSlotKey(digitKey)) {
			return false;
		}
		Key vanillaKey = digitKey.getVanillaKey();
		int newIndex = -1;
		for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
			if (vanillaKey.equals(mc.options.keyHotbarSlots[i].getKey())) {
				newIndex = i;
				break;
			}
		}
		if (newIndex < 0) return false;

		boolean pickedAHotbarSlot = false;
		ClientControlScheme controlScheme = getActiveControlSchemeForHotbarOperation();
		if (controlScheme != null) {
			ClientControlScheme.MoveGroup curControls = controlScheme.getCurGroup();
			@Nullable AbilitySelectionWheel curWheel = mc.screen instanceof AbilitySelectionWheel w ? w : null;
			for (Hotbar hotbar : curControls.hotbars) {
				if (isSelectingAbility(hotbar) && newIndex < hotbar.slots.size()) {
					HotbarSlot slot = hotbar.slots.get(newIndex);
					if (slot.showAbility() != null) {
						hotbar.slotIndex = newIndex;
						if (curWheel != null && curWheel.abilities == hotbar) {
							curWheel.setIgnoreMouseUntilMove(OptionalInt.of(newIndex));
						}
					}
					
					pickedAHotbarSlot |= true;
				}
			}
		}
		return pickedAHotbarSlot;
	}

	private boolean isVanillaHotbarSlotKey(ClientKey key) {
		Key vanillaKey = key.getVanillaKey();
		for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
			if (vanillaKey.equals(mc.options.keyHotbarSlots[i].getKey())) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isSelectingAbility(Hotbar hotbar) {
		if (inputsDisabled) {
			return false;
		}
		if (hotbar.alwaysSwitchAbility() || hotbarsSelection.containsKey(hotbar)) {
			return true;
		}
		return isHotbarToggleEnabled(hotbar) && toggledHotbarsSelection.contains(hotbar)
				&& !hasHeldOtherHotbar(hotbar);
	}
	
	public boolean setSelectingAbility(Hotbar hotbar, ClientKey key, boolean selecting) {
		if (selecting) {
			if (hotbarsSelection.isEmpty()) {
				hotbarsSelectionTimestamp = ClientTickHandler.tickCount + ClientUtil.partialTick();
			}
			hotbarsSelection.put(hotbar, key);
			return true;
		}
		else {
			hotbarsSelection.remove(hotbar);
			return false;
		}
	}

	private boolean switchToggledHotbarControls(Hotbar hotbar) {
		if (toggledHotbarsSelection.remove(hotbar)) {
			return false;
		}
		if (toggledHotbarsSelection.isEmpty() && hotbarsSelection.isEmpty()) {
			hotbarsSelectionTimestamp = ClientTickHandler.tickCount + ClientUtil.partialTick();
		}
		toggledHotbarsSelection.clear();
		toggledHotbarsSelection.add(hotbar);
		return true;
	}

	private boolean hasHeldOtherHotbar(Hotbar hotbar) {
		for (Hotbar heldHotbar : hotbarsSelection.keySet()) {
			if (heldHotbar != hotbar) {
				return true;
			}
		}
		return false;
	}

	private boolean isHotbarToggleEnabled(Hotbar hotbar) {
		return false;
	}
	
	public float getHotbarsSelectionTime() {
		float time = ClientTickHandler.tickCount + ClientUtil.partialTick();
		return time - hotbarsSelectionTimestamp;
	}
	
	
	public void onUpdatedControls(ClientControlScheme.MoveGroup newControls) {
		hotbarsSelection.clear();
		toggledHotbarsSelection.clear();
	}
	
	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void playerMovementInput(MovementInputUpdateEvent event) {
		Player player = event.getEntity();
		Input input = event.getInput();
		float movementMultiplier = 1;
		
		EntityActionInstance playerAction = LivingComponentAction.getCurEntityAction(player);
		if (playerAction != null) {
			movementMultiplier *= playerAction.userWalkSpeed;
		}
		
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (stand != null) {
			float standMovementMultiplier = 1;
			EntityActionInstance standAction = LivingComponentAction.getCurEntityAction(stand);
			if (standAction != null) {
				standMovementMultiplier *= standAction.userWalkSpeed;
			}
			movementMultiplier *= stand.getUserWalkSpeed(standMovementMultiplier);
		}
		if (handleHamonMeditationInput(player, input)) {
			return;
		}
		if (player.getVehicle() instanceof LeavesGliderEntity glider) {
			glider.setInput(input.leftImpulse > 0.0F, input.leftImpulse < 0.0F);
		}
		
		if (movementMultiplier != 1) {
			input.forwardImpulse *= movementMultiplier;
			input.leftImpulse *= movementMultiplier;
			if (movementMultiplier < 1) {
				player.setSprinting(false);
			}
		}
		handleHamonDoubleShiftInput(player, input);
		handleStandLeapInput(player, input, movementMultiplier);
	}

	private boolean handleHamonMeditationInput(Player player, Input input) {
		HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null || !hamon.isMeditating()) {
			return false;
		}
		boolean hasInput = input.up || input.down || input.left || input.right || input.jumping;
		if (hamon.getMeditationTicks() >= 40 && hasInput) {
			PacketDistributor.sendToServer(new ClHamonMeditationPacket(false));
		}
		input.up = false;
		input.down = false;
		input.left = false;
		input.right = false;
		input.jumping = false;
		input.forwardImpulse = 0;
		input.leftImpulse = 0;
		return true;
	}

	private void handleHamonDoubleShiftInput(Player player, Input input) {
		int shiftPress = hamonDoubleShift.inputUpdate(input);
		if (shiftPress == 2 && canSendHamonDoubleShift(player)) {
			PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				hamon.setDoubleShiftPress(player);
			});
			PacketDistributor.sendToServer(new ClHamonDoubleShiftPressPacket());
		}
	}

	private boolean canSendHamonDoubleShift(Player player) {
		return player != null && player.isAlive() && player.onGround()
				&& PlayerPower.getPowerData(player, ModPlayerPowers.HAMON)
				.map(hamon -> hamon.isSkillLearned(ModHamonSkills.LIQUID_WALKING.get()))
				.orElse(false);
	}

	private void handleStandLeapInput(Player player, Input input, float movementMultiplier) {
		Entity playerVehicle = player.getVehicle();
		boolean onGround = player.onGround() || playerVehicle != null && playerVehicle.onGround();
		if (player.isFallFlying() || movementMultiplier < 1 || !onGround) {
			return;
		}
		if (!(player.isPassenger() || input.shiftKeyDown) || !input.jumping) {
			return;
		}
		PacketType leapPacketType = null;
		float leapStrength = 0.0F;
		PlayerPower playerPower = null;
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower != null && standPower.canLeap()) {
			leapPacketType = PacketType.STAND_LEAP;
			leapStrength = standPower.leapStrength();
		}
		else {
			playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower != null && playerPower.canLeap()) {
				leapPacketType = PacketType.PLAYER_POWER_LEAP;
				leapStrength = playerPower.leapStrength();
			}
		}
		if (leapPacketType == null || leapStrength <= 0) {
			return;
		}
		if (!player.isPassenger()) {
			input.shiftKeyDown = false;
		}
		input.jumping = false;
		if (playerPower != null) {
			playerPower.setLeapCooldown(playerPower.getLeapCooldownPeriod());
		}
		PacketDistributor.sendToServer(ClNoParamsPacket.of(leapPacketType));
		IPlayerLeap.onLeapFixWrongMovement(player);
		if (playerVehicle != null) {
			StandUtil.leap(playerVehicle, leapStrength);
		}
		else {
			StandUtil.leap(player, leapStrength);
		}
	}
	
	private static class DoubleShiftDetector {
		private boolean hasPrevInput;
		private boolean prevUp;
		private boolean prevDown;
		private boolean prevLeft;
		private boolean prevRight;
		private int shiftPresses;
		private int triggerTime;
		private boolean triggerGap;

		private int inputUpdate(Input playerInput) {
			boolean isShiftPressed = playerInput.shiftKeyDown;
			boolean trigger = false;

			if (shiftPresses > 0 && (playerInput.jumping || !checkInputUpdate(playerInput))) {
				reset();
			}
			if (triggerTime > 0 && --triggerTime == 0) {
				reset();
			}
			if (isShiftPressed) {
				trigger = triggerGap && (shiftPresses == 0 || triggerTime > 0);
				if (trigger) {
					triggerTime = 7;
					if (shiftPresses++ == 0) {
						saveInputState(playerInput);
					}
				}
			}
			triggerGap = !isShiftPressed;
			return trigger ? shiftPresses : 0;
		}

		private boolean checkInputUpdate(Input thisTick) {
			if (!hasPrevInput) {
				saveInputState(thisTick);
				return true;
			}
			return prevUp == thisTick.up && prevDown == thisTick.down
					&& prevLeft == thisTick.left && prevRight == thisTick.right;
		}

		private void saveInputState(Input input) {
			hasPrevInput = true;
			prevUp = input.up;
			prevDown = input.down;
			prevLeft = input.left;
			prevRight = input.right;
		}

		private void reset() {
			hasPrevInput = false;
			shiftPresses = 0;
			triggerTime = 0;
		}
	}
	
	
	public static final Int2ObjectMap<Direction2D> ARROW_KEYS = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
		map.put(GLFW.GLFW_KEY_LEFT,  Direction2D.LEFT);
		map.put(GLFW.GLFW_KEY_UP,	 Direction2D.UP);
		map.put(GLFW.GLFW_KEY_RIGHT, Direction2D.RIGHT);
		map.put(GLFW.GLFW_KEY_DOWN,  Direction2D.DOWN);
	});
	
	@Nullable
	public static Direction2D getArrowKey(int keyCode) {
		return ARROW_KEYS.get(keyCode);
	}


	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void fixArrowPunchKick(InteractionKeyMappingTriggered event) {
		if (event.isAttack() && !isValidPlayerAttackTarget(mc.hitResult)) {
			event.setCanceled(true); // prevents kick for "Attempting to attack an invalid entity"
			event.setSwingHand(false);
		}
	}

	public static boolean isValidPlayerAttackTarget(HitResult hitResult) {
		if (hitResult.getType() == HitResult.Type.ENTITY) {
			Entity entity = ((EntityHitResult) hitResult).getEntity();
			if (entity == Minecraft.getInstance().player || entity instanceof AbstractArrow
					/* || entity instanceof ItemEntity || entity instanceof ExperienceOrb */) {
				return false;
			}
		}
		return true;
	}
}
