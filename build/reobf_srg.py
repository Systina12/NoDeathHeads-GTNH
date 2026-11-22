#!/usr/bin/env python3
"""Rewrite Minecraft member references from MCP to 1.7.10 SRG names.

Minecraft 1.7.10 production mods require SRG references for vanilla classes.
OpenBlocks and Forge API references stay unchanged. This tool rewrites just the
compiled mod class, using exact owner/name/descriptor matches.
"""
from pathlib import Path
import struct

CLASS = Path(__file__).resolve().parent / 'classes/dev/gtnh/nodeathheads/NoDeathHeads.class'

REMAP = {
    ('net/minecraft/entity/player/EntityPlayer', 'worldObj', 'Lnet/minecraft/world/World;'): 'field_70170_p',
    ('net/minecraft/world/World', 'isRemote', 'Z'): 'field_72995_K',
    ('net/minecraft/init/Items', 'skull', 'Lnet/minecraft/item/Item;'): 'field_151144_bL',
    ('net/minecraft/entity/item/EntityItem', 'getEntityItem', '()Lnet/minecraft/item/ItemStack;'): 'func_92059_d',
    ('net/minecraft/item/ItemStack', 'getItem', '()Lnet/minecraft/item/Item;'): 'func_77973_b',
    ('net/minecraft/item/ItemStack', 'getItemDamage', '()I'): 'func_77960_j',
    ('net/minecraft/item/ItemStack', 'getTagCompound', '()Lnet/minecraft/nbt/NBTTagCompound;'): 'func_77978_p',
    ('net/minecraft/entity/player/EntityPlayer', 'getGameProfile', '()Lcom/mojang/authlib/GameProfile;'): 'func_146103_bH',
    ('net/minecraft/entity/player/EntityPlayer', 'getCommandSenderName', '()Ljava/lang/String;'): 'func_70005_c_',
    ('net/minecraft/nbt/NBTTagCompound', 'hasKey', '(Ljava/lang/String;)Z'): 'func_74764_b',
    ('net/minecraft/nbt/NBTTagCompound', 'hasKey', '(Ljava/lang/String;I)Z'): 'func_150297_b',
    ('net/minecraft/nbt/NBTTagCompound', 'getString', '(Ljava/lang/String;)Ljava/lang/String;'): 'func_74779_i',
    ('net/minecraft/nbt/NBTTagCompound', 'getCompoundTag', '(Ljava/lang/String;)Lnet/minecraft/nbt/NBTTagCompound;'): 'func_74775_l',
}

def u2(b):
    return struct.unpack('>H', b)[0]

def pack2(i):
    return struct.pack('>H', i)

def reobf(data):
    assert data[:4] == b'\xca\xfe\xba\xbe', 'invalid class file'
    cp_count = u2(data[8:10]); pos = 10
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        start = pos; tag = data[pos]; pos += 1
        if tag == 1:
            size = u2(data[pos:pos+2]); pos += 2
            value = data[pos:pos+size].decode('utf-8'); pos += size
            cp[i] = [tag, value, data[start:pos]]
        elif tag in (3,4):
            pos += 4; cp[i] = [tag,None,data[start:pos]]
        elif tag in (5,6):
            pos += 8; cp[i] = [tag,None,data[start:pos]]
            i += 1
        elif tag in (7,8,16,19,20):
            val = u2(data[pos:pos+2]); pos += 2
            cp[i] = [tag,val,data[start:pos]]
        elif tag in (9,10,11,12,17,18):
            a,b = struct.unpack('>HH',data[pos:pos+4]); pos += 4
            cp[i] = [tag,(a,b),data[start:pos]]
        elif tag == 15:
            pos += 3; cp[i] = [tag,None,data[start:pos]]
        else:
            raise ValueError('unsupported constant pool tag: %d' % tag)
        i += 1
    tail = data[pos:]
    old_cp_size = cp_count
    def add_utf8(s):
        raw=s.encode('utf-8')
        cp.append([1,s,bytes([1])+pack2(len(raw))+raw]); return len(cp)-1
    def add_name_and_type(name_index, desc_index):
        cp.append([12,(name_index,desc_index),bytes([12])+pack2(name_index)+pack2(desc_index)]); return len(cp)-1
    count=0; found=set()
    for index in range(1,old_cp_size):
        entry=cp[index]
        if entry is None or entry[0] not in (9,10,11): continue
        cls_idx,nt_idx=entry[1]
        owner=cp[cp[cls_idx][1]][1]
        name_idx,desc_idx=cp[nt_idx][1]
        key=(owner,cp[name_idx][1],cp[desc_idx][1])
        if key not in REMAP: continue
        new_name_idx=add_utf8(REMAP[key]); new_nt_idx=add_name_and_type(new_name_idx,desc_idx)
        entry[1]=(cls_idx,new_nt_idx)
        entry[2]=bytes([entry[0]])+pack2(cls_idx)+pack2(new_nt_idx)
        count+=1; found.add(key)
    missing=set(REMAP)-found
    assert not missing,'expected refs absent: '+repr(missing)
    result=data[:8]+pack2(len(cp))+b''.join(e[2] for e in cp[1:] if e is not None)+tail
    return result,count

if __name__=='__main__':
    raw=CLASS.read_bytes()
    result,count=reobf(raw)
    CLASS.write_bytes(result)
    print('Reobfuscated',count,'Minecraft field/method references to SRG names')
