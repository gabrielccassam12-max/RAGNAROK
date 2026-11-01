package com.ragnarok.client;

import com.ragnarok.particle.ParticleProfile;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

/** One generic, profile-driven particle: bright, translucent, animated. All the mod's particles use it. */
public class GlowParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final ParticleProfile pf;

    public GlowParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                        SpriteProvider sprites, ParticleProfile pf) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.pf = pf;
        float sizeMul = 1f;
        if (pf.sizeFromVx()) {
            sizeMul = (float) Math.max(0.05, vx);
            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
        } else {
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
        }
        this.maxAge = pf.life() + this.random.nextInt(Math.max(1, pf.life() / 4));
        this.scale = pf.size() * sizeMul * (0.85f + this.random.nextFloat() * 0.3f);
        int rgb = pf.rgb();
        this.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        this.alpha = 1f;
        this.gravityStrength = pf.gravity();
        this.collidesWithWorld = false;
        this.angle = this.random.nextFloat() * (float) (Math.PI * 2);
        this.prevAngle = this.angle;
        this.setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }
        this.velocityY += pf.rise() - 0.04 * this.gravityStrength;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= pf.drag();
        this.velocityY *= pf.drag();
        this.velocityZ *= pf.drag();
        this.prevAngle = this.angle;
        this.angle += pf.spin();
        this.scale *= pf.grow();
        float t = (float) this.age / (float) this.maxAge;
        this.alpha = t < 0.65f ? 1f : Math.max(0f, 1f - (t - 0.65f) / 0.35f);
        this.setSpriteForAge(sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        return pf.glow() ? 0xF000F0 : super.getBrightness(tint);
    }
}
