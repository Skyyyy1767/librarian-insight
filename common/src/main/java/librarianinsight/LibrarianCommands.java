package librarianinsight;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import static librarianinsight.LibrarianInsight.priceDisplay;

/** One command tree and settings implementation for every loader. */
public final class LibrarianCommands<S> {
    private final BiConsumer<S, Component> feedback;

    public LibrarianCommands(BiConsumer<S, Component> feedback) {
        this.feedback = feedback;
    }

    public static <S> void register(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> feedback) {
        var commands = new LibrarianCommands<>(feedback);
        dispatcher.register(commands.command("librarianinsight"));
        dispatcher.register(commands.command("li"));
    }

    private LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public LiteralArgumentBuilder<S> command(String name) {
        return literal(name)
                .then(literal("price")
                        .executes(context -> updatePriceSetting(context.getSource(), null))
                        .then(literal("on").executes(context -> updatePriceSetting(context.getSource(), true)))
                        .then(literal("off").executes(context -> updatePriceSetting(context.getSource(), false))))
                .then(colorCommand())
                .then(themeCommand());
    }

    private LiteralArgumentBuilder<S> colorCommand() {
        var command = literal("color");
        for (LecternTextColor color : LecternTextColor.values()) {
            command.then(literal(color.commandName())
                    .executes(context -> updateTextColor(context.getSource(), color, false)));
        }
        return command.then(literal("reset")
                .executes(context -> updateTextColor(context.getSource(), LecternTextColor.BLACK, true)));
    }

    private LiteralArgumentBuilder<S> themeCommand() {
        var command = literal("theme")
                .executes(context -> updateMenuTheme(context.getSource(), null));
        for (LibrarianMenuTheme theme : LibrarianMenuTheme.values()) {
            command.then(literal(theme.commandName())
                    .executes(context -> updateMenuTheme(context.getSource(), theme)));
        }
        return command;
    }

    private int updatePriceSetting(
            S source,
            Boolean enabled
    ) {
        if (enabled == null) {
            priceDisplay.toggle();
        } else {
            priceDisplay.setEnabled(enabled);
        }
        feedback.accept(source, Component.literal("Price display " + (priceDisplay.isEnabled() ? "enabled." : "disabled.")));
        return 1;
    }

    private int updateTextColor(
            S source,
            LecternTextColor color,
            boolean reset
    ) {
        priceDisplay.setTextColor(color);
        String message = reset
                ? "Text color reset to Black."
                : "Text color set to " + color.displayName() + ".";
        feedback.accept(source, Component.literal(message));
        return 1;
    }

    private int updateMenuTheme(
            S source,
            LibrarianMenuTheme theme
    ) {
        if (theme == null) {
            priceDisplay.toggleMenuTheme();
        } else {
            priceDisplay.setMenuTheme(theme);
        }
        feedback.accept(source, Component.literal(
                "Librarian Insight menu theme set to " + priceDisplay.getMenuTheme().displayName() + "."
        ));
        return 1;
    }
}
