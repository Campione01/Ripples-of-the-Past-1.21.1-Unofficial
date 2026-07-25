package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.client.ui.screen_jojomenu.GoldExperienceChooseLifeformScreen;
import com.github.standobyte.jojo.powersystem.ability.ProgressionSkipHandler;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class GoldExperienceChooseLifeformAbility extends GoldExperienceUtilityAbility implements ProgressionSkipHandler {

    public GoldExperienceChooseLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            GoldExperienceChooseLifeformScreen.openWindowOnClick();
        }
        else if (user instanceof ServerPlayer player) {
            GoldExperienceLifeformState.get(player).syncToPlayer(player);
        }
    }

    @Override
    public void onProgressionSkipped(StandPower power) {
        LivingEntity user = power.getUser();
        if (user instanceof ServerPlayer player) {
            GoldExperienceLifeformState state = GoldExperienceLifeformState.get(player);
            state.learnAllValidLifeforms(player.level());
            state.syncToPlayer(player);
        }
    }
}
