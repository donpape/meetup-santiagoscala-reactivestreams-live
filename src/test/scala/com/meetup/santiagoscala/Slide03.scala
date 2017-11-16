package com.meetup.santiagoscala

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object Slide03 extends LazyLogging {

  case class QueueItem(n: Long, subscriber: Subscriber[_ >: Int])

  class RandomPublisher extends Publisher[Int] {
    // non threadsafe
    val randomGenerator = Random
    val subscriptions = new AtomicLong(0)
    val queue: BlockingQueue[QueueItem] = new LinkedBlockingQueue[QueueItem]()
    val workerThread = new AtomicReference[Thread]()

    def internalThreadFunction(): Unit = {
      while (subscriptions.get() > 0) {
        logger.info("   publisher internal loop once again {}", subscriptions.get())
        Option(queue.poll(1000, TimeUnit.MILLISECONDS)).foreach { item =>
          0L.until(item.n).foreach { i =>
            logger.info("<  subscriber {} <- published {} elements of {}", item.subscriber.hashCode(), i, item.n)
            item.subscriber.onNext(randomGenerator.nextInt())
          }
        }
      }
      logger.info("   publisher internal loop ended")
    }

    def createAndStartWorkerThread: Thread = {
      val t = new Thread {
        override def run(): Unit = internalThreadFunction()
      }
      t.setName(s"publisher-thread-${System.currentTimeMillis()}")
      t.start()
      t
    }

    def offer(item: QueueItem): Boolean = queue.offer(item)

    override def subscribe(subscriber: Subscriber[_ >: Int]): Unit = {
      logger.info("   subscriber {} <- creating subscription in publisher", subscriber.hashCode())
      val subscription = new RandomSubscription(this, subscriber)
      subscriber.onSubscribe(subscription)
      subscriptions.incrementAndGet()
      if (workerThread.get() == null) {
        workerThread.set(createAndStartWorkerThread)
      }
    }

    def unsubscribe(subscriber: Subscriber[_ >: Int]): Unit = {
      subscriptions.decrementAndGet()
      logger.info("   subscriber {} <- unsubscribed", subscriber.hashCode())
    }

  }

  class RandomSubscription(publisher: RandomPublisher, subscriber: Subscriber[_ >: Int]) extends Subscription {
    val operational = new AtomicBoolean(true)

    override def request(n: Long): Unit = {
      if (operational.get()) {
        logger.info("   subscriber {} <- requested {} elements", subscriber.hashCode(), n)
        publisher.offer(QueueItem(n, subscriber))
      } else {
        logger.info("   subscription already cancelled!")
      }
    }

    override def cancel(): Unit = if (operational.getAndSet(false)) {
      logger.info("   subscription cancelling")
      publisher.unsubscribe(subscriber)
    } else {
      logger.info("   subscription already cancelled!")
    }

  }


  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("meetup")
    implicit val materializer = ActorMaterializer()

    def log(t: (Int, Long)): Int = {
      logger.info(" >  > got {}th value {}", t._2, t._1)
      t._1
    }

    val publisher = new RandomPublisher

    val source = Source.fromPublisher(publisher)

    val flow = Flow[Int]
      //.throttle(4, 1.second, 4, ThrottleMode.shaping)
      .zipWithIndex.map(log)

    val done = source
      .via(flow)
      .take(40)
      .runWith(Sink.seq)
    val seq = Await.result(done, 10.minutes)
    logger.info("   resulting sequence length is {} elements", seq.length)


    system.terminate()
  }
}




// compartir para los spring-web
// https://developer.lightbend.com/docs/alpakka/current/spring-web.html
