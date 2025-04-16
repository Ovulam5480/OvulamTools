package Tools;

import Tools.UI.ToolsWindows;
import Tools.UI.UpdaterTable;
import Tools.copy.CopyPathfinder;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.*;

public class Tools extends Mod{
    public static CopyPathfinder copyPathfinder;
    public static Group buttonGroup;
    public static ToolsWindows windows;
    public static TableChangeEvent tableChangeEvent = new TableChangeEvent();

    @Override
    public void init(){
        copyPathfinder = new CopyPathfinder();
        
        ui.settings.addCategory("工具箱设置", Icon.chartBar, st -> {
            st.checkPref("禁用弹药范围显示, 重启生效", false);
            st.checkPref("详细溅射范围", false);
            st.checkPref("显示伤害数值", true);
            st.checkPref("启用更新检查", true);
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

            st.checkPref("启用旧版发射台与旧版发射方式", false, b -> Planets.serpulo.campaignRules.legacyLaunchPads = b);
            Planets.serpulo.campaignRules.legacyLaunchPads = Core.settings.getBool("启用旧版发射台与旧版发射方式");

            st.checkPref("禁用快捷蓝图表, 重启生效", false);
            st.sliderPref("快捷蓝图与蓝图分类行数", 5, 4, 8, 1, i -> {
                Events.fire(tableChangeEvent);
                return i + "行";
            });
            st.sliderPref("快捷蓝图列数", 5, 4, 12, 1, i -> {
                Events.fire(tableChangeEvent);
                return i + "列";
            });
            st.sliderPref("蓝图分类列数", 2, 1, 12, 1, i -> {
                Events.fire(tableChangeEvent);
                return i + "列";
            });

            st.sliderPref("快捷蓝图表大小", 100, 20, 250, 10, i -> {
                if(buttonGroup != null){
                    buttonGroup.setScale(i / 100f);
                    windows.applyPosition();
                }
                return i + "%";
            });
            st.sliderPref("蓝图物品需求表自动隐藏时间", 3, 0, 15, 1, i -> {
                if(i == 0)return "立即隐藏";
                return i + "秒";
            });
            st.sliderPref("自动挖矿阈值", 1000, 100, 3500, 100, i -> i + "");
            st.checkPref("蓝图物品需求计入核心", false);
            st.checkPref("保存设定的工具表的位置", true);
            st.checkPref("是否默认收起快捷蓝图表", false);
            //st.checkPref("重生或附身其他单位时保存建筑序列", true);
        });

        if(!Core.settings.getBool("禁用弹药范围显示, 重启生效"))new ShowShowSheRange();
        if(!Core.settings.getBool("禁用快捷蓝图表, 重启生效")){
            buttonGroup = new WidgetGroup();
            buttonGroup.setFillParent(true);
            buttonGroup.touchable = Touchable.childrenOnly;
            buttonGroup.visible(() -> state.isGame());
            //buttonGroup.visible(() -> true);
            buttonGroup.setScale(Core.settings.getInt("快捷蓝图表大小", 100) / 100f);
            buttonGroup.setTransform(true);

            Core.scene.add(buttonGroup);

            windows = new ToolsWindows(buttonGroup);

            Fi fi = Vars.dataDirectory.child("mods").child("SchematicAuxiliary");
            if(!fi.exists() || !fi.isDirectory()){
                fi.file().mkdir();

                Fi sl = thisMod().root.child("飙车示例.json");
                if(!fi.child("飙车示例.json").exists() && sl.exists()){
                    sl.copyTo(fi);
                }
            }

            if(Core.settings.getBool("启用更新检查")) {
                Http.get("https://api.github.com/repos/Ovulam5480/OvulamTools/releases/latest", res -> {
                    Jval json = Jval.read(res.getResultAsString());
                    String tag = json.get("tag_name").asString();
                    if (compareVersions(thisMod().meta.version, tag) == -1) {
                        UpdaterTable.assets = json.get("assets").asArray();
                        new UpdaterTable(windows.updaterTable);

                        Log.info("工具箱有新版更新");
                    } else {
                        Log.info("当前工具箱已是最新版本");
                    }
                }, e -> Log.info("无法连接github查询版本情况"));
            }
        }

        biabiabia();
    }

    public Mods.LoadedMod thisMod(){
        return mods.getMod(Tools.class);
    }

    public static int compareVersions(String current, String lastest) {
        String[] parts1 = current.split("\\.");
        String[] parts2 = lastest.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
            int num2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;

            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
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

    public static class TableChangeEvent{
    }
}
