package Tools;

import arc.Events;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;

import java.time.temporal.ValueRange;

public class PublicStaticVoids {

    public static boolean moveTo(Position target, float circleLength, Unit unit, boolean attack){
        if(target == null) return false;

        Vec2 vec = new Vec2();

        vec.set(target).sub(unit);

        float length = circleLength <= 0.001f ? 1f : Mathf.clamp((unit.dst(target) - circleLength) / 10, -1f, 1f);

        vec.setLength(unit.speed() * length);

        if(length < -0.5f){
            vec.setZero();
        }else if(length < 0){
            vec.setZero();
        }

        //do not move when infinite vectors are used or if its zero.
        if(vec.isNaN() || vec.isInfinite()) return false;

        if(!unit.type.omniMovement && unit.type.rotateMoveFirst){
            float angle = vec.angle();
            unit.lookAt(angle);
            if(Angles.within(unit.rotation, angle, 3f)){
                unit.movePref(vec);
            }
        }else{
            unit.movePref(vec);
        }

        boolean arrive = target.within(unit, attack ? unit.type.range - 8 : unit.type.hitSize * 10);

        if(arrive){
            Tmp.v3.set(-unit.vel.x / unit.type.accel * 2f, -unit.vel.y / unit.type.accel * 2f).add((target.getX() - unit.x), (target.getY() - unit.y));
            vec.add(Tmp.v3).limit(unit.speed() * length);
        }

        return arrive;
    }


    public static boolean collide(float x1, float y1, float w1, float h1, float vx1, float vy1,
                                        float x2, float y2, float w2, float h2, float vx2, float vy2, Vec2 out){
        float px = vx1, py = vy1;

        vx1 -= vx2;
        vy1 -= vy2;

        float xInvEntry, yInvEntry;
        float xInvExit, yInvExit;

        if(vx1 > 0.0f){
            xInvEntry = x2 - (x1 + w1);
            xInvExit = (x2 + w2) - x1;
        }else{
            xInvEntry = (x2 + w2) - x1;
            xInvExit = x2 - (x1 + w1);
        }

        if(vy1 > 0.0f){
            yInvEntry = y2 - (y1 + h1);
            yInvExit = (y2 + h2) - y1;
        }else{
            yInvEntry = (y2 + h2) - y1;
            yInvExit = y2 - (y1 + h1);
        }

        float xEntry = xInvEntry / vx1;
        float xExit = xInvExit / vx1;
        float yEntry = yInvEntry / vy1;
        float yExit = yInvExit / vy1;

        float entryTime = Math.max(xEntry, yEntry);
        float exitTime = Math.min(xExit, yExit);

        if(entryTime > exitTime || xExit < 0.0f || yExit < 0.0f || xEntry > 1.0f || yEntry > 1.0f){
            return false;
        }else{
            float dx = x1 + w1 / 2f + px * entryTime;
            float dy = y1 + h1 / 2f + py * entryTime;

            out.set(dx, dy);

            return true;
        }
    }

    public static boolean checkCollide(Hitboxc a, Hitboxc b){
        Rect r1 = Tmp.r1;
        Rect r2 = Tmp.r2;
        Vec2 l1 = Tmp.v1;

        a.hitbox(r1);
        b.hitbox(r2);

        r1.x += (a.lastX() - a.getX());
        r1.y += (a.lastY() - a.getY());
        r2.x += (b.lastX() - b.getX());
        r2.y += (b.lastY() - b.getY());

        float vax = a.getX() - a.lastX();
        float vay = a.getY() - a.lastY();
        float vbx = b.getX() - b.lastX();
        float vby = b.getY() - b.lastY();

        if(a != b && a.collides(b) && b.collides(a)){
            l1.set(a.getX(), a.getY());

            return r1.overlaps(r2) || collide(r1.x, r1.y, r1.width, r1.height, vax, vay,
                    r2.x, r2.y, r2.width, r2.height, vbx, vby, l1);
        }
        return false;
    }

    public static float calculateDamage(Unit unit, Bullet bullet) {
        if (bullet.type.splashDamageRadius > 0 && !bullet.absorbed) {
            float dist = bullet.type.scaledSplashDamage ? Math.max(0, unit.dst(bullet) - unit.type.hitSize / 2) : unit.dst(bullet);
            float radius = bullet.type.splashDamageRadius;
            float damage = bullet.type.splashDamage * bullet.damageMultiplier();

            float falloff = 0.4f;
            float scaled = Mathf.lerp(1f - dist / radius, 1f, falloff);
            return damage * scaled;
        }
        return 0;
    }
}
