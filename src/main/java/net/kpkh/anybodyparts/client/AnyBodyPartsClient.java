package net.kpkh.anybodyparts.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kpkh.anybodyparts.AdminStatePayload;

public final class AnyBodyPartsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("anybodyparts", "body_part"), BodyPartSpecialRenderer.Unbaked.CODEC);
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) helper.register(new GraftLayer(avatar, context.getModelSet()));
        });
        ClientPlayNetworking.registerGlobalReceiver(AdminStatePayload.TYPE, (payload, context) -> {
            if (payload.open()) context.client().gui.setScreen(new AdminBodyScreen(payload));
            else if (context.client().gui.screen() instanceof AdminBodyScreen screen) screen.update(payload);
        });
    }
}
