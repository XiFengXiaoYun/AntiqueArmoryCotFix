/*
 * Copyright (c) 2018-2020 C4
 *
 * This file is part of Construct's Armory, a mod made for Minecraft.
 *
 * Construct's Armory is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Construct's Armory is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Construct's Armory.  If not, see <https://www.gnu.org/licenses/>.
 */

package c4.conarm.common.inventory;

import c4.conarm.client.gui.GuiArmorStation;
import c4.conarm.common.network.ArmorStationSelectionPacket;
import c4.conarm.common.network.ArmorStationTextPacket;
import c4.conarm.common.tileentities.TileArmorStation;
import c4.conarm.lib.ArmoryRegistry;
import c4.conarm.lib.armor.ArmorCore;
import c4.conarm.lib.modifiers.IArmorModifyable;
import c4.conarm.lib.tinkering.ArmorBuilder;
import c4.conarm.lib.tinkering.TinkersArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.StringUtils;
import net.minecraft.world.WorldServer;
import slimeknights.mantle.inventory.BaseContainer;
import slimeknights.mantle.util.ItemStackList;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerNetwork;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.events.TinkerCraftingEvent;
import slimeknights.tconstruct.library.modifiers.TinkerGuiException;
import slimeknights.tconstruct.library.tinkering.IRepairable;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tools.IToolPart;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolBuilder;
import slimeknights.tconstruct.tools.common.inventory.ContainerTinkerStation;

import java.util.List;
import java.util.Set;

public class ContainerArmorStation extends ContainerTinkerStation<TileArmorStation>{

    private final EntityPlayer player;
    protected SlotArmorStationOut out;
    protected ArmorCore selectedArmor;
    protected int activeSlots;
    public String armorName;
    protected boolean isUpgraded;

    public ContainerArmorStation(final InventoryPlayer playerInventory, final TileArmorStation tile, boolean isUpgraded) {
        super(tile);
        this.player = playerInventory.player;
        this.isUpgraded = isUpgraded;
        int i;
        for (i = 0; i < tile.getSizeInventory(); i++) {
            this.addSlotToContainer(new SlotArmorStationIn(tile, i, 0, 0, this));
        }
        this.out = new SlotArmorStationOut(i, 124, 38, this);
        this.addSlotToContainer(out);
        this.addPlayerInventory(playerInventory, 8, 92);
        if (tile.isDeconstructing()) {
            out.inventory.setInventorySlotContents(0, tile.getDeconstructingStack());
        } else {
            onCraftMatrixChanged(playerInventory);
        }
    }

    public ItemStack getResult() {
        return this.out.getStack();
    }

    protected void syncNewContainer(final EntityPlayerMP player) {
        this.activeSlots = (this.tile).getSizeInventory();
        TinkerNetwork.sendTo(new ArmorStationSelectionPacket(null, (this.tile).getSizeInventory()), player);
    }

    protected void syncWithOtherContainer(final BaseContainer<TileArmorStation> otherContainer, final EntityPlayerMP player) {
        this.syncWithOtherContainer((ContainerArmorStation) otherContainer, player);
    }

    protected void syncWithOtherContainer(final ContainerArmorStation otherContainer, final EntityPlayerMP player) {
        this.setArmorSelection(otherContainer.selectedArmor, otherContainer.activeSlots);
        this.setArmorName(otherContainer.armorName);
        TinkerNetwork.sendTo(new ArmorStationSelectionPacket(otherContainer.selectedArmor, otherContainer.activeSlots), player);
        if (otherContainer.armorName != null && !otherContainer.armorName.isEmpty()) {
            TinkerNetwork.sendTo(new ArmorStationTextPacket(otherContainer.armorName), player);
        }
    }

    public void setArmorSelection(final ArmorCore armor, int activeSlots) {
        if (activeSlots > (this.tile).getSizeInventory()) {
            activeSlots = (this.tile).getSizeInventory();
        }
        this.activeSlots = activeSlots;
        this.selectedArmor = armor;
        for (int i = 0; i < (this.tile).getSizeInventory(); ++i) {
            final Slot slot = this.inventorySlots.get(i);
            if (slot instanceof SlotArmorStationIn) {
                final SlotArmorStationIn slotArmorPart = (SlotArmorStationIn) slot;
                slotArmorPart.setRestriction(null);
                if (i >= activeSlots) {
                    slotArmorPart.deactivate();
                }
                else {
                    slotArmorPart.activate();
                    if (armor != null) {
                        final List<PartMaterialType> pmts = armor.getArmorBuildComponents();
                        if (i < pmts.size()) {
                            slotArmorPart.setRestriction(pmts.get(i));
                        }
                    }
                }
                if (this.world.isRemote) {
                    slotArmorPart.updateIcon();
                }
            }
        }
    }

    public void setArmorName(final String name) {
        this.armorName = name;
        if (this.world.isRemote) {
            final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiArmorStation) {
                ((GuiArmorStation)screen).textField.setText(name);
            }
        }
        this.onCraftMatrixChanged(tile);
        if (this.out.getHasStack()) {
            if (name != null && !name.isEmpty()) {
                this.out.inventory.getStackInSlot(0).setStackDisplayName(name);
            }
            else {
                this.out.inventory.getStackInSlot(0).clearCustomName();
            }
        }
    }

    public void onCraftMatrixChanged(final IInventory inventoryIn) {
        this.updateGUI();
        try {
            ItemStack result = this.repairArmor(false);

            if (result.isEmpty()) {
                result = this.replaceArmorParts(false);
            }
            if (result.isEmpty()) {
                result = this.modifyArmor(false);
            }
            if(result.isEmpty()) {
                result = this.renameArmor();
            }
            if (result.isEmpty()) {
                result = this.buildArmor();
            }
            ItemStack outputStack = out.getStack();
            if(!result.isEmpty()) {
                out.inventory.setInventorySlotContents(0, result);
            } else if(Config.deconstructTools && !outputStack.isEmpty() && outputStack.getItem() instanceof TinkersArmor && out.isArmorForDeconstruction) {
                out.isArmorForDeconstruction = false;
                if(deconstructArmor()) {
                    NonNullList<ItemStack> parts = getDeconstructedParts(outputStack);
                    for(int i = 0; i < tile.getSizeInventory() && i < parts.size(); i++) {
                        tile.setInventorySlotContents(i, parts.get(i));
                    }
                } else {
                    for(int i = 0; i < tile.getSizeInventory(); i++) {
                        tile.setInventorySlotContents(i, ItemStack.EMPTY);
                    }
                    out.inventory.setInventorySlotContents(0, outputStack);
                }
                tile.setDeconstructingStack(out.inventory.getStackInSlot(0));
            } else {
                out.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
                tile.setDeconstructingStack(out.inventory.getStackInSlot(0));
            }
            this.updateGUI();
        }
        catch (TinkerGuiException e) {
            this.out.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
            this.error(e.getMessage());
        }
        if (!this.world.isRemote) {
            final WorldServer server = (WorldServer)this.world;
            for (final EntityPlayer player : server.playerEntities) {
                if (player.openContainer != this && player.openContainer instanceof ContainerArmorStation && this.sameGui((ContainerArmorStation)player.openContainer)) {
                    ((ContainerArmorStation)player.openContainer).out.inventory.setInventorySlotContents(0, this.out.getStack());
                }
            }
        }
    }

    public void onResultTaken(final EntityPlayer playerIn, final ItemStack stack) {
        boolean resultTaken = false;
        try {
            if (Config.deconstructTools && this.tile.isDeconstructing()) {
                // clear input slots to remove previewed parts
                for (int i = 0; i < tile.getSizeInventory(); i++) {
                    tile.setInventorySlotContents(i, ItemStack.EMPTY);
                }
                onCraftMatrixChanged(null);
                tile.setDeconstructingStack(out.inventory.getStackInSlot(0));
                return;
            } else {
                resultTaken = (!this.repairArmor(true).isEmpty() || !this.replaceArmorParts(true).isEmpty() || !this.modifyArmor(true).isEmpty() || !this.renameArmor().isEmpty());
            }
        }
        catch (TinkerGuiException e) {
            e.printStackTrace();
        }
        if (resultTaken) {
            this.updateSlotsAfterArmorAction();
        } else {
            try {
                final ItemStack tool = this.buildArmor();
                if (!tool.isEmpty()) {
                    for (int i = 0; i < (this.tile).getSizeInventory(); ++i) {
                        (this.tile).decrStackSize(i, 1);
                    }
                    this.setArmorName("");
                }
            }
            catch (TinkerGuiException e) {
                e.printStackTrace();
            }
        }
        this.onCraftMatrixChanged(null);
        this.playCraftSound(playerIn);
    }

    protected void playCraftSound(EntityPlayer player) {
        Sounds.playSoundForAll(player, Sounds.saw, 0.8f, 0.8f + 0.4f * TConstruct.random.nextFloat());
    }

    private ItemStack repairArmor(final boolean remove) {
        final ItemStack repairable = this.inventorySlots.get(0).getStack();
        if (repairable.isEmpty() || !(repairable.getItem() instanceof IRepairable)) {
            return ItemStack.EMPTY;
        }
        return ToolBuilder.tryRepairTool(this.getInputs(), repairable, remove);
    }

    private ItemStack replaceArmorParts(final boolean remove) throws TinkerGuiException {
        final ItemStack armor = this.inventorySlots.get(0).getStack();
        if (armor.isEmpty() || !(armor.getItem() instanceof TinkersArmor)) {
            return ItemStack.EMPTY;
        }
        final NonNullList<ItemStack> inputs = this.getInputs();
        final ItemStack result = ArmorBuilder.tryReplaceArmorParts(armor, inputs, remove);
        if (!result.isEmpty()) {
            TinkerCraftingEvent.ToolPartReplaceEvent.fireEvent(result, this.player, inputs);
        }
        return result;
    }

    private ItemStack modifyArmor(final boolean remove) throws TinkerGuiException {

        final ItemStack modifyable = this.inventorySlots.get(0).getStack();
        if (modifyable.isEmpty() || !(modifyable.getItem() instanceof IArmorModifyable)) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = ArmorBuilder.tryModifyArmor(this.getInputs(), modifyable, remove);
        if (!result.isEmpty()) {

            if (!isUpgraded) {
                throw new TinkerGuiException(Util.translate("gui.error.modifier_on_station"));
            }

            TinkerCraftingEvent.ToolModifyEvent.fireEvent(result, this.player, modifyable.copy());
        }
        return result;
    }

    private ItemStack renameArmor() throws TinkerGuiException {
        ItemStack armorStack = getArmorStack();

        if(armorStack.isEmpty() ||
                !(armorStack.getItem() instanceof TinkersArmor) ||
                StringUtils.isNullOrEmpty(armorName) ||
                armorStack.getDisplayName().equals(armorName)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = armorStack.copy();
        if(TagUtil.getNoRenameFlag(result)) {
            throw new TinkerGuiException(Util.translate("gui.error.no_rename"));
        }

        result.setStackDisplayName(armorName);

        return result;
    }

    private ItemStack buildArmor() throws TinkerGuiException {
        final NonNullList<ItemStack> input = ItemStackList.withSize((this.tile).getSizeInventory());
        for (int i = 0; i < input.size(); ++i) {
            input.set(i, (this.tile).getStackInSlot(i));
        }
        ItemStack result = ArmorBuilder.tryBuildArmor(input, this.armorName, this.getBuildableArmor());
        if (!result.isEmpty()) {
            if (tile.isDeconstructing()) {
                result = tile.getDeconstructingStack();
            } else {
                TinkerCraftingEvent.ToolCraftingEvent.fireEvent(result, this.player, input);
            }
        }
        return result;
    }

    private ItemStack getArmorStack() {
        return inventorySlots.get(0).getStack();
    }

    protected Set<ArmorCore> getBuildableArmor() {
        return ArmoryRegistry.getArmorCrafting();
    }

    private void updateSlotsAfterArmorAction() {
        (this.tile).setInventorySlotContents(0, ItemStack.EMPTY);
        for (int i = 1; i < (this.tile).getSizeInventory(); ++i) {
            if (!(this.tile).getStackInSlot(i).isEmpty() && (this.tile).getStackInSlot(i).getCount() == 0) {
                (this.tile).setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
    }

    private NonNullList<ItemStack> getInputs() {
        final NonNullList<ItemStack> input = NonNullList.withSize((this.tile).getSizeInventory() - 1, ItemStack.EMPTY);
        for (int i = 1; i < (this.tile).getSizeInventory(); ++i) {
            input.set(i - 1, (this.tile).getStackInSlot(i));
        }
        return input;
    }

    @Override
    public boolean canMergeSlot(final ItemStack stack, final Slot slot) {
        if(slot == this.out || tile.isDeconstructing() && slot instanceof SlotArmorStationIn) {
            return false;
        }
        return super.canMergeSlot(stack, slot);
    }

    public List<ItemStack> getInputSlotContents() {
        NonNullList<ItemStack> contents = NonNullList.create();
        for(Slot slot : inventorySlots) {
            if(slot instanceof SlotArmorStationIn && !slot.getStack().isEmpty()) {
                contents.add(slot.getStack());
            }
        }
        return contents;
    }

    public ArmorCore getSelectedArmor() {
        return selectedArmor;
    }

    private boolean deconstructArmor() throws TinkerGuiException {
        ItemStack armorStack = out.getStack();

        if(this.selectedArmor != null) {
            return false;
        }
        if(armorStack.isEmpty() || !(armorStack.getItem() instanceof TinkersArmor)) {
            return false;
        }

        TinkersArmor armor = (TinkersArmor) armorStack.getItem();
        NBTTagList materials = TagUtil.getBaseMaterialsTagList(armorStack);
        if(materials.tagCount() == 0) {
            return false;
        }

        for(int i = 0; i < activeSlots; i++) {
            if(!tile.getStackInSlot(i).isEmpty()) {
                throw new TinkerGuiException(Util.translate("gui.error.input_slots_not_empty"));
            }
        }

        ArmorCore armorCore = (ArmorCore) armor;
        List<PartMaterialType> requiredComponents = armorCore.getRequiredComponents();
        if(requiredComponents.size() > activeSlots) {
            return false;
        }

        NonNullList<ItemStack> parts = NonNullList.create();
        for(int i = 0; i < materials.tagCount() && i < requiredComponents.size(); i++) {
            String materialId = materials.getStringTagAt(i);
            PartMaterialType pmt = requiredComponents.get(i);
            ItemStack partStack = ItemStack.EMPTY;
            for(IToolPart part : pmt.getPossibleParts()) {
                ItemStack testStack = new ItemStack((Item) part);
                if(testStack.getItem() instanceof IToolPart) {
                    NBTTagCompound nbt = TagUtil.getTagSafe(testStack);
                    nbt.setString("Material", materialId);
                    testStack.setTagCompound(nbt);
                    if(pmt.isValid(testStack)) {
                        partStack = testStack;
                        break;
                    }
                }
            }
            if(partStack.isEmpty()) {
                return false;
            }
            parts.add(partStack);
        }

        // todo: fire deconstruction event

        return true;
    }

    private NonNullList<ItemStack> getDeconstructedParts(ItemStack armorStack) {
        NonNullList<ItemStack> parts = NonNullList.create();
        if(!(armorStack.getItem() instanceof ArmorCore)) {
            return parts;
        }
        ArmorCore armorCore = (ArmorCore) armorStack.getItem();
        NBTTagList materials = TagUtil.getBaseMaterialsTagList(armorStack);
        List<PartMaterialType> requiredComponents = armorCore.getRequiredComponents();
        for(int i = 0; i < materials.tagCount() && i < requiredComponents.size(); i++) {
            String materialId = materials.getStringTagAt(i);
            PartMaterialType pmt = requiredComponents.get(i);
            ItemStack partStack = ItemStack.EMPTY;
            for(IToolPart part : pmt.getPossibleParts()) {
                ItemStack testStack = new ItemStack((Item) part);
                if(testStack.getItem() instanceof IToolPart) {
                    NBTTagCompound nbt = TagUtil.getTagSafe(testStack);
                    nbt.setString("Material", materialId);
                    testStack.setTagCompound(nbt);
                    if(pmt.isValid(testStack)) {
                        partStack = testStack;
                        break;
                    }
                }
            }
            if(!partStack.isEmpty()) {
                parts.add(partStack);
            }
        }
        return parts;
    }
}
