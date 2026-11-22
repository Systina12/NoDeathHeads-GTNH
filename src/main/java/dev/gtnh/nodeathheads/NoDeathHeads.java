package dev.gtnh.nodeathheads;

import java.util.Iterator;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import openblocks.api.GraveDropsEvent;

/**
 * Suppresses a player's own death head, while retaining OpenBlocks graves and
 * every other item. Works on a dedicated server without client installation.
 */
@Mod(
    modid = NoDeathHeads.MODID,
    name = "No Death Heads (GTNH)",
    version = NoDeathHeads.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:OpenBlocks"
)
public final class NoDeathHeads {
    public static final String MODID = "nodeathheads";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // OpenBlocks fires this event while deciding which death drops to store.
    // Marking an entry DELETE removes only that entry and keeps the grave intact.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGraveDrops(GraveDropsEvent event) {
        if (event.player == null || event.player.worldObj.isRemote) {
            return;
        }
        for (GraveDropsEvent.ItemAction entry : event.drops) {
            if (entry != null && isOwnDeathHead(event.player, entry.item)) {
                entry.action = GraveDropsEvent.Action.DELETE;
            }
        }
    }

    // Covers normal player death drops when no grave is generated. OpenBlocks
    // processes these at LOW; GraveDropsEvent above handles grave contents.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlayerDrops(PlayerDropsEvent event) {
        if (event.entityPlayer == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        final Iterator<EntityItem> iterator = event.drops.iterator();
        while (iterator.hasNext()) {
            if (isOwnDeathHead(event.entityPlayer, iterator.next())) {
                iterator.remove();
            }
        }
    }

    static boolean isOwnDeathHead(EntityPlayer player, EntityItem dropped) {
        if (dropped == null) return false;
        final ItemStack stack = dropped.getEntityItem();
        if (stack == null || stack.getItem() != Items.skull || stack.getItemDamage() != 3) {
            return false;
        }

        final NBTTagCompound tag = stack.getTagCompound();
        // A player-head item with no owner is also filtered; named heads of
        // OTHER players already in the inventory are preserved.
        if (tag == null || !tag.hasKey("SkullOwner")) return true;

        final GameProfile profile = player.getGameProfile();
        final String playerName = profile != null && profile.getName() != null
            ? profile.getName() : player.getCommandSenderName();
        final UUID playerId = profile != null ? profile.getId() : null;

        if (tag.hasKey("SkullOwner", 8)) {
            final String owner = tag.getString("SkullOwner");
            return owner.isEmpty() || owner.equalsIgnoreCase(playerName);
        }
        if (tag.hasKey("SkullOwner", 10)) {
            final NBTTagCompound owner = tag.getCompoundTag("SkullOwner");
            if (owner.hasKey("Name", 8) && !owner.getString("Name").isEmpty()) {
                return owner.getString("Name").equalsIgnoreCase(playerName);
            }
            if (owner.hasKey("Id", 8) && playerId != null) {
                return playerId.toString().equalsIgnoreCase(owner.getString("Id"));
            }
            return !owner.hasKey("Id") && !owner.hasKey("Name");
        }
        return true;
    }
}
