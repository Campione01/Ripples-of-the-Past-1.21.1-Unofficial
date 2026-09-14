package rotp.core.impl.stands.crazydiamond.client;

import java.util.List;

import rotp.core.client.input.AbilityInputState;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletAbility;

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
