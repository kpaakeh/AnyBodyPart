package net.kpkh.anybodyparts;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Pose;

public final class BodyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BodyConfig current = new BodyConfig();
    private transient BodyRules cachedRules;
    public int configVersion = 1;
    public boolean enabled = true;
    public List<String> twoHandedItems = new ArrayList<>(List.of("minecraft:bow", "minecraft:crossbow"));
    public Global global = new Global();
    public Saw saw = new Saw();
    public Movement movement = new Movement();
    public Sewing sewing = new Sewing();
    public Mechanical mechanical = new Mechanical();
    public Crafting crafting = new Crafting();
    public Features features = new Features();
    public static final class Mechanical {
        public boolean enabled = true;
        public boolean craftingEnabled = true;
        public List<String> disabledParts = new ArrayList<>();
        public List<String> disabledCrafts = new ArrayList<>();
        public float legSpeed = 0.75F, legJump = 0.85F;
        public float armDamage = 0.85F, armAttackSpeed = 0.80F, armMiningSpeed = 0.65F, armCrawlSpeed = 0.75F;
        public float torsoSpeed = 0.80F;
    }
    public static final class Crafting { public boolean saw = true, needle = true; }
    public static final class Features {
        public boolean achievements = true, creativeTab = true, detachedDrops = true, progressOverlay = true;
    }
    public boolean restoreOnDeath = true;
    public int adminPermissionLevel = 2;

    public static final class Global {
        public List<String> disabledParts = new ArrayList<>();
        public List<String> uncuttableParts = new ArrayList<>();
    }
    public static final class Saw {
        public boolean enabled = true;
        public boolean allowOtherPlayers = true;
        public List<String> opOnlyParts = new ArrayList<>();
        public double selfSeconds = 4;
        public double otherSeconds = 10;
        public double reach = 2.5;
        public double movementTolerance = 0.01;
        public boolean requireTargetSneaking = false;
        public boolean cancelOnLook = false;
        public boolean cancelOnDamage = true;
        public boolean allowLastArm = true;
        public boolean requireLimbsRemovedForTorso = true;
        public int durabilityCost = 8;
        public float damageOnCut = 0;
        public boolean preventLethalCut = true;
        public boolean logCuts = true;
    }
    public static final class Movement {
        public boolean allowLeglessJump = true;
        public float leglessJumpStrength = 0.65F;
        public float withoutTorso = 0.75F;
        public float hop = 0.55F;
        public float crawlTwo = 0.32F;
        public float crawlOne = 0.18F;
        public float wriggle = 0.06F;
        public float roll = 0.45F;
    }
    public static final class Sewing {
        public boolean enabled = true;
        public boolean allowForeignParts = true;
        public double seconds = 5;
        public double reach = 2.5;
        public double movementTolerance = 0.01;
        public boolean requireTargetSneaking = false;
        public boolean cancelOnDamage = true;
        public int threadCost = 1;
        public int durabilityCost = 1;
    }

    public static BodyConfig get() { return current; }
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("anybodyparts.json"); }
    public BodyRules rules() {
        if (cachedRules == null) cachedRules = new BodyRules(enabled, mask(global.disabledParts), List.copyOf(twoHandedItems), restoreOnDeath,
            movement.withoutTorso, movement.hop, movement.crawlTwo, movement.crawlOne, movement.wriggle, movement.roll, MechanicalRules.from(mechanical), features.creativeTab, movement.allowLeglessJump, movement.leglessJumpStrength);
        return cachedRules;
    }
    public static BodyPart part(String name) {
        try { return BodyPart.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new IllegalArgumentException("Неизвестная часть тела: " + name); }
    }
    public static int mask(List<String> parts) {
        int mask = 0;
        for (String name : parts) mask |= part(name).bit();
        return mask;
    }
    public static BodyConfig parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) throw new IllegalArgumentException("Ожидался JSON-объект");
        checkKeys(element.getAsJsonObject(), new BodyConfig());
        BodyConfig config = GSON.fromJson(element, BodyConfig.class);
        config.validate();
        return config;
    }
    private static void checkKeys(JsonObject json, Object defaults) {
        JsonObject schema = GSON.toJsonTree(defaults).getAsJsonObject();
        for (var entry : json.entrySet()) {
            if (entry.getKey().startsWith("_")) continue;
            if (!schema.has(entry.getKey())) throw new IllegalArgumentException("Неизвестная настройка: " + entry.getKey());
            JsonElement value = entry.getValue(), expected = schema.get(entry.getKey());
            if (value.isJsonNull() || value.isJsonArray() != expected.isJsonArray() || value.isJsonObject() != expected.isJsonObject()) {
                throw new IllegalArgumentException("Неверный тип настройки: " + entry.getKey());
            }
            if (value.isJsonObject()) checkKeys(value.getAsJsonObject(), expected);
            else if (value.isJsonPrimitive() && (value.getAsJsonPrimitive().isBoolean() != expected.getAsJsonPrimitive().isBoolean()
                || value.getAsJsonPrimitive().isNumber() != expected.getAsJsonPrimitive().isNumber())) {
                throw new IllegalArgumentException("Неверный тип настройки: " + entry.getKey());
            }
        }
    }
    public void validate() {
        Objects.requireNonNull(global); Objects.requireNonNull(saw); Objects.requireNonNull(movement); Objects.requireNonNull(twoHandedItems);
        Objects.requireNonNull(mechanical); Objects.requireNonNull(crafting); Objects.requireNonNull(features);
        mask(mechanical.disabledParts); mask(mechanical.disabledCrafts);
        for (float value : new float[]{mechanical.legSpeed, mechanical.legJump, mechanical.armDamage, mechanical.armAttackSpeed,
            mechanical.armMiningSpeed, mechanical.armCrawlSpeed, mechanical.torsoSpeed}) range(value, 0.05, 0.95, "mechanical multiplier");
        int disabled = mask(global.disabledParts), protectedMask = mask(global.uncuttableParts);
        mask(saw.opOnlyParts);
        if ((disabled & protectedMask) != 0) throw new IllegalArgumentException("Часть одновременно в disabledParts и uncuttableParts");
        for (String id : twoHandedItems) {
            Identifier parsed = Identifier.parse(id.startsWith("#") ? id.substring(1) : id);
            if (!id.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(parsed)) throw new IllegalArgumentException("Неизвестный предмет: " + id);
        }
        range(saw.selfSeconds, 0.5, 120, "saw.selfSeconds"); range(saw.otherSeconds, 10, 120, "saw.otherSeconds");
        range(saw.reach, 1, 3, "saw.reach"); range(saw.movementTolerance, 0.001, 0.05, "saw.movementTolerance");
        range(saw.durabilityCost, 1, 64, "saw.durabilityCost"); range(saw.damageOnCut, 0, 20, "saw.damageOnCut");
        range(adminPermissionLevel, 0, 4, "adminPermissionLevel");
        Objects.requireNonNull(sewing);
        range(sewing.seconds, 0.5, 120, "sewing.seconds"); range(sewing.reach, 1, 3, "sewing.reach");
        range(sewing.movementTolerance, 0.001, 0.05, "sewing.movementTolerance");
        range(sewing.threadCost, 1, 16, "sewing.threadCost"); range(sewing.durabilityCost, 1, 64, "sewing.durabilityCost");
        range(movement.leglessJumpStrength, 0.05, 1, "movement.leglessJumpStrength");
        for (float speed : new float[]{movement.withoutTorso, movement.hop, movement.crawlTwo, movement.crawlOne, movement.wriggle, movement.roll}) range(speed, 0.01, 1, "movement");
        if (movement.crawlTwo < movement.crawlOne || movement.crawlOne < movement.wriggle) throw new IllegalArgumentException("Скорости: crawlTwo >= crawlOne >= wriggle");
    }
    private static void range(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(name + ": допустимо " + min + ".." + max);
    }
    public static void initialize() {
        try { reload(null); }
        catch (Exception e) { AnyBodyParts.LOGGER.error("Не удалось прочитать {}. Используются безопасные значения по умолчанию; файл сохранён.", path(), e); }
    }
    private static boolean addMissing(JsonObject target, JsonObject defaults) {
        boolean changed = false;
        for (var entry : defaults.entrySet()) {
            if (!target.has(entry.getKey())) { target.add(entry.getKey(), entry.getValue().deepCopy()); changed = true; }
            else if (target.get(entry.getKey()).isJsonObject() && entry.getValue().isJsonObject())
                changed |= addMissing(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
        }
        return changed;
    }
    public static void reload(MinecraftServer server) throws IOException {
        Path path = path();
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            try (var input = Objects.requireNonNull(BodyConfig.class.getResourceAsStream("/anybodyparts-default.json"))) {
                Files.copy(input, path);
            }
        }
        if (Files.size(path) > 65536) throw new IOException("Конфиг превышает 64 КиБ");
        JsonObject document = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        // Validate before migrating so an invalid user file is never rewritten.
        parse(document.toString());
        boolean migrated = !document.has("configVersion");
        if (migrated) {
            JsonObject saw = document.has("saw") ? document.getAsJsonObject("saw") : null;
            if (saw != null && saw.has("opOnlyParts")) saw.getAsJsonArray("opOnlyParts").remove(new JsonPrimitive("torso"));
            document.addProperty("configVersion", 1);
        }
        BodyConfig next = parse(document.toString());
        try (var input = Objects.requireNonNull(BodyConfig.class.getResourceAsStream("/anybodyparts-default.json"))) {
            JsonObject defaults = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (addMissing(document, defaults) | migrated) {
                Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        current = next;
        if (server != null) {
            SawItem.cancelAll();
            SewingNeedleItem.cancelAll();
            for (var player : server.getPlayerList().getPlayers()) {
                player.setAttached(BodyParts.RULES, next.rules());
                player.setAttached(BodyParts.CUTTING, 0);
                player.setPose(Pose.SWIMMING);
                player.refreshDimensions();
                if (!BodyParts.canSprint(player)) player.setSprinting(false);
            }
        }
    }
}
