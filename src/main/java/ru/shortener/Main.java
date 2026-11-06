package ru.shortener;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Main {

    private static final Path USER_FILE = Path.of("user.id");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static LinkService service;
    private static UUID currentUser;

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Config config;
        try {
            config = Config.load("config.properties");
        } catch (IllegalStateException e) {
            System.out.println("Ошибка конфигурации: " + e.getMessage());
            return;
        }
        service = new LinkService(config, Clock.systemUTC());
        currentUser = loadUser();


        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        cleaner.scheduleWithFixedDelay(service::removeExpired, 1, config.cleanupSeconds, TimeUnit.SECONDS);

        System.out.println("Сервис сокращения ссылок. Введите help, чтобы увидеть команды.");
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.equals("exit")) {
                break;
            }
            handle(line);
        }
        cleaner.shutdownNow();
        System.out.println("До свидания!");
    }

    private static void handle(String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        try {
            switch (parts[0]) {
                case "help" -> printHelp();
                case "create" -> create(parts);
                case "open" -> open(parts);
                case "list" -> list();
                case "limit" -> changeLimit(parts);
                case "delete" -> delete(parts);
                case "notifications" -> notifications();
                case "whoami" -> System.out.println("Ваш UUID: " + (currentUser == null ? "ещё не создан" : currentUser));
                case "login" -> login(parts);
                default -> System.out.println("Неизвестная команда: " + parts[0] + ". Введите help.");
            }
        } catch (LinkException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Ошибка: лимит должен быть целым числом");
        }
    }

    private static void create(String[] parts) {
        if (parts.length < 2 || parts.length > 3) {
            System.out.println("Использование: create <url> [лимит]   например: create https://example.com 5");
            return;
        }
        Integer limit = parts.length == 3 ? Integer.valueOf(parts[2]) : null;
        // UUID создаётся при первом создании ссылки
        boolean firstTime = currentUser == null;
        UUID owner = firstTime ? UUID.randomUUID() : currentUser;

        Link link = service.create(owner, parts[1], limit);

        if (firstTime) {
            currentUser = owner;
            saveUser(owner);
            System.out.println("Ваш UUID (сохраните его): " + owner);
        }
        System.out.println("Короткая ссылка: " + service.shortUrl(link.code));
        System.out.println("Лимит переходов: " + link.limit + ", действует до " + formatTime(link));
    }

    private static void open(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Использование: open <короткая ссылка>");
            return;
        }
        String url = service.open(parts[1]);
        System.out.println("Открываю: " + url);
        try {
            if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
                throw new IOException("браузер недоступен");
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            System.out.println("Не удалось открыть браузер, откройте адрес вручную.");
        }
    }

    private static void list() {
        List<Link> links = service.list(requireUser());
        if (links.isEmpty()) {
            System.out.println("У вас пока нет ссылок.");
        }
        for (Link link : links) {
            System.out.println(service.shortUrl(link.code) + " -> " + link.url
                    + "  [переходов " + link.clicks + " из " + link.limit + ", до " + formatTime(link) + "]");
        }
    }

    private static void changeLimit(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Использование: limit <короткая ссылка> <новый лимит>");
            return;
        }
        service.changeLimit(requireUser(), parts[1], Integer.parseInt(parts[2]));
        System.out.println("Лимит изменён.");
    }

    private static void delete(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Использование: delete <короткая ссылка>");
            return;
        }
        service.delete(requireUser(), parts[1]);
        System.out.println("Ссылка удалена.");
    }

    private static void notifications() {
        List<String> messages = service.getNotifications(requireUser());
        if (messages.isEmpty()) {
            System.out.println("Уведомлений нет.");
        }
        for (String message : messages) {
            System.out.println("- " + message);
        }
    }

    private static void login(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Использование: login <uuid>");
            return;
        }
        try {
            currentUser = UUID.fromString(parts[1]);
            saveUser(currentUser);
            System.out.println("Вы вошли как " + currentUser);
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка: это не похоже на UUID");
        }
    }

    private static void printHelp() {
        System.out.println("Команды:");
        System.out.println("  create <url> [лимит]           создать короткую ссылку");
        System.out.println("  open <ссылка>                  перейти по короткой ссылке");
        System.out.println("  list                           мои ссылки");
        System.out.println("  limit <ссылка> <новый лимит>   изменить лимит переходов");
        System.out.println("  delete <ссылка>                удалить ссылку");
        System.out.println("  notifications                  мои уведомления");
        System.out.println("  whoami                         показать мой UUID");
        System.out.println("  login <uuid>                   войти под другим UUID");
        System.out.println("  help                           эта справка");
        System.out.println("  exit                           выход");
    }

    private static UUID requireUser() {
        if (currentUser == null) {
            throw new LinkException("Сначала создайте ссылку (create) или выполните login <uuid>");
        }
        return currentUser;
    }

    private static String formatTime(Link link) {
        return TIME.format(link.expiresAt.atZone(ZoneId.systemDefault()));
    }

    private static UUID loadUser() {
        try {
            if (Files.exists(USER_FILE)) {
                return UUID.fromString(Files.readString(USER_FILE).trim());
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Не удалось прочитать user.id, будет создан новый UUID");
        }
        return null;
    }

    private static void saveUser(UUID id) {
        try {
            Files.writeString(USER_FILE, id.toString());
        } catch (IOException e) {
            System.out.println("Не удалось сохранить UUID в user.id: " + e.getMessage());
        }
    }
}
