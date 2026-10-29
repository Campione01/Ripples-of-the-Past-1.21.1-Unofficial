package rotp.addons.hermitpurple.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import rotp.core.core.JojoRegistries;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HermitTargetPayloads {
    private static Consumer<OpenHermitTargetPayload>
            clientOpenHandler = payload -> {};

    private HermitTargetPayloads() {}

    public static ResourceLocation id(String path) {
        return HermitPurpleAddon.id(path);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenHermitTargetPayload.TYPE,
                OpenHermitTargetPayload.STREAM_CODEC,
                HermitTargetPayloads::handleOpen);
        registrar.playToServer(
                SelectHermitTargetPayload.TYPE,
                SelectHermitTargetPayload.STREAM_CODEC,
                HermitTargetPayloads::handleSelection);
    }

    public static void installClientOpenHandler(
            Consumer<OpenHermitTargetPayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static OpenHermitTargetPayload createOpenPayload(
            ServerPlayer player) {
        List<HermitTargetEntry> entries = new ArrayList<>();
        entries.add(new HermitTargetEntry(
                HermitPurpleSpec.TARGET_MODE_RANDOM,
                "random",
                "Random powerful entity"));
        player.serverLevel().players().stream()
                .filter(candidate -> candidate != player)
                .map(candidate -> candidate.getGameProfile().getName())
                .sorted()
                .forEach(name -> entries.add(new HermitTargetEntry(
                        HermitPurpleSpec.TARGET_MODE_PLAYER,
                        name,
                        "Player: " + name)));
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .map(entry -> entry.getKey().location())
                .sorted(Comparator.comparing(
                        ResourceLocation::toString))
                .forEach(id -> entries.add(new HermitTargetEntry(
                        HermitPurpleSpec.TARGET_MODE_ENTITY,
                        id.toString(),
                        "Entity: " + id)));
        JojoRegistries.DEFAULT_STANDS_REG.entrySet().stream()
                .map(entry -> entry.getKey().location())
                .sorted(Comparator.comparing(
                        ResourceLocation::toString))
                .forEach(id -> entries.add(new HermitTargetEntry(
                        HermitPurpleSpec.TARGET_MODE_STAND,
                        id.toString(),
                        "Stand user: " + id)));
        dynamicIds(
                player,
                Registries.STRUCTURE,
                "Structure: ",
                HermitPurpleSpec.TARGET_MODE_STRUCTURE,
                entries);
        dynamicIds(
                player,
                Registries.BIOME,
                "Biome: ",
                HermitPurpleSpec.TARGET_MODE_BIOME,
                entries);
        return new OpenHermitTargetPayload(entries);
    }

    private static <T> void dynamicIds(
            ServerPlayer player,
            net.minecraft.resources.ResourceKey<
                    ? extends Registry<T>> registryKey,
            String prefix,
            int mode,
            List<HermitTargetEntry> entries) {
        player.registryAccess()
                .registryOrThrow(registryKey)
                .keySet()
                .stream()
                .sorted(Comparator.comparing(
                        ResourceLocation::toString))
                .forEach(id -> entries.add(new HermitTargetEntry(
                        mode, id.toString(), prefix + id)));
    }

    private static void handleOpen(
            OpenHermitTargetPayload payload,
            IPayloadContext context) {
        clientOpenHandler.accept(payload);
    }

    private static void handleSelection(
            SelectHermitTargetPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        HermitPurpleStandEntity controller =
                HermitPurpleAddon.controller(player);
        if (controller != null
                && valid(
                        player,
                        payload.mode(),
                        payload.target())) {
            controller.setTargetSelection(
                    payload.mode(), payload.target());
        }
    }

    public static boolean valid(
            ServerPlayer player, int mode, String target) {
        if (mode == HermitPurpleSpec.TARGET_MODE_RANDOM) {
            return "random".equals(target);
        }
        if (mode == HermitPurpleSpec.TARGET_MODE_PLAYER) {
            return player.serverLevel().players().stream()
                    .anyMatch(candidate ->
                            candidate.getGameProfile()
                                    .getName()
                                    .equals(target));
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) {
            return false;
        }
        return switch (mode) {
            case HermitPurpleSpec.TARGET_MODE_ENTITY ->
                    BuiltInRegistries.ENTITY_TYPE
                            .getOptional(id)
                            .map(type -> id.equals(
                                    EntityType.getKey(type)))
                            .orElse(false);
            case HermitPurpleSpec.TARGET_MODE_STAND ->
                    JojoRegistries.DEFAULT_STANDS_REG
                            .containsKey(id);
            case HermitPurpleSpec.TARGET_MODE_STRUCTURE ->
                    dynamicContains(
                            player, Registries.STRUCTURE, id);
            case HermitPurpleSpec.TARGET_MODE_BIOME ->
                    dynamicContains(
                            player, Registries.BIOME, id);
            default -> false;
        };
    }

    private static <T> boolean dynamicContains(
            ServerPlayer player,
            net.minecraft.resources.ResourceKey<
                    ? extends Registry<T>> registryKey,
            ResourceLocation id) {
        return player.registryAccess()
                .registryOrThrow(registryKey)
                .containsKey(id);
    }
}
