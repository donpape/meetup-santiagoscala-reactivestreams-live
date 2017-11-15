package com.meetup.santiagoscala

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

object JDBC {

  val url = "jdbc:hsqldb:mem:mymemdb"
  val user = "SA"
  val pass = ""

  def createConnection(): Connection = DriverManager.getConnection(url, user, pass)

  def createDataSource: DataSource = {
    val hc = new HikariConfig()
    hc.setDriverClassName("org.hsqldb.jdbc.JDBCDriver")
    hc.setJdbcUrl(url)
    hc.setUsername(user)
    hc.setPassword(pass)
    hc.setMaximumPoolSize(2500)
    new HikariDataSource(hc)
  }

}