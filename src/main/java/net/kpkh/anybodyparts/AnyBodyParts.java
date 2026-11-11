package net.kpkh.anybodyparts;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.InteractionResult;

public final class AnyBodyParts implements ModInitializer {
    public static final String MOD_ID = "anybodyparts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BodyParts.STATE.identifier();
        Grafts.DATA.identifier();
        MechanicalParts.DATA.identifier();
        FeatureCondition.register();
        ModItems.initialize();
        BodyConfig.initialize();
        BodyAdmin.register();
        BodyCommands.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.player.setAttached(BodyParts.RULES, BodyConfig.get().rules()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            player.setAttached(BodyParts.RULES, BodyConfig.get().rules());
            if (!alive && BodyConfig.get().restoreOnDeath) BodyParts.reset(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(SawItem::cleanup);
        ServerTickEvents.END_SERVER_TICK.register(SewingNeedleItem::cleanup);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SawItem.cancelAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SewingNeedleItem.cancelAll());
        UseItemCallback.EVENT.register((p, level, hand) -> BodyParts.canUse(p, hand) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseBlockCallback.EVENT.register((p, level, hand, hit) -> {
            if (!BodyParts.canUse(p, hand)) return InteractionResult.FAIL;
            if (p.getItemInHand(hand).getItem() instanceof SewingNeedleItem needle) return needle.use(level, p, hand);
            return p.getItemInHand(hand).getItem() instanceof SawItem saw ? saw.use(level, p, hand) : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((p, level, hand, entity, hit) -> {
            if (!BodyParts.canUse(p, hand)) return InteractionResult.FAIL;
            if (p.getItemInHand(hand).is(ModItems.SEWING_NEEDLE) && entity instanceof net.minecraft.world.entity.player.Player other) return SewingNeedleItem.begin(p, other, hand);
            if (p.getItemInHand(hand).is(ModItems.LIMB_SAW) && entity instanceof net.minecraft.world.entity.player.Player other) return SawItem.begin(p, other, hand);
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((p, level, hand, entity, hit) -> BodyParts.canUse(p, hand) ? InteractionResult.PASS : InteractionResult.FAIL);
        AttackBlockCallback.EVENT.register((p, level, hand, pos, direction) -> BodyParts.canUse(p, hand) ? InteractionResult.PASS : InteractionResult.FAIL);
        LOGGER.info("Any Body Parts initialized");
    }
}
