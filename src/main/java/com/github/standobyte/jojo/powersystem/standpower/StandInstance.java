package com.github.standobyte.jojo.powersystem.standpower;

import java.util.Objects;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.network.s2c.TrPowerStandInstancePacket;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandInstance {
	private final Either<StandType, ResourceLocation> standType;
	private final EnumSet<StandPart> parts = EnumSet.allOf(StandPart.class);
	private Optional<ResourceLocation> skin = Optional.empty();
	private boolean dirty;
	
	@Nullable
	public static StandInstance fromExistingStandId(ResourceLocation standId) {
		StandType standType = StandType.fromId(standId);
		return standType != null ? new StandInstance(standType) : null;
	}
	
	protected static StandInstance fromStandId(ResourceLocation standId) {
		StandType stand = StandType.fromId(standId);
		return new StandInstance(stand != null ? Either.left(stand) : Either.right(standId));
	}
	
	public StandInstance(@Nonnull StandType standType) {
		this.standType = Either.left(standType);
	}
	
	protected StandInstance(Either<StandType, ResourceLocation> standType) {
		this.standType = standType;
	}

	@Nullable
	public StandType getStandType() {
		return standType.left().filter(StandType::isEnabled).orElse(null);
	}
	
	public boolean standExists() {
		return standType.left().filter(StandType::isEnabled).isPresent();
	}
	
	public ResourceLocation getStandId() {
		return standType.map(StandType::getId, Function.identity());
	}
	
	public boolean hasPart(StandPart part) {
		return parts.contains(part);
	}
	
	public boolean removePart(StandPart part) {
		boolean removed = parts.remove(part);
		dirty |= removed;
		return removed;
	}
	
	public boolean addPart(StandPart part) {
		boolean added = parts.add(part);
		dirty |= added;
		return added;
	}
	
	public EnumSet<StandPart> getAllParts() {
		return parts;
	}
	
	private EnumSet<StandPart> getMissingParts() {
		EnumSet<StandPart> missingParts = EnumSet.allOf(StandPart.class);
		missingParts.removeAll(parts);
		return missingParts;
	}
	
	private List<StandPart> getMissingPartsForCodec() {
		return getMissingParts().stream().toList();
	}
	
	public void syncIfDirty(LivingEntity standUser) {
		if (dirty && standUser != null && !standUser.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(standUser, new TrPowerStandInstancePacket(standUser.getId(), Optional.of(this)));
		}
		dirty = false;
	}
	
	
	@ApiStatus.Internal
	public void setCustomSkin(Optional<ResourceLocation> skin) {
		Objects.requireNonNull(skin);
		this.skin = skin;
	}
	
	public Optional<ResourceLocation> getSelectedSkin() {
		return skin;
	}
	
	
	@Override
	public int hashCode() {
		return Objects.hash(getStandId(), parts, skin);
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof StandInstance other
				&& this.getStandId().equals(other.getStandId())
				&& this.parts.equals(other.parts)
				&& this.skin.equals(other.skin);
	}
	
	public StandInstance copy() {
		StandInstance stand = new StandInstance(this.standType);
		stand.parts.clear();
		stand.parts.addAll(this.parts);
		stand.setCustomSkin(this.skin);
		return stand;
	}
	
	
	@Nullable
	public Component getStandName(boolean clientSide) {
		StandType stand = getStandType();
		if (stand == null) {
			MutableComponent name = Component.translatable(Util.makeDescriptionId("stand", getStandId()));
			return name.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
		}
		Component name = stand.name.get();
		if (clientSide) {
			StandSkin skin = StandSkinsLoader.getInstance().getSkin(this);
			if (skin != null) {
				int color = skin.getColor();
				return name.copy().withColor(color);
			}
		}
		return name;
	}
	
	
	public static final Codec<StandInstance> CODEC = RecordCodecBuilder.create(
			builder -> builder.group(
					ResourceLocation.CODEC.fieldOf("stand_type").forGetter(StandInstance::getStandId),
					ResourceLocation.CODEC.optionalFieldOf("skin").forGetter(StandInstance::getSelectedSkin),
					StandPart.CODEC.listOf().optionalFieldOf("missing_limbs", List.of()).forGetter(StandInstance::getMissingPartsForCodec))
			.apply(builder, 
					(ResourceLocation standId, Optional<ResourceLocation> standSkin, List<StandPart> missingParts) -> {
						StandInstance stand = StandInstance.fromStandId(standId);
						stand.setCustomSkin(standSkin);
						stand.parts.removeAll(missingParts);
						return stand;
					}));
	
	
	/**
	 * Because of datapack Stands, if the Stand type query were to happen right at the moment of packet decoding, 
	 * it would be too early, when the datapack packet is not handled yet,
	 * so we need an intermediate class that lazily creates the Stand instance
	 */
	public static class NetworkData {
		private final ResourceLocation standTypeId;
		private final Optional<ResourceLocation> skin;
		private final EnumSet<StandPart> missingParts;
		private StandInstance standInstance;
		
		public static NetworkData wrap(@Nonnull StandInstance standInstance) {
			NetworkData data = new NetworkData(standInstance.getStandId(), standInstance.skin, standInstance.getMissingParts());
			data.standInstance = standInstance;
			return data;
		}
		
		public NetworkData(ResourceLocation standTypeId, Optional<ResourceLocation> skin, EnumSet<StandPart> missingParts) {
			this.standTypeId = standTypeId;
			this.skin = skin;
			this.missingParts = copyParts(missingParts);
		}
		
		public static void encode(FriendlyByteBuf buffer, StandInstance instance) {
			NETWORK_CODEC.encode(buffer, wrap(instance));
		}
		
		public static final StreamCodec<FriendlyByteBuf, StandInstance.NetworkData> NETWORK_CODEC = new StreamCodec<>() {
			@Override
			public StandInstance.NetworkData decode(FriendlyByteBuf buffer) {
				ResourceLocation standTypeId = ResourceLocation.STREAM_CODEC.decode(buffer);
				Optional<ResourceLocation> skin = ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buffer);
				EnumSet<StandPart> missingParts = EnumSet.noneOf(StandPart.class);
				int missingPartsCount = buffer.readVarInt();
				for (int i = 0; i < missingPartsCount; i++) {
					missingParts.add(buffer.readEnum(StandPart.class));
				}
				return new StandInstance.NetworkData(standTypeId, skin, missingParts);
			}
			
			@Override
			public void encode(FriendlyByteBuf buffer, StandInstance.NetworkData instance) {
				ResourceLocation.STREAM_CODEC.encode(buffer, instance.standTypeId);
				ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buffer, instance.skin);
				EnumSet<StandPart> missingParts = instance.missingParts;
				buffer.writeVarInt(missingParts.size());
				for (StandPart part : missingParts) {
					buffer.writeEnum(part);
				}
			}
		};
		
		public StandInstance get() {
			if (this.standInstance == null) {
				this.standInstance = StandInstance.fromStandId(standTypeId);
				this.standInstance.skin = this.skin;
				this.standInstance.parts.removeAll(this.missingParts);
			}
			return this.standInstance;
		}
		
		private static EnumSet<StandPart> copyParts(EnumSet<StandPart> parts) {
			EnumSet<StandPart> copy = EnumSet.noneOf(StandPart.class);
			copy.addAll(parts);
			return copy;
		}
	}
	
	public enum StandPart {
		MAIN_BODY("main_body"),
		ARMS("arms"),
		LEGS("legs");
		
		public static final Codec<StandPart> CODEC = Codec.STRING.comapFlatMap(
				StandPart::read, StandPart::serializedName);
		
		private final String serializedName;
		
		StandPart(String serializedName) {
			this.serializedName = serializedName;
		}
		
		public String serializedName() {
			return serializedName;
		}
		
		public static StandPart fromName(String name) {
			String normalized = name.toLowerCase(Locale.ROOT);
			for (StandPart part : values()) {
				if (part.serializedName.equals(normalized) || part.name().toLowerCase(Locale.ROOT).equals(normalized)) {
					return part;
				}
			}
			return null;
		}
		
		private static DataResult<StandPart> read(String name) {
			StandPart part = fromName(name);
			return part != null ? DataResult.success(part) : DataResult.error(() -> "Unknown stand part: " + name);
		}
	}

}
