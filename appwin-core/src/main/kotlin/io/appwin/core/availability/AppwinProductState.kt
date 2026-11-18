package io.appwin.core.availability

import io.appwin.core.AppwinInternalApi

/** A product's verdict and its pause, published together so no reader sees one without the other. */
@AppwinInternalApi
public data class AppwinProductState(val result: AppwinInitResult, val pause: AppwinPause?)
