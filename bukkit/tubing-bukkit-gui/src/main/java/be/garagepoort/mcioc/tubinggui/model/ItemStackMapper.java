package be.garagepoort.mcioc.tubinggui.model;

import be.garagepoort.mcioc.IocBean;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@IocBean
public class ItemStackMapper {

    public static final int LINE_LENGTH = 50;

    private final TextMapper textMapper;

    public ItemStackMapper(TextMapper textMapper) {
        this.textMapper = textMapper;
    }

    public ItemStack map(TubingGuiItem tubingGuiItem, boolean showIds) {
        TubingGuiItemStack tubingGuiItemStack = tubingGuiItem.getTubingGuiItemStack();

        ItemStack itemStack = new ItemStack(tubingGuiItemStack.getMaterial());
        itemStack.setAmount(tubingGuiItemStack.getAmount());

        addName(itemStack, tubingGuiItem, tubingGuiItemStack.getName(), showIds);
        addLore(itemStack, tubingGuiItemStack.getLoreLines(), showIds);

        if (tubingGuiItemStack.isEnchanted()) {
            itemStack.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
            meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    private void addName(ItemStack itemStack, TubingGuiItem tubingGuiItem, TubingGuiText name, boolean showIds) {
        ItemMeta itemMeta = getItemMeta(itemStack);
        if (name.isHidden()) {
            itemMeta.setDisplayName(getId(tubingGuiItem, showIds));
        } else {
            itemMeta.setDisplayName(getId(tubingGuiItem, showIds) + getClasses(tubingGuiItem, showIds) + textMapper.mapText(name).orElse(""));
        }
        itemStack.setItemMeta(itemMeta);
    }

    private ItemMeta getItemMeta(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if(itemMeta == null) {
            // hack if no item meta is set for this material
            itemMeta = Bukkit.getItemFactory().getItemMeta(Material.PAPER);
        }
        return itemMeta;
    }

    private void addLore(ItemStack itemStack, List<TubingGuiText> lore, boolean showIds) {
        ItemMeta itemMeta = getItemMeta(itemStack);
        List<String> original = itemMeta.getLore();
        if (original == null) original = new ArrayList<>();
        for (TubingGuiText itemStackLoreLine : lore) {
            textMapper.mapText(itemStackLoreLine).ifPresent(original::add);
            if (showIds) {
                List<String> idLines = mapGuiId(itemStackLoreLine);
                original.addAll(idLines);
                List<String> classLines = mapGuiClasses(itemStackLoreLine);
                original.addAll(classLines);
            }
        }
        itemMeta.setLore(original);
        itemStack.setItemMeta(itemMeta);
    }

    private List<String> mapGuiId(TubingGuiText itemStackLoreLine) {
        List<String> result = new ArrayList<>();
        String partText = "";
        for (TubingGuiTextPart part : itemStackLoreLine.getParts()) {
            if (part.getId() == null || !part.getId().getId().isPresent()) {
                continue;
            }
            String idText = "&C(" + part.getId().getId().get() + ")";
            partText = parseLoreLine(result, partText, idText);
        }
        if (!partText.isEmpty()) {
            result.add(format(partText));
        }
        return result;
    }

    private List<String> mapGuiClasses(TubingGuiText itemStackLoreLine) {
        List<String> result = new ArrayList<>();
        String partText = "";
        for (TubingGuiTextPart part : itemStackLoreLine.getParts()) {
            if (part.getId() == null || part.getId().getClasses().isEmpty()) {
                continue;
            }
            String classText = "&2(" + String.join(",", part.getId().getClasses()) + ")";
            partText = parseLoreLine(result, partText, classText);
        }
        if (!partText.isEmpty()) {
            result.add(format(partText));
        }
        return result;
    }

    private String parseLoreLine(List<String> result, String partText, String classText) {
        String line = partText + classText;

        if (line.length() > LINE_LENGTH && classText.length() <= LINE_LENGTH) {
            if (!partText.isEmpty()) {
                result.add(format(partText));
            }
            partText = classText;
        } else if (line.length() > LINE_LENGTH) {
            result.add(format(line));
            partText = "";
        } else {
            partText = line;
        }
        return partText;
    }

    private String format(String loreLine) {
        return ChatColor.translateAlternateColorCodes('&', loreLine);
    }

    private String getId(TubingGuiItem tubingGui, boolean showIds) {
        if (!showIds || !tubingGui.getStyleId().isPresent() || !tubingGui.getStyleId().get().getId().isPresent()) {
            return "";
        }
        return format("&C(" + tubingGui.getStyleId().get().getId().get().split("_")[0] + ") ");
    }

    private String getClasses(TubingGuiItem tubingGui, boolean showIds) {
        if (!showIds || !tubingGui.getStyleId().isPresent() || tubingGui.getStyleId().get().getClasses().isEmpty()) {
            return "";
        }
        return format("&2(" + String.join(",", tubingGui.getStyleId().get().getClasses()) + ") ");
    }
}
