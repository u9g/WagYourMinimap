package xyz.wagyourtail.minimap.map.chunkdata.cache;

import xyz.wagyourtail.minimap.map.MapServer;
import xyz.wagyourtail.minimap.map.chunkdata.ChunkData;
import xyz.wagyourtail.minimap.map.chunkdata.ChunkLocation;
import xyz.wagyourtail.minimap.waypoint.Waypoint;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public abstract class AbstractCacher {
    public abstract ChunkData loadChunk(ChunkLocation location);

    public abstract void saveChunk(ChunkLocation location, ChunkData data);

    public abstract void saveWaypoints(MapServer server, Stream<Waypoint> waypointList);

    public abstract List<Waypoint> loadWaypoints(MapServer server);

    public abstract void close();

}
