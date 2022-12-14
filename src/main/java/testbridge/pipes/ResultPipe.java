package testbridge.pipes;

import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.gui.hud.HUDSatellite;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.interfaces.ISendRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifierStack;

import network.rs485.logisticspipes.connection.*;
import network.rs485.logisticspipes.pipes.IChassisPipe;
import network.rs485.logisticspipes.SatellitePipe;

import testbridge.core.TestBridge;
import testbridge.network.packets.resultpackethandler.TB_SyncNamePacket;
import testbridge.network.GuiIDs;
import testbridge.client.TB_Textures;

public class ResultPipe extends CoreRoutedPipe implements IHeadUpDisplayRendererProvider, IChangeListener, SatellitePipe, IChassisPipe, ISendRoutedItem {
  public static final Set<ResultPipe> AllResults = Collections.newSetFromMap(new WeakHashMap<>());
  public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
  private final HUDSatellite HUD = new HUDSatellite(this);
  private String resultPipeName = "";

  @Nullable
  private SingleAdjacent pointedAdjacent = null;

  public ResultPipe(Item item) {
    super(item);
    throttleTime = 40;
    _orderItemManager = new LogisticsItemOrderManager(this, this); // null by default when not needed
  }

  // called only on server shutdown
  public static void cleanup() {
    ResultPipe.AllResults.clear();
  }

  @Override
  public TextureType getCenterTexture() {
    return TB_Textures.TESTBRIDGE_RESULT_TEXTURE;
  }

  @Nullable
  @Override
  public LogisticsModule getLogisticsModule() {
    return null;
  }

  @Override
  public ItemSendMode getItemSendMode() {
    return ItemSendMode.Normal;
  }

  @Override
  public void nextOrientation() {}

  @Override
  public IRoutedItem sendStack(@Nonnull ItemStack stack, int destRouterId, @Nonnull SinkReply sinkReply, @Nonnull ItemSendMode itemSendMode, EnumFacing direction) {
    return super.sendStack(stack, destRouterId, sinkReply, itemSendMode, direction);
  }

  @Override
  public void setPointedOrientation(@Nullable EnumFacing dir) {}

  /**
   * Returns the pointed adjacent EnumFacing or null, if this chassis does not have an attached inventory.
   */
  @Nullable
  @Override
  public EnumFacing getPointedOrientation() {
    return null;
  }

  @Nonnull
  protected Adjacent getPointedAdjacentOrNoAdjacent() {
    // for public access, use getAvailableAdjacent()
    if (pointedAdjacent == null) {
      return NoAdjacent.INSTANCE;
    } else {
      return pointedAdjacent;
    }
  }

  @Nonnull
  @Override
  public Adjacent getAvailableAdjacent() {
    return getPointedAdjacentOrNoAdjacent();
  }

  @Override
  protected void updateAdjacentCache() {
    super.updateAdjacentCache();
    final Adjacent adjacent = getAdjacent();
    if (adjacent instanceof SingleAdjacent) {
      pointedAdjacent = ((SingleAdjacent) adjacent);
    } else {
      final SingleAdjacent oldPointedAdjacent = pointedAdjacent;
      SingleAdjacent newPointedAdjacent = null;
      if (oldPointedAdjacent != null) {
        // update pointed adjacent with connection type or reset it
        newPointedAdjacent = adjacent.optionalGet(oldPointedAdjacent.getDir()).map(connectionType -> new SingleAdjacent(this, oldPointedAdjacent.getDir(), connectionType)).orElse(null);
      }
      if (newPointedAdjacent == null) {
        newPointedAdjacent = adjacent.neighbors().entrySet().stream().findAny().map(connectedNeighbor -> new SingleAdjacent(this, connectedNeighbor.getKey().getDirection(), connectedNeighbor.getValue())).orElse(null);
      }
      pointedAdjacent = newPointedAdjacent;
    }
  }

  @Override
  public IInventory getModuleInventory() {
    return null;
  }

  @Override
  public int getChassisSize() {
    return 0;
  }

  @Override
  public void startWatching() {}

  @Override
  public void stopWatching() {}

  @Override
  public IHeadUpDisplayRenderer getRenderer() {
    return HUD;
  }

  @Override
  public void readFromNBT(@Nonnull NBTTagCompound nbttagcompound) {
    super.readFromNBT(nbttagcompound);
    if (nbttagcompound.hasKey("resultid")) {
      int resultId = nbttagcompound.getInteger("resultid");
      this.resultPipeName = Integer.toString(resultId);
    } else {
      this.resultPipeName = nbttagcompound.getString("resultPipeName");
    }
    if (MainProxy.isServer(getWorld())) {
      ensureAllSatelliteStatus();
    }
  }

  @Override
  public void writeToNBT(NBTTagCompound nbttagcompound) {
    nbttagcompound.setString("resultPipeName", this.resultPipeName);
    super.writeToNBT(nbttagcompound);
  }

  @Override
  public void ensureAllSatelliteStatus() {
    if (resultPipeName.isEmpty()) {
      ResultPipe.AllResults.remove(this);
    }
    if (!resultPipeName.isEmpty()) {
      ResultPipe.AllResults.add(this);
    }
  }

  @Override
  public void updateWatchers() {
    CoordinatesPacket packet = PacketHandler.getPacket(TB_SyncNamePacket.class).setString(resultPipeName).setTilePos(this.getContainer());
    MainProxy.sendToPlayerList(packet, localModeWatchers);
    MainProxy.sendPacketToAllWatchingChunk(this.getContainer(), packet);
  }

  @Override
  public void onAllowedRemoval() {
    if (MainProxy.isClient(getWorld())) {
      return;
    }
    ResultPipe.AllResults.remove(this);
  }

  @Override
  public void onWrenchClicked(EntityPlayer entityplayer) {
    // Send the result id when opening gui
    final ModernPacket packet = PacketHandler.getPacket(TB_SyncNamePacket.class).setString(resultPipeName).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
    MainProxy.sendPacketToPlayer(packet, entityplayer);
    entityplayer.openGui(TestBridge.INSTANCE, GuiIDs.ENUM.RESULT_PIPE.ordinal(), getWorld(), getX(), getY(), getZ());
  }

  @Nonnull
  public Set<SatellitePipe> getSatellitesOfType() {
    return Collections.unmodifiableSet(AllResults);
  }

  @Override
  public String getSatellitePipeName() {
    return resultPipeName;
  }

  public void setSatellitePipeName(@Nonnull String resultPipeName) {
    this.resultPipeName = resultPipeName;
  }

  @Nonnull
  @Override
  public List<ItemIdentifierStack> getItemList() {
    return new LinkedList<>();
  }

  @Override
  public void listenedChanged() {}
}
