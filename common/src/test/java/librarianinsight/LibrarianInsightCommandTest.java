package librarianinsight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LibrarianInsightCommandTest {
    @TempDir Path directory;

    @Test
    void commandsUpdateAndPersistSettingsAndSendFeedback() throws Exception {
        PriceDisplayConfig previous = LibrarianInsight.priceDisplay;
        try {
            LibrarianInsight.priceDisplay = PriceDisplayConfig.inDirectory(directory);
            List<String> messages = new ArrayList<>();
            CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
            LibrarianCommands.register(dispatcher, (source, message) -> messages.add(message.getString()));
            Object source = new Object();
            assertEquals(1, dispatcher.execute("li price off", source));
            assertFalse(LibrarianInsight.priceDisplay.isEnabled());
            assertEquals(1, dispatcher.execute("librarianinsight price", source));
            assertEquals(1, dispatcher.execute("li color yellow", source));
            assertEquals(LecternTextColor.YELLOW, LibrarianInsight.priceDisplay.getTextColor());
            assertEquals(1, dispatcher.execute("librarianinsight color reset", source));
            assertEquals(1, dispatcher.execute("li theme dark", source));
            assertEquals(LibrarianMenuTheme.DARK, LibrarianInsight.priceDisplay.getMenuTheme());
            assertEquals(1, dispatcher.execute("librarianinsight theme", source));
            PriceDisplayConfig reloaded = PriceDisplayConfig.inDirectory(directory);
            assertEquals(true, reloaded.isEnabled());
            assertEquals(LecternTextColor.BLACK, reloaded.getTextColor());
            assertEquals(LibrarianMenuTheme.LIGHT, reloaded.getMenuTheme());
            assertEquals(List.of("Price display disabled.", "Price display enabled.",
                    "Text color set to Yellow.", "Text color reset to Black.",
                    "Librarian Insight menu theme set to Dark.",
                    "Librarian Insight menu theme set to Light."), messages);
        } finally {
            LibrarianInsight.priceDisplay = previous;
        }
    }

    @Test
    void fullCommandAndAliasExposeIdenticalTrees() {
        CommandNode<Object> full = new LibrarianCommands<Object>((source, message) -> {}).command("librarianinsight").build();
        CommandNode<Object> alias = new LibrarianCommands<Object>((source, message) -> {}).command("li").build();

        List<String> fullPaths = childPaths(full);
        assertEquals(fullPaths, childPaths(alias));
        assertFalse(fullPaths.isEmpty());
    }

    private static List<String> childPaths(CommandNode<Object> root) {
        List<String> paths = new ArrayList<>();
        collect(root, "", paths);
        paths.sort(String::compareTo);
        return paths;
    }

    private static void collect(
            CommandNode<Object> node,
            String parent,
            List<String> paths
    ) {
        for (CommandNode<Object> child : node.getChildren()) {
            String path = parent.isEmpty() ? child.getName() : parent + "/" + child.getName();
            paths.add(path);
            collect(child, path, paths);
        }
    }
}
