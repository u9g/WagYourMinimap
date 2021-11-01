package xyz.wagyourtail.minimap.map.chunkdata.parts;

import xyz.wagyourtail.minimap.map.chunkdata.ChunkData;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

public abstract class DataPart<T extends DataPart<?>> {
    public final ChunkData parent;

    /**
     * empty data
     *
     * @param parent container
     */
    public DataPart(ChunkData parent) {
        this.parent = parent;
    }

    public abstract void mergeFrom(T other);

    public abstract void deserialize(ByteBuffer buffer);

    public abstract void serialize(ByteBuffer buffer);

    public abstract int getBytes();

    public abstract void usedResourceLocations(Set<Integer> used);

    public abstract void remapResourceLocations(Map<Integer, Integer> map);
}
