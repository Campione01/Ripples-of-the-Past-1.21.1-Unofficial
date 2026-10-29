package rotp.addons.d4c.client;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;

final class D4CPlayerSkinResolver {
    private D4CPlayerSkinResolver() {}

    static PlayerSkin resolve(UUID profileId, String profileName) {
        String name = profileName == null || profileName.isBlank()
                ? "D4C"
                : profileName;
        if (profileId == null) {
            profileId = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name)
                            .getBytes(StandardCharsets.UTF_8));
        }
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var playerInfo = connection != null
                ? connection.getPlayerInfo(profileId)
                : null;
        if (playerInfo != null) {
            return playerInfo.getSkin();
        }
        return minecraft.getSkinManager().getInsecureSkin(
                new GameProfile(profileId, name));
    }
}
