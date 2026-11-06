package ru.shortener;

public class LinkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LinkException(String message) {
        super(message);
    }
}
