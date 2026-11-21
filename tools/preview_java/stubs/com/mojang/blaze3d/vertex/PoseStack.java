package com.mojang.blaze3d.vertex;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;

/** Stub: matriz 2D completa (translate, scale, rotação em Z). O texto usa tx/ty/s; fill e blit usam a matriz inteira. */
public class PoseStack {
    private AffineTransform m = new AffineTransform();
    private final Deque<AffineTransform> stack = new ArrayDeque<>();

    public void pushPose() { stack.push(new AffineTransform(m)); }
    public void popPose() { if (!stack.isEmpty()) m = stack.pop(); }
    public void translate(float x, float y, float z) { m.translate(x, y); }
    public void scale(float x, float y, float z) { m.scale(x, y); }
    public void mulPose(org.joml.Quaternionf q) { m.rotate(Math.toRadians(q.deg)); }

    public float tx() { return (float) m.getTranslateX(); }
    public float ty() { return (float) m.getTranslateY(); }
    public float s() { return (float) m.getScaleX(); }
    public boolean identity() { return m.isIdentity(); }
    public Point2D inverse(double x, double y) {
        try { return m.inverseTransform(new Point2D.Double(x, y), null); } catch (Exception e) { return new Point2D.Double(x, y); }
    }
    public double[] bounds(double x1, double y1, double x2, double y2) {
        java.awt.Shape r = m.createTransformedShape(new java.awt.geom.Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1));
        java.awt.geom.Rectangle2D b = r.getBounds2D();
        return new double[]{b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY()};
    }
}
