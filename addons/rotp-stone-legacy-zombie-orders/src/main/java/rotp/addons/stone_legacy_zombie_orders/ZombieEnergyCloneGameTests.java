package rotp.addons.stone_legacy_zombie_orders;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import rotp.core.api.playerpower.PlayerPowerTransitions;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZombieOrdersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieEnergyCloneGameTests {
    private ZombieEnergyCloneGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_addon_api_smoke", timeoutTicks = 20)
    public static void restoredAddonEnergyComesFromZombieDeath(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "EnergyClone");
        FakePlayer original = new FakePlayer(level, profile);
        FakePlayer replacement = new FakePlayer(level, profile);
        Cow owner = EntityType.COW.create(level);
        helper.assertTrue(owner != null, "Missing owner fixture");
        try {
            PlayerPowerType<?> speedwagon = JojoRegistries.PLAYER_POWER_TYPES_REG.get(
                    ResourceLocation.fromNamespaceAndPath("rotp_sp", "speedwagon"));
            helper.assertTrue(speedwagon != null, "This focused test requires Speedwagon");
            PlayerPower oldPower = PowerClass.PLAYER_POWER.attachGet(original);
            oldPower.setPowerType(speedwagon);
            ((PlayerPowerData) oldPower.getCurTypeData()).setCommandEnergy(original, 1000.0F);
            helper.assertTrue(((PlayerPowerData) oldPower.getCurTypeData()).getCommandEnergy() == 1000.0F,
                    "Could not prepare original addon energy");
            var snapshot = PlayerPowerTransitions.forceTemporary(
                    oldPower, ModPlayerPowers.ZOMBIE.get()).orElseThrow();
            ZombieOwnership.markConverted(original, owner, snapshot);
            oldPower.getCurTypeData(ModPlayerPowers.ZOMBIE).orElseThrow()
                    .setEnergy(original, 37.0F);

            // Rebuild from serialized state before entering the actual Clone hook.
            var powerNbt = oldPower.serializeNBT(registries).copy();
            var conversionNbt = ZombieOrdersAddon.conversion(original)
                    .serializeNBT(registries).copy();
            oldPower.deserializeNBT(registries, powerNbt);
            ZombieOrdersAddon.conversion(original).deserializeNBT(registries, conversionNbt);
            replacement.restoreFrom(original, false);

            PlayerPower restored = PlayerPower.get(replacement);
            helper.assertTrue(restored != null && restored.getPowerType() == speedwagon,
                    "Death Clone did not restore the addon power");
            helper.assertTrue(((PlayerPowerData) restored.getCurTypeData()).getCommandEnergy() == 37.0F,
                    "Death Clone kept snapshot energy instead of Zombie energy");
            var restoredNbt = restored.serializeNBT(registries).copy();
            helper.assertTrue(restoredNbt.getCompound("powersData")
                            .getCompound("rotp_sp:speedwagon")
                            .getFloat("SpeedwagonEnergy") == 37.0F,
                    "Restored energy was not persisted");
            restored.deserializeNBT(registries, restoredNbt);
            helper.assertTrue(((PlayerPowerData) restored.getCurTypeData()).getCommandEnergy() == 37.0F,
                    "Restored energy changed on NBT reload");
            helper.assertTrue(!ZombieOrdersAddon.conversion(replacement).isConverted(),
                    "Death Clone retained the temporary conversion");
            helper.succeed();
        } finally {
            original.discard();
            replacement.discard();
            if (owner != null) {
                owner.discard();
            }
        }
    }
}
