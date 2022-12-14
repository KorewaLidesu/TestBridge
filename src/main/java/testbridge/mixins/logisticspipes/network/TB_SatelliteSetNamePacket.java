package testbridge.mixins.logisticspipes.network;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import logisticspipes.network.packets.satpipe.SatelliteSetNamePacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

@Mixin(SatelliteSetNamePacket.class)
public abstract class TB_SatelliteSetNamePacket {
  @Inject(method = "processPacket", at = @At(
      value = "INVOKE_ASSIGN",
      shift = At.Shift.AFTER,
      target = "network/rs485/logisticspipes/SatellitePipe.ensureAllSatelliteStatus()V"),
      locals = LocalCapture.CAPTURE_FAILSOFT)
  private void markPipeDirty(EntityPlayer player, CallbackInfo ci, LogisticsTileGenericPipe pipe) {
    pipe.getTile().markDirty();
  }
}
