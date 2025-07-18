package Tools.UI.ShortcutsSchematics;

import Tools.Tools;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ctype.Content;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.Objects;

import static mindustry.Vars.*;

public class ShortcutsSchematicsTable {
    int currentCategory = 0;
    boolean clearMode, syncMode, editMode, view;
    Table schematicsTable = new Table(Tex.pane).margin(10),
            categoryTable = new Table(Tex.pane).margin(10),
            schematicsAndCategoryTable = new Table();
    SchematicsConfigDialog schematicsConfigDialog = new SchematicsConfigDialog();

    Schematic hovered, wasHovered;
    TextureRegionDrawable hoveredIcons;
    Image image;

    public ShortcutsSchematicsTable(Table parents) {
        buildTop(parents);

        rebuild();
        rebuildCat();

        schematicsAndCategoryTable.add(schematicsTable);
        schematicsAndCategoryTable.add(categoryTable).growY();

        schematicsAndCategoryTable.table(Tex.pane, t -> {
            ui.addDescTooltip(t.button(Icon.trash, Styles.clearNoneTogglei, () -> clearMode = !clearMode).size(46).get(), "按下后点击删除蓝图或者分类的提示");
            t.row();
            ui.addDescTooltip(t.button(Icon.refresh, Styles.clearNoneTogglei, () -> syncMode = !syncMode).size(46).get(), "按下后点击更新为蓝图库内同名蓝图");
            t.row();
            ui.addDescTooltip(t.button(Icon.edit, Styles.clearNoneTogglei, () -> editMode = !editMode).size(46).get(), "按下后点击编辑蓝图名称, 或编辑分类的提示");
            t.row();
            ui.addDescTooltip(t.button(Icon.eye, Styles.clearNoneTogglei, () -> view = !view).size(46).get(), "蓝图预览");
            t.row();
            t.add().growY();
            
        }).margin(10).growY();

        parents.add(schematicsAndCategoryTable);

        Events.on(Tools.TableChangeEvent.class, e -> {
            rebuild();
            rebuildCat();
        });

        Events.on(SchematicsSelectDialog.SchematicsSelectEvent.class, e -> {
            if (Objects.equals(e.type, "schematics")) rebuild();
        });

        image = new Image((Drawable)null);
        image.color.a = 0.5f;
        image.touchable = Touchable.disabled;
        image.setScaling(Scaling.fit);
        image.setFillParent(true);

        image.visible(() -> view && image.getDrawable() != null);
        Core.scene.add(image);
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

                    image.setDrawable(new TextureRegionDrawable(new TextureRegion(schematics.getPreview(hovered))));
                });

                button.exited(() -> {
                    int time = Core.settings.getInt("蓝图物品需求表自动隐藏时间");

                    if (time <= 1) hovered = null;
                    else Time.runTask(time * 60, () -> {
                        if (hovered == schematic) hovered = null;
                    });

                    image.setDrawable((Drawable) null);
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
        })).growX().margin(10).marginBottom(15).visible(() -> hovered != null || Core.settings.getInt("蓝图物品需求表自动隐藏时间") == 0).row();
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
                    try {
                        currentCategory = index;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    rebuild();
                }
            }).size(46).group(group);

            if(Core.settings.has("SchematicsFragment" + "-" + index)){
                button.tooltip(Core.settings.getString("SchematicsFragment" + "-" + index, ""));
            }
        }
    }

    public static int getTotalWidth(){
        return (getRowWidth() + getCatWidth() + 1) * 46 + 60;
    }

    public static int getRowHeight() {
        return Core.settings.getInt("快捷蓝图与蓝图分类行数");
    }

    public static int getRowWidth() {
        return Core.settings.getInt("快捷蓝图列数");
    }

    public static int getCatWidth() {
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
}
