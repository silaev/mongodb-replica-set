package com.silaev.mongodb.replicaset.util;

import java.util.Optional;

public class StringUtils {
    private StringUtils() {
    }

    public static boolean isBlank(String str) {
        int strLen;
        if (str != null && (strLen = str.length()) != 0) {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(str.charAt(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }

    public static String[] getArrayByDelimiter(final String s) {
        return Optional.ofNullable(s)
            .map(n -> n.split(":"))
            .orElseThrow(
                () -> new IllegalArgumentException("Parameter should not be null")
            );
    }

}
