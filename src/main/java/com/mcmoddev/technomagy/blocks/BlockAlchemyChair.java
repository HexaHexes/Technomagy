package com.mcmoddev.technomagy.blocks;

import java.util.List;

import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.registry.IOreDictionaryEntry;
import com.mcmoddev.technomagy.Technomagy;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BlockAlchemyChair extends Block implements IMMDObject, IOreDictionaryEntry {

	protected MMDMaterial material;
	protected String oreDictSuffix = "chair";
	
	public BlockAlchemyChair( MMDMaterial material ) {
		super(Material.WOOD);
		this.material = material;
	}

	@Override
	public String getOreDictionaryName() {
		return "chairAlchemy";
	}

	@Override
	public MMDMaterial getMaterial() {
		return this.material;
	}

	@Override
	@Deprecated
	public MMDMaterial getMetalMaterial() {
		return this.material;
	}
	
	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}
	
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
	
	@SubscribeEvent
	public void onInteract(PlayerInteractEvent.RightClickBlock ev) {
		Technomagy.logger.error("Player Interact!");
		EntityPlayer player = ev.getEntityPlayer();
		if( player.getRidingEntity() != null ) {
			// can't sit on something if you're riding something!
			return;
		}
		
		World w = ev.getWorld();
		BlockPos p = ev.getPos();
		

		Vec3d vec = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		double maxDist = 2;
		if((vec.xCoord - player.posX) * (vec.xCoord - player.posX) + (vec.yCoord - player.posY) * (vec.yCoord - player.posY) + (vec.zCoord - player.posZ) * (vec.zCoord - player.posZ) > maxDist * maxDist) {
			return;
		}
		

		IBlockState state = w.getBlockState(p);

		ItemStack rh = player.getHeldItemMainhand();
		ItemStack lh = player.getHeldItemOffhand();
		if(rh != null || lh != null)
			return;

		if(state.getBlock() instanceof BlockAlchemyChair && spaceForSitting(w, p)) {
			List<Chair> chairs = w.getEntitiesWithinAABB(Chair.class, new AxisAlignedBB(p, p.add(1, 1, 1)));

			if(chairs.isEmpty()) {
				Chair ch = new Chair(w, p);
				w.spawnEntity(ch);
				ev.getEntityPlayer().startRiding(ch);
			}
		}		
	}
	
	protected static boolean spaceForSitting(World w, BlockPos p) {
		BlockPos up = p.up();
		IBlockState s = w.getBlockState(up);
		Block b = s.getBlock();
		return b.getCollisionBoundingBox(s, w, up) != null;
	}
		
	public static class Chair extends Entity {
		private World worldObj;
		
		public Chair(World world, BlockPos pos) {
			this(world);
			
			setPosition(pos.getX(), pos.getY(), pos.getZ());
		}
		
		public Chair(World world) {
			super(world);
			worldObj = world;
			
			setSize(0F, 0F);
		}
				
		@Override
		public void onUpdate() {
			super.onUpdate();
			
			BlockPos pos = getPosition();
			
			if( pos != null && !(worldObj.getBlockState(pos).getBlock() instanceof BlockAlchemyChair) || spaceForSitting(worldObj,pos)) {
				setDead();
				return;
			}
			
			List<Entity> p = getPassengers();
			if( p.isEmpty() ) {
				setDead();
			}
			
			for( Entity e : p ) {
				if( e.isSneaking() ) {
					setDead();
				}
			}
		}
		
		@Override
		protected void entityInit() {
			
		}
		
		@Override
		protected void readEntityFromNBT(NBTTagCompound tagCompound) {

		}
		
		@Override
		protected void writeEntityToNBT(NBTTagCompound tagCompound) {

		}

	}
}
