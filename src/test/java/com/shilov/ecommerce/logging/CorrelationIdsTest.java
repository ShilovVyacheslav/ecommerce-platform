package com.shilov.ecommerce.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorrelationIds")
public class CorrelationIdsTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"3f2b9c1e-8f4a-4d7e-9b1c-2a6e5d4c3b2a", "order-42", "partner:ABC.123_x", "a"})
    @DisplayName("Short id of safe characters -> accepted as is")
    void acceptOrGenerate_ValidId_KeepsIt(String candidate) {
        assertThat(CorrelationIds.acceptOrGenerate(candidate)).isEqualTo(candidate);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "id\r\nlevel=ERROR fake log line", "id with spaces", "<script>", "{\"json\":1}"})
    @DisplayName("Missing, blank or unsafe id (log injection) -> replaced with a generated UUID")
    void acceptOrGenerate_InvalidId_GeneratesNewOne(String candidate) {
        String correlationId = CorrelationIds.acceptOrGenerate(candidate);

        assertThat(correlationId).isNotEqualTo(candidate);
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("Id longer than 64 characters -> replaced, a client cannot inflate every log line")
    void acceptOrGenerate_TooLongId_GeneratesNewOne() {
        assertThat(CorrelationIds.acceptOrGenerate("a".repeat(64))).isEqualTo("a".repeat(64));
        assertThat(CorrelationIds.acceptOrGenerate("a".repeat(65))).isNotEqualTo("a".repeat(65));
    }

}
