package Tools.UI;

import Tools.UI.ShortcutsSchematics.SchematicsSelectDialog;
import Tools.type.Placer;
import Tools.type.SchematicPlacer;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.CoreBlock;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Scanner;

import static mindustry.Vars.*;
import static mindustry.Vars.state;

public class SchematicAuxiliaryTable {
    private final SchematicsSelectDialog selectDialog = new SchematicsSelectDialog("placers");
    private final Table all = new Table(Tex.pane);
    private final Table list = new Table();
    private final ScrollPane pane;
    private final Seq<ImageButton> group = new Seq<>();
    private final LoadDialog loadDialog = new LoadDialog();
    private final Rect tmpRect = new Rect();
    //脚本
    private Seq<Placer> placers = new Seq<>();
    //当前蓝图
    private int currentPlacer, movingPlacer = -1;
    //全体蓝图坐标
    private int spx, spy;
    private boolean adjustMoving, editorMode = true, hideList, showingScripts;

    public SchematicAuxiliaryTable(Table parents) {
        buildTop(parents);

        parents.add(all);
        list.visible(() -> !hideList);

        pane = new ScrollPane(list, Styles.horizontalPane);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);

        rebuild();

        Events.run(EventType.Trigger.draw, () -> {
            if (spx + spy == 0 && !adjustMoving) return;
            int total = placers.size;

            for (int i = 0; i < total; i++) {
                int index = total - 1 - i;
                Placer placer = placers.get(index);

                if (placer.showing) {
                    int sx = (adjustMoving || index == movingPlacer ? World.toTile(Core.input.mouseWorld().x) : spx) * 8;
                    int sy = (adjustMoving || index == movingPlacer ? World.toTile(Core.input.mouseWorld().y) : spy) * 8;

                    Draw.alpha(editorMode || (total <= 8 || (float) (i + currentPlacer) / total * 4 > 3) ? 1 : 0);
                    placer.show(sx, sy);

                    if(i == 0)tmpRect.set(placer.hitbox);
                    else tmpRect.merge(placer.hitbox);
                }
            }

            int px = (adjustMoving ? World.toTile(Core.input.mouseWorld().x) : spx) * 8;
            int py = (adjustMoving ? World.toTile(Core.input.mouseWorld().y) : spy) * 8;

            drawRangeRect(tmpRect, Tmp.v1.set(px, py), Pal.accent);

            Draw.reset();
        });

        Events.on(EventType.TapEvent.class, e -> {
            if (e.player != Vars.player) return;

            if (adjustMoving) {
                spx = e.tile.x;
                spy = e.tile.y;
                adjustMoving = false;
            } else if (movingPlacer >= 0) {
                Placer placer = placers.get(movingPlacer);
                placer.x = spx - e.tile.x;
                placer.y = spy - e.tile.y;
                movingPlacer = -1;
            }
        });

        Events.on(SchematicsSelectDialog.SchematicsSelectEvent.class, e -> {
            if (Objects.equals(e.type, "placers")) {
                if (spx + spy == 0) {
                    spx = player.tileX();
                    spy = player.tileY();
                }

                placers.add(new SchematicPlacer(e.schematic));
                rebuildPlacerList();

                Time.run(3f, () -> pane.setScrollY(pane.getMaxY()));

                movingPlacer = placers.size - 1;
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            currentPlacer = 0;
            movingPlacer = -1;
            if(!editorMode && placers.size > 0)adjustMoving = true;
        });
    }

    public static void drawRangeRect(Rect rect, Vec2 vec2, Color color) {
        Drawf.dashRect(color, rect);
        Drawf.dashLine(color, vec2.x, vec2.y, rect.x, rect.y);
        Drawf.dashLine(color, vec2.x, vec2.y, rect.x + rect.width, rect.y);
        Drawf.dashLine(color, vec2.x, vec2.y, rect.x, rect.y + rect.height);
        Drawf.dashLine(color, vec2.x, vec2.y, rect.x + rect.width, rect.y + rect.height);
    }

    void rebuild() {
        rebuildPlacerList();

        all.clearChildren();

        all.add(pane).maxHeight(editorMode ? 384 : 192).growX().row();
        addAdjustButtons(all);
    }

    public void rebuildPlacerList() {
        list.clear();
        group.clear();

        int[] i = new int[]{0};
        for (Placer placer : placers) {
            int j = i[0];

            list.table(placer instanceof SchematicPlacer sp && Vars.schematics.all().contains(sp.getSchematic()) ? Tex.buttonSelect : Tex.sliderBack, t -> {
                if (!editorMode) t.check("", b -> currentPlacer = j).update(c -> c.setChecked(currentPlacer == j));
                t.add(placer.name()).left().growX();

                if (editorMode) {
                    t.table(b -> {
                        b.defaults().size(27).margin(4);

                        if (placer instanceof SchematicPlacer sp) {
                            b.button(Icon.refresh, Styles.clearNonei, () -> {
                                Schematic schematic = schematics.all().find(s -> Objects.equals(s.name(), sp.getSchematic().name()));
                                if (schematic != null) {
                                    sp.setSchematic(schematic);
                                    rebuildPlacerList();
                                }
                            }).tooltip("同步为蓝图库内相同名称蓝图");
                        } else b.add();

                        if (j > 0) {
                            b.button(Icon.up, Styles.clearNonei, () -> {
                                placers.swap(j, j - 1);
                                rebuildPlacerList();
                            }).tooltip("上移");
                        } else b.add();

                        if (j < placers.size - 1) {
                            b.button(Icon.down, Styles.clearNonei, () -> {
                                placers.swap(j, j + 1);
                                rebuildPlacerList();
                            }).tooltip("下移");
                        } else b.add();
                    }).growX().row();
                }

                if (placer instanceof SchematicPlacer sp) {
                    addEditorButtons(t, sp);
                }
                t.table(tt -> tt.add((j + 1) + "")).width(36).right();
            }).growX().margin(12).row();

            i[0]++;
        }
    }

    //某蓝图调整
    public void addEditorButtons(Table parents, SchematicPlacer schematicPlacer) {
        parents.table(t -> {
            t.defaults().size(32).margin(3);
            int index = placers.indexOf(schematicPlacer);
            if (editorMode) {
                t.button(Icon.undo, Styles.clearNonei, () -> schematicPlacer.rotate(true)).tooltip("向左旋转");
                t.button(Icon.redo, Styles.clearNonei, () -> schematicPlacer.rotate(false)).tooltip("向右旋转");
                t.button(Icon.flipX, Styles.clearNonei, () -> schematicPlacer.flip(true)).tooltip("X翻转");
                t.button(Icon.flipY, Styles.clearNonei, () -> schematicPlacer.flip(false)).tooltip("Y翻转");

                t.button(Icon.move, Styles.clearNoneTogglei, () -> {
                    movingPlacer = index;
                    schematicPlacer.toZero();
                }).update(i -> i.setChecked(movingPlacer == index)).tooltip("设定该蓝图的相对位置");

                t.button(Icon.trash, Styles.clearNonei, () -> {
                    placers.remove(schematicPlacer);
                    currentPlacer = Mathf.clamp(currentPlacer, 0, placers.size - 1);

                    rebuildPlacerList();
                }).tooltip("删除");
            }

            t.button("", Styles.cleart, () -> schematicPlacer.showing = !schematicPlacer.showing).get().imageDraw(() -> placers.get(index).showing ? Icon.eye : Icon.eyeOff).tooltip("显示蓝图虚影");
        }).left();
    }

    //全部蓝图调整
    public void addAdjustButtons(Table parents) {
        parents.table(t -> {
            if (editorMode) {
                t.table(adjust -> {
                    adjust.defaults().size(46);
                    adjust.button(Icon.save, Styles.clearNonei, () -> ui.showTextInput("输入保存的地蓝辅助器名称", "名称:", "", name -> {
                        try {
                            save(name, placers);
                        } catch (IOException e) {
                            ui.showInfoToast("创建地蓝辅助器文件失败", 3f);
                        }
                    })).tooltip("保存");

                    adjust.button(Icon.upload, Styles.clearNonei, loadDialog::show).tooltip("加载现有文件");
                    adjust.button(Icon.trash, Styles.clearNonei, () -> ui.showConfirm("是否清除全体蓝图放置器", () -> {
                        currentPlacer = 0;
                        list.clear();
                        placers.clear();
                    })).tooltip("删除全部蓝图");
                    adjust.button(Icon.undo, Styles.clearNonei, () -> rotateCentreOn(true)).tooltip("全体蓝图绕中心向左旋转");
                    adjust.button(Icon.redo, Styles.clearNonei, () -> rotateCentreOn(false)).tooltip("全体蓝图绕中心向右旋转");
                    adjust.button(Icon.flipX, Styles.clearNonei, () -> flipCentreOn(true)).tooltip("全体蓝图绕中心X翻转");
                    adjust.button(Icon.flipY, Styles.clearNonei, () -> flipCentreOn(false)).tooltip("全体蓝图绕中心Y翻转");
                }).growX().row();
            }

            t.table(edit -> {
                edit.defaults().size(46);

                if (editorMode) {
                    edit.button(Icon.move, Styles.clearNoneTogglei, () -> {
                        adjustMoving = !adjustMoving;
                        movingPlacer = -1;
                    }).update(b -> b.setChecked(adjustMoving || spx + spy == 0)).tooltip("设定全体蓝图的绝对位置");

                    edit.button(Icon.add, Styles.clearNonei, selectDialog::show).tooltip("添加新的蓝图放置器");
                } else {
                    edit.add();
                    edit.add();
                    edit.add();

                    edit.button(Icon.paste, Styles.clearNonei, () -> {
                        placers.get(currentPlacer).put(spx, spy);
                        pane.setScrollY(pane.getMaxY() - list.getChildren().first().getHeight() * (placers.size - currentPlacer - 2));

                        if (currentPlacer + 1 < placers.size) currentPlacer++;
                    }).tooltip("放置");
                }

                ImageButton button = edit.button(hideList ? Icon.rightOpen : Icon.upOpen, Styles.clearNoneTogglei, 32, () -> {
                    hideList = !hideList;

                    if (hideList) list.clear();
                    else rebuildPlacerList();
                }).checked(hideList).tooltip("隐藏蓝图放置器列表").get();

                button.clicked(() -> button.replaceImage(new Image(hideList ? Icon.rightOpen : Icon.upOpen)));

                edit.button(Icon.eye, Styles.clearNonei, () -> {
                    showingScripts = !showingScripts;
                    placers.each(s -> s.showing = showingScripts);
                }).tooltip("设置所有蓝图的虚影");

                edit.button(editorMode ? Icon.edit : Icon.modePvp, Styles.clearNonei, () -> {
                    editorMode = !editorMode;
                    rebuild();
                    pane.setScrollY(0);
                    if(spx + spy == 0)adjustMoving = true;

                }).tooltip("切换模式");
            }).right();
        }).margin(10).marginTop(6);
    }

    public void buildTop(Table parents) {
        parents.table(Tex.buttonEdge3, top -> top.update(() -> {
            if (currentPlacer >= placers.size || currentPlacer < 0 || !(placers.get(currentPlacer) instanceof SchematicPlacer sp)) return;
            Schematic sche = sp.getSchematic();
            top.clear();

            top.table(header -> {
                //header.image(hoveredIcons).size(32).left().padLeft(16);
                header.labelWrap(sche::name).left().padLeft(10);

                header.button(Icon.infoSmall, Styles.squarei, () -> ui.schematics.showInfo(sche)).right().growX().size(40);
            }).growX().marginTop(10f);

            top.row();

            top.table(req -> {
                req.top().left();

                for (ItemStack stack : getRequirements(sche)) {
                    req.table(line -> {
                        line.image(stack.item.uiIcon).size(8 * 2).padLeft(11);
                        line.add(stack.item.localizedName).maxWidth(140f).fillX().color(Color.lightGray).padLeft(2).left().get().setEllipsis(true);
                        line.labelWrap(() -> {
                            Building core = player.core();
                            int stackamount = Math.round(stack.amount * state.rules.buildCostMultiplier);
                            if (core == null || state.rules.infiniteResources) return "*/" + stackamount;
                            int amount = core.items.get(stack.item);
                            String color = (amount < stackamount / 2f ? "[scarlet]" : amount < stackamount ? "[accent]" : "[white]");

                            return color + UI.formatAmount(amount) + "[white]/" + stackamount;
                        }).padLeft(5);
                    }).left();
                    req.row();
                }
            }).growX().margin(3);

        })).growX().margin(10).marginBottom(15).visible(() -> !editorMode).row();
    }

    public ItemStack[] getRequirements(Schematic schematic) {
        ItemSeq req = Core.settings.getBool("蓝图物品需求计入核心") ? schematic.requirements() : nonCoreRequirements(schematic);
        return req.toArray();
    }

    public ItemSeq nonCoreRequirements(Schematic schematic) {
        ItemSeq requirements = new ItemSeq();

        schematic.tiles.select(t -> !(t.block instanceof CoreBlock)).each(t -> {
            for (ItemStack stack : t.block.requirements) {
                requirements.add(stack.item, stack.amount);
            }
        });

        return requirements;
    }

    public void flipCentreOn(boolean x) {
        placers.each(s -> s.flipCentreOn(x, getOffsideCenter(x)));
    }

    public void rotateCentreOn(boolean counter) {
        placers.each(s -> s.rotateCentreOn(counter, getOffsideCenter(true), getOffsideCenter(false)));
    }

    public boolean getOffsideCenter(boolean isx) {
        return World.toTile(isx ? tmpRect.width : tmpRect.height) % 2 == 0;
    }

    public void save(String name, Seq<Placer> placers) throws IOException {
        Fi fi = Vars.dataDirectory.child("mods").child("SchematicAuxiliary").child(name + ".json");
        if (!fi.file().exists() && fi.file().isFile()) {
            if (!fi.file().createNewFile()) {
                ui.showInfoToast("创建地蓝辅助器文件失败", 3f);
                return;
            }
        }

        OutputStream outputStream = fi.write();
        Writer writer = new FileWriter(fi.file());
        Jval jval = Jval.newArray();

        placers.each(sp -> {
            Jval placerVal = Jval.newArray();
            placerVal.add(sp.getType());
            placerVal.add(sp.save());
            placerVal.add(Point2.pack(sp.x, sp.y));

            jval.add(placerVal);
        });

        jval.writeTo(writer, Jval.Jformat.formatted);
        writer.close();

        outputStream.flush();
        outputStream.close();
        ui.showInfoToast("已保存至mods/SchematicAuxiliary", 3f);
    }

    public class LoadDialog extends BaseDialog {
        private final Table table = new Table();

        public LoadDialog() {
            super("加载地蓝放置器");
            addCloseButton();

            cont.add(table).growX();
            table.defaults().growX().height(64f);
            shown(this::load);
        }

        public void load() {
            table.clear();

            Seq<Fi> fis = Vars.dataDirectory.child("mods").child("SchematicAuxiliary").findAll(f -> f.extension().equals("json"));
            fis.each(fi -> table.button(fi.name(), Styles.flatBordert, () -> {
                placers = read(fi);
                rebuild();
                spx = player.tileX();
                spy = player.tileY();
                hide();
            }).row());
        }

        public Seq<Placer> read(Fi fi){
            Jval jval;
            Seq<Placer> placers = new Seq<>();

            try {
                jval = Jval.read(new FileReader(fi.file()));
            } catch (FileNotFoundException e) {
                ui.showInfoToast("不存在该文件", 3f);
                return placers;
            }

            Jval.JsonArray list = jval.asArray();

            list.each(j -> {
                Jval.JsonArray array = j.asArray();
                String type = array.get(0).asString();
                if(Objects.equals(type, "schematic")){
                    placers.add(new SchematicPlacer().load(array.get(1).asString(), array.get(2).asString()));
                }
            });

            return placers;
        }
    }
}
