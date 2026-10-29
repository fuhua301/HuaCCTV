package com.gathin.tvhome;

import java.util.*;
import java.util.regex.*;

/** Parse only untagged per-UID histories; device totals and tagged records must never double count. */
final class AppTrafficStats {
    private static final Pattern UID = Pattern.compile("(?:^|\\s)uid=(\\d+)(?:\\s|$)");
    private static final Pattern BYTES = Pattern.compile("\\brb=(\\d+).*?\\btb=(\\d+)");
    static Map<Integer,long[]> parse(String dump) {
        Map<Integer,long[]> totals = new HashMap<>(); boolean section = false; int uid = -1;
        for (String line : dump.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("UID stats:")) { section = true; uid = -1; continue; }
            if (!section) continue;
            if (trimmed.equals("UID tag stats:")) break;
            if (trimmed.startsWith("ident=")) {
                uid = -1; Matcher match = UID.matcher(trimmed);
                if (match.find() && trimmed.contains("tag=0x0") && !trimmed.contains("set=DBG_")) uid = Integer.parseInt(match.group(1));
                continue;
            }
            if (uid < 0 || !trimmed.startsWith("st=")) continue;
            Matcher bytes = BYTES.matcher(trimmed);
            if (bytes.find()) {
                long[] value = totals.computeIfAbsent(uid, k -> new long[2]);
                value[0] += Long.parseLong(bytes.group(1)); value[1] += Long.parseLong(bytes.group(2));
            }
        }
        if (!section) throw new IllegalArgumentException("系统未提供 APP 网络统计");
        return totals;
    }
    static long rate(long before, long after, long elapsed) {
        return elapsed <= 0 || after < before ? 0 : Math.round((after - before) * 1000d / elapsed);
    }
    static String speed(long bytes) {
        if (bytes < 1024) return bytes + " B/s";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB/s", bytes / 1024d);
        return String.format(Locale.ROOT, "%.2f MB/s", bytes / 1048576d);
    }
}
