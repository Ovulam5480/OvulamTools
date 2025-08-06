package Tools;

import arc.Events;
import arc.func.Cons2;import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.struct.IntIntMap;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.blocks.units.UnitBlock;

import java.util.Arrays;

import static mindustry.Vars.*;
import static mindustry.Vars.world;

public class PathSpawners {
    public IntIntMap spawners = new IntIntMap();
    public boolean[] hasType = new boolean[6];

    private static final float coreMargin = tilesize * 2f;
    private static final float maxSteps = 30;
    private static boolean any = false;

    public PathSpawners() {
        Events.on(EventType.UnitCreateEvent.class, e -> {
            if (!waves()) return;

            if (e.unit.team == waveTeam() && e.spawner != null) {
                int type = e.unit.type.flowfieldPathType;
                spawners.put(e.spawner.tile.pos(), 1 << type);

                if(!hasType[type] && type != 4)hasType[type] = true;
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if (!waves()) return;

            Building spawner = e.tile.build;
            if (spawner != null && spawner.team == waveTeam()) {
                spawners.remove(e.tile.pos());
            }
        });
    }

    public void eachSpawnerTiles(Cons2<Tile, Integer> get){
        spawners.forEach(e -> {
            Tile tile = getSpawnerTile(e.key);
            if(tile == null)return;

            int pathType = e.value;
            for (int i = 0; i < 6; i++){
                if(hasType[i] && ((pathType >> i) & 1) == 1){
                    get.get(tile, i);
                }
            }
        });
    }

    public void init() {
        Arrays.fill(hasType, false);
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
                    if (hasType[type]) return;

                    if (s.spawn == -1) {
                        spawners.forEach(e -> spawners.put(e.key, e.value | (1 << type)));
                        hasType[type] = true;
                    } else if (spawners.containsKey(s.spawn)) {
                        spawners.put(s.spawn, spawners.get(s.spawn) | (1 << type));
                    }
                });
            }
        }
    }

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

    public boolean waves(){
        return state.rules.waves;
    }

    public boolean randomAI(){
        return state.rules.randomWaveAI;
    }

    public Team defaultTeam(){
        return state.rules.defaultTeam;
    }

    public Team waveTeam(){
        return state.rules.waveTeam;
    }

    public Tile getSpawnerTile(int pos){
        Tile tile = world.tile(pos);

        if(tile.overlay() == Blocks.spawn)return tile;

        Building building = tile.build;
        if(building == null){
            Vars.ui.showErrorMessage("Mod-OvulamTools: 图格--- " + tile + " 不存在建筑出生点");
            return null;
        }

        Block block = building.block;

        if(block instanceof UnitAssembler || block instanceof UnitBlock){
            int radius = (block instanceof UnitAssembler a ? a.areaSize : 0) + block.size * 4 + 1;
            int ox = Geometry.d4x(building.rotation) * radius;
            int oy = Geometry.d4y(building.rotation) * radius;

            return world.tileWorld(building.x + ox, building.y + oy);
        }else if(block instanceof CoreBlock) {
            return coreSpawn((CoreBlock.CoreBuild)building);
        }

        return tile;
    }
}
