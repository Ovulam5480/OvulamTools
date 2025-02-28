package Tools;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Point2;
import arc.struct.IntFloatMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.core.World;
import mindustry.entities.Damage;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.SuppressionFieldAbility;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.TimedKillc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.world.Tile;

import static arc.Core.camera;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class ShowShowSheRange {
    public FrameBuffer buffer = new FrameBuffer();
    public Seq<PotentialBullet> potentialBullets = new Seq<>();
    public Rand rand = new Rand();

    public ShowShowSheRange() {
        Events.run(EventType.Trigger.draw, () -> {
            buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            resetPotentialBullet();

            if (Core.settings.getInt("溅射范围透明度") > 0) {
                drawRange(() -> potentialBullets.each(potentialBullet -> {
                    if (potentialBullet.type.splashDamageRadius > 0) {
                        showSplashDamage(potentialBullet.x, potentialBullet.y, potentialBullet.type.splashDamageRadius, potentialBullet.type.splashDamage, potentialBullet.team);
                    }
                }), Core.settings.getInt("溅射范围透明度") / 100f);
            }

            if (Core.settings.getInt("破片范围透明度") > 0) {
                drawRange(() -> potentialBullets.each(potentialBullet -> {
                    if (potentialBullet.type.fragBullet != null) {
                        showFragBullet(potentialBullet.x, potentialBullet.y, potentialBullet.rotation, potentialBullet.type);
                    }
                }), Core.settings.getInt("破片范围透明度") / 100f);
            }

            if (Core.settings.getInt("闪电路径透明度") > 0) {
                drawRange(() -> potentialBullets.each(potentialBullet -> {
                    if (potentialBullet.type.lightning > 0) {
                        rand.setSeed(potentialBullet.id);
                        showLightning(potentialBullet.x, potentialBullet.y, potentialBullet.type.lightningLength / 2, potentialBullet.type.lightningLengthRand / 2, potentialBullet.type.lightning);
                    }
                }), Core.settings.getInt("闪电路径透明度") / 100f);
            }

            if (Core.settings.getInt("压制范围透明度") > 0) {
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
            }

            if (Core.settings.getInt("治疗范围透明度") > 0) {
                drawRange(() -> {
                    Groups.unit.each(unit -> {
                        RepairFieldAbility repairField = (RepairFieldAbility) unit.type.abilities.find(a -> a instanceof RepairFieldAbility);
                        if (repairField != null) {
                            showRepairField(unit.x, unit.y, repairField.range);
                        }
                        RepairBeamWeapon repairBeam = (RepairBeamWeapon) unit.type.weapons.find(a -> a instanceof RepairBeamWeapon);
                        if(repairBeam != null){
                            showRepairBeam(unit.x, unit.y, repairBeam.range());
                        }
                    });

                }, Core.settings.getInt("治疗范围透明度") / 100f);
            }

            if (Core.settings.getInt("死亡爆炸透明度") > 0) {
                drawRange(() -> {
                    Groups.unit.each(unit -> {
                        UnitType type = unit.type;
                        float alpha = Core.settings.getInt("死亡爆炸透明度") / 100f;

                        if (!unit.spawnedByCore && type.itemCapacity > 0 && unit.hasItem()) {

                            Item item = unit.item();
                            int amount = unit.stack().amount;


                            float explosiveness = 2 + item.explosiveness * amount * 1.53f;
                            int waves = explosiveness <= 2 ? 0 : Mathf.clamp((int)(explosiveness / 11), 1, 25);
                            if (waves > 0) {
                                float radius = (unit.bounds() + type.legLength / 1.7f) / 2f;
                                showCompleteSplashDamage(unit.x, unit.y, Mathf.clamp(radius + explosiveness, 0, 50f));
                            }

                            float power = item.charge * Mathf.pow(amount, 1.11f) * 160f;
                            if (power > 0) {
                                int length = 5 + Mathf.clamp((int) (Mathf.pow(power, 0.98f) / 500), 1, 18);
                                rand.setSeed(unit.id);
                                showLightning(unit.x, unit.y, length / 2, 1, (int) Mathf.clamp(power / 700, 0, 8));
                            }
                        }

                        if (type.flying && !unit.spawnedByCore && type.createWreck) {
                            Draw.color(Items.blastCompound.color, alpha);
                            Fill.circle(unit.x, unit.y, Mathf.pow(unit.hitSize, 0.94f) * 1.25f);
                            Draw.reset();
                        }
                    });
                }, Core.settings.getInt("死亡爆炸透明度") / 100f);
            }

            Draw.reset();
        });
    }

    public void showSplashDamage(float x, float y, float radius, float damage, Team team) {
        if(Core.settings.getBool("详细溅射范围")){
            Draw.color(Items.pyratite.color);
            Lines.circle(x, y, radius);
            Draw.reset();

            drawDetailedSplashDamage(World.toTile(x), World.toTile(y), radius / tilesize, damage, team);
        }else {
            showCompleteSplashDamage(x, y, radius);
        }
    }

    public void showCompleteSplashDamage(float x, float y, float radius){
        Draw.color(Items.pyratite.color);

        Fill.circle(x, y, radius);

        Draw.reset();
    }

    private static final IntFloatMap damages = new IntFloatMap();
    public void drawDetailedSplashDamage(int tx, int ty, float baseRadius, float damage, Team team) {
        var in = world.build(tx, ty);
        if(in != null && in.team != team && in.block.size > 1 && in.health > damage){
            Draw.color(Items.blastCompound.color);
            Fill.square(in.x, in.y, in.block.size * 4);
            Draw.reset();
            return;
        }

        damages.clear();

        float radius = Math.min(baseRadius, 100), rad2 = radius * radius;
        int rays = Mathf.ceil(radius * 2 * Mathf.pi);
        double spacing = Math.PI * 2.0 / rays;

        for(int i = 0; i <= rays; i++){
            float dealt = 0f;
            int startX = tx;
            int startY = ty;
            int endX = tx + (int)(Math.cos(spacing * i) * radius), endY = ty + (int)(Math.sin(spacing * i) * radius);

            int xDist = Math.abs(endX - startX);
            int yDist = -Math.abs(endY - startY);
            int xStep = (startX < endX ? +1 : -1);
            int yStep = (startY < endY ? +1 : -1);
            int error = xDist + yDist;

            while(startX != endX || startY != endY){
                var build = world.build(startX, startY);
                if(build != null && build.team != team){
                    float edgeScale = 0.6f;
                    float mult = (1f-(Mathf.dst2(startX, startY, tx, ty) / rad2) + edgeScale) / (1f + edgeScale);
                    float next = damage * mult - dealt;
                    int p = Point2.pack(startX, startY);
                    damages.put(p, Math.max(damages.get(p), next));
                    dealt += build.health;

                    if(next - dealt <= 0){
                        break;
                    }
                }

                if(2 * error - yDist > xDist - 2 * error){
                    error += yDist;
                    startX += xStep;
                }else{
                    error += xDist;
                    startY += yStep;
                }
            }
        }

        //apply damage
        for(var e : damages){
            int cx = Point2.x(e.key), cy = Point2.y(e.key);
            Tile tile = world.tile(cx, cy);

            float a = Mathf.curve(e.value / damage, 0.375f, 1);

            Draw.color(Color.white, Items.blastCompound.color, a);
            Fill.square(tile.worldx(), tile.worldy(), 4);
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
        float averageLength = 0.97981f * 15;

        for (int i = 0; i < length; i++) {
            Lines.circle(x, y, i * averageLength);
        }

        Draw.color(Items.surgeAlloy.color);

        float totalLength = (lengthRand + length) * averageLength;

        Lines.circle(x, y, totalLength);

        float randAngle = rand.range(360f);
        for (int i = 0; i < amount; i++) {
            float angle = i * 360f / amount + randAngle;
            Lines.line(x, y, x + Mathf.sinDeg(angle) * totalLength, y + Mathf.cosDeg(angle) * totalLength);
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

    public void resetPotentialBullet() {
        potentialBullets.clear();

        //todo 限制在镜头内?
        Groups.bullet.each(bullet -> {
            potentialBullets.add(new PotentialBullet(bullet.x, bullet.y, bullet.rotation(), bullet.type, bullet.id, bullet.team));
        });

        Groups.unit.each(unit -> {
            UnitType type = unit.type;
            type.weapons.each(weapon -> {
                if (weapon.shootOnDeath) potentialBullets.add(new PotentialBullet(unit.x, unit.y, unit.rotation(), weapon.bullet, unit.id, unit.team));
            });
        });
    }


    public void drawRange(Runnable runnable, float alpha) {
        Draw.draw(Layer.groundUnit + 1f, () -> {
            buffer.begin(Color.clear);

            runnable.run();

            buffer.end();

            Draw.alpha(alpha);
            Tmp.tr1.set(Draw.wrap(buffer.getTexture()));
            Tmp.tr1.flip(false, true);

            Draw.scl(4 / Vars.renderer.getDisplayScale());
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

        public PotentialBullet(float x, float y, float rotation, BulletType type, long id, Team team) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.type = type;
            this.id = id;
            this.team = team;
        }
    }
}
