package Tools.type;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.power.PowerNode;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class SchematicPlacer extends Placer{
    //蓝图
    private Schematic schematic;

    private final Seq<Building> breaks = new Seq<>();

    public static Seq<BuildPlan> tmpPlan = new Seq<>();
    static {
        Events.run(EventType.Trigger.update, () -> {
            Unit unit = Vars.player.unit();
            if(!tmpPlan.isEmpty() && unit != null && unit.type.buildSpeed > 0 && unit.plans().isEmpty()){
                tmpPlan.each(p -> unit.plans.add(p));
                tmpPlan.clear();
            }
        });
    }

    public SchematicPlacer(){
    }

    public SchematicPlacer(boolean showing){
        this();
        this.showing = showing;
    }

    public SchematicPlacer(Schematic schematic) {
        this();
        this.schematic = schematic;
        width = schematic.width;
        height = schematic.height;
    }

    public SchematicPlacer(Schematic schematic, int pos) {
        this(schematic);
        this.x = Point2.unpack(pos).x;
        this.y = Point2.unpack(pos).y;
    }

    @Override
    public void put(int spx, int spy) {
        Tile tile = world.tile(spx - x, spy - y);
        if(tile == null)return;

        Unit unit = Vars.player.unit();

        Vars.schematics.toPlans(schematic, tile.x, tile.y).each(bp -> {
            if (bp.build() != null && bp.build().block == bp.block && bp.build().tileX() == bp.x && bp.build().tileY() == bp.y) {
                return;
            }

            breaks.clear();
            bp.hitbox(Tmp.r1);

            boolean cover = false;

            for (int i = 0; i < Tmp.r1.width / 8; i++) {
                for (int j = 0; j < Tmp.r1.height / 8; j++) {
                    int x = (int) (Tmp.r1.x / 8 + i) + 1;
                    int y = (int) (Tmp.r1.y / 8 + j) + 1;

                    Building building = Vars.world.build(x, y);
                    Tile worldTile = world.tile(x, y);

                    if(worldTile == null)return;

                    //不拆除能被替换的建筑: 防止出现玩家建造能够替换的建筑时, 由于 被替换的建筑的拆除计划 仍然存在导致的建造列表错误
                    if (!canReplace(bp, worldTile)){
                        if (building != null) {
                            if (breaks.contains(building)) continue;
                            breaks.addUnique(building);

                            unit.plans.add(new BuildPlan(building.tileX(), building.tileY()));
                        } else {
                            unit.plans.add(new BuildPlan(x, y));
                        }
                        cover = true;
                    }
                }
            }

            if(cover)tmpPlan.add(bp);
            else unit.plans.add(bp);
        });

        showing = false;
    }

    public static boolean canReplace(BuildPlan plan, Tile check) {
        return (plan.block.canReplace(check.block()) || plan.build() instanceof ConstructBlock.ConstructBuild c && c.current == plan.block) && plan.bounds(Tmp.r2).grow(0.01f).contains(check.block().bounds(check.centerX(), check.centerY(), Tmp.r3));
    }

    @Override
    public void show(int mx, int my) {
        mx -= ((schematic.width % 2 == 0 ? 4 : 0) + x * 8);
        my -= ((schematic.height % 2 == 0 ? 4 : 0) + y * 8);

        Tmp.tr1.set((Vars.schematics.getPreview(schematic)));

        Draw.mixcol(Color.white, 0.24f + Mathf.absin(Time.globalTime, 6f, 0.28f));
        Draw.rect(Tmp.tr1, mx, my);
        Draw.reset();

        Drawf.dashRect(Pal.accent, getHitbox(mx, my));
    }

    @Override
    public Rect getHitbox(int x, int y) {
        int w = width * 8;
        int h = height * 8;

        hitbox.set(x - w / 2f, y - h / 2f, w, h);

        return hitbox;
    }

    @Override
    public String name() {
        return schematic.name();
    }

    @Override
    public String save() {
        schematic.tiles.each(st -> {
            if(st.config instanceof Point2[]){
                st.config = new Point2(){};
            }
        });
        return Vars.schematics.writeBase64(schematic);
    }

    @Override
    public SchematicPlacer load(String data, String position){
        this.setSchematic(Schematics.readBase64(data));

        int pos = Integer.parseInt(position);
        this.x = Point2.unpack(pos).x;
        this.y = Point2.unpack(pos).y;
        return this;
    }

    public void toZero() {
        x = 0;
        y = 0;
    }

    @Override
    public void flipCentreOn(boolean isx, boolean offsetCenter) {
        super.flipCentreOn(isx, offsetCenter);

        flip(isx);
    }

    public void flip(boolean isx) {
        Schematic schem = new Schematic(new Seq<>(), schematic.tags, schematic.width, schematic.height);

        schem.tiles.addAll(schematic.tiles);

        schem.tiles.each(req -> {
            req.config = BuildPlan.pointConfig(req.block, req.config, p -> {
                if (isx) {
                    p.x = (short) -p.x;
                } else {
                    p.y = (short) -p.y;
                }
            });

            float offset = req.block.offset / tilesize;

            if (isx) {
                req.x = (short) (-req.x + schematic.width - 1 - offset * 2);
            } else {
                req.y = (short) (-req.y + schematic.height - 1 - offset * 2);
            }

            boolean rot = (isx && req.rotation % 2 == 1) || (!isx && req.rotation % 2 == 0);

            req.rotation = (byte) (!rot ? Mathf.mod(req.rotation + 2, 4) : req.rotation);
        });

        schematic = schem;
    }

    //奇数边长默认绕格中心旋转, 偶数边长默认绕格交界点旋转
    public void rotateCentreOn(boolean counter, boolean offsetX, boolean offsetY) {
        super.rotateCentreOn(counter, offsetX, offsetY);

        rotate(counter);

        width = schematic.width;
        height = schematic.height;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public void rotate(boolean counter) {
        int direction = Mathf.sign(counter);
        Schematic schem = new Schematic(new Seq<>(), schematic.tags, schematic.width, schematic.height);

        schem.tiles.addAll(schematic.tiles);

        float ox = schematic.width / 2f - 0.5f;
        float oy = schematic.height / 2f - 0.5f;

        schem.tiles.each(req -> {
            req.config = BuildPlan.pointConfig(req.block, req.config, p -> {
                Tmp.v1.set(p.x, p.y);
                Tmp.v1.rotate90(direction);
                p.set((int) Tmp.v1.x, (int) Tmp.v1.y);
            });

            float offset = req.block.offset / tilesize;

            Tmp.v1.set(req.x, req.y).add(offset, offset).sub(ox, oy);
            //x加oy, y加ox
            Tmp.v1.rotate90(direction).sub(offset, offset).add(oy, ox);

            req.x = (short) Tmp.v1.x;
            req.y = (short) Tmp.v1.y;

            req.rotation = (byte) Mathf.mod(req.rotation + direction, 4);
        });

        schem.width = schematic.height;
        schem.height = schematic.width;

        schematic = schem;
    }

    @Override
    public String getType() {
        return "schematic";
    }

    public void setSchematic(Schematic schematic) {
        this.schematic = schematic;
        width = schematic.width;
        height = schematic.height;
    }

    public Schematic getSchematic() {
        return schematic;
    }
}
