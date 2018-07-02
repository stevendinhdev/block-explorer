package com.xsn.explorer.data.anorm

import javax.inject.Inject

import com.alexitc.playsonify.core.ApplicationResult
import com.alexitc.playsonify.models.{FieldOrdering, PaginatedQuery, PaginatedResult}
import com.xsn.explorer.data.TransactionBlockingDataHandler
import com.xsn.explorer.data.anorm.dao.TransactionPostgresDAO
import com.xsn.explorer.models._
import com.xsn.explorer.models.fields.TransactionField
import org.scalactic.Good
import play.api.db.Database

class TransactionPostgresDataHandler @Inject() (
    override val database: Database,
    transactionPostgresDAO: TransactionPostgresDAO)
    extends TransactionBlockingDataHandler
        with AnormPostgresDataHandler {

  override def getBy(
      address: Address,
      paginatedQuery: PaginatedQuery,
      ordering: FieldOrdering[TransactionField]): ApplicationResult[PaginatedResult[TransactionWithValues]] = withConnection { implicit conn =>

    val transactions = transactionPostgresDAO.getBy(address, paginatedQuery, ordering)
    val total = transactionPostgresDAO.countBy(address)
    val result = PaginatedResult(paginatedQuery.offset, paginatedQuery.limit, total, transactions)

    Good(result)
  }
}
