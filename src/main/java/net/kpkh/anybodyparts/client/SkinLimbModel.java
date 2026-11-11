package net.kpkh.anybodyparts.client;

import net.kpkh.anybodyparts.BodyPart;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.player.PlayerModel;

public final class SkinLimbModel extends Model<SkinLimbModel.State> {
    public record State(PartPose pose, boolean outerLayer) {}
    public SkinLimbModel(EntityModelSet models, BodyPart part, boolean slim) {
        super(part(new PlayerModel(models.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), part), net.minecraft.client.renderer.rendertype.RenderTypes::entityTranslucent);
    }
    public static ModelPart part(PlayerModel model, BodyPart part) {
        return switch (part) {
            case LEFT_ARM -> model.leftArm; case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg; case RIGHT_LEG -> model.rightLeg; case TORSO -> model.body;
        };
    }
    @Override public void setupAnim(State state) {
        super.setupAnim(state);
        root().loadPose(state.pose());
        for (var node : allParts()) node.visible = node == root() || state.outerLayer();
    }
    public static State centered(BodyPart part) {
        boolean arm = part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM;
        float x = part == BodyPart.LEFT_ARM ? -1 : part == BodyPart.RIGHT_ARM ? 1 : 0;
        return new State(PartPose.offset(x, arm ? -4 : -6, 0), true);
    }
}
