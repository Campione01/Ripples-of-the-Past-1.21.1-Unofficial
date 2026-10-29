package rotp.addons.hermitpurple;

import java.util.Optional;

import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class HermitPurpleSkins {
    private HermitPurpleSkins() {}

    public static int color(
            Optional<ResourceLocation> standSkin) {
        String path = standSkin
                .map(ResourceLocation::getPath)
                .orElse("");
        return switch (path) {
            case "skina" -> 0xB20000;
            case "skinb" -> 0x78A392;
            case "skinc" -> 0x7CD3E1;
            case "skind" -> 0x62506B;
            case "skine" -> 0x9A66DF;
            case "skinf" -> 0xD3A656;
            case "sking" -> 0x05B705;
            case "skinh" -> 0x262626;
            case "passion", "jonathan_passion", "jonathan" ->
                    0xF070D0;
            case "spider_man" -> 0xC91A09;
            default -> HermitPurpleSpec.MAP_COLOR;
        };
    }

    // 1.16 hp_grab .shout(USER_THROW): an action shout (sneak rule)
    public static void playThrowVoice(
            EntityActionType ability,
            LivingEntity user,
            HermitPurpleStandEntity controller) {
        Ability.sayShoutOf(
                ability,
                user,
                isJonathan(controller)
                        ? HermitPurpleAddon.JONATHAN_THROW
                        : HermitPurpleAddon.JOSEPH_THROW);
    }

    public static void playChargedVoice(
            LivingEntity user,
            HermitPurpleStandEntity controller) {
        JojoModUtil.sayVoiceLine(
                user,
                isJonathan(controller)
                        ? HermitPurpleAddon.JONATHAN_ATTACK.get()
                        : HermitPurpleAddon.JOSEPH_OD.get());
    }

    // 1.16 hp_grab_od / hp_grab_sc .shout(USER_OVER_DRIVE) (sneak rule)
    public static void playOverdriveVoice(
            EntityActionType ability,
            LivingEntity user,
            HermitPurpleStandEntity controller) {
        Ability.sayShoutOf(
                ability,
                user,
                isJonathan(controller)
                        ? HermitPurpleAddon.JONATHAN_OVERDRIVE
                        : HermitPurpleAddon.JOSEPH_OVERDRIVE);
    }

    public static void playWebSound(
            HermitPurpleStandEntity controller) {
        if (isSkin(controller, "spider_man")) {
            StandUtil.playStandEntitySound(
                    controller,
                    HermitPurpleAddon.WEB_SLING.get(),
                    1.0F,
                    1.0F);
        }
    }

    public static boolean isJonathan(
            HermitPurpleStandEntity controller) {
        return isSkin(controller, "jonathan")
                || isSkin(controller, "jonathan_passion");
    }

    private static boolean isSkin(
            HermitPurpleStandEntity controller,
            String path) {
        return controller != null
                && controller.getStandSkin()
                        .map(ResourceLocation::getPath)
                        .filter(path::equals)
                        .isPresent();
    }
}
