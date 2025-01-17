package Tools.type;

import arc.struct.Seq;
import arc.util.Nullable;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Separator;

public class Producer {
    public Block block;
    public Seq<ItemFlow> itemStacks = new Seq<>();
    public Seq<LiquidStack> liquidStacks = new Seq<>();

    public @Nullable Item selectItem;
    public float producerTime;

    public Producer(Block block) {
        this.block = block;
        addItemStacks();
        this.producerTime = findProduceTime(block);
    }

    public float getItemProductSpeed(Item item){
        return itemStacks.find(itemFlow -> itemFlow.item == item).amount / producerTime;
    }

    public float getLiquidProductSpeed(Liquid liquid){
        LiquidStack liquidStack = liquidStacks.find(ls -> ls.liquid == liquid);
        if(liquidStack == null)return 0;
        return liquidStack.amount / producerTime;
    }

    //只管普通工厂和分离机, 只考虑物品
    public void addItemStacks(){
        if (block instanceof GenericCrafter g && g.outputItems != null){
            Seq.with(g.outputItems).each(stack -> itemStacks.add(new ItemFlow(stack.item, stack.amount)));
        }else if(block instanceof Separator s){
            Seq<ItemStack> stacks =  Seq.with(s.results);
            int total = stacks.sum(itemStack -> itemStack.amount);

            stacks.each(stack -> itemStacks.add(new ItemFlow(stack.item, (float) stack.amount / total)));
        }

        if(itemStacks.size != 0)selectItem = itemStacks.first().item;
    }

    public void addLiquidStacks(){
        if (block instanceof GenericCrafter g && g.outputLiquid != null){
            liquidStacks.add(g.outputLiquids);
        }else if(block instanceof ConsumeGenerator c && c.outputLiquid != null){
            liquidStacks.add(c.outputLiquid);
        }
    }

    public static float findProduceTime(Block block) {
        float time = 0;
        if (block instanceof GenericCrafter g) time = g.craftTime;
        else if (block instanceof Separator s) time = s.craftTime;
        return time;
    }

    public static boolean isProducer(Block block){
        return findProduceTime(block) > 0;
    }
}
