package net.kpkh.anybodyparts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.*;
import java.util.function.Consumer;
import net.kpkh.anybodyparts.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

public final class BodyPartSpecialRenderer implements SpecialModelRenderer<PlayerSkinRenderCache.RenderInfo> {
    private final PlayerSkinRenderCache cache;
    private final SkinLimbModel wide, slim;
    private final BodyPart part;
    private final boolean mechanical;
    private BodyPartSpecialRenderer(BakingContext context, BodyPart part, boolean mechanical) {
        this.part = part; this.mechanical = mechanical; cache = context.playerSkinRenderCache();
        wide = new SkinLimbModel(context.entityModelSet(), part, false);
        slim = new SkinLimbModel(context.entityModelSet(), part, true);
    }
    @Override public PlayerSkinRenderCache.RenderInfo extractArgument(ItemStack stack) {
        var profile = stack.get(DataComponents.PROFILE);
        return profile == null ? null : cache.getOrDefault(profile);
    }
    @Override public void submit(PlayerSkinRenderCache.RenderInfo info, PoseStack poses, SubmitNodeCollector collector,
        int light, int overlay, boolean foil, int outline) {
        var skin = mechanical ? MechanicalSkin.SKIN : info == null ? DefaultPlayerSkin.getDefaultSkin() : info.playerSkin();
        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F); poses.scale(1, -1, -1);
        collector.submitModel(skin.model() == PlayerModelType.SLIM ? slim : wide, SkinLimbModel.centered(part), poses,
            RenderTypes.entityTranslucent(skin.body().texturePath()), light, overlay, -1, null, outline);
        poses.popPose();
    }
    @Override public void getExtents(Consumer<Vector3fc> output) {
        var pose = new PoseStack(); pose.translate(0.5F, 0.5F, 0.5F); pose.scale(1, -1, -1);
        wide.setupAnim(SkinLimbModel.centered(part)); wide.root().getExtentsForGui(pose, output);
    }
    public record Unbaked(BodyPart part, boolean mechanical) implements SpecialModelRenderer.Unbaked<PlayerSkinRenderCache.RenderInfo> {
        public static final MapCodec<Unbaked> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("part").forGetter(p -> p.part().name().toLowerCase(java.util.Locale.ROOT)),
            Codec.BOOL.optionalFieldOf("mechanical", false).forGetter(Unbaked::mechanical)
        ).apply(instance, (name, mechanical) -> new Unbaked(BodyConfig.part(name), mechanical)));
        @Override public MapCodec<? extends SpecialModelRenderer.Unbaked<PlayerSkinRenderCache.RenderInfo>> type() { return CODEC; }
        @Override public SpecialModelRenderer<PlayerSkinRenderCache.RenderInfo> bake(BakingContext context) { return new BodyPartSpecialRenderer(context, part, mechanical); }
    }
}
