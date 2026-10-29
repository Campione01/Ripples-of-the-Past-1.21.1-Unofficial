package rotp.addons.heavensdoor.client;

import java.util.ArrayList;
import java.util.List;

import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.network.BookCommandIntentPayload;
import rotp.addons.heavensdoor.network.BookPlayerOption;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.state.BookStateRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HeavensDoorPlayerSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 7;

    private final OpenBookSnapshotPayload snapshot;
    private final List<Button> optionButtons = new ArrayList<>();
    private Button previous;
    private Button next;
    private int page;
    private boolean intentSent;

    public HeavensDoorPlayerSelectionScreen(
            OpenBookSnapshotPayload snapshot) {
        super(Component.translatable(
                "gui.rotp_hd.player_selection.title"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        super.init();
        previous = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> {
                            page = Math.max(0, page - 1);
                            rebuildOptions();
                        })
                .bounds(width / 2 - 110, height - 32, 24, 20)
                .build());
        next = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> {
                            page++;
                            rebuildOptions();
                        })
                .bounds(width / 2 + 86, height - 32, 24, 20)
                .build());
        rebuildOptions();
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null
                || minecraft.player == null
                || minecraft.level == null) {
            onClose();
            return;
        }
        Entity entity = minecraft.level.getEntity(
                snapshot.entityId());
        LivingEntity target = entity instanceof LivingEntity living ? living : null;
        if (!BookScreenSessionRules.shouldRemainOpen(
                minecraft.level.getGameTime(),
                snapshot.sessionExpiresAt(),
                target != null,
                target != null && target.isAlive(),
                target != null ? minecraft.player.distanceToSqr(target) : Double.POSITIVE_INFINITY,
                BookStateRules.ACTION_RANGE)) {
            onClose();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font, title, width / 2, 16, 0xFFFFFF);
        if (snapshot.playerOptions().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.rotp_hd.player_selection.empty"),
                    width / 2,
                    48,
                    0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!intentSent) {
            intentSent = true;
            PacketDistributor.sendToServer(
                    new BookCommandIntentPayload(
                            snapshot.sessionNonce(),
                            BookCommand.CLOSE));
        }
        super.onClose();
    }

    private void rebuildOptions() {
        optionButtons.forEach(this::removeWidget);
        optionButtons.clear();
        int maxPage = Math.max(
                0,
                (snapshot.playerOptions().size() - 1)
                        / PAGE_SIZE);
        page = Math.min(page, maxPage);
        int from = page * PAGE_SIZE;
        int to = Math.min(
                from + PAGE_SIZE,
                snapshot.playerOptions().size());
        int buttonWidth = Math.min(300, width - 40);
        for (int index = from; index < to; index++) {
            BookPlayerOption option =
                    snapshot.playerOptions().get(index);
            int y = 42 + (index - from) * 24;
            Button button = Button.builder(
                            Component.literal(
                                    option.displayName()),
                            ignored -> select(option))
                    .bounds(
                            (width - buttonWidth) / 2,
                            y,
                            buttonWidth,
                            20)
                    .build();
            optionButtons.add(addRenderableWidget(button));
        }
        previous.active = page > 0;
        next.active = page < maxPage;
    }

    public void closeFromServer(int entityId) {
        if (snapshot.entityId() == entityId) {
            intentSent = true;
            super.onClose();
        }
    }

    private void select(BookPlayerOption option) {
        if (intentSent) {
            return;
        }
        intentSent = true;
        PacketDistributor.sendToServer(
                new BookCommandIntentPayload(
                        snapshot.sessionNonce(),
                        BookCommand.CANNOT_ATTACK_SELECTED_PLAYER,
                        option.optionId()));
        super.onClose();
    }
}
