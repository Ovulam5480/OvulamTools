package Tools.copy;

import Tools.Tools;
import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.IntQueue;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.PathTile;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

public class CopyPathfinder implements Runnable {
    public static final Seq<Prov<Flowfield>> fieldTypes = Seq.with(
            EnemyCoreField::new
    );
    public static final int
            costGround = 0,
            costLegs = 1,
            costNaval = 2;
    static final int impassable = -1;
    public static final Seq<PathCost> costTypes = Seq.with(
            //ground
            (team, tile) ->
                    (PathTile.allDeep(tile) || ((PathTile.team(tile) == team && !PathTile.teamPassable(tile)) || PathTile.team(tile) == 0) && PathTile.solid(tile)) ? impassable : 1 +
                            PathTile.health(tile) * 5 +
                            (PathTile.nearSolid(tile) ? 2 : 0) +
                            (PathTile.nearLiquid(tile) ? 6 : 0) +
                            (PathTile.deep(tile) ? 6000 : 0) +
                            (PathTile.damages(tile) ? 30 : 0),

            //legs
            (team, tile) ->
                    PathTile.legSolid(tile) ? impassable : 1 +
                            (PathTile.deep(tile) ? 6000 : 0) + //leg units can now drown
                            (PathTile.solid(tile) ? 5 : 0),

            //water
            (team, tile) ->
                    (!PathTile.liquid(tile) ? 6000 : 1) +
                            PathTile.health(tile) * 5 +
                            (PathTile.nearGround(tile) || PathTile.nearSolid(tile) ? 14 : 0) +
                            (PathTile.deep(tile) ? 0 : 1) +
                            (PathTile.damages(tile) ? 35 : 0)
    );
    private static final long maxUpdate = Time.millisToNanos(8);
    private static final int updateFPS = 60;
    private static final int updateInterval = 1000 / updateFPS;

    static int wwidth, wheight;

    int[] tiles = new int[0];


    Flowfield[] cache;

    Seq<Flowfield> threadList = new Seq<>(), mainList = new Seq<>();

    TaskQueue queue = new TaskQueue();

    @Nullable
    Thread thread;
    IntSeq tmpArray = new IntSeq();
    boolean shouldUpdate = true;

    public CopyPathfinder() {
        clearCache();

        Font f = Fonts.outline;
        Events.run(EventType.Trigger.draw, () -> {
            if(true)return;
            Draw.z(Layer.fogOfWar + 1);
            Flowfield path = Tools.copyPathfinder.getField(0);
            int[] values = path.hasComplete ? path.completeWeights : path.weights;
            if(values == null)return;

            f.getData().setScale(0.1f);
            PublicStaticVoids.eachCameraTiles(tile -> {
                int value = values[world.packArray(tile.x, tile.y)] % 6000;
                if(value == -1)return;
                Draw.color(Tmp.c1.set(Color.red).hue(value * 2f).a(0.5f));
                Fill.square(tile.x * 8, tile.y * 8, 4);

                f.draw(PublicStaticVoids.formatAmount(value), tile.x * 8, tile.y * 8, Align.center);
            });
            f.getData().setScale(1f);
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            stop();

            //reset and update internal tile array
            tiles = new int[world.width() * world.height()];
            wwidth = world.width();
            wheight = world.height();
            threadList = new Seq<>();
            mainList = new Seq<>();
            clearCache();

            for (int i = 0; i < tiles.length; i++) {
                Tile tile = world.tiles.geti(i);
                tiles[i] = packTile(tile);
            }

            if (state.rules.waveTeam.needsFlowField()) {
                preloadPath(getField(costGround));

                if (spawner.getSpawns().contains(t -> t.floor().isLiquid)) {
                    preloadPath(getField(costNaval));
                }

            }

            start();
        });

        Events.on(EventType.ResetEvent.class, event -> stop());

        Events.on(EventType.TileChangeEvent.class, event -> updateTile(event.tile));

        //remove nearSolid flag for tiles
        Events.on(EventType.TilePreChangeEvent.class, event -> {
            Tile tile = event.tile;

            if (tile.solid()) {
                for (int i = 0; i < 4; i++) {
                    Tile other = tile.nearby(i);
                    if (other != null) {
                        //other tile needs to update its nearSolid to be false if it's not solid and this tile just got un-solidified
                        if (!other.solid()) {
                            boolean otherNearSolid = false;
                            for (int j = 0; j < 4; j++) {
                                Tile othernear = other.nearby(i);
                                if (othernear != null && othernear.solid()) {
                                    otherNearSolid = true;
                                    break;
                                }
                            }
                            int arr = other.array();
                            //the other tile is no longer near solid, remove the solid bit
                            if (!otherNearSolid && tiles.length > arr) {
                                tiles[arr] &= ~(PathTile.bitMaskNearSolid);
                            }
                        }
                    }
                }
            }
        });
    }

    public void setShouldUpdate(boolean shouldUpdate) {
        this.shouldUpdate = shouldUpdate;
    }

    private void clearCache() {
        cache = new Flowfield[5];
    }

    /**
     * Packs a tile into its internal representation.
     */
    public int packTile(Tile tile) {
        boolean nearLiquid = false, nearSolid = false, nearLegSolid = false, nearGround = false, solid = tile.solid(), allDeep = tile.floor().isDeep();

        for (int i = 0; i < 4; i++) {
            Tile other = tile.nearby(i);
            if (other != null) {
                Floor floor = other.floor();
                boolean osolid = other.solid();
                if (floor.isLiquid) nearLiquid = true;
                //TODO potentially strange behavior when teamPassable is false for other teams?
                if (osolid && !other.block().teamPassable) nearSolid = true;
                if (!floor.isLiquid) nearGround = true;
                if (!floor.isDeep()) allDeep = false;
                if (other.legSolid()) nearLegSolid = true;

                //other tile is now near solid
                if (solid && !tile.block().teamPassable) {
                    tiles[other.array()] |= PathTile.bitMaskNearSolid;
                }
            }
        }

        int tid = tile.getTeamID();

        return PathTile.get(
                tile.build == null || !solid || tile.block() instanceof CoreBlock ? 0 : Math.min((int) (tile.build.health / 40), 80),
                tid == 0 && tile.build != null && state.rules.coreCapture ? 255 : tid, //use teamid = 255 when core capture is enabled to mark out derelict structures
                solid,
                tile.floor().isLiquid,
                tile.legSolid(),
                nearLiquid,
                nearGround,
                nearSolid,
                nearLegSolid,
                tile.floor().isDeep(),
                tile.floor().damageTaken > 0.00001f,
                allDeep,
                tile.block().teamPassable
        );
    }

    public int get(int x, int y) {
        return tiles[x + y * wwidth];
    }

    /**
     * Starts or restarts the pathfinding thread.
     */
    private void start() {
        stop();

        thread = new Thread(this, "Pathfinder");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stops the pathfinding thread.
     */
    private void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        queue.clear();
    }

    /**
     * Update a tile in the internal pathfinding grid.
     * Causes a complete pathfinding recalculation. Main thread only.
     */
    public void updateTile(Tile tile) {
        tile.getLinkedTiles(t -> {
            int pos = t.array();
            if (pos < tiles.length) {
                tiles[pos] = packTile(t);
            }
        });

        //can't iterate through array so use the map, which should not lead to problems
        for (Flowfield path : mainList) {
            if (path != null) {
                synchronized (path.targets) {
                    path.updateTargetPositions();
                }
            }
        }

        //mark every flow field as dirty, so it updates when it's done
        queue.post(() -> {
            for (Flowfield data : threadList) {
                data.dirty = true;
            }
        });
    }

    /**
     * Thread implementation.
     */
    @Override
    public void run() {
        while (true) {
            //Time.mark();
            if (shouldUpdate) {
                try {
                    if (state.isPlaying()) {
                        queue.run();

                        //each update time (not total!) no longer than maxUpdate
                        for (Flowfield data : threadList) {

                            //if it's dirty and there is nothing to update, begin updating once more
                            if (data.dirty && data.frontier.size == 0) {
                                updateTargets(data);
                                data.dirty = false;
                            }

                            updateFrontier(data, maxUpdate);
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            //float elapsed = Time.elapsed(); // 单位：毫秒
            //Log.info("耗时: @ ms", elapsed);

            try {
                Thread.sleep(updateInterval);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public Flowfield getField(int costType) {
        if (cache[costType] == null) {
            Flowfield field = fieldTypes.get(0).get();
            field.team = Vars.state.rules.waveTeam;
            field.cost = costTypes.get(costType);
            field.targets.clear();
            field.getPositions(field.targets);

            cache[costType] = field;
            queue.post(() -> registerPath(field));
        }
        return cache[costType];
    }

    /**
     * Gets next tile to travel to. Main thread only.
     */
    public @Nullable Tile getTargetTile(Tile tile, Flowfield path) {
        if (tile == null) return null;

        //uninitialized flowfields are not applicable
        if (!path.initialized) {
            return tile;
        }

        int[] values = path.hasComplete ? path.completeWeights : path.weights;
        int apos = tile.array();
        int value = values[apos];

        Tile current = null;
        int tl = 0;
        for (Point2 point : Geometry.d8) {
            int dx = tile.x + point.x, dy = tile.y + point.y;

            Tile other = world.tile(dx, dy);
            if (other == null) continue;

            int packed = world.packArray(dx, dy);

            if (values[packed] < value && (current == null || values[packed] < tl) && path.passable(packed) &&
                    !(point.x != 0 && point.y != 0 && (!path.passable(world.packArray(tile.x + point.x, tile.y)) || !path.passable(world.packArray(tile.x, tile.y + point.y))))) { //diagonal corner trap
                current = other;
                tl = values[packed];
            }
        }

        if (current == null || tl == impassable || (path.cost == costTypes.items[costGround] && current.dangerous() && !tile.dangerous()))
            return tile;

        return current;
    }


    private void updateTargets(Flowfield path) {
        //increment search, but do not clear the frontier
        path.search++;

        synchronized (path.targets) {
            //add targets
            for (int i = 0; i < path.targets.size; i++) {
                int pos = path.targets.get(i);

                path.weights[pos] = 0;
                path.searches[pos] = path.search;
                path.frontier.addFirst(pos);
            }
        }
    }

    private void preloadPath(Flowfield path) {
        path.updateTargetPositions();
        registerPath(path);
        updateFrontier(path, -1);
    }


    private void registerPath(Flowfield path) {
        path.lastUpdateTime = Time.millis();
        path.setup(tiles.length);

        threadList.add(path);

        Core.app.post(() -> mainList.add(path));

        for (int i = 0; i < tiles.length; i++) {
            path.weights[i] = impassable;
        }

        for (int i = 0; i < path.targets.size; i++) {
            int pos = path.targets.get(i);
            path.weights[pos] = 0;
            path.frontier.addFirst(pos);
        }
    }


    private void updateFrontier(Flowfield path, long nsToRun) {
        boolean hadAny = path.frontier.size > 0;
        long start = Time.nanos();

        int counter = 0;

        while (path.frontier.size > 0) {
            int tile = path.frontier.removeLast();
            if (path.weights == null) return;
            int cost = path.weights[tile];

            if (path.frontier.size >= world.width() * world.height()) {
                path.frontier.clear();
                return;
            }

            if (cost != impassable) {
                for (Point2 point : Geometry.d4) {

                    int dx = (tile % wwidth) + point.x, dy = (tile / wwidth) + point.y;

                    if (dx < 0 || dy < 0 || dx >= wwidth || dy >= wheight) continue;

                    int newPos = tile + point.x + point.y * wwidth;
                    int otherCost = path.cost.getCost(path.team.id, tiles[newPos]);

                    if ((path.weights[newPos] > cost + otherCost || path.searches[newPos] < path.search) && otherCost != impassable) {
                        path.frontier.addFirst(newPos);
                        path.weights[newPos] = cost + otherCost;
                        path.searches[newPos] = (short) path.search;
                    }
                }
            }

            if (nsToRun >= 0 && (counter++) >= 200) {
                counter = 0;
                if (Time.timeSinceNanos(start) >= nsToRun) {
                    return;
                }
            }
        }

        if (hadAny && path.frontier.size == 0) {
            System.arraycopy(path.weights, 0, path.completeWeights, 0, path.weights.length);
            path.hasComplete = true;
        }
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

            //spawn points are also enemies.
            if (state.rules.waves && team == state.rules.defaultTeam) {
                for (Tile other : spawner.getSpawns()) {
                    out.add(other.array());
                }
            }
        }
    }

    /**
     * Data for a flow field to some set of destinations.
     * Concrete subclasses must specify a way to fetch costs and destinations.
     */
    public static abstract class Flowfield {
        /**
         * all target positions; these positions have a cost of 0, and must be synchronized on!
         */
        final IntSeq targets = new IntSeq();
        /**
         * costs of getting to a specific tile
         */
        public int[] weights;
        /**
         * search IDs of each position - the highest, most recent search is prioritized and overwritten
         */
        public int[] searches;
        /**
         * the last "complete" weights of this tilemap.
         */
        public int[] completeWeights;
        /**
         * Refresh rate in milliseconds. Return any number <= 0 to disable.
         */
        protected int refreshRate;
        /**
         * Team this path is for. Set before using.
         */
        protected Team team = Team.derelict;
        /**
         * Function for calculating path cost. Set before using.
         */
        protected PathCost cost = costTypes.get(costGround);
        /**
         * Whether there are valid weights in the complete array.
         */
        protected volatile boolean hasComplete;
        /**
         * If true, this flow field needs updating. This flag is only set to false once the flow field finishes and the weights are copied over.
         */
        protected boolean dirty = false;
        /**
         * search frontier, these are Pos objects
         */
        IntQueue frontier = new IntQueue();
        /**
         * current search ID
         */
        int search = 1;
        /**
         * last updated time
         */
        long lastUpdateTime;
        /**
         * whether this flow field is ready to be used
         */
        boolean initialized;

        void setup(int length) {
            this.weights = new int[length];
            this.searches = new int[length];
            this.completeWeights = new int[length];
            this.frontier.ensureCapacity((length) / 4);
            this.initialized = true;
        }

        public boolean hasCompleteWeights() {
            return hasComplete && completeWeights != null;
        }

        public void updateTargetPositions() {
            targets.clear();
            getPositions(targets);
        }

        protected boolean passable(int pos) {
            int amount = cost.getCost(team.id, Tools.copyPathfinder.tiles[pos]);
            //edge case: naval reports costs of 6000+ for non-liquids, even though they are not technically passable
            return amount != impassable && !(cost == costTypes.get(costNaval) && amount >= 6000);
        }

        /**
         * Gets targets to pathfind towards. This must run on the main thread.
         */
        protected abstract void getPositions(IntSeq out);
    }

}