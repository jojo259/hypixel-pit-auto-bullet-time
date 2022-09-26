package boats.jojo.autobullettime;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = AutoBulletTime.MODID, version = AutoBulletTime.VERSION)
public class AutoBulletTime
{
    public static final String MODID = "autobullettime";
    public static final String VERSION = "1.0";
    
    Minecraft mcInstance = Minecraft.getMinecraft();
    
    int arrowDetectRange = 32;
	int minBearingDiff = 30;
	int minVerticalDiff = 8;
	
	double blockMilliseconds = 500;
	
	int prevSlot = -1; // slot that player was using before switching to bullet time
	int bulletTimeSwordSlot = -1; // -1 means no bullet time sword found yet
	
	boolean needToBlockTickLater = false;
	boolean needToUnblockAfterBlocking = false;
	
	double incomingArrowDetectedTime = 0;
	double lastCheckedForBulletTime = 0;
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
	public void tickEvent(TickEvent.PlayerTickEvent event) {
    	
    	if (!isInPit()) {
    		return;
    	}
    	
    	// check every second if the player has a bullet time sword (and what slot it is in if they do)
    	
    	if (System.currentTimeMillis() - lastCheckedForBulletTime > 1000) {
    		bulletTimeSwordSlot = getBulletTimeSwordSlot();
    	}
    	
    	if (bulletTimeSwordSlot == -1) {
    		// player doesn't have a bullet time sword, no need to do any logic
    		return;
    	}

    	if (System.currentTimeMillis() - incomingArrowDetectedTime > blockMilliseconds) {
    		// player not currently blocking due to previous arrow
    		
    		if (needToUnblockAfterBlocking) {
    			needToUnblockAfterBlocking = false;
    			KeyBinding.setKeyBindState(mcInstance.gameSettings.keyBindUseItem.getKeyCode(), false);
    			mcInstance.thePlayer.inventory.currentItem = prevSlot;
    		}
    		
    		checkForIncomingArrows();
    	}
	}
    
    public void checkForIncomingArrows() {
    	BlockPos clientPos = mcInstance.thePlayer.getPosition();
    	
    	double clientPosX = clientPos.getX();
    	double clientPosY = clientPos.getY();
    	double clientPosZ = clientPos.getZ();
    	
    	List<EntityArrow> entityList = mcInstance.theWorld.getEntitiesWithinAABB(EntityArrow.class, new AxisAlignedBB(new BlockPos(clientPosX - arrowDetectRange, clientPosY - arrowDetectRange, clientPosZ - arrowDetectRange), new BlockPos(clientPosX + arrowDetectRange, clientPosY + arrowDetectRange, clientPosZ + arrowDetectRange)));
    	
    	List<EntityArrow> arrowsList = entityList
    		.stream()
    		.filter(entity -> // can't find a simple boolean for arrow being in the air
    			entity.prevPosX != entity.posX
    			|| entity.prevPosY != entity.posY
    			|| entity.prevPosZ != entity.posZ
    		)
    		.collect(Collectors.toList());
    	
    	for(int i = 0; i < arrowsList.size(); i++){
    		EntityArrow curArrow = arrowsList.get(i);
    		
    		double curArrowPosY = curArrow.posY;
    		
    		if (Math.abs(curArrowPosY - (clientPosY + 0.5 /* like halfway up the player */)) > minVerticalDiff) {
    			// too far vertically
    			continue;
    		}
    		
    		// 2D bearings - they ignore Y level
    		double arrowMotionBearing = Math.toDegrees(Math.atan2(curArrow.motionZ, curArrow.motionX));
    		double arrowPlayerBearing = Math.toDegrees(Math.atan2(clientPosZ - curArrow.posZ, clientPosX - curArrow.posX));
    		
    		double bearingDiff = Math.abs(arrowMotionBearing - arrowPlayerBearing);
    		
    		if (bearingDiff < minBearingDiff) {
    			// incoming arrow detected
    			System.out.println("incoming arrow detected from bearing " + Math.round(bearingDiff));
    			incomingArrowDetectedTime = System.currentTimeMillis();
				
				needToBlockTickLater = true; // blocking delayed by 1 tick (~50ms) to avoid "using" the wrong item (maybe unnecessary)
    			needToUnblockAfterBlocking = true;
    			
        		KeyBinding.setKeyBindState(mcInstance.gameSettings.keyBindUseItem.getKeyCode(), true);
    			
    			prevSlot = mcInstance.thePlayer.inventory.currentItem;
    			mcInstance.thePlayer.inventory.currentItem = bulletTimeSwordSlot;
    			
    			break; // detected one so break
    		}
    	}
    }
    
    public int getBulletTimeSwordSlot() {
    	for (int atSlot = 0; atSlot < mcInstance.thePlayer.inventoryContainer.getInventory().size(); atSlot++){
    		
    		if (atSlot < 36 || atSlot > 45) {
				// not in hotbar (there is probably a smarter way to do this in the actual for statement)
				continue;
			}
    		
			ItemStack curItem = mcInstance.thePlayer.inventoryContainer.getInventory().get(atSlot);
			
			if (curItem == null) {
				continue;
			}
			
			NBTTagCompound curItemNbt = curItem.getTagCompound();
			
			if (curItemNbt == null) { // maybe not necessary
				continue;
			}
			
			if (!curItemNbt.hasKey("ExtraAttributes")) {
				continue;
			}
			
			NBTTagCompound curItemNbtExtraAttributes = (NBTTagCompound) curItemNbt.getTag("ExtraAttributes");
			
			if (!curItemNbtExtraAttributes.hasKey("CustomEnchants")) {
				continue;
			}
			
			NBTTagList curItemNbtCustomEnchants = curItemNbtExtraAttributes.getTagList("CustomEnchants", 10); // don't know what the 10 does but i had it in some previous code, it says "type"?
			
			for (int p = 0; p < curItemNbtCustomEnchants.tagCount(); p++) {
				
				NBTTagCompound curEnchant = (NBTTagCompound) curItemNbtCustomEnchants.get(p);
				
				String curEnchantKey = curEnchant.getString("Key");
				
				if (curEnchantKey.equals("blocking_cancels_projectiles")) {
					// bullet time enchant found
					return atSlot - 36;
				}
			}
    	}
    	
    	// didn't find a bullet time sword
    	return -1;
    }
    
    public boolean isInPit() {
    	return true;
    }
}
