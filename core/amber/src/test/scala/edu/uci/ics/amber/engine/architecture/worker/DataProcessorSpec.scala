package edu.uci.ics.amber.engine.architecture.worker

import akka.actor.ActorContext
import akka.testkit.TestProbe
import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.logging.{EmptyLogManagerImpl, LogManager}
import edu.uci.ics.amber.engine.architecture.logging.storage.{
  DeterminantLogStorage,
  EmptyLogStorage
}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkCommunicationActor.NetworkSenderActorRef
import edu.uci.ics.amber.engine.architecture.messaginglayer.{
  BatchToTupleConverter,
  NetworkInputPort,
  NetworkOutputPort,
  TupleToBatchConverter
}
import edu.uci.ics.amber.engine.architecture.recovery.{LocalRecoveryManager, RecoveryQueue}
import edu.uci.ics.amber.engine.architecture.worker.WorkerInternalQueue._
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.PauseHandler.PauseWorker
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.QueryStatisticsHandler.QueryStatistics
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.ResumeHandler.ResumeWorker
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.{
  COMPLETED,
  RUNNING,
  UNINITIALIZED
}
import edu.uci.ics.amber.engine.common.{Constants, IOperatorExecutor, InputExhausted}
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, DataPayload}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.ControlInvocation
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.ControlCommand
import edu.uci.ics.amber.engine.common.rpc.{AsyncRPCClient, AsyncRPCServer}
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.util.CONTROLLER
import edu.uci.ics.amber.engine.common.virtualidentity.{
  ActorVirtualIdentity,
  LayerIdentity,
  LinkIdentity
}
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

class DataProcessorSpec extends AnyFlatSpec with MockFactory with BeforeAndAfterEach {
  lazy val pauseManager: PauseManager = wire[PauseManager]
  val identifier: ActorVirtualIdentity = ActorVirtualIdentity("DP mock")
  lazy val mockDataInputHandler: (ActorVirtualIdentity, DataPayload) => Unit =
    mock[(ActorVirtualIdentity, DataPayload) => Unit]
  lazy val mockControlInputHandler: (ActorVirtualIdentity, ControlPayload) => Unit =
    mock[(ActorVirtualIdentity, ControlPayload) => Unit]
  lazy val mockDataOutputHandler
      : (ActorVirtualIdentity, ActorVirtualIdentity, Long, DataPayload) => Unit =
    mock[(ActorVirtualIdentity, ActorVirtualIdentity, Long, DataPayload) => Unit]
  lazy val mockControlOutputHandler
      : (ActorVirtualIdentity, ActorVirtualIdentity, Long, ControlPayload) => Unit =
    mock[(ActorVirtualIdentity, ActorVirtualIdentity, Long, ControlPayload) => Unit]
  lazy val mockDataInputPort: NetworkInputPort[DataPayload] =
    new NetworkInputPort[DataPayload](identifier, mockDataInputHandler)
  lazy val controlInputPort: NetworkInputPort[ControlPayload] =
    new NetworkInputPort[ControlPayload](identifier, mockControlInputHandler)
  lazy val mockDataOutputPort: NetworkOutputPort[DataPayload] =
    new NetworkOutputPort[DataPayload](identifier, mockDataOutputHandler)
  lazy val controlOutputPort: NetworkOutputPort[ControlPayload] =
    new NetworkOutputPort[ControlPayload](identifier, mockControlOutputHandler)
  lazy val batchProducer: TupleToBatchConverter = mock[TupleToBatchConverter]
  lazy val breakpointManager: BreakpointManager = mock[BreakpointManager]
  val linkID: LinkIdentity =
    LinkIdentity(
      LayerIdentity("testDP", "mockOp", "src"),
      LayerIdentity("testDP", "mockOp", "dst")
    )
  val tuples: Seq[ITuple] = (0 until 400).map(ITuple(_))

  def sendDataToDP(
      senderWorker: ActorVirtualIdentity,
      dp: DataProcessor,
      data: Seq[ITuple],
      interval: Long = -1
  ): Future[_] = {
    Future {
      data.foreach { x =>
        dp.appendElement(InputTuple(senderWorker, x))
        if (interval > 0) {
          Thread.sleep(interval)
        }
      }
      dp.appendElement(EndMarker(senderWorker))
    }(ExecutionContext.global)
  }

  def sendControlToDP(
      dp: DataProcessor,
      control: Seq[ControlPayload],
      interval: Long = -1
  ): Future[_] = {
    Future {
      control.foreach { x =>
        dp.enqueueCommand(x, CONTROLLER)
        if (interval > 0) {
          Thread.sleep(interval)
        }
      }
    }(ExecutionContext.global)
  }

  def waitForDataProcessing(
      workerStateManager: WorkerStateManager,
      timeout: FiniteDuration = 5.seconds
  ): Unit = {
    val deadline = timeout.fromNow
    while (deadline.hasTimeLeft()) {
      //wait
    }
    assert(workerStateManager.getCurrentState == COMPLETED)
  }

  def waitForControlProcessing(dp: DataProcessor, timeout: FiniteDuration = 5.seconds): Unit = {
    val deadline = timeout.fromNow
    while (deadline.hasTimeLeft()) {
      //wait
    }
    assert(!dp.isControlQueueNonEmptyOrPaused)
  }

  case class DummyControl() extends ControlCommand[Unit]

  "data processor" should "process data messages" in {
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    val operator = mock[OperatorExecutor]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val asyncRPCServer: AsyncRPCServer = null
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    inAnyOrder {
      (batchProducer.emitEndOfUpstream _).expects().anyNumberOfTimes()
      (asyncRPCClient.send[Unit] _).expects(*, *).anyNumberOfTimes()
      inSequence {
        (operator.open _).expects().once()
        tuples.foreach { x =>
          (operator.processTuple _).expects(Left(x), linkID, pauseManager, asyncRPCClient)
        }
        (operator.processTuple _).expects(
          Right(InputExhausted()),
          linkID,
          pauseManager,
          asyncRPCClient
        )
        (operator.close _).expects().once()
      }
    }

    val dp = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    operator.open()
    Await.result(sendDataToDP(senderID, dp, tuples), 3.seconds)
    waitForDataProcessing(workerStateManager)
    dp.shutdown()

  }

  "data processor" should "prioritize control messages" in {
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    val operator = mock[OperatorExecutor]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    val asyncRPCServer: AsyncRPCServer = mock[AsyncRPCServer]
    inAnyOrder {
      (asyncRPCServer.logControlInvocation _).expects(*, *).anyNumberOfTimes()
      (asyncRPCClient.send[Unit] _).expects(*, *).anyNumberOfTimes()
      inSequence {
        (operator.open _).expects().once()
        inAnyOrder {
          tuples.map { x =>
            (operator.processTuple _).expects(Left(x), linkID, pauseManager, asyncRPCClient)
          }
          (asyncRPCServer.receive _).expects(*, *).atLeastOnce() //process controls during execution
        }
        (operator.processTuple _).expects(
          Right(InputExhausted()),
          linkID,
          pauseManager,
          asyncRPCClient
        )
        (asyncRPCServer.receive _)
          .expects(*, *)
          .anyNumberOfTimes() // process controls before execution completes
        (batchProducer.emitEndOfUpstream _).expects().once()
        (asyncRPCServer.receive _)
          .expects(*, *)
          .anyNumberOfTimes() // process controls after execution
        (operator.close _).expects().once()
      }
    }
    val dp: DataProcessor = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    operator.open()
    val f1 = sendDataToDP(senderID, dp, tuples, 2)
    val f2 = sendControlToDP(dp, (0 until 100).map(_ => ControlInvocation(0, DummyControl())), 3)
    Await.result(f1.zip(f2), 5.seconds)
    waitForDataProcessing(workerStateManager)
    waitForControlProcessing(dp)
    dp.shutdown()
  }

  "data processor" should "process control command without inputting data" in {
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    val operator = mock[OperatorExecutor]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val asyncRPCServer: AsyncRPCServer = mock[AsyncRPCServer]
    inAnyOrder {
      (operator.open _).expects().once()
      (asyncRPCServer.logControlInvocation _).expects(*, *).anyNumberOfTimes()
      (asyncRPCClient.send[Unit] _).expects(*, *).anyNumberOfTimes()
      (asyncRPCServer.receive _).expects(*, *).repeat(3)
      (operator.close _).expects().once()
    }
    val dp: DataProcessor = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    operator.open()
    Await.result(
      sendControlToDP(dp, (0 until 3).map(_ => ControlInvocation(0, DummyControl()))),
      1.second
    )
    waitForControlProcessing(dp)
    operator.close()
    dp.shutdown()
  }

  "data processor" should "write determinant to log" in {
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    val operator = mock[OperatorExecutor]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val asyncRPCServer: AsyncRPCServer = mock[AsyncRPCServer]
    inAnyOrder {
      (operator.open _).expects().once()
      (asyncRPCServer.logControlInvocation _).expects(*, *).anyNumberOfTimes()
      (asyncRPCClient.send[Unit] _).expects(*, *).anyNumberOfTimes()
      (asyncRPCServer.receive _).expects(*, *).repeat(3)
      (operator.close _).expects().once()
    }
    val dp: DataProcessor = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    operator.open()
    Await.result(
      sendControlToDP(dp, (0 until 3).map(_ => ControlInvocation(0, DummyControl()))),
      1.second
    )
    waitForControlProcessing(dp)
    operator.close()
    dp.shutdown()
  }

  "data processor" should "process only control commands while paused" in {
    val operator = mock[OperatorExecutor]
    (operator.open _).expects().once()
    val ctx: ActorContext = null
    val batchToTupleConverter = mock[BatchToTupleConverter]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    (asyncRPCClient.send _).expects(*, *).anyNumberOfTimes()
    val asyncRPCServer: AsyncRPCServer = wire[AsyncRPCServer]
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    val dp: DataProcessor = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    val handlerInitializer = wire[WorkerAsyncRPCHandlerInitializer]
    inSequence {
      (operator.processTuple _).expects(*, *, *, *).once()
      (mockControlOutputHandler.apply _).expects(*, *, *, *).repeat(4)
      (operator.processTuple _).expects(*, *, *, *).repeat(4)
      (batchProducer.emitEndOfUpstream _).expects().once()
      (operator.close _).expects().once()
    }
    operator.open()
    dp.appendElement(InputTuple(senderID, ITuple(1)))
    Thread.sleep(500)
    dp.enqueueCommand(ControlInvocation(0, PauseWorker()), CONTROLLER)
    dp.appendElement(InputTuple(senderID, ITuple(2)))
    dp.enqueueCommand(ControlInvocation(1, QueryStatistics()), CONTROLLER)
    Thread.sleep(1000)
    dp.appendElement(InputTuple(senderID, ITuple(3)))
    dp.enqueueCommand(ControlInvocation(2, QueryStatistics()), CONTROLLER)
    dp.appendElement(InputTuple(senderID, ITuple(4)))
    dp.enqueueCommand(ControlInvocation(3, ResumeWorker()), CONTROLLER)
    dp.appendElement(EndMarker(senderID))
    waitForControlProcessing(dp)
    waitForDataProcessing(workerStateManager)
    dp.shutdown()

  }

  def monitorCredits(
      senderID: ActorVirtualIdentity,
      dp: DataProcessor,
      data: Seq[ITuple]
  ): Future[_] = {
    Future {
      data.foreach { x =>
        dp.appendElement(InputTuple(senderID, x))
      }
      assert(
        dp.getSenderCredits(senderID) == Constants.unprocessedBatchesCreditLimitPerSender - 1
      )
      dp.appendElement(EndMarker(senderID))
    }(ExecutionContext.global)
  }

  "data processor" should "reduce credits" in {
    Constants.flowControlEnabled = true
    val asyncRPCClient: AsyncRPCClient = mock[AsyncRPCClient]
    val logManager = new EmptyLogManagerImpl(NetworkSenderActorRef(null))
    val logStorage: DeterminantLogStorage = new EmptyLogStorage()
    val recoveryQueue: RecoveryQueue = new RecoveryQueue(logStorage.getReader)
    val recoveryManager = new LocalRecoveryManager()
    val operator = new IOperatorExecutor {
      override def open(): Unit = {}

      override def close(): Unit = {}

      override def processTuple(
          tuple: Either[ITuple, InputExhausted],
          input: LinkIdentity,
          pauseManager: PauseManager,
          asyncRPCClient: AsyncRPCClient
      ): Iterator[(ITuple, Option[LinkIdentity])] = {
        Await.result(
          Future {
            Thread.sleep(3000); 42
          }(ExecutionContext.global),
          3.seconds
        )
        return Iterator()
      }
    }

    val asyncRPCServer: AsyncRPCServer = null
    val workerStateManager: WorkerStateManager = new WorkerStateManager(UNINITIALIZED)
    val senderID = ActorVirtualIdentity("sender")
    val upstreamLinkStatus: UpstreamLinkStatus = new UpstreamLinkStatus(Set(linkID))
    upstreamLinkStatus.registerInput(senderID, linkID)
    val tuplesToSend: Seq[ITuple] = (0 until Constants.defaultBatchSize).map(ITuple(_))

    val dp = wire[DataProcessor]
    dp.registerInput(senderID, linkID)
    dp.start()
    operator.open()
    assert(dp.getSenderCredits(senderID) == Constants.unprocessedBatchesCreditLimitPerSender)
    Await.result(monitorCredits(senderID, dp, tuplesToSend), 3.seconds)
    dp.shutdown()

  }

}
