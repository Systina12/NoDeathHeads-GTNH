from pathlib import Path
root = Path(__file__).resolve().parent / 'stubs/src'
f = {
'cpw/mods/fml/common/Mod.java': '''package cpw.mods.fml.common;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Mod {
 String modid(); String name() default ""; String version() default "";
 String acceptedMinecraftVersions() default "";
 String acceptableRemoteVersions() default "";
 String dependencies() default "";
 @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
 public @interface EventHandler {}
}''',
'cpw/mods/fml/common/event/FMLInitializationEvent.java': '''package cpw.mods.fml.common.event;
public class FMLInitializationEvent {}''',
'cpw/mods/fml/common/eventhandler/EventPriority.java': '''package cpw.mods.fml.common.eventhandler;
public enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }''',
'cpw/mods/fml/common/eventhandler/SubscribeEvent.java': '''package cpw.mods.fml.common.eventhandler;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface SubscribeEvent { EventPriority priority() default EventPriority.NORMAL; boolean receiveCanceled() default false; }''',
'cpw/mods/fml/common/eventhandler/EventBus.java': '''package cpw.mods.fml.common.eventhandler;
public class EventBus { public void register(Object object) {} }''',
'net/minecraftforge/common/MinecraftForge.java': '''package net.minecraftforge.common;
import cpw.mods.fml.common.eventhandler.EventBus;
public class MinecraftForge { public static final EventBus EVENT_BUS = new EventBus(); }''',
'com/mojang/authlib/GameProfile.java': '''package com.mojang.authlib;
import java.util.UUID;
public class GameProfile { private UUID id; private String name;
 public GameProfile(UUID id, String name) {this.id=id; this.name=name;}
 public UUID getId() {return id;} public String getName() {return name;}
}''',
'net/minecraft/world/World.java': '''package net.minecraft.world;
public class World { public boolean isRemote; }''',
'net/minecraft/entity/player/EntityPlayer.java': '''package net.minecraft.entity.player;
import net.minecraft.world.World; import com.mojang.authlib.GameProfile;
public class EntityPlayer {public World worldObj; protected GameProfile profile;
 public EntityPlayer(World world, GameProfile profile){this.worldObj=world;this.profile=profile;}
 public GameProfile getGameProfile(){return profile;}
 public String getCommandSenderName(){return profile.getName();}
}''',
'net/minecraft/item/Item.java': '''package net.minecraft.item;
public class Item {}''',
'net/minecraft/init/Items.java': '''package net.minecraft.init;
import net.minecraft.item.Item;
public class Items {public static final Item skull = new Item(); public static final Item diamond = new Item();}''',
'net/minecraft/nbt/NBTTagCompound.java': '''package net.minecraft.nbt;
import java.util.*;
public class NBTTagCompound {final Map<String,Object> data = new HashMap<>();
 public boolean hasKey(String key){return data.containsKey(key);}
 public boolean hasKey(String key,int type){Object value=data.get(key); return type==8 ? value instanceof String : type==10 && value instanceof NBTTagCompound;}
 public String getString(String key){Object v=data.get(key);return v instanceof String?(String)v:"";}
 public NBTTagCompound getCompoundTag(String key){Object v=data.get(key);return v instanceof NBTTagCompound?(NBTTagCompound)v:new NBTTagCompound();}
 public void setString(String key,String value){data.put(key,value);}
 public void setTag(String key, NBTTagCompound value){data.put(key,value);}
}''',
'net/minecraft/item/ItemStack.java': '''package net.minecraft.item;
import net.minecraft.nbt.NBTTagCompound;
public class ItemStack {private final Item item; private final int meta; private NBTTagCompound tag;
 public ItemStack(Item item, int n, int meta){this.item=item;this.meta=meta;}
 public Item getItem(){return item;} public int getItemDamage(){return meta;}
 public NBTTagCompound getTagCompound(){return tag;}
 public void setTagCompound(NBTTagCompound tag){this.tag=tag;}
}''',
'net/minecraft/entity/item/EntityItem.java': '''package net.minecraft.entity.item;
import net.minecraft.item.ItemStack;
public class EntityItem {private final ItemStack stack;
 public EntityItem(ItemStack stack){this.stack=stack;}
 public ItemStack getEntityItem(){return stack;}
}''',
'openblocks/api/GraveDropsEvent.java': '''package openblocks.api;
import java.util.*; import net.minecraft.entity.item.EntityItem; import net.minecraft.entity.player.EntityPlayer;
public class GraveDropsEvent {
 public enum Action {STORE,DELETE,DROP}
 public static class ItemAction { public final EntityItem item; public Action action;
 public ItemAction(EntityItem item,Action action){this.item=item;this.action=action;} }
 public final EntityPlayer player; public final List<ItemAction> drops=new ArrayList<>();
 public GraveDropsEvent(EntityPlayer player){this.player=player;}
 public void addItem(EntityItem item){drops.add(new ItemAction(item,Action.STORE));}
}''',
'net/minecraftforge/event/entity/player/PlayerDropsEvent.java': '''package net.minecraftforge.event.entity.player;
import java.util.*; import net.minecraft.entity.item.EntityItem; import net.minecraft.entity.player.EntityPlayer;
public class PlayerDropsEvent { public EntityPlayer entityPlayer; public final List<EntityItem> drops=new ArrayList<>();
 public PlayerDropsEvent(EntityPlayer player){this.entityPlayer=player;}
}'''
}
for rel, contents in f.items():
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(contents+'\n')
print(len(f),'stub classes written')
