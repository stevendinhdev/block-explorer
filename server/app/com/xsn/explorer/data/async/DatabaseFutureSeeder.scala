package com.xsn.explorer.data.async

import javax.inject.Inject

import com.alexitc.playsonify.core.FutureApplicationResult
import com.xsn.explorer.data.{DatabaseBlockingSeeder, DatabaseSeeder}
import com.xsn.explorer.executors.DatabaseExecutionContext

import scala.concurrent.Future

class DatabaseFutureSeeder @Inject() (
    blockingSeeder: DatabaseBlockingSeeder)(
    implicit ec: DatabaseExecutionContext)
    extends DatabaseSeeder[FutureApplicationResult] {

  override def newBlock(command: DatabaseSeeder.CreateBlockCommand): FutureApplicationResult[Unit] = Future {
    blockingSeeder.newBlock(command)
  }

  override def replaceBlock(command: DatabaseSeeder.ReplaceBlockCommand): FutureApplicationResult[Unit] = Future {
    blockingSeeder.replaceBlock(command)
  }
}
