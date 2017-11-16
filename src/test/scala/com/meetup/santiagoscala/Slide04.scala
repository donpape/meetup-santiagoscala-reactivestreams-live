package com.meetup.santiagoscala

import java.sql.{Connection, ResultSet}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import com.meetup.santiagoscala.Slide04.JDBCStream.JDBCQueryPublisher
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Slide04 extends LazyLogging {

  object JDBCStream {

    class JDBCQueryPublisher[T](connection: Connection, sql: String, deserializer: ResultSet => T) extends Publisher[T] {

      logger.info("creating publisher for sql: {}", sql)

      // executor para ejecutar reads
      val executor = Executors.newSingleThreadExecutor()
      // valor del resultado
      val resultset = Try {
        // crear un statement que sea forward only
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        // ejecutar query
        statement.executeQuery(sql)
      }
      // estado del publisher, maneja estado de resultset
      val open = new AtomicBoolean(true)

      // cerrar todo lo que se pueda si falla temprano
      resultset match {
        case Success(rs) =>
        case Failure(err) =>
          cleanup
      }

      // implementacion del standard
      override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
        logger.info("   subscriber {} <- creating subscription in publisher", subscriber.hashCode())
        val subscription = new JDBCSubscription[T](this, subscriber)
        subscriber.onSubscribe(subscription)
        resultset match {
          case Success(rs) =>
          case Failure(err) =>
            logger.info("== subscriber {} <- onComplete", subscriber.hashCode())
            cleanup
            subscriber.onError(err)
        }
      }

      // para saber si un subscriber salio
      def unsubscribe(subscriber: Subscriber[_ >: T]): Unit = {
        logger.info("   subscriber {} <- unsubscribed", subscriber.hashCode())
      }

      def cleanup = Try {
        if (open.getAndSet(false)) {
          logger.info("   closing    connection")
          resultset match {
            case Success(rs) => rs.close()
            case Failure(err) =>
          }
          connection.close()
          executor.shutdownNow()
          //          executor.execute(() => executor.shutdownNow())
        }
      }

      def request(subscriber: Subscriber[_ >: T]): Unit = if (!executor.isShutdown)
        executor.execute { () =>
          if (open.get) {
            resultset match {
              case Failure(err) =>
              case Success(rs) =>
                if (!rs.isClosed && rs.next()) {
                  Try(deserializer(rs)) match {
                    case Success(t) =>
                      logger.info("<  subscriber {} <- onNext {} ", subscriber.hashCode(), t)
                      subscriber.onNext(t)
                    case Failure(e) =>
                      logger.info("== subscriber {} <- onError {} ", subscriber.hashCode(), e.getMessage)
                      cleanup
                      subscriber.onError(e)
                  }
                } else {
                  logger.info("== subscriber {} <- onComplete", subscriber.hashCode())
                  cleanup
                  subscriber.onComplete()
                }
            }
          }
        }
    }


    class JDBCSubscription[T](publisher: JDBCQueryPublisher[T], subscriber: Subscriber[_ >: T]) extends Subscription {
      val operational = new AtomicBoolean(true)

      override def request(n: Long): Unit = {
        if (n <= 0) {
          val e = new IllegalArgumentException()
          logger.info("== subscriber {} <- onError {} ", subscriber.hashCode(), e.getMessage)
          subscriber.onError(e)
        } else {
          if (operational.get()) {
            logger.info("   subscriber {} <- requested {} elements", subscriber.hashCode(), n)
            Iterator.iterate(0L)(_ + 1).takeWhile(_ < n && publisher.open.get).foreach(n => publisher.request(subscriber))
          } else {
            logger.info("   subscription already cancelled!")
          }
        }
      }

      override def cancel(): Unit = if (operational.getAndSet(false)) {
        logger.info("   subscription cancelling")
        publisher.unsubscribe(subscriber)
      } else {
        logger.info("   subscription already cancelled!")
      }

    }

  }


  case class Table(schema: String, name: String)

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("meetup")
    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val ds = JDBC.createDataSource
    val sql = "select * from INFORMATION_SCHEMA.TABLES"

    val publisher = new JDBCQueryPublisher(ds.getConnection, sql, (rs: ResultSet) => {
//      if (rs.getString(3) == "VIEWS")
//        throw new Exception("algo se fue a la B")
      Table(rs.getString(2), rs.getString(3))
    })

    val source = Source.fromPublisher(publisher)

    val flow = Flow[Table]
      .throttle(4, 1.second, 4, ThrottleMode.shaping)
      .zipWithIndex.map { t =>
      logger.info(" > got {}th value {}", t._2, t._1)
      t._1
    }

    val done = source.via(flow).take(400).runWith(Sink.seq)
      .recover { case e: Throwable =>
        logger.error("   resulting sequence with error", e)
        Seq.empty
      }
    val seq = Await.result(done, 10.minutes)
    logger.info("   resulting sequence length is {} elements", seq.length)

    val out = system.terminate()
    val ter = Await.result(out, 10.minutes)
    logger.info("   closing output", ter)
    System.exit(0)

  }
}