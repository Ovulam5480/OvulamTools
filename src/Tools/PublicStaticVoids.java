package Tools;

import arc.Events;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.struct.IntQueue;
import arc.struct.IntSeq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.ai.WaveSpawner;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.time.temporal.ValueRange;

import static mindustry.Vars.*;
import static mindustry.ai.Pathfinder.*;

public class PublicStaticVoids {


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
