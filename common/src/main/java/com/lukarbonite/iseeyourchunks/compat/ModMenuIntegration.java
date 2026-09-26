package com.lukarbonite.iseeyourchunks.compat;

import com.lukarbonite.iseeyourchunks.client.gui.ISeeYourChunksConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ISeeYourChunksConfigScreen::new;
	}
}
