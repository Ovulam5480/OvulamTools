package Tools.todo;

import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.types.MinerAI;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public abstract class PlayerBehavior {
    public static Player player = Vars.player;



    //移动至某个格子
    public static class Move extends PlayerBehavior{

        public static void move(int x, int y, float radius){
            Unit unit = player.unit();

            if(player.within(x, y, radius)){
                unit.vel.setZero();
                return;
            }

            float speed = unit.speed() * 1.2f;

            Tmp.v1.set(x * tilesize - player.x, y * tilesize - player.y).nor().scl(speed);
            unit.rotation(Tmp.v1.angle());

            unit.vel.set(Tmp.v1);
        }
    }

    public static class Mine extends PlayerBehavior{
        public static void mine(int x, int y){
            UnitType type = player.unit().type;
            Tile tile = world.tile(x, y);

            //move(x, y, type.mineRange);

            if(tile != null
                    && (type.mineFloor && tile.drop() != null && tile.drop().hardness <= player.unit().type.mineTier
                    || type.mineWalls && tile.wallDrop() != null && tile.wallDrop().hardness <= player.unit().type.mineTier)){
                player.unit().mineTile(tile);
            }
        }
    }
}
