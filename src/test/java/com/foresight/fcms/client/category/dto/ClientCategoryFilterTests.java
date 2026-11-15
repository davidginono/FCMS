package com.foresight.fcms.client.category.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientCategoryFilterTests {

    @Test
    void normalizesRevenueViewAndSearch() {
        ClientCategoryFilter filter = new ClientCategoryFilter();
        filter.setView(" revenue ");
        filter.setSearch("  Mwanza  ");

        assertThat(filter.isRevenueView()).isTrue();
        assertThat(filter.normalizedView()).isEqualTo("revenue");
        assertThat(filter.normalizedSearch()).isEqualTo("Mwanza");
    }

    @Test
    void clampsNegativePagesToZero() {
        ClientCategoryFilter filter = new ClientCategoryFilter();

        filter.setPage(-8);

        assertThat(filter.getPage()).isZero();
    }
}
