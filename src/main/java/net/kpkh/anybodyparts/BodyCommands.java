package net.kpkh.anybodyparts;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BodyCommands {
    private BodyCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = Commands.literal("bodyparts").requires(s -> BodyPermissions.admin(s, "command.status") || BodyPermissions.admin(s, "command.menu")
                || BodyPermissions.admin(s, "command.reset") || BodyPermissions.admin(s, "command.reload")
                || BodyPermissions.admin(s, "command.give_saw") || java.util.Arrays.stream(BodyPart.values()).anyMatch(p -> BodyPermissions.set(s, p)))
                ;
            root.then(Commands.literal("status").requires(s -> BodyPermissions.admin(s, "command.status"))
                .then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    var players = EntityArgument.getPlayers(ctx, "players");
                    for (var p : players) ctx.getSource().sendSuccess(() -> {
                        var text = Component.literal(p.getGameProfile().name() + ": ")
                            .append(Component.translatable("movement.anybodyparts." + BodyParts.get(p).movement()));
                        for (var part : BodyPart.values()) text.append(" | ").append(Component.translatable(part.key()))
                            .append(BodyParts.get(p).has(part) ? ": +" : ": -");
                        return text;
                    }, false);
                    return players.size();
                })));
            var set = Commands.literal("set").requires(s -> java.util.Arrays.stream(BodyPart.values()).anyMatch(p -> BodyPermissions.set(s, p)));
            var targets = Commands.argument("players", EntityArgument.players());
            for (var part : BodyPart.values()) targets.then(Commands.literal(part.name().toLowerCase(java.util.Locale.ROOT))
                .requires(s -> BodyPermissions.set(s, part))
                .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                    int changed = 0;
                    for (var p : EntityArgument.getPlayers(ctx, "players")) if (BodyAdmin.change(p, part, BoolArgumentType.getBool(ctx, "enabled"))) changed++;
                    int count = changed;
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.anybodyparts.changed", count), false);
                    return changed;
                })));
            root.then(set.then(targets));
            root.then(Commands.literal("reset").requires(s -> BodyPermissions.admin(s, "command.reset"))
                .then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    int changed = 0;
                    for (var p : EntityArgument.getPlayers(ctx, "players")) if (BodyParts.reset(p)) changed++;
                    int count = changed;
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.anybodyparts.changed", count), false);
                    return changed;
                })));
            root.then(Commands.literal("menu").requires(s -> BodyPermissions.admin(s, "command.menu")).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                BodyAdmin.send(p, p, true);
                return 1;
            }));
            root.then(Commands.literal("give_saw").requires(s -> BodyPermissions.admin(s, "command.give_saw"))
                .then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    var players = EntityArgument.getPlayers(ctx, "players");
                    for (var p : players) {
                        var item = new ItemStack(ModItems.LIMB_SAW);
                        if (!p.getInventory().add(item)) p.drop(item, false, net.minecraft.util.Prediction.PREDICTED);
                    }
                    return players.size();
                })));
            root.then(Commands.literal("reload").requires(s -> BodyPermissions.admin(s, "command.reload")).executes(ctx -> {
                try {
                    BodyConfig.reload(ctx.getSource().getServer());
                    var source = ctx.getSource();
                    var server = source.getServer();
                    server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                        if (failure == null) source.sendSuccess(() -> Component.translatable("message.anybodyparts.reloaded"), false);
                        else source.sendFailure(Component.translatable("message.anybodyparts.config_error", failure.getMessage()));
                    }));
                    return 1;
                } catch (Exception e) {
                    ctx.getSource().sendFailure(Component.translatable("message.anybodyparts.config_error", e.getMessage()));
                    return 0;
                }
            }));
            dispatcher.register(root);
        });
    }
}
