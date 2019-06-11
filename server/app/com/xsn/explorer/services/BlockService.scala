package com.xsn.explorer.services

import com.alexitc.playsonify.core.FutureApplicationResult
import com.alexitc.playsonify.core.FutureOr.Implicits.{FutureOps, OrOps}
import com.alexitc.playsonify.models.ordering.OrderingCondition
import com.alexitc.playsonify.models.pagination.{Limit, Offset, PaginatedQuery}
import com.alexitc.playsonify.validators.PaginatedQueryValidator
import com.xsn.explorer.cache.BlockHeaderCache
import com.xsn.explorer.data.async.BlockFutureDataHandler
import com.xsn.explorer.errors.{BlockRewardsNotFoundError, XSNMessageError}
import com.xsn.explorer.models._
import com.xsn.explorer.models.persisted.BlockHeader
import com.xsn.explorer.models.rpc.{Block, TransactionVIN}
import com.xsn.explorer.models.values.{Blockhash, Height}
import com.xsn.explorer.parsers.OrderingConditionParser
import com.xsn.explorer.services.logic.{BlockLogic, TransactionLogic}
import com.xsn.explorer.services.validators._
import javax.inject.Inject
import org.scalactic.{Bad, Good}
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

class BlockService @Inject()(
    xsnService: XSNService,
    blockDataHandler: BlockFutureDataHandler,
    paginatedQueryValidator: PaginatedQueryValidator,
    blockhashValidator: BlockhashValidator,
    blockLogic: BlockLogic,
    transactionLogic: TransactionLogic,
    orderingConditionParser: OrderingConditionParser,
    blockHeaderCache: BlockHeaderCache
)(implicit ec: ExecutionContext) {

  private val maxHeadersPerQuery = 1000

  def getBlockHeaders(
      limit: Limit,
      lastSeenHashString: Option[String],
      orderingConditionString: String
  ): FutureApplicationResult[(WrappedResult[List[BlockHeader]], Boolean)] = {

    val result = for {
      lastSeenHash <- validate(lastSeenHashString, blockhashValidator.validate).toFutureOr
      _ <- paginatedQueryValidator.validate(PaginatedQuery(Offset(0), limit), maxHeadersPerQuery).toFutureOr
      orderingCondition <- orderingConditionParser.parseReuslt(orderingConditionString).toFutureOr

      headers <- blockDataHandler.getHeaders(limit, orderingCondition, lastSeenHash).toFutureOr
      latestBlock <- blockDataHandler.getLatestBlock().toFutureOr
    } yield (WrappedResult(headers), canCacheResult(orderingCondition, limit.int, latestBlock, headers))

    result.toFuture
  }

  def getBlockHeader(blockhashString: String, includeFilter: Boolean): FutureApplicationResult[BlockHeader] = {
    val result = for {
      blockhash <- blockhashValidator.validate(blockhashString).toFutureOr
      header <- blockDataHandler.getHeader(blockhash, includeFilter).toFutureOr
    } yield header

    result.toFuture
  }

  def getBlockHeader(height: Height, includeFilter: Boolean): FutureApplicationResult[BlockHeader] = {
    blockDataHandler.getHeader(height, includeFilter)
  }

  private def canCacheResult(
      ordering: OrderingCondition,
      expectedSize: Int,
      latestKnownBlock: persisted.Block,
      result: List[BlockHeader]
  ): Boolean = {

    ordering == OrderingCondition.AscendingOrder && // from oldest to newest
    result.size == expectedSize && // a complete query
    expectedSize > 0 && // non empty result
    result.lastOption.exists(_.height.int + 20 < latestKnownBlock.height.int) // there are at least 20 more blocks (unlikely to occur rollbacks)
  }

  def getRawBlock(blockhashString: String): FutureApplicationResult[JsValue] = {
    val result = for {
      blockhash <- blockhashValidator.validate(blockhashString).toFutureOr
      block <- xsnService.getRawBlock(blockhash).toFutureOr
    } yield block

    result.toFuture
  }

  def getRawBlock(height: Height): FutureApplicationResult[JsValue] = {
    val result = for {
      blockhash <- xsnService.getBlockhash(height).toFutureOr
      block <- xsnService.getRawBlock(blockhash).toFutureOr
    } yield block

    result.toFuture
  }

  def getDetails(blockhashString: String): FutureApplicationResult[BlockDetails] = {
    val result = for {
      blockhash <- blockhashValidator.validate(blockhashString).toFutureOr
      details <- getDetailsPrivate(blockhash).toFutureOr
    } yield details

    result.toFuture
  }

  def getDetails(height: Height): FutureApplicationResult[BlockDetails] = {
    val result = for {
      blockhash <- xsnService
        .getBlockhash(height)
        .toFutureOr

      details <- getDetailsPrivate(blockhash).toFutureOr
    } yield details

    result.toFuture
  }

  private def getDetailsPrivate(blockhash: Blockhash): FutureApplicationResult[BlockDetails] = {
    val result = for {
      block <- xsnService
        .getBlock(blockhash)
        .toFutureOr

      rewards <- getBlockRewards(block).map {
        case Good(value) => Good(Some(value))
        case Bad(_) => Good(None)
      }.toFutureOr

    } yield BlockDetails(block, rewards)

    result.toFuture
  }

  def getLatestBlocks(): FutureApplicationResult[List[Block]] = {

    /**
     * Temporal workaround to retrieve the latest blocks, they
     * will be retrieved from the database once available.
     */
    val result = for {
      a <- xsnService.getLatestBlock().toFutureOr
      b <- xsnService.getBlock(a.previousBlockhash.get).toFutureOr
      c <- xsnService.getBlock(b.previousBlockhash.get).toFutureOr
      d <- xsnService.getBlock(c.previousBlockhash.get).toFutureOr
      e <- xsnService.getBlock(d.previousBlockhash.get).toFutureOr
      f <- xsnService.getBlock(e.previousBlockhash.get).toFutureOr
      g <- xsnService.getBlock(f.previousBlockhash.get).toFutureOr
      h <- xsnService.getBlock(g.previousBlockhash.get).toFutureOr
      i <- xsnService.getBlock(h.previousBlockhash.get).toFutureOr
      j <- xsnService.getBlock(i.previousBlockhash.get).toFutureOr
    } yield List(a, b, c, d, e, f, g, h, i, j)

    result.toFuture
  }

  def extractionMethod(block: rpc.Block): FutureApplicationResult[BlockExtractionMethod] = {
    if (block.tposContract.isDefined) {
      Future.successful(Good(BlockExtractionMethod.TrustlessProofOfStake))
    } else if (block.transactions.isEmpty) {
      Future.successful(Good(BlockExtractionMethod.ProofOfWork))
    } else {
      isPoS(block).toFutureOr.map {
        case true => BlockExtractionMethod.ProofOfStake
        case false => BlockExtractionMethod.ProofOfWork
      }.toFuture
    }
  }

  def estimateFee(nBlocks: Int): FutureApplicationResult[JsValue] = {
    if (nBlocks >= 1 && nBlocks <= 1000) {
      xsnService.estimateSmartFee(nBlocks)
    } else {
      val error = XSNMessageError("The nBlocks should be between 1 and 1000")
      Future.successful(Bad(error).accumulating)
    }
  }

  private def isPoS(block: rpc.Block): FutureApplicationResult[Boolean] = {
    val result = for {
      coinbaseTxid <- blockLogic.getCoinbase(block).toFutureOr
      coinbase <- xsnService.getTransaction(coinbaseTxid).toFutureOr
    } yield blockLogic.isPoS(block, coinbase)

    result.toFuture
  }

  private def getBlockRewards(block: Block): FutureApplicationResult[BlockRewards] = {
    val result = for {
      method <- extractionMethod(block).toFutureOr

      rewards <- method match {
        case BlockExtractionMethod.ProofOfWork => getPoWBlockRewards(block).toFutureOr
        case BlockExtractionMethod.ProofOfStake => getPoSBlockRewards(block).toFutureOr
        case BlockExtractionMethod.TrustlessProofOfStake => getTPoSBlockRewards(block).toFutureOr
      }
    } yield rewards

    result.toFuture
  }

  private def getPoWBlockRewards(block: Block): FutureApplicationResult[PoWBlockRewards] = {
    val result = for {
      txid <- blockLogic.getCoinbase(block).toFutureOr
      // TODO: handle tx not found
      tx <- xsnService.getTransaction(txid).toFutureOr
      vout <- transactionLogic.getVOUT(0, tx, BlockRewardsNotFoundError).toFutureOr
      address <- transactionLogic.getAddress(vout, BlockRewardsNotFoundError).toFutureOr
    } yield PoWBlockRewards(BlockReward(address, vout.value))

    result.toFuture
  }

  private def getPoSBlockRewards(block: Block): FutureApplicationResult[PoSBlockRewards] = {
    val result = for {
      coinstakeTxId <- blockLogic
        .getCoinstakeTransactionId(block)
        .toFutureOr
      coinstakeTx <- xsnService
        .getTransaction(coinstakeTxId)
        .toFutureOr
      coinstakeTxVIN <- transactionLogic
        .getVIN(coinstakeTx, BlockRewardsNotFoundError)
        .toFutureOr

      previousToCoinstakeTx <- xsnService
        .getTransaction(coinstakeTxVIN.txid)
        .toFutureOr
      previousToCoinstakeVOUT <- transactionLogic
        .getVOUT(coinstakeTxVIN, previousToCoinstakeTx, BlockRewardsNotFoundError)
        .toFutureOr

      coinstakeAddress <- transactionLogic
        .getAddress(previousToCoinstakeVOUT, BlockRewardsNotFoundError)
        .toFutureOr

      rewards <- blockLogic
        .getPoSRewards(coinstakeTx, coinstakeAddress, previousToCoinstakeVOUT.value)
        .toFutureOr
    } yield rewards

    result.toFuture
  }

  private def getTPoSBlockRewards(block: Block): FutureApplicationResult[BlockRewards] = {
    val result = for {
      coinstakeTxId <- blockLogic
        .getCoinstakeTransactionId(block)
        .toFutureOr
      coinstakeTx <- xsnService
        .getTransaction(coinstakeTxId)
        .toFutureOr
      coinstakeTxVIN <- transactionLogic
        .getVIN(coinstakeTx, BlockRewardsNotFoundError)
        .toFutureOr

      coinstakeInput <- getCoinstakeInput(coinstakeTxVIN).toFutureOr

      tposTxId <- blockLogic
        .getTPoSTransactionId(block)
        .toFutureOr
      tposTx <- xsnService
        .getTransaction(tposTxId)
        .toFutureOr

      contract <- blockLogic
        .getTPoSContractDetails(tposTx)
        .toFutureOr

      rewards <- blockLogic
        .getTPoSRewards(coinstakeTx, contract, coinstakeInput)
        .toFutureOr
    } yield rewards

    result.toFuture
  }

  private def getCoinstakeInput(coinstakeTxVIN: TransactionVIN): FutureApplicationResult[BigDecimal] = {
    def loadFromTx = {
      val result = for {
        previousToCoinstakeTx <- xsnService
          .getTransaction(coinstakeTxVIN.txid)
          .toFutureOr
        previousToCoinstakeVOUT <- transactionLogic
          .getVOUT(coinstakeTxVIN, previousToCoinstakeTx, BlockRewardsNotFoundError)
          .toFutureOr
      } yield previousToCoinstakeVOUT.value

      result.toFuture
    }

    coinstakeTxVIN match {
      case TransactionVIN.HasValues(_, _, value, _) => Future.successful(Good(value))
      case _ => loadFromTx
    }
  }
}
