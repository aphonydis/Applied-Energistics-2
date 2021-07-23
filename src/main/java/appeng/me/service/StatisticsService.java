/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.server.ServerWorld;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.events.statistics.GridChunkEvent;
import appeng.me.InWorldGridNode;

/**
 * A grid providing precomupted statistics about a network.
 * <p>
 * Currently this tracks the chunks a network is occupying.
 */
public class StatisticsService implements IGridService, IGridServiceProvider {

    private final IGrid grid;

    /**
     * This uses a {@link Multiset} so we can simply add or remove {@link IGridNode} without having to take into account
     * that others still might exist without explicitly counting these.
     */
    private final Map<IWorld, Multiset<ChunkPos>> chunks;

    public StatisticsService(final IGrid g) {
        this.grid = g;
        this.chunks = new HashMap<>();
    }

    @Override
    public void removeNode(final IGridNode node) {
        if (node instanceof InWorldGridNode inWorldNode) {
            this.removeChunk(inWorldNode.getWorld(), inWorldNode.getLocation());
        }
    }

    @Override
    public void addNode(final IGridNode node) {
        if (node instanceof InWorldGridNode inWorldNode) {
            this.addChunk(inWorldNode.getWorld(), inWorldNode.getLocation());
        }
    }

    public IGrid getGrid() {
        return grid;
    }

    /**
     * A set of all {@link IWorld} this grid spans.
     *
     * @return
     */
    public Set<IWorld> worlds() {
        return this.chunks.keySet();
    }

    /**
     * A set of chunks this grid spans in a specific world.
     *
     * @param world
     * @return
     */
    public Set<ChunkPos> chunks(IWorld world) {
        return this.chunks.get(world).elementSet();
    }

    public Map<IWorld, Multiset<ChunkPos>> getChunks() {
        return this.chunks;
    }

    /**
     * Mark the chunk of the {@link BlockPos} as location of the network.
     *
     * @param world
     * @param pos
     * @return
     */
    private boolean addChunk(IWorld world, BlockPos pos) {
        final ChunkPos position = new ChunkPos(pos);

        if (!this.getChunks(world).contains(position)) {
            this.grid.postEvent(new GridChunkEvent.GridChunkAdded((ServerWorld) world, position));
        }

        return this.getChunks(world).add(position);
    }

    /**
     * Remove the chunk of this {@link BlockPos} from the network locations.
     * <p>
     * This uses a {@link Multiset} to ensure it will only marked as no longer containing a grid once all other
     * gridnodes are removed as well.
     *
     * @param world
     * @param pos
     * @return
     */
    private boolean removeChunk(IWorld world, BlockPos pos) {
        final ChunkPos position = new ChunkPos(pos);
        boolean ret = this.getChunks(world).remove(position);

        if (ret && !this.getChunks(world).contains(position)) {
            this.grid.postEvent(new GridChunkEvent.GridChunkRemoved((ServerWorld) world, position));
        }

        this.clearWorld(world);

        return ret;
    }

    private Multiset<ChunkPos> getChunks(IWorld world) {
        return this.chunks.computeIfAbsent(world, w -> HashMultiset.create());
    }

    /**
     * Cleanup the map in case a whole world is unloaded
     *
     * @param world
     */
    private void clearWorld(IWorld world) {
        if (this.chunks.get(world).isEmpty()) {
            this.chunks.remove(world);
        }
    }
}