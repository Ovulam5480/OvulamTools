package Tools.UI;

import arc.Core;
import arc.graphics.Color;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import static mindustry.Vars.ui;

public class ReadjustDialog extends BaseDialog {
    Category currentCategory = Category.distribution;
    Table blocks = new Table();
    Seq<Block> reserve = new Seq<>();
    static Seq<Block> tmp = new Seq<>();
    StringBuilder builder = new StringBuilder();
    SelectionDialog selectionDialog;

    Seq<Block>[] adjusts = new Seq[20];
    Seq<Block> all = new Seq<>();

    public ReadjustDialog() {
        super("自定义方块分类和位置");
        for (int i = 0; i < Category.all.length; i++) {
            adjusts[i * 2] = new Seq<>();
            adjusts[i * 2 + 1] = new Seq<>();
        }

        reserve.selectFrom(Vars.content.blocks(), b -> b.requirements.length > 0);
        shown(() -> {
            loadSettings();
            rebuildBlocks();
        });

        build();

        selectionDialog = new SelectionDialog();

        addCloseButton();
        buttons.button("保存", () -> {
            saveSettings();
            hide();
            applySettings();
            Vars.ui.hudfrag.blockfrag.rebuild();
        });
    }

    void build() {
        cont.table(all -> {
            all.table(c -> {
                c.table(cc -> {
                    ButtonGroup<ImageButton> group = new ButtonGroup<>();
                    group.setMinCheckCount(1);

                    for (int i = 0; i < Category.all.length; i++) {
                        if (i % 5 == 0) cc.row();

                        Category category = Category.all[i];

                        ImageButton button = cc.button(ui.getIcon(category.name()), Styles.clearTogglei, () -> {
                            currentCategory = category;
                            rebuildBlocks();
                        }).checked(b -> currentCategory == category).get();
                        button.resizeImage(64);
                        group.add(button);
                    }
                }).margin(12);

                c.add().grow();

                c.table(s -> {

                    s.button(Icon.cancel, Styles.clearNonei, () -> {
                        ui.showConfirm("清空当前分类配置", "是否清空当前分类配置, 已经保存配置的建筑无法恢复默认分类, 需要重启游戏重置其分类", () -> {
                            clearSetting(getCategory(currentCategory), true);
                            clearSetting(getCategory(currentCategory), false);
                            rebuildBlocks();
                        });
                    }).get().resizeImage(64);

                    s.row();

                    s.button(Icon.trash, Styles.clearNonei, () -> {
                        ui.showConfirm("清空所有配置", "是否所有清空配置, 已经保存配置的建筑无法恢复默认分类, 需要重启游戏重置其分类", () -> {
                            for (int i = 0; i < Category.all.length; i++) {
                                clearSetting(i, true);
                                clearSetting(i, false);
                            }
                            rebuildBlocks();
                        });
                    }).get().resizeImage(64);
                }).margin(12);
            }).growX().row();

            ScrollPane pane = new ScrollPane(blocks);
            pane.setOverscroll(false, false);
            pane.setScrollingDisabled(true, false);

            all.add(pane).growX();
        });
    }

    void rebuildBlocks() {
        blocks.clear();

        for (int ahead = 0; ahead < 2; ahead++){
            int[] i = {0};
            int cat = getCategory(currentCategory) * 2 + ahead;

            Seq<Block> bs = adjusts[cat];

            blocks.table(blockTable -> {
                bs.each(b -> {
                    if (i[0]++ % 8 == 0) blockTable.row();

                    blockTable.button(b != null ? new TextureRegionDrawable(b.uiIcon) : Icon.cancel, Styles.clearNonei, () -> selectionDialog.show(cat, i[0])).margin(8).get().resizeImage(48);
                });

                if (i[0]++ % 8 == 0) blockTable.row();

                blockTable.button(Icon.add, Styles.clearNonei, () -> selectionDialog.show(cat, i[0])).margin(8).get().resizeImage(48);

            }).marginTop(ahead * 20).row();

            if(ahead == 0)blocks.image(Tex.underline).growX().row();
        }
    }

    static int getCategory(Category category) {
        for (int i = 0; i < Category.all.length; i++) {
            if (category == Category.all[i]) return i;
        }
        return 2;
    }

    void loadSettings() {
        all.clear();
        for (int i = 0; i < Category.all.length; i++) {
            loadSetting(i, true);
            loadSetting(i, false);
        }
    }

    void loadSetting(int category, boolean ahead) {
        int cat = category * 2 + (ahead ? 0 : 1);
        adjusts[cat].clear();

        String[] names = setSettingNames(cat);

        for (String name : names) {
            Block block = Vars.content.block(name);
            if(block == null)continue;

            adjusts[cat].add(block);
            all.add(block);
        }
    }

    static String[] setSettingNames(int cat){
        String namess = Core.settings.getString("ReadjustDialog-" + cat);
        if (namess == null || namess.isEmpty()) return new String[]{""};

        return namess.split("_");
    }

    void saveSettings() {
        for (int i = 0; i < Category.all.length; i++) {
            saveSetting(i, true);
            saveSetting(i, false);
        }
    }

    void saveSetting(int category, boolean ahead) {
        int cat = category * 2 + (ahead ? 0 : 1);

        Seq<Block> bs = adjusts[cat];
        if(bs.isEmpty()){
            Core.settings.put("ReadjustDialog-" + cat, "");
            return;
        }

        bs.each(b -> builder.append(b.name).append("_"));
        builder.deleteCharAt(builder.length() - 1);

        Core.settings.put("ReadjustDialog-" + cat, builder.toString());
        builder.setLength(0);
    }

    void clearSetting(int category, boolean ahead) {
        Seq<Block> bs = adjusts[category * 2 + (ahead ? 0 : 1)];

        all.removeAll(bs);
        bs.clear();
    }

    public static void applySettings(){
        for (int i = 0; i < Category.all.length; i++) {
            Category category = Category.all[i];

            for (int ahead = 0; ahead < 2; ahead++) {
                tmp.clear();

                int cat = getCategory(category) * 2 + ahead;
                String[] names = setSettingNames(cat);

                for (String name : names) {
                    Block block = Vars.content.block(name);
                    if(block == null)continue;

                    block.category = category;
                    tmp.add(block);
                    Vars.content.blocks().remove(block);
                }

                if(ahead == 0){
                    tmp.reverse();
                    tmp.each(b -> Vars.content.blocks().insert(0, b));
                }else {
                    Vars.content.blocks().addAll(tmp);
                }
            }
        }
    }

    class SelectionDialog extends BaseDialog {
        int cat, index;
        Table table = new Table();

        SelectionDialog() {
            super("选择位于该分类的方块");

            ScrollPane pane = new ScrollPane(table);
            pane.setOverscroll(false, false);
            pane.setScrollingDisabled(true, false);

            cont.add(pane);

            int[] i = {0};
            reserve.each(b -> {
                if (i[0]++ % 8 == 0) table.row();

                table.button(button -> {
                    button.setStyle(Styles.clearNoneTogglei);

                    Stack stack = new Stack();
                    stack.add(new Table(t -> {
                        t.left();
                        t.add(new Image(b.uiIcon)).size(48f).scaling(Scaling.fit);
                    }));

                    stack.add(new Table(t -> {
                        t.left().bottom();

                        t.update(() -> {
                            t.clear();

                            Seq<Block> bs = adjusts[cat];
                            int bindex = bs.indexOf(b) + 1;

                            if (bindex > 0) {
                                t.add(bindex + "").style(Styles.outlineLabel);
                            }
                        });

                        t.pack();
                    }));

                    button.add(stack);
                }, () -> {
                    Seq<Block> bs = adjusts[cat];
                    if (bs.contains(b)) {
                        all.remove(b);
                        bs.remove(b);

                        index--;
                    } else {
                        all.add(b);
                        if (index > bs.size) bs.add(b);
                        else bs.set(index, b);

                        index++;
                    }

                    rebuildBlocks();
                }).margin(8).update(bu -> {
                    Seq<Block> bs = adjusts[cat];
                    bu.setChecked(bs.contains(b));
                    bu.forEach(elem -> elem.setColor(all.contains(b) && !bs.contains(b) ? Pal.darkerGray : (getCategory(b.category) == cat / 2) && !bs.contains(b) ? Color.gray : Color.white));
                    bu.setDisabled(all.contains(b) && !bs.contains(b));
                });
            });

            addCloseButton();
        }

        public void show(int cat, int index) {
            this.cat = cat;
            this.index = index;
            show();
        }
    }
}
