package shadows.compatched.tileentity;

import java.awt.Color;

import javax.annotation.Nonnull;

import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import shadows.compatched.CompactRegistry;
import shadows.compatched.inventory.ContainerChest;
import shadows.compatched.inventory.IChest;
import shadows.compatched.inventory.InfoItemHandler;
import shadows.compatched.util.StorageInfo;
import shadows.placebo.recipe.VanillaPacketDispatcher;

public class TileEntityChest extends TileEntity implements IChest, ITickableTileEntity, INamedContainerProvider {

	private Color color = Color.WHITE;
	protected StorageInfo info;
	protected boolean retaining = false;
	private float lidAngle = 0;
	private float prevLidAngle = 0;
	protected int numPlayersUsing = 0;
	protected int ticksSinceSync = 0;
	private InfoItemHandler items;

	public TileEntityChest(StorageInfo info) {
		super(CompactRegistry.CHEST_TILE);
		this.info = info;
		this.items = new InfoItemHandler(info);
	}

	public TileEntityChest() {
		this(new StorageInfo(9, 3, 180, StorageInfo.Type.CHEST));
	}

	@Override
	public boolean receiveClientEvent(int id, int type) {
		if (id == 1) {
			this.numPlayersUsing = type;
			return true;
		} else {
			return super.receiveClientEvent(id, type);
		}
	}

	@Override
	public void onOpened(PlayerEntity player) {
		if (!player.isSpectator()) {
			if (this.numPlayersUsing < 0) {
				this.numPlayersUsing = 0;
			}

			++this.numPlayersUsing;
			this.world.addBlockEvent(this.pos, CompactRegistry.CHEST, 1, this.numPlayersUsing);
			this.world.notifyNeighborsOfStateChange(this.pos, CompactRegistry.CHEST);
		}
	}

	@Override
	public void onClosed(PlayerEntity player) {
		if (!player.isSpectator()) {
			--this.numPlayersUsing;
			this.world.addBlockEvent(this.pos, CompactRegistry.CHEST, 1, this.numPlayersUsing);
			this.world.notifyNeighborsOfStateChange(this.pos, CompactRegistry.CHEST);
		}
	}

	@Override
	@Nonnull
	public CompoundNBT write(CompoundNBT tag) {
		super.write(tag);
		tag.put("info", info.serialize());
		tag.putBoolean("retaining", retaining);
		tag.put("items", getItems().serializeNBT());
		return tag;
	}

	@Override
	public void read(CompoundNBT tag) {
		super.read(tag);
		this.retaining = tag.getBoolean("retaining");
		this.info.deserialize(tag.getCompound("info"));
		this.getItems().deserializeNBT(tag.getCompound("items"));
		this.color = getHue() == -1 ? Color.white : Color.getHSBColor(info.getHue() / 360f, 0.5f, 0.5f);
	}

	@Override
	@Nonnull
	public CompoundNBT getUpdateTag() {
		CompoundNBT tag = super.getUpdateTag();
		tag.put("info", info.serialize());
		tag.putBoolean("retaining", retaining);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundNBT tag) {
		this.retaining = tag.getBoolean("retaining");
		this.info.deserialize(tag.getCompound("info"));
		this.getItems().setSize(info.getSizeX() * info.getSizeY());
		this.color = getHue() == -1 ? Color.white : Color.getHSBColor(info.getHue() / 360f, 0.5f, 0.5f);
	}

	@Override
	public SUpdateTileEntityPacket getUpdatePacket() {
		return new SUpdateTileEntityPacket(pos, -1, getUpdateTag());
	}

	@Override
	public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
		handleUpdateTag(pkt.getNbtCompound());
	}

	public void updateBlock() {
		markDirty();
	}

	@Override
	public void tick() {
		int i = this.pos.getX();
		int j = this.pos.getY();
		int k = this.pos.getZ();
		++this.ticksSinceSync;

		if (!this.world.isRemote && this.numPlayersUsing != 0 && (this.ticksSinceSync + i + j + k) % 200 == 0) {
			this.numPlayersUsing = 0;

			for (PlayerEntity player : this.world.getEntitiesWithinAABB(PlayerEntity.class, new AxisAlignedBB(i - 5.0F, j - 5.0F, k - 5.0F, i + 1 + 5.0F, j + 1 + 5.0F, k + 1 + 5.0F))) {
				if (player.openContainer instanceof ContainerChest) {
					IChest iinventory = ((ContainerChest) player.openContainer).chest;
					if (iinventory == this) ++this.numPlayersUsing;
				}
			}
		}

		this.setPrevLidAngle(this.getLidAngle());

		if (this.numPlayersUsing > 0 && this.getLidAngle() == 0.0F) {
			double d1 = i + 0.5D;
			double d2 = k + 0.5D;

			this.world.playSound(null, d1, j + 0.5D, d2, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
		}

		if (this.numPlayersUsing == 0 && this.getLidAngle() > 0.0F || this.numPlayersUsing > 0 && this.getLidAngle() < 1.0F) {
			float f2 = this.getLidAngle();

			if (this.numPlayersUsing > 0) {
				this.setLidAngle(this.getLidAngle() + 0.1F);
			} else {
				this.setLidAngle(this.getLidAngle() - 0.1F);
			}

			if (this.getLidAngle() > 1.0F) {
				this.setLidAngle(1.0F);
			}

			if (this.getLidAngle() < 0.5F && f2 >= 0.5F) {
				double d3 = i + 0.5D;
				double d0 = k + 0.5D;

				this.world.playSound(null, d3, j + 0.5D, d0, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
			}

			if (this.getLidAngle() < 0.0F) {
				this.setLidAngle(0.0F);
			}
		}
	}

	@Override
	public int getInvX() {
		return this.info.getSizeX();
	}

	@Override
	public int getInvY() {
		return this.info.getSizeY();
	}

	@Override
	public StorageInfo getInfo() {
		return info;
	}

	@Override
	public int getHue() {
		return info.getHue();
	}

	@Override
	public void setHue(int hue) {
		info.setHue(hue);
	}

	LazyOptional<IItemHandler> itemOpt = LazyOptional.of(() -> items);

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap) {
		if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return itemOpt.cast();
		return super.getCapability(cap);
	}

	public Color getColor() {
		return color;
	}

	@Override
	public ItemStackHandler getItems() {
		return items;
	}

	public boolean isRetaining() {
		return this.retaining;
	}

	public void setRetaining(boolean retain) {
		this.retaining = retain;
		VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
	}

	public Direction getDirection() {
		return world.getBlockState(pos).get(HorizontalBlock.HORIZONTAL_FACING);
	}

	public float getLidAngle() {
		return lidAngle;
	}

	public void setLidAngle(float lidAngle) {
		this.lidAngle = lidAngle;
	}

	public float getPrevLidAngle() {
		return prevLidAngle;
	}

	public void setPrevLidAngle(float prevLidAngle) {
		this.prevLidAngle = prevLidAngle;
	}

	@Override
	public Container createMenu(int id, PlayerInventory inv, PlayerEntity player) {
		return new ContainerChest(id, world, this, player, pos);
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TranslationTextComponent(CompactRegistry.CHEST.getTranslationKey());
	}
}
