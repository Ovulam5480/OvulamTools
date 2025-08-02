package Tools;

import Tools.copy.PublicStaticVoids;
import arc.Events;
import arc.func.Cons2;
import arc.math.geom.Geometry;
import arc.struct.IntIntMap;
import mindustry.content.Blocks;
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

    public PathSpawners() {
        Events.on(EventType.UnitCreateEvent.class, e -> {
            if (!waves()) return;

            if (e.unit.team == waveTeam() && e.spawner != null) {
                Tile spawnTile = getSpawnerTile(e.spawner.pos());

                spawners.put(spawnTile.pos(), 1 << e.unit.type.flowfieldPathType);
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
            int pathType = e.value;

            for (int i = 0; i < 6; i++){
                if(hasType[i] && ((pathType >> i) & 1) == 1){
                    Tile tile = getSpawnerTile(e.key);
                    get.get(tile, i);
                }
            }
        });
    }

    public void init() {
        Arrays.fill(hasType, false);
        PublicStaticVoids.initGroundSpawns(spawners, hasType);
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
        Block block = building.block;

        if(block instanceof UnitAssembler || block instanceof UnitBlock){
            int radius = (block instanceof UnitAssembler a ? a.areaSize : 0) + block.size * 4 + 1;
            int ox = Geometry.d4x(building.rotation) * radius;
            int oy = Geometry.d4y(building.rotation) * radius;

            return world.tileWorld(building.x + ox, building.y + oy);
        }else if(block instanceof CoreBlock) {
            return PublicStaticVoids.coreSpawn((CoreBlock.CoreBuild)building);
        }

        return tile;
    }
}
