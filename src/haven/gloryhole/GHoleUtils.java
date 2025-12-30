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
        if (item == null || item.parent == null || item.item == null) {
            return;
        }
        item.item.wdgmsg("iact", Coord.z, 3);
    }

    public static boolean canDrinkFrom(WItem item) {
        if (item == null || item.parent == null || item.item == null) {
            return false;
        }

        Pattern liquidPattern = Pattern.compile(String.format("[0-9.]+ l of (%s)",
                String.join("|", liquids)), Pattern.CASE_INSENSITIVE);
        ItemInfo.Contents contents = getContents(item);

        if (contents != null && contents.sub != null && contents.content.count >= 0.05) {
            if (item.item.ui == null) {
                return false;
            }

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
        if (item == null || item.item == null) {
            return null;
        }

        try {
            for (ItemInfo info : item.item.info())
                if (info instanceof ItemInfo.Contents)
                    return (ItemInfo.Contents) info;
        } catch (Loading ignored) {
        } catch (NullPointerException ignored) {
            // Widget został usunięty podczas iteracji
        }
        return null;
    }

    public static void waitForFlowerMenu(UI ui) {
        if (ui == null || ui.root == null) {
            return;
        }
        while (ui.root.findchild(FlowerMenu.class) == null) {
            sleep(15);
        }
    }

    public static boolean waitForFlowerMenu(UI ui, int limit) {
        if (ui == null || ui.root == null) {
            return false;
        }

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
        if (ui == null || ui.root == null) {
            return false;
        }

        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null && menu.parent != null) {
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
        if (ui == null || ui.root == null) {
            return;
        }

        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null && menu.parent != null) {
            menu.choose(null);
            menu.destroy();
        }
        while (ui.root.findchild(FlowerMenu.class) != null) {
            sleep(15);
        }
    }

    public static FlowerMenu.Petal getPetal(UI ui, String name) {
        if (ui == null || ui.root == null) {
            return null;
        }

        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        if (menu != null && menu.parent != null) {
            for (FlowerMenu.Petal opt : menu.opts) {
                if (opt.name.equals(name)) {
                    return opt;
                }
            }
        }
        return null;
    }

    public static boolean petalExists(UI ui) {
        if (ui == null || ui.root == null) {
            return false;
        }

        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        return menu != null && menu.parent != null;
    }

    public static void waitFlowermenuClose(UI ui) {
        if (ui == null || ui.root == null) {
            return;
        }

        while (ui.root.findchild(FlowerMenu.class) != null)
            sleep(25);
    }

    public static boolean waitFlowermenuClose(UI ui, int limit) {
        if (ui == null || ui.root == null) {
            return false;
        }

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
        if (ui == null || ui.gui == null) {
            return 0.0;
        }
        try {
            return ui.gui.getmeter("stam", 0).a;
        } catch (NullPointerException e) {
            return 0.0;
        }
    }

    public static Color findHighestTextEntryValueLessThanQ(double q) {
        TextEntry[] textEntries = {OptWnd.q7ColorTextEntry, OptWnd.q6ColorTextEntry, OptWnd.q5ColorTextEntry, OptWnd.q4ColorTextEntry, OptWnd.q3ColorTextEntry, OptWnd.q2ColorTextEntry, OptWnd.q1ColorTextEntry};
        int highestValue = Integer.MIN_VALUE;
        int indexOfHighest = -1;

        for (int i = 0; i < textEntries.length; i++) {
            try {
                int value = Integer.parseInt(textEntries[i].text());
                if (value <= q && value > highestValue) {
                    highestValue = value;
                    indexOfHighest = i;
                }
            } catch (NumberFormatException ignored) {}
        }

        return switch (indexOfHighest) {
            case 0 -> OptWnd.q7ColorOptionWidget.currentColor;
            case 1 -> OptWnd.q6ColorOptionWidget.currentColor;
            case 2 -> OptWnd.q5ColorOptionWidget.currentColor;
            case 3 -> OptWnd.q4ColorOptionWidget.currentColor;
            case 4 -> OptWnd.q3ColorOptionWidget.currentColor;
            case 5 -> OptWnd.q2ColorOptionWidget.currentColor;
            case 6 -> OptWnd.q1ColorOptionWidget.currentColor;
            default -> new Color(255, 255, 255, 255);
        };
    }

}