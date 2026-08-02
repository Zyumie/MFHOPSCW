package fr.zyumie.shopadmin.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final String iconName;
    private final List<String> iconLore;
    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    public ShopCategory(String id, String name, Material icon, String iconName, List<String> iconLore) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.iconName = iconName;
        this.iconLore = iconLore;
    }

    public void addItem(ShopItem item) { items.put(item.getId(), item); }

    public List<ShopItem> getItemsSorted() {
        return items.values().stream()
                .sorted((a, b) -> Integer.compare(a.getSlot(), b.getSlot()))
                .toList();
    }

    public String getId()            { return id; }
    public String getName()          { return name; }
    public Material getIcon()        { return icon; }
    public String getIconName()      { return iconName; }
    public List<String> getIconLore(){ return iconLore; }
    public Map<String, ShopItem> getItems() { return items; }
}
