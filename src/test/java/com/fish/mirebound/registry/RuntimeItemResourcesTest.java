package com.fish.mirebound.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class RuntimeItemResourcesTest {
    @Test
    void recipesAndLootOnlyReferenceDeclaredModItems() throws Exception {
        Set<String> items = declaredItems();
        assertTrue(items.contains("mirebound:rope"));
        assertTrue(items.contains("mirebound:mud"));

        List<String> invalid = new ArrayList<>();
        for (String directory : List.of("recipe", "loot_table")) {
            Path root = Path.of("src/main/resources/data/mirebound", directory);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    inspect(JsonParser.parseString(Files.readString(path)), path.toString(), items, invalid);
                }
            }
        }
        assertTrue(invalid.isEmpty(), () -> "Unregistered runtime items:\n" + String.join("\n", invalid));
    }

    private static Set<String> declaredItems() throws Exception {
        // Parse declarations without bootstrapping NeoForge in a plain JUnit worker.
        // Comments, unrelated string literals and old resource files cannot add item IDs.
        Set<String> items = new HashSet<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var sources = files.getJavaFileObjectsFromPaths(List.of(
                    Path.of("src/main/java/com/fish/mirebound/registry/ModBlocks.java"),
                    Path.of("src/main/java/com/fish/mirebound/registry/ModMudworkContent.java")));
            JavacTask task = (JavacTask) compiler.getTask(null, files, null,
                    List.of("-proc:none"), null, sources);
            for (var source : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        String method = call.getMethodSelect().toString();
                        if ((method.equals("ITEMS.registerItem")
                                || method.equals("registerMudBall")
                                || method.equals("registerSinkingBlock"))
                                && !call.getArguments().isEmpty()
                                && call.getArguments().getFirst() instanceof LiteralTree literal
                                && literal.getValue() instanceof String name) {
                            items.add("mirebound:" + name);
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(source, null);
            }
        }
        return items;
    }

    private static void inspect(JsonElement value, String path, Set<String> items, List<String> invalid) {
        if (value.isJsonArray()) {
            int index = 0;
            for (JsonElement element : value.getAsJsonArray()) {
                inspect(element, path + "[" + index++ + "]", items, invalid);
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            check(object.get("item"), path, items, invalid);
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item")) {
                check(object.get("name"), path, items, invalid);
            }
            if (object.has("result")) {
                JsonElement result = object.get("result");
                check(result.isJsonObject() ? result.getAsJsonObject().get("id") : result,
                        path, items, invalid);
            }
            for (var entry : object.entrySet()) {
                inspect(entry.getValue(), path + "." + entry.getKey(), items, invalid);
            }
        }
    }

    private static void check(JsonElement value, String path, Set<String> items, List<String> invalid) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String id = value.getAsString();
            if (id.startsWith("mirebound:") && !items.contains(id)) {
                invalid.add(path + " -> " + id);
            }
        }
    }
}
