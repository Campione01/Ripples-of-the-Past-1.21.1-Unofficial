package com.github.standobyte.jojo.entityattachment.syncheddata;

import java.util.Arrays;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

public class SynchedDataBuilder {
	public final SyncedDataHolderExtended entity;
	public SynchedEntityData.DataItem<?>[] itemsById = new SynchedEntityData.DataItem<?>[0];

	public SynchedDataBuilder(SyncedDataHolderExtended entity) {
		this.entity = entity;
	}

	public <T> SynchedDataBuilder define(EntityDataAccessor<T> key, T value) {
		int id = key.id();
		if (id >= itemsById.length) {
			itemsById = Arrays.copyOf(itemsById, id + 1);
		}
		if (itemsById[id] != null) {
			throw new IllegalArgumentException("Duplicate id value for " + id + "!");
		}
		itemsById[id] = new SynchedEntityData.DataItem<>(key, value);
		return this;
	}

	public SynchedEntityData.DataItem<?>[] itemsById() {
		for (int i = 0; i < itemsById.length; i++) {
			if (itemsById[i] == null) {
				throw new IllegalStateException("Missing definition for synched data id " + i + " in " + entity.getClass().getName());
			}
		}
		return itemsById;
	}
}
