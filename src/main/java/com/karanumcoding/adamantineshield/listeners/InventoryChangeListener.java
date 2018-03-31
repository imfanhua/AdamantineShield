package com.karanumcoding.adamantineshield.listeners;

import java.util.Date;
import java.util.Optional;

import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.TileEntityTypes;
import org.spongepowered.api.block.tileentity.carrier.Chest;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.item.inventory.AffectSlotEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.*;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.item.inventory.type.CarriedInventory;

import com.karanumcoding.adamantineshield.db.Database;
import com.karanumcoding.adamantineshield.db.queue.InventoryQueueEntry;
import com.karanumcoding.adamantineshield.enums.ActionType;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public class InventoryChangeListener {

	private Database db;
	private boolean logContainers;
	
	public InventoryChangeListener(Database db, boolean logContainers) {
		this.db = db;
		this.logContainers = logContainers;
	}
	
	@Listener
	public void onInventoryTransfer(AffectSlotEvent e, @First Player p) {
		if (!logContainers) return;
		long timestamp = new Date().getTime();
		for (SlotTransaction transaction : e.getTransactions()) addTransferToDB(timestamp, p, transaction);
	}

	private void addTransferToDB(long timestamp, Player p, SlotTransaction transaction) {
		Slot slot = transaction.getSlot();
		if (!(slot.parent() instanceof CarriedInventory)) return;
		CarriedInventory<?> c = (CarriedInventory<?>) slot.parent();

		Location<World> location = null;
		{
			Optional<?> o = c.getCarrier();
			if (o.isPresent()) {
				Object cast = o.get();
				if (cast instanceof BlockCarrier) {
					Optional<TileEntity> tileEntity = ((BlockCarrier) cast).getLocation().getTileEntity();
					if (tileEntity.isPresent()) location = tileEntity.get().getLocation();
				}
			}
		}

		if (location == null) return;
		int containerSize = c.iterator().next().capacity();
		int slotId = transaction.getSlot().getProperty(SlotIndex.class, "slotindex").map(SlotIndex::getValue).orElse(-1);
		if (slotId >= containerSize) return;

		ItemStackSnapshot origItem = transaction.getOriginal();
		ItemStackSnapshot finalItem = transaction.getFinal();
		if (origItem == finalItem) return;

		if (origItem.createGameDictionaryEntry().matches(finalItem.createStack()) &&
				ItemStackComparators.ITEM_DATA.compare(origItem.createStack(), finalItem.createStack()) == 0) {
			if (origItem.getQuantity() > finalItem.getQuantity()) {
				ItemStackSnapshot stack = ItemStack.builder().itemType(origItem.getType())
						.quantity(origItem.getQuantity() - finalItem.getQuantity())
						.build().createSnapshot();
				db.addToQueue(new InventoryQueueEntry(location, slotId, stack, ActionType.CONTAINER_REMOVE, p, timestamp));
			} else if (origItem.getQuantity() < finalItem.getQuantity()) {
				ItemStackSnapshot stack = ItemStack.builder().itemType(origItem.getType())
						.quantity(finalItem.getQuantity() - origItem.getQuantity())
						.build().createSnapshot();
				db.addToQueue(new InventoryQueueEntry(location, slotId, stack, ActionType.CONTAINER_ADD, p, timestamp));
			}
		} else {
			if (origItem.getType() != ItemTypes.NONE) {
				db.addToQueue(new InventoryQueueEntry(location, slotId, origItem, ActionType.CONTAINER_REMOVE, p, timestamp));
			}
			if (finalItem.getType() != ItemTypes.NONE) {
				db.addToQueue(new InventoryQueueEntry(location, slotId, finalItem, ActionType.CONTAINER_ADD, p, timestamp));
			}
		}
	}
	
}
