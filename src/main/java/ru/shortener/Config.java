package ru.shortener;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {

    public String domain = "clck.ru";
    public int codeLength = 6;
    public long ttlSeconds = 86400;
    public int defaultLimit = 10;
    public int maxLimit = 1000;
    public long cleanupSeconds = 5;


    public static Config load(String fileName) {
        Config config = new Config();
        Path path = Path.of(fileName);
        if (!Files.exists(path)) {
            return config;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + fileName + ": " + e.getMessage());
        }
        config.domain = props.getProperty("short.domain", config.domain).trim();
        config.codeLength = readInt(props, "short.code-length", config.codeLength);
        config.ttlSeconds = readInt(props, "link.ttl-seconds", (int) config.ttlSeconds);
        config.defaultLimit = readInt(props, "link.default-limit", config.defaultLimit);
        config.maxLimit = readInt(props, "link.max-limit", config.maxLimit);
        config.cleanupSeconds = readInt(props, "cleanup.interval-seconds", (int) config.cleanupSeconds);
        config.check();
        return config;
    }

    private static int readInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("В config.properties параметр " + key + " должен быть числом");
        }
    }

    public void check() {
        if (codeLength < 4 || codeLength > 32) {
            throw new IllegalStateException("short.code-length должен быть от 4 до 32");
        }
        if (ttlSeconds < 1 || cleanupSeconds < 1) {
            throw new IllegalStateException("Время жизни и период проверки должны быть больше 0");
        }
        if (maxLimit < 1 || defaultLimit < 1 || defaultLimit > maxLimit) {
            throw new IllegalStateException("Лимиты заданы неверно: нужно 1 <= default-limit <= max-limit");
        }
    }
}
