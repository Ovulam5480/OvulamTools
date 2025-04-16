package Tools.UI;

import Tools.ResourcesCalculator;
import Tools.Tools;
import Tools.UI.ShortcutsSchematics.ShortcutsSchematicsTable;
import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.Group;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

public class ToolsWindows {
    private Mode mode = Mode.schematics, previousMode = Mode.schematics;
    boolean hide = Core.settings.getBool("是否默认收起快捷蓝图表"), drag, inited;
    final int maxHeight = Core.graphics.getHeight() / 2;

    ResourcesCalculator resourcesCalculator = new ResourcesCalculator();
    ShortcutsSchematicsTable schematicsTable;

    private final Group parent;

    public Table window = new Table();
    public Table child, childchild;
    public Table updaterTable;

    public Table[] children;
    RulesHintTable hint;
    Runnable rebuild;
    Cell empty;

    public ToolsWindows(Group parent) {
        this.parent = parent;

        child = window.table(frame -> {
            Seq<Table> tableSeq;

            Table allSchematicsTable = new Table();
            Table sensorOresTable = new Table(Tex.pane);
            Table buttonsTable = new Table(Tex.pane);
            Table scriptsTable = new Table();
            Table rulesHintTable = new Table(Tex.pane);
            updaterTable = new Table(Tex.pane);

            tableSeq = Seq.with(allSchematicsTable, sensorOresTable, buttonsTable,
                    scriptsTable, rulesHintTable, updaterTable);

            rebuild = () -> {
                tableSeq.each(Group::clearChildren);
                int width = ShortcutsSchematicsTable.getTotalWidth();

                //------------------快捷蓝图表------------------
                schematicsTable = new ShortcutsSchematicsTable(allSchematicsTable);
                //-----------------矿物计算器表------------------
                sensorOresTable.defaults().width(width);
                new SensorOresTables(sensorOresTable, resourcesCalculator);
                //------------------功能按钮表------------------
                buttonsTable.defaults().width(width);
                new ButtonsTable(buttonsTable);
                //--------------------地蓝辅助器表---------------
                scriptsTable.defaults().width(width);
                new SchematicAuxiliaryTable(scriptsTable);
                //--------------------规则提示表----------------
                rulesHintTable.defaults().width(width);
                hint = new RulesHintTable(rulesHintTable);
                //--------------------更新器表-----------------
                updaterTable = new Table(Tex.pane);
                updaterTable.defaults().width(width);
                //-------------------------------------------
            };

            rebuild.run();

            Stack interfactStack = new Stack();
            interfactStack.update(() -> {
                if (hide || mode != previousMode) {
                    interfactStack.clear();
                    if (hide) return;
                }
                previousMode = mode;

                if (is(Mode.schematics)) interfactStack.add(allSchematicsTable);
                else if (is(Mode.buttons)) interfactStack.add(buttonsTable);
                else if (is(Mode.ores)) interfactStack.add(sensorOresTable);
                else if (is(Mode.scripts)) interfactStack.add(scriptsTable);
                else if (is(Mode.rules)) interfactStack.add(rulesHintTable);
                else if (is(Mode.update)) interfactStack.add(updaterTable);
            });
            children = tableSeq.toArray(Table.class);

            childchild = frame.table(t -> {
                empty = t.table().width(ShortcutsSchematicsTable.getTotalWidth()).growY();
                t.row();
                t.add(interfactStack);
            }).height(maxHeight).get();

            frame.row();

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

                ImageButton draggable = getDragButton();

                optionTable.add(draggable);

                addOptionButton(optionTable, Icon.list, Mode.buttons);
                addOptionButton(optionTable, Icon.zoom, Mode.ores);
                addOptionButton(optionTable, Icon.save, Mode.scripts);
//                addOptionButton(optionTable, Icon.github, Mode.update).update(b -> {
//                    b.setDisabled(!UpdaterTable.hasBuild);
//                    b.forEach(e -> e.setColor(UpdaterTable.hasBuild ? Color.white : Color.gray));
//                });
                addOptionButton(optionTable, Icon.info, Mode.rules).update(b -> {
                    b.setDisabled(!hint.hasLists());
                    b.forEach(e -> e.setColor(hint.hasLists() ? Color.white : Color.gray));
                });
            }).growX().margin(10).get();
        }).get();

        parent.addChild(window);

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (inited) return;

            if (Core.settings.getBool("保存设定的工具表的位置")) {
                applyPosition();
            } else {
                window.setPosition(clampX(0, 0), clampY(0, 0));
            }

            inited = true;
        });

        Events.on(EventType.TapEvent.class, e -> {
            if (is(Mode.ores)) {
                resourcesCalculator.sensorOres(e.tile, () -> true);
            }
        });

        Events.on(Tools.TableChangeEvent.class, e -> {
            rebuild.run();
            empty.width(ShortcutsSchematicsTable.getTotalWidth());
        });
    }

    public Cell<ImageButton> addOptionButton(Table parents, Drawable icon, Mode mode){
        return parents.button(icon, Styles.clearNoneTogglei, () -> setModeDef(mode)).update(b -> b.setChecked(is(mode)));
    }

    private ImageButton getDragButton() {
        ImageButton draggable = new ImageButton(Icon.lock, Styles.clearNoneTogglei);
        draggable.clicked(() -> {
            drag = !drag;

            Image image = new Image(drag ? Icon.move : Icon.lock);
            draggable.replaceImage(image);
        });

        draggable.dragged((x, y) -> {
            if (drag) {
                int deltaX = clampX(Core.input.deltaX() / parent.scaleX, - window.x);
                int deltaY = clampY(Core.input.deltaY() / parent.scaleY, - window.y);

                window.moveBy(deltaX, deltaY);

                if(Core.settings.getBool("保存设定的工具表的位置")) {
                    Core.settings.put("工具表X", window.x / getGroupWidth());
                    Core.settings.put("工具表Y", window.y / getGroupHeight());
                }
            }
        });
        return draggable;
    }

    public float getGroupWidth(){
        return Core.graphics.getWidth() / parent.scaleX;
    }

    public float getGroupHeight(){
        return Core.graphics.getHeight() / parent.scaleY;
    }

    public int clampX(float value, float offset){
        return (int) Mathf.clamp(value, child.getWidth() / 2 + offset, getGroupWidth() - child.getWidth() / 2 + offset);
    }

    public int clampY(float value, float offset){
        //Log.info(getGroupHeight() + " " + (child.getHeight()) + " " + maxHeight + " " + childchild.getHeight());
        //value++;
        return (int) Mathf.clamp(value, child.getHeight() / 2 + offset, getGroupHeight() + maxHeight / 2f + offset);
    }

    public void applyPosition(){
        window.x = clampX(Core.settings.getFloat("工具表X") * getGroupWidth(), 0);
        window.y = clampY(Core.settings.getFloat("工具表Y") * getGroupHeight(), 0);
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
