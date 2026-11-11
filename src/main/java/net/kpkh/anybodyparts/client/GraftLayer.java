package net.kpkh.anybodyparts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import net.kpkh.anybodyparts.BodyPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.PlayerModelType;

public final class GraftLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final EnumMap<BodyPart, SkinLimbModel> wide = new EnumMap<>(BodyPart.class), slim = new EnumMap<>(BodyPart.class);
    public GraftLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models) {
        super(renderer);
        for (var part : BodyPart.values()) { wide.put(part, new SkinLimbModel(models, part, false)); slim.put(part, new SkinLimbModel(models, part, true)); }
    }
    @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        var data = (BodyRenderState)state;
        if (state.isSpectator || state.isInvisible && state.isInvisibleToPlayer && !state.appearsGlowing()) return;
        for (var entry : data.anybodyparts$grafts().entrySet()) {
            BodyPart part = entry.getKey();
            if (!data.anybodyparts$body().has(part)) continue;
            var skin = entry.getValue();
            var model = (skin.model() == PlayerModelType.SLIM ? slim : wide).get(part);
            boolean outer = switch (part) {
                case LEFT_ARM -> state.showLeftSleeve; case RIGHT_ARM -> state.showRightSleeve;
                case LEFT_LEG -> state.showLeftPants; case RIGHT_LEG -> state.showRightPants; case TORSO -> state.showJacket;
            };
            var type = state.isInvisible && state.isInvisibleToPlayer ? RenderTypes.outline(skin.body().texturePath()) : RenderTypes.entityTranslucent(skin.body().texturePath());
            poses.pushPose(); getParentModel().root().translateAndRotate(poses);
            collector.submitModel(model, new SkinLimbModel.State(SkinLimbModel.part(getParentModel(), part).storePose(), outer), poses,
                type, light, LivingEntityRenderer.getOverlayCoords(state, 0), state.isInvisible && !state.isInvisibleToPlayer ? 654311423 : -1, null, state.outlineColor);
            poses.popPose();
        }
    }
}
