package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint

/**
 * Raises a platform notification for a motion or sound alert. The viewer always shows an
 * inline banner as well — this is the part that reaches a parent who has looked away.
 *
 * Takes the whole [endpoint] rather than a name because the notification has to be
 * openable: tapping it should land on *this* camera's live view with the sound already on.
 * Before, it carried a display string and nothing else, so the tap could only reopen the app
 * at whatever screen it was last on.
 */
expect fun notifyAlert(endpoint: CameraEndpoint, message: String)
