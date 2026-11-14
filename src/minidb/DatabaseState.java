package minidb;

import java.io.Serializable;
import java.util.*;


public class DatabaseState implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Map<String, Table> tables = new LinkedHashMap<String, Table>();
    public final Set<String> indexDefinitions = new LinkedHashSet<String>();
}
