/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

import _root_.services.audit.AuditEvents
import config.ApplicationConfig
import connectors.ERSFileValidatorConnector
import javax.inject.{Inject, Singleton}
import metrics.Metrics
import models._
import models.upscan.UpscanCallback
import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import utils.ErrorResponseMessages

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class FileProcessingService @Inject()(dataGenerator: DataGenerator,
                                      auditEvents: AuditEvents,
                                      ersConnector: ERSFileValidatorConnector,
                                      sessionService: SessionService,
                                      appConfig: ApplicationConfig,
                                      implicit val ec: ExecutionContext) extends Metrics {

  val splitSchemes: Boolean = appConfig.splitLargeSchemes
  val maxNumberOfRows: Int = appConfig.maxNumberOfRowsPerSubmission

  @throws(classOf[ERSFileProcessingException])
  def processFile(callbackData: UpscanCallback, empRef: String)(implicit hc: HeaderCarrier, schemeInfo: SchemeInfo, request : Request[_]): Int = {
    val startTime = System.currentTimeMillis()
    Logger.info("2.0 start: ")
    val result = dataGenerator.getData(readFile(callbackData.downloadUrl))
    Logger.info("2.1 result contains: " + result)
    deliverBESMetrics(startTime)
    Logger.debug("No if SchemeData Objects " + result.size)
    val filesWithData = result.filter(_.data.nonEmpty)
    var totalRows = 0
    val res1 = filesWithData.foldLeft(0) {
      (res, el) => {
        totalRows += el.data.size
        res + sendScheme(el, empRef)
      }
    }
    sessionService.storeCallbackData(callbackData, totalRows).map {
      case callback: Option[UpscanCallback] if callback.isDefined => res1
      case _ => Logger.error(s"storeCallbackData failed with Exception , timestamp: ${System.currentTimeMillis()}.")
        throw ERSFileProcessingException(("callback data storage in sessioncache failed "), "Exception storing callback data")
    }.recover {
      case e: Throwable => Logger.error(s"storeCallbackData failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        throw e
    }

    Logger.warn(s"Total rows for schemeRef ${schemeInfo.schemeRef}: $totalRows")
    auditEvents.totalRows(totalRows, schemeInfo)
    res1
  }

  def processCsvFile(callbackData: UpscanCallback, empRef: String)(implicit hc: HeaderCarrier, schemeInfo: SchemeInfo, request: Request[_]): Future[(Int, Int)] = {
    val startTime = System.currentTimeMillis()
    readCSVFile(callbackData.downloadUrl).map { fileData =>
      Logger.info(" 2. Invoke Data generator ")
      deliverBESMetrics(startTime)

      val sheetName = callbackData.name.replace(".csv","")
      val result: ListBuffer[Seq[String]] = dataGenerator.getCsvData(fileData)(schemeInfo, sheetName,hc,request)
      val schemeData: SchemeData = SchemeData(schemeInfo, sheetName, None, result)

      Logger.info("2.1 result contains: " + result)
      Logger.debug("No if SchemeData Objects " + result.size)
      (sendScheme(schemeData, empRef), schemeData.data.size)
    }
  }

  private[services] def readFile(downloadUrl: String): Iterator[String] = {
    val stream = ersConnector.upscanFileStream(downloadUrl)
    val targetFileName = "content.xml"
    val zipInputStream = new ZipInputStream(stream)
    @scala.annotation.tailrec
    def findFileInZip(stream: ZipInputStream): InputStream = {
      Option(stream.getNextEntry) match {
        case Some(entry) if entry.getName == targetFileName =>
          stream
        case Some(_) =>
          findFileInZip(stream)
        case None =>
          throw ERSFileProcessingException(
            s"${ErrorResponseMessages.fileProcessingServiceFailedStream}",
            s"${ErrorResponseMessages.fileProcessingServiceBulkEntity}"
          )
      }
    }
    val contentInputStream = findFileInZip(zipInputStream)
    new StaxProcessor(contentInputStream)
  }

  def readCSVFile(downloadUrl: String): Future[Iterator[String]] = {
    try {
      val reader = new BufferedReader(new InputStreamReader(ersConnector.upscanFileStream(downloadUrl)))
      Future(reader.lines().iterator().asScala)
    } catch {
      case _: Throwable => throw ERSFileProcessingException(
        s"${ErrorResponseMessages.fileProcessingServiceFailedStream}",
        s"${ErrorResponseMessages.fileProcessingServiceBulkEntity}")
    }
  }

  def sendSchemeData(ersSchemeData: SchemeData, empRef: String)(implicit hc: HeaderCarrier, request: Request[_]): Unit = {
    Logger.debug("Sheedata sending to ers-submission " + ersSchemeData.sheetName)
    val result = ersConnector.sendToSubmissions(ersSchemeData, empRef).onComplete {
      case Success(suc) => {
        auditEvents.fileValidatorAudit(ersSchemeData.schemeInfo, ersSchemeData.sheetName)
      }
      case Failure(ex) => {
        auditEvents.auditRunTimeError(ex, ex.getMessage, ersSchemeData.schemeInfo, ersSchemeData.sheetName)
        Logger.error(ex.getMessage)
        throw new ERSFileProcessingException(ex.toString, ex.getStackTrace.toString)
      }
    }
  }

  def sendScheme(schemeData: SchemeData, empRef: String)(implicit hc: HeaderCarrier, request: Request[_]): Int = {
    if(splitSchemes && (schemeData.data.size > maxNumberOfRows)) {

      def numberOfSlices(sizeOfBuffer: Int): Int = {
        if(sizeOfBuffer%maxNumberOfRows > 0)
          sizeOfBuffer/maxNumberOfRows + 1
        else
          sizeOfBuffer/maxNumberOfRows
      }

      val slices: Int = numberOfSlices(schemeData.data.size)
      for(i <- 0 until slices * maxNumberOfRows by maxNumberOfRows) {
        val scheme = new SchemeData(schemeData.schemeInfo, schemeData.sheetName, Option(slices), schemeData.data.slice(i, (i + maxNumberOfRows)))
        Logger.debug("The size of the scheme data is " + scheme.data.size + " and i is " + i)
        sendSchemeData(scheme, empRef)
      }
      slices
    }
    else {
      sendSchemeData(schemeData, empRef)
      1
    }
  }

  def deliverBESMetrics(startTime:Long): Unit =
    metrics.besTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
}
