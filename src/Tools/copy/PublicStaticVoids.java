package Tools.copy;

import arc.func.Cons;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.struct.IntIntMap;
import arc.util.Strings;
import arc.util.Tmp;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

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

        if(!Intersector.intersectRectangles(Tmp.r1, Tmp.r2, Tmp.r3)){
            return;
        }

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

    public static void initGroundSpawns(IntIntMap spawners, boolean[] covered){
        spawners.clear();

        if(state.rules.waves) {
            spawner.getSpawns().each(t -> spawners.put(t.pos(), 0));

            if (state.rules.attackMode && state.teams.isActive(state.rules.waveTeam)) {
                for (Building core : state.rules.waveTeam.cores()) {
                    spawners.put(core.tile.pos(), 0);
                }
            }

            if (state.hasSpawns()) {
                state.rules.spawns.each(s -> {
                    if (s.type.flying) return;

                    int type = s.type.flowfieldPathType;
                    if (covered[type]) return;

                    if (s.spawn == -1) {
                        spawners.forEach(e -> spawners.put(e.key, e.value | (1 << type)));
                        covered[type] = true;
                    } else if (spawners.containsKey(s.spawn)) {
                        spawners.put(s.spawn, spawners.get(s.spawn) | (1 << type));
                    }
                });
            }
        }
    }

    private static final float coreMargin = tilesize * 2f;
    private static final float maxSteps = 30;
    private static boolean any = false;
    public static Tile coreSpawn(CoreBlock.CoreBuild core){
        float x, y;

        if(core.commandPos != null){
            x = core.commandPos.x;
            y = core.commandPos.y;
        }else{
            boolean valid = false;

            if(state.teams.playerCores().isEmpty())return null;

            Building firstCore = state.teams.playerCores().first();
            Tmp.v1.set(firstCore).sub(core).limit(coreMargin + core.block.size * tilesize /2f * Mathf.sqrt2);

            int steps = 0;

            //keep moving forward until the max step amount is reached
            while(steps++ < maxSteps){
                int tx = World.toTile(core.x + Tmp.v1.x), ty = World.toTile(core.y + Tmp.v1.y);
                any = false;
                Geometry.circle(tx, ty, world.width(), world.height(), 3, (dx, dy) -> {
                    if(world.solid(dx, dy)){
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
                x = core.x + Tmp.v1.x;
                y = core.y + Tmp.v1.y;
            }else {
                return null;
            }
        }

        return world.tileWorld(x, y);
    }
}
