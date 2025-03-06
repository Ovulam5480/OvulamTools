package Tools;

import Tools.UI.ToolsFragment;
import Tools.copy.CopyPathfinder;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.mods;
import static mindustry.Vars.ui;

public class Tools extends Mod{
    public static SettingsMenuDialog.SettingsTable toolsSettingTable;
    public static CopyPathfinder copyPathfinder;

    @Override
    public void init(){
        copyPathfinder = new CopyPathfinder();
        toolsSettingTable = new SettingsMenuDialog.SettingsTable();
        
        ui.settings.addCategory("工具箱设置", Icon.chartBar, st -> {
            st.checkPref("禁用弹药范围显示, 重启生效", false);
            st.checkPref("详细溅射范围", false);
            putSetting(st, "溅射范围透明度");
            putSetting(st, "破片范围透明度");
            putSetting(st, "闪电路径透明度");
            putSetting(st, "压制范围透明度");
            putSetting(st, "死亡爆炸透明度");
            putSetting(st, "治疗范围透明度");

            LaserBulletType corvusBullet =  ((LaserBulletType)UnitTypes.corvus.weapons.get(0).bullet);
            st.sliderPref("死星激光宽度", 75, 0, 100, 1, i -> {
                corvusBullet.width = i;
                return i + "";
            });
            corvusBullet.width = Core.settings.getInt("死星激光宽度");

            st.checkPref("禁用快捷蓝图表, 重启生效", false);
            st.sliderPref("快捷蓝图与蓝图分类行数", 5, 4, 8, 1, i -> i + "行");
            st.sliderPref("快捷蓝图列数", 5, 4, 8, 1, i -> i + "列");
            st.sliderPref("蓝图分类列数", 2, 1, 8, 1, i -> i + "列");
            st.sliderPref("蓝图物品需求表自动隐藏时间", 3, 0, 15, 1, i -> {
                if(i == 0)return "立即隐藏";
                return i + "秒";
            });
            st.sliderPref("自动挖矿阈值", 1000, 100, 3500, 100, i -> i + "");
            st.checkPref("蓝图物品需求计入核心", false);
            st.checkPref("是否默认收起快捷蓝图表", false);
            st.checkPref("保存设定的工具表的位置", true);
            //st.checkPref("重生或附身其他单位时保存建筑序列", true);
        });

        if(!Core.settings.getBool("禁用弹药范围显示, 重启生效"))new ShowShowSheRange();
        if(!Core.settings.getBool("禁用快捷蓝图表, 重启生效")){
            new ToolsFragment(ui.hudGroup);

            Fi fi = Vars.dataDirectory.child("mods").child("SchematicAuxiliary");
            if(!fi.exists() || !fi.isDirectory()){
                fi.file().mkdir();

                Fi sl = mods.getMod(Tools.class).root.child("飙车示例.json");
                if(!fi.child("飙车示例.json").exists() && sl.exists()){
                    sl.copyTo(fi);
                }
            }
        }

        biabiabia();
    }

    public void sdawda(){
        Vars.state.rules.infiniteResources = true;
        SchematicsDialog
    }

    public void putSetting(SettingsMenuDialog.SettingsTable st,  String name){
        st.sliderPref(name, 20, 0, 100, 1, f -> {
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

    public static int iconSize = 46;

    public static int getTableWidth(){
        return (Core.settings.getInt("快捷蓝图列数") + Core.settings.getInt("蓝图分类列数")) * iconSize + 40;
    }
}
