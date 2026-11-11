package net.kpkh.anybodyparts;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;

public final class GraftGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        UUID donor;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 -60 0 0 0");
            world.getConnection().waitForChunksRender(); context.waitTicks(20);
            donor = context.computeOnClient(c -> {
                for (int i=1;i<30;i++) {
                    UUID id = new UUID(0, i);
                    if (!DefaultPlayerSkin.get(new GameProfile(id, "Donor")).body().texturePath().equals(c.player.getSkin().body().texturePath())) return id;
                }
                throw new AssertionError("No contrasting donor skin");
            });
            server.runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                BodyParts.set(p, BodyState.COMPLETE.with(BodyPart.LEFT_LEG, false));
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SEWING_NEEDLE));
                SawItem.select(p.getMainHandItem(), BodyPart.LEFT_LEG);
                p.getInventory().setItem(10, DetachedPartItem.create(BodyPart.LEFT_LEG, ResolvableProfile.createResolved(new GameProfile(donor, "Donor"))));
                // Missing thread must not start or consume anything.
                SewingNeedleItem.begin(p, p, InteractionHand.MAIN_HAND);
                check(!p.isUsingItem(), "Sewed without thread");
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING, 3));
            });
            context.waitTicks(10);
            context.runOnClient(c -> { c.options.setCameraType(CameraType.FIRST_PERSON); c.player.setXRot(-25); });
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(3); context.takeScreenshot("donor-limb-inventory"); context.setScreen(() -> null);
            context.getInput().holdKey(o -> o.keyUse); context.waitTicks(25);
            context.takeScreenshot("needle-stitching");
            context.getInput().releaseKey(o -> o.keyUse); context.waitTicks(5);
            server.runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                check(p.getInventory().getItem(10).getCount()==1 && p.getOffhandItem().getCount()==3, "Cancelled stitch consumed supplies");
                check(p.getMainHandItem().getDamageValue()==0, "Cancelled stitch damaged needle");
                check(!BodyParts.get(p).has(BodyPart.LEFT_LEG), "Cancelled stitch restored part");
            });
            context.getInput().holdKey(o -> o.keyUse); context.waitTicks(110); context.getInput().releaseKey(o -> o.keyUse);
            context.waitFor(c -> BodyParts.get(c.player).has(BodyPart.LEFT_LEG) && Grafts.get(c.player).containsKey(BodyPart.LEFT_LEG));
            server.runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                check(p.getInventory().getItem(10).isEmpty(), "Successful stitch did not consume limb");
                check(p.getOffhandItem().getCount()==2 && p.getMainHandItem().getDamageValue()==1, "Stitch cost mismatch");
                check(Grafts.donor(p, BodyPart.LEFT_LEG).partialProfile().id().equals(donor), "Donor profile lost on stitch");
                check(!p.getUUID().equals(donor), "Foreign profile fixture is own profile");
                for (String achievement : new String[]{"root", "needle", "collect_part", "first_stitch", "foreign_part"}) achievement(p, achievement);
            });
            context.runOnClient(c -> { c.options.setCameraType(CameraType.THIRD_PERSON_FRONT); c.player.setXRot(10); });
            context.waitTicks(10); context.takeScreenshot("foreign-leg-transplant");
            server.runOnServer(s -> {
                var target = world.getConnection().getServerPlayer();
                var doctor = new ServerPlayer(s, target.level(), new GameProfile(UUID.randomUUID(), "Doctor"), ClientInformation.createDefault()) {
                    @Override public void sendOverlayMessage(Component message) { }
                    @Override public boolean hasInfiniteMaterials() { return true; }
                    @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
                };
                doctor.setPos(target.getX(), target.getY(), target.getZ()-1.5); doctor.setOnGround(true); doctor.setYRot(0); doctor.setXRot(15);
                // Re-cutting a graft must drop the original donor, not the recipient's skin.
                doctor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIMB_SAW));
                SawItem.select(doctor.getMainHandItem(), BodyPart.LEFT_LEG);
                SawItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<200;i++) ModItems.LIMB_SAW.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                var drops = target.level().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(5), e -> e.getItem().is(ModItems.PARTS.get(BodyPart.LEFT_LEG)));
                check(drops.size()==1, "Saw must drop exactly one part");
                var detached = drops.getFirst().getItem();
                check(detached.get(DataComponents.PROFILE).partialProfile().id().equals(donor), "Re-cut changed donor skin");
                check(!Grafts.get(target).containsKey(BodyPart.LEFT_LEG), "Removed graft metadata remains active");
                doctor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SEWING_NEEDLE));
                SawItem.select(doctor.getMainHandItem(), BodyPart.LEFT_LEG);
                doctor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING, 2));
                doctor.getInventory().setItem(10, detached.copy()); drops.getFirst().discard();
                SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<20;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                doctor.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 71979);
                check(!doctor.isUsingItem() && !doctor.getInventory().getItem(10).isEmpty(), "Removing thread did not safely cancel");
                check(!BodyParts.get(target).has(BodyPart.LEFT_LEG), "Removing thread still restored limb");
                doctor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING, 2));
                SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<99;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                check(!BodyParts.get(target).has(BodyPart.LEFT_LEG), "Stitch completed too early");
                ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 71900);
                check(BodyParts.get(target).has(BodyPart.LEFT_LEG), "Stitch to another player failed");
                check(doctor.getInventory().getItem(10).isEmpty() && doctor.getOffhandItem().getCount()==1, "Other-player stitch cost mismatch");
                // An original matching part is also accepted, and cannot replace an existing part.
                BodyParts.set(target, BodyParts.stored(target).with(BodyPart.RIGHT_ARM, false));
                doctor.getInventory().setItem(10, DetachedPartItem.create(BodyPart.RIGHT_ARM, ResolvableProfile.createResolved(target.getGameProfile())));
                SawItem.select(doctor.getMainHandItem(), BodyPart.RIGHT_ARM);
                SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<100;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                check(Grafts.donor(target, BodyPart.RIGHT_ARM).partialProfile().id().equals(target.getUUID()), "Own part rejected");
                achievement(target, "own_part");
                check(Grafts.donor(target, BodyPart.LEFT_LEG).partialProfile().id().equals(donor), "Another graft cleared when restoring last part");
                doctor.getInventory().setItem(10, DetachedPartItem.create(BodyPart.RIGHT_ARM, ResolvableProfile.createResolved(new GameProfile(donor,"Donor"))));
                doctor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING));
                SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                check(!doctor.isUsingItem() && !doctor.getInventory().getItem(10).isEmpty(), "Existing part was overwritten");
                // Render a contrasting arm as well as leg; use a valid donor fixture for visual QA.
                Grafts.put(target, BodyPart.RIGHT_ARM, ResolvableProfile.createResolved(new GameProfile(donor, "Donor")));
                BodyParts.set(target, BodyParts.stored(target).with(BodyPart.RIGHT_LEG, false));
                doctor.getInventory().setItem(10, DetachedPartItem.create(BodyPart.RIGHT_LEG, ResolvableProfile.createResolved(new GameProfile(donor, "Donor"))));
                doctor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STRING));
                SawItem.select(doctor.getMainHandItem(), BodyPart.RIGHT_LEG);
                SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                for (int i=0;i<100;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                achievement(target, "patchwork");
                target.getInventory().setItem(11, DetachedPartItem.create(BodyPart.RIGHT_LEG, ResolvableProfile.createResolved(new GameProfile(donor, "Donor"))));
                target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); target.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            });
            context.waitFor(c -> Grafts.get(c.player).size()==3);
            context.waitTicks(10); context.takeScreenshot("foreign-arm-and-leg");
            context.runOnClient(c -> { c.options.setCameraType(CameraType.FIRST_PERSON); c.player.setXRot(10); });
            context.waitTicks(10); context.takeScreenshot("foreign-arm-first-person");
        }
        try (var world = save.open()) {
            context.waitFor(c -> c.player != null && Grafts.get(c.player).size()==3);
            context.runOnClient(c -> check(Grafts.donor(c.player, BodyPart.LEFT_LEG).partialProfile().id().equals(donor), "Graft donor did not persist across relog"));
            world.getServer().runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                check(Grafts.donor(p, BodyPart.RIGHT_ARM).partialProfile().id().equals(donor), "Server graft persistence");
                check(p.getInventory().getItem(11).get(DataComponents.PROFILE).partialProfile().id().equals(donor), "Detached part donor did not survive save/load");
                BodyParts.reset(p); check(Grafts.get(p).isEmpty(), "Administrative reset did not restore original skin");
            });
            world.getServer().runCommand("gamemode creative @a");
            context.waitFor(c -> c.player.isCreative());
            context.setScreen(() -> {
                var c = net.minecraft.client.Minecraft.getInstance();
                return new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(c.player, c.level.enabledFeatures(), true);
            });
            context.runOnClient(c -> {
                var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ModItems.TAB_KEY.identifier());
                check(tab != null && tab.getDisplayItems().size()==13, "Creative tab contents");
                check(((net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen)c.gui.screen()).setSelectedTab(tab), "Creative tab selection");
            });
            context.waitTicks(3); context.takeScreenshot("anybodyparts-creative-tab"); context.setScreen(() -> null);
            context.setScreen(() -> new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(net.minecraft.client.Minecraft.getInstance().player.connection.getAdvancements()));
            context.runOnClient(c -> {
                var advancements = c.player.connection.getAdvancements();
                advancements.setSelectedTab(advancements.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("anybodyparts", "body/root")), true);
            });
            context.waitTicks(3); context.takeScreenshot("anybodyparts-advancements"); context.setScreen(() -> null);
        }
    }
    private static void achievement(ServerPlayer player, String name) {
        var advancement = player.level().getServer().getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("anybodyparts", "body/"+name));
        check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(), "Missing advancement: "+name);
        check(!advancement.value().display().orElseThrow().announceToChat(), "Advancement spam in chat");
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
