package pl.tyb.fw.actors

import java.io.IOException

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

object FileWatcherSupervisor {
  def props(): Props = Props[FileWatcherSupervisor]

  case class CreateWatcher(path: String)
  case class RemoveWatcher(path: String)

  case class WatcherInitialized(path: String)
  case class WatcherRemoved(path: String)

  case object IsStarted
  case class IsWatched(path: String)

}

class FileWatcherSupervisor extends Actor with ActorLogging {

  import pl.tyb.fw.actors.FileWatcherSupervisor._
  import akka.actor.SupervisorStrategy.{Restart, Stop}

  implicit val timeout = Timeout(5 seconds)

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ex: IOException =>
        log.error(ex, "path not found")
        Stop
      case ex: Exception =>
        log.error(ex, "exception raised")
        Restart
      case e =>
        log.error(e, "unknonw error raised")
        Stop
    }

  override def receive: Receive = receive(Map[String, ActorRef]())

  def receive(watcherMap: Map[String, ActorRef]): Receive = {
    case CreateWatcher(path) =>
      val initialSender = sender()
      val newRef: ActorRef = context.actorOf(FileWatcher.props(path), s"path-watcher-${Random.nextLong()}")
      val future = newRef ? IsStarted
      Try(Await.result(future, timeout.duration).asInstanceOf[WatcherInitialized]) match {
        case Success(watcherInitialized) =>
          context.become(receive(watcherMap + (path -> newRef)))
          initialSender ! watcherInitialized
          log.info(s"Started watching ${watcherInitialized.path}")
        case Failure(ex) =>
          log.error(ex, s"couldn't create watcher for path: $path")
      }

    case RemoveWatcher(path) =>
      log.info(watcherMap.toString())
      watcherMap.get(path) match {
        case Some(watcherRef) =>
          watcherRef ! PoisonPill
          context.become(receive(watcherMap - path))
          log.info(s"Stopped watching $path")
        case None =>
          log.info(s"no actor found for path: $path")
      }

    case IsWatched(path) =>
      sender() ! watcherMap.contains(path)
  }

}
