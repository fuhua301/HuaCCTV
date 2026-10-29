package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TileShapeTest {
    @Test public void selectionAndArtworkShareTheSameRoundedCornersAtEveryTileSize() {
        for (int width : new int[]{160, 230, 360, 480}) {
            assertEquals(AppAdapter.tileHeightPx(width) * .26f, AppAdapter.cornerRadius(width, 2f), .001f);
        }
    }
}
