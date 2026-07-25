package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.client.input.AbilityInputState;
import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.input.controlscheme.ClientKey;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.Hotbar;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme.InputsByKeyModifier;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.client.ui.utils.GuiIcon;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojoimpl.stands.goldexperience.GECreatedLifeformEffect;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.TriState;

public class GoldExperienceLifeformRevertMarker extends MarkerRenderer {
    private static final String REVERT_LIFEFORM_ABILITY = "revert_lifeform";
    private static final GuiIcon ICON_TOOTH = new GuiIcon(JojoMod.resLoc("textures/icons/tooth.png"), 16, 16);

    public GoldExperienceLifeformRevertMarker(Minecraft mc) {
        super(JojoMod.resLoc("textures/icons/soul_cloud.png"), mc);
        renderThroughBlocks = false;
        useStandSkinColor = true;
    }

    @Override
    protected boolean shouldRender() {
        return shouldShowRevertMarkers();
	}

    static boolean shouldShowRevertMarkers() {
        ClientControlScheme controlScheme = InputHandler.getInstance().getActiveControlScheme();
        if (controlScheme == null) {
            return false;
        }
        KeyModifier modifier = InputHandler.getInstance().getCurModifier();
        ClientControlScheme.MoveGroup curGroup = controlScheme.getCurGroup();
        for (Hotbar hotbar : curGroup.hotbars) {
            HotbarSlot selectedSlot = hotbar.getSelected();
            if (selectedSlot != null && hasVisibleRevertBind(selectedSlot.getBinds(), modifier)) {
                return true;
            }
        }
        for (Map.Entry<ClientKey, InputsByKeyModifier> bindEntry : curGroup.getBinds().entrySet()) {
            if (hasVisibleRevertBind(bindEntry.getValue(), modifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVisibleRevertBind(InputsByKeyModifier binds, KeyModifier modifier) {
        for (InputMethod inputMethod : InputMethod.values()) {
            AbilityControlsEntry abilityEntry = binds.getFirst(modifier, inputMethod);
            if (abilityEntry != null && REVERT_LIFEFORM_ABILITY.equals(abilityEntry.abilityName())) {
                AbilityConditionCheck ability = abilityEntry.getAbility();
                return ability != null && AbilityInputState.showAbilityInHUD(ability, TriState.FALSE);
            }
        }
        return false;
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        GoldExperienceLifeformMarker.updateGELifeformMarkers(list, partialTick, mc, true);
    }

    @Override
    protected void renderIcon(PoseStack poseStack, MarkerInstance marker, float partialTick, StandSkin standSkin) {
        getStandEffect(marker)
                .filter(GECreatedLifeformEffect.class::isInstance)
                .map(GECreatedLifeformEffect.class::cast)
                .ifPresentOrElse(effect -> {
                    var item = effect.getItemView();
                    if (!item.isEmpty()) {
                        renderItem(poseStack, item, partialTick);
                    }
                    else if (effect.isToothSource()) {
                        ICON_TOOTH.render(poseStack, 0, 0);
                    }
                    else {
                        super.renderIcon(poseStack, marker, partialTick, standSkin);
                    }
                }, () -> super.renderIcon(poseStack, marker, partialTick, standSkin));
    }
}
