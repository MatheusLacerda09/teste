package com.mojang.math;

import org.joml.Quaternionf;

/** Stub do Axis do Minecraft (só o eixo Z). */
public final class Axis {
    public static final Axis ZP = new Axis();
    public Quaternionf rotationDegrees(float d) { return new Quaternionf(d); }
}
