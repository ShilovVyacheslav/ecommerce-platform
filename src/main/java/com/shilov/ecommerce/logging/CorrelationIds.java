package com.shilov.ecommerce.logging;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private CorrelationIds() {}

    public static String acceptOrGenerate(String candidate) {
        return isValid(candidate) ? candidate : generate();
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID_CORRELATION_ID.matcher(candidate).matches();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

}
