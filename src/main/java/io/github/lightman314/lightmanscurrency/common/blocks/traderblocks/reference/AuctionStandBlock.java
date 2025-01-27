package io.github.lightman314.lightmanscurrency.common.blocks.traderblocks.reference;

import com.google.common.collect.Lists;
import io.github.lightman314.lightmanscurrency.common.blockentity.AuctionStandBlockEntity;
import io.github.lightman314.lightmanscurrency.api.misc.blocks.IEasyEntityBlock;
import io.github.lightman314.lightmanscurrency.api.misc.blocks.LazyShapes;
import io.github.lightman314.lightmanscurrency.common.menus.validation.types.BlockValidator;
import io.github.lightman314.lightmanscurrency.api.traders.TraderData;
import io.github.lightman314.lightmanscurrency.common.traders.TraderSaveData;
import io.github.lightman314.lightmanscurrency.common.traders.auction.AuctionHouseTrader;
import io.github.lightman314.lightmanscurrency.common.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Collection;

public class AuctionStandBlock extends Block implements IEasyEntityBlock {

    public AuctionStandBlock(Properties properties) { super(properties); }

    @Override
    @Nonnull
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) { return LazyShapes.BOX_T; }

    @Override
    @Nonnull
    @SuppressWarnings("deprecation")
    public InteractionResult use(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand, @Nonnull BlockHitResult result) {
        if(!level.isClientSide && AuctionHouseTrader.isEnabled())
        {
            TraderData ah = TraderSaveData.GetAuctionHouse(false);
            if(ah != null)
                ah.openTraderMenu(player, BlockValidator.of(pos, this));
        }
        return InteractionResult.SUCCESS;
    }

    @Nonnull
    @Override
    public Collection<BlockEntityType<?>> getAllowedTypes() { return Lists.newArrayList(ModBlockEntities.AUCTION_STAND.get()); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) { return new AuctionStandBlockEntity(pos, state); }

}