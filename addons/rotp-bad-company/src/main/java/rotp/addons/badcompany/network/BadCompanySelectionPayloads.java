package rotp.addons.badcompany.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.ability.BadCompanyCommandAbility.Command;
import rotp.addons.badcompany.entity.BadCompanyFormation;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import rotp.addons.badcompany.entity.BadCompanyUnitType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BadCompanySelectionPayloads {
    private static Consumer<OpenBadCompanySelectionPayload>
            clientOpenHandler = payload -> {};

    private BadCompanySelectionPayloads() {}

    public static ResourceLocation id(String path) {
        return BadCompanyAddon.id(path);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar =
                event.registrar("selection-v1");
        registrar.playToClient(
                OpenBadCompanySelectionPayload.TYPE,
                OpenBadCompanySelectionPayload.STREAM_CODEC,
                BadCompanySelectionPayloads::handleOpen);
        registrar.playToServer(
                SetBadCompanySelectionPayload.TYPE,
                SetBadCompanySelectionPayload.STREAM_CODEC,
                BadCompanySelectionPayloads::handleSelection);
    }

    public static void installClientOpenHandler(
            Consumer<OpenBadCompanySelectionPayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static void open(
            ServerPlayer player,
            BadCompanyStandEntity coordinator,
            Command command) {
        StandPower power = StandPower.get(player);
        if (power == null
                || !coordinator.isActiveFor(player)) {
            return;
        }
        BadCompanySelectionKind kind;
        int selected;
        if (command == Command.SELECT_UNITS) {
            kind = BadCompanySelectionKind.UNIT;
            selected =
                    coordinator.selectedUnitType().ordinal();
        } else if (command == Command.SELECT_FORMATION) {
            kind = BadCompanySelectionKind.FORMATION;
            selected = coordinator.formation().ordinal();
        } else {
            return;
        }
        int optionCount = allowedOptionCount(
                kind, power.getResolveLevel());
        if (optionCount <= 0) {
            return;
        }
        selected = Math.min(selected, optionCount - 1);
        PacketDistributor.sendToPlayer(
                player,
                new OpenBadCompanySelectionPayload(
                        kind.networkId(),
                        selected,
                        optionCount));
    }

    private static void handleOpen(
            OpenBadCompanySelectionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(
                () -> clientOpenHandler.accept(payload));
    }

    private static void handleSelection(
            SetBadCompanySelectionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player()
                    instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null
                    || power.getPowerType()
                            != BadCompanyAddon.BAD_COMPANY.get()
                    || !(power.getSummonedStandEntity()
                            instanceof BadCompanyStandEntity
                                    coordinator)
                    || !coordinator.isActiveFor(player)) {
                return;
            }
            BadCompanySelectionKind.fromNetworkId(
                            payload.kind())
                    .ifPresent(kind -> applySelection(
                            coordinator,
                            player,
                            power.getResolveLevel(),
                            kind,
                            payload.selected()));
        });
    }

    private static void applySelection(
            BadCompanyStandEntity coordinator,
            ServerPlayer player,
            int resolveLevel,
            BadCompanySelectionKind kind,
            int selected) {
        int optionCount = allowedOptionCount(
                kind, resolveLevel);
        if (selected < 0 || selected >= optionCount) {
            return;
        }
        if (kind == BadCompanySelectionKind.UNIT) {
            BadCompanyUnitType[] values =
                    BadCompanyUnitType.values();
            coordinator.selectUnitType(
                    player, values[selected]);
            return;
        }
        BadCompanyFormation[] values =
                BadCompanyFormation.values();
        coordinator.selectFormation(
                player, values[selected]);
    }

    private static int allowedOptionCount(
            BadCompanySelectionKind kind,
            int resolveLevel) {
        return switch (kind) {
            case UNIT -> resolveLevel < 2
                    ? 0
                    : resolveLevel >= 3
                            ? BadCompanyUnitType.values().length
                            : BadCompanyUnitType.values().length - 1;
            case FORMATION -> resolveLevel >= 1
                    ? BadCompanyFormation.values().length
                    : 0;
        };
    }
}
