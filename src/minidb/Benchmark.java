package minidb;

import java.io.File;


public class Benchmark {
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        String dir = "benchmark-data";
        new File(dir).mkdirs();
        String data = dir + "/bench.db", index = dir + "/bench-index.db";
        new File(data).delete();
        new File(index).delete();
        SqlEngine db = new SqlEngine(data, index);
        db.execute("CREATE TABLE samples (id INT PRIMARY KEY, group_name STRING, amount DOUBLE);");

        long insertStart = System.nanoTime();
        for (int start = 0; start < count; start += 250) {
            StringBuilder sql = new StringBuilder("INSERT INTO samples VALUES ");
            int end = Math.min(count, start + 250);
            for (int i = start; i < end; i++) {
                if (i > start) sql.append(",");
                sql.append("(").append(i + 1).append(", 'group")
                   .append(i % 100).append("', ").append(i * 1.25).append(")");
            }
            sql.append(";");
            String result = db.execute(sql.toString());
            if (result.startsWith("Ошибка:")) throw new RuntimeException(result);
        }
        long insertMs = (System.nanoTime() - insertStart) / 1000000L;

        long scanStart = System.nanoTime();
        for (int i = 0; i < 30; i++) db.execute("SELECT * FROM samples WHERE group_name = 'group42';");
        long scanNs = System.nanoTime() - scanStart;

        db.execute("CREATE INDEX idx_samples_group ON samples (group_name);");
        long indexStart = System.nanoTime();
        for (int i = 0; i < 30; i++) db.execute("SELECT * FROM samples WHERE group_name = 'group42';");
        long indexNs = System.nanoTime() - indexStart;

        System.out.println("Строк: " + count);
        System.out.println("Вставка: " + insertMs + " мс");
        System.out.println("30 запросов без индекса: " + (scanNs / 1000000.0) + " мс");
        System.out.println("30 запросов с индексом: " + (indexNs / 1000000.0) + " мс");
        System.out.println("Ускорение: " + (indexNs == 0 ? "n/a" : String.format(java.util.Locale.US, "%.2fx", (double)scanNs / indexNs)));
        System.out.println("Примечание: это учебный микробенчмарк; повторите несколько раз и сравнивайте медиану.");
    }
}
