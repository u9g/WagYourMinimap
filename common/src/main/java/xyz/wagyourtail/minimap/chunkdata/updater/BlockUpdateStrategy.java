package xyz.wagyourtail.minimap.chunkdata.updater;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import xyz.wagyourtail.minimap.api.MinimapApi;
import xyz.wagyourtail.minimap.chunkdata.ChunkData;
import xyz.wagyourtail.minimap.chunkdata.parts.SurfaceDataPart;
import xyz.wagyourtail.minimap.map.MapServer;

public class BlockUpdateStrategy extends AbstractChunkUpdateStrategy<SurfaceDataPart> {
    public static final Event<BlockUpdate> BLOCK_UPDATE_EVENT = EventFactory.createLoop();

    public BlockUpdateStrategy() {
        super();
    }


    public SurfaceDataPart updateYCol(ChunkData parent, SurfaceDataPart data, ChunkAccess chunk, MapServer.MapLevel level, Level mclevel, BlockPos bp) {
        if (data == null) {
            return ChunkLoadStrategy.loadFromChunk(chunk, level, mclevel, parent, data);
        }
        data.parent.updateTime = System.currentTimeMillis();
        Registry<Biome> biomeRegistry = mclevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        int x = bp.getX();
        int z = bp.getZ();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(x, 0, z);
        int i = SurfaceDataPart.blockPosToIndex(blockPos);
        if (mclevel.dimensionType().hasCeiling()) {
            int ceiling = mclevel.dimensionType().logicalHeight() - 1;
            boolean air = false;
            for (int j = ceiling; j > mclevel.getMinBuildHeight(); --j) {
                BlockState block = chunk.getBlockState(blockPos.setY(j));
                if (block.getBlock() instanceof AirBlock) {
                    air = true;
                } else if (air) {
                    data.heightmap[i] = j;
                    data.blockid[i] = parent.getOrRegisterBlockState(block);
                    data.biomeid[i] = parent.getOrRegisterBiome(biomeRegistry.getKey(
                        chunk.getNoiseBiome(x >> 2, data.heightmap[i] >> 2, z >> 2)
                    ));
                    break;
                }
            }
        } else {
            data.heightmap[i] = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockState top = chunk.getBlockState(blockPos.setY(data.heightmap[i]));
            data.blockid[i] = parent.getOrRegisterBlockState(top);
            data.biomeid[i] = parent.getOrRegisterBiome(biomeRegistry.getKey(
                chunk.getNoiseBiome(x >> 2, data.heightmap[i] >> 2, z >> 2)
            ));
            if (top.getBlock().equals(Blocks.WATER)) {
                BlockState b = top;
                while (b.getBlock().equals(Blocks.WATER)) {
                    b = chunk.getBlockState(blockPos.setY(blockPos.getY() - 1));
                }
                data.oceanFloorHeightmap[i] = blockPos.getY();
                data.oceanFloorBlockid[i] = parent.getOrRegisterBlockState(chunk.getBlockState(blockPos));
            }
        }
        parent.markDirty();
        return data;
    }

    public SurfaceDataPart updateLighting(ChunkData parent, SurfaceDataPart data, ChunkAccess chunk, MapServer.MapLevel level, Level mclevel) {
        if (data == null) {
            return ChunkLoadStrategy.loadFromChunk(chunk, level, mclevel, parent, data);
        }
        data.parent.updateTime = System.currentTimeMillis();
        ChunkPos pos = chunk.getPos();
        //TODO: replace with chunk section stuff to not use a MutableBlockPos at all (see baritone), maybe not possible since we need light levels too
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        LayerLightEventListener light = getBlockLightLayer(mclevel);
        for (int i = 0; i < 256; ++i) {
            int x = (i >> 4) % 16;
            int z = i % 16;
            data.blocklight[i] = (byte) light.getLightValue(blockPos.set((pos.x << 4) + x, data.heightmap[i] + 1, (pos.z << 4) + z));
        }
        parent.markDirty();
        return data;
    }

    public void updateNeighborLighting(MapServer.MapLevel level, Level mclevel, int chunkX, int chunkZ) {
        for (int i = chunkX - 1; i < chunkX + 2; ++i) {
            for (int j = chunkZ - 1; j < chunkZ + 2; ++j) {
                if (mclevel.hasChunk(i, j)) {
                    ChunkAccess chunk = mclevel.getChunk(i, j, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue;
                    }
                    //TODO: update lighting only function
                    updateChunk(
                        getChunkLocation(level, i, j),
                        (location, parent, oldData) -> updateLighting(parent, oldData, chunk, level, mclevel)
                    );
                }
            }
        }
    }

    @Override
    protected void registerEventListener() {
        BLOCK_UPDATE_EVENT.register((pos, level) -> {
            if (level != mc.level) {
                return;
            }
            MapServer.MapLevel mapLevel = MinimapApi.getInstance().getMapServer().getCurrentLevel();
            ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            if (chunk == null) {
                return;
            }
            updateChunk(
                getChunkLocation(mapLevel, pos.getX() >> 4, pos.getZ() >> 4),
                (location, parent, oldData) -> updateYCol(parent, oldData, chunk, mapLevel, level, pos)
            );
            updateNeighborLighting(mapLevel, level, pos.getX() >> 4, pos.getZ() >> 4);
        });
    }

    @Override
    public Class<SurfaceDataPart> getType() {
        return SurfaceDataPart.class;
    }

    public interface BlockUpdate {
        void onBlockUpdate(BlockPos pos, Level level);

    }

}
