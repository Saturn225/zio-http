/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

package zio.http

import zio.test.TestAspect.withLiveClock
import zio.test._
import zio.{Scope, ZLayer}

import zio.http.Header.UserAgent
import zio.http.Header.UserAgent.ProductOrComment
import zio.http.netty.NettyConfig

object NettyMaxHeaderLengthSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  private val serverConfig: Server.Config = Server.Config.default.onAnyOpenPort.copy(maxHeaderSize = 1)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("should get a failure instead of an empty body") {
      val routes = Handler
        .fromFunctionZIO[Request] { request =>
          request.body.asString.map { body =>
            val responseBody = if (body.isEmpty) "<empty>" else body
            Response.text(responseBody)
          } // this should not be run, as the request is invalid
        }
        .sandbox
        .toRoutes

      for {
        port <- Server.installRoutes(routes)
        url     = URL.decode(s"http://localhost:$port").toOption.get
        headers = Headers(
          UserAgent(ProductOrComment.Product("a looooooooooooooooooooooooooooong header", None)),
        )

        res  <- Client.batched(Request(url = url, headers = headers, body = Body.fromString("some-body")))
        data <- res.body.asString
      } yield assertTrue(extractStatus(res) == Status.InternalServerError, data == "")
    }.provide(
      Client.default,
      Server.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      ZLayer.succeed(serverConfig),
    ) @@ withLiveClock
}
