package com.brotherc.aquant.common.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    void shouldParseDateOnlyValue() {
        assertThat(DateUtils.parseLocalDate("2026-07-01"))
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void shouldParseAkShareDateTimeValue() {
        assertThat(DateUtils.parseLocalDate("2026-07-01T00:00:00.000"))
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
