package gwint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextureLoaderTest {

    @Test
    public void loadsExistingCardArt() {
        String[] files = {
            "art_placeholder.png",
            "frame_basic.png",
            "circle_hero.png",
            "circle_normal.png",
            "icon_melee.png",
            "icon_range.png",
            "icon_siege.png",
            "icon_special.png"
        };
        for (String file : files) {
            assertTrue("Missing card texture " + file, TextureLoader.isAvailable(file));
        }
    }

    @Test
    public void loadsGeneratedUiTextures() {
        String[] files = {
            "board_background.png",
            "row_lane.png",
            "card_back.png",
            "row_badge.png",
            "selection_highlight.png",
            "horn_slot.png",
            "circle_special.png"
        };
        for (String file : files) {
            assertTrue("Missing UI texture " + file, TextureLoader.isAvailable(file));
            assertTrue("Empty UI texture " + file, TextureLoader.get(file).getWidth() > 0);
        }
    }
}
