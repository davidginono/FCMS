package com.foresight.fcms.client.category.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientCategoryReportTypeTests {

    @Test
    void unionSupportsLegacyHostingAndBulkSmsReports() {
        assertThat(ClientCategoryReportType.selectableTypes(ClientCategory.UNIONS))
                .contains(ClientCategoryReportType.HOSTING_FEE, ClientCategoryReportType.BULK_SMS);
    }

    @Test
    void microfinanceAndOthersRejectUnsupportedReportTypes() {
        assertThat(ClientCategoryReportType.from("hosting_fee", ClientCategory.MICROFINANCE))
                .isEqualTo(ClientCategoryReportType.FULL);
        assertThat(ClientCategoryReportType.from("bulk_sms", ClientCategory.OTHERS))
                .isEqualTo(ClientCategoryReportType.FULL);
    }

    @Test
    void categorySlugsMapToRequestedSidebarRoutes() {
        assertThat(ClientCategory.fromSlug("unions")).isEqualTo(ClientCategory.UNIONS);
        assertThat(ClientCategory.fromSlug("microfinance")).isEqualTo(ClientCategory.MICROFINANCE);
        assertThat(ClientCategory.fromSlug("others")).isEqualTo(ClientCategory.OTHERS);
    }
}
