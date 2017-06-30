/**
  * Copyright 2015 Thomson Reuters
  *
  * Licensed under the Apache License, Version 2.0 (the “License”); you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
  * an “AS IS” BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */


package cmwell.it

import cmwell.util.concurrent.SimpleScheduler.schedule
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.enablers.{Emptiness, Size}
import play.api.libs.json._
import org.scalatest.{AsyncFunSpec, Matchers}

class ExpansionTests extends AsyncFunSpec with Matchers with Helpers with fixture.NSHashesAndPrefixes with LazyLogging {

  describe("Search API should") {
    //Assertions
    val ingestData = {
      val imntr = scala.io.Source.fromURL(this.getClass.getResource(s"/steve_harris/Iron_Maiden.nt")).mkString
      val shrdf = scala.io.Source.fromURL(this.getClass.getResource(s"/steve_harris/steve.rdf")).mkString
      val f1 = Http.post(_in, imntr, Some("text/plain;charset=UTF-8"), List("format" -> "ntriples"), tokenHeader)
      val f2 = Http.post(_in, shrdf, Some("application/rdf+xml;charset=UTF-8"), List("format" -> "rdfxml"), tokenHeader)
      f1.zip(f2).map {
        case (r1, r2) => {
          withClue(r1 -> r2) {
            r1.status should be(200)
            r2.status should be(200)
            Json.parse(r1.payload) should be(jsonSuccess)
            Json.parse(r2.payload) should be(jsonSuccess)
          }
        }
      }
    }

    val waitForIngest = ingestData.flatMap(_ => schedule(indexingDuration)(()))

    val makeSureSteveHarrisIs404Ghost = waitForIngest.flatMap { _ =>
      Http.get(cmw / "dbpedia.org" / "resource" / "Steve_Harris_(musician)", List("format" -> "json")).map {
        res => withClue(res) {
          res.status should be(404)
        }
      }
    }

    implicit val jsValueArrSize = new Size[JsArray] {
      def sizeOf(obj: JsArray): Long = obj.value.size
    }

    implicit val jsValueArrEmptiness = new Emptiness[JsArray] {
      override def isEmpty(thing: JsArray): Boolean = thing.value.isEmpty
    }

    val skipSteveHarrisGhostToGetBand = waitForIngest.flatMap { _ =>
      Http.get(cmw / "identi.ca" / "user" / "13766", List("yg" -> ">sameAs.owl<bandMember.ontology","format" -> "json")).map {
        res => withClue(res) {
          res.status should be(200)
          Json.parse(res.payload) \ "infotons" match {
            case JsDefined(arr: JsArray) => arr should have size(2)
            case somethingElse => fail(s"got something that is not json with array: $somethingElse")
          }
        }
      }
    }

    val skipSteveHarrisGhostToGetIdentity = waitForIngest.flatMap { _ =>
      Http.get(cmw / "dbpedia.org" / "resource" / "Iron_Maiden",List("yg" -> ">bandMember.ontology<sameAs.owl","format" -> "json")).map {
        res => withClue(res) {
          res.status should be(200)
          Json.parse(res.payload) \ "infotons" match {
            case JsDefined(arr: JsArray) => arr should have size(2)
            case somethingElse => fail(s"got something that is not json with array: $somethingElse")
          }
        }
      }
    }

    val getInfotonsPointingToGhostOfSteveHarrisFrom404Path = waitForIngest.flatMap { _ =>
      Http.get(cmw / "dbpedia.org" / "resource" / "Steve_Harris_(musician)", List("yg" -> "<bandMember.ontology,sameAs.owl","format" -> "json")).map {
        res => withClue(res) {
          res.status should be(200)
          Json.parse(res.payload) \ "infotons" match {
            case JsDefined(arr: JsArray) => arr should have size(2)
            case somethingElse => fail(s"got something that is not json with array: $somethingElse")
          }
        }
      }
    }

    val filterOutInfotonsPointingToGhostOfSteveHarrisFrom404Path = waitForIngest.flatMap { _ =>
      Http.get(cmw / "dbpedia.org" / "resource" / "Steve_Harris_(musician)", List("yg" -> "<bandMember.ontology[type.rdf::http://www.w3.org/ns/prov#NotRealPersonType]","format" -> "json")).map {
        res => withClue(res) {
          res.status should be(200)
          Json.parse(res.payload) \ "infotons" match {
            case JsDefined(arr: JsArray) => arr shouldBe empty
            case somethingElse => fail(s"got something that is not json with array: $somethingElse")
          }
        }
      }
    }

    val tryToSkipSteveHarrisGhostToGetIdentityButGetGhostFilttered = waitForIngest.flatMap { _ =>
      Http.get(cmw / "dbpedia.org" / "resource" / "Iron_Maiden",List("yg" -> ">bandMember.ontology[type.rdf::http://www.w3.org/ns/prov#NotRealPersonType]<sameAs.owl","format" -> "json")).map {
        res => withClue(res) {
          res.status should be(200)
          Json.parse(res.payload) \ "infotons" match {
            case JsDefined(arr: JsArray) => arr should have size(1)
            case somethingElse => fail(s"got something that is not json with array: $somethingElse")
          }
        }
      }
    }

    it("should ingest the data")(ingestData)
    it("should make sure that steve harris is a ghost (I.E. 404)")(makeSureSteveHarrisIs404Ghost)
    it("should skip the ghost to get Iron Maiden band")(skipSteveHarrisGhostToGetBand)
    it("should skip the ghost in the opposite direction")(skipSteveHarrisGhostToGetIdentity)
    it("should expand from 404 path to existing infotons")(getInfotonsPointingToGhostOfSteveHarrisFrom404Path)
    it("should return an empty json response if traversal started from 404 path & all results were filtered out")(filterOutInfotonsPointingToGhostOfSteveHarrisFrom404Path)
    it("should try to skip the ghost but ended up ghost filtered")(tryToSkipSteveHarrisGhostToGetIdentityButGetGhostFilttered)
    //TODO: all ghost tests should also work for a deleted infoton (still 404 path...)
  }
}