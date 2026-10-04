package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

final class HeartStyleResolver {
    private static final Identifier ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    private HeartStyleResolver() {}
    static HeartStyles.Resolved resolve(MinecraftClient client, Identifier[] ignored, Object previous, boolean hardcore) {
        if (previous != null) return null;
        int[] u = {52, 61, 16, 160, 169};
        int v = hardcore ? 45 : 0;
        HeartStyles.Icon[] icons = new HeartStyles.Icon[u.length];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = new HeartStyles.Icon(null, ICONS, u[i] / 256F, v / 256F,
                    (u[i] + 9) / 256F, (v + 9) / 256F, 9, 9, 256, 256);
        }
        return new HeartStyles.Resolved(icons, HeartRenderLayer.text(ICONS), ICONS);
    }
}
