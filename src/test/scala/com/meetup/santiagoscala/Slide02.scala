package com.meetup.santiagoscala

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Attributes, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object Slide02 extends LazyLogging {

  // revisar documentacion en:
  // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md

  /**
    * In summary, Reactive Streams is a standard and specification for Stream-oriented libraries for the JVM that
    *  - process a potentially unbounded number of elements in sequence,
    *  - asynchronously passing elements between components,
    *  - with mandatory non-blocking backpressure.
    */


  /**
    * API Components
    * - Publisher -> A Publisher is a provider of a potentially unbounded number of sequenced elements,
    * publishing them according to the demand received from its Subscriber(s)
    * - Subscriber -> Alguien que solicita elementos de un Publisher
    * - Subscription -> Representa la relacion entre Publisher y Subscriber
    * - Processor -> una etapa del stream que es Subscriber y Publisher a la vez
    */

  /**
    * Reglas Generales: Publisher
    *  - Existe para crear Subscription a partir de el mismo y un Subscriber
    *  - Genera o extrae los eventos necesarios para alimentar Subscription
    *  - Decide si envia eventos a todos o algunas de sus suscripciones, depende de la implementacion y objetivo
    */

  /**
    * Reglas Generales: Subscriber
    *  - Implementa que hacer con cada elemento recibido
    *  - Implementa manejo de errores y termino del stream
    *  - no puede tener mas de un Subscription
    *  - por lo general uno no implementa esto.
    */

  /**
    * Reglas Generales: Subscription
    *  - Solicita eventos para el Subscriber
    *  - al solicitar eventos no debe bloquear
    *  - Implementa manejo de errores
    *  - no debe enviar eventos al Subscriber si esta cancelado
    */

  class RandomPublisher extends Publisher[Int] {
    val randomGenerator = Random

    override def subscribe(subscriber: Subscriber[_ >: Int]) = {
      logger.info("   subscriber {} <- creating subscription in publisher", subscriber.hashCode())
      val subscription = new RandomSubscription(this, subscriber)
      subscriber.onSubscribe(subscription)
    }

  }

  class RandomSubscription(publisher: RandomPublisher, subscriber: Subscriber[_ >: Int]) extends Subscription {
    val operational = new AtomicBoolean(true)

    override def request(n: Long) = {
      logger.info("   subscriber {} <- requested {} elements", subscriber.hashCode(), n)
      if (operational.get()) {
        0L.until(n).foreach { i =>
          logger.info("<  subscriber {} <- published {} elements of {}", subscriber.hashCode(), i, n)
          subscriber.onNext(publisher.randomGenerator.nextInt())
        }
      } else {
        logger.info("   subscription already cancelled!")
      }
    }

    override def cancel() = if (operational.getAndSet(false)) {
      logger.info("   subscription cancelling")
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
      .throttle(4, 1.second, 4, ThrottleMode.shaping)
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