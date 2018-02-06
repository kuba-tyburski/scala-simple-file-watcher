package pl.tyb.fw

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.util.Timeout
import pl.tyb.fw.actors.FileWatcherSupervisor
import pl.tyb.fw.actors.FileWatcherSupervisor.{CreateWatcher, RemoveWatcher}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object Main {

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("FW")

    val fileWatcherSupervisor = actorSystem.actorOf(FileWatcherSupervisor.props())

    val path = Paths.get("./").toAbsolutePath
    fileWatcherSupervisor ! CreateWatcher(path.toString)
    //non-existing path - watcher won't be created, supervisor will not restart, nor escalate problem
    fileWatcherSupervisor ! CreateWatcher(path.toString+"asdfasdf")

    Thread.sleep(1000)

    //spotted files
    val pathToFile1 = Files.createFile(new File(path.toFile, s"new${Random.nextLong()}.txt").toPath)
    val pathToFile2 = Files.createFile(new File(path.toFile, s"asdf${Random.nextLong()}.txt").toPath)
    val pathToFile3 = Files.createFile(new File(path.toFile, s"qwer${Random.nextLong()}.txt").toPath)

    Thread.sleep(10000)

    pathToFile1.toFile.delete()
    pathToFile2.toFile.delete()
    pathToFile3.toFile.delete()

    Thread.sleep(3000)

    fileWatcherSupervisor ! RemoveWatcher(path.toString)

    Thread.sleep(1000)

    //not spotted file
    val pathToFile4 = Files.createFile(new File(path.toFile, s"qwer${Random.nextLong()}.txt").toPath)

    Thread.sleep(4000)

    pathToFile4.toFile.delete()

    Await.result(actorSystem.terminate(), Timeout(5 seconds).duration)
  }

}
