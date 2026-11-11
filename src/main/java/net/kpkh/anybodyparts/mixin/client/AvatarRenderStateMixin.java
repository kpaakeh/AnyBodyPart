package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.BodyState;
import net.kpkh.anybodyparts.client.BodyRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.*;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements BodyRenderState {
    @Unique private BodyState anybodyparts$body = BodyState.COMPLETE;
    @Unique private float anybodyparts$roll;
    @Unique private int anybodyparts$cutting;
    @Unique private java.util.Map<net.kpkh.anybodyparts.BodyPart, net.minecraft.world.entity.player.PlayerSkin> anybodyparts$grafts = java.util.Map.of();
    public BodyState anybodyparts$body() { return anybodyparts$body; }
    public void anybodyparts$body(BodyState state) { anybodyparts$body = state; }
    public float anybodyparts$roll() { return anybodyparts$roll; }
    public void anybodyparts$roll(float angle) { anybodyparts$roll = angle; }
    public int anybodyparts$cutting() { return anybodyparts$cutting; }
    public void anybodyparts$cutting(int value) { anybodyparts$cutting = value; }
    public java.util.Map<net.kpkh.anybodyparts.BodyPart, net.minecraft.world.entity.player.PlayerSkin> anybodyparts$grafts() { return anybodyparts$grafts; }
    public void anybodyparts$grafts(java.util.Map<net.kpkh.anybodyparts.BodyPart, net.minecraft.world.entity.player.PlayerSkin> grafts) { anybodyparts$grafts = grafts; }
}
