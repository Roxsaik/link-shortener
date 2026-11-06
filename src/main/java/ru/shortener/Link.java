package ru.shortener;

import java.time.Instant;
import java.util.UUID;


public class Link {

    public final String code;
    public final String url;
    public final UUID owner;
    public final Instant expiresAt;
    public int limit;
    public int clicks;

    public Link(String code, String url, UUID owner, Instant expiresAt, int limit) {
        this.code = code;
        this.url = url;
        this.owner = owner;
        this.expiresAt = expiresAt;
        this.limit = limit;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isLimitReached() {
        return clicks >= limit;
    }
}
