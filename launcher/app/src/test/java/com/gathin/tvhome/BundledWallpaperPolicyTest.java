package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collections;

public class BundledWallpaperPolicyTest {
    @Test public void newInstallGetsDefaults() { assertTrue(BundledWallpaperPolicy.shouldCopy("lake.jpg",Collections.emptySet(),false)); }
    @Test public void existingImagesAreNotOverwritten() { assertFalse(BundledWallpaperPolicy.shouldCopy("lake.jpg",Collections.emptySet(),true)); }
    @Test public void deletedDefaultsStayDeleted() { assertFalse(BundledWallpaperPolicy.shouldCopy("lake.jpg",Collections.singleton("lake.jpg"),false)); }
    @Test public void laterNewDefaultCanBeSeeded() { assertTrue(BundledWallpaperPolicy.shouldCopy("mountain.jpg",Collections.singleton("lake.jpg"),false)); }
    @Test public void onlySafeImageNames() { assertFalse(BundledWallpaperPolicy.shouldCopy("../lake.jpg",Collections.emptySet(),false));assertFalse(BundledWallpaperPolicy.validName("notes.txt")); }
}
