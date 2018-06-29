package com.xsn.explorer.data

import com.alexitc.playsonify.models._
import com.xsn.explorer.data.anorm.dao.{BlockPostgresDAO, TransactionPostgresDAO}
import com.xsn.explorer.data.anorm.interpreters.FieldOrderingSQLInterpreter
import com.xsn.explorer.data.anorm.{BlockPostgresDataHandler, TransactionPostgresDataHandler}
import com.xsn.explorer.data.common.PostgresDataHandlerSpec
import com.xsn.explorer.errors.TransactionNotFoundError
import com.xsn.explorer.helpers.DataHelper._
import com.xsn.explorer.models._
import com.xsn.explorer.models.fields.TransactionField
import com.xsn.explorer.models.rpc.Block
import org.scalactic.{Bad, Good}
import org.scalatest.BeforeAndAfter

class TransactionPostgresDataHandlerSpec extends PostgresDataHandlerSpec with BeforeAndAfter {

  lazy val dataHandler = new TransactionPostgresDataHandler(database, new TransactionPostgresDAO(new FieldOrderingSQLInterpreter))
  lazy val blockDataHandler = new BlockPostgresDataHandler(database, new BlockPostgresDAO)
  val defaultOrdering = FieldOrdering(TransactionField.Time, OrderingCondition.DescendingOrder)

  val block = Block(
    hash = createBlockhash("ad92f0dcea2fdaa357aac6eab00695cf07b487e34113598909f625c24629c981"),
    previousBlockhash = None,
    nextBlockhash = None,
    merkleRoot = createBlockhash("ad9320dcea2fdaa357aac6eab00695cf07b487e34113598909f625c24629c981"),
    transactions = List.empty,
    confirmations = Confirmations(0),
    size = Size(10),
    height = Height(0),
    version = 0,
    time = 0,
    medianTime = 0,
    nonce = 0,
    bits = "abcdef",
    chainwork = "abcdef",
    difficulty = 12.2,
    tposContract = None
  )

  val inputs = List(
    Transaction.Input(0, None, None),
    Transaction.Input(1, Some(BigDecimal(100)), Some(createAddress("XxQ7j37LfuXgsLd5DZAwFKhT3s2ZMkW85F")))
  )

  val outputs = List(
    Transaction.Output(0, BigDecimal(50), createAddress("XxQ7j37LfuXgsLd5DZAwFKhT3s2ZMkW85F"), None, None),
    Transaction.Output(
      1,
      BigDecimal(150),
      createAddress("Xbh5pJdBNm8J9PxnEmwVcuQKRmZZ7DkpcF"),
      Some(createAddress("XfAATXtkRgCdMTrj2fxHvLsKLLmqAjhEAt")),
      Some(createAddress("XjfNeGJhLgW3egmsZqdbpCNGfysPs7jTNm")))
  )

  val transaction = Transaction(
    createTransactionId("99c51e4fe89466faa734d6207a7ef6115fa1dd33f7156b006fafc6bb85a79eb8"),
    block.hash,
    12312312L,
    Size(1000),
    inputs,
    outputs)

  before {
    blockDataHandler.insert(block)
  }

  "upsert" should {
    "add a new transaction" in {
      val result = dataHandler.upsert(transaction)
      result mustEqual Good(transaction)
    }

    "update an existing transaction" in {
      val newTransaction = transaction.copy(
        time = 2313121L,
        size = Size(2000))

      dataHandler.upsert(transaction).isGood mustEqual true
      val result = dataHandler.upsert(newTransaction)
      result mustEqual Good(newTransaction)
    }
  }

  "delete" should {
    "delete a transaction" in {
      dataHandler.upsert(transaction).isGood mustEqual true
      val result = dataHandler.delete(transaction.id)
      result mustEqual Good(transaction)
    }

    "fail to delete a non-existent transaction" in {
      dataHandler.delete(transaction.id)
      val result = dataHandler.delete(transaction.id)
      result mustEqual Bad(TransactionNotFoundError).accumulating
    }
  }

  "deleteBy blockhash" should {
    "delete the transactions related to a block" in {
      dataHandler.upsert(transaction).isGood mustEqual true

      val result = dataHandler.deleteBy(transaction.blockhash)
      println(result)
      result.isGood mustEqual true
      result.get mustEqual List(transaction)
    }
  }

  "getBy address" should {
    val address = createAddress("XxQ7j37LfuXgsLd5DZAwFKhT3s2ZMkW86F")
    val inputs = List(
      Transaction.Input(0, None, None),
      Transaction.Input(1, Some(BigDecimal(100)), Some(address)),
      Transaction.Input(2, Some(BigDecimal(200)), Some(createAddress("XxQ7j37LfuXgsLD5DZAwFKhT3s2ZMkW86F")))
    )

    val outputs = List(
      Transaction.Output(0, BigDecimal(50), address, None, None),
      Transaction.Output(
        1,
        BigDecimal(250),
        createAddress("Xbh5pJdBNm8J9PxnEmwVcuQKRmZZ7DkpcF"),
        None, None)
    )

    val transaction = Transaction(
      createTransactionId("92c51e4fe89466faa734d6207a7ef6115fa1dd33f7156b006fafc6bb85a79eb8"),
      block.hash,
      12312312L,
      Size(1000),
      inputs,
      outputs)

    val query = PaginatedQuery(Offset(0), Limit(10))

    "find no results" in {
      val expected = PaginatedResult(query.offset, query.limit, Count(0), List.empty)
      val result = dataHandler.getBy(address, query, defaultOrdering)

      result mustEqual Good(expected)
    }

    "find the right values" in {
      val transactionWithValues = TransactionWithValues(
        transaction.id, transaction.blockhash, transaction.time, transaction.size,
        sent = 100,
        received = 50)

      val expected = PaginatedResult(query.offset, query.limit, Count(1), List(transactionWithValues))
      dataHandler.upsert(transaction).isGood mustEqual true

      val result = dataHandler.getBy(address, query, defaultOrdering)
      result mustEqual Good(expected)
    }
  }
}
