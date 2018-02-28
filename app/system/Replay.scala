package org.vindinium.server
package system

import MongoDB._
import akka.event.slf4j.Logger
import org.joda.time.DateTime
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.Play.current
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Replay(
    _id: String,
    init: Game,
    moves: List[Dir],
    training: Boolean,
    names: List[String],
    finished: Boolean,
    date: DateTime) {

  def games: Enumerator[Game] =
    (Enumerator enumerate moves) &> Enumeratee.scanLeft(init)(Arbiter.replay)

  def id = _id
}

object Replay {

  private val log = Logger(getClass.getTypeName)

  import BSONHandlers._

  def make(game: Game) = Replay(
    _id = game.id,
    init = game,
    moves = Nil,
    training = game.training,
    names = game.names,
    finished = game.finished,
    date = DateTime.now)

  def find(id: String): Future[Option[Replay]] = {
    log.info(s"finding by id `$id`")
    val r = coll.find(BSONDocument("_id" -> id)).one[Replay]
    log.info(s"found by id `$id`")
    r
  }

  def recent(nb: Int): Future[List[Replay]] = {
    log.info(s"getting recent by nb `$nb`")
    val r =coll.find(BSONDocument("training" -> false))
      .sort(BSONDocument("date" -> -1))
      .cursor[Replay].collect[List](nb)
    log.info(s"got recent by nb `$nb`")
    r
  }

  def recentByUserName(name: String, nb: Int): Future[List[Replay]] = {
    log.info(s"getting recent by username `$name` with nb `$nb`")
    val r = coll.find(BSONDocument("training" -> false, "names" -> name))
      .sort(BSONDocument("date" -> -1))
      .cursor[Replay].collect[List](nb)
    log.info(s"got recent by username `$name` with nb `$nb`")
    r
  }

  def addMove(id: String, dir: Dir) = {
    log.info(s"adding move with id '$id' in the direction '$dir'")
    val r = coll.update(
      BSONDocument("_id" -> id),
      BSONDocument("$push" -> BSONDocument("moves" -> dir))
    )
    log.info(s"added move with id '$id' in the direction '$dir'")
    r
  }

  def finish(id: String, moves: Seq[Dir]) = {
    log.info(s"finishing id '$id'")
    val r = coll.update(
      BSONDocument("_id" -> id),
      BSONDocument("$set" -> BSONDocument(
        "finished" -> true,
        "moves" -> moves
      ))
    )
    log.info(s"finished id '$id'")
    r
  }

  def insert(game: Game) = {
    log.info(s"inserting game '${game.id}'")
    val r = coll.insert(make(game))
    log.info(s"inserted game '${game.id}'")
    r
  }

  private val db = play.modules.reactivemongo.ReactiveMongoPlugin.db
  private val coll = db("replay")
}
