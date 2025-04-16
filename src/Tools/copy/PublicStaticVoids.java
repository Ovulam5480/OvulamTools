package Tools.copy;

import arc.func.Cons;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.util.Strings;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Tile;

import static arc.Core.camera;
import static mindustry.Vars.*;
import static mindustry.core.UI.*;

public class PublicStaticVoids {
    public static String formatAmount(long number) {
        return formatAmount(number, 0);
    }


    public static String formatAmount(long number, float decimal) {
        long mag = Math.abs(number);
        String sign = number < 0 ? "-" : "";

        if (mag >= 1_000_000_000) {
            return sign + Strings.fixed(mag / 1_000_000_000f, 1) + "[gray]" + billions + "[]";
        } else if (mag >= 1_000_000) {
            return sign + Strings.fixed(mag / 1_000_000f, 1) + "[gray]" + millions + "[]";
        } else if (mag >= 10_000) {
            return number / 1000 + "[gray]" + thousands + "[]";
        } else if (mag >= 1000) {
            return sign + Strings.fixed(mag / 1000f, 1) + "[gray]" + thousands + "[]";
        } else if (mag >= 10) {
            return Mathf.ceil(number + decimal) + "";
        } else {
            return decimal == 0 ? number + "" : sign + Strings.fixed(mag + decimal, 1) + "[]";
        }
    }

    public static void eachCameraTiles(Cons<Tile> get){
        camera.bounds(Tmp.r1).grow(2 * tilesize);
        Tmp.r2.set(0, 0, (world.width() - 1) * tilesize, (world.height() - 1) * tilesize);

        Intersector.intersectRectangles(Tmp.r1, Tmp.r2, Tmp.r3);

        for (int i = 0; i < Tmp.r3.width; i = i + tilesize) {
            for (int j = 0; j < Tmp.r3.height; j = j + tilesize) {
                get.get(world.tileWorld(Tmp.r3.x + i, Tmp.r3.y + j));
            }
        }
    }

    public static int completeDamage(Team team, float x, float y, float radius){
        int total = 0;
        int trad = (int)(radius / tilesize);
        for(int dx = -trad; dx <= trad; dx++){
            for(int dy = -trad; dy <= trad; dy++){
                Tile tile = world.tile(Math.round(x / tilesize) + dx, Math.round(y / tilesize) + dy);
                if(tile != null && tile.build != null && (team == null || team != tile.team()) && dx*dx + dy*dy <= trad*trad){
                    total ++;
                }
            }
        }
        return total;
    }

    private static boolean any = false;
    private static final float coreMargin = tilesize * 2f;
    private static final float maxSteps = 30;
    public static void eachGroundSpawn(int filterPos, SpawnConsumer cons){
        if(state.hasSpawns()){
            for(Tile spawn : spawner.getSpawns()){
                if(filterPos != -1 && filterPos != spawn.pos()) continue;

                cons.accept(spawn, true, null);
            }
        }

        if(state.rules.attackMode && state.teams.isActive(state.rules.waveTeam) && !state.teams.playerCores().isEmpty()){
            Building firstCore = state.teams.playerCores().first();
            for(Building core : state.rules.waveTeam.cores()){
                if(filterPos != -1 && filterPos != core.pos()) continue;

                Tmp.v1.set(firstCore).sub(core).limit(coreMargin + core.block.size * tilesize /2f * Mathf.sqrt2);

                boolean valid = false;
                int steps = 0;

                //keep moving forward until the max step amount is reached
                while(steps++ < maxSteps){
                    int tx = World.toTile(core.x + Tmp.v1.x), ty = World.toTile(core.y + Tmp.v1.y);
                    any = false;
                    Geometry.circle(tx, ty, world.width(), world.height(), 3, (x, y) -> {
                        if(world.solid(x, y)){
                            any = true;
                        }
                    });

                    //nothing is in the way, spawn it
                    if(!any){
                        valid = true;
                        break;
                    }else{
                        //make the vector longer
                        Tmp.v1.setLength(Tmp.v1.len() + tilesize*1.1f);
                    }
                }

                if(valid){
                    cons.accept(Vars.world.tileWorld(core.x + Tmp.v1.x, core.y + Tmp.v1.y), false, core);
                }
            }
        }
    }

    public interface SpawnConsumer{
        void accept(Tile tile, boolean shockwave, Building coreBuild);
    }
}
