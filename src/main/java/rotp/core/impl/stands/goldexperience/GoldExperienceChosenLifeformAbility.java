package rotp.core.impl.stands.goldexperience;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.impl.stands.goldexperience.client.EntityTypeIcon;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.util.mc.entitysubtype.EntitySubtype;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/*
 * 1.16 GoldExperienceCreateLifeform.getTranslatedName and renderActionIcon: the ability carries the name and the icon
 * of the chosen lifeform. They live apart from GoldExperienceCreateLifeformAbility because renderAbilityIcon names
 * client classes in its signature, and reading that class's methods reflectively on a dedicated server would load them.
 */
public abstract class GoldExperienceChosenLifeformAbility extends GoldExperienceUtilityAbility {

    protected GoldExperienceChosenLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public Component getName(Power<?> context) {
        return chosenLifeform(context)
                .map(lifeform -> abilityName(context, ".param", lifeform.getDescription()))
                .orElseGet(() -> super.getName(context));
    }

    @Override
    public void renderAbilityIcon(Power<?> context, GuiGraphics guiGraphics, TextureAtlasSprite sprite, float x, float y, int color) {
        Optional<EntitySubtype<?>> lifeform = chosenLifeform(context);
        if (lifeform.isPresent()) {
            EntityTypeIcon.renderIcon(lifeform.get(), guiGraphics.pose(), x, y, color);
        }
        else {
            super.renderAbilityIcon(context, guiGraphics, sprite, x, y, color);
        }
    }

    private static Optional<EntitySubtype<?>> chosenLifeform(@Nullable Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        return user != null ? GoldExperienceLifeformState.get(user).selectedLifeformSubtype(user.level()) : Optional.empty();
    }
}
