package minidb;

import java.io.Serializable;

public enum DataType implements Serializable {
    INT, DOUBLE, STRING, DATE, BOOLEAN;

    public static DataType fromSql(String value) {
        String v = value.trim().toUpperCase();
        if (v.equals("INTEGER")) v = "INT";
        if (v.equals("FLOAT") || v.equals("REAL")) v = "DOUBLE";
        if (v.equals("VARCHAR") || v.equals("TEXT")) v = "STRING";
        return DataType.valueOf(v);
    }

    public Object convert(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("NULL")) return null;
        switch (this) {
            case INT: return Integer.valueOf(s);
            case DOUBLE: return Double.valueOf(s);
            case STRING:
                if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) ||
                        (s.startsWith("\"") && s.endsWith("\""))))
                    s = s.substring(1, s.length() - 1);
                return s.replace("''", "'");
            case DATE:
                if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'"))
                    s = s.substring(1, s.length() - 1);
                return java.time.LocalDate.parse(s);
            case BOOLEAN: return Boolean.valueOf(s);
            default: throw new IllegalArgumentException("Unsupported type: " + this);
        }
    }
}
