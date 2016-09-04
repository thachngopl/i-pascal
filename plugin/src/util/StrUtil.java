package com.siberika.idea.pascal.util;

import com.intellij.codeInspection.SmartHashMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Author: George Bakhtadze
 * Date: 09/04/2015
 */
public class StrUtil {
    public static final Pattern PATTERN_FIELD = Pattern.compile("[fF][A-Z]\\w*");

    public static boolean hasLowerCaseChar(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isLowerCase(c)) {
                return true;
            }
        }
        return false;
    }

    public static String getFieldName(String name) {
        int ind = Math.min(getPos(name, '('), getPos(name, ':'));
        ind = name.substring(0, ind).lastIndexOf('.');
        if (ind > 0) {
            return name.substring(ind + 1);
        } else {
            return name;
        }
    }

    private static int getPos(String name, char c) {
        int ind = name.indexOf(c);
        return ind >= 0 ? ind : name.length();
    }

    public static <K, V> Map<K, V> getParams(List<Pair<K, V>> entries) {
        Map<K, V> res = entries.size() <= 1 ? new SmartHashMap<K, V>() : new HashMap < K, V>(entries.size());
        for (Pair<K, V> entry : entries) {
            res.put(entry.first, entry.second);
        }
        return res;
    }

    public static String limit(String xml, int max) {
        if ((xml != null) && (xml.length() > max)) {
            return String.format("%s <more %d symbols>", xml.substring(0, max), xml.length() - max);
        } else {
            return xml;
        }
    }

    public static TextRange getIncludeNameRange(String text) {
        if ((null == text) || !text.startsWith("{$") || !text.endsWith("}")) {
            return null;
        }
        int end = text.length() - 1;
        String str = text.substring(2, end).toUpperCase();
        int start = end;

        if (str.startsWith("I ")) {
            start = 4;
        }
        if (str.startsWith("INCLUDE ")) {
            start = 10;
        }
        while ((start < end) && (text.charAt(start) <= ' ')) {
            start++;
        }
        while ((start < end) && (text.charAt(end - 1) <= ' ')) {
            end--;
        }
        if (text.charAt(start) == '\'') {
            start++;
            end--;
        }
        return start < end ? TextRange.create(start, end) : null;
    }

    public static String getIncludeName(String text) {
        TextRange r = getIncludeNameRange(text);
        return r != null ? r.substring(text) : null;
    }

    public static boolean isVersionLessOrEqual(String version1, String version2) {
        return version1.compareTo(version2) <= 0;
    }
}
