package com.lukarbonite.iseeyourchunks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, holds, and persists the single active {@link ISeeYourChunksConfig}.
 *
 * <p>Every failure path falls back to defaults and logs rather than throwing: a malformed config file
 * must not stop the game from starting, and on a dedicated server there is nobody at the console to
 * react to a crash anyway.
 */
public final class ISeeYourChunksConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH =
		FabricLoader.getInstance().getConfigDir().resolve("i-see-your-chunks.json");

	private static ISeeYourChunksConfig active = new ISeeYourChunksConfig();

	private ISeeYourChunksConfigManager() {
	}

	/** Reads the config from disk, then writes it straight back so new keys appear in the file. */
	public static void load() {
		active = readOrDefault();
		write();
	}

	public static ISeeYourChunksConfig getConfig() {
		return active;
	}

	/** Replaces the active config (clamping as it goes) and persists it. */
	public static void setConfig(ISeeYourChunksConfig config) {
		active = config == null ? new ISeeYourChunksConfig() : config.copy();
		write();
	}


	private static ISeeYourChunksConfig readOrDefault() {
		if (!Files.exists(CONFIG_PATH)) {
			return new ISeeYourChunksConfig();
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			ISeeYourChunksConfig parsed = GSON.fromJson(reader, ISeeYourChunksConfig.class);
			// Gson yields null for an empty or all-whitespace file, and copy() applies the clamps that
			// reflective field population bypasses.
			return parsed == null ? new ISeeYourChunksConfig() : parsed.copy();
		} catch (IOException | RuntimeException exception) {
			ISeeYourChunks.LOGGER.error("Could not read {}; falling back to defaults.", CONFIG_PATH, exception);
			return new ISeeYourChunksConfig();
		}
	}

	private static void write() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(active, writer);
			}
		} catch (IOException exception) {
			ISeeYourChunks.LOGGER.error("Could not write {}; settings will not persist.", CONFIG_PATH, exception);
		}
	}
}
