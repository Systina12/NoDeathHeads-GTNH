package dev.gtnh.nodeathheads;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import openblocks.api.GraveDropsEvent;

public final class NoDeathHeadsTest {
    private static int assertions = 0;
    private static void ok(boolean value, String why) {
        assertions++;
        if (!value) throw new AssertionError(why);
    }
    private static EntityItem head(String owner, boolean compound) {
        ItemStack s = new ItemStack(Items.skull, 1, 3);
        if (owner != null) {
            NBTTagCompound tag = new NBTTagCompound();
            if (compound) {
                NBTTagCompound o = new NBTTagCompound();
                o.setString("Name", owner);
                tag.setTag("SkullOwner", o);
            } else {
                tag.setString("SkullOwner", owner);
            }
            s.setTagCompound(tag);
        }
        return new EntityItem(s);
    }
    public static void main(String[] args) {
        World world = new World();
        world.isRemote = false;
        EntityPlayer player = new EntityPlayer(world,new GameProfile(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "Systina12"));
        NoDeathHeads mod = new NoDeathHeads();
        GraveDropsEvent grave = new GraveDropsEvent(player);
        grave.addItem(head("Systina12", false));
        grave.addItem(head("systina12", true));
        grave.addItem(head(null, false));
        grave.addItem(head("OtherPlayer", false));
        grave.addItem(new EntityItem(new ItemStack(Items.skull,1,1)));
        grave.addItem(new EntityItem(new ItemStack(Items.diamond,1,0)));
        mod.onGraveDrops(grave);
        ok(grave.drops.get(0).action == GraveDropsEvent.Action.DELETE,"own string head is deleted");
        ok(grave.drops.get(1).action == GraveDropsEvent.Action.DELETE,"own compound head is deleted");
        ok(grave.drops.get(2).action == GraveDropsEvent.Action.DELETE,"unnamed head is deleted");
        ok(grave.drops.get(3).action == GraveDropsEvent.Action.STORE,"other player's head is preserved");
        ok(grave.drops.get(4).action == GraveDropsEvent.Action.STORE,"wither skull preserved");
        ok(grave.drops.get(5).action == GraveDropsEvent.Action.STORE,"regular inventory preserved");
        PlayerDropsEvent drops = new PlayerDropsEvent(player);
        drops.drops.add(head("Systina12",false));
        drops.drops.add(head("OtherPlayer",false));
        drops.drops.add(new EntityItem(new ItemStack(Items.diamond,1,0)));
        mod.onPlayerDrops(drops);
        ok(drops.drops.size()==2,"death drops remove only own head");
        ok(drops.drops.get(0).getEntityItem().getItem()==Items.skull,"other player's head remains");
        ok(drops.drops.get(1).getEntityItem().getItem()==Items.diamond,"diamond remains");
        world.isRemote=true;
        GraveDropsEvent clientGrave = new GraveDropsEvent(player);
        clientGrave.addItem(head("Systina12",false));
        mod.onGraveDrops(clientGrave);
        ok(clientGrave.drops.get(0).action==GraveDropsEvent.Action.STORE,"client event ignored");
        System.out.println("PASS: " + assertions + " checks");
    }
}
