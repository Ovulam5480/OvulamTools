package Tools.UI.ShortcutsSchematics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.event.ClickListener;
import arc.scene.event.HandCursorListener;
import arc.scene.ui.*;
import arc.scene.ui.layout.Stack;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.util.regex.Pattern;

import static mindustry.Vars.schematics;

public class SchematicsSelectDialog extends BaseDialog {
    private Runnable rebuild = () -> {};
    private String search = "";
    private TextField searchField;
    private final Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");

    public SchematicsSelectDialog(String type) {
        super("配置快捷蓝图蓝图" + (Mathf.chance(0.2) ? "我知道这个界面真的很丑, 但是能用就行" : ""));
        addCloseButton();
        shown(() -> rebuildSelect(type));
    }

    @Override
    public Dialog show(){
        super.show();

        if(Core.app.isDesktop() && searchField != null){
            Core.scene.setKeyboardFocus(searchField);
        }

        return this;
    }

    void rebuildSelect(String type){
        cont.top();
        cont.clear();

        cont.table(s -> {
            s.left();
            s.image(Icon.zoom);
            searchField = s.field(search, res -> {
                search = res;
                rebuild.run();
            }).growX().get();
        }).fillX().padBottom(4);

        cont.row();

        cont.pane(table -> rebuild = () -> {
            table.clear();

            table.table(schematicTable -> {
                schematicTable.defaults().style(Styles.flatBordert).size(160).pad(20);

                int i = 0;
                String searchString = ignoreSymbols.matcher(search.toLowerCase()).replaceAll("");

                for(Schematic s : schematics.all()){
                    if(!search.isEmpty() && !ignoreSymbols.matcher(s.name().toLowerCase()).replaceAll("").contains(searchString)) continue;

                    int finalI = ++i;

                    Time.runTask(i, () -> {
                        Image image = new Image(schematics.getPreview(s)).setScaling(Scaling.fit);
                        Image background = new Image(Tex.slider);

                        Stack stack = new Stack(background, image);

                        schematicTable.add(stack);

                        ClickListener listener = new ClickListener();
                        image.addListener(listener);
                        if(!Vars.mobile){
                            image.addListener(new HandCursorListener());
                            image.update(() -> image.color.lerp(!listener.isOver() ? Color.lightGray : Color.white, Mathf.clamp(0.4f * Time.delta)));
                        }

                        image.clicked(() -> {
                            Events.fire(new SchematicsSelectEvent(type, s));
                            hide();
                        });

                        image.addListener(new Tooltip(t -> t.background(Tex.button).add(s.name())));

                        if(finalI % 6 == 0)schematicTable.row();
                    });

                }
            });
        });

        rebuild.run();
    }

    public static class SchematicsSelectEvent {
        public String type;
        public Schematic schematic;

        public SchematicsSelectEvent(String type, Schematic schematic) {
            this.type = type;
            this.schematic = schematic;
        }
    }
}