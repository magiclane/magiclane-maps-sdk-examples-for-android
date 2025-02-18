// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.senddebuginfo

// -------------------------------------------------------------------------------------------------

import kotlinx.coroutines.*
import java.util.concurrent.Executors

// -------------------------------------------------------------------------------------------------

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class CoroutinesAsyncTask<Params, Progress, Result>
{
    // ---------------------------------------------------------------------------------------------
    
    companion object
    {
        private var threadPoolExecutor: CoroutineDispatcher? = null
    }

    // ---------------------------------------------------------------------------------------------

    var status: Status = Status.PENDING
    private var preJob: Job? = null
    private var bgJob: Deferred<Result>? = null
    abstract fun doInBackground(vararg params: Params?): Result
    open fun onProgressUpdate(vararg progress: Progress?) {}
    open fun onPostExecute(result: Result?) {}
    open fun onPreExecute() {}
    open fun onCancelled(result: Result?) {}
    protected var isCancelled = false

    // ---------------------------------------------------------------------------------------------

    /**
     * Executes background task parallel with other background tasks in the queue using
     * default thread pool
     */
    fun execute(vararg params: Params?)
    {
        execute(Dispatchers.Default, *params)
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Executes background tasks sequentially with other background tasks in the queue using
     * single thread executor @Executors.newSingleThreadExecutor().
     */
    fun executeOnExecutor(vararg params: Params?)
    {
        if (threadPoolExecutor == null)
        {
            threadPoolExecutor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        }
        threadPoolExecutor?.let { execute(it, *params) }
    }

    // ---------------------------------------------------------------------------------------------

    private fun execute(dispatcher: CoroutineDispatcher, vararg params: Params?)
    {

        if (status != Status.PENDING)
        {
            when (status)
            {
                Status.RUNNING -> throw IllegalStateException("Cannot execute task. The task is already running.")
                Status.FINISHED -> throw IllegalStateException("Cannot execute task. The task has already been executed (a task can be executed only once)")
                else -> { }
            }
        }

        status = Status.RUNNING

        // it can be used to setup UI - it should have access to Main Thread
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.Main)
        {
            preJob = launch(Dispatchers.Main)
            {
                onPreExecute()
                bgJob = async(dispatcher)
                {
                    doInBackground(*params)
                }
            }
            preJob?.join()
            if (!isCancelled)
            {
                withContext(Dispatchers.Main)
                {
                    onPostExecute(bgJob?.await())
                    status = Status.FINISHED
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun cancel(mayInterruptIfRunning: Boolean)
    {
        if (preJob == null || bgJob == null)
        {
            return
        }
        
        if (mayInterruptIfRunning || (preJob?.isActive == false && bgJob?.isActive == false))
        {
            isCancelled = true
            status = Status.FINISHED
            if (bgJob?.isCompleted == true)
            {
                CoroutineScope(Dispatchers.IO).launch(Dispatchers.Main)
                {
                    onCancelled(bgJob?.await())
                }
            }
            preJob?.cancel(CancellationException("PreExecute: Coroutine Task cancelled"))
            bgJob?.cancel(CancellationException("doInBackground: Coroutine Task cancelled"))
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun publishProgress(vararg progress: Progress)
    {
        //need to update main thread
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.Main)
        {
            if (!isCancelled)
            {
                onProgressUpdate(*progress)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    enum class Status
    {
        PENDING,
        RUNNING,
        FINISHED
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
