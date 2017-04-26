package com.mcmoddev.technomagy.entities;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public class TileAlchemicFurnace extends TileEntityLockable implements ITickable, ISidedInventory {

	private ItemStack[] stacks = new ItemStack[3];
	private int currentBurnTime;
	private int totalBurnTime;
	private int currentCookTime;
	private int totalCookTime;
	
	public TileAlchemicFurnace() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public int getSizeInventory() {
		return this.stacks.length;
	}

	@Override
	public ItemStack getStackInSlot(int index) {
		return this.stacks[index];
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {
		return ItemStackHelper.getAndSplit(this.stacks, index, count);
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		return ItemStackHelper.getAndRemove(this.stacks, index);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
        boolean flag = stack != null && stack.isItemEqual(this.stacks[index]) && ItemStack.areItemStackTagsEqual(stack, this.stacks[index]);
        this.stacks[index] = stack;

        if (stack != null && stack.stackSize > this.getInventoryStackLimit())
        {
            stack.stackSize = this.getInventoryStackLimit();
        }

        if (index == 0 && !flag)
        {
            this.totalBurnTime = this.getCookTime(stack);
            this.currentBurnTime = 0;
            this.markDirty();
        }
	}

	public int getCookTime(ItemStack stack) {
		return 200;
	}
	
	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return this.world.getTileEntity(this.pos) != this ? false : player.getDistanceSq((double)this.pos.getX() + 0.5D, (double)this.pos.getY() + 0.5D, (double)this.pos.getZ() + 0.5D) <= 64.0D;	
	}

	@Override
	public void openInventory(EntityPlayer player) {
		// open gui here ?
	}

	@Override
	public void closeInventory(EntityPlayer player) {
		// close gui here ?
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		if( index > 1 ) {
			return false;
		} else if( index == 0 ) {
			// validate that it has a recipe
			return true;
		} else {
			// validate that it is a fuel
			return true;
		}
	}

	@Override
	public int getField(int id) {
        switch (id)
        {
            case 0:
                return this.currentBurnTime;
            case 1:
                return this.currentCookTime;
            case 2:
                return this.totalBurnTime;
            case 3:
                return this.totalCookTime;
            default:
                return 0;
        }
	}

	@Override
	public void setField(int id, int value) {
        switch (id)
        {
            case 0:
                this.currentBurnTime = value;
                break;
            case 1:
                this.currentCookTime = value;
                break;
            case 2:
                this.totalBurnTime = value;
                break;
            case 3:
               this.totalCookTime = value;
               break;
            default:
                return;
        }
        return;
	}

	@Override
	public int getFieldCount() {
		return 4;
	}

	@Override
	public void clear() {
        for (int i = 0; i < this.stacks.length; ++i)
        {
            this.stacks[i] = null;
        }
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasCustomName() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGuiID() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

}
