package rotp.addons.heavensdoor.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.network.BookCommandIntentPayload;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.state.BookStateRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HeavensDoorBookScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            HeavensDoorAddon.id("textures/gui/book_gui.png");
    private static final int PAGE_COLOR = 0xFFE2E8F9;
    private static final int INK_COLOR = 0xFF3D3027;
    private static final int SEPARATOR_COLOR = 0xFF25252B;

    private final OpenBookSnapshotPayload snapshot;
    private boolean intentSent;

    public HeavensDoorBookScreen(
            OpenBookSnapshotPayload snapshot) {
        super(Component.translatable("gui.rotp_hd.book.title"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        super.init();
        addCommandButton(
                BookCommand.FLY_BACK,
                "gui.rotp_hd.command.fly_backwards",
                HeavensDoorBookLayout.RIGHT_COMMANDS.get(0));
        addCommandButton(
                BookCommand.BLINDNESS_TOGGLE,
                snapshot.blindnessActive()
                        ? "gui.rotp_hd.command.no_vanish_nearby"
                        : "gui.rotp_hd.command.vanish_nearby",
                HeavensDoorBookLayout.RIGHT_COMMANDS.get(1));
        addCommandButton(
                BookCommand.FREEZE,
                "gui.rotp_hd.command.freeze_text",
                HeavensDoorBookLayout.RIGHT_COMMANDS.get(2));
        addCommandButton(
                BookCommand.SELF_HARM_TOGGLE,
                snapshot.selfHarmActive()
                        ? "gui.rotp_hd.command.no_kys"
                        : "gui.rotp_hd.command.kys",
                HeavensDoorBookLayout.RIGHT_COMMANDS.get(3));
        addCommandButton(
                BookCommand.CANNOT_ATTACK_SELECTED_PLAYER,
                "gui.rotp_hd.command.cant_attack_player",
                HeavensDoorBookLayout.LEFT_COMMANDS.get(0));
        addCommandButton(
                BookCommand.MOB_BEHAVIOR_TOGGLE,
                "gui.rotp_hd.command.behavior",
                HeavensDoorBookLayout.LEFT_COMMANDS.get(1));
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
        LivingEntity target =
                entity instanceof LivingEntity living
                        ? living
                        : null;
        // The server-issued session is authoritative; effect sync may
        // legitimately arrive after the screen-open payload.
        if (!BookScreenSessionRules.shouldRemainOpen(
                minecraft.level.getGameTime(),
                snapshot.sessionExpiresAt(),
                target != null,
                target != null && target.isAlive(),
                target != null
                        ? minecraft.player.distanceToSqr(target)
                        : Double.POSITIVE_INFINITY,
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
        int left = bookLeft();
        int top = bookTop();
        BookDrawState drawState = new BookDrawState();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            graphics.blit(
                TEXTURE,
                left,
                top,
                0,
                0,
                HeavensDoorBookLayout.IMAGE_WIDTH,
                HeavensDoorBookLayout.IMAGE_HEIGHT,
                HeavensDoorBookLayout.IMAGE_WIDTH,
                HeavensDoorBookLayout.IMAGE_HEIGHT);
        } finally {
            RenderSystem.disableBlend();
        }

        drawPageInterior(graphics, left, top);
        draw(graphics, left, top, drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(0),
                "name", snapshot.name());
        draw(graphics, left, top, drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(1),
                "entity", Component.translatable(
                        "gui.rotp_hd.book.entity",
                        snapshot.entityTypeName()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(2),
                "health",
                String.format(
                        java.util.Locale.ROOT,
                        "%.1f / %.1f",
                        snapshot.health(),
                        snapshot.maxHealth()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(3),
                "age",
                Integer.toString(snapshot.ageSeconds()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(4),
                "dimensions",
                String.format(
                        java.util.Locale.ROOT,
                        "%.2f x %.2f",
                        snapshot.width(),
                        snapshot.height()));
        // Full server-built line: owner, Stand user, player or empty.
        draw(graphics, left, top, drawState,
                HeavensDoorBookLayout.LEFT_FIELDS.get(5),
                "owner", snapshot.ownerLine());

        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.RIGHT_FIELDS.get(0),
                "stand",
                Component.translatable(
                        "gui.rotp_hd.book.stand",
                        snapshot.standName()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.RIGHT_FIELDS.get(1),
                "player_power",
                Component.translatable(
                        "gui.rotp_hd.book.player_power",
                        snapshot.playerPowerName()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.RIGHT_FIELDS.get(2),
                "pages",
                Integer.toString(snapshot.pageCount()));
        draw(
                graphics,
                left,
                top,
                drawState,
                HeavensDoorBookLayout.RIGHT_FIELDS.get(3),
                "remaining",
                Long.toString(snapshot.remainingTicks()));
        for (Renderable renderable : renderables) {
            renderable.render(
                    graphics, mouseX, mouseY, partialTick);
        }
        HeavensDoorBookRenderDiagnostics.recordScreenDraw(
                this,
                snapshot,
                left,
                top,
                width,
                height,
                TEXTURE,
                PAGE_COLOR,
                INK_COLOR,
                SEPARATOR_COLOR,
                HeavensDoorBookLayout.LEFT_PAGE_FILL,
                HeavensDoorBookLayout.RIGHT_PAGE_FILL,
                drawState.fields(),
                renderables.size());
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

    public void closeFromServer(int entityId) {
        if (snapshot.entityId() == entityId) {
            intentSent = true;
            super.onClose();
        }
    }

    private void addCommandButton(
            BookCommand command,
            String translationKey,
            HeavensDoorBookLayout.Area area) {
        if (!command.permittedBy(
                snapshot.availableCommandMask())) {
            return;
        }
        int left = bookLeft();
        int top = bookTop();
        addRenderableWidget(new BookCommandButton(
                left + area.x(),
                top + area.y(),
                area.width(),
                area.height(),
                Component.translatable(translationKey),
                button -> {
                    if (command
                            == BookCommand
                                    .CANNOT_ATTACK_SELECTED_PLAYER) {
                        openPlayerSelection();
                    }
                    else {
                        submit(command);
                    }
                }));
    }

    private void openPlayerSelection() {
        if (intentSent || minecraft == null) {
            return;
        }
        minecraft.setScreen(
                new HeavensDoorPlayerSelectionScreen(snapshot));
    }

    private void submit(BookCommand command) {
        if (intentSent) {
            return;
        }
        intentSent = true;
        PacketDistributor.sendToServer(
                new BookCommandIntentPayload(
                        snapshot.sessionNonce(), command));
        super.onClose();
    }

    private void draw(
            GuiGraphics graphics,
            int left,
            int top,
            BookDrawState drawState,
            HeavensDoorBookLayout.Area area,
            String key,
            String value) {
        draw(graphics, left, top, drawState, area, key,
                Component.translatable(
                        "gui.rotp_hd.book." + key, value));
    }

    private void draw(
            GuiGraphics graphics,
            int left,
            int top,
            BookDrawState drawState,
            HeavensDoorBookLayout.Area area,
            String key,
            Component message) {
        String text = message.getString();
        int textWidth = area.width() - 4;
        int maxLines = Math.max(
                1, (area.height() - 2) / font.lineHeight);
        List<String> lines =
                wrapText(font, text, textWidth, maxLines);
        int x = left + area.x();
        int y = top + area.y();
        graphics.enableScissor(
                x, y, x + area.width(), y + area.height());
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(
                    font,
                    lines.get(line),
                    x + 2,
                    y + 1 + line * font.lineHeight,
                    INK_COLOR,
                    false);
        }
        graphics.disableScissor();
        drawState.record(key, area, text, lines);
    }

    private void drawPageInterior(
            GuiGraphics graphics, int left, int top) {
        fill(graphics, left, top, HeavensDoorBookLayout.LEFT_PAGE_FILL,
                PAGE_COLOR);
        fill(graphics, left, top, HeavensDoorBookLayout.RIGHT_PAGE_FILL,
                PAGE_COLOR);
        drawSeparators(
                graphics, left, top, HeavensDoorBookLayout.LEFT_FIELDS);
        drawSeparators(
                graphics, left, top, HeavensDoorBookLayout.RIGHT_FIELDS);
    }

    private static void fill(
            GuiGraphics graphics,
            int left,
            int top,
            HeavensDoorBookLayout.Area area,
            int color) {
        graphics.fill(
                left + area.x(),
                top + area.y(),
                left + area.right(),
                top + area.bottom(),
                color);
    }

    private static void drawSeparators(
            GuiGraphics graphics,
            int left,
            int top,
            List<HeavensDoorBookLayout.Area> fields) {
        for (HeavensDoorBookLayout.Area area : fields) {
            int y = top + area.bottom() - 1;
            graphics.fill(
                    left + area.x(),
                    y,
                    left + area.right(),
                    y + 1,
                    SEPARATOR_COLOR);
        }
    }

    private static List<String> wrapText(
            Font font,
            String text,
            int maxWidth,
            int maxLines) {
        List<String> lines = new ArrayList<>(maxLines);
        String remaining = text.strip();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (font.width(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(ellipsize(font, remaining, maxWidth));
                break;
            }
            String prefix =
                    font.plainSubstrByWidth(remaining, maxWidth);
            int split = prefix.lastIndexOf(' ');
            if (split > 0) {
                prefix = prefix.substring(0, split);
            }
            if (prefix.isEmpty()) {
                prefix = remaining.substring(0, 1);
            }
            lines.add(prefix.stripTrailing());
            remaining = remaining.substring(prefix.length()).stripLeading();
        }
        return lines;
    }

    private static String ellipsize(
            Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int prefixWidth = Math.max(0, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(text, prefixWidth) + suffix;
    }

    private static final class BookDrawState {
        private final List<
                HeavensDoorBookRenderDiagnostics.TextFieldReceipt> fields =
                        new ArrayList<>(10);

        private void record(
                String key,
                HeavensDoorBookLayout.Area area,
                String sourceText,
                List<String> renderedLines) {
            fields.add(
                    new HeavensDoorBookRenderDiagnostics.TextFieldReceipt(
                            key,
                            area,
                            renderedLines,
                            HeavensDoorBookRenderDiagnostics.fingerprint(
                                    sourceText)));
        }

        private List<
                HeavensDoorBookRenderDiagnostics.TextFieldReceipt> fields() {
            return List.copyOf(fields);
        }
    }

    private int bookLeft() {
        return (width - HeavensDoorBookLayout.IMAGE_WIDTH) / 2;
    }

    private int bookTop() {
        return (height - HeavensDoorBookLayout.IMAGE_HEIGHT) / 2;
    }

    private static final class BookCommandButton extends Button {
        private BookCommandButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                OnPress onPress) {
            super(
                    x,
                    y,
                    width,
                    height,
                    message,
                    onPress,
                    Button.DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            int background = isHoveredOrFocused()
                    ? 0x554C5368
                    : 0x224C5368;
            graphics.fill(
                    getX(),
                    getY(),
                    getX() + getWidth(),
                    getY() + getHeight(),
                    background);
            graphics.fill(
                    getX(),
                    getY() + getHeight() - 1,
                    getX() + getWidth(),
                    getY() + getHeight(),
                    SEPARATOR_COLOR);

            Font font = Minecraft.getInstance().font;
            String text = ellipsize(
                    font, getMessage().getString(), getWidth() - 4);
            int textX = getX()
                    + Math.max(2, (getWidth() - font.width(text)) / 2);
            int textY = getY()
                    + Math.max(1, (getHeight() - font.lineHeight) / 2);
            graphics.drawString(
                    font,
                    text,
                    textX,
                    textY,
                    INK_COLOR,
                    false);
        }
    }
}
