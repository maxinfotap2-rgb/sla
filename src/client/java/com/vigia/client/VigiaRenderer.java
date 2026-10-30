package com.vigia.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

import com.vigia.VigiaEntity;
import com.vigia.VigiaMod;

/** Usa o corpo humanoide padrão (64x64), esticado para ficar alto e magro. */
public class VigiaRenderer extends MobRenderer<VigiaEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURA = VigiaMod.id("textures/entity/vigia.png");

	public VigiaRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURA;
	}

	@Override
	public void submit(HumanoidRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.scale(0.85F, 1.25F, 0.85F); // alto e magro (~2,5 blocos)
		super.submit(state, poseStack, collector, camera);
		poseStack.popPose();
	}
}
