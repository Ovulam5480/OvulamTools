package Tools.type;

import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.util.Tmp;

public abstract class Placer {
    //放置器相对坐标
    public int x, y;
    public int width, height;
    public boolean showing = true;
    public Rect hitbox = new Rect();

    public abstract String getType();

    public abstract void put(int spx, int spy);

    public abstract void show(int mx, int my);

    public abstract Rect getHitbox(int mx, int my);

    public abstract String name();

    public abstract String save();

    public abstract <T extends Placer> T load(String data, String pos);

    public void flipCentreOn(boolean isx, boolean offsetCenter) {
        int offset = getOffset(offsetCenter, isx ? width : height);

        if (isx) x = -x + offset;
        else y = -y + offset;
    }

    public void rotateCentreOn(boolean counter, boolean offsetX, boolean offsetY) {
        float ox = 0;
        float oy = 0;

        if(offsetX && width % 2 == 1) ox = 0.5f;
        else if(!offsetX && width % 2 == 0)ox = -0.5f;

        if(offsetY && height % 2 == 1) oy = 0.5f;
        else if(!offsetY && height % 2 == 0)oy = -0.5f;


        int direction = Mathf.sign(counter);
        Tmp.v1.set(x, y).sub(ox, oy).rotate90(direction).add(oy, ox);

        x = (int) Tmp.v1.x;
        y = (int) Tmp.v1.y;
    }

    public int getOffset(boolean offset, int side){
        if(offset && side % 2 == 0)return -1;
        else if(!offset && side % 2 == 1)return 1;

        return 0;
    }
}
