package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.graph.DimensionPalette;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class SingleBlockEntityEdgePoint extends TrackEdgePoint {

	private static final String BLOCK_ENTITY_POSITION = "BlockEntityPos";
	private static final String BLOCK_ENTITY_DIMENSION = "BlockEntityDimension";
	private static final String LEGACY_BLOCK_ENTITY_POSITION = "TilePos";
	private static final String LEGACY_BLOCK_ENTITY_DIMENSION = "TileDimension";

	public ResourceKey<Level> blockEntityDimension;
	public BlockPos blockEntityPos;

	public BlockPos getBlockEntityPos() {
		return blockEntityPos;
	}

	public ResourceKey<Level> getBlockEntityDimension() {
		return blockEntityDimension;
	}

	@Override
	public void blockEntityAdded(BlockEntity blockEntity, boolean front) {
		this.blockEntityPos = blockEntity.getBlockPos();
		this.blockEntityDimension = blockEntity.getLevel()
			.dimension();
	}

	@Override
	public void blockEntityRemoved(BlockPos blockEntityPos, boolean front) {
		removeFromAllGraphs();
	}

	@Override
	public void invalidate(LevelAccessor level) {
		invalidateAt(level, blockEntityPos);
	}

	@Override
	public boolean canMerge() {
		return false;
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
		super.read(nbt, registries, migration, dimensions);
		if (migration)
			return;
		String positionKey = nbt.contains(BLOCK_ENTITY_POSITION) ? BLOCK_ENTITY_POSITION : LEGACY_BLOCK_ENTITY_POSITION;
		String dimensionKey =
			nbt.contains(BLOCK_ENTITY_DIMENSION) ? BLOCK_ENTITY_DIMENSION : LEGACY_BLOCK_ENTITY_DIMENSION;
		blockEntityPos = NBTHelper.readBlockPos(nbt, positionKey);
		blockEntityDimension = dimensions.decode(nbt.contains(dimensionKey) ? nbt.getInt(dimensionKey) : -1);
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
		super.write(nbt, registries, dimensions);
		nbt.put(BLOCK_ENTITY_POSITION, NbtUtils.writeBlockPos(blockEntityPos));
		nbt.putInt(BLOCK_ENTITY_DIMENSION, dimensions.encode(blockEntityDimension));
	}

}
