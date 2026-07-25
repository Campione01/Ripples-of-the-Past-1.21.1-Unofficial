package com.github.standobyte.jojoimpl.stands.crazydiamond.client;

import java.util.List;

import com.github.standobyte.jojo.client.input.AbilityInputState;
import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.Hotbar;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBlockBulletAbility;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.util.TriState;

public class CrazyDBloodHomingMarker extends MarkerRenderer {
	private static final String BLOCK_BULLET_ABILITY = "block_bullet";

	public CrazyDBloodHomingMarker(Minecraft mc) {
		super(JojoMod.resLoc("textures/icons/blood_drops.png"), mc);
		renderThroughBlocks = false;
		useStandSkinColor = true;
	}

	@Override
	protected boolean shouldRender() {
		if (mc.player == null || mc.player.isShiftKeyDown()) {
			return false;
		}
		ClientControlScheme controlScheme = InputHandler.getInstance().getActiveControlScheme();
		if (controlScheme == null) {
			return false;
		}
		for (Hotbar hotbar : controlScheme.getCurGroup().hotbars) {
			HotbarSlot selectedSlot = hotbar.getSelected();
			if (selectedSlot == null) {
				continue;
			}
			for (InputMethod inputMethod : InputMethod.values()) {
				AbilityControlsEntry abilityEntry = selectedSlot.getBinds().getFirst(InputHandler.getInstance().getCurModifier(), inputMethod);
				if (abilityEntry != null && BLOCK_BULLET_ABILITY.equals(abilityEntry.abilityName())) {
					AbilityConditionCheck ability = abilityEntry.getAbility();
					return ability != null && AbilityInputState.showAbilityInHUD(ability, TriState.FALSE);
				}
			}
		}
		return false;
	}

	@Override
	protected void updatePositions(List<MarkerInstance> list, float partialTick) {
		fillWithStandEffectTargets(list, partialTick, ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get(), CrazyDBlockBulletAbility.PLAYER_TRACKING_RANGE, mc, true);
	}
}
