package Tools.copy;

import Tools.OvulamTools;
import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.IntQueue;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.PathTile;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.world.Tile;
import mindustry.world.meta.BlockFlag;

import java.util.Arrays;

import static mindustry.Vars.*;

public class ClientPathfinder {
    public static final Seq<Prov<Flowfield>> fieldTypes = Seq.with(
            EnemyCoreField::new
    );
    public static final int
            costGround = 0,
            costLegs = 1,
            costNaval = 2,
            costNeoplasm = 3,
            costNone = 4,
            costHover = 5,
            maxCosts = 6;
    static final int impassable = -1;
    public static final Seq<PathCost> costTypes = Seq.with(
            (team, tile) ->
                    (PathTile.allDeep(tile) || ((PathTile.team(tile) == team && !PathTile.teamPassable(tile)) || PathTile.team(tile) == 0) && PathTile.solid(tile)) ? impassable : 1 +
                            PathTile.health(tile) * 5 +
                            (PathTile.nearSolid(tile) ? 2 : 0) +
                            (PathTile.nearLiquid(tile) ? 6 : 0) +
                            (PathTile.deep(tile) ? 6000 : 0) +
                            (PathTile.damages(tile) ? 30 : 0),

            (team, tile) ->
                    PathTile.legSolid(tile) ? impassable : 1 +
                            (PathTile.deep(tile) ? 6000 : 0) +
                            (PathTile.solid(tile) ? 5 : 0),

            (team, tile) ->
                    (!PathTile.liquid(tile) || PathTile.solid(tile) ? 6000 : 1) +
                            PathTile.health(tile) * 5 +
                            (PathTile.nearGround(tile) || PathTile.nearSolid(tile) ? 14 : 0) +
                            (PathTile.deep(tile) ? 0 : 1) +
                            (PathTile.damages(tile) ? 35 : 0),

            (team, tile) ->
                    (PathTile.deep(tile) || (PathTile.team(tile) == 0 && PathTile.solid(tile))) ? impassable : 1 +
                            (PathTile.health(tile) * 3) +
                            (PathTile.nearSolid(tile) ? 2 : 0) +
                            (PathTile.nearLiquid(tile) ? 2 : 0),

            (team, tile) -> 1,

            (team, tile) ->
                    (((PathTile.team(tile) == team && !PathTile.teamPassable(tile)) || PathTile.team(tile) == 0) && PathTile.solid(tile)) ? impassable : 1 +
                            PathTile.health(tile) * 5 +
                            (PathTile.nearSolid(tile) ? 2 : 0)
    );

    static int wwidth, wheight;
    int[] tiles = {};
    Flowfield[] cache;
    int[] maxValues = new int[maxCosts];
    Seq<Flowfield> mainList = new Seq<>();
    IntSeq tmpArray = new IntSeq();
    boolean needsRefresh;
    boolean enable;
    boolean hasInit = false;
    FinishedFrontier finishedEvent = new FinishedFrontier();

    public int addedFrontier;
    public int drawValueType = Core.settings.getInt("移动代价类型");
    public boolean drawValue = Core.settings.getBool("流场数据可视化");
    public boolean printValue = Core.settings.getBool("流场数据直显");
    public float fleshTime = Core.settings.getInt("算法最大计算时间") * 100000;

    public ClientPathfinder(boolean enable) {
        clearCache();

        this.enable = enable;

        Font f = Fonts.outline;
        Events.run(EventType.Trigger.draw, () -> {
            if (!enable) return;

            Flowfield path = OvulamTools.clientPathfinder.getField(drawValueType);
            int[] values = path.hasComplete ? path.completeWeights : path.weights;
            //int[] values = path.weights;

            int[] maxSpawnValue = {0};
            if(drawValue) {
                Draw.z(Layer.fogOfWar + 1);

                for (Tile spawn : spawner.getSpawns()) {
                    Tile[] hasSafe = {null};

                    loop:
                    for (int i = 1; i <= 3; i++) {
                        for (int j = -i; j <= i; j++) {
                            for (int k = -i; k <= i; k++) {
                                if (Math.abs(j) == i || Math.abs(k) == i) {
                                    Tile t = spawn.nearby(j, k);
                                    if (t != null && !t.dangerous()) {
                                        hasSafe[0] = t;
                                        break loop;
                                    }
                                }
                            }
                        }
                    }

                    if (hasSafe[0] != null) spawn = hasSafe[0];
                    else continue;

                    maxSpawnValue[0] = Math.max(maxSpawnValue[0], values[world.packArray(spawn.x, spawn.y)] % 6015);
                }

            }

            boolean shouldPrint = printValue && renderer.camerascale > 3.5f;
            if (shouldPrint) f.getData().setScale(0.1f);

            PublicStaticVoids.eachCameraTiles(tile -> {
                int value = values[world.packArray(tile.x, tile.y)] % 6015;
                int vv = values[world.packArray(tile.x, tile.y)];
                if (value == -1) return;

                if(drawValue) {
                    Draw.color(Tmp.c1.set(Color.blue).lerp(Color.red, (float) value / maxSpawnValue[0]).a(0.5f));
                    Fill.square(tile.x * 8, tile.y * 8, 4);
                }

                if (shouldPrint) f.draw(vv + "", tile.x * 8, tile.y * 8, Align.center);
            });

            if (shouldPrint) f.getData().setScale(1f);
        });

        Events.run(Trigger.update, this::update);

        Events.on(WorldLoadEvent.class, event -> {
            //时机比ButtonTable晚, 不需要在此初始化
            //if(enable)init();
            hasInit = false;
        });

        Events.on(ResetEvent.class, event -> {
            needsRefresh = false;
            mainList.clear();
        });

        Events.on(TileChangeEvent.class, event -> {
            if (state.isEditor()) return;

            updateTile(event.tile);
        });

        Events.on(TilePreChangeEvent.class, event -> {
            if (state.isEditor()) return;

            Tile tile = event.tile;

            if (tile.solid()) {
                for (int i = 0; i < 4; i++) {
                    Tile other = tile.nearby(i);
                    if (other != null) {
                        if (!other.solid()) {
                            boolean otherNearSolid = false;
                            for (int j = 0; j < 4; j++) {
                                Tile othernear = other.nearby(j);
                                if (othernear != null && othernear.solid()) {
                                    otherNearSolid = true;
                                    break;
                                }
                            }
                            int arr = other.array();
                            if (!otherNearSolid && tiles.length > arr) {
                                tiles[arr] &= ~(PathTile.bitMaskNearSolid);
                            }
                        }
                    }
                }
            }

        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if (needsRefresh && Core.graphics.getFrameId() % 2 == 0) {
                needsRefresh = false;

                for (Flowfield path : mainList) {
                    path.updateTargetPositions();
                    path.dirty = true;
                }
            }
        });
    }

    public void init() {
        if (hasInit) return;
        hasInit = true;

        tiles = new int[world.width() * world.height()];
        wwidth = world.width();
        wheight = world.height();
        mainList = new Seq<>();
        clearCache();

        for (int i = 0; i < tiles.length; i++) {
            Tile tile = world.tiles.geti(i);
            tiles[i] = Vars.pathfinder.packTile(tile);
        }

        if (state.rules.waveTeam.needsFlowField() && enable) {
            preloadPath(getField(costGround));

            if (spawner.getSpawns().contains(t -> t.floor().isLiquid)) {
                preloadPath(getField(costNaval));
            }
        }
    }

    private void clearCache() {
        cache = new Flowfield[maxCosts];
    }

    public int get(int x, int y) {
        return tiles[x + y * wwidth];
    }

    public void updateTile(Tile tile) {
        if (!enable) return;

        tile.getLinkedTiles(t -> {
            int pos = t.array();
            if (pos < tiles.length) {
                tiles[pos] = Vars.pathfinder.packTile(t);
            }
        });

        controlPath.updateTile(tile);

        needsRefresh = true;
    }

    public void update() {
        if (!enable || !state.isPlaying()) return;
        addedFrontier = 0;

        for (Flowfield data : mainList) {
            if (data.dirty && data.frontier.size == 0) {
                updateTargets(data);
                data.dirty = false;
            }

            updateFrontier(data);
        }

        //updateMaxValue();
    }

    //todo 根据最大代价, 限制边界更新
    public void updateMaxValue(){
        //对于陆军
        {
            Flowfield groundPath = OvulamTools.clientPathfinder.getField(0);

            int[] groundValues = groundPath.hasComplete ? groundPath.completeWeights : groundPath.weights;

            int groundMax = -1;
            for (Tile spawn : spawner.getSpawns()) {
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        if (Math.abs(i) == 3 || Math.abs(j) == 3) {
                            Tile t = spawn.nearby(i, j);
                            if (t != null && !t.dangerous()) {
                                groundMax = Math.max(groundMax, groundValues[world.packArray(t.x, t.y)]);
                            }
                        }
                    }
                }
            }
            maxValues[0] = groundMax;
        }

        for (int i = 1; i < maxCosts; i++){
            Flowfield path = OvulamTools.clientPathfinder.getField(i);
            int[] values = path.hasComplete ? path.completeWeights : path.weights;

            int max = -1;
            for (Tile spawn : spawner.getSpawns()) {
                max = Math.max(max, values[world.packArray(spawn.x, spawn.y)]);
            }
            maxValues[i] = max;
        }
    }

    public Flowfield getField(int costType) {
        if(!OvulamTools.pathSpawners.hasType[costType]){
            costType = 0;
        }

        if (cache[costType] == null) {
            Flowfield field = fieldTypes.get(0).get();
            field.team = Vars.state.rules.waveTeam;
            field.cost = costTypes.get(costType);
            field.costType = costType;
            field.targets.clear();
            field.getPositions(field.targets);

            cache[costType] = field;
            registerPath(field);
        }
        return cache[costType];
    }

    public @Nullable Tile getTargetTile(Tile tile, Flowfield path) {
        return getTargetTile(tile, path, 0);
    }

    public @Nullable Tile getTargetTile(Tile tile, Flowfield path, int avoidanceId) {
        if (tile == null) throw new RuntimeException("BOOM! 寻路炸辣哈哈哈!!!");

        if (!hasInit) return tile;

        if (!path.initialized || path.targets.size == 0) {
            return tile;
        }

        if (path.frontier.size == 0) {
            tmpArray.clear();
            path.getPositions(tmpArray);

            path.updateTargetPositions();
            updateTargets(path);
        }

        int[] values = path.getWidths();
        int res = path.resolution;
        int ww = path.width;
        int apos = tile.x / res + tile.y / res * ww;
        int value = values[apos];

        var points = Geometry.d8;
        int[] avoid = avoidanceId <= 0 ? null : avoidance.getAvoidance();

        Tile current = null;
        int tl = 0;
        for (Point2 point : points) {
            int dx = tile.x + point.x * res, dy = tile.y + point.y * res;

            Tile other = world.tile(dx, dy);
            if (other == null) continue;

            int packed = dx / res + dy / res * ww;
            int avoidance = avoid == null ? 0 : avoid[packed] > Integer.MAX_VALUE - avoidanceId ? 1 : 0;
            int cost = values[packed] + avoidance;

            if (cost < value && avoidance == 0 && (current == null || cost < tl) && path.passable(packed) &&
                    !(point.x != 0 && point.y != 0 && (!path.passable(((tile.x + point.x) / res + tile.y / res * ww)) || !path.passable((tile.x / res + (tile.y + point.y) / res * ww))))) {
                current = other;
                tl = cost;
            }
        }

        if (current == null || tl == impassable || (path.cost == costTypes.items[costGround] && current.dangerous() && !tile.dangerous()))
            return tile;

        return current;
    }

    private void updateTargets(Flowfield path) {
        path.search++;

        if (path.search >= Short.MAX_VALUE) {
            Arrays.fill(path.searches, (short) 0);
            path.search = 1;
        }

        for (int i = 0; i < path.targets.size; i++) {
            int pos = path.targets.get(i);

            if (pos >= path.weights.length) continue;

            path.weights[pos] = 0;
            path.searches[pos] = (short) path.search;
            path.frontier.addFirst(pos);
        }
    }

    private void preloadPath(Flowfield path) {
        path.updateTargetPositions();
        registerPath(path);
    }

    private void registerPath(Flowfield path) {
        path.setup();

        mainList.add(path);

        Arrays.fill(path.weights, impassable);

        for (int i = 0; i < path.targets.size; i++) {
            int pos = path.targets.get(i);
            path.weights[pos] = 0;
            path.frontier.addFirst(pos);
        }
    }
    private void updateFrontier(Flowfield path) {
        boolean hadAny = path.frontier.size > 0;
        int w = path.width, h = path.height;

        int counter = 0;

        long start = Time.nanos();
        boolean isDrawer = path.costType == drawValueType;

        while (path.frontier.size > 0) {
            int tile = path.frontier.removeLast();
            if (path.weights == null) return;
            int cost = path.weights[tile];

            if (path.frontier.size >= w * h) {
                path.frontier.clear();
                return;
            }

            if (cost != impassable) {

                for (Point2 point : Geometry.d4) {
                    int dx = (tile % w) + point.x, dy = (tile / w) + point.y;
                    if (dx < 0 || dy < 0 || dx >= w || dy >= h) continue;

                    int newPos = dx + dy * w;
                    int otherCost = path.getCost(tiles, newPos);

                    if ((path.weights[newPos] > cost + otherCost || path.searches[newPos] < path.search && otherCost != impassable)) {
                        path.frontier.addFirst(newPos);
                        path.weights[newPos] = cost + otherCost;
                        path.searches[newPos] = (short) path.search;

                        if(isDrawer){
                            addedFrontier++;
                        }
                    }
                }
            }

            if ((counter++) >= 200) {
                counter = 0;

                if (Time.timeSinceNanos(start) >= fleshTime) {
                    return;
                }
            }
        }

        if(hadAny && path.frontier.size == 0){
            System.arraycopy(path.weights, 0, path.completeWeights, 0, path.weights.length);
            path.hasComplete = true;
            Events.fire(finishedEvent);
        }
    }

    public static class FinishedFrontier{}

    public void setEnabled(boolean b) {
        this.enable = b;
    }

    public interface PathCost {
        int getCost(int team, int tile);
    }

    public static class EnemyCoreField extends Flowfield {
        @Override
        protected void getPositions(IntSeq out) {
            for (Building other : indexer.getEnemy(team, BlockFlag.core)) {
                out.add(other.tile.array());
            }

            if (state.rules.waves && team == state.rules.defaultTeam) {
                for (Tile other : spawner.getSpawns()) {
                    out.add(other.array());
                }
            }
        }
    }

    public static abstract class Flowfield {
        public final int resolution;
        public final int width, height;
        public final IntQueue frontier = new IntQueue();
        final IntSeq targets = new IntSeq();
        public short[] searches;
        protected boolean hasComplete;
        public int[] completeWeights, weights;
        protected Team team = Team.derelict;
        protected PathCost cost = costTypes.get(costGround);
        protected int costType = 0;
        protected boolean dirty = false;
        public int search = 1;
        boolean initialized;

        public Flowfield() {
            this(1);
        }

        public Flowfield(int resolution) {
            this.resolution = resolution;
            this.width = Mathf.ceil((float) wwidth / resolution);
            this.height = Mathf.ceil((float) wheight / resolution);
        }

        void setup() {
            int length = width * height;

            this.searches = new short[length];
            this.weights = new int[length];
            this.completeWeights = new int[length];
            this.frontier.ensureCapacity((length) / 4);
            this.initialized = true;
        }

        public int getCost(int[] tiles, int pos) {
            return cost.getCost(team.id, tiles[pos]);
        }

        public void updateTargetPositions() {
            targets.clear();
            getPositions(targets);
        }

        protected boolean passable(int pos) {
            int amount = cost.getCost(team.id, OvulamTools.clientPathfinder.tiles[pos]);
            return amount != impassable && !(cost == costTypes.get(costNaval) && amount >= 6000);
        }

        protected int[] getWidths() {
            return hasComplete ? completeWeights : weights;
        }

        protected abstract void getPositions(IntSeq out);
    }
}