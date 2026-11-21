package net.minecraft.resources;
/** Stub só para o preview fora do Minecraft. */
public final class ResourceLocation {
    public final String ns, path;
    public ResourceLocation(String ns, String path) { this.ns = ns; this.path = path; }
    @Override public String toString() { return ns + ":" + path; }
}
