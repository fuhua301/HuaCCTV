package com.gathin.tvhome;

import java.util.Locale;
import java.util.Set;

/** A previously offered bundled image must not return after the user deletes it. */
final class BundledWallpaperPolicy {
    static boolean validName(String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }
    static boolean shouldCopy(String name, Set<String> installed, boolean exists) {
        return validName(name) && !exists && !installed.contains(name);
    }
}
