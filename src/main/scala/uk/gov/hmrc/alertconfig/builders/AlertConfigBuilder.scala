/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.alertconfig.builders

import java.io.{File, FileInputStream, FileNotFoundException}

import org.yaml.snakeyaml.Yaml
import spray.json.DefaultJsonProtocol._
import uk.gov.hmrc.alertconfig.logging.Logger

import scala.collection.JavaConversions.mapAsScalaMap
import scala.util.{Failure, Success, Try}

trait Builder[T] {
  def build: T
}

case class AlertConfigBuilder(serviceName: String, handlers: Seq[String] = Seq("noop"), exceptionThreshold: Int = 2, http5xxThreshold: Int = 2, http5xxPercentThreshold: Double = 100, containerKillThreshold : Int = 2) extends Builder[Option[String]]{

  import spray.json._

  val logger = new Logger()

  def withHandlers(handlers: String*) = this.copy(handlers = handlers)

  def withExceptionThreshold(exceptionThreshold: Int) = this.copy(exceptionThreshold = exceptionThreshold)

  def withHttp5xxThreshold(http5xxThreshold: Int) = this.copy(http5xxThreshold = http5xxThreshold)

  def withHttp5xxPercentThreshold(http5xxPercentThreshold: Int) = this.copy(http5xxPercentThreshold = http5xxPercentThreshold)

  def withContainerKillThreshold(containerCrashThreshold : Int) = this.copy(containerKillThreshold = containerCrashThreshold)

  def build: Option[String] = {
    val appConfigPath = System.getProperty("app-config-path", "../app-config")
    val appConfigDirectory = new File(appConfigPath)
    val appConfigFile = new File(appConfigDirectory, s"${serviceName}.yaml")

    if (!appConfigDirectory.exists) {
      throw new FileNotFoundException(s"Could not find app-config repository: $appConfigPath")
    }

    appConfigFile match {
      case file if !file.exists =>
        logger.info(s"No app-config file found for service: '${serviceName}'. File was expected at: '${file.getAbsolutePath}'")
        None
      case file if getZone(file).isEmpty =>
        logger.warn(s"app-config file for service: '${serviceName}' does not contain 'zone' key.")
        None
      case file =>
        val serviceDomain = getZone(file)

        ZoneToServiceDomainMapper.getServiceDomain(serviceDomain).map(serviceDomain =>
          s"""
             |{\"app\": \"$serviceName.$serviceDomain\",\"handlers\": ${handlers.toJson.compactPrint}, \"exception-threshold\":$exceptionThreshold, \"5xx-threshold\":$http5xxThreshold, \"5xx-percent-threshold\":$http5xxPercentThreshold, \"containerKillThreshold\" : $containerKillThreshold}
          """.stripMargin
        )
    }
  }

  def getZone(appConfigFile: File): Option[String] = {
    def parseAppConfigFile: Try[Object] = {
      Try(new Yaml().load(new FileInputStream(appConfigFile)))
    }

    parseAppConfigFile match {
      case Failure(exception) => {
        logger.warn(s"app-config file ${appConfigFile} for service: '${serviceName}' is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
        None
      }
      case Success(appConfigYamlMap) => {
        val appConfig = appConfigYamlMap.asInstanceOf[java.util.Map[String, java.util.Map[String, String]]]
        val versionObject = appConfig.toMap.mapValues(_.toMap)("0.0.0")
        versionObject.get("zone")
      }
    }
  }
}

case class TeamAlertConfigBuilder(services: Seq[String], handlers: Seq[String] = Seq("noop"), exceptionThreshold: Int = 2, http5xxThreshold: Int = 2, http5xxPercentThreshold: Double = 100) extends Builder[Seq[AlertConfigBuilder]] {

  def withHandlers(handlers: String*) = this.copy(handlers = handlers)

  def withExceptionThreshold(exceptionThreshold: Int) = this.copy(exceptionThreshold = exceptionThreshold)

  def withHttp5xxThreshold(http5xxThreshold: Int) = this.copy(http5xxThreshold = http5xxThreshold)

  def withHttp5xxPercentThreshold(percentThreshold: Double) = this.copy(http5xxPercentThreshold = percentThreshold)

  override def build: Seq[AlertConfigBuilder] = services.map(service =>
    AlertConfigBuilder(service, handlers, exceptionThreshold, http5xxThreshold, http5xxPercentThreshold)
  )
}

object TeamAlertConfigBuilder {

  def teamAlerts(services: Seq[String]): TeamAlertConfigBuilder = {
    require(services.nonEmpty, "no alert service provided")
    TeamAlertConfigBuilder(services)
  }

}

object ZoneToServiceDomainMapper {
  val logger = new Logger()
  val zoneToServiceMappingFilePath = System.getProperty("zone-mapping-path", "zone-to-service-domain-mapping.yml")
  val zoneToServiceMappingFile = new File(zoneToServiceMappingFilePath)
  if(!zoneToServiceMappingFile.exists()) {
    throw new FileNotFoundException(s"Could not find zone to service domain mapping file: ${zoneToServiceMappingFilePath}")
  }
  val zoneToServiceDomainMappings: Map[String, String] = mapAsScalaMap[String, String](
    new Yaml().load(new FileInputStream(zoneToServiceMappingFile)).asInstanceOf[java.util.Map[String, String]])
      .toMap

  def getServiceDomain(zone: Option[String]): Option[String] = {
    zone match {
      case Some(_: String) => {
        val mappedServiceDomain = zoneToServiceDomainMappings.get(zone.get)
        if(mappedServiceDomain.isEmpty) {
          logger.error(s"Zone to service domain mapping file '${zoneToServiceMappingFile.getAbsolutePath}' does not contain mapping for zone '${zone.get}'")
        }
        mappedServiceDomain
      }
      case _ => zone
    }
  }
}
