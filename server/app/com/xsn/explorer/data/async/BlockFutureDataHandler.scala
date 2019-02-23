package com.xsn.explorer.data.async

import com.alexitc.playsonify.core.{FutureApplicationResult, FuturePaginatedResult}
import com.alexitc.playsonify.models.ordering.FieldOrdering
import com.alexitc.playsonify.models.pagination
import com.alexitc.playsonify.models.pagination.PaginatedQuery
import com.xsn.explorer.data.{BlockBlockingDataHandler, BlockDataHandler}
import com.xsn.explorer.executors.DatabaseExecutionContext
import com.xsn.explorer.models.fields.BlockField
import com.xsn.explorer.models.persisted.{Block, BlockHeader}
import com.xsn.explorer.models.values.{Blockhash, Height}
import javax.inject.Inject

import scala.concurrent.Future

class BlockFutureDataHandler @Inject() (
    blockBlockingDataHandler: BlockBlockingDataHandler)(
    implicit ec: DatabaseExecutionContext)
    extends BlockDataHandler[FutureApplicationResult] {

  override def getBy(blockhash: Blockhash): FutureApplicationResult[Block] = Future {
    blockBlockingDataHandler.getBy(blockhash)
  }

  override def getBy(height: Height): FutureApplicationResult[Block] = Future {
    blockBlockingDataHandler.getBy(height)
  }

  override def getBy(paginatedQuery: PaginatedQuery, ordering: FieldOrdering[BlockField]): FuturePaginatedResult[Block] = Future {
    blockBlockingDataHandler.getBy(paginatedQuery, ordering)
  }

  override def delete(blockhash: Blockhash): FutureApplicationResult[Block] = Future {
    blockBlockingDataHandler.delete(blockhash)
  }

  override def getLatestBlock(): FutureApplicationResult[Block] = Future {
    blockBlockingDataHandler.getLatestBlock()
  }

  override def getFirstBlock(): FutureApplicationResult[Block] = Future {
    blockBlockingDataHandler.getFirstBlock()
  }

  override def getHeaders(limit: pagination.Limit, lastSeenHash: Option[Blockhash]): FutureApplicationResult[List[BlockHeader]] = Future {
    blockBlockingDataHandler.getHeaders(limit, lastSeenHash)
  }
}
