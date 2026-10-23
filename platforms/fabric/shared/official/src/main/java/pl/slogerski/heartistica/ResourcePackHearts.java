package pl.slogerski.heartistica;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

final class ResourcePackHearts {
    static boolean modified(ResourceManager manager) {
        try {
            for (String name : new String[]{"full", "half", "container", "absorbing_full", "absorbing_half",
                    "hardcore_full", "hardcore_half", "container_hardcore", "absorbing_hardcore_full", "absorbing_hardcore_half"}) {
                var resources = manager.getResourceStack(Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/" + name + ".png"));
                if (resources.size() < 2) continue;
                try (var base = resources.get(0).open(); var selected = resources.get(resources.size() - 1).open()) {
                    if (HeartTextureCheck.differs(base, selected, false)) return true;
                }
            }
            return false;
        } catch (java.io.IOException | RuntimeException error) {
            System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not compare resource pack hearts", error);
            return true;
        }
    }
}
