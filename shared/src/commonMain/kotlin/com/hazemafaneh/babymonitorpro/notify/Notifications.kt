package com.hazemafaneh.babymonitorpro.notify

/**
 * Raises a platform notification for a motion or sound alert. The viewer always shows an
 * inline banner as well — this is the part that reaches a parent who has looked away.
 */
expect fun notifyAlert(cameraName: String, message: String)
