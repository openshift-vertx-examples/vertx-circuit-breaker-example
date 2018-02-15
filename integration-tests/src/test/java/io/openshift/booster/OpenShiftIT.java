/*
 *
 *  Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.openshift.booster;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.json.JsonObject;

import static io.restassured.RestAssured.get;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.awaitility.Awaitility.await;

/**
 * @author Martin Kouba
 * @author Slavomir Krupa
 */
@RunWith(Arquillian.class)
public class OpenShiftIT {

	private static final String NAME_SERVICE_APP = "name-service";
	private static final String GREETING_SERVICE_APP = "greeting-service";

	private static final String OK = "ok";
	private static final String FAIL = "fail";
	private static final String HELLO_OK = "Hello, World!";
	private static final String HELLO_FALLBACK = "Hello, Fallback!";

	private static final long SLEEP_WINDOW = 5000L;
	private static final long REQUEST_THRESHOLD = 3;

	@RouteURL(NAME_SERVICE_APP)
	private URL nameServiceUrl;

	@RouteURL(GREETING_SERVICE_APP)
	private URL greetingServiceUrl;

	@Before
	public void setup() {
		await().pollInterval(1, TimeUnit.SECONDS).atMost(5, TimeUnit.MINUTES).until(() -> {
			try {
				return get(greetingServiceUrl.toExternalForm() + "health").getStatusCode() == 200
						&& get(nameServiceUrl.toExternalForm() + "health").getStatusCode() == 200;
			} catch (Exception ignored) {
				return false;
			}
		});
	}

	@Test
	public void testThatCircuitBreakerIsClosedByDefault() throws InterruptedException {
		assertCircuitBreaker(CircuitBreakerState.CLOSED);
		assertGreeting(HELLO_OK);
	}

	@Test
	public void testThatCircuitBreakerIsOpenedAfterFailures() throws InterruptedException {
		changeNameServiceState(FAIL);
		for (int i = 0; i < REQUEST_THRESHOLD; i++) {
			assertGreeting(HELLO_FALLBACK);
		}
		// Circuit breaker should be open now
		await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.OPEN));
		changeNameServiceState(OK);
		await().atMost(7, TimeUnit.SECONDS).pollDelay(SLEEP_WINDOW, TimeUnit.MILLISECONDS).until(() -> testGreeting(HELLO_OK));
		// The health counts should be reset
		assertCircuitBreaker(CircuitBreakerState.CLOSED);
	}

	@Test
	public void testThatWeExposeHalfOpenState() throws InterruptedException {
		changeNameServiceState(FAIL);
		for (int i = 0; i < REQUEST_THRESHOLD; i++) {
			assertGreeting(HELLO_FALLBACK);
		}
		// Circuit breaker should be open now
		await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.OPEN));
		await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.HALF_OPEN));
		// when half open state shows up we should switch to
		changeNameServiceState(OK);
		assertGreeting(HELLO_OK);
		assertCircuitBreaker(CircuitBreakerState.CLOSED);
	}

	private Response greetingResponse() {
		return RestAssured.when().get(greetingServiceUrl.toExternalForm() + "api/greeting");
	}

	private void assertGreeting(String expected) {
		Response response = greetingResponse();
		response.then().statusCode(200).body("content", equalTo(expected));
	}

	private boolean testGreeting(String expected) {
		Response response = greetingResponse();
		response.then().statusCode(200);
		return response.getBody().jsonPath().getString("content").equals(expected);
	}

	private Response circuitBreakerResponse() {
		return RestAssured.when().get(greetingServiceUrl.toExternalForm() + "api/cb-state");
	}

	private void assertCircuitBreaker(CircuitBreakerState expectedState) {
		Response response = circuitBreakerResponse();
		response.then().statusCode(200).body("state", equalTo(expectedState.name()));
	}

	private boolean testCircuitBreakerState(CircuitBreakerState expectedState) {
		Response response = circuitBreakerResponse();
		response.then().statusCode(200);
		return response.getBody().asString().contains(expectedState.name());
	}

	private void changeNameServiceState(String state) {
		Response response = RestAssured.given().header("Content-type", "application/json")
				.body(new JsonObject().put("state", state).encodePrettily()).put(nameServiceUrl.toExternalForm() + "api/state");
		response.then().assertThat().statusCode(200).body("state", equalTo(state));
	}
}
