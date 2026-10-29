package rotp.addons.sexpistols.client;

import java.util.Optional;

import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

final class SexPistolsClientTextures {
    private static final ResourceLocation BASE_MEMBER_ICON =
            memberIconPath(1);

    private SexPistolsClientTextures() {}

    static ResolvedTexture memberIcon(
            Optional<ResourceLocation> selectedSkin,
            int memberNumber) {
        ResourceLocation requested = memberIconPath(memberNumber);
        StandSkinsLoader loader = StandSkinsLoader.getInstance();
        StandSkin skin = loader != null
                ? loader.getSkinFromId(
                        SexPistolsIds.SEX_PISTOLS,
                        selectedSkin != null
                                ? selectedSkin
                                : Optional.empty())
                : null;
        if (skin == null) {
            return new ResolvedTexture(requested, false);
        }

        ResourceLocation remapped = StandSkinsLoader.remap(
                requested, skin.skinId);
        if (exists(remapped)) {
            return new ResolvedTexture(remapped, true);
        }
        ResourceLocation remappedBase = StandSkinsLoader.remap(
                BASE_MEMBER_ICON, skin.skinId);
        if (exists(remappedBase)) {
            return new ResolvedTexture(remappedBase, true);
        }
        return new ResolvedTexture(requested, false);
    }

    private static ResourceLocation memberIconPath(int memberNumber) {
        return SexPistolsIds.id(
                "textures/gui/sex_pistols_no_"
                        + memberNumber
                        + ".png");
    }

    private static boolean exists(ResourceLocation texture) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .isPresent();
    }

    record ResolvedTexture(
            ResourceLocation texture, boolean remapped) {}
}
