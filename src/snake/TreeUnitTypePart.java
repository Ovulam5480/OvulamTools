package snake;

import arc.func.Floatc4;
import arc.func.Floatf;
import arc.graphics.Color;
import arc.util.Time;
import mindustry.graphics.Drawf;
import mindustry.type.StatusEffect;

//nodeManager
public class TreeUnitTypePart implements Cloneable{
    public TreeUnitType type;
    //部位在根单位的相对位置, 单位默认角度为相对位置的角度
    public float x, y;
    //部位相对于根单位的初始角度, 如果单位在"后方"生成同类需要设为180
    public float rotation;
    //部位是否镜像
    public boolean mirrorX, mirrorY;
    //部位的单位的初始编号
    public int initNumber;
    //是否循环移动
    public boolean partMove;
    //循环移动的相对位置, 角度
    public float x2, y2, rotation2;
    //循环移动的进程函数
    public Floatf<TreeUnit> progress;

    //"折断"的角度差
    public float fractureAngle = 80f;
    //部位瞬移所需的最小角度差
    public float minSettingAngle = 45f;
    //在最小角度差内, 部位回归到应当位置的力度
    public float homingLerp = 0.01f;
    // 部位回归的角度
    public float minHomingAngle = 10f;

    //该部位在根单位的生成时立即生成
    public boolean immediatelyAdd = false;
    //该部位死亡或未生成时, 根单位构造该部位需要的时间,
    public float constructTime = 1 * 60f;
    //部位构造动画
    public Floatc4 drawConstruct = (x, y, rotation, progress) -> Drawf.construct(x, y, type.fullIcon, rotation, progress, progress, Time.time);


    public TreeUnitTypePart(TreeUnitType type, float x, float y) {
        this.type = type;
        this.x = x;
        this.y = y;

        rotation = rotation2 = y < 0 ? 180 : 0;
    }

    public TreeUnitTypePart(float x, float y) {
        this(null, x, y);
    }

    public TreeUnitTypePart copy(){
        try{
            return (TreeUnitTypePart) clone();
        }catch(CloneNotSupportedException suck){
            throw new RuntimeException("man, what can i say!", suck);
        }
    }
}