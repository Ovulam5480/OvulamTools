package Tools.UI;

import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.type.Planet;

import static mindustry.Vars.*;

public class RulesHintTable {
    public Seq<String> rules = new Seq<>();
    public Seq<UnlockableContent> banBlocks = new Seq<>();
    public Seq<UnlockableContent> banUnits = new Seq<>();
    public Table rulesHint;

    public RulesHintTable(Table parents) {
        rulesHint = parents.table(this::rebuild).margin(10).get();

        Events.on(EventType.WorldLoadEvent.class, e -> {
            rebuild(rulesHint);
        });
    }

    public void rebuild(Table table) {
        table.clear();
        rules.clear();
        banBlocks.clear();
        banUnits.clear();

        print();

        rules.each(s -> table.add(s).left().growX().row());

        if (banBlocks.size > 0) {
            table.add("禁用建筑:").left().growX().row();
            table.table(bbt -> {
                int[] i = {0};
                banBlocks.each(bb -> {
                    bbt.image(bb.fullIcon).size(32f).tooltip(bb.localizedName);
                    if (++i[0] == 12) bbt.row();
                });
            }).left().row();
        }

        if (banUnits.size > 0) {
            table.add("禁用单位:").left().growX().row();
            table.table(but -> {
                int[] i = {0};
                banUnits.each(bu -> {
                    but.image(bu.fullIcon).size(32f).tooltip(bu.localizedName);
                    if (++i[0] == 12) but.row();
                });
            }).left().row();
        }

    }

    public void print() {
        if (state.rules.solarMultiplier != 1) rules.add("太阳能倍率:" + state.rules.solarMultiplier);

        if (state.rules.unitBuildSpeedMultiplier != 1)
            rules.add("单位生产速度倍率:" + state.rules.unitBuildSpeedMultiplier);
        if (state.rules.unitCostMultiplier != 1) rules.add("单位生产花费倍率:" + state.rules.unitCostMultiplier);
        if (state.rules.unitDamageMultiplier != 1) rules.add("单位伤害倍率:" + state.rules.unitDamageMultiplier);
        if (state.rules.unitHealthMultiplier != 1) rules.add("单位生命倍率:" + state.rules.unitHealthMultiplier);

        if (state.rules.blockHealthMultiplier != 1) rules.add("建筑生命倍率:" + state.rules.blockHealthMultiplier);
        if (state.rules.blockDamageMultiplier != 1) rules.add("建筑伤害倍率:" + state.rules.blockDamageMultiplier);
        if (state.rules.buildCostMultiplier != 1) rules.add("建造花费倍率:" + state.rules.buildCostMultiplier);
        if (state.rules.buildSpeedMultiplier != 1) rules.add("建造速度倍率:" + state.rules.buildSpeedMultiplier);
        if (state.rules.deconstructRefundMultiplier != 0.5f)
            rules.add("拆解返还倍率:" + state.rules.deconstructRefundMultiplier);

        if (state.rules.bannedBlocks.size > 0) {
            banBlocks.addAll(state.rules.bannedBlocks);
        }

        if (state.rules.bannedUnits.size > 0) {
            banUnits.addAll(state.rules.bannedUnits);
        }
    }

    public boolean hasLists() {
        return !(rules.isEmpty() && banBlocks.isEmpty() && banUnits.isEmpty());
    }
}
