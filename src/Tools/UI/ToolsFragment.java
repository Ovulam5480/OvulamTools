package Tools.UI;

import Tools.ResourcesCalculator;
import Tools.Tools;
import Tools.UI.ShortcutsSchematics.ShortcutsSchematicsTable;
import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.scene.Group;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Shaders;
import mindustry.ui.Styles;

public class ToolsFragment {
    private Mode mode = Mode.schematics, previousMode = Mode.schematics;
    boolean hide = Core.settings.getBool("是否默认收起快捷蓝图表"), drag;

    ResourcesCalculator resourcesCalculator = new ResourcesCalculator();

    ShortcutsSchematicsTable schematicsTable;

    Stack mainStack;

    public Table updaterTable;

    public ToolsFragment(Group parent) {
        parent.fill(full -> {
            full.bottom().left().visible(() -> Vars.ui.hudfrag.shown);
            full.table(frame -> {
                //------------------快捷蓝图表------------------
                Table allSchematicsTable = new Table();
                schematicsTable = new ShortcutsSchematicsTable(allSchematicsTable);
                //-----------------矿物计算器表------------------
                Table sensorOresTable = new Table(Tex.pane);
                sensorOresTable.defaults().width(getWidth());
                new SensorOresTables(sensorOresTable, resourcesCalculator);
                //------------------功能按钮表------------------
                Table buttonsTable = new Table(Tex.pane);
                new ButtonsTable(buttonsTable);
                //--------------------地蓝辅助器表---------------
                Table scriptsTable = new Table();
                scriptsTable.defaults().width(46 * 7 + 40);
                new SchematicAuxiliaryTable(scriptsTable);
                //--------------------规则提示表----------------
                Table rulesHintTable = new Table(Tex.pane);
                rulesHintTable.defaults().width(getWidth());
                RulesHintTable hint =new RulesHintTable(rulesHintTable);
                //--------------------更新器表-----------------
                updaterTable = new Table(Tex.pane);
                updaterTable.defaults().width(46 * 7 + 40);
                //-------------------------------------------

                mainStack = new Stack();
                mainStack.update(() -> {
                    if(hide || mode != previousMode){
                        mainStack.clear();
                        if(hide)return;
                    }
                    previousMode = mode;

                    if(is(Mode.schematics))mainStack.add(allSchematicsTable);
                    else if(is(Mode.buttons))mainStack.add(buttonsTable);
                    else if(is(Mode.ores))mainStack.add(sensorOresTable);
                    else if(is(Mode.scripts))mainStack.add(scriptsTable);
                    else if(is(Mode.rules))mainStack.add(rulesHintTable);
                    else if(is(Mode.update))mainStack.add(updaterTable);
                });

                frame.add(mainStack).row();

                //---------------------------特殊按钮表--------------------------
                frame.table(Tex.pane, optionTable -> {
                    optionTable.left();
                    optionTable.defaults().size(46);

                    Prov<Drawable> icon = () -> hide ? Icon.rightOpen : Icon.upOpen;
                    ImageButton hideTable = new ImageButton(icon.get(), Styles.clearNoneTogglei);
                    hideTable.clicked(() -> {
                        hide = !hide;
                        schematicsTable.setHovered(null);

                        Image image = new Image(icon.get());

                        hideTable.replaceImage(image);
                    });
                    optionTable.add(hideTable).checked(hide);
                    //optionTable.add(hideTable).tooltip("快捷键 `");

                    ImageButton draggable = getDragButton(full, optionTable);

                    optionTable.add(draggable);

                    addOptionButton(optionTable, Icon.list, Mode.buttons);
                    addOptionButton(optionTable, Icon.zoom, Mode.ores);
                    addOptionButton(optionTable, Icon.save, Mode.scripts);
                    addOptionButton(optionTable, Icon.github, Mode.update).update(b -> {
                        b.setDisabled(!UpdaterTable.hasBuild);
                        b.forEach(e -> e.setColor(UpdaterTable.hasBuild ? Color.white : Color.gray));
                    });
                    addOptionButton(optionTable, Icon.info, Mode.rules).update(b -> {
                        b.setDisabled(!hint.hasLists());
                        b.forEach(e -> e.setColor(hint.hasLists() ? Color.white : Color.gray));
                    });

                }).growX().margin(10);
            });

            if(Core.settings.getBool("保存设定的工具表的位置")){
                full.x = Core.settings.getFloat("工具表X");
                full.y = Core.settings.getFloat("工具表Y");
            }

        });

        Events.on(EventType.TapEvent.class, e -> {
            if (is(Mode.ores)) {
                resourcesCalculator.sensorOres(e.tile, () -> true);
            }
        });
    }

    public void addUpdater(){

    }

    public Cell<ImageButton> addOptionButton(Table parents, Drawable icon, Mode mode){
        return parents.button(icon, Styles.clearNoneTogglei, () -> setModeDef(mode)).update(b -> b.setChecked(is(mode)));
    }

    private ImageButton getDragButton(Table full, Table optionTable) {
        ImageButton draggable = new ImageButton(Icon.lock, Styles.clearNoneTogglei);
        draggable.clicked(() -> {
            drag = !drag;

            Image image = new Image(drag ? Icon.move : Icon.lock);
            draggable.replaceImage(image);
        });

        draggable.dragged((x, y) -> {
            if (drag) {
                int deltaX = (int) Mathf.clamp(Core.input.deltaX(), -full.x, Core.graphics.getWidth() - (optionTable.getWidth() + full.x));
                int deltaY = (int) Mathf.clamp(Core.input.deltaY(), -full.y, Core.graphics.getHeight() - (optionTable.getHeight() + full.y));

                full.moveBy(deltaX, deltaY);

                Core.settings.put("工具表X", full.x);
                Core.settings.put("工具表Y", full.y);
            }
        });
        return draggable;
    }

    public int getWidth(){
        return Tools.getTableWidth();
    }

    public void setModeDef(Mode mode) {
        if(is(mode))this.mode = Mode.schematics;
        else this.mode = mode;
    }

    public boolean is(Mode mode){
        return this.mode == mode;
    }

    public enum Mode{
        schematics, buttons, ores, scripts, rules, update
    }
}
