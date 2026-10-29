package rotp.addons.frosttraveler.item;

import java.util.Optional;

import com.mojang.authlib.properties.PropertyMap;

import rotp.core.JojoModConfig;
import rotp.core.mechanics.JojoDefinitions;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class MeatMaskItem extends Item {
    public MeatMaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // Creative interaction supplies a copy; persist on the actual hand stack.
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(this)) {
            return InteractionResult.FAIL;
        }
        DisguiseData old = held.get(
                FrostTravelerAddon.DISGUISE_DATA);
        if (old == null
                || !old.ownerUuid().equals(player.getUUID())
                || target == player) {
            return InteractionResult.FAIL;
        }
        capture(held, target, old.ownerUuid());
        return InteractionResult.CONSUME;
    }

    public static boolean capture(
            ItemStack stack,
            LivingEntity target,
            java.util.UUID ownerUuid) {
        if (stack.isEmpty()
                || !stack.is(FrostTravelerAddon.MEAT_DGS_ITEM.get())
                || target == null
                || !target.isAlive()) {
            return false;
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE
                .getKey(target.getType());
        boolean hostile = target instanceof Player player
                ? JojoDefinitions.isPlayerJojoVampiric(player)
                        && !JojoModConfig
                                .getCommonConfigInstance(false)
                                .vampiresAggroMobs
                                .get()
                : target.getType().getCategory()
                        == MobCategory.MONSTER;
        Optional<ResolvableProfile> capturedProfile = Optional.empty();
        if (target instanceof Player targetPlayer) {
            var profile = targetPlayer.getGameProfile();
            PropertyMap properties = new PropertyMap();
            properties.putAll(profile.getProperties());
            capturedProfile = Optional.of(new ResolvableProfile(
                    Optional.ofNullable(profile.getName()),
                    Optional.of(profile.getId()),
                    properties));
        }
        stack.set(
                FrostTravelerAddon.DISGUISE_DATA,
                new DisguiseData(
                        ownerUuid,
                        target instanceof Player
                                ? Optional.empty()
                                : Optional.of(type),
                        target instanceof Player
                                ? Optional.of(target.getUUID())
                                : Optional.empty(),
                        target.getName().getString(),
                        hostile,
                        capturedProfile));
        return true;
    }
}
