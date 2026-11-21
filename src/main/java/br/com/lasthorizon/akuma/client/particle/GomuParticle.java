package br.com.lasthorizon.akuma.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Partícula genérica da Gomu Gomu no Mi, configurada por um {@link GomuParticleStyle}. */
public class GomuParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final GomuParticleStyle style;
    private final float startSize;
    private final float spinSpeed;

    protected GomuParticle(ClientLevel level, double x, double y, double z,
                           double vx, double vy, double vz,
                           SpriteSet sprites, GomuParticleStyle style) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.style = style;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = style.minLife() + this.random.nextInt(Math.max(1, style.maxLife() - style.minLife() + 1));
        this.startSize = style.minSize() + this.random.nextFloat() * (style.maxSize() - style.minSize());
        this.quadSize = this.startSize;
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = true;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        this.oRoll = this.roll;
        this.spinSpeed = (this.random.nextFloat() - 0.5F) * 2.0F * style.spin();
        if (style.animated()) {
            this.setSpriteFromAge(sprites);
        } else {
            this.pickSprite(sprites);
        }
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) return;
        this.roll += this.spinSpeed;
        float life = this.age / (float) this.lifetime;
        if (this.style.animated()) this.setSpriteFromAge(this.sprites);
        if (this.style.fadeOut()) this.alpha = Math.max(0.0F, 1.0F - life * life);
        if (this.style.shrink()) this.quadSize = this.startSize * (1.0F - 0.6F * life);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.style.fullBright() ? 15728880 : super.getLightColor(partialTick);
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final GomuParticleStyle style;

        public Provider(SpriteSet sprites, GomuParticleStyle style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xs, double ys, double zs) {
            return new GomuParticle(level, x, y, z, xs, ys, zs, this.sprites, this.style);
        }
    }
}
