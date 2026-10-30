package com.vigia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.vigia.VigiaMod;

public class VigiaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(VigiaMod.VIGIA, VigiaRenderer::new);
	}
}
