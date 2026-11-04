package com.gathin.tvhome;

/** Stable, local-only identifiers; preference values never become arbitrary asset paths. */
final class VideoWallpaperCatalog {
    static final String ALL = "all", IMAGES = "images", NONE = "none";
    static final String DEFAULT = "aquarium";
    static final String[] IDS = {"ocean", "aquarium", "waterfall", "peach", "beach"};
    static final String[] TITLES = {"珊瑚海洋", "水草鱼缸", "伊瓜苏瀑布", "桃花纷飞", "热带海岸"};
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
    static String normalize(String choice) {
        return ALL.equals(choice) || IMAGES.equals(choice) || NONE.equals(choice) || index(choice) >= 0 ? choice : DEFAULT;
    }
    static boolean isVideo(String choice) { return ALL.equals(choice) || index(choice) >= 0; }
    static int next(int index) { return (Math.max(-1, index) + 1) % IDS.length; }
    static boolean rotate(String choice, long changed, long now, long interval) {
        return ALL.equals(choice) && changed > 0 && WallpaperRotation.due(changed, now, interval);
    }
    private VideoWallpaperCatalog() { }
}
