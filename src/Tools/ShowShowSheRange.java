package Tools;

import Tools.copy.PublicStaticVoids;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Point2;
import arc.scene.ui.layout.Scl;
import arc.struct.IntFloatMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.World;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.SuppressionFieldAbility;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.ui.Fonts;
import mindustry.world.Tile;

import static arc.Core.camera;
import static mindustry.Vars.*;

public class ShowShowSheRange {
    private static final IntFloatMap damages = new IntFloatMap();

    private final float averageLength = 0.97981f * 15;
    private final float[] radiusMulti = new float[16];
    public FrameBuffer buffer = new FrameBuffer();
    public Seq<PotentialBullet> potentialBullets = new Seq<>();
    public ObjectMap<BulletType, Float> bulletTypeRange = new ObjectMap<>();
    public Rand rand = new Rand();
    public Font font = Fonts.outline;


    public ShowShowSheRange() {
        for (int i = 0; i < radiusMulti.length; i++) {
            radiusMulti[i] = getRadiusMulti(i + 1);
        }

        Events.run(EventType.Trigger.draw, () -> {
            buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());

            camera.bounds(Tmp.r1);
            resetPotentialBullet();

            drawRange(() -> potentialBullets.each(potentialBullet -> {
                if (potentialBullet.type.splashDamageRadius > 0) {
                    showSplashDamage(potentialBullet.x, potentialBullet.y, potentialBullet.type.splashDamageRadius, potentialBullet.type.splashDamage, potentialBullet.team, potentialBullet.type.buildingDamageMultiplier);
                }
            }), Core.settings.getInt("溅射范围透明度") / 100f);


            drawRange(() -> potentialBullets.each(potentialBullet -> {
                if (potentialBullet.type.fragBullet != null) {
                    showFragBullet(potentialBullet.x, potentialBullet.y, potentialBullet.rotation, potentialBullet.type);
                }
            }), Core.settings.getInt("破片范围透明度") / 100f);


            drawRange(() -> potentialBullets.each(potentialBullet -> {
                if (potentialBullet.type.lightning > 0) {
                    rand.setSeed(potentialBullet.id);
                    showLightning(potentialBullet.x, potentialBullet.y, potentialBullet.type.lightningLength / 2, potentialBullet.type.lightningLengthRand / 2, potentialBullet.type.lightning);
                }
            }), Core.settings.getInt("闪电路径透明度") / 100f);


            drawRange(() -> {
                potentialBullets.each(potentialBullet -> {
                    if (potentialBullet.type.suppressionRange > 0) {
                        showSuppression(potentialBullet.x, potentialBullet.y, potentialBullet.type.suppressionRange);
                    }
                });
                Groups.unit.each(unit -> {
                    SuppressionFieldAbility ability = (SuppressionFieldAbility) unit.type.abilities.find(a -> a instanceof SuppressionFieldAbility);
                    if (ability != null) {
                        showSuppression(unit.x, unit.y, ability.range);
                    }
                });
            }, Core.settings.getInt("压制范围透明度") / 100f);


            drawRange(() -> Groups.unit.each(unit -> {
                RepairFieldAbility repairField = (RepairFieldAbility) unit.type.abilities.find(a -> a instanceof RepairFieldAbility);
                if (repairField != null) {
                    showRepairField(unit.x, unit.y, repairField.range);
                }
                RepairBeamWeapon repairBeam = (RepairBeamWeapon) unit.type.weapons.find(a -> a instanceof RepairBeamWeapon);
                if (repairBeam != null) {
                    showRepairBeam(unit.x, unit.y, repairBeam.range());
                }
            }), Core.settings.getInt("治疗范围透明度") / 100f);


            drawRange(() -> Groups.unit.each(unit -> {
                if (unit.spawnedByCore) return;
                UnitType type = unit.type;

                boolean show = Core.settings.getBool("显示伤害数值");
                boolean detail = Core.settings.getBool("详细溅射范围");

                if (unit.hasItem()) {
                    Item item = unit.item();
                    int amount = unit.stack().amount;

                    if (item.explosiveness > 0) {
                        float explosiveness = 2 + item.explosiveness * amount * 1.53f;
                        int waves = Mathf.clamp((int) (explosiveness / 11), 1, 25);
                        float radius = Mathf.clamp((unit.bounds() + type.legLength / 1.7f) / 2f + explosiveness, 0, 50f);
                        float damagePerWave = explosiveness / 2f;
                        float radiusMulti = radius / 8 * 0.4f;

                        if (detail) {
                            showLineSplashDamage(unit.x, unit.y, radius);

//                            if(show) {
//                                totalDamages.clear();
//
//                                for (int i = 0; i < waves; i++) {
//                                    countSplashDamage(World.toTile(unit.x), World.toTile(unit.y), ((i + 1f) / waves) * radiusMulti, damagePerWave, unit.team);
//                                    damages.forEach(e -> totalDamages.put(e.key, e.value + totalDamages.get(e.key, 0)));
//                                }
//
//                                drawDamages(totalDamages, damagePerWave);
//                            }
                        } else {
                            showCompleteSplashDamage(unit.x, unit.y, radius);

                            if (show) {
                                String value;

                                if (unit.buildOn() != null && unit.buildOn().team != unit.team) {
                                    float total = 0;
                                    for (int i = 0; i < waves; i++) {
                                        total += Math.min(((i + 1f) / waves) * radiusMulti, unit.buildOn().block.size) * damagePerWave;
                                    }

                                    value = twoDig(total) + "";
                                } else {
                                    float wavesMulti = (waves + 1) / 2f * radiusMulti;
                                    value = twoDig(damagePerWave) + (waves == 1 ? "" : ("*(" + waves + "~" + wavesMulti + ")" + "=" + twoDig(damagePerWave * wavesMulti)));
                                }

                                showValue(value, Items.pyratite.color, unit.x, unit.y + radius + 2);
                            }
                        }
                    }

                    if (item.charge > 0) {
                        float power = item.charge * Mathf.pow(amount, 1.11f) * 160f;
                        int length = (5 + Mathf.clamp((int) (Mathf.pow(power, 0.98f) / 500), 1, 18)) / 2;
                        int lightingAmount = (int) Mathf.clamp(power / 700, 0, 8);

                        rand.setSeed(unit.id);
                        showLightning(unit.x, unit.y, length, 1, lightingAmount);

                        if (show) {
                            float damage = 3 + Mathf.pow(power, 0.35f);
                            float total = twoDig(lightingAmount * damage * length);

                            String value;

                            if (unit.buildOn() != null && unit.buildOn().team != unit.team) {
                                value = twoDig(radiusMulti[unit.buildOn().block.size - 1] * lightingAmount * damage) + "";
                            } else {
                                value = lightingAmount + "*" + twoDig(damage) + "*" + length + "=" + total;
                            }

                            showValue(value, Items.surgeAlloy.color, unit.x, unit.y + averageLength * length + 2);
                        }

                    }
                }

                float alpha = Core.settings.getInt("死亡爆炸透明度") / 100f;
                if (type.flying && type.createWreck) {
                    Draw.color(Items.blastCompound.color, alpha);

                    float radius = Mathf.pow(unit.hitSize, 0.94f) * 1.25f;
                    Lines.circle(unit.x, unit.y, radius);

                    if (show) {
                        int amount = PublicStaticVoids.completeDamage(unit.team, unit.x, unit.y, radius);
                        float damage = Mathf.pow(unit.hitSize, 0.75f) * type.crashDamageMultiplier * 5f * state.rules.unitCrashDamage(unit.team);

                        showValue(twoDig(damage) + (amount == 0 ? "" : ("*" + amount + "=" + twoDig(damage * amount))), Items.blastCompound.color, unit.x, unit.y + radius + 2);
                    }
                }
            }), Core.settings.getInt("死亡爆炸透明度") / 100f);


            Draw.reset();
        });
    }

    public float getRadiusMulti(int size) {
        float cell = averageLength / 8;
        float radius = size / 2f;

        int min = Mathf.ceil(radius / cell);
        int max = Mathf.ceil(radius * 1.414f / cell);

        float total = 0;
        float section = 0;

        for (int i = min; i <= max; i++) {
            float angle = Math.min((float) Math.acos(radius / (i * cell)), Mathf.pi / 4f);
            total += (angle - section) / (Mathf.pi / 4f) * i;
            section = angle;
        }

        return total;
    }

    public float twoDig(float f) {
        return Mathf.round(f * 100) / 100f;
    }

    public void showSplashDamage(float x, float y, float radius, float damage, Team team, float buildMulti) {
        if (Core.settings.getBool("详细溅射范围")) {
            showLineSplashDamage(x, y, radius);

            countSplashDamage(World.toTile(x), World.toTile(y), radius / tilesize, damage * buildMulti, team);
            drawDamages(damages, damage * buildMulti);
        } else {
            showCompleteSplashDamage(x, y, radius);
        }
    }

    public void showCompleteSplashDamage(float x, float y, float radius) {
        Draw.color(Items.pyratite.color);
        Fill.circle(x, y, radius);
        Draw.reset();
    }

    public void showLineSplashDamage(float x, float y, float radius) {
        Draw.color(Items.pyratite.color);
        Lines.circle(x, y, radius);
        Draw.reset();
    }

    public void countSplashDamage(int tx, int ty, float baseRadius, float damage, Team team) {
        damages.clear();

        var in = world.build(tx, ty);
        if (in != null && in.team != team && in.block.size > 1 && in.health > damage) {
            Draw.color(Items.blastCompound.color);
            Fill.square(in.x, in.y, in.block.size * 4);
            Draw.reset();

            float value = damage * Math.min((in.block.size), baseRadius * 0.4f);

            int p = Point2.pack(in.tileX(), in.tileY());
            damages.put(p, value);
            return;
        }

        float radius = Math.min(baseRadius, 100), rad2 = radius * radius;
        int rays = Mathf.ceil(radius * 2 * Mathf.pi);
        double spacing = Math.PI * 2.0 / rays;

        for (int i = 0; i <= rays; i++) {
            float dealt = 0f;
            int startX = tx;
            int startY = ty;
            int endX = tx + (int) (Math.cos(spacing * i) * radius), endY = ty + (int) (Math.sin(spacing * i) * radius);

            int xDist = Math.abs(endX - startX);
            int yDist = -Math.abs(endY - startY);
            int xStep = (startX < endX ? +1 : -1);
            int yStep = (startY < endY ? +1 : -1);
            int error = xDist + yDist;

            while (startX != endX || startY != endY) {
                var build = world.build(startX, startY);
                if (build != null && build.team != team) {
                    float edgeScale = 0.6f;
                    float mult = (1f - (Mathf.dst2(startX, startY, tx, ty) / rad2) + edgeScale) / (1f + edgeScale);
                    float next = damage * mult - dealt;
                    int p = Point2.pack(startX, startY);
                    damages.put(p, Math.max(damages.get(p), next));
                    dealt += build.health;

                    if (next - dealt <= 0) {
                        break;
                    }
                }

                if (2 * error - yDist > xDist - 2 * error) {
                    error += yDist;
                    startX += xStep;
                } else {
                    error += xDist;
                    startY += yStep;
                }
            }
        }
    }

    public void drawDamages(IntFloatMap damages, float initialDamage) {
        Draw.z(Layer.shields + 1f);
        for (var e : damages) {
            int cx = Point2.x(e.key), cy = Point2.y(e.key);
            Tile tile = world.tile(cx, cy);

            float a = Mathf.curve(e.value / initialDamage, 0.375f, 1);

            Draw.color(Color.white, Items.blastCompound.color, a);
            Fill.square(tile.worldx(), tile.worldy(), 4);

            if (Core.settings.getBool("显示伤害数值")) {
                float value = e.value;

                int floor = Mathf.floor(value);
                float decimal = value - floor;

                showValue(PublicStaticVoids.formatAmount(floor, decimal), Color.white, tile.worldx(), tile.worldy() - 2, 0.75f);
            }
        }
        Draw.reset();
    }

    public void showFragBullet(float x, float y, float rotation, BulletType type) {
        BulletType fragType = type.fragBullet;
        float angle = type.fragSpread > 0 ? type.fragSpread * type.fragBullets : type.fragRandomSpread;

        Draw.color(Items.plastanium.color);
        Fill.arc(x, y, fragType.lifetime * fragType.speed, angle / 360f, rotation - angle / 2f);

        Draw.reset();
    }

    public void showLightning(float x, float y, int length, int lengthRand, int amount) {
        Draw.color(Items.surgeAlloy.color);

        Lines.dashCircle(x, y, length * averageLength);

        float totalLength = (lengthRand + length) * averageLength;
        Lines.circle(x, y, totalLength);

        float randAngle = rand.range(360f);
        for (int i = 0; i < amount; i++) {
            float angle = i * 360f / amount + randAngle;
            Lines.line(x, y, x + Mathf.sinDeg(angle) * length * averageLength, y + Mathf.cosDeg(angle) * length * averageLength);
        }

        Draw.reset();
    }

    public void showSuppression(float x, float y, float radius) {
        Draw.color(Pal.suppress);
        Fill.circle(x, y, radius);
        Draw.reset();
    }

    public void showRepairField(float x, float y, float radius) {
        Draw.color(Pal.heal);
        Fill.circle(x, y, radius);
        Draw.reset();
    }

    public void showRepairBeam(float x, float y, float radius) {
        Draw.color(Pal.heal);
        Lines.circle(x, y, radius);
        Draw.reset();
    }

    public void showValue(String value, Color color, float x, float y, float scl) {
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(scl / 4f / Scl.scl(1f));
        layout.setText(font, value);

        font.setColor(color);
        font.draw(value, x, y + layout.height + 1, Align.center);

        font.setUseIntegerPositions(ints);
        font.setColor(Color.white);
        font.getData().setScale(1f);
        Draw.reset();

        Pools.free(layout);
    }


    public void showValue(String value, Color color, float x, float y) {
        showValue(value, color, x, y, 1.5f);
    }

    public void resetPotentialBullet() {
        Pools.freeAll(potentialBullets);
        potentialBullets.clear();

        Groups.bullet.each(bullet -> limit(bullet.x, bullet.y, bullet.rotation(), bullet.id, bullet.team, bullet.type));

        Groups.unit.each(unit -> unit.type.weapons.each(weapon -> {
            if (weapon.shootOnDeath) {
                limit(unit.x, unit.y, unit.rotation(), unit.id, unit.team, weapon.bullet);
            }
        }));
    }

    public void limit(float x, float y, float rotation, int id, Team team, BulletType type) {
        if (!bulletTypeRange.containsKey(type)) {
            float range = Math.max(type.splashDamageRadius, type.suppressionRange);
            float frags = type.fragBullet != null ? type.fragBullet.lifetime * type.fragBullet.speed : -1;
            float lightings = type.lightning > 0 ? (type.lightningLength + type.lightningLengthRand) / 2 * averageLength : -1;
            range = Math.max(Math.max(range, frags), lightings);

            bulletTypeRange.put(type, range);
        }

        float range = bulletTypeRange.get(type);
        if (range > 0 && Tmp.r2.set(Tmp.r1).grow(range).contains(x, y)) {
            PotentialBullet b = Pools.obtain(PotentialBullet.class, PotentialBullet::new);
            b.set(x, y, rotation, type, id, team);

            potentialBullets.add(b);
        }
    }


    public void drawRange(Runnable runnable, float alpha) {
        if (alpha == 0) return;

        Draw.draw(Layer.groundUnit + 1, () -> {
            buffer.begin(Color.clear);
            runnable.run();
            buffer.end();

            Tmp.tr1.set(Draw.wrap(buffer.getTexture()));
            Tmp.tr1.flip(false, true);

            Draw.scl(4 / Vars.renderer.getDisplayScale());
            Draw.alpha(alpha);
            Draw.rect(Tmp.tr1, camera.position.x, camera.position.y);
            Draw.scl();
        });
    }

    public class PotentialBullet {
        public float x, y;
        public float rotation;
        public BulletType type;
        public long id;
        public Team team;

        public PotentialBullet() {
        }

        public PotentialBullet(float x, float y, float rotation, BulletType type, long id, Team team) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.type = type;
            this.id = id;
            this.team = team;
        }

        public void set(float x, float y, float rotation, BulletType type, long id, Team team) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.type = type;
            this.id = id;
            this.team = team;
        }
    }
}
