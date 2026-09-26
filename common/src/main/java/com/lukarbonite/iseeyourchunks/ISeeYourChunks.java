package com.lukarbonite.iseeyourchunks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared mod identity and logger. Platform-agnostic on purpose: the Fabric loader entry point lives in
 * {@code ISeeYourChunksFabric}, so this class stays usable from any future loader implementation.
 */
public final class ISeeYourChunks {
	public static final String MOD_ID = "i_see_your_chunks";
	public static final String MOD_NAME = "I See Your Chunks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private ISeeYourChunks() {
	}
}
