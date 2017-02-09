package org.folio.inventory.domain

import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

import java.util.function.Consumer

interface AsynchronousCollection<T> {
  void empty(Closure completionCallback)
  void add(T item, Closure resultCallback)
  void findById(String id,
                Consumer<Success<T>> resultCallback,
                Consumer<Failure> failureCallback)
  void findAll(PagingParameters pagingParameters,
               Consumer<Success<List<T>>> resultsCallback,
               Consumer<Failure> failureCallback)
  void delete(String id, Closure completionCallback)
  void update(T item,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback)
}
