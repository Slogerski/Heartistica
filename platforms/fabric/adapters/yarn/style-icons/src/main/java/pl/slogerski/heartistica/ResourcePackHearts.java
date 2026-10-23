package pl.slogerski.heartistica;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

final class ResourcePackHearts {
    static boolean modified(ResourceManager manager) {
        try {
            var resources = manager.getAllResources(new Identifier("minecraft", "textures/gui/icons.png"));
            if (resources.size() < 2) return false;
            try (var base = resources.get(0).getInputStream(); var selected = resources.get(resources.size() - 1).getInputStream()) {
                return HeartTextureCheck.differs(base, selected, true);
            }
        } catch (java.io.IOException | RuntimeException error) {
            System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not compare resource pack hearts", error);
            return true;
        }
    }
}
