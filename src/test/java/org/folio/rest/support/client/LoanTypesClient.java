package org.folio.rest.support.client;

import io.vertx.core.json.JsonObject;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class LoanTypesClient {
  private final HttpClient client;
  private final URL loanTypesUrl;

  public LoanTypesClient(HttpClient client, URL loanTypesUrl) {
    this.client = client;
    this.loanTypesUrl = loanTypesUrl;
  }

  public String create(String name) {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    JsonObject loanTypeRequest = new JsonObject()
      .put("name", name);

    client.post(loanTypesUrl, loanTypeRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));

    Response response = TestBase.get(completed);

    return response.getJson().getString("id");
  }
}
