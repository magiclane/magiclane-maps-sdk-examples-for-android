/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.search_compose.data.repository

import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

interface SearchRepository {
    fun searchByFilter(filter: String, reference: Coordinates?): Flow<RepositorySearchResult>
    fun cancelSearch()
    fun getCurrentPosition(): Coordinates?
}

class SearchRepositoryImpl : SearchRepository {
    
    private val searchService = SearchService ( onCompleted =  {results, errorCode, _ ->
        searchCallback?.invoke(results, errorCode)}
    )
    
    private var searchCallback: ((ArrayList<Landmark>, Int) -> Unit)? = null
    
    override fun searchByFilter(filter: String, reference: Coordinates?): Flow<RepositorySearchResult> = callbackFlow {
        searchCallback = { results, errorCode ->
            val searchResult = when (errorCode) {
                GemError.NoError -> {
                    if (results.isEmpty()) {
                        RepositorySearchResult.Empty
                    } else {
                        RepositorySearchResult.Success(results)
                    }
                }
                GemError.Cancel -> RepositorySearchResult.Cancelled
                GemError.Busy -> RepositorySearchResult.Error("Requested operation cannot be performed. Internal limit reached. Please use an API token in order to avoid this error.")
                GemError.NotFound -> RepositorySearchResult.Empty
                else -> RepositorySearchResult.Error(GemError.getMessage(errorCode))
            }
            trySend(searchResult)
        }
        
        SdkCall.postAsync {
            searchService.searchByFilter(filter, reference)
        }
        
        awaitClose {
            searchCallback = null
        }
    }.flowOn(Dispatchers.IO)
    
    override fun cancelSearch() {
        searchService.cancelSearch()
    }
    
    override fun getCurrentPosition(): Coordinates? {
        val position = PositionService.position
        return if (position?.isValid() == true) {
            position.coordinates
        } else {
            Coordinates(51.5072, 0.1276) // center London as fallback
        }
    }
}

sealed class RepositorySearchResult {
    data class Success(val landmarks: ArrayList<Landmark>) : RepositorySearchResult()
    object Empty : RepositorySearchResult()
    object Cancelled : RepositorySearchResult()
    data class Error(val message: String) : RepositorySearchResult()
}
