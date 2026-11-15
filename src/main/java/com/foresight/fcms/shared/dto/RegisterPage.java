package com.foresight.fcms.shared.dto;

import java.util.List;

public record RegisterPage<T>(List<T> rows, int page, int size, boolean hasNext) {
    public static <T> RegisterPage<T> of(List<T> fetched, RegisterQuery q) {
        return new RegisterPage<>(
                List.copyOf(fetched.subList(0, Math.min(q.getSize(), fetched.size()))),
                q.getPage(),
                q.getSize(),
                fetched.size() > q.getSize());
    }
}
