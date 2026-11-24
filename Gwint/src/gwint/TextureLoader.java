package gwint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

final class TextureLoader {

    private static final String[] RESOURCE_DIRS = {
        "assets/images/cards/",
        "assets/images/ui/"
    };
    private static final String[] FILE_DIRS = {
        "assets/images/cards/",
        "assets/images/ui/",
        "Gwint/assets/images/cards/",
        "Gwint/assets/images/ui/"
    };
    private static final Map<String, Loaded> CACHE = new ConcurrentHashMap<>();
    private static final Loaded MISSING = new Loaded(
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), false);

    private TextureLoader() {
    }

    static BufferedImage get(String name) {
        return load(name).image;
    }

    static boolean isAvailable(String name) {
        return load(name).found;
    }

    private static Loaded load(String name) {
        return CACHE.computeIfAbsent(name, TextureLoader::read);
    }

    private static Loaded read(String name) {
        ClassLoader loader = TextureLoader.class.getClassLoader();
        for (String dir : RESOURCE_DIRS) {
            try (InputStream in = loader.getResourceAsStream(dir + name)) {
                if (in != null) {
                    BufferedImage image = ImageIO.read(in);
                    if (image != null) {
                        return new Loaded(image, true);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        for (String dir : FILE_DIRS) {
            File file = new File(dir + name);
            if (file.isFile()) {
                try {
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) {
                        return new Loaded(image, true);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return MISSING;
    }

    private static final class Loaded {
        private final BufferedImage image;
        private final boolean found;

        private Loaded(BufferedImage image, boolean found) {
            this.image = image;
            this.found = found;
        }
    }
}
