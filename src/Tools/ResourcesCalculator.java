package Tools;

import arc.func.Boolp;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

public class ResourcesCalculator {
    public Seq<Tile> resourcesTiles = new Seq<>();
    public Item dropItem;

    public Seq<Tile> farthestOres = new Seq<>();
    public int farthestDistance;



    //该格子附近同类矿物数量
    public void sensorOres(Tile tile, Boolp condition) {
        sensorOres(tile, condition, 0);
    }

    public void sensorOres(Tile tile, Boolp condition, int root) {
        Floor ore = tile.overlay();

        if (root == 0) {
            if (ore.itemDrop == null) return;

            resourcesTiles.clear();
            farthestOres.clear();

            farthestDistance = 0;
            dropItem = ore.itemDrop;
        }

        resourcesTiles.add(tile);
        Fx.lightBlock.at(tile.worldx(), tile.worldy(), 1, dropItem.color);

        Time.run(2f, () -> {
            boolean hasLeaf = false;
            int nextRoot = root + 1;

            for (int i = 0; i < 8; i++) {
                Point2 p = Geometry.d8(i);
                Tile t = Vars.world.tile(tile.x + p.x, tile.y + p.y);
                if (t != null && t.overlay() == ore
                        && !resourcesTiles.contains(t)
                        && (t.block() == Blocks.air || t.breakable())
                        && condition.get()) {
                    sensorOres(t, condition, nextRoot);
                    hasLeaf = true;

                    if(nextRoot >= farthestDistance){
                        if(nextRoot > farthestDistance){
                            farthestOres.clear();
                            farthestDistance = nextRoot;
                        }

                        farthestOres.add(t);
                    }
                }
            }

            if(!hasLeaf)farthestOres.remove(tile);

        });

    }

    public boolean finishedSensor(){
        return farthestOres.size == 0;
    }

    //该钻头与附近其他钻头挖掘的矿石数量
    //public static void sensorDrillOres(Tile tile, boolean isRoot, Drill drill){}

}
