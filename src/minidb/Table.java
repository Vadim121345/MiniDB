package minidb;

import java.io.Serializable;
import java.util.*;


public class Table implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String name;
    public final LinkedHashMap<String, Column> columns = new LinkedHashMap<String, Column>();
    public final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    public String primaryKey;

    public Table(String name, List<Column> definition) {
        this.name = name.toLowerCase();
        for (Column c : definition) {
            if (columns.containsKey(c.name)) throw new IllegalArgumentException("Duplicate column: " + c.name);
            columns.put(c.name, c);
            if (c.primaryKey) {
                if (primaryKey != null) throw new IllegalArgumentException("Only one primary key is supported");
                primaryKey = c.name;
            }
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("Table must have columns");
    }

    public Column column(String name) {
        Column c = columns.get(name.toLowerCase());
        if (c == null) throw new IllegalArgumentException("Unknown column '" + name + "' in table " + this.name);
        return c;
    }
}
