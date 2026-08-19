package librarianinsight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

final class LibrarianInsightCommandTest {
    @Test
    void fullCommandAndAliasExposeIdenticalTrees() {
        CommandNode<FabricClientCommandSource> full = LibrarianInsight.command("librarianinsight").build();
        CommandNode<FabricClientCommandSource> alias = LibrarianInsight.command("li").build();

        List<String> fullPaths = childPaths(full);
        assertEquals(fullPaths, childPaths(alias));
        assertFalse(fullPaths.isEmpty());
    }

    private static List<String> childPaths(CommandNode<FabricClientCommandSource> root) {
        List<String> paths = new ArrayList<>();
        collect(root, "", paths);
        paths.sort(String::compareTo);
        return paths;
    }

    private static void collect(
            CommandNode<FabricClientCommandSource> node,
            String parent,
            List<String> paths
    ) {
        for (CommandNode<FabricClientCommandSource> child : node.getChildren()) {
            String path = parent.isEmpty() ? child.getName() : parent + "/" + child.getName();
            paths.add(path);
            collect(child, path, paths);
        }
    }
}
