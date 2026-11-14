package minidb;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        final SqlEngine db = new SqlEngine("data/minidb.db", "data/indexes.db");
        db.createDemoSchema();

        if (args.length > 0 && "--console".equalsIgnoreCase(args[0])) {
            runConsole(db);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception ignored) { }
                new DatabaseFrame(db).setVisible(true);
            }
        });
    }

    private static void runConsole(SqlEngine db) {
        System.out.println("MiniDB — консольный режим. HELP — справка, EXIT — выход.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("minidb> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("EXIT") || line.equalsIgnoreCase("QUIT")) break;
            if (line.equalsIgnoreCase("HELP")) {
                System.out.println("Команды: CREATE TABLE, DROP TABLE, CREATE INDEX, INSERT INTO, SELECT, UPDATE, DELETE.");
                System.out.println("Типы: INT, DOUBLE, STRING, DATE (yyyy-MM-dd), BOOLEAN.");
                System.out.println("WHERE: одно условие с =, !=, <>, >, <, >=, <=.");
                continue;
            }
            if (!line.isEmpty()) System.out.println(db.execute(line));
        }
        scanner.close();
    }
}
