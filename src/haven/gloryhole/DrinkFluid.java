package haven.gloryhole;

import haven.*;
import haven.Window;

import java.awt.*;

public class DrinkFluid implements Runnable {
    WItem drinkFromThis = null;
    GameUI gui;

    public DrinkFluid(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        try {
            drink();
        } catch (InterruptedException e) {

        }
    }


    private void drink() throws InterruptedException {

        Equipory e = gui.getequipory();


        if (drinkFromThis == null) {
            for (Widget w = gui.lchild; w != null; w = w.prev) {
                if (drinkFromThis != null) break;
                if (w instanceof Window) {
                    Window wnd = (Window) w;
                    for (Widget wdg = wnd.lchild; wdg != null; wdg = wdg.prev) {
                        if (drinkFromThis != null) break;
                        if (wdg instanceof Inventory) {
                            for (WItem item : wdg.children(WItem.class)) {
                                if (GHoleUtils.canDrinkFrom(item)) {
                                    drinkFromThis = item;
                                    break;
                                } else if (item.item.contents instanceof Inventory) {
                                    for (WItem nitem : item.item.contents.children(WItem.class)) {
                                        if (GHoleUtils.canDrinkFrom(nitem)) {
                                            drinkFromThis = nitem;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (drinkFromThis == null) {
            for (WItem item : e.slots) {
                if (drinkFromThis != null) break;
                if (item != null && item.item.contents instanceof Inventory) {
                    for (WItem nitem : item.item.contents.children(WItem.class)) {
                        if (GHoleUtils.canDrinkFrom(nitem)) {
                            drinkFromThis = nitem;
                            break;
                        }
                    }
                }
            }
        }

        boolean success = false;
        if (drinkFromThis != null) {
            if (GHoleUtils.petalExists(gui.ui)) {
                int limit = 10;
                int sleep = 10;
                int cycles = 0;
                while (GHoleUtils.petalExists(gui.ui)) {
                    if (cycles >= limit) {
                        if (GHoleUtils.getPetal(gui.ui, "Empty") != null) {
                            GHoleUtils.closeFlowermenu(gui.ui);
                            break;
                        }
                        return;
                    }
                    GHoleUtils.sleep(sleep);
                    cycles += sleep;
                }
            }
            success = sipMode(drinkFromThis);

        }


    }


    private boolean sipMode(WItem drinkFromThis) throws InterruptedException {
        double stamina = GHoleUtils.getStamina(gui.ui);
        int sips = 1;
        for (int i = 0; i < sips; i++) {
            if (!GHoleUtils.canDrinkFrom(drinkFromThis)) {
                return false;
            }
            if (GHoleUtils.petalExists(gui.ui)) {
                int limit = 10;
                int sleep = 10;
                int cycles = 0;
                while (GHoleUtils.petalExists(gui.ui)) {
                    if (cycles >= limit) {

                        return false;
                    }
                    GHoleUtils.sleep(sleep);
                    cycles += sleep;
                }
            }

            GHoleUtils.activateItem(drinkFromThis);

            if (!GHoleUtils.waitForFlowerMenu(gui.ui, 5000)) {
                return false;
            }

            if (GHoleUtils.choosePetal(gui.ui, "Sip"))
                GHoleUtils.waitFlowermenuClose(gui.ui);
            else {
                GHoleUtils.closeFlowermenu(gui.ui);
                return false;
            }

        }
        return true;
    }



    //drink item



}
