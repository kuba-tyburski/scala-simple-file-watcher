package pl.tyb.fw.actors

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._

import akka.actor.{Actor, ActorLogging, Props}
import pl.tyb.fw.actors.FileWatcher.PollEvents

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object FileWatcher {
  def props(pathStr : String) = Props(new FileWatcher(pathStr))

  case object PollEvents
}

class FileWatcher(pathStr: String)/*(implicit val kafkaActor: ActorRef)*/ extends Actor with ActorLogging {
  import pl.tyb.fw.actors.FileWatcherSupervisor.{IsStarted, WatcherInitialized}

  implicit val ec: ExecutionContext = context.system.dispatcher

  private val path = Paths.get(pathStr)
  private val watcher = FileSystems.getDefault.newWatchService()

  def receive: Receive = {
    case IsStarted =>
      path.register(watcher, ENTRY_CREATE, ENTRY_DELETE)
      sender() ! WatcherInitialized(path.toAbsolutePath.toString)
      self ! PollEvents

    case PollEvents =>
      log.info("polling events")
      Try(watcher.take()) match {
        case Success(key) =>
          key.pollEvents().asScala.foreach { event =>
            val kind = event.kind()
            if (kind != OVERFLOW) {
              val pathEvent = event.asInstanceOf[WatchEvent[Path]]
              val path = pathEvent.context()
              log.info(s"Found new file: $path event $kind")
//              kafkaActor ! FileEvent(path, kind)
            }

          }
          key.reset()
        case Failure(_) =>
      }
      context.system.scheduler.scheduleOnce(3 seconds, self, PollEvents)
  }
}
