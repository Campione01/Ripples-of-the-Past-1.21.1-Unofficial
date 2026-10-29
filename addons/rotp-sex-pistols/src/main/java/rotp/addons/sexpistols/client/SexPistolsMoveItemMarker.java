package rotp.addons.sexpistols.client;

import java.util.List;

import rotp.core.client.input.AbilityInputState;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMoveSelectedAbility;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.util.TriState;

public final class SexPistolsMoveItemMarker
        extends MarkerRenderer {
    public SexPistolsMoveItemMarker(Minecraft minecraft) {
        super("sex_pistols_stand_recall", minecraft);
        renderThroughBlocks = false;
        useStandSkinColor = true;
    }

    @Override
    protected boolean shouldRender() {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        StandPower power = StandPower.get(mc.player);
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !power.isSummoned()) {
            return false;
        }
        ClientControlScheme controls =
                InputHandler.getInstance()
                        .getActiveControlScheme();
        if (controls == null) {
            return false;
        }
        for (Hotbar hotbar : controls.getCurGroup().hotbars) {
            HotbarSlot selected = hotbar.getSelected();
            if (selected == null) {
                continue;
            }
            for (InputMethod inputMethod : InputMethod.values()) {
                AbilityControlsEntry entry =
                        selected.getBinds().getFirst(
                                InputHandler.getInstance()
                                        .getCurModifier(),
                                inputMethod);
                if (entry == null
                        || !SexPistolsIds
                                .SEX_PISTOLS_MOVE_SELECTED_PATH
                                .equals(entry.abilityName())) {
                    continue;
                }
                var ability = entry.getAbility();
                return ability != null
                        && AbilityInputState.showAbilityInHUD(
                                ability, TriState.FALSE);
            }
        }
        return false;
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> markers,
            float partialTick) {
        ItemEntity lookedAt =
                SexPistolsMoveSelectedAbility.findLookedAtItem(
                        mc.player,
                        SexPistolsMoveSelectedAbility.TARGET_RANGE);
        double rangeSquared =
                SexPistolsMoveSelectedAbility.TARGET_RANGE
                        * SexPistolsMoveSelectedAbility.TARGET_RANGE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item)
                    || item.distanceToSqr(mc.player) > rangeSquared
                    || !SexPistolsMoveSelectedAbility
                            .isValidItemTarget(item)) {
                continue;
            }
            markers.add(new MarkerInstance(
                    entityMarkerPos(item, partialTick),
                    item == lookedAt));
        }
    }
}
