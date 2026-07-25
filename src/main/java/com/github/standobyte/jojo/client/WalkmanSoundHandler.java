package com.github.standobyte.jojo.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.sound.WalkmanRewindSound;
import com.github.standobyte.jojo.client.sound.WalkmanTrackSound;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.WalkmanItem;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.cassette.CassetteSide;
import com.github.standobyte.jojo.item.cassette.CassetteTrackSource;
import com.github.standobyte.jojo.item.cassette.WalkmanPlaybackMode;
import com.github.standobyte.jojo.network.c2s.ClWalkmanControlsPacket;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.util.sound.OstSoundList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class WalkmanSoundHandler {
	private static Playlist playlist;

	@Nullable
	public static Playlist getPlaylist(int walkmanId) {
		return playlist != null && playlist.walkmanId == walkmanId ? playlist : null;
	}

	@Nullable
	public static Playlist getCurrentPlaylist() {
		return playlist;
	}

	@Nullable
	public static Playlist initPlaylist(CassetteTracksSided cassetteTracks, ItemStack cassetteItem, int walkmanId, InteractionHand hand) {
		clearPlaylist();
		CassetteData cassette = CassetteRecordedItem.getOrBroken(cassetteItem);
		if (!cassette.isBroken() && !cassetteTracks.isEmpty()) {
			playlist = new Playlist(cassetteTracks, cassette, walkmanId, hand);
		}
		return playlist;
	}

	public static void clearPlaylist() {
		if (playlist != null) {
			playlist.stopPlaying();
			playlist = null;
		}
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (playlist != null) {
			playlist.tick();
		}
	}

	public static class Playlist {
		private final CassetteTracksSided cassetteTracks;
		private final int distortion;
		private final int walkmanId;
		private final InteractionHand hand;

		private float volume = 1.0F;
		private WalkmanPlaybackMode playbackMode = WalkmanPlaybackMode.STOP_AT_THE_END;
		private TrackInfo currentTrack;
		private TrackInfo fastForwardTrack;
		private boolean currentTrackIsLast;
		private TrackInfo rewindTrack;
		private TrackInfo flipSideTrack;

		private boolean isPlaying;
		private WalkmanTrackSound currentSound;
		private int rewindSoundTicks;
		private boolean playCurrentSoundAfterRewind;
		private WalkmanRewindSound rewindSound;
		private IndicatorStatus indicatorStatus;

		private Playlist(CassetteTracksSided cassetteTracks, CassetteData cassette, int walkmanId, InteractionHand hand) {
			this.cassetteTracks = cassetteTracks;
			this.distortion = cassette.generation();
			this.walkmanId = walkmanId;
			this.hand = hand;
		}

		public void setTrack(@Nullable TrackInfo track) {
			currentTrack = track;
			fastForwardTrack = rewindTrack = flipSideTrack = null;
			if (track == null) {
				currentTrackIsLast = true;
				return;
			}

			CassetteSide currentSide = track.side;
			List<Track> tracksThisSide = cassetteTracks.get(currentSide);
			List<Track> tracksOppositeSide = cassetteTracks.get(currentSide.opposite());
			int trackNumber = Mth.clamp(track.number, 0, tracksThisSide.size() - 1);

			if (trackNumber < tracksThisSide.size() - 1) {
				fastForwardTrack = TrackInfo.of(cassetteTracks, currentSide, trackNumber + 1);
			}
			else if (!tracksOppositeSide.isEmpty()) {
				fastForwardTrack = TrackInfo.of(cassetteTracks, currentSide.opposite(), 0);
			}
			else if (!tracksThisSide.isEmpty()) {
				fastForwardTrack = TrackInfo.of(cassetteTracks, currentSide, 0);
			}
			currentTrackIsLast = isTrackLast(track);

			if (trackNumber > 0 && !tracksThisSide.isEmpty()) {
				rewindTrack = TrackInfo.of(cassetteTracks, currentSide, trackNumber - 1);
			}
			else {
				rewindTrack = currentTrack;
			}

			if (!tracksOppositeSide.isEmpty()) {
				int flipSideTrackNumber = Math.max(tracksThisSide.size(), tracksOppositeSide.size()) - 1 - trackNumber;
				flipSideTrack = TrackInfo.of(cassetteTracks, currentSide.opposite(), Mth.clamp(flipSideTrackNumber, 0, tracksOppositeSide.size() - 1));
			}

			PacketDistributor.sendToServer(ClWalkmanControlsPacket.position(walkmanId, currentTrack.side, currentTrack.number));
		}

		public void playCurrentTrack() {
			playTrack(currentTrack);
		}

		private void playTrack(@Nullable TrackInfo track) {
			if (currentSound != null) {
				currentSound.stopPlaying();
				currentSound = null;
			}
			if (track != null) {
				if (rewindSoundTicks > 0) {
					playCurrentSoundAfterRewind = true;
				}
				else {
					WalkmanTrackSound newSound = new WalkmanTrackSound(track.track.soundEvent, distortion);
					currentSound = newSound;
					newSound.setVolume(volume);
					Minecraft mc = Minecraft.getInstance();
					mc.getSoundManager().play(newSound);
					mc.gui.setNowPlaying(track.track.name);
					playCurrentSoundAfterRewind = false;
				}
				isPlaying = true;
			}
			else {
				isPlaying = false;
				playCurrentSoundAfterRewind = false;
				setRewindSoundTicks(0);
			}
		}

		public void stopPlaying() {
			playTrack(null);
		}

		public void setAndPlayNext() {
			boolean stop = stopAfterCurrentTrack();
			setTrack(getFastForwardTrack());
			if (stop) {
				stopPlaying();
			}
			else {
				playCurrentTrack();
			}
		}

		public boolean stopAfterCurrentTrack() {
			return currentTrackIsLast && playbackMode == WalkmanPlaybackMode.STOP_AT_THE_END;
		}

		public void setRewindSoundTicks(int ticks) {
			setRewindSoundTicks(ticks, null);
		}

		public void setRewindSoundTicks(int ticks, @Nullable IndicatorStatus indicatorStatus) {
			this.rewindSoundTicks = ticks;
			this.indicatorStatus = indicatorStatus;
			if (ticks > 0 && (rewindSound == null || rewindSound.isStopped())) {
				rewindSound = new WalkmanRewindSound();
				Minecraft.getInstance().getSoundManager().play(rewindSound);
			}
		}

		public int getRewindSoundTicks() {
			return rewindSoundTicks;
		}

		@Nullable
		public IndicatorStatus getIndicatorStatus() {
			return indicatorStatus;
		}

		private void tick() {
			if (rewindSoundTicks > 0) {
				rewindSoundTicks--;
				if (rewindSoundTicks == 0 && playCurrentSoundAfterRewind) {
					playCurrentTrack();
					indicatorStatus = null;
				}
				return;
			}

			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || !walkmanStillPresent(mc)) {
				clearPlaylist();
				return;
			}

			if (currentSound != null) {
				if (mc.options.getSoundSourceVolume(currentSound.getSource()) <= 0) {
					stopPlaying();
				}
				else if (!mc.getSoundManager().isActive(currentSound)) {
					setAndPlayNext();
				}

				if (mc.player.tickCount % 100 == 0) {
					mc.getMusicManager().stopPlaying();
				}
			}
		}

		private boolean walkmanStillPresent(Minecraft mc) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (isThisWalkman(mc.player.getItemInHand(hand))) {
					return true;
				}
			}
			for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
				if (isThisWalkman(mc.player.getInventory().getItem(i))) {
					return true;
				}
			}
			return false;
		}

		private boolean isThisWalkman(ItemStack stack) {
			return WalkmanItem.getWalkmanData(stack)
					.map(data -> data.idInitialized() && data.id() == walkmanId)
					.orElse(false);
		}

		public void setVolume(float volume) {
			this.volume = volume;
			if (currentSound != null) {
				currentSound.setVolume(volume);
			}
		}

		public void setPlaybackMode(WalkmanPlaybackMode mode) {
			this.playbackMode = mode;
		}

		private boolean isTrackLast(TrackInfo track) {
			return track.number >= cassetteTracks.get(track.side).size() - 1
					&& (track.side == CassetteSide.SIDE_B || cassetteTracks.get(CassetteSide.SIDE_B).isEmpty());
		}

		public boolean isPlaying() {
			return isPlaying;
		}

		@Nullable
		public TrackInfo getFlipSideTrack() {
			return flipSideTrack;
		}

		@Nullable
		public TrackInfo getRewindTrack() {
			return rewindTrack;
		}

		@Nullable
		public TrackInfo getFastForwardTrack() {
			return fastForwardTrack;
		}

		public CassetteTracksSided getAllTracks() {
			return cassetteTracks;
		}

		@Nullable
		public TrackInfo getCurrentTrack() {
			return currentTrack;
		}
	}

	public enum IndicatorStatus {
		REWIND,
		FAST_FORWARD
	}

	public record TrackInfo(Track track, CassetteSide side, int number) {
		public static TrackInfo of(CassetteTracksSided allTracks, CassetteSide side, int number) {
			List<Track> tracksThisSide = allTracks.get(side);
			if (number < 0 || number >= tracksThisSide.size()) {
				throw new IllegalArgumentException("The track number is supposed to be checked already");
			}
			return new TrackInfo(Objects.requireNonNull(tracksThisSide.get(number)), side, number);
		}
	}

	public record Track(SoundEvent soundEvent, Component name) {}

	public static class CassetteTracksSided {
		public static final CassetteTracksSided EMPTY_TRACK_LIST = new CassetteTracksSided(List.of(), List.of());

		private final Map<CassetteSide, List<Track>> tracksMap = new EnumMap<>(CassetteSide.class);
		private final boolean isEmpty;

		private CassetteTracksSided(List<Track> sideA, List<Track> sideB) {
			tracksMap.put(CassetteSide.SIDE_A, sideA);
			tracksMap.put(CassetteSide.SIDE_B, sideB);
			isEmpty = sideA.isEmpty() && sideB.isEmpty();
		}

		public List<Track> get(CassetteSide side) {
			return tracksMap.get(side);
		}

		public void forEach(BiConsumer<CassetteSide, List<Track>> action) {
			tracksMap.forEach(action);
		}

		public boolean isEmpty() {
			return isEmpty;
		}

		public boolean matches(CassetteTracksSided other) {
			if (this.isEmpty && other.isEmpty) return true;
			if (this.isEmpty || other.isEmpty) return false;
			return this.get(CassetteSide.SIDE_A).equals(other.get(CassetteSide.SIDE_A))
					&& this.get(CassetteSide.SIDE_B).equals(other.get(CassetteSide.SIDE_B));
		}

		public static CassetteTracksSided fromCassette(CassetteData cassette, HolderLookup.Provider registries) {
			if (cassette.isBroken()) {
				return EMPTY_TRACK_LIST;
			}
			List<Track> tracks = cassette.tracks().stream()
					.flatMap(source -> getTracks(source, registries))
					.collect(Collectors.toList());
			if (tracks.isEmpty()) {
				return EMPTY_TRACK_LIST;
			}
			int lastSideATrack = (tracks.size() - 1) / 2;
			return new CassetteTracksSided(
					List.copyOf(tracks.subList(0, lastSideATrack + 1)),
					List.copyOf(tracks.subList(lastSideATrack + 1, tracks.size())));
		}

		private static Stream<Track> getTracks(CassetteTrackSource source, HolderLookup.Provider registries) {
			Optional<SoundEvent> soundEvent = resolveSoundEvent(source, registries);
			if (soundEvent.isEmpty() || !hasLoadedSound(soundEvent.get())) {
				return Stream.empty();
			}
			return Stream.of(new Track(soundEvent.get(), source.displayName(registries)));
		}
	}

	private static Optional<SoundEvent> resolveSoundEvent(CassetteTrackSource source, HolderLookup.Provider registries) {
		return switch (source.type()) {
			case MUSIC_DISC -> resolveMusicDiscSound(source.id(), registries);
			case STAND_DISC -> resolveStandDiscSound(source.id());
			case DYE_COLOR -> resolveDyeSound(source.dyeColor());
		};
	}

	private static Optional<SoundEvent> resolveMusicDiscSound(ResourceLocation itemId, HolderLookup.Provider registries) {
		Item item = BuiltInRegistries.ITEM.get(itemId);
		ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return JukeboxSong.fromStack(registries, stack)
				.map(holder -> holder.value().soundEvent().value());
	}

	private static Optional<SoundEvent> resolveStandDiscSound(ResourceLocation standId) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
		if (standType == null) {
			return Optional.empty();
		}
		OstSoundList ost = standType.getOst(null);
		return ost != null ? Optional.ofNullable(ost.getForCassette()) : Optional.empty();
	}

	private static Optional<SoundEvent> resolveDyeSound(Optional<DyeColor> dyeColor) {
		return dyeColor.map(dye -> switch (dye) {
			case WHITE -> ModSoundEvents.CASSETTE_WHITE.get();
			case ORANGE -> ModSoundEvents.CASSETTE_ORANGE.get();
			case MAGENTA -> ModSoundEvents.CASSETTE_MAGENTA.get();
			case LIGHT_BLUE -> ModSoundEvents.CASSETTE_LIGHT_BLUE.get();
			case YELLOW -> ModSoundEvents.CASSETTE_YELLOW.get();
			case LIME -> ModSoundEvents.CASSETTE_LIME.get();
			case PINK -> ModSoundEvents.CASSETTE_PINK.get();
			case GRAY -> ModSoundEvents.CASSETTE_GRAY.get();
			case LIGHT_GRAY -> ModSoundEvents.CASSETTE_LIGHT_GRAY.get();
			case CYAN -> ModSoundEvents.CASSETTE_CYAN.get();
			case PURPLE -> ModSoundEvents.CASSETTE_PURPLE.get();
			case BLUE -> ModSoundEvents.CASSETTE_BLUE.get();
			case BROWN -> ModSoundEvents.CASSETTE_BROWN.get();
			case GREEN -> ModSoundEvents.CASSETTE_GREEN.get();
			case RED -> ModSoundEvents.CASSETTE_RED.get();
			case BLACK -> ModSoundEvents.CASSETTE_BLACK.get();
		});
	}

	private static boolean hasLoadedSound(SoundEvent soundEvent) {
		WeighedSoundEvents sounds = Minecraft.getInstance().getSoundManager().getSoundEvent(soundEvent.getLocation());
		return sounds != null && sounds.getSound(RandomSourceHolder.RANDOM) != SoundManager.EMPTY_SOUND;
	}

	private static final class RandomSourceHolder {
		private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();
	}
}
