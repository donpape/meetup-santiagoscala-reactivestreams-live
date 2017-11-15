package com.meetup.santiagoscala

import java.sql.ResultSet

import com.meetup.santiagoscala.Slide04.JDBCStream.JDBCQueryPublisher
import com.meetup.santiagoscala.Slide04.Table
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}


object Slide05 {
  val ds = JDBC.createDataSource
}

class Slide05 extends PublisherVerification[Table](new TestEnvironment()) {

  import Slide05._

  override def createPublisher(elements: Long) = {
    val sql = s"select * from INFORMATION_SCHEMA.TABLES limit $elements"
    new JDBCQueryPublisher(JDBC.createConnection(), sql, (rs: ResultSet) => {
      Table(rs.getString(2), rs.getString(3))
    })
  }

  override def createFailedPublisher() = {
    val sql = "sql malo"
    new JDBCQueryPublisher(JDBC.createConnection(), sql, (rs: ResultSet) => {
      Table(rs.getString(2), rs.getString(3))
    })
  }

  override def maxElementsFromPublisher = 95
}