package minidb;

import java.io.File;

public class SelfTest {
    private static int passed = 0;

    public static void main(String[] args) {
        String base = "test-data";
        new File(base).mkdirs();
        String data = base + "/test.db";
        String index = base + "/test-index.db";
        new File(data).delete();
        new File(index).delete();

        SqlEngine db = new SqlEngine(data, index);
        db.createDemoSchema();
        check(db.execute("INSERT INTO owners VALUES (1, 'Иван', '1995-04-12', TRUE);").contains("Добавлено"), "INSERT owners");
        check(db.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, 1);").contains("Добавлено"), "INSERT cars");
        check(db.execute("INSERT INTO cars VALUES (1, 'BMW', 30000.0, TRUE, 1);").startsWith("Ошибка:"), "PK uniqueness");
        check(db.execute("INSERT INTO cars VALUES (2, 'BMW', 30000.0, TRUE, 99);").startsWith("Ошибка:"), "FK validation");
        check(db.execute("CREATE INDEX idx_cars_brand ON cars (brand);").contains("создан"), "CREATE INDEX");
        check(db.execute("SELECT * FROM cars WHERE brand = 'Toyota';").contains("использован индекс"), "indexed SELECT");
        check(db.execute("UPDATE cars SET price = 28000.0, brand = 'Toyota Updated' WHERE id = 1;").contains("Обновлено"), "multi-column UPDATE");
        check(db.execute("DELETE FROM cars WHERE id = 1;").contains("Удалено"), "DELETE");
        check(db.getRowCount("cars") == 0, "row count after DELETE");

        db.execute("INSERT INTO cars VALUES (3, 'Volvo', 18000.0, FALSE, 1);");
        SqlEngine reopened = new SqlEngine(data, index);
        check(reopened.getRowCount("cars") == 1, "persistence after restart");
        check(reopened.getIndexes().contains("cars.brand"), "index restoration");
        check(reopened.execute("DELETE FROM owners WHERE id = 1;").startsWith("Ошибка:"), "FK delete restriction");

        System.out.println("Результат: " + passed + "/12 проверок пройдено.");
        if (passed != 11) System.exit(1);
    }

    private static void check(boolean ok, String name) {
        if (!ok) {
            System.err.println("[FAIL] " + name);
            throw new AssertionError(name);
        }
        passed++;
        System.out.println("[ OK ] " + name);
    }
}
