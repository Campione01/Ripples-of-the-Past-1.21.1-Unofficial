package com.github.standobyte.jojo.client.ui.screen.walkman;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.client.WalkmanSoundHandler;
import com.github.standobyte.jojo.client.WalkmanSoundHandler.CassetteTracksSided;
import com.github.standobyte.jojo.client.WalkmanSoundHandler.IndicatorStatus;
import com.github.standobyte.jojo.client.WalkmanSoundHandler.Playlist;
import com.github.standobyte.jojo.client.WalkmanSoundHandler.Track;
import com.github.standobyte.jojo.client.WalkmanSoundHandler.TrackInfo;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.WalkmanItem;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.cassette.CassetteSide;
import com.github.standobyte.jojo.item.cassette.WalkmanMenu;
import com.github.standobyte.jojo.item.cassette.WalkmanPlaybackMode;
import com.github.standobyte.jojo.network.c2s.ClWalkmanControlsPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class WalkmanScreen extends AbstractContainerScreen<WalkmanMenu> {
	static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/gui/container/walkman.png");
	static final ResourceLocation CASSETTE_TEXTURE = JojoMod.resLoc("textures/gui/container/walkman_cassette.png");

	private ItemStack prevCassetteItem = ItemStack.EMPTY;
	private CassetteTracksSided cassetteTracks = CassetteTracksSided.EMPTY_TRACK_LIST;
	private CassetteSide currentSide;
	private TrackInfo currentTrack;
	private List<Track> tracksToShow;
	private WalkmanPlaybackMode mode = WalkmanPlaybackMode.STOP_AT_THE_END;
	private int walkmanId;

	private Button playButton;
	private Button flipSideButton;
	private Button stopButton;
	private Button rewindButton;
	private Button fastForwardButton;
	private Button playbackModeSwitch;
	private WalkmanVolumeWheel volumeWheel;

	public WalkmanScreen(WalkmanMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.empty());
		imageWidth = 194;
		imageHeight = 224;
	}

	@Override
	protected void init() {
		super.init();
		var walkmanData = WalkmanItem.getOrDefault(menu.getWalkmanItem());
		mode = walkmanData.playbackMode();
		walkmanId = walkmanData.id();

		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null) {
			cassetteTracks = playlist.getAllTracks();
			currentTrack = playlist.getCurrentTrack();
			currentSide = currentTrack != null ? currentTrack.side() : null;
		}

		int x = leftPos + 38;
		int y = topPos + 106;
		rewindButton = addRenderableWidget(Button.builder(Component.empty(), button -> rewind()).bounds(x, y, 20, 16).build());
		playButton = addRenderableWidget(Button.builder(Component.empty(), button -> play()).bounds(x + 23, y, 43, 16).build());
		flipSideButton = addRenderableWidget(Button.builder(Component.empty(), button -> flip()).bounds(x + 23, y, 43, 16).build());
		fastForwardButton = addRenderableWidget(Button.builder(Component.empty(), button -> fastForward()).bounds(x + 69, y, 20, 16).build());
		stopButton = addRenderableWidget(Button.builder(Component.empty(), button -> stop()).bounds(x + 92, y, 32, 16).build());
		playbackModeSwitch = addRenderableWidget(Button.builder(Component.empty(), button -> toggleLoop()).bounds(x + 139, y, 20, 16).build());

		volumeWheel = addRenderableWidget(new WalkmanVolumeWheel(this, leftPos + 17, topPos + 61, 11, 37));
		volumeWheel.setValue(walkmanData.volume(), false);
		updateCassette();
		updateButtons();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		updateCassette();
		updateButtons();
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private void updateCassette() {
		ItemStack cassetteItem = menu.getCassetteItem();
		if (ItemStack.matches(prevCassetteItem, cassetteItem)) {
			return;
		}

		currentTrack = null;
		tracksToShow = null;
		currentSide = null;
		if (cassetteItem.isEmpty() || minecraft == null || minecraft.level == null) {
			cassetteTracks = CassetteTracksSided.EMPTY_TRACK_LIST;
		}
		else {
			CassetteData cassette = CassetteRecordedItem.getOrBroken(cassetteItem);
			cassetteTracks = CassetteTracksSided.fromCassette(cassette, minecraft.level.registryAccess());
			currentSide = cassette.side();
			List<Track> tracks = cassetteTracks.get(currentSide);
			if (tracks.isEmpty()) {
				currentSide = currentSide.opposite();
				tracks = cassetteTracks.get(currentSide);
			}
			if (!tracks.isEmpty()) {
				setTrack(TrackInfo.of(cassetteTracks, currentSide, Mth.clamp(cassette.sideTrack(), 0, tracks.size() - 1)));
			}
		}
		prevCassetteItem = cassetteItem.copy();
	}

	private void updateButtons() {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		boolean playing = playlist != null && playlist.isPlaying();
		playButton.active = currentTrack != null && !playing;
		flipSideButton.active = playing && playlist.getFlipSideTrack() != null;
		stopButton.active = playing;
		rewindButton.active = playing && playlist.getRewindTrack() != null;
		fastForwardButton.active = playing && playlist.getFastForwardTrack() != null;
		playButton.visible = !flipSideButton.active;
		flipSideButton.visible = !playButton.visible;

		playButton.setMessage(Component.translatable("walkman.button.play", tooltipTrackName(currentTrack)));
		flipSideButton.setMessage(Component.translatable("walkman.button.flip", tooltipTrackName(playlist != null ? playlist.getFlipSideTrack() : null)));
		stopButton.setMessage(Component.translatable("walkman.button.stop"));
		rewindButton.setMessage(Component.translatable("walkman.button.rewind", tooltipTrackName(playlist != null ? playlist.getRewindTrack() : currentTrack)));
		fastForwardButton.setMessage(playlist != null && playlist.stopAfterCurrentTrack()
				? Component.translatable("walkman.button.fast_forward.end")
				: Component.translatable("walkman.button.fast_forward", tooltipTrackName(playlist != null ? playlist.getFastForwardTrack() : currentTrack)));
		playbackModeSwitch.setMessage(Component.translatable("walkman.button.playback_mode." + (mode == WalkmanPlaybackMode.LOOP ? "loop" : "default")));
	}

	private void play() {
		if (currentTrack == null || cassetteTracks.isEmpty()) {
			return;
		}
		Playlist playlist = WalkmanSoundHandler.initPlaylist(cassetteTracks, menu.getCassetteItem(), walkmanId, menu.getHand());
		if (playlist == null) {
			return;
		}
		playlist.setPlaybackMode(mode);
		playlist.setVolume(volumeWheel.getValue());
		playlist.setTrack(currentTrack);
		minecraft.getMusicManager().stopPlaying();
		playlist.playCurrentTrack();
	}

	private void stop() {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null) {
			playlist.stopPlaying();
		}
		PacketDistributor.sendToServer(ClWalkmanControlsPacket.stop(menu.getHand()));
	}

	private void rewind() {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null && playlist.getRewindTrack() != null) {
			setTrack(playlist.getRewindTrack());
			playlist.setTrack(currentTrack);
			playlist.setRewindSoundTicks(40, IndicatorStatus.REWIND);
			playlist.playCurrentTrack();
		}
		else {
			PacketDistributor.sendToServer(ClWalkmanControlsPacket.rewind(menu.getHand()));
		}
	}

	private void fastForward() {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null && playlist.getFastForwardTrack() != null) {
			setTrack(playlist.getFastForwardTrack());
			playlist.setRewindSoundTicks(40, IndicatorStatus.FAST_FORWARD);
			playlist.setAndPlayNext();
		}
	}

	private void flip() {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null && playlist.getFlipSideTrack() != null) {
			setTrack(playlist.getFlipSideTrack());
			playlist.setTrack(currentTrack);
			playlist.setRewindSoundTicks(20);
			playlist.playCurrentTrack();
		}
		else {
			PacketDistributor.sendToServer(ClWalkmanControlsPacket.flip(menu.getHand()));
		}
	}

	private void toggleLoop() {
		mode = mode.toggle();
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null) {
			playlist.setPlaybackMode(mode);
		}
		PacketDistributor.sendToServer(ClWalkmanControlsPacket.toggleLoop(menu.getHand()));
	}

	void onVolumeChanged(float volume) {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist != null) {
			playlist.setVolume(volume);
		}
		PacketDistributor.sendToServer(ClWalkmanControlsPacket.volume(menu.getHand(), volume));
	}

	private void setTrack(TrackInfo track) {
		currentTrack = track;
		currentSide = track != null ? track.side() : currentSide;
		tracksToShow = null;
		if (track != null) {
			List<Track> tracksThisSide = cassetteTracks.get(track.side());
			int trackNumber = track.number();
			int showFrom = trackNumber - 1;
			int showTo = trackNumber + 2;
			if (showFrom < 0) {
				showTo -= showFrom;
				showFrom = 0;
			}
			if (showTo > tracksThisSide.size()) {
				showFrom = Math.max(0, tracksThisSide.size() - 3);
				showTo = tracksThisSide.size();
			}
			tracksToShow = tracksThisSide.subList(showFrom, showTo);
		}
	}

	private Component tooltipTrackName(TrackInfo track) {
		return track != null
				? track.track().name().copy().withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE)
				: Component.literal("");
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		renderIndicators(guiGraphics);
		renderCassette(guiGraphics);
	}

	private void renderIndicators(GuiGraphics guiGraphics) {
		Playlist playlist = WalkmanSoundHandler.getPlaylist(walkmanId);
		if (playlist == null || !playlist.isPlaying() || currentSide == null || minecraft == null || minecraft.player == null) {
			return;
		}

		IndicatorStatus operation = playlist.getIndicatorStatus();
		boolean fwdFlicker = operation == IndicatorStatus.FAST_FORWARD;
		boolean fwdLight = !fwdFlicker;
		boolean revFlicker = operation == IndicatorStatus.REWIND;
		boolean revLight = false;
		if (currentSide == CassetteSide.SIDE_B) {
			boolean tmp = fwdLight;
			fwdLight = revLight;
			revLight = tmp;
			tmp = fwdFlicker;
			fwdFlicker = revFlicker;
			revFlicker = tmp;
		}
		boolean flickerTick = minecraft.player.tickCount % 40 >= 20;
		if (revLight || revFlicker && flickerTick) {
			guiGraphics.blit(TEXTURE, leftPos + 17, topPos + 18, 17, 226, 9, 9);
		}
		if (fwdLight || fwdFlicker && flickerTick) {
			guiGraphics.blit(TEXTURE, leftPos + 17, topPos + 35, 17, 243, 9, 9);
		}
	}

	private void renderCassette(GuiGraphics guiGraphics) {
		ItemStack cassetteItem = menu.getCassetteItem();
		if (cassetteItem.isEmpty()) {
			return;
		}
		guiGraphics.blit(CASSETTE_TEXTURE, leftPos + 35, topPos + 7, 0, 0, 150, 95);
		Optional<DyeColor> color = CassetteRecordedItem.getCassetteData(cassetteItem).flatMap(CassetteData::dye);
		color.ifPresent(dye -> guiGraphics.blit(CASSETTE_TEXTURE, leftPos + 40, topPos + 41, 5, 128 + dye.getId() * 8, 140, 7));
		if (currentSide != null) {
			guiGraphics.blit(CASSETTE_TEXTURE, leftPos + 47, topPos + 49, 204 + currentSide.ordinal() * 16, 41, 11, 11);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		ItemStack cassetteItem = menu.getCassetteItem();
		if (cassetteItem.isEmpty()) {
			return;
		}
		if (cassetteItem.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			String cassetteName = trimToWidth(cassetteItem.getHoverName().getString(), 124);
			guiGraphics.drawString(font, cassetteName, 110 - font.width(cassetteName) / 2, 66, 0x404040, false);
		}
		if (tracksToShow != null && !tracksToShow.isEmpty()) {
			int i = 0;
			for (Track track : tracksToShow) {
				String trackName = trimToWidth(track.name().getString(), 136);
				int color = currentTrack != null && currentTrack.track().equals(track) ? 0x205020 : 0x404040;
				guiGraphics.drawString(font, trackName, 42, 13 + i * 9, color, false);
				i++;
			}
		}
	}

	private String trimToWidth(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
	}
}
