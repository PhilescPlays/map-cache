package com.philesc.map_cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;


public class ClientMaps implements ClientModInitializer {
    private static final String STORAGE_DIR = ".map_cache";

	public static final Logger LOGGER = LoggerFactory.getLogger("client_maps");
	public static MinecraftClient client;
    private static final Map<Integer, byte[]> mapStates = Maps.newHashMap();
    // Pending loads of map data from disk
    // TODO(piz) this is probably race condition hell :)
    public static final Set<Integer> pending = new HashSet<>();
    // Marker for dummy MapStates
    public static final byte MARKER = (byte)128;

	@Override
	public void onInitializeClient() {
        client = MinecraftClient.getInstance();

        try {
            transfer_folders();
        } catch (Exception e) {
            LOGGER.error("Could not transfer folders, skipping");
            LOGGER.error(e.getMessage());
        }
	}

    private void transfer_folders() {
        File root = new File(client.runDirectory, STORAGE_DIR);
        
        for (File file : root.listFiles()) {
            if (file.getName().contains(":")) {
                File dst = new File(root, file.getName().replace(":", "_"));
                LOGGER.info(file.getName() + " -> " + dst.getName());
                file.renameTo(dst);
            }
        }
    }

    private static File get_dir() {
        File maps_root =  new File(client.runDirectory, STORAGE_DIR);
		if (client.isInSingleplayer()) {
            return new File(maps_root, "singleplayer/" + client.getServer().getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString().replace(":", "_"));
        }

        return new File(maps_root, client.getCurrentServerEntry().address.replace(":", "_"));
    }

	public static byte[] getMap(Integer mapId) {
        if (mapStates.containsKey(mapId)) {
            return mapStates.get(mapId);
        }

        File save_dir = get_dir();
        File mapfile = new File(save_dir, String.valueOf(mapId));
        byte[] data = null;

        if (mapfile.exists()) {
            data = new byte[(int) mapfile.length()];
            try (FileInputStream stream = new FileInputStream(mapfile)) {
                LOGGER.info("Reading file " + mapfile.getAbsolutePath());
                stream.read(data);
            } catch (IOException e) {
                LOGGER.error("Could not read map file " + mapfile.getAbsolutePath());
                data = null;
            }
        }

        // Cache the result (even if null)
        mapStates.put(mapId, data);
        return data;
	}

	public static void setMap(Integer mapId, byte[] data) throws FileNotFoundException, IOException, ClassNotFoundException {
        if (data == null) {
            return;
        }

        // Check if already cached and identical
        byte[] cachedData = mapStates.get(mapId);
        if (cachedData != null && Arrays.equals(data, cachedData)) {
            return;
        }

        byte[] storedData = data.clone();
		File save_dir = get_dir();

        if(!save_dir.exists() && !save_dir.mkdirs()) {
            LOGGER.info("Could not create directory {}: cannot continue!", save_dir.getAbsolutePath());
            return;
        }

        File mapfile = new File(save_dir, String.valueOf(mapId));
		
		try (FileOutputStream stream = new FileOutputStream(mapfile)) {
			stream.write(data);
            LOGGER.info("Writing file " + mapfile.getAbsolutePath());
		}

        mapStates.put(mapId, storedData);
	}
}