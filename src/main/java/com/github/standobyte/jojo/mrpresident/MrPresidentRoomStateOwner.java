package com.github.standobyte.jojo.mrpresident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.world.dimension.ModDimensions;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;

public class MrPresidentRoomStateOwner extends SavedData {
	private static final String FILE_NAME = JojoMod.MOD_ID + "-mr_president_room_state";
	private static final SavedData.Factory<MrPresidentRoomStateOwner> FACTORY =
			new SavedData.Factory<>(MrPresidentRoomStateOwner::new, MrPresidentRoomStateOwner::load);
	private static final ResourceLocation ROOM_TEMPLATE = JojoMod.resLoc("mr_president_room");
	private static final double ROOM_INSIDE_Y = 200.0D;
	private static final int ROOM_ENTER_X_OFFSET = 8;
	private static final int ROOM_ENTER_Y_OFFSET = 6;
	private static final int ROOM_ENTER_Z_OFFSET = 8;
	private static final List<BlockPos> WALL_OFFSETS = makeWallOffsets(false);
	private static final List<BlockPos> CEILING_OFFSETS = makeWallOffsets(true);

	private final Map<UUID, RoomRecord> roomsByTurtle = new HashMap<>();
	private final Set<UUID> lockedRooms = new HashSet<>();

	private static List<BlockPos> makeWallOffsets(boolean ceiling) {
		List<BlockPos> offsets = new ArrayList<>();
		if (ceiling) {
			for (int x = 4; x <= 11; x++) {
				for (int z = 4; z <= 11; z++) {
					offsets.add(new BlockPos(x, 10, z));
				}
			}
			return offsets;
		}
		for (int x = 4; x <= 11; x++) {
			for (int y = 6; y <= 9; y++) {
				offsets.add(new BlockPos(x, y, 3));
				offsets.add(new BlockPos(x, y, 12));
			}
		}
		for (int z = 4; z <= 11; z++) {
			for (int y = 6; y <= 9; y++) {
				offsets.add(new BlockPos(3, y, z));
				offsets.add(new BlockPos(12, y, z));
			}
		}
		for (int x = 4; x <= 11; x++) {
			for (int z = 4; z <= 11; z++) {
				offsets.add(new BlockPos(x, 5, z));
			}
		}
		return offsets;
	}

	public static MrPresidentRoomStateOwner load(CompoundTag nbt, HolderLookup.Provider registries) {
		MrPresidentRoomStateOwner data = new MrPresidentRoomStateOwner();
		ListTag rooms = nbt.getList("Rooms", Tag.TAG_COMPOUND);
		for (Tag element : rooms) {
			if (element instanceof CompoundTag roomNbt) {
				RoomRecord room = RoomRecord.fromNbt(roomNbt);
				if (room != null) {
					data.roomsByTurtle.put(room.turtleUuid(), room);
				}
			}
		}
		return data;
	}

	public static MrPresidentRoomStateOwner get(MinecraftServer server) {
		ServerLevel mrPresidentLevel = server.getLevel(ModDimensions.MR_PRESIDENT);
		DimensionDataStorage storage = mrPresidentLevel != null
				? mrPresidentLevel.getDataStorage()
				: server.overworld().getDataStorage();
		MrPresidentRoomStateOwner data = storage.computeIfAbsent(FACTORY, FILE_NAME);
		if (mrPresidentLevel != null) {
			DimensionDataStorage legacyStorage = server.overworld().getDataStorage();
			MrPresidentRoomStateOwner legacyData = legacyStorage.get(FACTORY, FILE_NAME);
			if (legacyData != null && legacyData != data && data.roomsByTurtle.isEmpty() && !legacyData.roomsByTurtle.isEmpty()) {
				data.copyRoomsFrom(legacyData);
			}
		}
		return data;
	}

	private void copyRoomsFrom(MrPresidentRoomStateOwner other) {
		roomsByTurtle.clear();
		roomsByTurtle.putAll(other.roomsByTurtle);
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag nbt = new CompoundTag();
		ListTag rooms = new ListTag();
		for (RoomRecord room : roomsByTurtle.values()) {
			rooms.add(room.toNbt());
		}
		nbt.put("Rooms", rooms);
		return nbt;
	}

	public RoomRecord getOrCreateForTurtle(UUID turtleUuid) {
		RoomRecord room = roomsByTurtle.get(turtleUuid);
		if (room == null) {
			room = new RoomRecord(turtleUuid, UUID.randomUUID(), false, null, null, null, null, null, List.of(), null, null, null, null,
					null, null, null, null);
			roomsByTurtle.put(turtleUuid, room);
			setDirty();
		}
		return room;
	}

	public boolean rememberTurtlePosition(CocoJumboTurtleEntity turtle) {
		RoomRecord record = getOrCreateForTurtle(turtle.getUUID());
		RoomRecord updated = new RoomRecord(
				record.turtleUuid(),
				record.roomId(),
				record.generated(),
				record.roomLevel(),
				record.roomSectionX(),
				record.roomSectionY(),
				record.roomSectionZ(),
				record.occupyingPlayer(),
				record.enteredEntities(),
				record.returnLevel(),
				record.returnX(),
				record.returnY(),
				record.returnZ(),
				turtle.level().dimension().location().toString(),
				turtle.getX(),
				turtle.getY(1.0D),
				turtle.getZ());
		roomsByTurtle.put(record.turtleUuid(), updated);
		setDirty();
		return true;
	}

	public RoomRecord getForTurtle(UUID turtleUuid) {
		return roomsByTurtle.get(turtleUuid);
	}

	public RoomRecord findRoomByOccupant(UUID playerUuid) {
		for (RoomRecord record : roomsByTurtle.values()) {
			if (playerUuid.equals(record.occupyingPlayer())) {
				return record;
			}
		}
		return null;
	}

	public RoomRecord findRoomByEntity(UUID entityUuid) {
		for (RoomRecord record : roomsByTurtle.values()) {
			if (entityUuid.equals(record.occupyingPlayer()) || record.enteredEntities().contains(entityUuid)) {
				return record;
			}
		}
		return null;
	}

	public RoomRecord findRoomByBlockPos(BlockPos pos) {
		RoomSectionPos section = new RoomSectionPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
		for (RoomRecord record : roomsByTurtle.values()) {
			if (section.equals(roomSection(record))) {
				return record;
			}
		}
		return null;
	}

	public boolean enterRoom(ServerPlayer player, UUID turtleUuid) {
		if (findRoomByEntity(player.getUUID()) != null) {
			return false;
		}
		RoomRecord existing = roomsByTurtle.get(turtleUuid);
		if (existing == null) {
			return false;
		}
		existing = ensureRoomSectionAllocated(existing);
		ServerLevel roomLevel = findRoomLevelForEntry(player.server, existing, player.serverLevel());
		double[] inside = roomInsidePosFor(existing);
		boolean generated = existing.generated();
		if (!generated) {
			generated = ensureRoomGenerated(roomLevel, existing);
		}
		String roomLevelId = roomLevel.dimension().location().toString();
		String returnLevelId = player.level().dimension().location().toString();
		double returnX = player.getX();
		double returnY = player.getY();
		double returnZ = player.getZ();
		if (!teleportEntityToTarget(player, new ReturnTarget(roomLevel, roomLevelId, inside[0], inside[1], inside[2]))) {
			return false;
		}
		RoomRecord updated = new RoomRecord(
				existing.turtleUuid(),
				existing.roomId(),
				generated,
				roomLevelId,
				existing.roomSectionX(),
				existing.roomSectionY(),
				existing.roomSectionZ(),
				player.getUUID(),
				addEnteredEntity(existing.enteredEntities(), player.getUUID()),
				returnLevelId,
				returnX,
				returnY,
				returnZ,
				existing.turtleLevel(),
				existing.turtleX(),
				existing.turtleY(),
				existing.turtleZ());
		roomsByTurtle.put(turtleUuid, updated);
		setDirty();
		ModCriteriaTriggers.triggerMrPresidentRoomEntered(player);
		return true;
	}

	public boolean enterFallingTargets(ServerLevel level, CocoJumboTurtleEntity turtle, List<Entity> targets) {
		return enterTargets(level, turtle, targets);
	}

	public boolean enterTargets(ServerLevel level, CocoJumboTurtleEntity turtle, List<Entity> targets) {
		RoomRecord existing = roomsByTurtle.get(turtle.getUUID());
		if (existing == null || targets.isEmpty()) {
			return false;
		}
		existing = ensureRoomSectionAllocated(existing);
		ServerLevel roomLevel = findRoomLevelForEntry(level.getServer(), existing, level);
		boolean generated = existing.generated();
		if (!generated) {
			generated = ensureRoomGenerated(roomLevel, existing);
		}
		double[] inside = roomInsidePosFor(existing);
		String roomLevelId = roomLevel.dimension().location().toString();
		List<UUID> enteredEntities = existing.enteredEntities();
		UUID occupyingPlayer = existing.occupyingPlayer();
		boolean changed = generated != existing.generated() || !roomLevelId.equals(existing.roomLevel());
		for (Entity target : targets) {
			if (findRoomByEntity(target.getUUID()) != null) {
				continue;
			}
			if (!teleportEntityToTarget(target, new ReturnTarget(roomLevel, roomLevelId, inside[0], inside[1], inside[2]))) {
				continue;
			}
			if (occupyingPlayer == null && target instanceof ServerPlayer player) {
				occupyingPlayer = player.getUUID();
			}
			enteredEntities = addEnteredEntity(enteredEntities, target.getUUID());
			if (target instanceof ServerPlayer player) {
				ModCriteriaTriggers.triggerMrPresidentRoomEntered(player);
			}
			changed = true;
		}
		if (!changed) {
			return false;
		}
		RoomRecord updated = new RoomRecord(
				existing.turtleUuid(),
				existing.roomId(),
				generated,
				roomLevelId,
				existing.roomSectionX(),
				existing.roomSectionY(),
				existing.roomSectionZ(),
				occupyingPlayer,
				enteredEntities,
				existing.returnLevel(),
				existing.returnX(),
				existing.returnY(),
				existing.returnZ(),
				existing.turtleLevel(),
				existing.turtleX(),
				existing.turtleY(),
				existing.turtleZ());
		roomsByTurtle.put(existing.turtleUuid(), updated);
		setDirty();
		return true;
	}

	public boolean returnFromRoom(ServerPlayer player) {
		RoomRecord record = findRoomByEntity(player.getUUID());
		if (record == null) {
			return false;
		}
		return returnPlayerFromRecord(player, record);
	}

	public boolean returnFromRoom(ServerPlayer player, BlockPos roomBlockPos) {
		RoomRecord record = findRoomByBlockPos(roomBlockPos);
		if (record == null) {
			record = findRoomByEntity(player.getUUID());
		}
		if (record == null) {
			return false;
		}
		return returnPlayerFromRecord(player, record);
	}

	private boolean returnPlayerFromRecord(ServerPlayer player, RoomRecord record) {
		ReturnTarget target = findReturnTarget(player.server, record);
		if (target == null) {
			return false;
		}
		if (!teleportEntityToTarget(player, target)) {
			return false;
		}
		markEntityReturned(record, player.getUUID());
		return true;
	}

	public boolean ejectLockedRoom(MinecraftServer server, UUID turtleUuid) {
		RoomRecord record = roomsByTurtle.get(turtleUuid);
		if (record == null) {
			return false;
		}
		return returnRoomEntities(server, record, entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand));
	}

	public boolean tickRoomLockState(MinecraftServer server, UUID turtleUuid, boolean roomLocked) {
		if (!roomLocked) {
			lockedRooms.remove(turtleUuid);
			return false;
		}
		if (!lockedRooms.add(turtleUuid)) {
			return false;
		}
		return ejectLockedRoom(server, turtleUuid);
	}

	public boolean checkRoomWallsAdvancement(ServerPlayer player) {
		RoomRecord record = findRoomByEntity(player.getUUID());
		if (record == null) {
			return false;
		}
		BlockPos lowerCorner = roomLowerCornerFor(record);
		if (hasDecoratedRoomWalls(player.level(), lowerCorner)) {
			ModCriteriaTriggers.triggerMrPresidentRoomWalls(player);
			return true;
		}
		return false;
	}

	public boolean cleanupRoomForTurtle(MinecraftServer server, UUID turtleUuid) {
		RoomRecord record = roomsByTurtle.get(turtleUuid);
		if (record == null) {
			return false;
		}
		ServerLevel roomLevel = findRoomLevel(server, record);
		if (roomLevel != null) {
			ensureRoomChunkLoaded(roomLevel, record);
			breakAndTeleportRoomBlocks(roomLevel, roomLowerCornerFor(record));
		}
		return returnRoomEntities(server, record, entity -> true);
	}

	private boolean returnRoomEntities(MinecraftServer server, RoomRecord record, Predicate<Entity> filter) {
		ensureRoomChunkLoaded(server, record);
		Set<Entity> entities = collectRoomEntities(server, record, filter);
		boolean changed = false;
		for (Entity entity : entities) {
			RoomRecord current = roomsByTurtle.get(record.turtleUuid());
			if (current == null) {
				break;
			}
			changed |= returnEntityFromRoom(server, current, entity);
		}
		return changed;
	}

	private Set<Entity> collectRoomEntities(MinecraftServer server, RoomRecord record, Predicate<Entity> filter) {
		Set<Entity> entities = new HashSet<>();
		for (UUID entityId : record.enteredEntities()) {
			Entity entity = findLiveEntity(server, entityId);
			if (entity != null && filter.test(entity)) {
				entities.add(entity);
			}
		}
		ServerLevel roomLevel = findRoomLevel(server, record);
		if (roomLevel != null) {
			entities.addAll(roomLevel.getEntities((Entity) null, roomBounds(record), filter));
		}
		return entities;
	}

	private boolean returnEntityFromRoom(MinecraftServer server, RoomRecord record, Entity entity) {
		if (entity instanceof ServerPlayer player) {
			return returnPlayerFromRecord(player, record);
		}
		ReturnTarget target = findReturnTarget(server, record);
		if (target == null || !teleportEntityToTarget(entity, target)) {
			return false;
		}
		markEntityReturned(record, entity.getUUID());
		return true;
	}

	private void markEntityReturned(RoomRecord record, UUID entityUuid) {
		boolean wasOccupyingPlayer = entityUuid.equals(record.occupyingPlayer());
		RoomRecord updated = new RoomRecord(
				record.turtleUuid(),
				record.roomId(),
				record.generated(),
				record.roomLevel(),
				record.roomSectionX(),
				record.roomSectionY(),
				record.roomSectionZ(),
				wasOccupyingPlayer ? null : record.occupyingPlayer(),
				removeEnteredEntity(record.enteredEntities(), entityUuid),
				wasOccupyingPlayer ? null : record.returnLevel(),
				wasOccupyingPlayer ? null : record.returnX(),
				wasOccupyingPlayer ? null : record.returnY(),
				wasOccupyingPlayer ? null : record.returnZ(),
				record.turtleLevel(),
				record.turtleX(),
				record.turtleY(),
				record.turtleZ());
		roomsByTurtle.put(record.turtleUuid(), updated);
		setDirty();
	}

	private static void breakAndTeleportRoomBlocks(ServerLevel level, BlockPos lowerCorner) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					pos.set(lowerCorner.getX() + x, lowerCorner.getY() + y, lowerCorner.getZ() + z);
					BlockState blockState = level.getBlockState(pos);
					if (!blockState.isAir() && blockState.getDestroySpeed(level, pos) >= 0.0F) {
						JojoModUtil.destroyBlock(level, pos, true, null);
					}
				}
			}
		}
	}

	private static boolean hasDecoratedRoomWalls(Level level, BlockPos lowerCorner) {
		for (BlockPos offset : WALL_OFFSETS) {
			BlockState blockState = level.getBlockState(lowerCorner.offset(offset));
			if (blockState.isAir()) {
				return false;
			}
		}
		for (BlockPos offset : CEILING_OFFSETS) {
			BlockPos pos = lowerCorner.offset(offset);
			BlockState blockState = level.getBlockState(pos);
			if (blockState.isAir() && !level.getBlockState(pos.above()).is(ModBlocks.MR_PRESIDENT_EXIT.get())) {
				return false;
			}
		}
		return true;
	}

	private static BlockPos roomLowerCornerFor(UUID roomId) {
		double[] inside = roomInsidePosFor(roomId);
		return new BlockPos(
				(int) Math.floor(inside[0]) - ROOM_ENTER_X_OFFSET,
				(int) Math.floor(inside[1]) - ROOM_ENTER_Y_OFFSET,
				(int) Math.floor(inside[2]) - ROOM_ENTER_Z_OFFSET);
	}

	private static BlockPos roomLowerCornerFor(RoomRecord record) {
		RoomSectionPos section = roomSection(record);
		if (section != null) {
			return section.blockPosition();
		}
		return roomLowerCornerFor(record.roomId());
	}

	private static AABB roomBounds(UUID roomId) {
		BlockPos lowerCorner = roomLowerCornerFor(roomId);
		return new AABB(
				lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
				lowerCorner.getX() + 16, lowerCorner.getY() + 16, lowerCorner.getZ() + 16);
	}

	private static AABB roomBounds(RoomRecord record) {
		BlockPos lowerCorner = roomLowerCornerFor(record);
		return new AABB(
				lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
				lowerCorner.getX() + 16, lowerCorner.getY() + 16, lowerCorner.getZ() + 16);
	}

	private static double[] roomInsidePosFor(UUID roomId) {
		long hi = roomId.getMostSignificantBits();
		long lo = roomId.getLeastSignificantBits();
		double rx = (((hi & 0xFFFFL) - 0x8000L) * 16.0D);
		double rz = (((lo & 0xFFFFL) - 0x8000L) * 16.0D);
		return new double[] { rx, ROOM_INSIDE_Y, rz };
	}

	private static double[] roomInsidePosFor(RoomRecord record) {
		RoomSectionPos section = roomSection(record);
		if (section != null) {
			return new double[] {
					(section.x() << 4) + ROOM_ENTER_X_OFFSET,
					(section.y() << 4) + ROOM_ENTER_Y_OFFSET,
					(section.z() << 4) + ROOM_ENTER_Z_OFFSET };
		}
		return roomInsidePosFor(record.roomId());
	}

	private static boolean ensureRoomGenerated(ServerLevel level, RoomRecord record) {
		BlockPos lowerCorner = roomLowerCornerFor(record);
		StructureTemplate template = level.getStructureManager().getOrCreate(ROOM_TEMPLATE);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		return template.placeInWorld(level, lowerCorner, lowerCorner, settings, level.getRandom(), 2);
	}

	private static ReturnTarget findReturnTarget(MinecraftServer server, RoomRecord record) {
		Entity turtle = findLiveTurtle(server, record.turtleUuid());
		if (turtle != null && turtle.level() instanceof ServerLevel level) {
			return new ReturnTarget(level, level.dimension().location().toString(), turtle.getX(), turtle.getY(1.0D), turtle.getZ());
		}
		if (record.turtleLevel() != null && record.turtleX() != null && record.turtleY() != null && record.turtleZ() != null) {
			ServerLevel level = findLevelById(server, record.turtleLevel());
			if (level != null) {
				return new ReturnTarget(level, record.turtleLevel(), record.turtleX(), record.turtleY(), record.turtleZ());
			}
		}
		if (record.returnLevel() != null && record.returnX() != null && record.returnY() != null && record.returnZ() != null) {
			ServerLevel level = findLevelById(server, record.returnLevel());
			if (level != null) {
				return new ReturnTarget(level, record.returnLevel(), record.returnX(), record.returnY(), record.returnZ());
			}
		}
		return null;
	}

	private static ServerLevel findLevelById(MinecraftServer server, String levelId) {
		ResourceLocation location = ResourceLocation.parse(levelId);
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
		return server.getLevel(key);
	}

	private static boolean teleportEntityToTarget(Entity entity, ReturnTarget target) {
		return entity.teleportTo(target.level(), target.x(), target.y(), target.z(), Set.of(), entity.getYRot(), entity.getXRot());
	}

	private static Entity findLiveTurtle(MinecraftServer server, UUID turtleUuid) {
		return findLiveEntity(server, turtleUuid);
	}

	private static Entity findLiveEntity(MinecraftServer server, UUID entityUuid) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(entityUuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static ServerLevel findRoomLevel(MinecraftServer server, RoomRecord record) {
		if (record.roomLevel() != null) {
			for (ServerLevel level : server.getAllLevels()) {
				if (record.roomLevel().equals(level.dimension().location().toString())) {
					return level;
				}
			}
		}
		for (UUID entityId : record.enteredEntities()) {
			Entity entity = findLiveEntity(server, entityId);
			if (entity != null && entity.level() instanceof ServerLevel level) {
				return level;
			}
		}
		if (record.generated()) {
			ServerLevel legacyLevel = findFirstExistingLevel(server, record.turtleLevel(), record.returnLevel());
			if (legacyLevel != null) {
				return legacyLevel;
			}
		}
		return null;
	}

	private static ServerLevel findFirstExistingLevel(MinecraftServer server, String... levelIds) {
		for (String levelId : levelIds) {
			if (levelId != null) {
				ServerLevel level = findLevelById(server, levelId);
				if (level != null) {
					return level;
				}
			}
		}
		return null;
	}

	private static ServerLevel findRoomLevelForEntry(MinecraftServer server, RoomRecord record, ServerLevel fallback) {
		ServerLevel existingRoomLevel = findRoomLevel(server, record);
		if (existingRoomLevel != null) {
			return existingRoomLevel;
		}
		if (record.generated()) {
			return fallback;
		}
		ServerLevel mrPresidentLevel = server.getLevel(ModDimensions.MR_PRESIDENT);
		return mrPresidentLevel != null ? mrPresidentLevel : fallback;
	}

	private static void ensureRoomChunkLoaded(MinecraftServer server, RoomRecord record) {
		ServerLevel roomLevel = findRoomLevel(server, record);
		if (roomLevel != null) {
			ensureRoomChunkLoaded(roomLevel, record);
		}
	}

	private static void ensureRoomChunkLoaded(ServerLevel level, RoomRecord record) {
		RoomSectionPos section = roomSection(record);
		if (section != null) {
			level.getChunk(section.x(), section.z());
			return;
		}
		BlockPos lowerCorner = roomLowerCornerFor(record);
		level.getChunk(lowerCorner.getX() >> 4, lowerCorner.getZ() >> 4);
	}

	private RoomRecord ensureRoomSectionAllocated(RoomRecord record) {
		if (roomSection(record) != null || record.generated()) {
			return record;
		}
		RoomSectionPos pos = posForNewRoom();
		RoomRecord updated = new RoomRecord(
				record.turtleUuid(),
				record.roomId(),
				record.generated(),
				record.roomLevel(),
				pos.x(),
				pos.y(),
				pos.z(),
				record.occupyingPlayer(),
				record.enteredEntities(),
				record.returnLevel(),
				record.returnX(),
				record.returnY(),
				record.returnZ(),
				record.turtleLevel(),
				record.turtleX(),
				record.turtleY(),
				record.turtleZ());
		roomsByTurtle.put(record.turtleUuid(), updated);
		setDirty();
		return updated;
	}

	private RoomSectionPos posForNewRoom() {
		int x = 0;
		int y = 0;
		int z = 0;
		int ring = 0;

		RoomSectionPos pos = new RoomSectionPos(x, y, z);
		while (isRoomSectionAllocated(pos)) {
			if (y < 15) {
				y++;
			}
			else {
				y = 0;
				if (x == ring) {
					ring++;
					x = ring - 1;
					z = 1;
				}
				else if (z == ring) {
					z--;
					x = -1;
				}
				else if (x == -ring) {
					x++;
					z = -1;
				}
				else if (z == -ring) {
					z++;
					x = 1;
				}
				else if (x > 0 && z > 0) {
					x--;
					z++;
				}
				else if (x < 0 && z > 0) {
					x--;
					z--;
				}
				else if (x < 0 && z < 0) {
					x++;
					z--;
				}
				else if (x > 0 && z < 0) {
					x++;
					z++;
				}
				else {
					throw new IllegalStateException("Unexpected Mr President room allocation state");
				}
			}
			pos = new RoomSectionPos(x, y, z);
		}
		return pos;
	}

	private boolean isRoomSectionAllocated(RoomSectionPos pos) {
		for (RoomRecord record : roomsByTurtle.values()) {
			if (pos.equals(roomSection(record))) {
				return true;
			}
		}
		return false;
	}

	private static RoomSectionPos roomSection(RoomRecord record) {
		if (record.roomSectionX() != null && record.roomSectionY() != null && record.roomSectionZ() != null) {
			return new RoomSectionPos(record.roomSectionX(), record.roomSectionY(), record.roomSectionZ());
		}
		return null;
	}

	private static List<UUID> addEnteredEntity(List<UUID> enteredEntities, UUID entityUuid) {
		if (enteredEntities.contains(entityUuid)) {
			return enteredEntities;
		}
		List<UUID> updated = new ArrayList<>(enteredEntities);
		updated.add(entityUuid);
		return updated;
	}

	private static List<UUID> removeEnteredEntity(List<UUID> enteredEntities, UUID entityUuid) {
		if (!enteredEntities.contains(entityUuid)) {
			return enteredEntities;
		}
		List<UUID> updated = new ArrayList<>(enteredEntities);
		updated.remove(entityUuid);
		return updated;
	}

	private record ReturnTarget(ServerLevel level, String levelId, double x, double y, double z) {}

	private record RoomSectionPos(int x, int y, int z) {
		private BlockPos blockPosition() {
			return new BlockPos(x << 4, y << 4, z << 4);
		}

		private ListTag toNbt() {
			ListTag nbt = new ListTag();
			nbt.add(IntTag.valueOf(x));
			nbt.add(IntTag.valueOf(y));
			nbt.add(IntTag.valueOf(z));
			return nbt;
		}

		private static RoomSectionPos fromNbt(ListTag nbt) {
			if (nbt.size() == 3 && nbt.getElementType() == Tag.TAG_INT) {
				return new RoomSectionPos(nbt.getInt(0), nbt.getInt(1), nbt.getInt(2));
			}
			return null;
		}
	}

	public record RoomRecord(UUID turtleUuid, UUID roomId, boolean generated, String roomLevel,
			Integer roomSectionX, Integer roomSectionY, Integer roomSectionZ, UUID occupyingPlayer,
			List<UUID> enteredEntities, String returnLevel, Double returnX, Double returnY, Double returnZ,
			String turtleLevel, Double turtleX, Double turtleY, Double turtleZ) {
		private static final String TURTLE_UUID = "Turtle";
		private static final String ROOM_UUID = "Room";
		private static final String GENERATED = "Generated";
		private static final String ROOM_LEVEL = "RoomLevel";
		private static final String ROOM_POS = "Pos";
		private static final String OCCUPYING_PLAYER = "OccupyingPlayer";
		private static final String ENTERED_ENTITIES = "EnteredEntities";
		private static final String RETURN_LEVEL = "ReturnLevel";
		private static final String RETURN_X = "ReturnX";
		private static final String RETURN_Y = "ReturnY";
		private static final String RETURN_Z = "ReturnZ";
		private static final String TURTLE_LEVEL = "TurtleLevel";
		private static final String TURTLE_X = "TurtleX";
		private static final String TURTLE_Y = "TurtleY";
		private static final String TURTLE_Z = "TurtleZ";

		public RoomRecord {
			enteredEntities = enteredEntities != null ? List.copyOf(enteredEntities) : List.of();
		}

		public CompoundTag toNbt() {
			CompoundTag nbt = new CompoundTag();
			nbt.putUUID(TURTLE_UUID, turtleUuid);
			nbt.putUUID(ROOM_UUID, roomId);
			nbt.putBoolean(GENERATED, generated);
			if (roomLevel != null) {
				nbt.putString(ROOM_LEVEL, roomLevel);
			}
			RoomSectionPos roomSection = roomSection(this);
			if (roomSection != null) {
				nbt.put(ROOM_POS, roomSection.toNbt());
			}
			if (occupyingPlayer != null) {
				nbt.putUUID(OCCUPYING_PLAYER, occupyingPlayer);
			}
			if (!enteredEntities.isEmpty()) {
				ListTag entityIds = new ListTag();
				for (UUID entityId : enteredEntities) {
					entityIds.add(NbtUtils.createUUID(entityId));
				}
				nbt.put(ENTERED_ENTITIES, entityIds);
			}
			if (returnLevel != null) {
				nbt.putString(RETURN_LEVEL, returnLevel);
			}
			if (returnX != null) {
				nbt.putDouble(RETURN_X, returnX);
			}
			if (returnY != null) {
				nbt.putDouble(RETURN_Y, returnY);
			}
			if (returnZ != null) {
				nbt.putDouble(RETURN_Z, returnZ);
			}
			if (turtleLevel != null) {
				nbt.putString(TURTLE_LEVEL, turtleLevel);
			}
			if (turtleX != null) {
				nbt.putDouble(TURTLE_X, turtleX);
			}
			if (turtleY != null) {
				nbt.putDouble(TURTLE_Y, turtleY);
			}
			if (turtleZ != null) {
				nbt.putDouble(TURTLE_Z, turtleZ);
			}
			return nbt;
		}

		public static RoomRecord fromNbt(CompoundTag nbt) {
			if (!nbt.hasUUID(TURTLE_UUID) || !nbt.hasUUID(ROOM_UUID)) {
				return null;
			}
			UUID turtleUuid = nbt.getUUID(TURTLE_UUID);
			UUID roomId = nbt.getUUID(ROOM_UUID);
			boolean generated = nbt.getBoolean(GENERATED);
			String roomLevel = nbt.contains(ROOM_LEVEL) ? nbt.getString(ROOM_LEVEL) : null;
			RoomSectionPos roomSection = RoomSectionPos.fromNbt(nbt.getList(ROOM_POS, Tag.TAG_INT));
			UUID occupyingPlayer = nbt.hasUUID(OCCUPYING_PLAYER) ? nbt.getUUID(OCCUPYING_PLAYER) : null;
			List<UUID> enteredEntities = new ArrayList<>();
			ListTag enteredEntityIds = nbt.getList(ENTERED_ENTITIES, Tag.TAG_INT_ARRAY);
			for (Tag element : enteredEntityIds) {
				UUID entityId = NbtUtils.loadUUID(element);
				if (entityId != null) {
					enteredEntities.add(entityId);
				}
			}
			String returnLevel = nbt.contains(RETURN_LEVEL) ? nbt.getString(RETURN_LEVEL) : null;
			Double returnX = nbt.contains(RETURN_X) ? nbt.getDouble(RETURN_X) : null;
			Double returnY = nbt.contains(RETURN_Y) ? nbt.getDouble(RETURN_Y) : null;
			Double returnZ = nbt.contains(RETURN_Z) ? nbt.getDouble(RETURN_Z) : null;
			String turtleLevel = nbt.contains(TURTLE_LEVEL) ? nbt.getString(TURTLE_LEVEL) : null;
			Double turtleX = nbt.contains(TURTLE_X) ? nbt.getDouble(TURTLE_X) : null;
			Double turtleY = nbt.contains(TURTLE_Y) ? nbt.getDouble(TURTLE_Y) : null;
			Double turtleZ = nbt.contains(TURTLE_Z) ? nbt.getDouble(TURTLE_Z) : null;
			return new RoomRecord(turtleUuid, roomId, generated, roomLevel,
					roomSection != null ? roomSection.x() : null,
					roomSection != null ? roomSection.y() : null,
					roomSection != null ? roomSection.z() : null,
					occupyingPlayer, enteredEntities,
					returnLevel, returnX, returnY, returnZ, turtleLevel, turtleX, turtleY, turtleZ);
		}
	}
}
