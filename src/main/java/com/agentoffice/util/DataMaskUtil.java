package com.agentoffice.util;

import java.util.regex.Pattern;

public class DataMaskUtil {
    private static final Pattern PHONE = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("(\\w{2})[^@]*(@.*)");
    private static final Pattern ID_CARD = Pattern.compile("(\\d{4})\\d{10}(\\d{4})");
    private static final Pattern IP = Pattern.compile("(\\d+\\.\\d+)\\.\\d+\\.(\\d+)");

    public static String maskPhone(String text) {
        return PHONE.matcher(text).replaceAll("$1****$2");
    }

    public static String maskEmail(String text) {
        return EMAIL.matcher(text).replaceAll("$1***$2");
    }

    public static String maskIdCard(String text) {
        return ID_CARD.matcher(text).replaceAll("$1**********$2");
    }

    public static String maskIp(String text) {
        return IP.matcher(text).replaceAll("$1.*.$2");
    }

    public static String mask(String text) {
        if (text == null) return null;
        text = maskPhone(text);
        text = maskEmail(text);
        text = maskIdCard(text);
        text = maskIp(text);
        return text;
    }
}
