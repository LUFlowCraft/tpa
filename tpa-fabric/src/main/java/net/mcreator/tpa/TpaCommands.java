package net.mcreator.tpa;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class TpaCommands {
    private TpaCommands() {}

    /** /<name> <player> | accept | refuse | enable | disable */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String name, TpaManager manager) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            if (target == null) return 0;
                            manager.send(sender, target);
                            return 1;
                        }))
                .then(Commands.literal("accept").executes(ctx -> {
                    manager.accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("refuse").executes(ctx -> {
                    manager.refuse(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("enable").executes(ctx -> {
                    manager.enable(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    manager.disable(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
        dispatcher.register(root);
    }
}
