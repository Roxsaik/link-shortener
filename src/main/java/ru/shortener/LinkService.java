package ru.shortener;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class LinkService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final Config config;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Link> links = new HashMap<>();
    private final Map<UUID, List<String>> notifications = new HashMap<>();

    public LinkService(Config config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }


    public synchronized Link create(UUID owner, String url, Integer limit) {
        checkUrl(url);
        int realLimit = (limit == null) ? config.defaultLimit : limit;
        checkLimit(realLimit);

        Instant expiresAt = clock.instant().plusSeconds(config.ttlSeconds);
        Link link = new Link(newCode(), url.trim(), owner, expiresAt, realLimit);
        links.put(link.code, link);
        return link;
    }


    public synchronized String open(String shortLink) {
        String code = getCode(shortLink);
        Link link = links.get(code);
        if (link == null) {
            throw new LinkException("Ссылка " + shortUrl(code) + " не найдена (возможно, её срок жизни истёк)");
        }
        if (link.isExpired(clock.instant())) {
            removeExpiredLink(link);
            throw new LinkException("Срок жизни ссылки " + shortUrl(code) + " истёк, ссылка удалена");
        }
        if (link.isLimitReached()) {
            throw new LinkException("Лимит переходов по ссылке " + shortUrl(code) + " исчерпан");
        }

        link.clicks++;
        if (link.isLimitReached()) {
            notify(link.owner, "Лимит переходов по ссылке " + shortUrl(code) + " исчерпан (" + link.limit + ")");
        }
        return link.url;
    }


    public synchronized List<Link> list(UUID user) {
        List<Link> result = new ArrayList<>();
        for (Link link : links.values()) {
            if (link.owner.equals(user)) {
                result.add(link);
            }
        }
        return result;
    }


    public synchronized void changeLimit(UUID user, String shortLink, int newLimit) {
        Link link = getOwnLink(user, shortLink);
        checkLimit(newLimit);
        if (newLimit <= link.clicks) {
            throw new LinkException("Новый лимит должен быть больше числа уже сделанных переходов (" + link.clicks + ")");
        }
        link.limit = newLimit;
    }


    public synchronized void delete(UUID user, String shortLink) {
        Link link = getOwnLink(user, shortLink);
        links.remove(link.code);
    }

    public synchronized int removeExpired() {
        Instant now = clock.instant();
        List<Link> expired = new ArrayList<>();
        for (Link link : links.values()) {
            if (link.isExpired(now)) {
                expired.add(link);
            }
        }
        for (Link link : expired) {
            removeExpiredLink(link);
        }
        return expired.size();
    }

    public synchronized List<String> getNotifications(UUID user) {
        return new ArrayList<>(notifications.getOrDefault(user, List.of()));
    }

    public String shortUrl(String code) {
        return config.domain + "/" + code;
    }

    public String getCode(String shortLink) {
        if (shortLink == null || shortLink.isBlank()) {
            throw new LinkException("Не указана короткая ссылка");
        }
        String code = shortLink.trim().replaceFirst("^(?i)https?://", "");
        String prefix = config.domain + "/";
        if (code.toLowerCase().startsWith(prefix.toLowerCase())) {
            code = code.substring(prefix.length());
        }
        if (!code.matches("[0-9A-Za-z]+")) {
            throw new LinkException("Некорректная короткая ссылка: " + shortLink);
        }
        return code;
    }

    private Link getOwnLink(UUID user, String shortLink) {
        String code = getCode(shortLink);
        Link link = links.get(code);
        if (link == null) {
            throw new LinkException("Ссылка " + shortUrl(code) + " не найдена");
        }
        if (!link.owner.equals(user)) {
            throw new LinkException("Доступ запрещён: ссылка " + shortUrl(code) + " создана другим пользователем");
        }
        return link;
    }

    private void removeExpiredLink(Link link) {
        links.remove(link.code);
        notify(link.owner, "Срок жизни ссылки " + shortUrl(link.code) + " истёк, ссылка удалена");
    }

    private void notify(UUID user, String message) {
        notifications.computeIfAbsent(user, id -> new ArrayList<>()).add(message);
        System.out.println();
        System.out.println("[УВЕДОМЛЕНИЕ] " + message);
    }

    private String newCode() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < config.codeLength; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!links.containsKey(code)) {
                return code;
            }
        }
    }

    private void checkLimit(int limit) {
        if (limit < 1 || limit > config.maxLimit) {
            throw new LinkException("Лимит переходов должен быть от 1 до " + config.maxLimit);
        }
    }

    private void checkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new LinkException("URL не может быть пустым");
        }
        String trimmed = url.trim();
        if (trimmed.length() > 2048 || trimmed.matches(".*\\s.*")) {
            throw new LinkException("URL слишком длинный или содержит пробелы");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw new LinkException("Нужен URL вида https://example.com/page");
            }
        } catch (URISyntaxException e) {
            throw new LinkException("Некорректный URL: " + e.getReason());
        }
    }
}
