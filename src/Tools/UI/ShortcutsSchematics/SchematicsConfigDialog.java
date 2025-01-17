package Tools.UI.ShortcutsSchematics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.event.ClickListener;
import arc.scene.event.HandCursorListener;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Schematic;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.BaseDialog;

import java.util.Objects;

import static mindustry.Vars.schematics;

public class SchematicsConfigDialog extends BaseDialog {
    private final Table datebaseTable = new Table();
    private final SchematicsSelectDialog schematicsSelectDialog = new SchematicsSelectDialog("schematics");
    private int category, index;
    private String contentName = UnitTypes.mono.name;

    public SchematicsConfigDialog() {
        super("配置快捷蓝图图标");
        addCloseButton();
        shown(this::rebuildDatebase);

        datebaseTable.margin(20).marginTop(0f);

        cont.pane(datebaseTable).scrollX(false);

        Events.on(SchematicsSelectDialog.SchematicsSelectEvent.class, e -> {
            if(Objects.equals(e.type, "schematics")){
                put(e.schematic);
                hide();
            }
        });
    }

    public void put(Schematic schematic) {
        putConfig(contentName, schematic, category, index);
    }

    public static void putConfig(String contentName, Schematic schematic, int category, int index) {
        String setting = schematics.writeBase64(schematic) + "_" + contentName;

        Core.settings.put("SchematicsFragment" + "-" + category + "-" + index, setting);
    }

    public void show(int category, int index){
        this.category = category;
        this.index = index;
        this.show();
    }

    void rebuildDatebase(){
        datebaseTable.clear();

        Seq<Content>[] allContent = Vars.content.getContentMap();

        for(int j = 0; j < allContent.length; j++){
            ContentType type = ContentType.all[j];

            Seq<Content> array = allContent[j].select(c -> c instanceof UnlockableContent u
                    && !(u.fullIcon == null || u.fullIcon == Core.atlas.find("error")));

            if(array.size == 0) continue;

            datebaseTable.add("@content." + type.name() + ".name").growX().left().color(Pal.accent);
            datebaseTable.row();
            datebaseTable.image().growX().pad(5).padLeft(0).padRight(0).height(3).color(Pal.accent);
            datebaseTable.row();

            datebaseTable.table(list -> {
                list.left();

                int cols = (int) Mathf.clamp((Core.graphics.getWidth() - Scl.scl(30)) / Scl.scl(32 + 12), 1, 22);
                int count = 0;

                for(int i = 0; i < array.size; i++){
                    UnlockableContent unlock = (UnlockableContent)array.get(i);

                    Image image = new Image(unlock.fullIcon).setScaling(Scaling.fit);

                    list.add(image).size(8 * 4).pad(3);

                    ClickListener listener = new ClickListener();
                    image.addListener(listener);
                    if(!Vars.mobile){
                        image.addListener(new HandCursorListener());
                        image.update(() -> image.color.lerp(!listener.isOver() ? Color.lightGray : Color.white, Mathf.clamp(0.4f * Time.delta)));
                    }

                    image.clicked(() -> {
                        schematicsSelectDialog.show();
                        contentName = unlock.name;
                    });

                    if((++count) % cols == 0){
                        list.row();
                    }
                }
            }).growX().left().padBottom(10);
            datebaseTable.row();
        }
    }

}
