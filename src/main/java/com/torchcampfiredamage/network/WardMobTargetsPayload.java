package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WardMobTargetsPayload(BlockPos pos,int action,String entityId,boolean enabled) implements CustomPacketPayload {
    public static final Type<WardMobTargetsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID,"ward_mob_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WardMobTargetsPayload> CODEC=new StreamCodec<>() {
        public WardMobTargetsPayload decode(RegistryFriendlyByteBuf b){return new WardMobTargetsPayload(b.readBlockPos(),b.readVarInt(),b.readUtf(256),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,WardMobTargetsPayload p){b.writeBlockPos(p.pos());b.writeVarInt(p.action());b.writeUtf(p.entityId(),256);b.writeBoolean(p.enabled());}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(WardMobTargetsPayload p,IPayloadContext ctx){
        ctx.enqueueWork(()->{
            if(!(ctx.player() instanceof ServerPlayer player)||!(player.level() instanceof ServerLevel level))return;
            if(player.distanceToSqr(p.pos().getX()+0.5,p.pos().getY()+0.5,p.pos().getZ()+0.5)>64.0)return;
            BlockEntity be=level.getBlockEntity(p.pos());
            if(!(be instanceof WardSourceBlockEntity ward))return;
            ResourceLocation id=ResourceLocation.tryParse(p.entityId()); if(id==null)return;
            switch(p.action()){
                case 0 -> ward.setMobTarget(id,p.enabled());
                case 1 -> ward.removeMobTarget(id);
                case 2 -> ward.deleteMobTarget(id);
                case 3 -> ward.discoverMobTarget(id,p.enabled());
                default -> {}
            }
        });
    }
}
