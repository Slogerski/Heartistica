package pl.slogerski.heartistica;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class OgulniegaLanguage implements LanguageSettings.Source {
    private final boolean present;
    private final ClassLoader loader;
    private Method getter;

    OgulniegaLanguage(boolean present) { this(present, OgulniegaLanguage.class.getClassLoader()); }
    OgulniegaLanguage(boolean present, ClassLoader loader) { this.present = present; this.loader = loader; }

    @Override public boolean isPresent() { return present; }

    @Override public String getLanguage() {
        if (!present) return null;
        try {
            if (getter == null) {
                Class<?> api = Class.forName("com.ogulniega.OgulniegaModApi", false, loader);
                Method method = api.getMethod("getLanguage");
                if (method.getReturnType() != String.class || !Modifier.isStatic(method.getModifiers()))
                    throw new IllegalStateException("Unsupported Ogulniega language API");
                getter = method;
            }
            return (String) getter.invoke(null);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof NullPointerException) return null;
            throw new IllegalStateException("Ogulniega language API failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Ogulniega language API unavailable", error);
        }
    }
}
