package Tools.todo;

import arc.util.Nullable;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.blocks.defense.turrets.Turret;

public class TurretDataAnalysis {

    //炮台旋转速度, 计入超速
    public static float getRotateSpeed(Turret.TurretBuild turretBuild){
        return ((Turret)turretBuild.block).rotateSpeed * turretBuild.timeScale();
    }

    //炮台当前弹药
    public static @Nullable BulletType peekAmmo(Turret.TurretBuild turretBuild){
        return turretBuild.peekAmmo();
    }

    //炮台炮口到单位的距离
    public static float getTargetDistance(Turret.TurretBuild turretBuild, Posc p){
        return turretBuild.dst2(p) - ((Turret)turretBuild.block).shootY;
    }

    //多少帧后, 单位将会被炮台发射的子弹击中
    //todo 单位的碰撞箱实际上是正方形,
    public float getHitTime(Turret.TurretBuild turretBuild, UnitType type, Posc unitPos){
        BulletType bulletType = peekAmmo(turretBuild);

        if(bulletType == null || !bulletType.collides
                || (bulletType.collidesAir && !type.flying)
                || (bulletType.collidesGround && type.flying))return 99999;

        float rotTime = (turretBuild.angleTo(unitPos) - ((Turret)turretBuild.block).shootCone) / getRotateSpeed(turretBuild);
        float arriveTime = getTargetDistance(turretBuild, unitPos) / bulletType.speed;

        return rotTime + arriveTime;
    }


    public boolean willHitted(Bullet bullet, Unit unit){
        bullet.vel();
        return true;
    }


}
