package minidb;

import java.io.*;
import java.nio.file.*;


public class StorageManager {
    private final Path file;

    public StorageManager(String filename) {
        this.file = Paths.get(filename);
    }

    public DatabaseState load() {
        if (!Files.exists(file)) return new DatabaseState();
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            Object value = in.readObject();
            if (!(value instanceof DatabaseState)) throw new IOException("Invalid database file");
            return (DatabaseState) value;
        } catch (Exception e) {
            System.err.println("Не удалось загрузить БД: " + e.getMessage());
            System.err.println("Создаётся пустое состояние. Исходный файл не удалён.");
            return new DatabaseState();
        }
    }

    public void save(DatabaseState state) {
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeObject(state);
                out.flush();
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить БД: " + e.getMessage(), e);
        }
    }
}
