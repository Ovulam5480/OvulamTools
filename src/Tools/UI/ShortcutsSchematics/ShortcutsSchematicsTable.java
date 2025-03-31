package Tools.UI.ShortcutsSchematics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ctype.Content;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.input.Binding;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.Objects;

import static Tools.type.SchematicPlacer.canReplace;
import static mindustry.Vars.*;
import static mindustry.Vars.iconMed;

public class ShortcutsSchematicsTable {
    public boolean coverMode;
    int currentCategory = 0;
    boolean clearMode, syncMode, editMode;
    Table schematicsTable = new Table(Tex.pane).margin(10),
            categoryTable = new Table(Tex.pane).margin(10),
            schematicsAndCategoryTable = new Table();
    SchematicsConfigDialog schematicsConfigDialog = new SchematicsConfigDialog();
    Seq<Building> breaks = new Seq<>();
    Seq<BuildPlan> tmpPlans = new Seq<>(); ;

    Schematic hovered, wasHovered, coverPlan;
    TextureRegionDrawable hoveredIcons;

    int previousHeight, previousWidth, previousCatWidth;

    Binding[] blockSelect = {
            Binding.block_select_01,
            Binding.block_select_02,
            Binding.block_select_03,
            Binding.block_select_04,
            Binding.block_select_05,
            Binding.block_select_06,
            Binding.block_select_07,
            Binding.block_select_08,
            Binding.block_select_09,
            Binding.block_select_10
    };

    public ShortcutsSchematicsTable(Table parents) {
        buildTop(parents);

        rebuild();
        rebuildCat();

        schematicsAndCategoryTable.add(schematicsTable);
        schematicsAndCategoryTable.add(categoryTable).growY();

        schematicsAndCategoryTable.table(Tex.pane, t -> {
            t.button(Icon.trash, Styles.clearNoneTogglei, () -> clearMode = !clearMode).size(46).tooltip("按下后点击删除蓝图或者分类的提示").row();
            t.button(Icon.refresh, Styles.clearNoneTogglei, () -> syncMode = !syncMode).size(46).tooltip("按下后点击更新为蓝图库内同名蓝图").row();
            t.button(Icon.edit, Styles.clearNoneTogglei, () -> editMode = !editMode).size(46).tooltip("按下后点击编辑蓝图名称, 或编辑分类的提示").row();
            t.button(Icon.wrench, Styles.clearNoneTogglei, () -> coverMode = !coverMode).size(46).tooltip("覆盖模式, 将会拆除建造列表下方阻挡的建筑, 不局限于快捷蓝图").row();
            t.add().growY();

        }).margin(10).growY();

        parents.add(schematicsAndCategoryTable);

        Events.run(EventType.Trigger.update, () -> {
            if (getRowHeight() != previousHeight) {
                previousHeight = getRowHeight();
                rebuild();
                rebuildCat();
            }
            if (getRowWidth() != previousWidth) {
                previousWidth = getRowWidth();
                rebuild();
            }
            if (getCatWidth() != previousCatWidth) {
                previousCatWidth = getCatWidth();
                rebuildCat();
            }

            if(tmpPlans.size > 0 && player.unit() != null && player.unit().plans.size == 0){
                tmpPlans.each(p -> player.unit().plans.add(p));
                tmpPlans.clear();
            }
        });

        Events.on(EventType.TapEvent.class, e -> {
            if (e.player != Vars.player || !coverMode) return;

            coverPlace(e.tile.x, e.tile.y);
        });


        Events.on(SchematicsSelectDialog.SchematicsSelectEvent.class, e -> {
            if (Objects.equals(e.type, "schematics")) rebuild();
        });

    }


    public void rebuild() {
        schematicsTable.clear();

        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);

        int rowWidth = getRowWidth();
        int rowHeight = getRowHeight();

        for (int i = 0; i < rowWidth * rowHeight; i++) {
            if (i % rowWidth == 0) {
                schematicsTable.row();
            }

            ImageButton button;
            String schematicCode = Core.settings.getString("SchematicsFragment" + "-" + currentCategory + "-" + i, null);

            int j = i;
            if (schematicCode == null) {
                button = schematicsTable.button(Icon.add, Styles.clearNoneTogglei, () -> schematicsConfigDialog.show(currentCategory, j)).size(46).group(group).name(i + "").get();
            } else {
                String[] parts = schematicCode.split("_");
                //todo 删除
                if(parts.length <= 1){
                    String s = parts[0];
                    parts = Seq.with(s, "").toArray();
                }

                String name = parts[1];
                UnlockableContent iconContent = null;

                for (Seq<Content> contents : content.getContentMap()) {
                    UnlockableContent uc = (UnlockableContent) contents.find(c -> c instanceof UnlockableContent u && Objects.equals(u.name, name));
                    if (uc != null) {
                        iconContent = uc;
                        break;
                    }
                }

                TextureRegionDrawable icon;

                if (iconContent == null) {
                    icon = Icon.cancel;
                } else {
                    icon = new TextureRegionDrawable(iconContent.fullIcon);
                }

                Schematic schematic = Schematics.readBase64(parts[0]);

                button = schematicsTable.button(icon, Styles.clearNoneTogglei, () -> {
                    if (clearMode) {
                        Core.settings.remove("SchematicsFragment" + "-" + currentCategory + "-" + j);
                        this.rebuild();
                    } else if (syncMode) {
                        Schematic s = schematics.all().find(sc -> Objects.equals(sc.name(), schematic.name()));
                        if (s != null) {
                            SchematicsConfigDialog.putConfig(name, s, currentCategory, j);

                            ui.showInfoToast("蓝图已更新", 3f);
                        }
                        rebuild();
                    }else if(editMode){
                        Vars.ui.showTextInput("修改蓝图名称", "@name", "", s -> {
                            schematic.tags.put("name", s);
                            SchematicsConfigDialog.putConfig(name, schematic, currentCategory, j);
                            rebuild();
                        });
                    }else {
                        control.input.useSchematic(schematic);
                        if(coverMode) coverPlan = schematic;
                    }
                }).size(46).group(group).name(schematic.name()).get();

                button.update(() -> {
                    Building core = player.core();

                    Color color = (state.rules.infiniteResources || (core != null && (core.items.has(getRequirements(schematic), state.rules.buildCostMultiplier) || state.rules.infiniteResources))) && player.isBuilder() ? Color.white : Color.gray;
                    button.forEach(elem -> elem.setColor(color));

                    if (syncMode || clearMode) button.setChecked(false);
                });

                button.hovered(() -> {
                    hovered = schematic;
                    hoveredIcons = icon;
                });

                button.exited(() -> {
                    int time = Core.settings.getInt("蓝图物品需求表自动隐藏时间") * 60;

                    if (time == 0) hovered = null;
                    else Time.runTask(time, () -> {
                        if (hovered == schematic) hovered = null;
                    });
                });
            }

            schematicsTable.act(0f);
            button.resizeImage(iconMed);

        }
    }

    public void buildTop(Table parents) {
        parents.table(Tex.buttonEdge3, top -> top.update(() -> {
            if (wasHovered == hovered) return;
            wasHovered = hovered;

            top.clear();

            if (hovered != null) {
                top.table(header -> {
                    header.image(hoveredIcons).size(32).left().padLeft(16);
                    header.labelWrap(() -> hovered.name()).left().padLeft(10);

                    header.button(Icon.infoSmall, Styles.squarei, () -> ui.schematics.showInfo(hovered)).right().growX().size(40);
                }).growX().marginTop(10f);

                top.row();

                top.table(req -> {
                    req.top().left();

                    for (ItemStack stack : getRequirements(hovered)) {
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
            }
        })).growX().margin(10).marginBottom(15).visible(() -> hovered != null).row();
    }

    public void rebuildCat() {
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        categoryTable.clear();

        int catWidth = getCatWidth();
        int rowHeight = getRowHeight();

        for (int i = 0; i < catWidth * rowHeight; i++) {
            int index = i;

            if (i % catWidth == 0) {
                categoryTable.row();
            }

            Cell<TextButton> button = categoryTable.button(String.valueOf(i + 1), Styles.flatToggleMenut, () -> {
                if (clearMode) {
                    Core.settings.remove("SchematicsFragment" + "-" + index);
                    rebuildCat();
                } else if(editMode){
                    ui.showTextInput("修改类别-" + index + " 名称", "@name", "", s -> {
                        Core.settings.put("SchematicsFragment" + "-" + index, s);
                        rebuildCat();
                    });
                } else {
                    currentCategory = index;
                    rebuild();
                }
            }).size(46).group(group).update(b -> {
                b.setChecked(currentCategory == index);

                if (index < 10 && Core.input.alt() && Core.input.keyTap(blockSelect[index])) {
                    currentCategory = index;
                    rebuild();
                }
            });

            if(Core.settings.has("SchematicsFragment" + "-" + index)){
                button.tooltip(Core.settings.getString("SchematicsFragment" + "-" + index, ""));
            }
        }
    }


    public int getRowHeight() {
        return Core.settings.getInt("快捷蓝图与蓝图分类行数");
    }

    public int getRowWidth() {
        return Core.settings.getInt("快捷蓝图列数");
    }

    public int getCatWidth() {
        return Core.settings.getInt("蓝图分类列数");
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

    public void setHovered(Schematic hovered) {
        this.hovered = hovered;
    }

    public void coverPlace(int spx, int spy) {
        Tile tile = world.tile(spx, spy);
        if(tile == null)return;

        Unit unit = Vars.player.unit();
        if(unit == null)return;

        control.input.selectPlans.each(bp -> {
            if (bp.build() != null && bp.build().block == bp.block && bp.build().tileX() == bp.x && bp.build().tileY() == bp.y) {
                return;
            }

            breaks.clear();
            bp.hitbox(Tmp.r1);

            for (int i = 0; i < Tmp.r1.width / 8; i++) {
                for (int j = 0; j < Tmp.r1.height / 8; j++) {
                    int x = (int) (Tmp.r1.x / 8 + i) + 1;
                    int y = (int) (Tmp.r1.y / 8 + j) + 1;

                    Building building = Vars.world.build(x, y);
                    Tile worldTile = world.tile(x, y);

                    if(worldTile == null)return;

                    //不拆除能被替换的建筑: 防止出现玩家建造能够替换的建筑时, 由于 被替换的建筑的拆除计划 仍然存在导致的建造列表错误
                    if (!canReplace(bp, worldTile)){
                        if (building != null) {
                            if (breaks.contains(building)) continue;
                            breaks.addUnique(building);

                            unit.plans.add(new BuildPlan(building.tileX(), building.tileY()));
                        } else {
                            unit.plans.add(new BuildPlan(x, y));
                        }
                    }
                }
            }
        });

        control.input.selectPlans.each(bp -> tmpPlans.add(bp.copy()));
    }
}
