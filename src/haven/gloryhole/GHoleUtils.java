package haven.gloryhole;

import haven.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class GHoleUtils {

    public static List<String> liquids = new ArrayList<String>(Arrays.asList("Water", "Milk", "Aurochs Milk", "Cowsmilk", "Sheepsmilk", "Goatsmilk", "Piping Hot Tea", "Tea", "Applejuice", "Pearjuice", "Grapejuice", "Stale grapejuice", "Cider", "Perry", "Wine", "Beer", "Weißbier", "Mead")) {{
        sort(String::compareTo);
    }};

    public static void activateItem(WItem item) {
        item.item.wdgmsg("iact", Coord.z, 3);
    }

    public static boolean canDrinkFrom(WItem item) {
        Pattern liquidPattern = Pattern.compile(String.format("[0-9.]+ l of (%s)",
                //	String.join("|", new String[] { "Water", "Piping Hot Tea", "Tea" }), Pattern.CASE_INSENSITIVE));
                String.join("|", liquids)), Pattern.CASE_INSENSITIVE);
        ItemInfo.Contents contents = getContents(item);
        if (contents != null && contents.sub != null && contents.content.count >= 0.05) {
            synchronized (item.item.ui) {
                for (ItemInfo info : contents.sub) {
                    if (info instanceof ItemInfo.Name) {
                        ItemInfo.Name name = (ItemInfo.Name) info;
                        if (name.str != null) {
                            if (liquidPattern.matcher(name.str.text).matches()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static ItemInfo.Contents getContents(WItem item) {
        try {
            for (ItemInfo info : item.item.info())
                if (info instanceof ItemInfo.Contents)
                    return (ItemInfo.Contents) info;
        } catch (Loading ignored) {
        }
        return null;
    }

    public static void waitForFlowerMenu(UI ui) {
        while (ui.root.findchild(FlowerMenu.class) == null) {
            sleep(15);
        }
    }

    public static boolean waitForFlowerMenu(UI ui, int limit) {
        int cycles = 0;
        int sleep = 10;
        while (ui.root.findchild(FlowerMenu.class) == null) {
            if (cycles == limit) {
                return false;
            } else {
                sleep(sleep);
                cycles += sleep;
            }
        }
        return true;
    }

    public static void sleep(int t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static boolean choosePetal(UI ui, String name) {
        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            for (FlowerMenu.Petal opt : menu.opts) {
                if (opt.name.equals(name)) {
                    menu.choose(opt);
                    menu.destroy();
                    return true;
                }
            }
        }
        return false;
    }

    public static void closeFlowermenu(UI ui) {
        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            menu.choose(null);
            menu.destroy();
        }
        while (ui.root.findchild(FlowerMenu.class) != null) {
            sleep(15);
        }
    }

    public static FlowerMenu.Petal getPetal(UI ui, String name) {
        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            for (FlowerMenu.Petal opt : menu.opts) {
                if (opt.name.equals(name)) {
                    return opt;
                }
            }
        }
        return null;
    }

    public static boolean petalExists(UI ui) {
        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            return true;
        }
        return false;
    }

    public static void waitFlowermenuClose(UI ui) {
        while (ui.root.findchild(FlowerMenu.class) != null)
            sleep(25);
    }

    public static boolean waitFlowermenuClose(UI ui, int limit) {
        int cycles = 0;
        int sleep = 25;
        while (ui.root.findchild(FlowerMenu.class) != null) {
            if (cycles == limit) {
                return false;
            } else {
                sleep(sleep);
                cycles += sleep;
            }
        }
        return true;
    }

    public static double getStamina(UI ui) {
        return ui.gui.getmeter("stam", 0).a;
    }

}
