package xyz.wagyourtail.minimap.map.chunkdata.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.resources.ResourceLocation;
import xyz.wagyourtail.minimap.api.MinimapApi;
import xyz.wagyourtail.minimap.map.MapServer;
import xyz.wagyourtail.minimap.map.chunkdata.ChunkData;
import xyz.wagyourtail.minimap.map.chunkdata.ChunkLocation;
import xyz.wagyourtail.minimap.waypoint.Waypoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZipCacher extends AbstractCacher {

    private final LoadingCache<Path, FileSystem> zipCache;

    public ZipCacher() {
        zipCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.SECONDS).
            removalListener(e -> {
                try {
                    ((FileSystem) e.getValue()).close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }).build(
                new CacheLoader<>() {
                    @Override
                    public FileSystem load(Path key) throws Exception {
                        if (Files.notExists(key.getParent())) Files.createDirectories(key.getParent());
                        return FileSystems.newFileSystem(key, Map.of("create", true));
                    }
                }
            );
    }

    public synchronized FileSystem getRegionZip(ChunkLocation location) {
        try {
            return zipCache.get(locationToPath(location));
        } catch (ExecutionException ignored) {}
        return null;
    }

    @Override
    public synchronized ChunkData loadChunk(ChunkLocation location) {
        FileSystem zipfs = getRegionZip(location);
        if (zipfs == null) return null;
        Path dataPath = zipfs.getPath(location.index() + ".data");
        Path resourcesPath = zipfs.getPath(location.index() + ".resources");
        if (Files.exists(dataPath) && Files.exists(resourcesPath)) {
            return loadFromDisk(location, dataPath, resourcesPath);
        }
        return null;
    }

    private Path locationToPath(ChunkLocation location) {
        return MinimapApi.getInstance().configFolder.resolve(location.level().parent.server_slug).resolve(location.level().level_slug).resolve(location.region().getString() + ".zip");
    }

    @Override
    public synchronized void saveChunk(ChunkLocation location, ChunkData data) {
        if (!data.changed) return;
        FileSystem zipfs = getRegionZip(location);
        if (zipfs == null) throw new NullPointerException("Zip file system is null");
        Path dataPath = zipfs.getPath(location.index() + ".data");
        Path resourcesPath = zipfs.getPath(location.index() + ".resources");
        writeToZip(dataPath, resourcesPath, data);
    }

    private synchronized void writeToZip(Path dataPath, Path resourcesPath, ChunkData chunk) {
        try {
            String resources = chunk.getResources().stream().map(ResourceLocation::toString).reduce("", (a, b) -> a + b + "\n");
            Files.writeString(resourcesPath, resources);
            ByteBuffer data = ByteBuffer.allocate(Long.BYTES + Integer.BYTES * 256 * 6 + Byte.BYTES * 256);
            data.putLong(chunk.updateTime);
            for (int i = 0; i < 256; ++i) {
                data.putInt(chunk.heightmap[i]);
            }
            for (int i = 0; i < 256; ++i) {
                data.put(chunk.blocklight[i]);
            }
            for (int i = 0; i < 256; ++i) {
                data.putInt(chunk.blockid[i]);
            }
            for (int i = 0; i < 256; ++i) {
                data.putInt(chunk.biomeid[i]);
            }
            for (int i = 0; i < 256; ++i) {
                data.putInt(chunk.oceanFloorHeightmap[i]);
            }
            for (int i = 0; i < 256; ++i) {
                data.putInt(chunk.oceanFloorBlockid[i]);
            }
            Files.write(dataPath, data.array());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized void saveWaypoints(MapServer server, Stream<Waypoint> waypointList) {
        Path wpFile = serverPath(server).resolve("way.points");
        String points = waypointList.map(Waypoint::serialize).collect(Collectors.joining("\n"));
        try {
            Files.writeString(wpFile, points);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path serverPath(MapServer server) {
        return MinimapApi.getInstance().configFolder.resolve(server.server_slug);
    }

    @Override
    public synchronized List<Waypoint> loadWaypoints(MapServer server) {
        Path wpFile = serverPath(server).resolve("way.points");
        List<Waypoint> points = new ArrayList<>();
        if (Files.exists(wpFile)) {
            try {
                return Files.readAllLines(wpFile).stream().map(Waypoint::deserialize).collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void close() {
        zipCache.invalidateAll();
        zipCache.cleanUp();
    }

    private ChunkData loadFromDisk(ChunkLocation location, Path dataPath, Path resourcesPath) {
        ChunkData chunk = new ChunkData(location);
        try (InputStream stream = Files.newInputStream(dataPath)) {
            ByteBuffer data = ByteBuffer.wrap(stream.readAllBytes());
            data.rewind();
            chunk.updateTime = data.getLong();
            for (int i = 0; i < 256; ++i) {
                chunk.heightmap[i] = data.getInt();
            }
            for (int i = 0; i < 256; ++i) {
                chunk.blocklight[i] = data.get();
            }
            for (int i = 0; i < 256; ++i) {
                chunk.blockid[i] = data.getInt();
            }
            for (int i = 0; i < 256; ++i) {
                chunk.biomeid[i] = data.getInt();
            }
            for (int i = 0; i < 256; ++i) {
                chunk.oceanFloorHeightmap[i] = data.getInt();
            }
            for (int i = 0; i < 256; ++i) {
                chunk.oceanFloorBlockid[i] = data.getInt();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try (InputStream stream = Files.newInputStream(resourcesPath)) {
            for (String resource : new String(stream.readAllBytes()).split("\n")) {
                chunk.getOrRegisterResourceLocation(new ResourceLocation(resource));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return chunk;
    }

}
