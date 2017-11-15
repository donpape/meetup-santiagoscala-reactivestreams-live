package com.meetup.santiagoscala

import scala.collection.mutable
import scala.util.Try

object Slide01 extends App {


  // jdbc para principiates, normal en java
  Try {
    val c = JDBC.createConnection
    val s = c.createStatement()
    val rs = s.executeQuery("select * from INFORMATION_SCHEMA.TABLES limit 1")
    val r = if (rs.next()) {
      Some(rs.getString(3))
    } else {
      throw new Exception("como que no hay?")
    }
    rs.close()
    s.close()
    c.close()
    r
  }.map(println)

  // puristas scala van a llorar
  Try {
    val ds = JDBC.createDataSource
    val c = ds.getConnection
    val s = c.createStatement()
    val rs = s.executeQuery("select * from INFORMATION_SCHEMA.TABLES")
    val r = mutable.ArrayBuffer[String]()
    while (rs.next()) {
      r += rs.getString(3)
    }
    rs.close()
    s.close()
    c.close()
    r
  }.map(println)
}