package snake;

import arc.func.Prov;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.EntityMapping;
import mindustry.gen.Entityc;
import mindustry.type.UnitType;

public class OvulamUnitTypes {
    public static TreeUnitType S1, S2, S3, S4;
    
    //public static String modName = "tools-";
    public static String modName = "春节-";

    public static ObjectMap<Class<? extends Entityc>, Integer> ids = new ObjectMap<>();

    public static int getId(Class<? extends Entityc> key) {
        return ids.get(key);
    }

    public static void put(Class<? extends Entityc> unitClass, UnitType type, Prov prov){
        ids.put(unitClass, EntityMapping.register(modName + type.name, prov));
    }

    public static void load() {
        ids.put(TreeUnit.class, EntityMapping.register(modName + "生肖-蛇", TreeUnit::new));
        ids.put(TreeUnit.class, EntityMapping.register(modName + "生肖-蛇-蛇身甲", TreeUnit::new));
        ids.put(TreeUnit.class, EntityMapping.register(modName + "生肖-蛇-蛇身乙", TreeUnit::new));
        ids.put(TreeUnit.class, EntityMapping.register(modName + "生肖-蛇-蛇尾", TreeUnit::new));

        S4 = new TreeUnitType("生肖-蛇-蛇尾", IntMap.of()){{
            hidden = true;
            hitSize = 24f;
        }};

        S3 = new TreeUnitType("生肖-蛇-蛇身乙", IntMap.of(
                3, Seq.with(new TreeUnitTypePart(0f, -24)),
                4, Seq.with(new TreeUnitTypePart(S4, 0, -24){{immediatelyAdd = true;}})
        )){{
            hidden = true;
            hitSize = 24f;
        }};

        S2 = new TreeUnitType("生肖-蛇-蛇身甲", IntMap.of(
                5, Seq.with(new TreeUnitTypePart(0f, -24)),
                6, Seq.with(new TreeUnitTypePart(S3, 0, -24))
        )){{
            hidden = true;
            hitSize = 24f;
        }};

        S1 = new TreeUnitType("生肖-蛇", IntMap.of(
                1, Seq.with(new TreeUnitTypePart(S2, 0, -24f))
        )){{
            health = 80000;
            hitSize = 24f;
        }};
    }
}
