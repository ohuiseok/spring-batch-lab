package com.example.batch.domain;

public record ProductCsvRow(
        long id,
        String name,
        long price
) {
}
