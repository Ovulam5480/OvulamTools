package Tools;

import Tools.UI.ToolsFragment;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.type.StatusEffect;

import static mindustry.Vars.mods;
import static mindustry.Vars.ui;

public class Tools extends Mod{

    @Override
    public void init(){
        ui.settings.graphics.checkPref("详细溅射范围", false);
        putSetting("溅射范围透明度");
        putSetting("破片范围透明度");
        putSetting("闪电路径透明度");
        putSetting("压制范围透明度");
        putSetting("死亡爆炸透明度");
        putSetting("治疗范围透明度");

        ui.settings.game.sliderPref("死星激光宽度", 75, 0, 100, 1, i -> i + ", 重启游戏有效");
        ((LaserBulletType)UnitTypes.corvus.weapons.get(0).bullet).width = Core.settings.getInt("死星激光宽度");

        ui.settings.game.sliderPref("快捷蓝图与蓝图分类行数", 5, 4, 8, 1, i -> i + "行");
        ui.settings.game.sliderPref("快捷蓝图列数", 5, 4, 8, 1, i -> i + "列");
        ui.settings.game.sliderPref("蓝图分类列数", 2, 1, 8, 1, i -> i + "列");
        ui.settings.game.sliderPref("蓝图物品需求表自动隐藏时间", 3, 0, 15, 1, i -> {
            if(i == 0)return "立即隐藏";
            return i + "秒";
        });
        ui.settings.game.sliderPref("自动挖矿阈值", 1000, 100, 3500, 100, i -> i + "");
        ui.settings.game.checkPref("蓝图物品需求计入核心", false);
        ui.settings.game.checkPref("是否默认收起快捷蓝图表", false);
        ui.settings.game.checkPref("保存设定的工具表的位置", true);
        //ui.settings.game.checkPref("重生或附身其他单位时保存建筑序列", true);

        new ShowShowSheRange();
        new ToolsFragment(ui.hudGroup);

        biabiabia();

        Fi fi = Vars.dataDirectory.child("mods").child("SchematicAuxiliary");
        if(!fi.exists() || !fi.isDirectory()){
            fi.file().mkdir();

            Fi sl = mods.getMod(Tools.class).root.child("飙车示例.json");
            if(!fi.child("飙车示例.json").exists() && sl.exists()){
                sl.copyTo(fi);
            }
        }

        Mods.LoadedMod mod = mods.getMod(this.getClass());
        Log.info(mod.main != null && !mod.meta.hidden);
    }

    public void sdawda(){
    }

    public void putSetting(String name){
        ui.settings.graphics.sliderPref(name, 20, 0, 100, 1, f -> {
            if(f == 0)return "关闭";
            return f + "%";
        });
    }

    final String[] displayNames = new String[]{
            "致敬传奇球状闪电j8n",
            "9527的服务器为什么总会换9527的图?",
            "666666说:666666",
            "一键自动飙车",
            "原版代码搬运工",
            "这个模组被EOD的病毒传染了!",
            "按F8自动下载原神",
            "[gray]9527[scarlet]飙车[brown]大奋[violet]正[acid]版[yellow]授[red]权",
            "不允许使用蓝图(库)跟我快捷蓝图表有什么关系"
    };

    //秒做为单位
    int changeTime = 60 * 60;
    int index = Mathf.random(displayNames.length - 1);

    public void biabiabia(){
        Mods.LoadedMod mod = mods.getMod(this.getClass());

        Events.run(EventType.Trigger.update, () -> {
            if(Mathf.randomBoolean(1 / 60f / changeTime)) {
                index = Mathf.random(displayNames.length - 1);
                mod.meta.displayName = mod.meta.name + "   " + displayNames[index];
            }

            mod.meta.author = (Mathf.random(8999) + 1000) + "";
        });
    }

    public static class AffinityStatusEffects{
        public StatusEffect effect;
        public Unit unit;

        public AffinityStatusEffects(StatusEffect effect, Unit unit) {
            this.effect = effect;
            this.unit = unit;
        }
    }

    public static int iconSize = 46;

    public static int getTableWidth(){
        return (Core.settings.getInt("快捷蓝图列数") + Core.settings.getInt("蓝图分类列数")) * iconSize + 40;
    }
}
