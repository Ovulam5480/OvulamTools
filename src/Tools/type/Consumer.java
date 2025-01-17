package Tools.type;

import arc.util.Nullable;
import mindustry.content.Blocks;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.RegenProjector;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Separator;
import mindustry.world.blocks.units.Reconstructor;

public class Consumer {
    public Block block;
    public @Nullable ItemStack itemStack;
    public @Nullable LiquidStack liquidStack;
    public float consumeTime;

    public Consumer(Block block, ItemStack itemStack) {
        this(block, itemStack, null);
    }

    public Consumer(Block block, LiquidStack liquidStack) {
        this(block, null, liquidStack);
    }

    public Consumer(Block block, ItemStack itemStack, LiquidStack liquidStack) {
        this.block = block;
        this.itemStack = itemStack;
        this.liquidStack = liquidStack;
        this.consumeTime = findConsumeTime(block);
    }

    public float getItemConsumeTime(){
        if(itemStack == null)return 0;
        return itemStack.amount / consumeTime;
    }

    public float getLiquidConsumeTime(){
        if(liquidStack == null)return 0;
        return liquidStack.amount / consumeTime;
    }

    public static float findConsumeTime(Block block) {
        //return block.stats.timePeriod;

        float time = 0;
        if (block instanceof GenericCrafter g) time = g.craftTime;
        else if (block instanceof Separator s) time = s.craftTime;
        else if (block instanceof ConsumeGenerator g) time = g.itemDuration;
        else if (block instanceof NuclearReactor n) time = n.itemDuration;
        else if (block instanceof ImpactReactor i) time = i.itemDuration;
        else if (block instanceof Reconstructor r) time = r.constructTime;
        else if (block instanceof RegenProjector r) time = r.optionalUseTime;
        else if (block instanceof MendProjector m) time = m.useTime;
        else if (block instanceof OverdriveProjector o) time = o.useTime;
        else if (block instanceof ForceProjector f) time = f.phaseUseTime;
        return time;
    }

    public static boolean isConsumer(Block block){
        return findConsumeTime(block) > 0;
    }
}