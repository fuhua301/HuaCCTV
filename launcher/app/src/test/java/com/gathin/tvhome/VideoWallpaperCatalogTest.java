package com.gathin.tvhome;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class VideoWallpaperCatalogTest {
    @Test public void fiveDistinctThemesIncludeUnderwater() {
        assertEquals(5, VideoWallpaperCatalog.IDS.length);
        assertEquals(5, VideoWallpaperCatalog.TITLES.length);
        HashSet<String> names = new HashSet<>();
        for (String id : VideoWallpaperCatalog.IDS) { assertTrue(names.add(id)); assertTrue(VideoWallpaperCatalog.index(id) >= 0); }
        assertEquals("ocean", VideoWallpaperCatalog.IDS[0]);
        assertEquals(-1, VideoWallpaperCatalog.index("earth"));
        assertEquals(-1, VideoWallpaperCatalog.index("moon"));
        assertEquals(-1, VideoWallpaperCatalog.index("sun"));
        assertEquals(-1, VideoWallpaperCatalog.index("nebula"));
    }
    @Test public void unsafeAndUnknownPreferencesUseTheDefaultCollection() {
        for (String invalid : new String[]{null, "", "../secret", "https://host/video.mp4", "missing"})
            assertEquals("aquarium", VideoWallpaperCatalog.normalize(invalid));
        for (String id : VideoWallpaperCatalog.IDS) assertEquals(id, VideoWallpaperCatalog.normalize(id));
    }
    @Test public void aFreshInstallDefaultsToTheFixedAquarium() {
        assertEquals("aquarium", VideoWallpaperCatalog.DEFAULT);
        assertEquals("aquarium", VideoWallpaperCatalog.normalize(null));
        assertTrue(VideoWallpaperCatalog.isVideo(VideoWallpaperCatalog.DEFAULT));
        assertFalse(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.DEFAULT, 1000, 121000, 60000));
        assertEquals(VideoWallpaperCatalog.ALL, VideoWallpaperCatalog.normalize(VideoWallpaperCatalog.ALL));
    }
    @Test public void selectionModesDoNotEnableVideoForPicturesOrDisabledWallpaper() {
        assertFalse(VideoWallpaperCatalog.isVideo(VideoWallpaperCatalog.NONE));
        assertFalse(VideoWallpaperCatalog.isVideo(VideoWallpaperCatalog.IMAGES));
        assertTrue(VideoWallpaperCatalog.isVideo(VideoWallpaperCatalog.ALL));
        assertTrue(VideoWallpaperCatalog.isVideo("ocean"));
    }
    @Test public void fullRotationVisitsEverySceneBeforeReturningToTheFirst() {
        int current = 0;
        HashSet<Integer> visited = new HashSet<>();
        for (int i = 0; i < 5; i++) { assertTrue(visited.add(current)); current = VideoWallpaperCatalog.next(current); }
        assertEquals(0, current);
    }
    @Test public void fixedVideoLoopsInsteadOfSwitchingAtTheRotationInterval() {
        assertFalse(VideoWallpaperCatalog.rotate("ocean", 1000, 121000, 60000));
        assertFalse(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.IMAGES, 1000, 121000, 60000));
        assertFalse(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.ALL, 1000, 60999, 60000));
        assertTrue(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.ALL, 1000, 61000, 60000));
        assertFalse(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.ALL, 0, 121000, 60000));
        assertFalse(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.ALL, 1000, 121000, 0));
        assertTrue(VideoWallpaperCatalog.rotate(VideoWallpaperCatalog.ALL, 121000, 1000, 60000));
    }
}
