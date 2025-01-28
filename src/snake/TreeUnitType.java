package snake;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

//多体节单位
public class TreeUnitType extends UnitType {
    public IntMap<Seq<TreeUnitTypePart>> node;
    public TextureRegion[] regions;
    public int regionAmount = 2;

    public TreeUnitType(String name, IntMap<Seq<TreeUnitTypePart>> node) {
        super(name);
        engineSize = 0;

        health = 80000f;
        drawCell = false;

        //physics = false;

        this.node = node;
    }

    public static void setRotation(TreeUnit node, TreeUnitTypePart part, Unit root) {
        float partRot = Mathf.lerp(part.rotation, part.rotation2, part.partMove ? part.progress.get(node) : 0);
        float angle = Mathf.angle(node.x - root.x, node.y - root.y);

        node.rotation(angle + partRot);
    }

    public static Vec2 getFramePos(TreeUnit node, TreeUnitTypePart part, TreeUnit root, Vec2 out) {
        return getPartPos(part.partMove ? part.progress.get(node) : 0, part, out).rotate(root.rotation - 90).add(root.framePos);
    }

    public static Vec2 getPartPos(float progress, TreeUnitTypePart part, Vec2 out) {
        out.set(Mathf.lerp(part.x, part.x2, progress), Mathf.lerp(part.y, part.y2, progress));
        return out;
    }

    public static float getInferRotation(TreeUnitTypePart part, Unit root) {
        float partRot = Mathf.lerp(part.rotation, part.rotation2, part.partMove ? 0.5f : 0);
        float angle = getPartPos(part.partMove ? 0.5f : 0, part, Tmp.v1).angle();

        return angle + partRot + root.rotation + 180f;
    }

    @Override
    public void load() {
        super.load();
        regions = new TextureRegion[regionAmount];
        for (int i = 0; i < regionAmount; i++) {
            regions[i] = Core.atlas.find(name + "-" + i);
        }
        fullIcon = uiIcon = regions[0];
    }

    @Override
    public void loadIcon() {
    }

    @Override
    public void drawBody(Unit unit) {
        if (unit instanceof TreeUnit t) {
            applyColor(unit);

            Draw.rect(regions[Mathf.floor((t.isTreeRoot() ? t.id : t.treeRoot.id) % regionAmount)], unit.x, unit.y, unit.rotation - 90);

            Draw.reset();
        }
    }

    @Override
    public void init() {
        super.init();
        node.values().toArray().each(a -> a.each(part -> {
            if (part.type == null) part.type = this;
            if (part.mirrorX) {
                TreeUnitTypePart p = part.copy();
                p.x = -p.x;
                p.x2 = -p.x2;
                p.mirrorX = false;
                a.add(p);
            }

            if (part.mirrorY) {
                TreeUnitTypePart p = part.copy();
                p.y = -p.y;
                p.y2 = -p.y2;
                p.mirrorY = false;
                a.add(p);
            }
        }));
    }
}
