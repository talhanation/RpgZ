package net.rpgz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameterSets;
import net.minecraft.loot.LootTable;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.rpgz.access.IInventoryAccess;
import net.rpgz.forge.config.RPGZConfig;
import net.rpgz.tag.Tags;
import net.rpgz.ui.LivingEntityScreenHandler;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements IInventoryAccess {
	@Shadow
	public int deathTime;
	@Shadow
	public float renderYawOffset;
	@Shadow
	protected int recentlyHit;

	Inventory dropInventory = new Inventory(9);

	public LivingEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "livingTick", at = @At("HEAD"), cancellable = true)
	private void livingTickMixin(CallbackInfo info) {
		LivingEntity livingEntity = (LivingEntity) (Object) this;
		if (this.deathTime > 19 && livingEntity instanceof MobEntity) {
			AxisAlignedBB box = this.getBoundingBox();
			BlockPos blockPos = new BlockPos(box.getCenter().getX(), box.minY, box.getCenter().getZ());
			if (this.world.getBlockState(blockPos).isAir()) {
				if (livingEntity instanceof FlyingEntity) {
					this.setRawPosition(this.getPosX(), this.getPosY() - 0.25D, this.getPosZ());
				} else if (this.getMotion().y > 0) {
					this.setRawPosition(this.getPosX(), this.getPosY() - (this.getMotion().y > 0.8D ? 0.8D : this.getMotion().y),
							this.getPosZ());
				} else if (this.getMotion().y < 0) {
					this.setRawPosition(this.getPosX(), this.getPosY() + (this.getMotion().y < -0.8D ? -0.8D : this.getMotion().y)
							+ (this.getMotion().y > -0.2D ? -0.4D : 0.0D), this.getPosZ());
				} else {
					this.setRawPosition(this.getPosX(), this.getPosY() - 0.1D, this.getPosZ());
				}
			} else
				// Water floating
				if (this.world.containsAnyLiquid(box.offset(0.0D, box.getYSize(), 0.0D))) {
					if (RPGZConfig.surfacing_in_water.get()) {
						this.setRawPosition(this.getPosX(), this.getPosY() + 0.03D, this.getPosZ());

					}
					BlockPos newBlockPos = new BlockPos(box.getCenter().getX(), box.maxY, box.getCenter().getZ());
					if (this.world.getBlockState(newBlockPos).getFluidState().isTagged(FluidTags.LAVA)
							&& this.func_230285_a_(Fluids.LAVA)) {
						this.setRawPosition(this.getPosX(), this.getPosY() + 0.03D, this.getPosZ());
					} else if (this.world.containsAnyLiquid(box.offset(0.0D, -box.getYSize() + (box.getYSize() / 5), 0.0D))
							&& !RPGZConfig.surfacing_in_water.get()) {
						this.setRawPosition(this.getPosX(), this.getPosY() - 0.05D, this.getPosZ());
					}
				}
			info.cancel();
		}

	}

	@Overwrite
	public void onDeathUpdate() {
		++this.deathTime;

		if (this.isBurning() && this.deathTime == 1) {
			this.extinguish();
		}
		if (this.getRidingEntity() != null) {
			this.stopRiding();
		}

		if (this.deathTime >= 20) {
			AxisAlignedBB newBoundingBox = new AxisAlignedBB(this.getPosX() - (this.getWidth() / 3.0F), this.getPosY() - (this.getWidth() / 3.0F),
					this.getPosZ() - (this.getWidth() / 3.0F), this.getPosX() + (this.getWidth() / 1.5F),
					this.getPosY() + (this.getWidth() / 1.5F), this.getPosZ() + (this.getWidth() / 1.5F));
			if ((this.getSize(Pose.STANDING).height < 1.0F
					&& this.getSize(Pose.STANDING).width < 1.0F)
					|| (this.getSize(Pose.STANDING).width
							/ this.getSize(Pose.STANDING).height) > 1.395F) {
				this.setBoundingBox(newBoundingBox);
			} else {
				this.setBoundingBox(newBoundingBox.offset(this.getVectorForRotation(0F, this.renderYawOffset).rotateYaw(-30.0F)));
				// this.setBoundingBox(newBoundingBox.offset(this.getRotationVecClient().rotateYaw(-30.0F)));
				// acceptable solution
			}
			// Chicken always has trouble - not fixable
			// this.checkBlockCollision(); //Doesnt solve problem
			// if (this.isInsideWall()) {} // Doenst work

			AxisAlignedBB box = this.getBoundingBox();
			BlockPos blockPos = new BlockPos(box.minX + 0.001D, box.minY + 0.001D, box.minZ + 0.001D).up();
			BlockPos blockPos2 = new BlockPos(box.maxX - 0.001D, box.maxY - 0.001D, box.maxZ - 0.001D);
			
			// Older method, might be better?
		      // if (this.world.isRegionLoaded(blockPos, blockPos2)) {
		      // if (!world.isClient && !this.inventory.isEmpty()
		      // && (world.getBlockState(blockPos).isFullCube(world, blockPos)
		      // || world.getBlockState(blockPos2).isFullCube(world, blockPos2) ||
		      // this.isBaby()
		      // || (Config.CONFIG.drop_unlooted && this.deathTime >
		      // Config.CONFIG.drop_after_ticks))
		      // || this.getType().isIn(Tags.EXCLUDED_ENTITIES)
		      // ||
		      // Config.CONFIG.excluded_entities.contains(this.getType().toString().replace("entity.",
		      // ""))) {
		      // this.inventory.clearToList().forEach(this::dropStack);
		      // }
		      // }

		      // New method to check if inside block
			AxisAlignedBB checkBox = new AxisAlignedBB(box.maxX, box.maxY, box.maxZ, box.maxX + 0.001D, box.maxY + 0.001D, box.maxZ + 0.001D);
			AxisAlignedBB checkBoxTwo = new AxisAlignedBB(box.minX, box.maxY, box.minZ, box.minX + 0.001D, box.maxY + 0.001D, box.minZ + 0.001D);
			AxisAlignedBB checkBoxThree = new AxisAlignedBB(box.maxX - (box.getXSize() / 3D), box.maxY, box.maxZ - (box.getZSize() / 3D),
		          box.maxX + 0.001D - (box.getXSize() / 3D), box.maxY + 0.001D, box.maxZ + 0.001D - (box.getZSize() / 3D));
			
			if (this.world.isAreaLoaded(blockPos, blockPos2)) {
				if (!world.isRemote && !this.dropInventory.isEmpty()
						&& (((!this.world.getBlockCollisionShapes(this, checkBox).allMatch(VoxelShape::isEmpty)
				                || !this.world.getBlockCollisionShapes(this, checkBoxThree).allMatch(VoxelShape::isEmpty))
				                && (!this.world.getBlockCollisionShapes(this, checkBoxTwo).allMatch(VoxelShape::isEmpty)
				                    || !this.world.getBlockCollisionShapes(this, checkBoxThree).allMatch(VoxelShape::isEmpty)))
				                || this.isChild() || (RPGZConfig.drop_unlooted.get() && this.deathTime > RPGZConfig.drop_after_ticks.get()))
						|| this.getType().isContained(Tags.EXCLUDED_ENTITIES)
						|| RPGZConfig.excluded_entities.get().contains(this.getType().toString().replace("entity.", ""))) {
					this.dropInventory./*clearToList*/func_233543_f_().forEach(this::entityDropItem);
				}

			}
			// world.getClosestPlayer(this,// 1.0D)// !=// null// || Testing purpose
		}

		if ((this.deathTime >= 20 && !this.world.isRemote && this.dropInventory.isEmpty()
				&& RPGZConfig.despawn_immediately_when_empty.get())
				|| (this.deathTime == RPGZConfig.despawn_corps_after_ticks.get())) {
			if (!this.world.isRemote) { // Make sure only on server particle
				this.despawnParticlesServer();
			}

			this.remove((Object) this instanceof net.minecraft.entity.player.ServerPlayerEntity);
		}

	}

	private void despawnParticlesServer() {
		for (int i = 0; i < 20; ++i) {
			double d = this.rand.nextGaussian() * 0.025D;
			double e = this.rand.nextGaussian() * 0.025D;
			double f = this.rand.nextGaussian() * 0.025D;
			double x = MathHelper.nextDouble(rand, this.getBoundingBox().minX - 0.5D, this.getBoundingBox().maxX) + 0.5D;
			double y = MathHelper.nextDouble(rand, this.getBoundingBox().minY, this.getBoundingBox().maxY) + 0.5D;
			double z = MathHelper.nextDouble(rand, this.getBoundingBox().minZ - 0.5D, this.getBoundingBox().maxZ) + 0.5D;
			((ServerWorld) this.world).spawnParticle(ParticleTypes.POOF, x, y, z, 0, d, e, f, 0.01D);
		}
	}

	@Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
	private void dropLootMixin(DamageSource source, boolean causedByPlayer, CallbackInfo info) {
		Entity entity = this;
		if (entity instanceof MobEntity) {
			LootTable lootTable = this.world.getServer().getLootTableManager().getLootTableFromLocation(this.getType().getLootTable());
			LootContext.Builder builder = this.getLootContextBuilder(causedByPlayer, source);
			lootTable.generate(builder.build(LootParameterSets.ENTITY), this::addingInventoryItems);
			info.cancel();
		}

	}

	@Override
	public void addingInventoryItems(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		} else if (this.world.isRemote) {
			return;
		} else {
			this.dropInventory.addItem(stack);
		}
	}

	@Override
	public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d hitPos, Hand hand) {
		if (world.isRemote && this.deathTime > 20) {
			return ActionResultType.SUCCESS;
		} else if (!world.isRemote && this.deathTime > 20 && !this.dropInventory.isEmpty()) {
			player.openContainer(new SimpleNamedContainerProvider(
					(syncId, inv, p) -> new LivingEntityScreenHandler(syncId, p.inventory, this.dropInventory), new StringTextComponent("")));
			return ActionResultType.SUCCESS;
		} else
			return ActionResultType.PASS;
	}

	@Shadow
	protected LootContext.Builder getLootContextBuilder(boolean causedByPlayer, DamageSource source) {
		return (new LootContext.Builder((ServerWorld) this.world));
	}

	@Shadow
	public boolean /*canWalkOnFluid*/func_230285_a_(Fluid fluid) {
		return false;
	}

	@Shadow
	public boolean isChild() {
		return false;
	}

	@Override
	public Inventory getDropsInventory() {
		return dropInventory;
	}

	@Override
	public void setDropsInventory(Inventory inventory) {
		this.dropInventory = inventory;

	}

}