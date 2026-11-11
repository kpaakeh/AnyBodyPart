package net.kpkh.anybodyparts;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;

public final class AuditGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        final String original;
        try { original = Files.readString(BodyConfig.path()); } catch (Exception e) { throw new RuntimeException(e); }
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("tp @a 0 -60 0 0 0");
            world.getConnection().waitForChunksRender(); context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var settings = JsonParser.parseString(original).getAsJsonObject();
                for (String value : new String[]{"0.9", "4294967296"}) {
                    var invalid = settings.deepCopy(); invalid.add("adminPermissionLevel", JsonParser.parseString(value));
                    try { BodyConfig.parse(invalid.toString()); throw new AssertionError("Non-integer/overflow config accepted: " + value); }
                    catch (IllegalArgumentException expected) { }
                }
                var patient = world.getConnection().getServerPlayer();
                var constrained = settings.deepCopy();
                constrained.getAsJsonObject("sewing").addProperty("allowForeignParts", false);
                constrained.getAsJsonObject("mechanical").addProperty("enabled", false);
                apply(server, constrained);
                BodyParts.set(patient, BodyState.COMPLETE.with(BodyPart.LEFT_LEG, false));
                patient.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SEWING_NEEDLE));
                patient.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING, 2));
                SawItem.select(patient.getMainHandItem(), BodyPart.LEFT_LEG);
                patient.getInventory().setItem(9, DetachedPartItem.create(BodyPart.LEFT_LEG, ResolvableProfile.createResolved(new GameProfile(UUID.randomUUID(), "Foreign"))));
                patient.getInventory().setItem(10, new ItemStack(ModItems.MECHANICAL.get(BodyPart.LEFT_LEG)));
                patient.getInventory().setItem(11, DetachedPartItem.create(BodyPart.LEFT_LEG, ResolvableProfile.createResolved(patient.getGameProfile())));
                SewingNeedleItem.begin(patient, patient, InteractionHand.MAIN_HAND);
                check(patient.isUsingItem(), "Earlier forbidden parts blocked a valid own part");
                for (int i = 0; i < 100; i++) ModItems.SEWING_NEEDLE.onUseTick(patient.level(), patient, patient.getMainHandItem(), 72000-i);
                check(patient.getInventory().getItem(11).isEmpty() && patient.getInventory().getItem(9).getCount()==1
                    && patient.getInventory().getItem(10).getCount()==1 && patient.getOffhandItem().getCount()==1, "Wrong limb consumed when skipping forbidden parts");
                check(Grafts.donor(patient, BodyPart.LEFT_LEG).partialProfile().id().equals(patient.getUUID()), "Wrong donor after selecting eligible part");
                apply(server, settings); BodyParts.reset(patient);
                var surgeon = surgeon(server, patient, ModItems.LIMB_SAW);
                SawItem.select(surgeon.getMainHandItem(), BodyPart.LEFT_LEG);
                SawItem.begin(surgeon, patient, InteractionHand.MAIN_HAND);
                check(surgeon.isUsingItem(), "Saw setup failed");
                for (int i = 0; i < 10; i++) ModItems.LIMB_SAW.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 72000-i);
                patient = respawn(server, patient);
                ModItems.LIMB_SAW.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 71989);
                check(!surgeon.isUsingItem() && BodyParts.get(patient).has(BodyPart.LEFT_LEG), "Saw survived patient respawn");
                var persist = settings.deepCopy(); persist.addProperty("restoreOnDeath", false); apply(server, persist);
                BodyParts.set(patient, BodyState.COMPLETE.with(BodyPart.LEFT_LEG, false));
                var tailor = surgeon(server, patient, ModItems.SEWING_NEEDLE);
                tailor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING, 2));
                tailor.getInventory().setItem(10, DetachedPartItem.create(BodyPart.LEFT_LEG, ResolvableProfile.createResolved(patient.getGameProfile())));
                SawItem.select(tailor.getMainHandItem(), BodyPart.LEFT_LEG);
                SewingNeedleItem.begin(tailor, patient, InteractionHand.MAIN_HAND);
                check(tailor.isUsingItem(), "Sewing setup failed");
                patient = respawn(server, patient);
                // A new operation on the replacement player must retain its lock when the old one cancels.
                var nextSurgeon = surgeon(server, patient, ModItems.LIMB_SAW);
                SawItem.select(nextSurgeon.getMainHandItem(), BodyPart.RIGHT_LEG);
                SawItem.begin(nextSurgeon, patient, InteractionHand.MAIN_HAND);
                check(nextSurgeon.isUsingItem(), "New surgery setup failed");
                SewingNeedleItem needle = (SewingNeedleItem)ModItems.SEWING_NEEDLE;
                needle.onUseTick(tailor.level(), tailor, tailor.getMainHandItem(), 71999);
                check(!tailor.isUsingItem() && tailor.getOffhandItem().getCount()==2 && tailor.getInventory().getItem(10).getCount()==1,
                    "Sewing survived respawn or consumed materials");
                check(patient.getAttachedOrElse(BodyParts.CUTTING, 0)==BodyPart.RIGHT_LEG.ordinal()+1, "Old surgery cleared a new patient's operation lock");
                ModItems.LIMB_SAW.releaseUsing(nextSurgeon.getMainHandItem(), nextSurgeon.level(), nextSurgeon, 71999);
                apply(server, settings); BodyParts.reset(patient);
            });
            context.waitTicks(10);
            context.runOnClient(client -> check(BodyParts.get(client.player).equals(BodyState.COMPLETE), "Respawn state not synchronized"));
        } finally {
            try { Files.writeString(BodyConfig.path(), original); BodyConfig.reload(null); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    private static ServerPlayer respawn(MinecraftServer server, ServerPlayer old) {
        var pos = old.position();
        var player = server.getPlayerList().respawn(old, false, Entity.RemovalReason.KILLED);
        player.connection.teleport(pos.x, pos.y, pos.z, 0, 0); player.setOnGround(true);
        return player;
    }
    private static ServerPlayer surgeon(MinecraftServer server, ServerPlayer patient, Item tool) {
        var player = new ServerPlayer(server, patient.level(), new GameProfile(UUID.randomUUID(), "Auditor"), ClientInformation.createDefault()) {
            @Override public void sendOverlayMessage(Component text) { }
            @Override public boolean hasInfiniteMaterials() { return true; }
            @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
        };
        player.setPos(patient.getX(), patient.getY(), patient.getZ()-1.5); player.setOnGround(true); player.setYRot(0); player.setXRot(15);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
        return player;
    }
    private static void apply(MinecraftServer server, JsonObject settings) {
        try { Files.writeString(BodyConfig.path(), settings.toString()); BodyConfig.reload(server); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
