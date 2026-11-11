package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException

/** A failed read cannot turn a confirmed write into a failed command. */
internal object ConfirmedMutation {
    suspend fun run(mutate:suspend ()->Unit,current:()->Boolean,acknowledge:()->Unit,
                    refresh:suspend ()->Unit,refreshFailed:()->Unit) {
        mutate()
        if(!current())return
        acknowledge()
        try {refresh()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){if(current())refreshFailed()}
    }
}
