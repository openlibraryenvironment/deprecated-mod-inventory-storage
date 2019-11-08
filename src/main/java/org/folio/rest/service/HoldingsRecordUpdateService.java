package org.folio.rest.service;

import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.HoldingsStorageAPI.ITEM_TABLE;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public class HoldingsRecordUpdateService {
  private static final Logger LOG = LoggerFactory.getLogger(HoldingsRecordUpdateService.class);
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";

  private final PostgresClient postgresClient;

  public HoldingsRecordUpdateService(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  public void updateHoldingsRecordAndItems(
    HoldingsRecord holdingsRecord, Handler<AsyncResult<Void>> resultHandler) {

    Future<List<Item>> itemsForHoldingFuture = getItemsForHoldingRecord(holdingsRecord.getId());
    Future<List<Item>> itemsToUpdateFuture = setEffectiveCallNumberForItems(itemsForHoldingFuture, holdingsRecord);

    itemsToUpdateFuture.setHandler(itemsToUpdate -> {
      if (itemsToUpdate.failed()) {
        LOG.warn("Can not fetch items for holding: " + holdingsRecord.getId(),
          itemsToUpdate.cause());

        resultHandler.handle(Future.failedFuture(itemsToUpdate.cause()));
        return;
      }

      postgresClient.startTx(connection -> {
        if (connection.failed()) {
          LOG.warn("Can not start transaction", connection.cause());

          resultHandler.handle(Future.failedFuture(connection.cause()));
          return;
        }

        Future<UpdateResult> lastUpdate = Future.succeededFuture();
        for (Item item : itemsToUpdate.result()) {
          lastUpdate = lastUpdate.compose(prev ->
            updateSingleEntity(connection, ITEM_TABLE, item.getId(), item));
        }

        // Once we done with items, update holding
        lastUpdate = lastUpdate.compose(prev ->
          updateSingleEntity(connection, HOLDINGS_RECORD_TABLE, holdingsRecord.getId(), holdingsRecord));

        // Transaction handling section
        lastUpdate.setHandler(updateResult -> {
          if (updateResult.failed()) {
            LOG.warn("Update failed with exception, rejecting transaction",
              updateResult.cause());

            postgresClient.rollbackTx(connection,
              revertResult -> resultHandler
                .handle(Future.failedFuture(updateResult.cause()))
            );
          } else {
            LOG.debug("Successfully updated, committing transaction");
            postgresClient.endTx(connection, resultHandler);
          }
        });
      });
    });
  }

  private Future<List<Item>> getItemsForHoldingRecord(String id) {
    Promise<List<Item>> itemsForHolding = Promise.promise();

    final Criterion criterion = new Criterion(
      new Criteria().addField("holdingsRecordId")
        .setJSONB(false).setOperation("=").setVal(id));

    postgresClient.get(ITEM_TABLE, Item.class, criterion, false, false,
      result -> {
        if (result.failed()) {
          itemsForHolding.fail(result.cause());
          return;
        }

        Results<Item> items = result.result();
        // Preventing a NPE
        itemsForHolding.complete(items.getResults() == null
          ? Collections.emptyList()
          : items.getResults()
        );
      });

    return itemsForHolding.future();
  }

  private Future<List<Item>> setEffectiveCallNumberForItems(Future<List<Item>> itemsFuture, HoldingsRecord holdingsRecord) {
    return itemsFuture.map(
      items -> items.stream()
        .map(item -> updateItemEffectiveCallNumber(item, holdingsRecord))
        .collect(Collectors.toList())
    );
  }

  private Item updateItemEffectiveCallNumber(Item item, HoldingsRecord holdingsRecord) {
    String updatedCallNumber = null;
    if (StringUtils.isNotBlank(item.getItemLevelCallNumber())) {
      updatedCallNumber = item.getItemLevelCallNumber();
    } else if (StringUtils.isNotBlank(holdingsRecord.getCallNumber())) {
      updatedCallNumber = holdingsRecord.getCallNumber();
    }

    item.setEffectiveCallNumber(updatedCallNumber);
    return item;
  }

  private <T> Future<UpdateResult> updateSingleEntity(
    AsyncResult<SQLConnection> connection, String tableName, String id, T entity) {

    Promise<UpdateResult> updateResultPromise = Promise.promise();

    postgresClient.update(connection, tableName, entity, "jsonb",
      String.format(WHERE_CLAUSE, id), false, updateResultPromise);

    return updateResultPromise.future();
  }
}
