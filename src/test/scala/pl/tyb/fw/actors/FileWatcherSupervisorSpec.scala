package pl.tyb.fw.actors

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pl.tyb.fw.actors.FileWatcherSupervisor.{CreateWatcher, IsWatched, RemoveWatcher, WatcherInitialized}

class FileWatcherSupervisorSpec extends TestKit(ActorSystem("FileWatcherSupervisorSpec"))
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "FileWatcherSupervisorSpec" should {
    "create new watcher for passed path and confirm it watches that path" in {
      //given
      val expectedPath = "/dummyPath/"
      val probe = TestProbe()
      val supervisor = TestActorRef(FileWatcherSupervisor.props())

      //when
      probe.send(supervisor, CreateWatcher(expectedPath))

      //then
      val initialized = probe.expectMsgClass(classOf[WatcherInitialized])
      initialized.path should equal(expectedPath)

      //when
      probe.send(supervisor, IsWatched(expectedPath))

      //then
      probe.expectMsg(true)
    }

    "return false if the path in query is not watched" in {
      //given
      val probe = TestProbe()
      val supervisor = TestActorRef(FileWatcherSupervisor.props())

      //when
      probe.send(supervisor, IsWatched("/nonWatchedPath/"))

      //then
      probe.expectMsg(false)
    }

    "schedule shutdown of watcher actor and remove path from watched map when received RemoveWatcher" in {
      //given
      val expectedPath = "/dummyPath/"
      val probe = TestProbe()
      val probeCheck = TestProbe()
      val supervisor = TestActorRef(FileWatcherSupervisor.props())
      probe.send(supervisor, CreateWatcher(expectedPath))
      val initialized = probe.expectMsgClass(classOf[WatcherInitialized])
      initialized.path should equal(expectedPath)

      //when
      probe.send(supervisor, RemoveWatcher(expectedPath))
      probeCheck.send(supervisor, IsWatched(expectedPath))

      //then
      probeCheck.expectMsg(false)
    }
  }

}
