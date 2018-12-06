package com.cyclone.wsman.examples

import akka.actor.ActorSystem
import com.cyclone.command.TimeoutContext
import com.cyclone.util.kerberos.{KerberosArtifacts, KerberosDeployer}
import com.cyclone.util.net.{AuthenticationMethod, PasswordSecurityContext}
import com.cyclone.wsman.command.{EnumerateByWQL, WSManInstancesResult}
import com.cyclone.wsman.{WSMan, WSManTarget}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * This examples enumerates configured services on a remote Windows PC.
  *
  * To run this need an application.conf in the classpath containing connection details:
  * {{{
  *wsman {
  *   # The target host.
  *   # Note that Kerberos requires fully qualified host names to authenticate.
  *   # If IPs are used we will attempt to resolve to a fully
  *   # qualified host name. To do this DNS needs to be configured - see documentation.
  *   host = someHost.someDomain
  *
  *   username = someUser
  *   password = somePassword
  * }
  * }}}
  *
  * If using Kerberos authentication, Kerberos can be configured using the following in the application.conf:
  * {{{
  * cyclone {
  *   kerberos {
  *     realm = domain.name # Or whatever is the domain name
  *     kdcHosts = [192.168.1.2] # IP of domain controller
  *     realmHosts = []
  *   }
  * }
  * }}}
  */
object QueryCommandExample extends App {

  val config = ConfigFactory.load()

  val host = config.getString("wsman.host")
  val username = config.getString("wsman.username")
  val password = config.getString("wsman.password")

  implicit val actorSystem: ActorSystem = ActorSystem("exampleActorSystem")
  implicit val timeoutContext: TimeoutContext = TimeoutContext.default

  val wsman = WSMan.create

  val futureResult: Future[WSManInstancesResult] =
    for {
      // If using Kerberos, this will create temporary krb5.conf and login.conf and configure system properties
      _ <- KerberosDeployer.create.deploy(KerberosArtifacts.simpleFromConfig)

      commandResult <- wsman.executeCommand(
        WSManTarget(
          WSMan.httpUrlFor(host, ssl = false),
          PasswordSecurityContext(username, password, AuthenticationMethod.Kerberos)
        ),
        EnumerateByWQL("select * from Win32_Service")
      )
    } yield commandResult

  futureResult.onComplete {
    case Success(result) =>
      result.instances.foreach { instance =>
        for {
          caption   <- instance.stringProperty("Caption")
          startMode <- instance.stringProperty("StartMode")
          state     <- instance.stringProperty("State")
        } println(s"Service '$caption' with start mode $startMode state is $state")
      }
      System.exit(0)

    case Failure(e) =>
      e.printStackTrace()
      System.exit(1)
  }
}
