package Tools.UI;

import Tools.Tools;
import Tools.type.Consumer;
import Tools.type.ItemFlow;
import Tools.type.Producer;
import Tools.ResourcesCalculator;
import arc.func.Boolp;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.consumers.*;

import static mindustry.Vars.content;
import static mindustry.Vars.iconMed;
import static mindustry.gen.Tex.clear;
import static mindustry.gen.Tex.slider;

public class SensorOresTables {
    ScrollPane.ScrollPaneStyle paneStyle = new ScrollPane.ScrollPaneStyle(Styles.smallPane) {{
        hScroll = clear;
        hScrollKnob = slider;
    }};

    int maxHeight = 84;

    Runnable rebuildConsumeFinder, rebuildDrillsTable;
    int previousOres;
    Item previousOre;

    boolean liquidBoost, excludeDistribution;

    Drill clickedDrill;
    OrderedMap<Block, ItemFlow> clickedProducers = new OrderedMap<>();

    Seq<Seq<Consumer>> consumerLists = new Seq<>();

    public SensorOresTables(Table parents, ResourcesCalculator resourcesCalculator) {
        //clickedDrillSpeed为点击过的钻头产出的矿物速度, clickedProducers作为点击过的产出器
        parents.table(dataTable -> dataTable.update(() -> {
            int oreAmount = resourcesCalculator.resourcesTiles.size;
            Item ore = resourcesCalculator.dropItem;
            if (ore == null) return;

            if (previousOres != oreAmount && previousOre != null) {
                dataTable.clear();
                clickedDrill = null;

                previousOres = oreAmount;

                dataTable.table(oreTable -> {
                    oreTable.table(t -> t.image(previousOre.fullIcon).size(36)).marginRight(10);
                    oreTable.add("" + previousOres).grow();

                    //液体加强
                    oreTable.button(new TextureRegionDrawable(Icon.waves),
                            Styles.clearNoneTogglei,
                            46,
                            () -> {
                                liquidBoost = !liquidBoost;
                                rebuildDrillsTable.run();
                                rebuildConsumeFinder.run();
                            }).update(b -> b.setChecked(liquidBoost)).get().resizeImage(iconMed);

                    oreTable.button(new TextureRegionDrawable(Icon.distribution),
                            Styles.clearNoneTogglei,
                            46,
                            () -> {
                                excludeDistribution = !excludeDistribution;
                                rebuildDrillsTable.run();
                                rebuildConsumeFinder.run();
                            }).update(b -> b.setChecked(excludeDistribution)).get().resizeImage(iconMed);

                }).grow().margin(10);

                Seq<Block> drills = content.blocks().select(b -> b instanceof Drill d && d.tier >= previousOre.hardness);

                dataTable.row();

                Table drillsTable = new Table();

                rebuildDrillsTable = () -> {
                    drillsTable.clear();
                    for (Block drill : drills) {
                        if (drill instanceof Drill d) {
                            ButtonGroup<ImageButton> group = new ButtonGroup<>();
                            group.setMinCheckCount(0);

                            addDrillImageButton(drillsTable, d, getDrillSpeed(d), group);
                        }
                    }
                };
                rebuildDrillsTable.run();

                ScrollPane pane = new ScrollPane(drillsTable, paneStyle);
                pane.setScrollingDisabled(false, true);
                pane.setOverscroll(false, false);

                pane.setScrollXForce(drills.size);

                dataTable.add(pane).height(maxHeight).margin(10).left();

            }
            if (previousOre != ore || previousOres != oreAmount) {
                previousOre = ore;
                consumerLists.clear();
                clickedProducers.clear();

                rebuildConsumeFinder.run();
            }
        })).margin(0).row();

        parents.table(consumeFinder -> rebuildConsumeFinder = () -> {
            consumeFinder.clear();
            //如果查询矿物物品改变, 则重置消耗者列表
            consumerLists.clear();

            if (clickedDrill != null) {
                //立即添加该矿物的消耗者列表
                addItemConsumer(previousOre);

                clickedProducers.each((b, i) -> addItemConsumer(i.item));

                int i = 0;
                for (Seq<Consumer> consumerList : consumerLists) {
                    ButtonGroup<ImageButton> group = new ButtonGroup<>();
                    group.setMinCheckCount(0);

                    Table consumerListTable = new Table().left();

                    for (Consumer consumer : consumerList) {
                        addConsumerImageButton(consumerListTable, consumer, i, group);
                    }

                    ScrollPane pane = new ScrollPane(consumerListTable, paneStyle);
                    pane.setScrollingDisabled(false, true);
                    pane.setOverscroll(false, false);

                    pane.setScrollXForce(consumerList.size);

                    consumeFinder.add(pane).height(maxHeight).row();
                    i++;
                }
            }
        }).margin(10);
    }

    public void addDrillImageButton(Table table, Drill drill, float speed, ButtonGroup<ImageButton> group) {
        table.table(t -> {
            ImageButton imageButton = t.button(new TextureRegionDrawable(drill.fullIcon), Styles.clearNoneTogglei, () -> {
                if (clickedDrill == drill) {
                    clickedDrill = null;
                    clickedProducers.clear();
                } else {
                    clickedDrill = drill;
                }

                rebuildConsumeFinder.run();
            }).size(46).group(group).grow().update(b -> b.setChecked(clickedDrill == drill)).get();
            t.row();
            t.add((liquidBoost ? "[cyan]" : "") + getTweDecimal(speed));

            imageButton.resizeImage(iconMed);
        }).top().margin(5);
    }

    public void addConsumerImageButton(Table table, Consumer consumer, int index, ButtonGroup<ImageButton> group) {
        Block block = consumer.block;

        float previousSpeed = getDrillSpeed(clickedDrill);

        int i = 0;
        for (ObjectMap.Entry<Block, ItemFlow> producers : clickedProducers) {
            if (i >= index) break;
            previousSpeed *= producers.value.amount;
            i++;
        }

        float consumerAmount = getTweDecimal(previousSpeed / consumer.getItemConsumeTime() / 60f);

        Producer producer = new Producer(block);
        boolean hasProduction;

        if (Producer.isProducer(block)) hasProduction = producer.selectItem != null;
        else hasProduction = false;

        table.table(t -> {
            ImageButton imageButton = new ImageButton(new TextureRegionDrawable(block.fullIcon), Styles.clearNoneTogglei);
            Boolp hasClicked = () -> index < clickedProducers.size && clickedProducers.orderedKeys().get(index) == block;

            if (hasProduction) {
                imageButton.clicked(() -> {
                    Seq<Block> keys = clickedProducers.orderedKeys().copy();

                    keys.select(b -> keys.indexOf(b) >= index).each(b -> clickedProducers.remove(b));

                    if (!hasClicked.get()) {
                        clickedProducers.put(block, new ItemFlow(producer.selectItem, producer.getItemProductSpeed(producer.selectItem) / consumer.getItemConsumeTime()));
                    }

                    rebuildConsumeFinder.run();
                });
            }

            t.add(imageButton).size(46).group(group).update(b -> b.setChecked(hasClicked.get()));

            imageButton.resizeImage(iconMed);
            t.row();
            t.add((liquidBoost ? "[cyan]" : "") + getTweDecimal(consumerAmount));
        }).margin(5);

    }

    public void addItemConsumer(Item item) {
        Seq<Consumer> consumers = new Seq<>();
        content.blocks().select(Consumer::isConsumer).each(b -> {
            Consume consume;

            consume = Seq.with(b.consumers).find(c -> (c instanceof ConsumeItems ci && Seq.with(ci.items).contains(is -> is.item == item)));
            if (consume != null) {
                ItemStack stack = Seq.with(((ConsumeItems) consume).items).find(itemStack -> itemStack.item == item);
                consumers.addUnique(new Consumer(b, stack));
            }

            consume = Seq.with(b.consumers).find(c -> (c instanceof ConsumeItemFilter cif && cif.filter.get(item)));
            if (consume != null) {
                consumers.addUnique(new Consumer(b, new ItemStack(item, 1)));
            }
        });

        if (!consumers.isEmpty()) consumerLists.add(consumers);
    }

    public void addLiquidConsumer(Liquid liquid) {
        Seq<Consumer> consumers = new Seq<>();
        content.blocks().select(Consumer::isConsumer).each(b -> {
            Consume consume;

            consume = Seq.with(b.consumers).find(c -> (c instanceof ConsumeLiquids cls && Seq.with(cls.liquids).contains(is -> is.liquid == liquid)));
            if (consume != null) {
                LiquidStack stack = Seq.with(((ConsumeLiquids) consume).liquids).find(itemStack -> itemStack.liquid == liquid);
                Consumer consumer = new Consumer(b, stack);

                consumers.addUnique(consumer);
            }

            consume = Seq.with(b.consumers).find(c -> (c instanceof ConsumeLiquid cl && cl.liquid == liquid));
            if (consume != null) {
                LiquidStack stack = new LiquidStack(liquid, ((ConsumeLiquidBase) consume).amount);
                Consumer consumer = new Consumer(b, stack);

                consumers.addUnique(consumer);
            }

            consume = Seq.with(b.consumers).find(c -> (c instanceof ConsumeLiquidFilter clf && clf.filter.get(liquid)));
            if (consume != null) {
                LiquidStack stack = new LiquidStack(liquid, ((ConsumeLiquidBase) consume).amount);
                Consumer consumer = new Consumer(b, stack);

                consumers.addUnique(consumer);
            }
        });

        if (!consumers.isEmpty()) consumerLists.add(consumers);
    }

    public float getDrillSpeed(Drill drill) {
        float boost = liquidBoost ? Mathf.sqr(drill.liquidBoostIntensity) : 1;
        float excludeTiles = excludeDistribution ? Mathf.floor(previousOres / (Mathf.sqr(drill.size) + drill.size / 2f)) * drill.size / 2f : 0;
        return 60f / drill.getDrillTime(previousOre) * (previousOres -excludeTiles) * boost;
    }

    public float getOneDecimal(float f) {
        return (float) Mathf.floor(f * 10) / 10;
    }

    public float getTweDecimal(float f) {
        return (float) Mathf.floor(f * 100) / 100;
    }
}
