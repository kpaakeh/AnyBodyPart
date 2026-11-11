package net.kpkh.anybodyparts;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.permission.v1.*;
import net.kpkh.anybodyparts.client.AdminBodyScreen;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class BodyPartsGameTest implements FabricClientGameTest {
    private static volatile UUID restricted;
    private static volatile boolean denyAdmin = true;
    private static volatile String deniedNode;
    @Override public void runTest(ClientGameTestContext context) {
        PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
            @Override @SuppressWarnings("unchecked") public <T> T handlePermissionRequest(PermissionContext owner, PermissionNode<T> node) {
                if (owner.uuid().equals(restricted) && (node.key().toString().equals(deniedNode)
                    || denyAdmin && (node.key().toString().startsWith("anybodyparts:command.") || node.key().toString().startsWith("anybodyparts:saw.bypass.")))) return (T)Boolean.FALSE;
                if (owner.uuid().equals(restricted) && node.key().toString().startsWith("anybodyparts:command.")) return (T)Boolean.valueOf(!denyAdmin);
                return null;
            }
        });
        String original;
        try { original = Files.readString(BodyConfig.path()); }
        catch (Exception e) { throw new RuntimeException(e); }
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 -60 0 0 0");
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            restricted = context.computeOnClient(c -> c.player.getUUID());
            String name = context.computeOnClient(c -> c.player.getGameProfile().name());
            // A forged admin packet and ordinary-player commands must both be refused.
            context.runOnClient(c -> ClientPlayNetworking.send(new AdminRequestPayload(name, 0, false)));
            context.waitTicks(5);
            context.runOnClient(c -> check(BodyParts.get(c.player).equals(BodyState.COMPLETE), "Forged admin request"));
            server.runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                var dispatcher = s.getCommands().getDispatcher();
                var parsed = dispatcher.parse("bodyparts set " + name + " left_arm false", p.createCommandSourceStack());
                check(parsed.getReader().canRead(), "Unprivileged command was accepted");
                check(!BodyPermissions.admin(p, "command.menu"), "Menu permission");
                check(SawItem.denial(p, p, BodyPart.TORSO).equals("message.anybodyparts.torso_last"), "Torso requires limbs removed, not OP");
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIMB_SAW));
            });
            context.waitTicks(5);
            context.runOnClient(c -> { c.options.setCameraType(CameraType.FIRST_PERSON); c.player.setXRot(-30); });
            context.getInput().holdKey(o -> o.keyShift);
            context.waitTicks(2);
            context.getInput().pressKey(o -> o.keyUse);
            context.waitTicks(2);
            context.getInput().releaseKey(o -> o.keyShift);
            context.waitTicks(3);
            context.runOnClient(c -> check(SawItem.selected(c.player.getMainHandItem()) == BodyPart.RIGHT_ARM, "Saw part selection"));
            // Short use cancels without consuming durability.
            context.getInput().holdKey(o -> o.keyUse);
            context.waitTicks(20);
            context.takeScreenshot("saw-first-person-animation");
            context.getInput().releaseKey(o -> o.keyUse);
            context.waitTicks(5);
            context.runOnClient(c -> {
                check(BodyParts.get(c.player).equals(BodyState.COMPLETE), "Early cut");
                check(c.player.getMainHandItem().getDamageValue() == 0, "Cancelled operation consumed durability");
            });
            context.getInput().holdKey(o -> o.keyUse);
            context.waitTicks(40);
            context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.takeScreenshot("saw-third-person-animation");
            context.waitTicks(45);
            context.getInput().releaseKey(o -> o.keyUse);
            context.waitFor(c -> !BodyParts.get(c.player).has(BodyPart.RIGHT_ARM));
            context.runOnClient(c -> check(c.player.getMainHandItem().getDamageValue() == 8, "Saw durability cost"));
            server.runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                var firstCut = s.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("anybodyparts", "body/first_cut"));
                check(firstCut != null && p.getAdvancements().getOrStartProgress(firstCut).isDone(), "First cut advancement not awarded");
            });
            denyAdmin = false;
            server.runCommand("bodyparts reset @a");
            context.waitFor(c -> BodyParts.get(c.player).equals(BodyState.COMPLETE));
            // A server-side surgeon runs the actual item tick path against the real connected player.
            server.runOnServer(s -> {
                var target = world.getConnection().getServerPlayer();
                var surgeon = new ServerPlayer(s, target.level(), new GameProfile(UUID.randomUUID(), "Surgeon"), ClientInformation.createDefault()) {
                    @Override public void sendOverlayMessage(Component message) { }
                    @Override public boolean hasInfiniteMaterials() { return true; }
                    @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
                };
                surgeon.setPos(target.getX(), target.getY(), target.getZ() - 1.5);
                surgeon.setOnGround(true); surgeon.setYRot(0); surgeon.setXRot(15);
                surgeon.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIMB_SAW));
                SawItem.select(surgeon.getMainHandItem(), BodyPart.LEFT_LEG);
                var saw = (SawItem)ModItems.LIMB_SAW;
                check(SawItem.denial(surgeon, target, BodyPart.LEFT_LEG) == null, "Other player initial conditions");
                SawItem.begin(surgeon, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<40;i++) saw.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 72000-i);
                Vec3 origin = target.position(); target.setPos(origin.add(0.02,0,0));
                saw.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 71959);
                check(BodyParts.get(target).has(BodyPart.LEFT_LEG), "Moving target was cut");
                check(!surgeon.isUsingItem(), "Moving target did not cancel");
                target.setPos(origin); target.setOnGround(true);
                SawItem.begin(surgeon, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<199;i++) saw.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 72000-i);
                check(BodyParts.get(target).has(BodyPart.LEFT_LEG), "Cut before 10 seconds");
                saw.onUseTick(surgeon.level(), surgeon, surgeon.getMainHandItem(), 71800);
                check(!BodyParts.get(target).has(BodyPart.LEFT_LEG), "Stationary 10-second cut failed");
                check(target.getAttachedOrElse(BodyParts.CUTTING, 0) == 0, "Cut animation left active");
                BodyParts.set(target, BodyState.COMPLETE);
            });
            // Server config reload, global masks, custom two-handed weapons, and hard protection.
            server.runOnServer(s -> {
                try {
                    JsonObject cfg = JsonParser.parseString(original).getAsJsonObject();
                    cfg.getAsJsonObject("global").getAsJsonArray("disabledParts").add("left_arm");
                    cfg.getAsJsonArray("twoHandedItems").add("minecraft:diamond_sword");
                    cfg.getAsJsonObject("global").getAsJsonArray("uncuttableParts").add("right_leg");
                    Files.writeString(BodyConfig.path(), cfg.toString()); BodyConfig.reload(s);
                    var p = world.getConnection().getServerPlayer();
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    check(!BodyParts.canUse(p, InteractionHand.MAIN_HAND), "Configured two-handed sword");
                    check(!BodyAdmin.change(p, BodyPart.RIGHT_LEG, false), "Global protection bypassed by admin");
                    check(BodyParts.stored(p).equals(BodyState.COMPLETE), "Global state destroyed personal data");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            context.waitFor(c -> !BodyParts.get(c.player).has(BodyPart.LEFT_ARM));
            context.runOnClient(c -> check(!BodyParts.canUse(c.player, InteractionHand.MAIN_HAND), "Config not synced to client"));
            server.runOnServer(s -> {
                try {
                    JsonObject cfg = JsonParser.parseString(original).getAsJsonObject(); cfg.addProperty("enabled", false);
                    Files.writeString(BodyConfig.path(), cfg.toString()); BodyConfig.reload(s);
                    BodyParts.set(world.getConnection().getServerPlayer(), new BodyState(16));
                    check(BodyParts.get(world.getConnection().getServerPlayer()).equals(BodyState.COMPLETE), "Disabled mod still active");
                    String invalid = "{\"unknown_setting\":true}";
                    Files.writeString(BodyConfig.path(), invalid);
                    try { BodyConfig.reload(s); throw new AssertionError("Invalid config accepted"); } catch (IllegalArgumentException expected) { }
                    check(!BodyConfig.get().enabled, "Failed reload replaced active settings");
                    check(Files.readString(BodyConfig.path()).equals(invalid), "Bad config overwritten");
                    Files.writeString(BodyConfig.path(), original); BodyConfig.reload(s);
                    BodyParts.set(world.getConnection().getServerPlayer(), BodyState.COMPLETE);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            context.waitFor(c -> BodyParts.get(c.player).equals(BodyState.COMPLETE));
            server.runOnServer(s -> s.getCommands().performPrefixedCommand(world.getConnection().getServerPlayer().createCommandSourceStack(), "bodyparts menu"));
            context.waitForScreen(AdminBodyScreen.class);
            context.waitTicks(3); context.takeScreenshot("admin-menu");
            context.runOnClient(c -> ClientPlayNetworking.send(new AdminRequestPayload(name, BodyPart.LEFT_ARM.ordinal(), false)));
            context.waitFor(c -> !BodyParts.get(c.player).has(BodyPart.LEFT_ARM));
            context.runOnClient(c -> ClientPlayNetworking.send(new AdminRequestPayload(name, -2, true)));
            context.waitFor(c -> BodyParts.get(c.player).equals(BodyState.COMPLETE));
            deniedNode = "anybodyparts:command.set.left_arm";
            context.runOnClient(c -> ClientPlayNetworking.send(new AdminRequestPayload(name, BodyPart.LEFT_ARM.ordinal(), false)));
            context.waitTicks(5);
            context.runOnClient(c -> check(BodyParts.get(c.player).equals(BodyState.COMPLETE), "Per-part permission bypass"));
            deniedNode = null;
            context.setScreen(() -> null);
            var reload = context.computeOnClient(c -> { c.getLanguageManager().setSelected("ru_ru"); c.options.languageCode="ru_ru"; return c.reloadResourcePacks(); });
            context.waitFor(c -> reload.isDone() && c.gui.overlay() == null);
            server.runOnServer(s -> BodyAdmin.send(world.getConnection().getServerPlayer(), world.getConnection().getServerPlayer(), true));
            context.waitForScreen(AdminBodyScreen.class);
            context.waitTicks(3); context.takeScreenshot("admin-menu-ru"); context.setScreen(() -> null);
        } finally {
            denyAdmin = false; deniedNode = null;
            try { Files.writeString(BodyConfig.path(), original); BodyConfig.reload(null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
