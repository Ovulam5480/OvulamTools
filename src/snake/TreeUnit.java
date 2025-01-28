package snake;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.entities.units.UnitController;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;


public class TreeUnit extends UnitEntity {
    public ObjectMap<TreeUnit, TreeUnitTypePart> nodes = new ObjectMap<>();
    public ObjectMap<TreeUnitTypePart, Float> constructingPart = new ObjectMap<>();

    public Seq<Unit> fractureds = new Seq<>();

    public Seq<TreeUnitTypePart> nodeParts;

    public Vec2 framePos = new Vec2();
    //非核心节点
    public @Nullable TreeUnit root;
    public TreeUnit treeRoot;
    public int number;
    public Effect fracturedEffect = new Effect(120f, e -> {
        if(!(e.data instanceof TextureRegion region))return;

        int index = Mathf.floor(e.time / 2f);
        float progress = e.time / 2f - index;

        Angles.randLenVectors(e.id + index, 1, 3, ((x1, y1) ->
                Angles.randLenVectors(e.id + index + 1, 1, 3, (x2, y2) -> {
                    float rx = e.x + Mathf.lerp(x1, x2, progress) * e.foutpowdown();
                    float ry = e.y + Mathf.lerp(y1, y2, progress) * e.foutpowdown();

                    Draw.scl(4 / e.rotation);
                    Draw.alpha(e.foutpow());
                    Draw.mixcol(Color.white, e.foutpow());

                    Draw.rect(region, rx, ry);
                    Draw.reset();
                })));
    });

    //核心节点
    public @Nullable ObjectMap<TreeUnit, TreeUnitTypePart> allNodes;

    public FrameBuffer buffer;
    public TextureRegion fractureRegion = new TextureRegion();

    public TreeUnitType asType() {
        return (TreeUnitType) type;
    }

    public int classId() {
        return OvulamUnitTypes.getId(this.getClass());
    }

    public void add(TreeUnit root, TreeUnit treeRoot, int number) {
        this.root = root;
        this.treeRoot = treeRoot;
        this.number = number;

        this.add();
    }

    @Override
    public void add() {
        if (!this.added) {
            this.index__all = Groups.all.addIndex(this);
            this.index__unit = Groups.unit.addIndex(this);
            this.index__sync = Groups.sync.addIndex(this);
            this.index__draw = Groups.draw.addIndex(this);
            this.added = true;
            this.updateLastPosition();
            if (isTreeRoot()) {
                this.team.data().updateCount(this.type, 1);
                if (this.type.useUnitCap && this.count() > this.cap() && !this.spawnedByCore && !this.dead && !Vars.state.rules.editor) {
                    Call.unitCapDeath(this);
                    this.team.data().updateCount(this.type, -1);
                }
            }

            if (treeRoot == null) {
                allNodes = new ObjectMap<>();
                treeRoot = this;
            }

            for (int item : asType().node.keys().toArray().items) {
                if (item > number) {
                    nodeParts = asType().node.get(item);
                    nodeParts.each(part -> {
                        if (part.immediatelyAdd) addNodeUnit(part);
                        else if (part.constructTime > 0) constructingPart.put(part, 0f);
                    });
                    break;
                }
            }
        }
    }

    public TreeUnit addNodeUnit(TreeUnitTypePart part) {
        UnitType type = part.type;
        int nodeNumber = type == asType() ? number + 1 : part.initNumber;

        Unit unit = type.create(team);
        if (!(unit instanceof TreeUnit n)) return null;

        nodes.put(n, part);

        n.set(TreeUnitType.getFramePos(n, part, this, Tmp.v1));
        TreeUnitType.setRotation(n, part, this);

        n.add(this, treeRoot, nodeNumber);

        n.treeRoot.allNodes.put(n, part);
        return n;
    }

    @Override
    public void update() {
        super.update();
        if (isTreeRoot()) framePos.set(this);

        nodes.each((unit, part) -> {
            if(unit.dead)return;
            Tmp.v1.set(unit).sub(this);
            TreeUnitType.getFramePos(unit, part, this, Tmp.v2).sub(this);

            float deltaAngle = Tmp.v1.angle(Tmp.v2);

            //todo 多人游戏可能会造成卡顿?
            if (Math.abs(deltaAngle) > part.fractureAngle && !unit.dead) {
                if(fractureds.contains(unit)){
                    Vars.ui.announce("如果出现此条请报告模组作者?");
                }
                fractureds.add(unit);
                unit.kill();
                return;
            }

            float angle = Math.abs(deltaAngle) > part.minSettingAngle ? part.minSettingAngle : Mathf.lerp(Math.abs(deltaAngle), part.minHomingAngle, part.homingLerp);
            unit.framePos.set(this).add(Tmp.v2.rotate(Mathf.sign(deltaAngle < 0) * angle));
            if (unit.canPass(World.toTile(unit.framePos.x), World.toTile(unit.framePos.y))) unit.set(unit.framePos);

            TreeUnitType.setRotation(unit, part, this);
        });

        if (dead) return;

        constructingPart.each((part, time) -> {
            time += Time.delta;

            if (time > part.constructTime) {
                TreeUnit t = addNodeUnit(part);
                Fx.spawn.at(t.x, t.y);

                constructingPart.remove(part);
            } else constructingPart.put(part, time);
        });

    }


    @Override
    public void draw() {
        super.draw();
        Draw.z(Draw.z() + 3);

        if (fractureds.size > 0) {
            fractureds.each(unit -> {
                if (buffer == null) {
                    buffer = new FrameBuffer();
                }

                buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());

                buffer.begin(Color.clear);
                unit.draw();
                buffer.end();

                buffer.getTexture().setFilter(Texture.TextureFilter.linear);
                fractureRegion.set(Draw.wrap(buffer.getTexture()));
                fractureRegion.flip(false, true);

                fracturedEffect.layer = Draw.z();
                fracturedEffect.at(Core.camera.position.x, Core.camera.position.y, Vars.renderer.getDisplayScale(), fractureRegion);
            });
            fractureds.clear();
        }

        Draw.draw(Layer.flyingUnit, () -> {
            constructingPart.each((part, time) -> {
                TreeUnitType.getPartPos(part.partMove ? 0.5f : 0, part, Tmp.v1).rotate(rotation - 90).add(this);

                part.drawConstruct.get(Tmp.v1.x, Tmp.v1.y, TreeUnitType.getInferRotation(part, this), time / part.constructTime);
            });
        });
    }

    public boolean isTreeRoot() {
        return treeRoot == this;
    }

    @Override
    public void remove() {
        if (!isTreeRoot()) {
            treeRoot.allNodes.remove(this);
            TreeUnitTypePart part = root.nodes.get(this);

            if (part.constructTime > 0) root.constructingPart.put(part, 0f);
            root.nodes.remove(this);
        }
        super.remove();
    }

    @Override
    public void display(Table table) {
        if (!isTreeRoot()) treeRoot.display(table);
        else super.display(table);
    }

    @Override
    public void controller(UnitController next) {
        if(treeRoot != null && !isTreeRoot())treeRoot.controller(next);
        else super.controller(next);
    }

    @Override
    public boolean isCommandable() {
        return isTreeRoot() && super.isCommandable();
    }

    @Override
    public void rawDamage(float amount) {
        if (!isTreeRoot()) {
            root.rawDamage(amount);
            super.rawDamage(0);
        } else super.rawDamage(amount);
    }

    @Override
    public void kill() {
        Time.run(3f, () -> {
            for (TreeUnit key : nodes.keys()) {
                key.kill();
            }
        });

        super.kill();
    }

    public boolean serialize() {
        return isTreeRoot();
    }
}
