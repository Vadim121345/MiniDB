package minidb;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class IndexManager {
    private final Path file;
    private final Map<String, Map<String, Set<Object>>> indexes =
            new HashMap<String, Map<String, Set<Object>>>();

    public IndexManager(String filename) {
        file = Paths.get(filename);
        load();
    }

    private String indexKey(String table, String column) {
        return table.toLowerCase() + "." + column.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(file)) return;
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object o = in.readObject();
            if (o instanceof Map) indexes.putAll((Map<String, Map<String, Set<Object>>>) o);
        } catch (Exception e) {
            System.err.println("Индексы будут восстановлены из данных: " + e.getMessage());
        }
    }

    public void rebuild(DatabaseState state) {
        indexes.clear();
        for (String def : state.indexDefinitions) {
            String[] p = def.split("\\.", 2);
            Table t = state.tables.get(p[0]);
            if (t != null && t.columns.containsKey(p[1])) buildOne(t, p[1]);
        }
        save();
    }

    public void create(Table table, String column) {
        table.column(column);
        buildOne(table, column.toLowerCase());
        save();
    }

    private void buildOne(Table table, String column) {
        Map<String, Set<Object>> map = new HashMap<String, Set<Object>>();
        for (Map<String, Object> row : table.rows) {
            Object value = row.get(column);
            if (value != null) {
                Set<Object> keys = map.get(String.valueOf(value));
                if (keys == null) { keys = new LinkedHashSet<Object>(); map.put(String.valueOf(value), keys); }
                keys.add(row.get(table.primaryKey));
            }
        }
        indexes.put(indexKey(table.name, column), map);
    }

    public Set<Object> lookup(String table, String column, Object value) {
        Map<String, Set<Object>> map = indexes.get(indexKey(table, column));
        if (map == null) return null;
        Set<Object> result = map.get(String.valueOf(value));
        return result == null ? Collections.<Object>emptySet() : new LinkedHashSet<Object>(result);
    }

    public boolean has(String table, String column) {
        return indexes.containsKey(indexKey(table, column));
    }

    public void save() {
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temp))) {
                out.writeObject(indexes);
            }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new RuntimeException("Index save failed: " + e.getMessage(), e); }
    }
}
