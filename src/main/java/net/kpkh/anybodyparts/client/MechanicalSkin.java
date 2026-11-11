package net.kpkh.anybodyparts.client;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;

public final class MechanicalSkin {
    public static final PlayerSkin SKIN = PlayerSkin.insecure(new ClientAsset.ResourceTexture(
        Identifier.fromNamespaceAndPath("anybodyparts", "entity/mechanical")), null, null, PlayerModelType.WIDE);
    private MechanicalSkin() {}
}
