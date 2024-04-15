/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.CarNavigationData

@Suppress("SameParameterValue", "MemberVisibilityCanBePrivate", "unused")
object CarAppNotifications {
    const val CHANNEL_ID = "NavigationServiceChannel"

    /** The identifier for the navigation notification displayed for the foreground service.  */
    private const val NAV_NOTIFICATION_ID = 87654321

    /** The identifier for the non-navigation notifications, such as a traffic accident warning.  */
    private const val NOTIFICATION_ID = 77654321

    private var mNotificationManager: NotificationManager? = null

    const val URI_SCHEME = "gem"
    const val URI_HOST = "navigation"

    /** Create a deep link URL from the given deep link action.  */
    private fun createDeepLinkUri(deepLinkAction: String): Uri {
        return Uri.fromParts(
            URI_SCHEME, URI_HOST, deepLinkAction
        )
    }

    private fun createNotificationChannel(context: Context) {
        mNotificationManager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = context.getString(R.string.app_name)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            )
            mNotificationManager?.createNotificationChannel(serviceChannel)
        }
    }

    /** Returns the [NotificationCompat] used as part of the foreground service.  */
    private fun getNotification(
        context: Context,
        shouldNotify: Boolean,
        showInCar: Boolean,
        navigatingDisplayTitle: CharSequence,
        navigatingDisplayContent: CharSequence,
        notificationIcon: Bitmap?
    ): Notification {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
//            .setContentIntent(createMainActivityPendingIntent())
            .setContentTitle(navigatingDisplayTitle)
            .setContentText(navigatingDisplayContent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(!shouldNotify)
            // Set the notification's background color on the car screen.
//            .setColor(
//                context.resources.getColor(
//                    R.color.nav_notification_background_color,
//                    null
//                )
//            )
            .setColorized(true)
            .setSmallIcon(R.drawable.ic_announce_white)
            .setLargeIcon(notificationIcon)
            .setTicker(navigatingDisplayTitle)
            .setWhen(System.currentTimeMillis())
            .setSilent(true)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
            builder.priority = NotificationManager.IMPORTANCE_HIGH
        }

        if (showInCar) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setComponent(ComponentName(context, Service::class.java))
                .setData(createDeepLinkUri(Intent.ACTION_VIEW))
            builder.extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setContentIntent(
                        CarPendingIntent.getCarApp(
                            context,
                            intent.hashCode(),
                            intent,
                            0
                        )
                    )
                    .build()
            )
        }
        return builder.build()
    }

    fun eraseNotification() {
        mNotificationManager?.cancel(NAV_NOTIFICATION_ID)
    }

    fun onNavigationDataUpdated(context: Context, data: CarNavigationData) {
        if (mNotificationManager == null)
            createNotificationChannel(context)

        val shouldNotify = false
        val notificationTitle = data.step?.distanceToNextTurn + data.step?.distanceToNextTurnUnit
        val notificationContent = data.step?.turnInstruction ?: ""
        val notificationIcon = data.step?.maneuver?.turnImage

        mNotificationManager?.notify(
            NAV_NOTIFICATION_ID,
            getNotification(
                context,
                shouldNotify,
                true,
                notificationTitle,
                notificationContent,
                notificationIcon
            )
        )
    }
}
