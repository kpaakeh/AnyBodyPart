package net.kpkh.anybodyparts.client;

import net.kpkh.anybodyparts.BodyState;

public interface BodyRenderState {
    BodyState anybodyparts$body();
    void anybodyparts$body(BodyState body);
    float anybodyparts$roll();
    void anybodyparts$roll(float angle);
    int anybodyparts$cutting();
    void anybodyparts$cutting(int value);
    java.util.Map<net.kpkh.anybodyparts.BodyPart, net.minecraft.world.entity.player.PlayerSkin> anybodyparts$grafts();
    void anybodyparts$grafts(java.util.Map<net.kpkh.anybodyparts.BodyPart, net.minecraft.world.entity.player.PlayerSkin> grafts);
}
