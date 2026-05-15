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

import c4.conarm.lib.tinkering.TinkersArmor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import javax.annotation.Nonnull;

public class SlotArmorStationOut extends Slot {
    public ContainerArmorStation parent;
    public boolean isArmorForDeconstruction = false;

    public SlotArmorStationOut(final int index, final int xPosition, final int yPosition, final ContainerArmorStation container) {
        super(new InventoryCraftResult(), index, xPosition, yPosition);
        this.parent = container;
    }

    @Override
    public boolean isItemValid(final ItemStack stack) {
        return Config.deconstructTools // config enabled
                && parent.getInputSlotContents().isEmpty() // input slots are empty
                && !stack.isEmpty() && stack.getItem() instanceof TinkersArmor // is armor
                && !stack.isItemDamaged() && !ToolHelper.isBroken(stack) // undamaged
                && parent.getBuildableArmor().contains(stack.getItem()) // can be built in the current table
                && !isSealedArtifact(stack) // is not a sealed artifact
                && hasEnoughXP(stack) // has enough xp
                && hasEnoughLevels(stack) // has enough levels
                && parent.getSelectedArmor() == null; // on the default screen and not an armor building screen or the armor that is built
    }

    @Override
    public void putStack(@Nonnull ItemStack stack) {
        super.putStack(stack);
        // trigger craft matrix update and sync when armor is placed in the output slot
        if(isItemValid(stack)) {
            this.isArmorForDeconstruction = true;
            parent.onCraftMatrixChanged(parent.getTile());
            parent.detectAndSendChanges();
        }
    }

    private boolean isSealedArtifact(ItemStack stack) {
        NBTTagCompound modifierTag = TinkerUtil.getModifierTag(stack, "tconevo.artifact");
        return ModifierNBT.readTag(modifierTag).level == 1;
    }

    private boolean hasEnoughXP(ItemStack stack) {
        NBTTagCompound modifierTag = TinkerUtil.getModifierTag(stack, "toolleveling");
        if(modifierTag.hasKey("xp")) {
            return modifierTag.getInteger("xp") >= Config.deconstructXPRequirement;
        }
        return true;
    }

    private boolean hasEnoughLevels(ItemStack stack) {
        NBTTagCompound modifierTag = TinkerUtil.getModifierTag(stack, "toolleveling");
        if(modifierTag.hasKey("level")) {
            return modifierTag.getInteger("level") >= Config.deconstructLevelRequirement;
        }
        return true;
    }

    @Nonnull
    @Override
    public ItemStack onTake(final EntityPlayer playerIn, @Nonnull final ItemStack stack) {
        FMLCommonHandler.instance().firePlayerCraftingEvent(playerIn, stack, this.parent.getTile());
        this.parent.onResultTaken(playerIn, stack);
        stack.onCrafting(playerIn.getEntityWorld(), playerIn, 1);
        return super.onTake(playerIn, stack);
    }
}
