package minidb;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;


public class DatabaseFrame extends JFrame {
    private final SqlEngine database;
    private final JList<String> tableList = new JList<String>();
    private final JTable dataGrid = new JTable();
    private final JTextArea sqlEditor = new JTextArea(7, 70);
    private final JTextArea output = new JTextArea(7, 70);
    private final JLabel status = new JLabel("Готово");
    private final JLabel tableInfo = new JLabel("Выберите таблицу");

    public DatabaseFrame(SqlEngine database) {
        super("MiniDB — система управления базами данных");
        this.database = database;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 700));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkspace(), BorderLayout.CENTER);
        add(buildStatus(), BorderLayout.SOUTH);
        refreshTables();
        if (!tableList.isSelectionEmpty()) loadSelectedTable();
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("MiniDB");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel("  СУБД • Java  • SQL");
        subtitle.setForeground(new Color(100, 110, 125));
        JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        labels.add(title);
        labels.add(subtitle);
        panel.add(labels, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        addButton(actions, "Обновить", e -> refreshAll());
        addButton(actions, "Новая таблица", e -> createTableDialog());
        addButton(actions, "Создать индекс", e -> createIndexDialog());
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildWorkspace() {
        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.setPreferredSize(new Dimension(210, 400));
        JLabel tablesTitle = new JLabel("  ТАБЛИЦЫ");
        tablesTitle.setFont(tablesTitle.getFont().deriveFont(Font.BOLD, 12f));
        left.add(tablesTitle, BorderLayout.NORTH);
        tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelectedTable(); });
        left.add(new JScrollPane(tableList), BorderLayout.CENTER);

        JPanel tableActions = new JPanel(new GridLayout(0, 1, 4, 4));
        addButton(tableActions, "Добавить запись", e -> insertDialog());
        addButton(tableActions, "Изменить запись", e -> editSelected());
        addButton(tableActions, "Удалить запись", e -> deleteSelected());
        addButton(tableActions, "Удалить таблицу", e -> dropSelectedTable());
        left.add(tableActions, BorderLayout.SOUTH);

        JPanel browser = new JPanel(new BorderLayout(5, 5));
        tableInfo.setBorder(new EmptyBorder(3, 4, 3, 4));
        tableInfo.setFont(tableInfo.getFont().deriveFont(Font.BOLD));
        browser.add(tableInfo, BorderLayout.NORTH);
        dataGrid.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        dataGrid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataGrid.setRowHeight(25);
        browser.add(new JScrollPane(dataGrid), BorderLayout.CENTER);

        JPanel sqlPanel = new JPanel(new BorderLayout(5, 5));
        JPanel sqlToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sqlToolbar.add(new JLabel("SQL-редактор"));
        addButton(sqlToolbar, "Выполнить  ▶", e -> executeSql());
        addButton(sqlToolbar, "Очистить", e -> sqlEditor.setText(""));
        addButton(sqlToolbar, "Примеры", e -> showExamples());
        sqlPanel.add(sqlToolbar, BorderLayout.NORTH);
        sqlEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sqlEditor.setText("SELECT * FROM cars;");
        sqlPanel.add(new JScrollPane(sqlEditor), BorderLayout.CENTER);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setRows(6);
        JPanel resultPanel = new JPanel(new BorderLayout(4, 4));
        resultPanel.add(new JLabel("Результат выполнения"), BorderLayout.NORTH);
        resultPanel.add(new JScrollPane(output), BorderLayout.CENTER);
        JSplitPane sqlAndResult = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sqlPanel, resultPanel);
        sqlAndResult.setResizeWeight(0.55);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, browser, sqlAndResult);
        right.setResizeWeight(0.55);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        main.setDividerLocation(210);
        return main;
    }

    private JComponent buildStatus() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(215, 220, 228)));
        status.setBorder(new EmptyBorder(5, 3, 0, 3));
        p.add(status, BorderLayout.WEST);
        JLabel files = new JLabel("Хранилище: data/minidb.db   |   Индексы: data/indexes.db");
        files.setForeground(new Color(110, 115, 125));
        p.add(files, BorderLayout.EAST);
        return p;
    }

    private void addButton(JPanel panel, String text, ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        panel.add(button);
    }

    private void refreshAll() {
        String selected = tableList.getSelectedValue();
        refreshTables();
        if (selected != null) tableList.setSelectedValue(selected, true);
        if (tableList.getSelectedValue() != null) loadSelectedTable();
    }

    private void refreshTables() {
        String selected = tableList.getSelectedValue();
        DefaultListModel<String> model = new DefaultListModel<String>();
        for (String name : database.getTableNames()) model.addElement(name);
        tableList.setModel(model);
        if (selected != null) tableList.setSelectedValue(selected, true);
        if (tableList.getSelectedIndex() < 0 && model.size() > 0) tableList.setSelectedIndex(0);
    }

    private void loadSelectedTable() {
        String name = tableList.getSelectedValue();
        if (name == null) return;
        List<String> columns = database.getColumnNames(name);
        DefaultTableModel model = new DefaultTableModel(columns.toArray(), 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Object[] row : database.getRows(name)) model.addRow(row);
        dataGrid.setModel(model);
        tableInfo.setText(database.describeTable(name).replace('\n', ' ').trim()
                + "     |     Записей: " + database.getRowCount(name));
        status.setText("Открыта таблица: " + name);
    }

    private void executeSql() {
        String sql = sqlEditor.getText().trim();
        if (sql.isEmpty()) return;
        String result = database.execute(sql);
        output.setText(result);
        output.setCaretPosition(0);
        boolean success = !result.startsWith("Ошибка:");
        status.setText(success ? "Запрос выполнен" : "Запрос завершился ошибкой");
        if (!success) JOptionPane.showMessageDialog(this, result, "Ошибка SQL", JOptionPane.ERROR_MESSAGE);
        refreshAll();
    }

    private void insertDialog() {
        String table = tableList.getSelectedValue();
        if (table == null) return;
        List<String> columns = database.getColumnNames(table);
        JPanel form = new JPanel(new GridLayout(columns.size(), 2, 8, 7));
        Map<String, JTextField> fields = new LinkedHashMap<String, JTextField>();
        for (String column : columns) {
            form.add(new JLabel(column));
            JTextField field = new JTextField();
            fields.put(column, field);
            form.add(field);
        }
        int result = JOptionPane.showConfirmDialog(this, form, "Новая запись: " + table,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        sql.append(join(columns)).append(") VALUES (");
        List<String> values = new ArrayList<String>();
        for (String column : columns) values.add(toSqlLiteral(fields.get(column).getText()));
        sql.append(join(values)).append(");");
        sqlEditor.setText(sql.toString());
        executeSql();
    }

    private void editSelected() {
        String table = tableList.getSelectedValue();
        int rowIndex = dataGrid.getSelectedRow();
        if (table == null || rowIndex < 0) {
            JOptionPane.showMessageDialog(this, "Сначала выберите запись.");
            return;
        }
        List<String> columns = database.getColumnNames(table);
        JPanel form = new JPanel(new GridLayout(columns.size(), 2, 8, 7));
        Map<String, JTextField> fields = new LinkedHashMap<String, JTextField>();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            form.add(new JLabel(column));
            JTextField field = new JTextField(String.valueOf(dataGrid.getValueAt(rowIndex, i)));
            fields.put(column, field);
            form.add(field);
        }
        int result = JOptionPane.showConfirmDialog(this, form, "Изменить запись: " + table,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        String pk = database.getPrimaryKey(table);
        if (pk == null) {
            JOptionPane.showMessageDialog(this, "Для редактирования требуется первичный ключ.");
            return;
        }
        int pkIndex = columns.indexOf(pk);
        Object oldKey = dataGrid.getValueAt(rowIndex, pkIndex);
        List<String> assignments = new ArrayList<String>();
        for (String column : columns)
            if (!column.equals(pk)) assignments.add(column + " = " + toSqlLiteral(fields.get(column).getText()));
        sqlEditor.setText("UPDATE " + table + " SET " + join(assignments) + " WHERE " + pk + " = " + oldKey + ";");
        executeSql();
    }

    private void deleteSelected() {
        String table = tableList.getSelectedValue();
        int rowIndex = dataGrid.getSelectedRow();
        if (table == null || rowIndex < 0) {
            JOptionPane.showMessageDialog(this, "Выберите запись для удаления.");
            return;
        }
        String pk = database.getPrimaryKey(table);
        if (pk == null) {
            JOptionPane.showMessageDialog(this, "Удаление из GUI требует первичного ключа.");
            return;
        }
        int pkIndex = database.getColumnNames(table).indexOf(pk);
        Object key = dataGrid.getValueAt(rowIndex, pkIndex);
        int confirm = JOptionPane.showConfirmDialog(this, "Удалить запись с " + pk + " = " + key + "?",
                "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            sqlEditor.setText("DELETE FROM " + table + " WHERE " + pk + " = " + key + ";");
            executeSql();
        }
    }

    private void createTableDialog() {
        JTextArea help = new JTextArea(
            "Введите CREATE TABLE целиком, например:\\n" +
            "CREATE TABLE service (id INT PRIMARY KEY, car_id INT REFERENCES cars(id), " +
            "cost DOUBLE, service_date DATE, completed BOOLEAN);", 5, 55);
        help.setLineWrap(true);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(help), "Создание таблицы",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            sqlEditor.setText(help.getText());
            executeSql();
        }
    }

    private void createIndexDialog() {
        String table = tableList.getSelectedValue();
        if (table == null) return;
        List<String> columns = database.getColumnNames(table);
        String column = (String)JOptionPane.showInputDialog(this, "Столбец для индексирования:",
                "Создание индекса", JOptionPane.PLAIN_MESSAGE, null, columns.toArray(), columns.get(0));
        if (column == null) return;
        String indexName = "idx_" + table + "_" + column;
        sqlEditor.setText("CREATE INDEX " + indexName + " ON " + table + " (" + column + ");");
        executeSql();
    }

    private void dropSelectedTable() {
        String table = tableList.getSelectedValue();
        if (table == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить таблицу " + table + "? Операция необратима.",
                "Удаление таблицы", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            sqlEditor.setText("DROP TABLE " + table + ";");
            executeSql();
        }
    }

    private void showExamples() {
        String[] examples = {
            "SELECT * FROM cars;",
            "SELECT brand, price FROM cars WHERE price > 20000;",
            "INSERT INTO owners VALUES (3, 'Пётр', '1990-01-15', TRUE);",
            "UPDATE cars SET price = 30000 WHERE id = 2;",
            "DELETE FROM cars WHERE id = 2;",
            "CREATE INDEX idx_cars_brand ON cars (brand);"
        };
        String selected = (String)JOptionPane.showInputDialog(this, "Выберите пример:",
                "Примеры SQL", JOptionPane.PLAIN_MESSAGE, null, examples, examples[0]);
        if (selected != null) sqlEditor.setText(selected);
    }

    private String toSqlLiteral(String value) {
        String v = value.trim();
        if (v.isEmpty()) return "NULL";
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false") ||
                v.equalsIgnoreCase("null") || v.matches("-?\\d+(\\.\\d+)?")) return v;
        return "'" + v.replace("'", "''") + "'";
    }

    private String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(values.get(i));
        }
        return b.toString();
    }
}
