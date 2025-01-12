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

import com.typesafe.config.ConfigFactory
import config.ApplicationConfig
import models.{ERSFileProcessingException, SchemeInfo}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => argEq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Request
import services.audit.AuditEvents
import services.headers.HeaderData
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.services.validation.models.{Cell, Row, ValidationError}
import uk.gov.hmrc.services.validation.DataValidator
import utils.ErrorResponseMessages

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Try

class DataGeneratorSpec extends PlaySpec with CSVTestData with GuiceOneAppPerSuite with ScalaFutures with MockitoSugar with BeforeAndAfter with HeaderData {

  val mockAuditEvents: AuditEvents = mock[AuditEvents]
  val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val dataGenerator = new DataGenerator(mockAuditEvents, mockAppConfig)

  val schemeInfo: SchemeInfo = SchemeInfo(
    schemeRef = "XA11999991234567",
    timestamp = DateTime.now,
    schemeId = "123PA12345678",
    taxYear = "2014/F15",
    schemeName = "MyScheme",
    schemeType = "EMI"
  )

  implicit val request: Request[_] = mock[Request[_]]
  implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  val testAct = List("", "", "", "")

  before {
    reset(mockAuditEvents)
  }

  "validateHeaderRow" should {
    "return with an error if the sheet name isn't recognised" in {
      val result = Try(dataGenerator.validateHeaderRow(XMLTestData.otherHeaderSheet1Data, "csopHeaderSheet1Data")(schemeInfo, hc, request))
      result.isFailure must be(true)
      verify(mockAuditEvents, times(1)).fileProcessingErrorAudit(argEq(schemeInfo), argEq("csopHeaderSheet1Data"), argEq("Could not set the validator"))(any(), any())
    }

    "validate CSOP_OptionsGranted_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(csopHeaderSheet1Data, "CSOP_OptionsGranted_V3")(schemeInfo, hc, request) must be(9)
    }

    "validate CSOP_OptionsRCL_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(csopHeaderSheet2Data, "CSOP_OptionsRCL_V3")(schemeInfo, hc, request) must be(9)
    }

    "validate CSOP_OptionsExercised_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(csopHeaderSheet3Data, "CSOP_OptionsExercised_V3")(schemeInfo, hc, request) must be(20)
    }

    "validate SIP_Awards_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(sipHeaderSheet1Data, "SIP_Awards_V3")(schemeInfo, hc, request) must be(17)
    }

    "validate SIP_Out_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(sipHeaderSheet2Data, "SIP_Out_V3")(schemeInfo, hc, request) must be(17)
    }

    "validate EMI40_Adjustments_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(emiHeaderSheet1Data, "EMI40_Adjustments_V3")(schemeInfo, hc, request) must be(14)
    }

    "validate EMI40_Replaced_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(emiHeaderSheet2Data, "EMI40_Replaced_V3")(schemeInfo, hc, request) must be(17)
    }

    "validate EMI40_RLC_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(emiHeaderSheet3Data, "EMI40_RLC_V3")(schemeInfo, hc, request) must be(12)
    }

    "validate EMI40_NonTaxable_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(emiHeaderSheet4Data, "EMI40_NonTaxable_V3")(schemeInfo, hc, request) must be(15)
    }

    "validate EMI40_Taxable_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(emiHeaderSheet5Data, "EMI40_Taxable_V3")(schemeInfo, hc, request) must be(20)
    }

    "validate Other_Grants_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet1Data, "Other_Grants_V3")(schemeInfo, hc, request) must be(4)
    }

    "validate Other_Options_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet2Data, "Other_Options_V3")(schemeInfo, hc, request) must be(42)
    }

    "validate Other_Acquisition_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet3Data, "Other_Acquisition_V3")(schemeInfo, hc, request) must be(40)
    }

    "validate Other_RestrictedSecurities_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet4Data, "Other_RestrictedSecurities_V3")(schemeInfo, hc, request) must be(20)
    }

    "validate Other_OtherBenefits_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet5Data, "Other_OtherBenefits_V3")(schemeInfo, hc, request) must be(13)
    }

    "validate Other_Convertible_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet6Data, "Other_Convertible_V3")(schemeInfo, hc, request) must be(15)
    }

    "validate Other_Notional_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet7Data, "Other_Notional_V3")(schemeInfo, hc, request) must be(13)
    }

    "validate Other_Enhancement_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet8Data, "Other_Enhancement_V3")(schemeInfo, hc, request) must be(14)
    }

    "validate Other_Sold_V3 headerRow as valid" in {
      dataGenerator.validateHeaderRow(otherHeaderSheet9Data, "Other_Sold_V3")(schemeInfo, hc, request) must be(14)
    }
  }

  "setValidator" should {
    "return a DataValidator if the given sheet name is valid" in {
      assert(dataGenerator.setValidator("EMI40_Adjustments_V3")(SchemeInfo("", DateTime.now(), "" ,"" ,"", ""), hc, request).isInstanceOf[DataValidator])
    }

    "throw an exception if the given sheet name is not valid" in {
      an[ERSFileProcessingException] mustBe thrownBy (dataGenerator.setValidator("Invalid")(SchemeInfo("", DateTime.now(), "" ,"" ,"", ""), hc, request))
    }
  }

  "getValidatorAndSheetInfo" should {
    "return a Right with a DataValidator if the given sheet name is valid" in {
      dataGenerator.getValidatorAndSheetInfo("EMI40_Adjustments_V3", SchemeInfo("", DateTime.now(), "" ,"" ,"", "")) match {
        case Left(_) => fail("Did not return validator")
        case Right(_) => succeed
      }
    }

    "return a left with an exception if the given sheet name is not valid" in {
      dataGenerator.getValidatorAndSheetInfo("Invalid", SchemeInfo("", DateTime.now(), "" ,"" ,"", "")) match {
        case Left(_) => succeed
        case Right(_) => fail("Did not return expected exception")
      }
    }
  }

  "identifyAndDefineSheet" should {
    "identify and define the sheet with correct scheme type" in {
      dataGenerator.identifyAndDefineSheet("EMI40_Adjustments_V3")(schemeInfo, hc, request) mustBe ("EMI40_Adjustments_V3")
    }

    "return an error when sheet name is invalid" in {
      val result = Try(dataGenerator.identifyAndDefineSheet("EMI40_Adjustments")(schemeInfo, hc, request))
      result.isFailure must be(true)
      verify(mockAuditEvents, times(1)).fileProcessingErrorAudit(argEq(schemeInfo), argEq("EMI40_Adjustments"), argEq("Could not set the validator"))(any(), any())
    }

    "return an error when scheme types do not match" in {
      val schemeInfo2: SchemeInfo = SchemeInfo(
        schemeRef = "XA11999991234567",
        timestamp = DateTime.now,
        schemeId = "123PA12345678",
        taxYear = "2014/F15",
        schemeName = "MyScheme",
        schemeType = "CSOP"
      )
      val result = Try(dataGenerator.identifyAndDefineSheet("EMI40_Adjustments_V3")(schemeInfo2, hc, request))
      result.isFailure must be(true)
      verify(mockAuditEvents, times(1)).fileProcessingErrorAudit(argEq(schemeInfo2), argEq("EMI40_Adjustments_V3"), argEq("emi is not equal to csop"))(any(), any())
    }
  }

  "isBlankRow" should {
    "return true when row is blank" in {
      dataGenerator.isBlankRow(testAct) must be(true)
    }

    "return true when row is blank and has white space" in {
      val testAct1 = List("  ", "  ", "   ", "  ")
      dataGenerator.isBlankRow(testAct1) must be(true)
    }

    "return false when row is not blank" in {
      val testAct1 = List("dfgdg", "", "", "")
      dataGenerator.isBlankRow(testAct1) must be(false)
    }
  }

  "generateRowData" should {
    val validator = new DataValidator(ConfigFactory.load.getConfig("ers-other-grants-validation-config"))

    "return the data when parsed correctly and no errors are found" in {
      val result = dataGenerator.generateRowData(XMLTestData.otherGrantsExpData, 10, validator)(schemeInfo, "Other_Grants_V3", hc, request)
      result must be(XMLTestData.otherGrantsExpData)
    }

    "return an error when an issue is found" in {
      val err = List(ValidationError(Cell("A",10,""),"error.1","001","Enter a date that matches the yyyy-mm-dd pattern."))
      val res1 = Try(dataGenerator.generateRowData(testAct, 10, validator)(schemeInfo, "Other_Grants_V3", hc, request))
      res1.isFailure mustBe true
      verify(mockAuditEvents, times(1)).validationErrorAudit(argEq(err), argEq(schemeInfo), argEq("Other_Grants_V3"))(any(), argEq(schemeInfo), any())
    }

    "throw exception if ErsValidator.validateRow throws exception" in {
      val mockValidator = mock[DataValidator]
      when(mockValidator.validateRow(any[Row])).thenThrow(new RuntimeException("this is a runtime exception"))

     val caughtException = intercept[RuntimeException] {
       dataGenerator.generateRowData(testAct, 10, mockValidator)(schemeInfo, "Other_Grants_V3", hc, request)
     }

      caughtException.getMessage mustBe "this is a runtime exception"
    }
  }

  "getData" should {
    "get an exception if ods file has less than 9 rows and doesn't have header data" in {
      val schemeInfo: SchemeInfo = SchemeInfo (
        schemeRef = "XA11000001231275",
        timestamp = DateTime.now,
        schemeId = "123PA12345678",
        taxYear = "2014/F15",
        schemeName = "MyScheme",
        schemeType = "CSOP"
      )
      val result = intercept[ERSFileProcessingException] {
        dataGenerator.getErrors(XMLTestData.getInvalidCSOPWithoutHeaders)(schemeInfo, hc, request)
      }
      result.message mustBe "Incorrect ERS Template - Header doesn't match"
    }

    "get an exception if ods file has more than 1 sheet but 1 of the sheets has less than 9 rows and doesn't have header data" in {
      val schemeInfo: SchemeInfo = SchemeInfo (
        schemeRef = "XA11000001231275",
        timestamp = DateTime.now,
        schemeId = "123PA12345678",
        taxYear = "2014/F15",
        schemeName = "MyScheme",
        schemeType = "CSOP"
      )
      val result = intercept[ERSFileProcessingException] {
        dataGenerator.getErrors(XMLTestData.getInvalidCSOPWith2Sheets1WithoutHeaders)(schemeInfo, hc, request)
      }
      result.message mustBe "Incorrect ERS Template - Header doesn't match"
    }

    "get an exception if ods file doesn't contain any data" in {
      val schemeInfo: SchemeInfo = SchemeInfo (
        schemeRef = "XA11000001231275",
        timestamp = DateTime.now,
        schemeId = "123PA12345678",
        taxYear = "2014/F15",
        schemeName = "MyScheme",
        schemeType = "CSOP"
      )
      val result = intercept[ERSFileProcessingException] {
        dataGenerator.getErrors(XMLTestData.getCSOPWithoutData)(schemeInfo, hc, request)
      }
      result.message mustBe "The file that you chose doesn’t have any data after row 9. The reportable events data must start in cell A10.<br/><a href=\"https://www.gov.uk/government/collections/employment-related-securities\">Use the ERS guidance documents</a> to help you create error-free files."
    }

    "get Data for Iterator of Strings" in {
      val result = dataGenerator.getErrors(XMLTestData.getEMIAdjustmentsTemplate)(schemeInfo,hc,request)
      result.size must be (1)
      result.foreach(_.data.foreach(_ mustBe (XMLTestData.emiAdjustmentsExpData)))
      try {
        dataGenerator.getErrors(XMLTestData.getIncorrectsheetNameTemplate)(schemeInfo,hc,request)
      } catch {
        case e:Throwable => e.getMessage mustBe "Incorrect ERS Template - Sheet Name isn't as expected"
      }
      verify(mockAuditEvents, times(1)).fileProcessingErrorAudit(argEq(schemeInfo), argEq("EMI40_Adjustment"), argEq("Could not set the validator"))(any(), any())
    }

    "get mandatory Data for Iterator of Strings" in {
      val result = dataGenerator.getErrors(XMLTestData.getEMIReplacedTemplate)(schemeInfo,hc,request)
      result.size must be (1)
      result.foreach(_.data.foreach(_ must be (XMLTestData.emiReplacedExpMandatoryData)))
    }

    "expand repeated rows" in {
      val result = dataGenerator.getErrors(XMLTestData.getEMIAdjustmentsRepeatedTemplate)(schemeInfo,hc,request)
      result.size mustEqual(1)
      result.head.data.size mustEqual(4)
    }
  }

  "constructColumnData" should {
    val emiAdjustmentsColCount = 14

    "trim a column to return a dataset that corresponds with the header size" in {
      val result = dataGenerator.constructColumnData(emiAdjustmentsTooLong.split(","),emiAdjustmentsColCount)
      result.size mustBe emiAdjustmentsColCount
      result.size must be < emiAdjustmentsTooLong.size
    }

    "pad a column to return a dataset that corresponds with the header size" in {
      val emiAdjustmentsOptionalEndSeq = emiAdjustmentsOptionalEnd.split(",")
      val result = dataGenerator.constructColumnData(emiAdjustmentsOptionalEndSeq,emiAdjustmentsColCount)
      result.size mustBe emiAdjustmentsColCount
      result.size must be > emiAdjustmentsOptionalEndSeq.size
    }

    "return the same sized data set if all columns are answered and present" in {
      val emiAdjustmentsCollectionSeq = emiAdjustmentsCollection.split(",")
      val result = dataGenerator.constructColumnData(emiAdjustmentsCollectionSeq,emiAdjustmentsColCount)
      result.size mustBe emiAdjustmentsColCount
      result.size mustBe emiAdjustmentsCollectionSeq.size
    }

  }

  "getSheetCsv" should {
    def testServiceCreator(sheets: Map[String, SheetInfo]) = new DataGenerator(
      mockAuditEvents, mockAppConfig
    ) {
      override val ersSheetsClone: Map[String, SheetInfo] = sheets
    }

    when(mockAuditEvents.fileProcessingErrorAudit(any(), any(), any())(any(), any())).thenReturn(true)

    "return a sheet info if sheetname is found in the sheets" in {
      val sheetTest: SheetInfo = SheetInfo("schemeType", 1, "sheetName", "sheetTitle", "configFileName", List("aHeader"))
      val testService: DataGenerator = testServiceCreator(Map("aName" -> sheetTest))

      val result = testService.getSheetCsv("aName", schemeInfo)
      assert(result.isRight)
      result.right.get mustBe sheetTest
    }

    "return a right if sheetname is not found in sheets" in {

      val sheetTest: SheetInfo = SheetInfo("schemeType", 1, "sheetName", "sheetTitle", "configFileName", List("aHeader"))
      val testService: DataGenerator = testServiceCreator(Map("anotherName" -> sheetTest))

      val result = testService.getSheetCsv("aWrongName", schemeInfo)
      assert(result.isLeft)
      result.left.get mustBe ERSFileProcessingException(
        s"${ErrorResponseMessages.dataParserIncorrectSheetName}",
        s"${ErrorResponseMessages.dataParserUnidentifiableSheetName("aWrongName")}")

    }
  }

}
