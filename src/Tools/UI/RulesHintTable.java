package Tools.UI;

import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;

import static mindustry.Vars.*;

public class RulesHintTable {
    public Seq<String> rules = new Seq<>();
    public Seq<UnlockableContent> banBlocks = new Seq<>();
    public Seq<UnlockableContent> banUnits = new Seq<>();
    public Table rulesHint;

    public RulesHintTable(Table parents) {
        rulesHint = parents.table(this::reprint).margin(10).get();

        Events.on(EventType.WorldLoadEvent.class, e -> {
            reprint(rulesHint);
        });
    }

    public void reprint(Table table) {
        table.clear();
        rules.clear();
        banBlocks.clear();
        banUnits.clear();

        printMulti();
        rules.each(s -> table.add(s).left().growX().row());

        if (state.rules.bannedBlocks.size > 0) {
            banBlocks.addAll(state.rules.bannedBlocks);
        }

        if (state.rules.bannedUnits.size > 0) {
            banUnits.addAll(state.rules.bannedUnits);
        }

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

    public void printMulti() {
        register("单位生产速度倍率", state.rules.unitBuildSpeedMultiplier, 1);
        register("单位生产花费倍率", state.rules.unitCostMultiplier, 1);
        register("单位伤害倍率", state.rules.unitDamageMultiplier, 1);
        register("单位生命倍率", state.rules.unitHealthMultiplier, 1);
        register("建筑生命倍率", state.rules.blockHealthMultiplier, 1);
        register("建筑伤害倍率", state.rules.blockDamageMultiplier, 1);
        register("建造花费倍率", state.rules.buildCostMultiplier, 1);
        register("建造速度倍率", state.rules.buildSpeedMultiplier, 1);
        register("太阳能倍率", state.rules.solarMultiplier, 1);
        register("拆解返还倍率", state.rules.deconstructRefundMultiplier, 0.5f);
    }

    public void register(String name, float value, float defaultValue){
        if(value != defaultValue){
            rules.add(name + ":" + value);
        }
    }

    public boolean hasLists() {
        return !(rules.isEmpty() && banBlocks.isEmpty() && banUnits.isEmpty());
    }
}
