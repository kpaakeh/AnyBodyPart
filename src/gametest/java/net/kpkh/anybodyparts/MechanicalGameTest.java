package net.kpkh.anybodyparts;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

public final class MechanicalGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        final String original;
        try { original = Files.readString(BodyConfig.path()); } catch (Exception e) { throw new RuntimeException(e); }
        var settings = JsonParser.parseString(original).getAsJsonObject();
        TestWorldSave save;
        try {
            try (var world = context.worldBuilder().create()) {
                save = world.getWorldSave();
                var server = world.getServer();
                server.runCommand("gamemode survival @a"); server.runCommand("tp @a 0 -60 0 0 0");
                world.getConnection().waitForChunksRender(); context.waitTicks(20);
                server.runOnServer(s -> {
                    var target = world.getConnection().getServerPlayer();
                    var legacy=settings.deepCopy();legacy.remove("configVersion");
                    legacy.getAsJsonObject("saw").add("opOnlyParts",JsonParser.parseString("[\"torso\",\"left_arm\"]"));
                    apply(s,legacy);
                    check(!BodyConfig.get().saw.opOnlyParts.contains("torso") && BodyConfig.get().saw.opOnlyParts.contains("left_arm"),"Legacy torso OP migration failed");
                    apply(s,settings);
                    verifyRecipes(s, target);
                    for(int mask : new int[]{16,17,3,0}) {
                        BodyParts.set(target,new BodyState(mask));
                        target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);target.setOnGround(true);
                        target.jumpFromGround();
                        double vertical=target.getDeltaMovement().y;
                        check(mask==0 ? vertical==0 : vertical>0 && vertical<0.42,"Legless jump failed: "+mask);
                    }
                    BodyParts.reset(target);target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);

                    var torsoRules=settings.deepCopy();apply(s,torsoRules);
                    for(int mask=16;mask<=31;mask++) {
                        BodyParts.set(target,new BodyState(mask));
                        String denial=SawItem.denial(target,target,BodyPart.TORSO);
                        check(mask==16 ? denial==null : "message.anybodyparts.torso_last".equals(denial),"Torso order failed: "+mask);
                    }
                    torsoRules.getAsJsonObject("saw").addProperty("requireLimbsRemovedForTorso",false);apply(s,torsoRules);
                    check(SawItem.denial(target,target,BodyPart.TORSO)==null,"Torso order switch failed");
                    BodyParts.reset(target);apply(s,settings);

                    // Disabled achievements must block both explicit grants and inventory triggers.
                    var disabled = settings.deepCopy(); disabled.getAsJsonObject("features").addProperty("achievements", false);
                    apply(s, disabled);
                    target.getInventory().setItem(10, new ItemStack(ModItems.MECHANICAL.get(BodyPart.LEFT_LEG)));
                    BodyAdvancements.award(target, "first_mechanical");
                    check(!done(target, "first_mechanical") && !done(target, "collect_mechanical"), "Disabled achievements still granted");
                    apply(s, settings);
                    target.getInventory().setItem(10, ItemStack.EMPTY);
                    var doctor = doctor(s, target);
                    double damage = target.getAttributeValue(Attributes.ATTACK_DAMAGE), attack = target.getAttributeValue(Attributes.ATTACK_SPEED);
                    double mining = target.getAttributeValue(Attributes.BLOCK_BREAK_SPEED), jump = target.getAttributeValue(Attributes.JUMP_STRENGTH);
                    BodyParts.set(target, new BodyState(0));
                    for (var part : BodyPart.values()) {
                        doctor.getInventory().setItem(10, new ItemStack(ModItems.MECHANICAL.get(part)));
                        SawItem.select(doctor.getMainHandItem(), part);
                        SewingNeedleItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                        for (int i=0;i<100;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(), doctor, doctor.getMainHandItem(), 72000-i);
                        check(BodyParts.stored(target).has(part) && (MechanicalParts.stored(target)&part.bit())!=0, "Mechanical sewing failed: "+part);
                        check(doctor.getInventory().getItem(10).isEmpty(), "Mechanical part not consumed");
                        check(!Grafts.get(target).containsKey(part), "Mechanical part retained donor profile");
                    }
                    check(doctor.getOffhandItem().getCount()==11, "Mechanical stitch costs");
                    target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    target.getInventory().setItem(12, new ItemStack(Items.ARROW, 8));
                    check(BodyParts.canUse(target, InteractionHand.MAIN_HAND), "Two mechanical arms rejected bow");
                    Items.BOW.use(target.level(), target, InteractionHand.MAIN_HAND);
                    check(target.isUsingItem(), "Mechanical arms cannot draw bow");
                    target.stopUsingItem();
                    target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    Items.CROSSBOW.use(target.level(), target, InteractionHand.MAIN_HAND);
                    check(target.isUsingItem(), "Mechanical arms cannot load crossbow");
                    target.stopUsingItem();
                    BodyParts.set(target,BodyParts.stored(target).with(BodyPart.LEFT_LEG,false));
                    target.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SEWING_NEEDLE));
                    target.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.STRING,2));
                    target.getInventory().setItem(10,new ItemStack(ModItems.MECHANICAL.get(BodyPart.LEFT_LEG)));
                    SawItem.select(target.getMainHandItem(),BodyPart.LEFT_LEG);
                    SewingNeedleItem.begin(target,target,InteractionHand.MAIN_HAND);
                    check(target.isUsingItem(),"Mechanical arms cannot start self-sewing");
                    for(int i=0;i<100;i++) ModItems.SEWING_NEEDLE.onUseTick(target.level(),target,target.getMainHandItem(),72000-i);
                    check(MechanicalParts.stored(target)==31 && target.getOffhandItem().getCount()==1,"Mechanical arms cannot finish self-sewing");
                    BodyParts.set(target, BodyParts.stored(target).with(BodyPart.LEFT_ARM,false).with(BodyPart.RIGHT_ARM,false));
                    target.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,3));
                    target.inventoryMenu.clicked(9,0,net.minecraft.world.inventory.ContainerInput.THROW,target);
                    target.inventoryMenu.clicked(9,1,net.minecraft.world.inventory.ContainerInput.THROW,target);
                    check(target.getInventory().getItem(9).getCount()==3,"Inventory Q removed item without arms");
                    target.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND,2));
                    target.inventoryMenu.clicked(-999,0,net.minecraft.world.inventory.ContainerInput.PICKUP,target);
                    check(target.inventoryMenu.getCarried().getCount()==2,"Cursor drop without arms");
                    target.inventoryMenu.setCarried(ItemStack.EMPTY);
                    BodyParts.set(target,BodyState.COMPLETE);
                    MechanicalParts.set(target,BodyPart.LEFT_ARM,true);MechanicalParts.set(target,BodyPart.RIGHT_ARM,true);
                    target.inventoryMenu.clicked(9,0,net.minecraft.world.inventory.ContainerInput.THROW,target);
                    check(target.getInventory().getItem(9).getCount()==2,"Mechanical arm cannot drop item");
                    target.getInventory().setItem(9,ItemStack.EMPTY);

                    check(target.getAttributeValue(Attributes.ATTACK_DAMAGE) < damage, "No mechanical attack damage penalty");
                    check(target.getAttributeValue(Attributes.ATTACK_SPEED) < attack, "No mechanical attack speed penalty");
                    check(target.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) < mining, "No mechanical mining penalty");
                    check(target.getAttributeValue(Attributes.JUMP_STRENGTH) < jump, "No mechanical jump penalty");
                    check(BodyParts.speed(target)<0.5 && !BodyParts.canSprint(target) && !BodyParts.canGlide(target), "No mechanical movement/torso penalty");
                    check(done(target,"first_mechanical") && done(target,"full_mechanical"), "Missing mechanical implantation achievements");
                    // Re-cut a prosthesis: preserve item type, never fabricate organic donor data.
                    doctor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIMB_SAW));
                    SawItem.select(doctor.getMainHandItem(), BodyPart.LEFT_LEG);
                    SawItem.begin(doctor, target, InteractionHand.MAIN_HAND);
                    for (int i=0;i<200;i++) ModItems.LIMB_SAW.onUseTick(doctor.level(),doctor,doctor.getMainHandItem(),72000-i);
                    var drops = target.level().getEntitiesOfClass(ItemEntity.class,target.getBoundingBox().inflate(5),e -> e.getItem().is(ModItems.MECHANICAL.get(BodyPart.LEFT_LEG)));
                    check(drops.size()==1 && !drops.getFirst().getItem().has(net.minecraft.core.component.DataComponents.PROFILE), "Mechanical re-cut produced wrong drop");
                    check((MechanicalParts.stored(target)&BodyPart.LEFT_LEG.bit())==0, "Re-cut retained mechanical flag");
                    doctor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SEWING_NEEDLE));
                    SawItem.select(doctor.getMainHandItem(),BodyPart.LEFT_LEG); doctor.getInventory().setItem(10,drops.getFirst().getItem().copy()); drops.getFirst().discard();
                    var mechanicalOff = settings.deepCopy(); mechanicalOff.getAsJsonObject("mechanical").addProperty("enabled",false); apply(s, mechanicalOff);
                    check(BodyParts.get(target).headOnly(), "Disabled mechanical parts remain functional");
                    SewingNeedleItem.begin(doctor,target,InteractionHand.MAIN_HAND);
                    check(!doctor.isUsingItem() && doctor.getInventory().getItem(10).getCount()==1, "Disabled mechanical sewing consumed item");
                    apply(s,settings);
                    SewingNeedleItem.begin(doctor,target,InteractionHand.MAIN_HAND);
                    for (int i=0;i<100;i++) ModItems.SEWING_NEEDLE.onUseTick(doctor.level(),doctor,doctor.getMainHandItem(),72000-i);
                    check(MechanicalParts.stored(target)==31, "Mechanical reattachment failed");
                    target.getInventory().setItem(11,new ItemStack(ModItems.MECHANICAL.get(BodyPart.TORSO)));
                    target.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);target.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
                });
                context.waitFor(c -> MechanicalParts.active(c.player)==31);
                context.runOnClient(c -> { c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);c.player.setXRot(10); });
                context.waitTicks(10);context.takeScreenshot("mechanical-full-body");
                context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
                context.waitTicks(10);context.takeScreenshot("mechanical-first-person");
                context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
                context.waitTicks(3);context.takeScreenshot("mechanical-item-inventory");context.setScreen(() -> null);
                // Real data reload must remove recipes, including from the server's crafting lookup.
                var disabledCraft = settings.deepCopy();disabledCraft.getAsJsonObject("mechanical").addProperty("craftingEnabled",false);
                var future = new AtomicReference<CompletableFuture<Void>>();
                server.runOnServer(s -> { apply(s,disabledCraft); future.set(s.reloadResources(s.getPackRepository().getSelectedIds())); });
                context.waitFor(c -> future.get().isDone());future.get().join();
                server.runOnServer(s -> {
                    for (var part:BodyPart.values()) check(s.getRecipeManager().byKey(recipeKey(part)).isEmpty(), "Disabled mechanical recipe still loaded: "+part);
                    check(MechanicalParts.active(world.getConnection().getServerPlayer())==31, "Craft-only toggle disabled installed prostheses");
                    apply(s,settings);future.set(s.reloadResources(s.getPackRepository().getSelectedIds()));
                });
                context.waitFor(c -> future.get().isDone());future.get().join();
                server.runOnServer(s -> verifyRecipes(s,world.getConnection().getServerPlayer()));
            }
            try (var world=save.open()) {
                context.waitFor(c -> c.player!=null && MechanicalParts.active(c.player)==31);
                world.getServer().runOnServer(s -> {
                    var target=world.getConnection().getServerPlayer();
                    check(MechanicalParts.stored(target)==31 && target.getInventory().getItem(11).is(ModItems.MECHANICAL.get(BodyPart.TORSO)), "Mechanical save/load failed");
                    var perPart=settings.deepCopy();perPart.getAsJsonObject("mechanical").add("disabledParts",JsonParser.parseString("[\"left_arm\"]"));apply(s,perPart);
                    check(!BodyParts.get(target).has(BodyPart.LEFT_ARM) && BodyParts.get(target).has(BodyPart.RIGHT_ARM), "Per-part mechanical toggle failed");
                    apply(s,settings);BodyParts.reset(target);check(MechanicalParts.stored(target)==0,"Reset left mechanical flags");
                    check(Math.abs(target.getAttributeValue(Attributes.JUMP_STRENGTH)-0.42)<0.0001,"Mechanical jump penalty survives reset");
                });
            }
        } finally {
            try { Files.writeString(BodyConfig.path(),original);BodyConfig.reload(null); } catch(Exception e){throw new RuntimeException(e);}
        }
    }
    private static void verifyRecipes(MinecraftServer s, ServerPlayer p) {
        String[][] patterns={{"NI ","PRI"," I "},{" IN","IPR"," I "},{"PI ","NRI"," I "},{" IP","IRN","I  "},{"INI","RPR","INI"}};
        for (var part:BodyPart.values()) {
            var cells=new ArrayList<ItemStack>();
            for(String row:patterns[part.ordinal()])for(char cell:row.toCharArray())cells.add(cell==' '?ItemStack.EMPTY:new ItemStack(switch(cell){case 'N'->Items.NETHERITE_BLOCK;case 'I'->Items.IRON_INGOT;case 'R'->Items.REDSTONE;default->Items.PISTON;}));
            var input=CraftingInput.of(3,3,cells);
            var recipe=s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,p.level()).orElseThrow(()->new AssertionError("Missing mechanical craft: "+part));
            check(recipe.value().assemble(input).is(ModItems.MECHANICAL.get(part)),"Ambiguous mechanical recipe: "+part);
        }
    }
    private static ResourceKey<Recipe<?>> recipeKey(BodyPart part){return ResourceKey.create(Registries.RECIPE,Identifier.fromNamespaceAndPath("anybodyparts","mechanical_"+part.name().toLowerCase(Locale.ROOT)));}
    private static void apply(MinecraftServer s,JsonObject settings){try{Files.writeString(BodyConfig.path(),settings.toString());BodyConfig.reload(s);}catch(Exception e){throw new RuntimeException(e);}}
    private static ServerPlayer doctor(MinecraftServer s,ServerPlayer target){
        var doctor=new ServerPlayer(s,target.level(),new GameProfile(UUID.randomUUID(),"Mechanic"),ClientInformation.createDefault()){
            @Override public void sendOverlayMessage(Component text){}
            @Override public boolean hasInfiniteMaterials(){return true;}
            @Override protected ItemCooldowns createItemCooldowns(){return new ItemCooldowns();}
        };
        doctor.setPos(target.getX(),target.getY(),target.getZ()-1.5);doctor.setOnGround(true);doctor.setYRot(0);doctor.setXRot(15);
        doctor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SEWING_NEEDLE));doctor.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.STRING,16));return doctor;
    }
    private static boolean done(ServerPlayer player,String name){var a=player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("anybodyparts","body/"+name));return a!=null&&player.getAdvancements().getOrStartProgress(a).isDone();}
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}


