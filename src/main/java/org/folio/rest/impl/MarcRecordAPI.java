/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Marcrecord;
import org.folio.rest.jaxrs.model.Marcrecords;
import org.folio.rest.jaxrs.resource.MarcRecords;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;


public class MarcRecordAPI implements MarcRecords {

  public static final String MARC_RECORD_TABLE = "";
  public static final String LOCATION_PREFIX = "";
  public static final Logger log = LoggerFactory.getLogger(MarcRecordAPI.class);
  private final Messages messages = Messages.getInstance();
  public static final String ID_FIELD = "'id'";
  public static final Logger logger = LoggerFactory.getLogger(
          MarcRecordAPI.class);

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(MARC_RECORD_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }


  //BEGIN UTILITY METHODS

  PostgresClient getPGClient(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }

  private String getErrorResponse(String response) {
    //Check to see if we're suppressing messages or not
    return response;
  }

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private boolean isDuplicate(String errorMessage){
    if(errorMessage != null && errorMessage.contains(
            "duplicate key value violates unique constraint")){
      return true;
    }
    return false;
  }

  private boolean isNotPresent(String errorMessage) {
    if(errorMessage != null && errorMessage.contains(
       "is not present in table")) {
      return true;
    }
    return false;
  }

  private boolean isCQLError(Throwable err) {
    if(err.getCause() != null && err.getCause().getClass().getSimpleName()
            .endsWith("CQLParseException")) {
      return true;
    }
    return false;
  }

  private String getTenant(Map<String, String> headers)  {
    return TenantTool.calculateTenantId(headers.get(
            RestVerticle.OKAPI_HEADER_TENANT));
  }

  //END UTILITY METHODS

  @Override
  public void getMarcRecords(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      CQLWrapper cql = getCQL(query, limit, offset);
      pgClient.get(MARC_RECORD_TABLE, Marcrecord.class,
          new String[]{"*"}, cql, true, true, getReply -> {
        if(getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              GetMarcRecordsResponse.respond500WithTextPlain(
              getErrorResponse(message))));
        } else {
          List<Marcrecord> mrList = (List<Marcrecord>)getReply.result().getResults();
          Marcrecords marcRecords = new Marcrecords();
          marcRecords.setMarcrecords(mrList);
          marcRecords.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
          asyncResultHandler.handle(Future.succeededFuture(
              GetMarcRecordsResponse.respond200WithApplicationJson(marcRecords)));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      if(isCQLError(e)) {
        message = String.format("CQL Error: %s", message);
      }
      asyncResultHandler.handle(Future.succeededFuture(
          GetMarcRecordsResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void postMarcRecords(String lang, Marcrecord entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      String id = entity.getId();
      if(id == null){
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      pgClient.save(MARC_RECORD_TABLE, id, entity, saveReply -> {
        if(saveReply.failed()) {
          String message = logAndSaveError(saveReply.cause());
          if(isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PostMarcRecordsResponse.respond422WithApplicationJson(
                ValidationHelper.createValidationErrorMessage("id",
                entity.getId(), "Marc Record Exists"))));
          } else if(isNotPresent(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PostMarcRecordsResponse.respond422WithApplicationJson(
                ValidationHelper.createValidationErrorMessage("id",
                entity.getId(), "Referenced Marc Record does not exist"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PostMarcRecordsResponse.respond500WithTextPlain(
                getErrorResponse(message))));
          }
        } else {
          String ret = saveReply.result();
            entity.setId(ret);
            asyncResultHandler.handle(Future.succeededFuture(
                PostMarcRecordsResponse
                  .respond201WithApplicationJson(entity,
                    PostMarcRecordsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PostMarcRecordsResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void deleteMarcRecords(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", MARC_RECORD_TABLE);
      logger.info(String.format("Deleting all MARC records with query %s",
          DELETE_ALL_QUERY));
      pgClient.mutate(DELETE_ALL_QUERY, mutateReply -> {
        if(mutateReply.failed()) {
          String message = logAndSaveError(mutateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteMarcRecordsResponse.respond500WithTextPlain(
              getErrorResponse(message))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteMarcRecordsResponse.noContent().build()));
          }
        });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(DeleteMarcRecordsResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    }
  }

  @Override
  public void getMarcRecordsByMarcrecordId(String marcrecordId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
try {
      String tenantId = getTenant(okapiHeaders);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      Criteria idCrit = new Criteria()
          .addField(ID_FIELD)
          .setOperation("=")
          .setValue(marcrecordId);
      pgClient.get(MARC_RECORD_TABLE, Marcrecord.class,
          new Criterion(idCrit), true, false, getReply -> {
        if (getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              GetMarcRecordsByMarcrecordIdResponse.respond500WithTextPlain(
              getErrorResponse(message))));
        } else {
          List<Marcrecord> marcRecordList = getReply.result().getResults();
          if (marcRecordList.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
                GetMarcRecordsByMarcrecordIdResponse
                .respond404WithTextPlain(String.format(
                "No MARC record exists with id '%s'", marcrecordId))));
          } else {
            Marcrecord marcRecord = marcRecordList.get(0);
            asyncResultHandler.handle(Future.succeededFuture(
                GetMarcRecordsByMarcrecordIdResponse.respond200WithApplicationJson(marcRecord)));
          }
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          GetMarcRecordsByMarcrecordIdResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void deleteMarcRecordsByMarcrecordId(String marcrecordId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
       String tenantId = getTenant(okapiHeaders);
       PostgresClient pgClient = getPGClient(vertxContext, tenantId);
       Criteria idCrit = new Criteria()
           .addField(ID_FIELD)
           .setOperation("=")
           .setValue(marcrecordId);
       pgClient.delete(MARC_RECORD_TABLE, new Criterion(idCrit),
           deleteReply -> {
         if(deleteReply.failed()) {
           String message = logAndSaveError(deleteReply.cause());
           asyncResultHandler.handle(Future.succeededFuture(
               DeleteMarcRecordsByMarcrecordIdResponse.respond500WithTextPlain(
               getErrorResponse(message))));
         } else {
           if(deleteReply.result().getUpdated() == 0) {
             asyncResultHandler.handle(Future.succeededFuture(
               DeleteMarcRecordsByMarcrecordIdResponse
               .respond404WithTextPlain("Not found")));
           } else {
             asyncResultHandler.handle(Future.succeededFuture(
               DeleteMarcRecordsByMarcrecordIdResponse.respond204()));
           }
         }
       });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteMarcRecordsByMarcrecordIdResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void putMarcRecordsByMarcrecordId(String marcrecordId, String lang,
      Marcrecord entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      Criteria idCrit = new Criteria()
          .addField(ID_FIELD)
          .setOperation("=")
          .setValue(marcrecordId);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      pgClient.update(MARC_RECORD_TABLE, entity, new Criterion(idCrit),
          false, updateReply -> {
        if(updateReply.failed()) {
          String message = logAndSaveError(updateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
             PutMarcRecordsByMarcrecordIdResponse
             .respond500WithTextPlain(getErrorResponse(message))));
        } else if(updateReply.result().getUpdated() == 0) {
          asyncResultHandler.handle(Future.succeededFuture(
             PutMarcRecordsByMarcrecordIdResponse
             .respond404WithTextPlain("Not found")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
             PutMarcRecordsByMarcrecordIdResponse
             .respond204()));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PutMarcRecordsByMarcrecordIdResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    }
  }
}
