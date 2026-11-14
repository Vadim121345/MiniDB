package minidb;

import java.util.*;
import java.util.regex.*;


public class SqlEngine {
    private final DatabaseState state;
    private final StorageManager storage;
    private final IndexManager indexes;

    public SqlEngine(String dataFile, String indexFile) {
        storage = new StorageManager(dataFile);
        state = storage.load();
        indexes = new IndexManager(indexFile);
        indexes.rebuild(state);
    }

    public void createDemoSchema() {
        if (!state.tables.containsKey("owners")) {
            List<Column> c = Arrays.asList(
                new Column("id", DataType.INT, true, null, null),
                new Column("name", DataType.STRING, false, null, null),
                new Column("birth_date", DataType.DATE, false, null, null),
                new Column("active", DataType.BOOLEAN, false, null, null));
            state.tables.put("owners", new Table("owners", c));
        }
        if (!state.tables.containsKey("cars")) {
            List<Column> c = Arrays.asList(
                new Column("id", DataType.INT, true, null, null),
                new Column("brand", DataType.STRING, false, null, null),
                new Column("price", DataType.DOUBLE, false, null, null),
                new Column("is_new", DataType.BOOLEAN, false, null, null),
                new Column("owner_id", DataType.INT, false, "owners", "id"));
            state.tables.put("cars", new Table("cars", c));
        }
        persist();
    }

    public String execute(String sql) {
        String s = sql.trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        if (s.isEmpty()) return "";
        String upper = s.toUpperCase(Locale.ROOT);
        try {
            if (upper.startsWith("CREATE TABLE ")) return createTable(s);
            if (upper.startsWith("DROP TABLE ")) return dropTable(s);
            if (upper.startsWith("CREATE INDEX ")) return createIndex(s);
            if (upper.startsWith("INSERT INTO ")) return insert(s);
            if (upper.startsWith("SELECT ")) return select(s);
            if (upper.startsWith("UPDATE ")) return update(s);
            if (upper.startsWith("DELETE FROM ")) return delete(s);
            return "Ошибка: неизвестная команда.";
        } catch (RuntimeException e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private String createTable(String s) {
        Matcher m = Pattern.compile("(?is)^CREATE TABLE (\\w+)\\s*\\((.*)\\)$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный CREATE TABLE");
        String name = m.group(1).toLowerCase();
        if (state.tables.containsKey(name)) throw new IllegalArgumentException("Таблица уже существует");
        List<Column> cols = new ArrayList<Column>();
        for (String part : splitTop(m.group(2), ',')) {
            String[] bits = part.trim().split("\\s+");
            if (bits.length < 2) throw new IllegalArgumentException("Неверное описание столбца: " + part);
            String col = bits[0];
            DataType type = DataType.fromSql(bits[1]);
            boolean pk = part.toUpperCase().contains("PRIMARY KEY");
            String rt = null, rc = null;
            Matcher ref = Pattern.compile("(?i)REFERENCES\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)").matcher(part);
            if (ref.find()) { rt = ref.group(1); rc = ref.group(2); }
            if (rt != null && !state.tables.containsKey(rt.toLowerCase()))
                throw new IllegalArgumentException("Таблица для REFERENCES не существует: " + rt);
            cols.add(new Column(col, type, pk, rt, rc));
        }
        state.tables.put(name, new Table(name, cols));
        persist();
        return "Таблица " + name + " создана.";
    }

    private String dropTable(String s) {
        Matcher m = Pattern.compile("(?i)^DROP TABLE\\s+(\\w+)$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный DROP TABLE");
        String name = m.group(1).toLowerCase();
        for (Table other : state.tables.values()) {
            if (!other.name.equals(name)) {
                for (Column column : other.columns.values())
                    if (name.equals(column.referenceTable))
                        throw new IllegalArgumentException("Нельзя удалить таблицу: на неё ссылается " + other.name);
            }
        }
        if (state.tables.remove(name) == null) throw new IllegalArgumentException("Таблица не найдена");
        Iterator<String> it = state.indexDefinitions.iterator();
        while (it.hasNext()) if (it.next().startsWith(name + ".")) it.remove();
        indexes.rebuild(state); persist();
        return "Таблица " + name + " удалена.";
    }

    private String createIndex(String s) {
        Matcher m = Pattern.compile("(?i)^CREATE INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный CREATE INDEX");
        String table = m.group(2).toLowerCase(), col = m.group(3).toLowerCase();
        Table t = table(table);
        t.column(col);
        String def = table + "." + col;
        if (!state.indexDefinitions.add(def)) throw new IllegalArgumentException("Индекс для столбца уже существует");
        indexes.create(t, col); persist();
        return "Индекс " + m.group(1) + " создан для " + def + ".";
    }

    private String insert(String s) {
        Matcher m = Pattern.compile("(?is)^INSERT INTO\\s+(\\w+)\\s*(?:\\((.*?)\\))?\\s*VALUES\\s*(.*)$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный INSERT");
        Table t = table(m.group(1));
        List<String> cols = new ArrayList<String>();
        if (m.group(2) != null) for (String c : splitTop(m.group(2), ',')) cols.add(c.trim().toLowerCase());
        else cols.addAll(t.columns.keySet());
        for (String c : cols) t.column(c);
        String values = m.group(3).trim();
        List<String> tuples = extractTuples(values);
        List<Map<String, Object>> added = new ArrayList<Map<String, Object>>();
        try {
            for (String tuple : tuples) {
                List<String> vals = splitTop(tuple, ',');
                if (vals.size() != cols.size()) throw new IllegalArgumentException("Количество значений не совпадает с количеством столбцов");
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (String c : t.columns.keySet()) row.put(c, null);
                for (int i = 0; i < cols.size(); i++) row.put(cols.get(i), t.column(cols.get(i)).type.convert(vals.get(i)));
                validateRow(t, row, null);
                t.rows.add(row);
                added.add(row);
            }
        } catch (RuntimeException ex) {
            t.rows.removeAll(added);
            throw ex;
        }
        afterMutation();
        return "Добавлено записей: " + added.size() + ".";
    }

    private String select(String s) {
        Matcher m = Pattern.compile("(?is)^SELECT\\s+(.*?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный SELECT");
        Table t = table(m.group(2));
        List<String> selected = new ArrayList<String>();
        if (m.group(1).trim().equals("*")) selected.addAll(t.columns.keySet());
        else for (String c : splitTop(m.group(1), ',')) { t.column(c.trim()); selected.add(c.trim().toLowerCase()); }
        Condition condition = m.group(3) == null ? null : condition(t, m.group(3));
        List<Map<String, Object>> rows = matching(t, condition);
        StringBuilder out = new StringBuilder();
        for (String c : selected) out.append("+----------------");
        out.append("+\n");
        for (String c : selected) out.append("| ").append(pad(c, 14)).append(" ");
        out.append("|\n");
        for (String c : selected) out.append("+----------------");
        out.append("+\n");
        for (Map<String, Object> row : rows) {
            for (String c : selected) out.append("| ").append(pad(String.valueOf(row.get(c)), 14)).append(" ");
            out.append("|\n");
        }
        for (String c : selected) out.append("+----------------");
        out.append("+\n").append("Строк: ").append(rows.size());
        if (condition != null && "=".equals(condition.operator) && indexes.has(t.name, condition.column))
            out.append(" (использован индекс)");
        return out.toString();
    }

    private String update(String s) {
        Matcher m = Pattern.compile("(?is)^UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный UPDATE");
        Table t = table(m.group(1));
        List<String> assignments = splitTop(m.group(2), ',');
        if (assignments.isEmpty()) throw new IllegalArgumentException("Не заданы поля для UPDATE");

        Map<String, Object> newValues = new LinkedHashMap<String, Object>();
        for (String assignment : assignments) {
            Matcher a = Pattern.compile("(?is)^\\s*(\\w+)\\s*=\\s*(.+?)\\s*$").matcher(assignment);
            if (!a.find()) throw new IllegalArgumentException("Неверное присваивание: " + assignment);
            String col = a.group(1).toLowerCase();
            if (newValues.containsKey(col)) throw new IllegalArgumentException("Столбец указан дважды: " + col);
            newValues.put(col, t.column(col).type.convert(a.group(2)));
        }

        Condition cond = m.group(3) == null ? null : condition(t, m.group(3));
        List<Map<String, Object>> targets = matching(t, cond);
        List<Map<String, Object>> snapshots = new ArrayList<Map<String, Object>>();
        try {
            for (Map<String, Object> row : targets) {
                snapshots.add(new LinkedHashMap<String, Object>(row));
                row.putAll(newValues);
                validateRow(t, row, row);
            }
        } catch (RuntimeException ex) {
            for (int i = 0; i < snapshots.size(); i++) {
                Map<String, Object> row = targets.get(i);
                row.clear();
                row.putAll(snapshots.get(i));
            }
            throw ex;
        }
        afterMutation();
        return "Обновлено записей: " + targets.size() + ".";
    }

    private String delete(String s) {
        Matcher m = Pattern.compile("(?is)^DELETE FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Неверный DELETE");
        Table t = table(m.group(1));
        Condition cond = m.group(2) == null ? null : condition(t, m.group(2));
        List<Map<String, Object>> targets = matching(t, cond);
        for (Table child : state.tables.values()) {
            for (Column column : child.columns.values()) {
                if (t.name.equals(column.referenceTable)) {
                    for (Map<String, Object> parentRow : targets) {
                        Object parentValue = parentRow.get(column.referenceColumn);
                        for (Map<String, Object> childRow : child.rows) {
                            if (parentValue != null && parentValue.equals(childRow.get(column.name)))
                                throw new IllegalArgumentException("Удаление запрещено: запись используется таблицей " + child.name);
                        }
                    }
                }
            }
        }
        t.rows.removeAll(targets);
        afterMutation();
        return "Удалено записей: " + targets.size() + ".";
    }

    private static class Condition {
        String column, operator; Object value;
        Condition(String c, String o, Object v) { column=c; operator=o; value=v; }
    }

    private Condition condition(Table t, String raw) {
        Matcher m = Pattern.compile("(?is)^\\s*(\\w+)\\s*(>=|<=|<>|!=|=|>|<)\\s*(.+?)\\s*$").matcher(raw);
        if (!m.find()) throw new IllegalArgumentException("Поддерживается одно простое условие WHERE");
        String col = m.group(1).toLowerCase();
        return new Condition(col, m.group(2), t.column(col).type.convert(m.group(3)));
    }

    private List<Map<String, Object>> matching(Table t, Condition c) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Set<Object> candidates = null;
        if (c != null && "=".equals(c.operator) && indexes.has(t.name, c.column)) {
            candidates = indexes.lookup(t.name, c.column, c.value);
        }
        for (Map<String, Object> row : t.rows) {
            if (candidates != null && !candidates.contains(row.get(t.primaryKey))) continue;
            if (c == null || test(row.get(c.column), c.operator, c.value)) result.add(row);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean test(Object a, String op, Object b) {
        if (a == null || b == null) return "=".equals(op) && a == b;
        int cmp;
        if (a instanceof Comparable && a.getClass().isInstance(b)) cmp = ((Comparable)a).compareTo(b);
        else cmp = String.valueOf(a).compareTo(String.valueOf(b));
        if ("=".equals(op)) return cmp == 0;
        if ("!=".equals(op) || "<>".equals(op)) return cmp != 0;
        if (">".equals(op)) return cmp > 0;
        if ("<".equals(op)) return cmp < 0;
        if (">=".equals(op)) return cmp >= 0;
        if ("<=".equals(op)) return cmp <= 0;
        return false;
    }

    private void validateRow(Table t, Map<String, Object> row, Map<String, Object> current) {
        if (t.primaryKey != null) {
            Object key = row.get(t.primaryKey);
            if (key == null) throw new IllegalArgumentException("Первичный ключ не может быть NULL");
            for (Map<String, Object> other : t.rows) {
                if (other != current && key.equals(other.get(t.primaryKey)))
                    throw new IllegalArgumentException("Нарушена уникальность первичного ключа: " + key);
            }
        }
        for (Column c : t.columns.values()) {
            if (c.referenceTable != null && row.get(c.name) != null) {
                Table parent = table(c.referenceTable);
                boolean found = false;
                for (Map<String, Object> p : parent.rows)
                    if (row.get(c.name).equals(p.get(c.referenceColumn))) { found = true; break; }
                if (!found) throw new IllegalArgumentException("Нарушен внешний ключ " + c.name);
            }
        }
    }

    private Table table(String name) {
        Table t = state.tables.get(name.toLowerCase());
        if (t == null) throw new IllegalArgumentException("Таблица не найдена: " + name);
        return t;
    }

    private void afterMutation() { indexes.rebuild(state); persist(); }
    private void persist() { storage.save(state); }



    public List<String> getTableNames() {
        List<String> names = new ArrayList<String>(state.tables.keySet());
        Collections.sort(names);
        return names;
    }


    public List<String> getColumnNames(String tableName) {
        return new ArrayList<String>(table(tableName).columns.keySet());
    }


    public List<Object[]> getRows(String tableName) {
        Table t = table(tableName);
        List<Object[]> result = new ArrayList<Object[]>();
        for (Map<String, Object> row : t.rows) {
            Object[] values = new Object[t.columns.size()];
            int i = 0;
            for (String column : t.columns.keySet()) values[i++] = row.get(column);
            result.add(values);
        }
        return result;
    }


    public String getPrimaryKey(String tableName) {
        return table(tableName).primaryKey;
    }


    public List<String> getIndexes() {
        List<String> result = new ArrayList<String>(state.indexDefinitions);
        Collections.sort(result);
        return result;
    }


    public int getRowCount(String tableName) {
        return table(tableName).rows.size();
    }


    public String describeTable(String tableName) {
        Table t = table(tableName);
        StringBuilder b = new StringBuilder("Таблица ").append(t.name).append("\\n");
        for (Column c : t.columns.values()) {
            b.append("  ").append(c.name).append(" ").append(c.type);
            if (c.primaryKey) b.append(" PRIMARY KEY");
            if (c.referenceTable != null) b.append(" REFERENCES ").append(c.referenceTable)
                    .append("(").append(c.referenceColumn).append(")");
            b.append("\\n");
        }
        return b.toString();
    }

    private static String pad(String s, int n) {
        if (s == null) s = "null";
        if (s.length() > n) s = s.substring(0, n - 1) + "…";
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    private static List<String> splitTop(String s, char delimiter) {
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder(); boolean quote = false; int depth = 0;
        for (int i=0;i<s.length();i++) {
            char ch=s.charAt(i);
            if (ch=='\'' && (i==0 || s.charAt(i-1)!='\\')) quote=!quote;
            if (!quote) { if (ch=='(') depth++; if (ch==')') depth--; }
            if (ch==delimiter && !quote && depth==0) { out.add(b.toString().trim()); b.setLength(0); }
            else b.append(ch);
        }
        if (b.length()>0) out.add(b.toString().trim());
        return out;
    }

    private static List<String> extractTuples(String s) {
        List<String> out = new ArrayList<String>(); boolean quote=false; int depth=0, start=-1;
        for (int i=0;i<s.length();i++) {
            char ch=s.charAt(i);
            if (ch=='\'') quote=!quote;
            if (!quote) {
                if (ch=='(') { if (depth==0) start=i+1; depth++; }
                if (ch==')') { depth--; if (depth==0 && start>=0) out.add(s.substring(start,i)); }
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Ожидался список VALUES в скобках");
        return out;
    }
}
