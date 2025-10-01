package org.dynmap.storage.aws_s3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.PlayerFaces.FaceType;
import org.dynmap.WebAuthManager;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTileEnumCB;
import org.dynmap.storage.MapStorageBaseTileEnumCB;
import org.dynmap.storage.MapStorageTileSearchEndCB;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

import com.github.davidmoten.aws.lw.client.Client;
import com.github.davidmoten.aws.lw.client.HttpMethod;
import com.github.davidmoten.aws.lw.client.Response;
import com.github.davidmoten.aws.lw.client.ServiceException;

public class AWSS3MapStorage extends MapStorage {
    public class StorageTile extends MapStorageTile {
        private final String baseKey;
        private final String uri;
        
        StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);
            
            String baseURI;
            if (zoom > 0) {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y;
            }
            else {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y;
            }
            uri = baseURI + "." + map.getImageFormat().getFileExt();
            baseKey = AWSS3MapStorage.this.prefix + "tiles/" + world.getName() + "/" + uri;
        }
        @Override
        public boolean exists() {
                boolean exists = false;
                Client s3 = null;
                try {
                        s3 = getConnection();
                        s3.path(bucketname, baseKey).method(HttpMethod.HEAD).execute();
                        exists = true;
            } catch (ServiceException x) {
                if (!x.getMessage().contains("NoSuchKey") && !x.getMessage().contains("404")) {
                        logS3Error("HEAD (exists check)", baseKey, x);
                }
            } catch (StorageShutdownException x) {
                
                } finally {
                        releaseConnection(s3);
                }
            return exists;
        }

        @Override
        public boolean matchesHashCode(long hash) {
                boolean matches = false;
                Client s3 = null;
                try {
                        s3 = getConnection();
                        Response response = s3.path(bucketname, baseKey).method(HttpMethod.HEAD).response();
                        String metadataHeader = getResponseHeader(response, "x-amz-meta-x-dynmap-hash");
                        if (metadataHeader != null) {
                                long storedHash = Long.parseLong(metadataHeader, 16);
                                matches = (storedHash == hash);
                        }
            } catch (ServiceException x) {
                if (!x.getMessage().contains("NoSuchKey") && !x.getMessage().contains("404")) {
                        logS3Error("HEAD (hash check)", baseKey, x);
                }
            } catch (StorageShutdownException x) {
                
                } finally {
                        releaseConnection(s3);
                }
                return matches;
        }

        @Override
        public TileRead read() {
                Client s3 = null;
                try {
                        s3 = getConnection();
                        byte[] buf = s3.path(bucketname, baseKey).responseAsBytes();
                        Response response = s3.path(bucketname, baseKey).response();
                        if (buf != null && response != null && response.isOk()) {
                    TileRead tr = new TileRead();
                        tr.image = new BufferInputStream(buf);
                    String ct = getResponseHeader(response, "Content-Type");
                    tr.format = ImageEncoding.fromContentType(ct != null ? ct : "application/octet-stream");
                    String v = getResponseHeader(response, "x-amz-meta-x-dynmap-hash");
                    if (v != null) {
                        tr.hashCode = Long.parseLong(v, 16);
                    }
                    v = getResponseHeader(response, "x-amz-meta-x-dynmap-ts");
                    if (v != null) {
                        tr.lastModified = Long.parseLong(v);
                    }
                    return tr;
                        }
                } catch (ServiceException x) {
                        if (x.getMessage().contains("NoSuchKey")) {
                                return null;
                        }
                        logS3Error("GET (tile read)", baseKey, x);
            } catch (StorageShutdownException x) {
                } finally {
                        releaseConnection(s3);
                }
                return null;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage, long timestamp) {
                boolean done = false;
                Client s3 = null;
                try {
                s3 = getConnection();
                        if (encImage == null) {
                                s3.path(bucketname, baseKey).method(HttpMethod.DELETE).execute();
                        }
                        else {
                    s3.path(bucketname, baseKey)
                        .method(HttpMethod.PUT)
                        .header("Content-Type", map.getImageFormat().getEncoding().getContentType())
                        .metadata("x-dynmap-hash", Long.toHexString(hash))
                        .metadata("x-dynmap-ts", Long.toString(timestamp))
                        .requestBody(Arrays.copyOf(encImage.buf, encImage.len))
                        .execute();
                        }
                        done = true;
            } catch (ServiceException x) {
                logS3Error(encImage == null ? "DELETE (tile)" : "PUT (tile write)", baseKey, x);
            } catch (StorageShutdownException x) {
                } finally {
                        releaseConnection(s3);
                }
            if (zoom == 0) {
                world.enqueueZoomOutUpdate(this);
            }
            return done;
        }

        @Override
        public boolean getWriteLock() {
            return true;
        }

        @Override
        public void releaseWriteLock() {
        }

        @Override
        public boolean getReadLock(long timeout) {
            return true;
        }

        @Override
        public void releaseReadLock() {
        }

        @Override
        public void cleanup() {
        }
        
        @Override
        public String getURI() {
            return uri;
        }
        
        @Override
        public void enqueueZoomOutUpdate() {
            world.enqueueZoomOutUpdate(this);
        }
        @Override
        public MapStorageTile getZoomOutTile() {
            int xx, yy;
            int step = 1 << zoom;
            if(x >= 0)
                xx = x - (x % (2*step));
            else
                xx = x + (x % (2*step));
            yy = -y;
            if(yy >= 0)
                yy = yy - (yy % (2*step));
            else
                yy = yy + (yy % (2*step));
            yy = -yy;
            return new StorageTile(world, map, xx, yy, zoom+1, var);
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof StorageTile) {
                StorageTile st = (StorageTile) o;
                return baseKey.equals(st.baseKey);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return baseKey.hashCode();
        }
        @Override
        public String toString() {
            return baseKey;
        }
    }
    
    private String bucketname;
    private String region;
    private String access_key_id;
    private String secret_access_key;
    private String prefix;
    private String endpoint;

    private int POOLSIZE = 4;
    private int cpoolCount = 0;
    private Client[] cpool = new Client[POOLSIZE];
    
    public AWSS3MapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        if (!core.isInternalWebServerDisabled) {
                Log.severe("AWS S3 storage is not supported option with internal web server: set disable-webserver: true in configuration.txt");
            return false;
        }
        if (core.isLoginSupportEnabled()) {
                Log.severe("AWS S3 storage is not supported option with loegin support enabled: set login-enabled: false in configuration.txt");
            return false;               
        }
        bucketname = core.configuration.getString("storage/bucketname", "dynmap");
        access_key_id = core.configuration.getString("storage/aws_access_key_id", System.getenv("AWS_ACCESS_KEY_ID"));
        secret_access_key = core.configuration.getString("storage/aws_secret_access_key", System.getenv("AWS_SECRET_ACCESS_KEY"));
        prefix = core.configuration.getString("storage/prefix", "");

        String region_name = core.configuration.getString("storage/region", "us-east-1");
        String region_endpoint = core.configuration.getString("storage/override_endpoint", "");

        region = region_name;
        endpoint = region_endpoint.length() > 0 ? region_endpoint : null;

        if ((prefix.length() > 0) && (prefix.charAt(prefix.length()-1) != '/')) {
                prefix += '/';
        }
        Log.info("Using AWS S3 storage: web site at S3 bucket " + bucketname + " in region " + region);
        Client s3 = null;
        try {
            s3 = getConnection();
            if (s3 == null) {
                Log.severe("Error creating S3 access client");      
                return false;
            }
                String response = s3.path(bucketname)
                        .query("list-type", "2")
                        .query("prefix", prefix)
                        .query("max-keys", "1")
                        .responseAsUtf8();
                if (response == null || !response.contains("ListBucketResult")) {
                        Log.severe("Error: cannot find or access S3 bucket");
                        return false;
                }
        } catch (ServiceException s3x) {
                logS3Error("LIST (bucket initialization)", prefix, s3x);
                return false;
        } catch (StorageShutdownException x) {
                return false;
        } finally {
                releaseConnection(s3);
        }

        return true;
    }
    
    @Override
    public MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y,
            int zoom, ImageVariant var) {
        return new StorageTile(world, map, x, y, zoom, var);
    }
    
    @Override
    public MapStorageTile getTile(DynmapWorld world, String uri) {
        String[] suri = uri.split("/");
        if (suri.length < 2) return null;
        String mname = suri[0];
        MapType mt = null;
        ImageVariant imgvar = null;
        for (int mti = 0; (mt == null) && (mti < world.maps.size()); mti++) {
            MapType type = world.maps.get(mti);
            ImageVariant[] var = type.getVariants();
            for (int ivi = 0; (imgvar == null) && (ivi < var.length); ivi++) {
                if (mname.equals(type.getPrefix() + var[ivi].variantSuffix)) {
                    mt = type;
                    imgvar = var[ivi];
                }
            }
        }
        if (mt == null) {
            return null;
        }
        String fname = suri[suri.length-1];
        String[] coord = fname.split("[_\\.]");
        if (coord.length < 3) {
            return null;
        }
        int zoom = 0;
        int x, y;
        try {
            if (coord[0].charAt(0) == 'z') {
                zoom = coord[0].length();
                x = Integer.parseInt(coord[1]);
                y = Integer.parseInt(coord[2]);
            }
            else {
                x = Integer.parseInt(coord[0]);
                y = Integer.parseInt(coord[1]);
            }
            return getTile(world, mt, x, y, zoom, imgvar);
        } catch (NumberFormatException nfx) {
            return null;
        }
    }


    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, 
                MapStorageTileSearchEndCB cbEnd) {
        String basekey = prefix + "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
        boolean done = false;
        Client s3 = null;
        String continuationToken = null;
        try {
                s3 = getConnection();
                while (!done) {
                        com.github.davidmoten.aws.lw.client.Request req = s3.path(bucketname)
                                .query("list-type", "2")
                                .query("prefix", basekey)
                                .query("max-keys", "1000");
                        if (continuationToken != null) {
                                req = req.query("continuation-token", continuationToken);
                        }
                        String response = req.responseAsUtf8();
                        List<String> keys = parseS3ListResponse(response);
                        for (String key : keys) { 
                                key = key.substring(basekey.length());
                        String ext = null;
                        int extoff = key.lastIndexOf('.');
                        if (extoff >= 0) {
                            ext = key.substring(extoff+1);
                            key = key.substring(0, extoff);
                        }
                        ImageEncoding fmt = ImageEncoding.fromExt(ext);
                        if (fmt == null) {
                            continue;
                        }
                        int zoom = 0;
                        if (key.startsWith("z")) {
                            while (key.startsWith("z")) {
                                key = key.substring(1);
                                zoom++;
                            }
                            if (key.startsWith("_")) {
                                key = key.substring(1);
                            }
                        }
                        String[] coord = key.split("_");
                        if (coord.length == 2) {
                            try {
                                int x = Integer.parseInt(coord[0]);
                                int y = Integer.parseInt(coord[1]);
                                MapStorageTile t = new StorageTile(world, map, x, y, zoom, var);
                                if(cb != null)
                                    cb.tileFound(t, fmt);
                                if(cbBase != null && t.zoom == 0)
                                    cbBase.tileFound(t, fmt);
                                t.cleanup();
                            } catch (NumberFormatException nfx) {
                            }
                        }
                        }
                        if (response.contains("<IsTruncated>true</IsTruncated>")) {
                        continuationToken = extractXmlValue(response, "NextContinuationToken");
                        }
                        else {
                                done = true;
                        }
                }
        } catch (ServiceException x) {
                if (!x.getMessage().contains("SignatureDoesNotMatch")) {
                        logS3Error("LIST (enumerate tiles)", basekey, x);
                }
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        if(cbEnd != null) {
            cbEnd.searchEnded();
        }
    }
    
    @Override
    public void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, cb, null, null);
            }
        }
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, null, cbBase, cbEnd);
            }
        }
    }

    private void processPurgeMapTiles(DynmapWorld world, MapType map, ImageVariant var) {
        String basekey = prefix + "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
                Client s3 = null;
        String continuationToken = null;
        try {
                s3 = getConnection();
                boolean done = false;
                while (!done) {
                        com.github.davidmoten.aws.lw.client.Request req = s3.path(bucketname)
                                .query("list-type", "2")
                                .query("prefix", basekey)
                                .query("max-keys", "1000");
                        if (continuationToken != null) {
                                req = req.query("continuation-token", continuationToken);
                        }
                        String response = req.responseAsUtf8();
                        List<String> keys = parseS3ListResponse(response);
                        for (String key : keys) { 
                                s3.path(bucketname, key).method(HttpMethod.DELETE).execute();
                        }
                        if (response.contains("<IsTruncated>true</IsTruncated>")) {
                        continuationToken = extractXmlValue(response, "NextContinuationToken");
                        }
                        else {
                                done = true;
                        }
                }
        } catch (ServiceException x) {
                if (!x.getMessage().contains("SignatureDoesNotMatch")) {
                        logS3Error("LIST/DELETE (purge tiles)", basekey, x);
                }
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processPurgeMapTiles(world, mt, var);
            }
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, FaceType facetype,
            BufferOutputStream encImage) {
        boolean done = false;
        String baseKey = prefix + "faces/" + facetype.id + "/" + playername + ".png";
        Client s3 = null;
        try {
                s3 = getConnection();
                if (encImage == null) {
                        s3.path(bucketname, baseKey).method(HttpMethod.DELETE).execute();
                }
                else {
            s3.path(bucketname, baseKey)
                .method(HttpMethod.PUT)
                .header("Content-Type", "image/png")
                .requestBody(Arrays.copyOf(encImage.buf, encImage.len))
                .execute();
                }
                        done = true;
        } catch (ServiceException x) {
                logS3Error(encImage == null ? "DELETE (player face)" : "PUT (player face)", baseKey, x);
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return done;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
            FaceType facetype) {
        BufferInputStream image = null;
        String baseKey = prefix + "faces/" + facetype.id + "/" + playername + ".png";
        Client s3 = null;
        try {
                s3 = getConnection();
                byte[] imagedata = s3.path(bucketname, baseKey).responseAsBytes();
            image = new BufferInputStream(imagedata);
        } catch (ServiceException x) {
                if (!x.getMessage().contains("NoSuchKey")) {
                        logS3Error("GET (player face image)", baseKey, x);
                }
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return image;
    }

    @Override
    public boolean hasPlayerFaceImage(String playername, FaceType facetype) {
        boolean exists = false;
        String baseKey = prefix + "faces/" + facetype.id + "/" + playername + ".png";
        Client s3 = null;
        try {
                s3 = getConnection();
                s3.path(bucketname, baseKey).method(HttpMethod.HEAD).execute();
                exists = true;
        } catch (ServiceException x) {
                if (!x.getMessage().contains("NoSuchKey") && !x.getMessage().contains("404")) {
                        logS3Error("HEAD (player face exists)", baseKey, x);
                }
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
        boolean done = false;
        String baseKey = prefix + "tiles/_markers_/" + markerid + ".png";
        Client s3 = null;
        try {
                s3 = getConnection();
                if (encImage == null) {
                        s3.path(bucketname, baseKey).method(HttpMethod.DELETE).execute();
                }
                else {
            s3.path(bucketname, baseKey)
                .method(HttpMethod.PUT)
                .header("Content-Type", "image/png")
                .requestBody(Arrays.copyOf(encImage.buf, encImage.len))
                .execute();
                }
                        done = true;
        } catch (ServiceException x) {
                logS3Error(encImage == null ? "DELETE (marker image)" : "PUT (marker image)", baseKey, x);
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return done;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
        BufferInputStream image = null;
        String baseKey = prefix + "tiles/_markers_/" + markerid + ".png";
        Client s3 = null;
        try {
                s3 = getConnection();
                byte[] imagedata = s3.path(bucketname, baseKey).responseAsBytes();
            image = new BufferInputStream(imagedata);
        } catch (ServiceException x) {
                if (!x.getMessage().contains("NoSuchKey")) {
                        logS3Error("GET (marker image)", baseKey, x);
                }
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return image;
    }

    @Override
    public boolean setMarkerFile(String world, String content) {
        boolean done = false;
        String baseKey = prefix + "tiles/_markers_/marker_" + world + ".json";
        Client s3 = null;
        try {
                s3 = getConnection();
                if (content == null) {
                        s3.path(bucketname, baseKey).method(HttpMethod.DELETE).execute();
                }
                else {
            s3.path(bucketname, baseKey)
                .method(HttpMethod.PUT)
                .header("Content-Type", "application/json")
                .requestBody(content.getBytes(StandardCharsets.UTF_8))
                .execute();
                }
                        done = true;
        } catch (ServiceException x) {
                logS3Error(content == null ? "DELETE (marker file)" : "PUT (marker file)", baseKey, x);
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return done;
    }

    @Override
    public String getMarkerFile(String world) {
        return null;
    }
    
    @Override
    public String getMarkersURI(boolean login_enabled) {
        return "tiles/";
    }

    @Override
    public String getTilesURI(boolean login_enabled) {
        return "tiles/";
    }
    
    public String getConfigurationJSONURI(boolean login_enabled) {
        return "standalone/dynmap_config.json?_={timestamp}";
    }

    public String getUpdateJSONURI(boolean login_enabled) {
        return "standalone/dynmap_{world}.json?_={timestamp}";
    }

    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        String p = core.getTilesFolder().getAbsolutePath();
        if(!p.endsWith("/"))
            p += "/";
        sb.append("$tilespath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        sb.append("$markerspath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        
        super.addPaths(sb, core);
    }


    @Override
    public BufferInputStream getStandaloneFile(String fileid) {
        return null;
    }

    
    private ConcurrentHashMap<String, byte[]> standalone_cache = new ConcurrentHashMap<String, byte[]>();
    
    @Override
    public boolean setStandaloneFile(String fileid, BufferOutputStream content) {
        return setStaticWebFile("standalone/" + fileid, content);
    }

    public boolean needsStaticWebFiles() {
        return true;
    }

    public boolean setStaticWebFile(String fileid, BufferOutputStream content) {
        
        boolean done = false;
        String baseKey = prefix + fileid;
        Client s3 = null;
        try {
                s3 = getConnection();
                byte[] cacheval = standalone_cache.get(fileid);
                
                if (content == null) {
                        if ((cacheval != null) && (cacheval.length == 0)) {
                                return true;
                        }
                            s3.path(bucketname, baseKey).method(HttpMethod.DELETE).execute();
                            standalone_cache.put(fileid, new byte[0]);
                }
                else {
                        byte[] digest = content.buf;
                        try {
                                MessageDigest md = MessageDigest.getInstance("MD5");
                                md.update(content.buf);
                                digest = md.digest();
                        } catch (NoSuchAlgorithmException nsax) {
                                
                        }
                    if (Arrays.equals(digest, cacheval)) {
                        return true;
                    }
                        String ct = "text/plain";
                        if (fileid.endsWith(".json")) {
                                ct = "application/json";
                        }
                        else if (fileid.endsWith(".php")) {
                                ct = "application/x-httpd-php";
                        }
                        else if (fileid.endsWith(".html")) {
                                ct = "text/html";
                        }
                        else if (fileid.endsWith(".css")) {
                                ct = "text/css";
                        }
                        else if (fileid.endsWith(".js")) {
                                ct = "application/x-javascript";
                        }
                s3.path(bucketname, baseKey)
                    .method(HttpMethod.PUT)
                    .header("Content-Type", ct)
                    .requestBody(Arrays.copyOf(content.buf, content.len))
                    .execute();
                        standalone_cache.put(fileid, digest);
                }
                        done = true;
        } catch (ServiceException x) {
                logS3Error(content == null ? "DELETE (static file)" : "PUT (static file)", baseKey, x);
        } catch (StorageShutdownException x) {
        } finally {
                releaseConnection(s3);
        }
        return done;
    }

    private Client getConnection() throws ServiceException, StorageShutdownException {
        Client c = null;
        if (isShutdown) throw new StorageShutdownException();
        synchronized (cpool) {
            while (c == null) {
                for (int i = 0; i < cpool.length; i++) {
                    if (cpool[i] != null) {
                        c = cpool[i];
                        cpool[i] = null;
                        break;
                    }
                }
                if (c == null) {
                    if (cpoolCount < POOLSIZE) {
                        if (endpoint != null && endpoint.length() > 0) {
                            c = Client.s3()
                                .region(region)
                                .accessKey(access_key_id)
                                .secretKey(secret_access_key)
                                .endpoint(endpoint)
                                .build();
                        } else {
                            c = Client.s3()
                                .region(region)
                                .accessKey(access_key_id)
                                .secretKey(secret_access_key)
                                .build();
                        }
                        
                        if (c == null) {
                                Log.severe("Error creating S3 access client");      
                                return null;
                        }
                        cpoolCount++;
                    }
                    else {
                        try {
                            cpool.wait();
                        } catch (InterruptedException e) {
                            return null;
                        }
                    }
                }
            }
        }
        return c;
    }
    
    private void releaseConnection(Client c) {
        if (c == null) return;
        synchronized (cpool) {
            for (int i = 0; i < POOLSIZE; i++) {
                if (cpool[i] == null) {
                    cpool[i] = c;
                    c = null;
                    cpool.notifyAll();
                    break;
                }
            }
            if (c != null) {
                cpoolCount--;
                cpool.notifyAll();
            }
        }
    }

    private String getResponseHeader(Response response, String headerName) {
        try {
            List<String> values = response.headers().get(headerName);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseS3ListResponse(String xml) {
        List<String> keys = new ArrayList<>();
        Pattern pattern = Pattern.compile("<Key>(.*?)</Key>");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private String extractXmlValue(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void logS3Error(String operation, String key, ServiceException e) {
        String errorMsg = String.format("S3 Error [%s] - Operation: %s, Key: %s, Message: %s", 
            bucketname, operation, key, e.getMessage());
        Log.severe(errorMsg);
        if (e.getCause() != null) {
            Log.severe("  Caused by: " + e.getCause().getMessage());
        }
    }
}
