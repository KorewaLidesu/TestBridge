package testbridge.gui.popup;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;
import java.util.function.Consumer;

public class GuiSelectSatellitePopup extends logisticspipes.gui.popup.GuiSelectSatellitePopup {
  static String GUI_LANG_KEY = "gui.popup.selectsatellite.";
  public GuiSelectSatellitePopup(BlockPos pos, Consumer<UUID> handleResult) {
    super(pos, false, handleResult);
  }
}